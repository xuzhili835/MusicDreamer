package com.musicdreamer.login.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 管理员审核歌手认证。 */
@Data
public class SingerAuditDTO {

    @NotNull(message = "申请ID不能为空")
    private Long applicationId;

    /** true 通过 / false 驳回。 */
    @NotNull(message = "审核结论不能为空")
    private Boolean pass;

    private String rejectReason;
}
