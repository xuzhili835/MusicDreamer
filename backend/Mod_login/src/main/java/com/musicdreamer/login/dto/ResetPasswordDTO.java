package com.musicdreamer.login.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 找回密码：用户名 + 注册邮箱 双匹配即可重置（邮箱验证码流程已随邮箱认证一并移除）。 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String newPassword;
}
