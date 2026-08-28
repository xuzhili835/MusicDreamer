package com.musicdreamer.login.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.common.jwt.JwtUtil;
import com.musicdreamer.login.entity.OperationLog;
import com.musicdreamer.login.entity.User;
import com.musicdreamer.login.mapper.OperationLogMapper;
import com.musicdreamer.login.mapper.UserMapper;
import com.musicdreamer.login.service.WxAuthService;
import com.musicdreamer.login.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;

/**
 * 微信小程序登录实现。
 * 约定：openid 是唯一身份键（user.wx_openid 唯一索引）；自动建号的账号密码为随机 UUID
 * （用户不知晓，不可口令登录），邮箱为占位 wx_{openid}@wx.placeholder 满足原表 NOT NULL + 唯一约束。
 * 会话/JWT/登录日志与 AuthServiceImpl 口令登录完全同构（session:{uid} 记录、网关 auth floor 兼容）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthServiceImpl implements WxAuthService {

    private static final String JSCODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";
    private static final String KEY_SESSION = "session:";
    /** 与 AuthServiceImpl.KEY_SESSION 同一约定。 */
    public static final String OP_WX_LOGIN = "WX_LOGIN";

    private final UserMapper userMapper;
    private final OperationLogMapper operationLogMapper;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;

    @Value("${wx.appid:}")
    private String appid;

    /** 密钥走环境变量 WX_APP_SECRET 或 gitignored 的 application-secret.yml，不进仓库。 */
    @Value("${wx.app-secret:}")
    private String appSecret;

    private final RestTemplate restTemplate = buildRestTemplate();

    @Override
    public LoginVO wxLogin(String code, HttpServletRequest request) {
        String openid = code2openid(code);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getWxOpenid, openid));
        if (user == null) {
            user = createWxUser(openid);
        }
        checkStatus(user);
        log.info("微信登录成功 userId={} username={}", user.getId(), user.getUsername());
        return issueToken(user, request);
    }

    @Override
    public void bindWx(Long userId, String code) {
        String openid = code2openid(code);
        User bound = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getWxOpenid, openid));
        if (bound != null && !bound.getId().equals(userId)) {
            throw new BizException(ErrorCode.WX_ALREADY_BOUND);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getWxOpenid, openid));
        log.info("绑定微信成功 userId={}", userId);
    }

    @Override
    public void unbindWx(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getWxOpenid, null));
        log.info("解绑微信成功 userId={}", userId);
    }

    /** jscode2session 换 openid：网络失败 1001、微信侧 errcode!=0 统一 2008。 */
    private String code2openid(String code) {
        if (!StringUtils.hasText(appid) || !StringUtils.hasText(appSecret)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "微信登录未配置（wx.app-secret）");
        }
        ResponseEntity<JsonNode> resp;
        try {
            resp = restTemplate.getForEntity(String.format(JSCODE2SESSION_URL, appid, appSecret, code), JsonNode.class);
        } catch (RestClientException e) {
            log.warn("[WX] jscode2session 调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        JsonNode body = resp.getBody();
        int errcode = body == null ? -1 : body.path("errcode").asInt(0);
        if (errcode != 0 || !StringUtils.hasText(body != null ? body.path("openid").asText(null) : null)) {
            log.warn("[WX] jscode2session 业务失败 errcode={} errmsg={}",
                    errcode, body == null ? "empty" : body.path("errmsg").asText(""));
            throw new BizException(ErrorCode.WX_CODE_INVALID);
        }
        return body.path("openid").asText();
    }

    /** 首次微信登录自动建号：并发首登靠 username/openid 唯一索引兜底，撞键后重查复用。 */
    private User createWxUser(String openid) {
        User user = new User();
        user.setUsername("wx_" + openid);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEmail("wx_" + openid + "@wx.placeholder");
        user.setNickname("悦友" + randomSuffix());
        user.setRole(0);
        user.setSingerStatus(0);
        user.setStatus(1);
        user.setWxOpenid(openid);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            User exists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getWxOpenid, openid));
            if (exists == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "微信账号创建失败，请重试");
            }
            return exists;
        }
        return user;
    }

    private void checkStatus(User user) {
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new BizException(ErrorCode.USER_DISABLED.getCode(), "该账号已注销，请联系管理员");
        }
    }

    /** 签发 JWT + 会话记录 + 登录日志，与口令登录同一套约定（remember=false 档 TTL）。 */
    private LoginVO issueToken(User user, HttpServletRequest request) {
        long ttl = JwtUtil.TTL_DEFAULT_MS;
        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getRole(), ttl);
        try {
            redis.opsForValue().set(KEY_SESSION + user.getId(), token, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("[REDIS] 写入会话记录失败(不阻断) 原因={}", e.getMessage());
        }
        writeWxLoginLog(user, request);
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .nickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();
    }

    private void writeWxLoginLog(User user, HttpServletRequest request) {
        try {
            OperationLog row = new OperationLog();
            row.setUserId(user.getId());
            row.setUsername(user.getUsername());
            row.setOperation(OP_WX_LOGIN);
            row.setMethod("POST");
            row.setParams("openid=" + mask(user.getWxOpenid()));
            row.setIp(request.getRemoteAddr());
            String ua = request.getHeader("User-Agent");
            row.setBrowser(ua != null && ua.length() > 100 ? ua.substring(0, 100) : ua);
            row.setStatus(1);
            operationLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("[OPLOG] 微信登录日志写入失败: {}", e.getMessage());
        }
    }

    /** 日志里 openid 打码：留首尾 4 位。 */
    private String mask(String openid) {
        if (!StringUtils.hasText(openid) || openid.length() <= 8) {
            return "****";
        }
        return openid.substring(0, 4) + "****" + openid.substring(openid.length() - 4);
    }

    private String randomSuffix() {
        return String.valueOf(1000 + (int) (Math.random() * 9000));
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }
}
