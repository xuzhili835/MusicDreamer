package com.musicdreamer.login.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 登录态修改密码（F004）。 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String newPassword;
}
