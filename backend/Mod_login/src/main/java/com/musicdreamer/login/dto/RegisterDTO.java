package com.musicdreamer.login.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 注册请求（F001）。 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 之间")
    private String username;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 50, message = "昵称长度需在 2-50 之间")
    private String nickname;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;
}
