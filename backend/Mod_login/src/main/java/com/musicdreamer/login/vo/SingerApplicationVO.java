package com.musicdreamer.login.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 认证申请视图（管理端列表，附带申请用户名）。 */
@Data
public class SingerApplicationVO {

    private Long id;

    private Long userId;

    private String username;

    private String realName;

    private String artistStatement;

    /** 1 审核中 2 通过 3 驳回。 */
    private Integer status;

    private String rejectReason;

    private LocalDateTime auditTime;

    private LocalDateTime createTime;
}
