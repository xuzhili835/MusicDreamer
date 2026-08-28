package com.musicdreamer.setting.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.setting.dto.SetSettingDTO;
import com.musicdreamer.setting.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置中心接口（设计文档第 11 章）：
 * /setting/all 登录可读；/setting/set/{key} 管理员；
 * /setting/internal/all 无鉴权，供其他服务 Feign 拉取后本地缓存 60 秒。
 */
@RestController
@RequestMapping("/api/v1/setting")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    /** 全量配置（登录）。 */
    @GetMapping("/all")
    public Mess all() {
        AuthContext.requireLogin();
        return Mess.ok(settingService.getAll());
    }

    /** upsert 单个配置并失效缓存（管理员）。 */
    @PostMapping("/set/{key}")
    public Mess set(@PathVariable String key, @RequestBody SetSettingDTO dto) {
        AuthContext.requireAdmin();
        settingService.set(key, dto == null ? null : dto.getValue());
        return Mess.ok();
    }

    /** 内部接口：全量配置（无鉴权，供 Feign 调用，不经网关）。 */
    @GetMapping("/internal/all")
    public Mess internalAll() {
        return Mess.ok(settingService.getAll());
    }
}
