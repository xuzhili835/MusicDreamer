package com.musicdreamer.music.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.music.dto.ReportHandleDTO;
import com.musicdreamer.music.dto.ReportSubmitDTO;
import com.musicdreamer.music.entity.Report;
import com.musicdreamer.music.mapper.ReportMapper;
import com.musicdreamer.music.vo.ReportItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 举报处理：提交（登录）/ 列表与处置（管理员）。
 * 处置动作 action：confirm=确认违规（歌曲类同步下架）/ dismiss=驳回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 举报状态：1 待处理 / 2 已处理(违规成立) / 3 已驳回。 */
    public static final int STATUS_PENDING = 1;
    public static final int STATUS_CONFIRMED = 2;
    public static final int STATUS_DISMISSED = 3;

    /** 举报对象类型：1 歌曲 / 2 评论 / 3 歌单 / 4 动态。 */
    public static final int TARGET_SONG = 1;
    public static final int TARGET_COMMENT = 2;

    private final ReportMapper reportMapper;
    private final SongService songService;
    private final CommentService commentService;

    /** 提交举报（登录）。 */
    public Long submit(ReportSubmitDTO dto) {
        Long reporterId = AuthContext.requireLogin();
        Report r = new Report();
        r.setReporterId(reporterId);
        r.setTargetType(dto.getTargetType());
        r.setTargetId(dto.getTargetId());
        r.setReason(dto.getReason());
        r.setDescription(dto.getDescription());
        r.setStatus(STATUS_PENDING);
        r.setCreateTime(LocalDateTime.now());
        reportMapper.insert(r);
        log.info("report submitted: id={}, reporter={}, target={}/{}",
                r.getId(), reporterId, dto.getTargetType(), dto.getTargetId());
        return r.getId();
    }

    /** 管理端举报列表（分页，可按状态筛选）。 */
    public Map<String, Object> list(int page, int size, Integer status) {
        AuthContext.requireAdmin();
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;
        long offset = (long) (page - 1) * size;
        List<ReportItemVO> rows = reportMapper.selectReportPage(status, offset, size);
        long total = reportMapper.countReports(status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", total);
        result.put("size", size);
        result.put("current", page);
        return result;
    }

    /** 处置举报（管理员）：确认违规时对歌曲/评论执行联动处置。 */
    @Transactional(rollbackFor = Exception.class)
    public void handle(ReportHandleDTO dto) {
        AuthContext.requireAdmin();
        Long adminId = AuthContext.getUserId();
        Report r = reportMapper.selectById(dto.getId());
        if (r == null) {
            throw new BizException(ErrorCode.PARAM_FORMAT_ERROR);
        }
        if (r.getStatus() != STATUS_PENDING) {
            throw new BizException(ErrorCode.PARAM_FORMAT_ERROR, "该举报已处理");
        }

        boolean confirm = "confirm".equalsIgnoreCase(dto.getAction());
        boolean dismiss = "dismiss".equalsIgnoreCase(dto.getAction());
        if (!confirm && !dismiss) {
            throw new BizException(ErrorCode.PARAM_FORMAT_ERROR, "action 仅支持 confirm/dismiss");
        }

        // 联动处置：确认违规 → 歌曲下架 / 评论删除
        if (confirm) {
            if (r.getTargetType() == TARGET_SONG) {
                songService.takedown(r.getTargetId(), "举报成立：" + (dto.getHandleResult() == null ? "违规内容" : dto.getHandleResult()));
            } else if (r.getTargetType() == TARGET_COMMENT) {
                commentService.delete(r.getTargetId());
            }
        }

        r.setStatus(confirm ? STATUS_CONFIRMED : STATUS_DISMISSED);
        r.setHandlerId(adminId);
        r.setHandleResult(dto.getHandleResult());
        r.setHandleTime(LocalDateTime.now());
        reportMapper.updateById(r);
        log.info("report handled: id={}, action={}, admin={}", r.getId(), dto.getAction(), adminId);
    }

    /** 供内部校验：某对象是否存在待处理举报。 */
    public boolean hasPending(int targetType, Long targetId) {
        return reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .eq(Report::getStatus, STATUS_PENDING)) > 0;
    }
}
