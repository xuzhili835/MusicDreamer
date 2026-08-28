package com.musicdreamer.login.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.login.dto.ChangePasswordDTO;
import com.musicdreamer.login.dto.ResetPasswordDTO;
import com.musicdreamer.login.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 密码相关接口：reset 匿名（用户名+邮箱双匹配），change 登录。 */
@RestController
@RequestMapping("/api/v1/user/password")
@RequiredArgsConstructor
public class PasswordController {

    private final AuthService authService;

    /** 找回密码：用户名 + 注册邮箱 匹配即可重置（匿名）。 */
    @PostMapping("/reset")
    public Mess reset(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Mess.ok();
    }

    /** 登录态修改密码（登录，校验旧密码）。 */
    @PostMapping("/change")
    public Mess change(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto);
        return Mess.ok();
    }
}
