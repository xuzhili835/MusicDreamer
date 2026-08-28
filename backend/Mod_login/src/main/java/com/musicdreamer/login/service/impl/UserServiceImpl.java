package com.musicdreamer.login.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.login.dto.AdminCreateUserDTO;
import com.musicdreamer.login.dto.UpdateUserInfoDTO;
import com.musicdreamer.login.dto.UserStatusDTO;
import com.musicdreamer.login.entity.SingerProfile;
import com.musicdreamer.login.entity.User;
import com.musicdreamer.login.mapper.SingerProfileMapper;
import com.musicdreamer.login.mapper.UserMapper;
import com.musicdreamer.login.service.UserService;
import com.musicdreamer.login.util.Pinyins;
import com.musicdreamer.login.vo.UserBriefVO;
import com.musicdreamer.login.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final SingerProfileMapper profileMapper;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    /** 认证地板键（与 AuthServiceImpl/网关约定一致）。 */
    private static final String KEY_AUTH_FLOOR = "md_auth_floor:";

    @Override
    public UserVO info(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    public void updateInfo(Long userId, UpdateUserInfoDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        boolean changed = false;
        if (StringUtils.hasText(dto.getNickname())) {
            wrapper.set(User::getNickname, dto.getNickname());
            changed = true;
        }
        if (StringUtils.hasText(dto.getAvatar())) {
            wrapper.set(User::getAvatar, dto.getAvatar());
            changed = true;
        }
        if (changed) {
            userMapper.update(null, wrapper);
        }
    }

    @Override
    public Map<String, Object> pageUsers(long page, long size, String keyword) {
        Page<User> p = new Page<>(Math.max(page, 1), size <= 0 ? 10 : Math.min(size, 100));
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // bug81：已删除（status=2）的账号不再出现在管理列表
        wrapper.ne(User::getStatus, 2);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw).or().like(User::getNickname, kw));
        }
        wrapper.orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(p, wrapper);
        List<UserVO> list = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("size", result.getSize());
        data.put("list", list);
        return data;
    }

    @Override
    public void updateStatus(UserStatusDTO dto) {
        User user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (dto.getStatus() == null || (dto.getStatus() != 0 && dto.getStatus() != 1)) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE.getCode(), "status 仅允许 0(禁用) 或 1(启用)");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, dto.getUserId())
                .set(User::getStatus, dto.getStatus()));
        // 禁用即吊销该用户全部存量 token（P1-1）：网关按认证地板拒绝旧 token。
        // 启用不清地板——被禁期间签发的 token 恢复后依然无效，需重新登录
        if (dto.getStatus() == 0) {
            revokeTokens(dto.getUserId());
        }
    }

    @Override
    public Long adminCreate(AdminCreateUserDTO dto) {
        if (dto.getRole() == null || dto.getRole() < 0 || dto.getRole() > 2) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE.getCode(), "role 仅允许 0(听众) 1(歌手) 2(管理员)");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())) > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        String email = StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim()
                : dto.getUsername() + "@musicdream.local";
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS, "邮箱已被使用");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(email);
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setRole(dto.getRole());
        // 管理员创建即直接可用（跳过邮件激活）；歌手账号直接视为认证通过
        user.setSingerStatus(dto.getRole() == 1 ? 2 : 0);
        user.setStatus(1);
        userMapper.insert(user);

        if (dto.getRole() == 1 && profileMapper.selectCount(new LambdaQueryWrapper<SingerProfile>()
                .eq(SingerProfile::getUserId, user.getId())) == 0) {
            SingerProfile profile = new SingerProfile();
            profile.setUserId(user.getId());
            profile.setStageName(user.getNickname());
            profile.setVerifiedDate(LocalDateTime.now());
            profile.setFansCount(0);
            profile.setTotalPlays(0L);
            profileMapper.insert(profile);
        }
        log.info("管理员创建账号 userId={} username={} role={}", user.getId(), user.getUsername(), dto.getRole());
        return user.getId();
    }

    @Override
    public Map<String, Object> ensureSinger(String nickname) {
        String name = nickname == null ? "" : nickname.trim();
        if (name.isEmpty() || name.length() > 50) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE.getCode(), "歌手名需 1-50 字");
        }
        // 同名歌手（或管理员）账号已存在则直接复用，不重复创建
        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getNickname, name).in(User::getRole, 1, 2)
                .orderByAsc(User::getId).last("limit 1"));
        Map<String, Object> data = new HashMap<>();
        if (exist != null) {
            data.put("userId", exist.getId());
            data.put("username", exist.getUsername());
            data.put("created", false);
            return data;
        }
        // bug75：用户名 = 歌手名拼音全拼（冲突加序号），初始密码固定 admin123
        String base = Pinyins.full(name);
        if (base == null || base.isBlank()) {
            base = "singer";
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String username = base;
        int i = 1;
        while (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            username = base + (++i);
        }
        AdminCreateUserDTO dto = new AdminCreateUserDTO();
        dto.setUsername(username);
        dto.setPassword("admin123");
        dto.setNickname(name);
        dto.setRole(1);
        Long id = adminCreate(dto);
        log.info("自动创建歌手账号 userId={} username={} nickname={}", id, username, name);
        data.put("userId", id);
        data.put("username", username);
        data.put("created", true);
        return data;
    }

    @Override
    public void deleteUser(Long userId, Long operatorId) {
        if (userId != null && userId.equals(operatorId)) {
            throw new BizException(ErrorCode.NO_PERMISSION.getCode(), "不能删除当前登录账号");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getRole() != null && user.getRole() == 2) {
            throw new BizException(ErrorCode.NO_PERMISSION.getCode(), "管理员账号不支持删除，请先调整为普通用户");
        }
        // bug81：软删除——歌曲/评论/收藏等历史数据仍引用 user_id，物理删除会留孤儿；
        // status=2 的账号登录即拒、管理列表不可见，昵称保留供历史记录展示
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, 2));
        revokeTokens(userId);
    }

    /** 吊销用户全部存量 token：抬升认证地板，网关据此拒绝旧 token。 */
    private void revokeTokens(Long userId) {
        long floor = System.currentTimeMillis();
        try {
            String key = KEY_AUTH_FLOOR + userId;
            String cur = redis.opsForValue().get(key);
            if (cur == null || Long.parseLong(cur.trim()) < floor) {
                redis.opsForValue().set(key, String.valueOf(floor));
            }
        } catch (Exception e) {
            log.warn("抬升认证地板失败 userId={}: {}", userId, e.getMessage());
        }
    }

    @Override
    public UserBriefVO brief(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        UserBriefVO vo = new UserBriefVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        return vo;
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
