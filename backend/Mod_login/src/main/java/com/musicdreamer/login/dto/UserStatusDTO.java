package com.musicdreamer.login.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 管理员禁用/启用用户。 */
@Data
public class UserStatusDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 0 禁用 1 启用。 */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
