package com.musicdreamer.login.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.login.dto.AdminCreateUserDTO;
import com.musicdreamer.login.dto.UserStatusDTO;
import com.musicdreamer.login.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 管理端用户接口（管理员）。 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    /** 用户分页列表（keyword 模糊匹配 username/nickname，不返回密码）。 */
    @GetMapping("/list")
    public Mess list(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String keyword) {
        AuthContext.requireAdmin();
        return Mess.ok(userService.pageUsers(page, size, keyword));
    }

    /** 禁用/启用用户。 */
    @PostMapping("/status")
    public Mess status(@Valid @RequestBody UserStatusDTO dto) {
        AuthContext.requireAdmin();
        userService.updateStatus(dto);
        return Mess.ok();
    }

    /** 直接创建账号（跳过注册与邮件激活；歌手角色直接认证通过）。 */
    @PostMapping("/create")
    public Mess create(@Valid @RequestBody AdminCreateUserDTO dto) {
        AuthContext.requireAdmin();
        return Mess.ok(userService.adminCreate(dto));
    }

    /** bug75：确保同名歌手账号存在（无则拼音用户名 + 初始密码 admin123 自动创建），幂等。 */
    @PostMapping("/ensure-singer")
    public Mess ensureSinger(@RequestBody java.util.Map<String, String> body) {
        AuthContext.requireAdmin();
        return Mess.ok(userService.ensureSinger(body.get("nickname")));
    }

    /** bug81：删除用户（软删除，保留历史数据引用；不能删自己与管理员账号）。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/delete/{id}")
    public Mess delete(@org.springframework.web.bind.annotation.PathVariable Long id) {
        AuthContext.requireAdmin();
        userService.deleteUser(id, AuthContext.requireLogin());
        return Mess.ok();
    }
}
