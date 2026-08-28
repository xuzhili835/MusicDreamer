package com.musicdreamer.media.client;

import com.musicdreamer.common.api.Mess;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** Mod_setting 内部配置接口（60 秒本地缓存见 SettingsService）。 */
@FeignClient(name = "settingService8005", path = "/api/v1/setting/internal",
        fallback = SettingClient.SettingClientFallback.class)
public interface SettingClient {

    @GetMapping("/all")
    Mess all();

    class SettingClientFallback implements SettingClient {
        @Override public Mess all() { return null; }
    }
}
