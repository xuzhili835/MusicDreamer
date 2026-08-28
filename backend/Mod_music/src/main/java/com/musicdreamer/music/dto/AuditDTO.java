package com.musicdreamer.music.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 管理员审核：pass=true 通过发布；false 驳回（保持审核中，需填原因）。 */
@Data
public class AuditDTO {

    @NotNull(message = "songId 不能为空")
    private Long songId;

    @NotNull(message = "pass 不能为空")
    private Boolean pass;

    /** 驳回原因（驳回时必填）。 */
    private String rejectReason;
}
