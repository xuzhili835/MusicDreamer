package com.musicdreamer.login.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.login.dto.SingerApplyDTO;
import com.musicdreamer.login.dto.SingerAuditDTO;
import com.musicdreamer.login.entity.SingerApplication;
import com.musicdreamer.login.entity.SingerProfile;
import com.musicdreamer.login.entity.User;
import com.musicdreamer.login.mapper.SingerApplicationMapper;
import com.musicdreamer.login.mapper.SingerProfileMapper;
import com.musicdreamer.login.mapper.UserMapper;
import com.musicdreamer.login.service.SingerService;
import com.musicdreamer.login.vo.SingerApplicationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 歌手认证实现（设计文档 5.3 状态流转）：
 * 申请：singer_status 0/3 -> 1（同一用户仅允许一个活跃申请）
 * 通过：application.status=2、user.singer_status=2、role 升级为 max(role,1)、补写 singer_profile(verified_date)
 * 驳回：application.status=3（记原因）、user.singer_status=3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SingerServiceImpl implements SingerService {

    private final SingerApplicationMapper applicationMapper;
    private final SingerProfileMapper profileMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(Long userId, SingerApplyDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        Long active = applicationMapper.selectCount(new LambdaQueryWrapper<SingerApplication>()
                .eq(SingerApplication::getUserId, userId)
                .eq(SingerApplication::getStatus, 1));
        if (active != null && active > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS.getCode(), "已有审核中的申请");
        }

        SingerApplication app = new SingerApplication();
        app.setUserId(userId);
        app.setRealName(dto.getRealName());
        app.setIdCard(dto.getIdCard());
        app.setIdCardFront("");   // 证件照字段课设简化
        app.setIdCardBack("");
        app.setArtistStatement(dto.getArtistStatement());
        app.setStatus(1);
        applicationMapper.insert(app);

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getSingerStatus, 1));
        log.info("歌手认证申请提交 userId={} applicationId={}", userId, app.getId());
        return app.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(SingerAuditDTO dto, Long auditorId) {
        SingerApplication app = applicationMapper.selectById(dto.getApplicationId());
        if (app == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND.getCode(), "认证申请不存在");
        }
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new BizException(ErrorCode.PARAM_FORMAT_ERROR.getCode(), "该申请已审核完结，不可重复审核");
        }
        User user = userMapper.selectById(app.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        boolean pass = Boolean.TRUE.equals(dto.getPass());
        if (pass) {
            applicationMapper.update(null, new LambdaUpdateWrapper<SingerApplication>()
                    .eq(SingerApplication::getId, app.getId())
                    .set(SingerApplication::getStatus, 2)
                    .set(SingerApplication::getAuditorId, auditorId)
                    .set(SingerApplication::getAuditTime, LocalDateTime.now()));
            int newRole = Math.max(user.getRole() == null ? 0 : user.getRole(), 1);
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getSingerStatus, 2)
                    .set(User::getRole, newRole));
            if (profileMapper.selectCount(new LambdaQueryWrapper<SingerProfile>()
                    .eq(SingerProfile::getUserId, user.getId())) == 0) {
                SingerProfile profile = new SingerProfile();
                profile.setUserId(user.getId());
                profile.setStageName(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
                profile.setVerifiedDate(LocalDateTime.now());
                profile.setFansCount(0);
                profile.setTotalPlays(0L);
                profileMapper.insert(profile);
            }
            log.info("歌手认证通过 userId={} applicationId={} auditor={}", user.getId(), app.getId(), auditorId);
        } else {
            String reason = StringUtils.hasText(dto.getRejectReason()) ? dto.getRejectReason() : "提交材料不符合认证要求";
            applicationMapper.update(null, new LambdaUpdateWrapper<SingerApplication>()
                    .eq(SingerApplication::getId, app.getId())
                    .set(SingerApplication::getStatus, 3)
                    .set(SingerApplication::getRejectReason, reason)
                    .set(SingerApplication::getAuditorId, auditorId)
                    .set(SingerApplication::getAuditTime, LocalDateTime.now()));
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getSingerStatus, 3));
            log.info("歌手认证驳回 userId={} applicationId={} auditor={} 原因={}",
                    user.getId(), app.getId(), auditorId, reason);
        }
    }

    @Override
    public List<SingerApplicationVO> applications(Integer status) {
        LambdaQueryWrapper<SingerApplication> wrapper = new LambdaQueryWrapper<SingerApplication>();
        if (status != null) {
            wrapper.eq(SingerApplication::getStatus, status);
        }
        wrapper.orderByDesc(SingerApplication::getId);
        List<SingerApplication> apps = applicationMapper.selectList(wrapper);
        if (apps.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = apps.stream().map(SingerApplication::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return apps.stream().map(app -> {
            SingerApplicationVO vo = new SingerApplicationVO();
            vo.setId(app.getId());
            vo.setUserId(app.getUserId());
            User u = userMap.get(app.getUserId());
            vo.setUsername(u == null ? null : u.getUsername());
            vo.setRealName(app.getRealName());
            vo.setArtistStatement(app.getArtistStatement());
            vo.setStatus(app.getStatus());
            vo.setRejectReason(app.getRejectReason());
            vo.setAuditTime(app.getAuditTime());
            vo.setCreateTime(app.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
