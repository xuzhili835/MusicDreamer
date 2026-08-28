package com.musicdreamer.login.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作日志表（init.sql：operation_log）。 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** LOGIN / UPLOAD / DELETE / EDIT 等。 */
    private String operation;

    private String method;

    private String params;

    private String ip;

    private String location;

    private String browser;

    /** 0 失败 1 成功。 */
    private Integer status;

    private String errorMsg;

    private LocalDateTime createTime;
}
