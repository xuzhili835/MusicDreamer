package com.musicdreamer.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicdreamer.music.entity.OperationLog;
import com.musicdreamer.music.mapper.OperationLogMapper;
import com.musicdreamer.music.util.WebUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import com.musicdreamer.common.auth.AuthContext;

/** 操作日志：审核/下架/重新上架/举报处理落 operation_log，失败不阻断主流程。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public void record(String operation, String method, Object params) {
        try {
            OperationLog l = new OperationLog();
            l.setUserId(AuthContext.getUserId());
            l.setUsername(AuthContext.getUsername());
            l.setOperation(operation);
            l.setMethod(method);
            l.setParams(params == null ? null : objectMapper.writeValueAsString(params));
            l.setIp(WebUtil.clientIp());
            l.setStatus(1);
            l.setCreateTime(LocalDateTime.now());
            operationLogMapper.insert(l);
        } catch (Exception e) {
            log.warn("operation log write failed, op={}: {}", operation, e.getMessage());
        }
    }
}
