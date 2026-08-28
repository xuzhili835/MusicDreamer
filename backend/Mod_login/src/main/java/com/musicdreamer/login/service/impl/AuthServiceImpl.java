package com.musicdreamer.login.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.common.jwt.JwtUtil;
import com.musicdreamer.login.dto.ChangePasswordDTO;
import com.musicdreamer.login.dto.LoginDTO;
import com.musicdreamer.login.dto.RegisterDTO;
import com.musicdreamer.login.dto.ResetPasswordDTO;
import com.musicdreamer.login.entity.OperationLog;
import com.musicdreamer.login.entity.User;
import com.musicdreamer.login.mapper.OperationLogMapper;
import com.musicdreamer.login.mapper.UserMapper;
import com.musicdreamer.login.service.AuthService;
import com.musicdreamer.login.service.MailService;
import com.musicdreamer.login.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 认证服务实现。
 * Redis 约定：
 *   activate:pending:{userId} = 1            （激活 pending 标记，7 天）
 *   activate:token:{token}    = {userId}      （一次性激活令牌，24 小时）
 *   session:{userId}          = {token}       （主动失效会话记录）
 *   md_auth_floor:{userId}    = {epochMillis} （认证地板：早于该时刻签发的 token 由网关拒绝）
 * Redis 不可用时仅记录日志，不阻断登录等主流程。
 * 邮箱激活验证由 mail.activate-enabled 开关控制（默认关闭）：关闭时注册即可登录。
 * 找回密码已改为"用户名+邮箱双匹配"直接重置，不再使用邮箱验证码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String KEY_PENDING = "activate:pending:";
    private static final String KEY_TOKEN = "activate:token:";
    private static final String KEY_SESSION = "session:";
    /** 认证地板键（网关 AuthGlobalFilter 同名约定）：改密/重置/禁用时抬升。 */
    public static final String KEY_AUTH_FLOOR = "md_auth_floor:";

    private static final Duration PENDING_TTL = Duration.ofDays(7);
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    /** 前端激活页地址（开发环境）。 */
    private static final String ACTIVATE_URL = "http://localhost:5173/activate?token=";

    private final UserMapper userMapper;
    private final OperationLogMapper operationLogMapper;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();

    /** 邮箱激活验证开关：false=注册即可登录；true=需邮件激活后才能登录。 */
    @Value("${mail.activate-enabled:false}")
    private boolean activateEnabled;

    @Override
    public Long register(RegisterDTO dto) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())) > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, dto.getEmail())) > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS, "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setRole(0);
        user.setSingerStatus(0);
        user.setStatus(1);
        userMapper.insert(user);

        // 激活验证关闭时：不写 pending 标记、不发邮件，注册即可登录
        if (activateEnabled) {
            String token = UUID.randomUUID().toString().replace("-", "");
            redisSafe("写入激活标记", () -> {
                redis.opsForValue().set(KEY_PENDING + user.getId(), "1", PENDING_TTL);
                redis.opsForValue().set(KEY_TOKEN + token, String.valueOf(user.getId()), TOKEN_TTL);
            });
            mailService.sendActivateEmail(dto.getEmail(), ACTIVATE_URL + token);
        }
        log.info("注册成功 userId={} username={} emailActivate={}", user.getId(), user.getUsername(), activateEnabled);
        return user.getId();
    }

    @Override
    public void activate(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.PARAM_MISSING.getCode(), "激活令牌不能为空");
        }
        String userId = redisGet(KEY_TOKEN + token);
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_FORMAT_ERROR.getCode(), "激活令牌无效或已过期");
        }
        redisSafe("删除激活标记", () -> {
            redis.delete(KEY_TOKEN + token);
            redis.delete(KEY_PENDING + userId);
        });
        log.info("账号激活成功 userId={}", userId);
    }

    @Override
    public LoginVO login(LoginDTO dto, HttpServletRequest request) {
        // 依次校验：存在(2001) → 状态(2005) → 激活标记(2004，仅开关开启时) → 密码(2003)
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        // bug81：软删除账号（status=2）等同注销，登录直接拒绝
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new BizException(ErrorCode.USER_DISABLED.getCode(), "该账号已注销，请联系管理员");
        }
        if (activateEnabled && "1".equals(redisGet(KEY_PENDING + user.getId()))) {
            throw new BizException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_WRONG);
        }

        boolean remember = Boolean.TRUE.equals(dto.getRemember());
        long ttl = remember ? JwtUtil.TTL_REMEMBER_MS : JwtUtil.TTL_DEFAULT_MS;
        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getRole(), ttl);
        String sessionKey = KEY_SESSION + user.getId();
        redisSafe("写入会话记录", () ->
                redis.opsForValue().set(sessionKey, token, Duration.ofSeconds(ttl)));

        writeLoginLog(user, request);
        log.info("登录成功 userId={} remember={}", user.getId(), remember);
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .nickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();
    }

    @Override
    public void logout() {
        Long userId = AuthContext.getUserId();
        if (userId != null) {
            redisSafe("删除会话记录", () -> redis.delete(KEY_SESSION + userId));
        }
    }

    @Override
    public void resetPassword(ResetPasswordDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername())
                .eq(User::getEmail, dto.getEmail()));
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND.getCode(), "用户名与邮箱不匹配");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPassword, passwordEncoder.encode(dto.getNewPassword())));
        // 密码重置后使旧会话失效，并抬升认证地板让网关拒绝旧 token（P1-1）
        redisSafe("删除会话记录", () -> redis.delete(KEY_SESSION + user.getId()));
        bumpAuthFloor(user.getId());
        log.info("找回密码完成 userId={}", user.getId());
    }

    @Override
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = AuthContext.requireLogin();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_WRONG);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(dto.getNewPassword())));
        // 改密成功：吊销全部旧 token（P1-1）
        bumpAuthFloor(userId);
        log.info("修改密码成功 userId={}", userId);
    }

    /** 抬升认证地板（取现值与当前时间的较大者）；网关据此拒绝早于该时刻签发的 token。 */
    void bumpAuthFloor(Long userId) {
        long floor = System.currentTimeMillis();
        redisSafe("抬升认证地板", () -> {
            String key = KEY_AUTH_FLOOR + userId;
            String cur = redis.opsForValue().get(key);
            if (cur == null || parseLongSafely(cur) < floor) {
                redis.opsForValue().set(key, String.valueOf(floor));
            }
        });
    }

    private long parseLongSafely(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 登录操作日志（失败不影响登录主流程）。 */
    private void writeLoginLog(User user, HttpServletRequest request) {
        try {
            OperationLog row = new OperationLog();
            row.setUserId(user.getId());
            row.setUsername(user.getUsername());
            row.setOperation("LOGIN");
            row.setMethod("POST");
            row.setParams("username=" + user.getUsername());
            row.setIp(clientIp(request));
            String ua = request.getHeader("User-Agent");
            row.setBrowser(ua != null && ua.length() > 100 ? ua.substring(0, 100) : ua);
            row.setStatus(1);
            operationLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("[OPLOG] 登录日志写入失败: {}", e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Redis 读操作：异常时返回 null（视为无标记）。 */
    private String redisGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[REDIS] 读取失败(不阻断) key={} 原因={}", key, e.getMessage());
            return null;
        }
    }

    /** Redis 写/删操作：异常时仅记录日志。 */
    private void redisSafe(String action, Runnable op) {
        try {
            op.run();
        } catch (Exception e) {
            log.warn("[REDIS] {}失败(不阻断) 原因={}", action, e.getMessage());
        }
    }
}
