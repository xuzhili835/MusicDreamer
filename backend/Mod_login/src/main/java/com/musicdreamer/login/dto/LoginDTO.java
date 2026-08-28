package com.musicdreamer.login.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 登录请求（F003）。 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 记住登录：true 时 token 有效期 7 天，否则 24 小时。 */
    private Boolean remember = false;
}
