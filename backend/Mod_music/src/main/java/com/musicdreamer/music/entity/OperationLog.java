package com.musicdreamer.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理操作日志表（审核/下架/重新上架/举报处理）。 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** 操作类型：AUDIT / TAKEDOWN / RELIST / REPORT_HANDLE 等。 */
    private String operation;

    private String method;

    private String params;

    private String ip;

    private String location;

    private String browser;

    private Integer status;

    private String errorMsg;

    private LocalDateTime createTime;
}
