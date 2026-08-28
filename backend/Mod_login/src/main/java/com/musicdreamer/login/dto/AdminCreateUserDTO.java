package com.musicdreamer.login.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** 管理员直接创建账号（跳过注册与邮件激活，F015 扩展）。 */
@Data
public class AdminCreateUserDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;

    /** 昵称，缺省同用户名。 */
    @Size(max = 50, message = "昵称最长 50 字符")
    private String nickname;

    /** 邮箱可选；留空生成 username@musicdream.local 占位（user.email 非空且唯一）。 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长 100 字符")
    private String email;

    /** 0 听众 1 认证歌手 2 管理员。 */
    @NotNull(message = "角色不能为空")
    private Integer role;
}
