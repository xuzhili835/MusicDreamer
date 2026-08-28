package com.musicdreamer.media.service;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.media.client.SettingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 配置读取：SettingClient 拉取 + 60 秒本地缓存；Feign 失败降级 yml 默认值。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingClient settingClient;

    @Value("${media.volume-target-lufs:-16}")
    private double defaultLufs;
    @Value("${media.storage-root:./data}")
    private String defaultStorageRoot;
    @Value("${media.tools-path:}")
    private String ymlToolsPath;

    private volatile Map<String, String> cache;
    private volatile long cacheAt = 0;

    public String get(String key, String def) {
        long now = System.currentTimeMillis();
        if (cache == null || now - cacheAt > 60_000) {
            try {
                Mess m = settingClient.all();
                if (m != null && m.isOk() && m.getData() instanceof Map<?, ?> raw) {
                    Map<String, String> fresh = new ConcurrentHashMap<>();
                    raw.forEach((k, v) -> fresh.put(String.valueOf(k),
                            v == null ? "" : String.valueOf(v).replaceAll("^\"|\"$", "")));
                    cache = fresh;
                    cacheAt = now;
                }
            } catch (Exception e) {
                log.warn("setting fetch degraded: {}", e.getMessage());
                if (cache == null) {
                    cache = new ConcurrentHashMap<>();
                    cacheAt = now;
                }
            }
        }
        String v = cache.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public String toolsPath() { return get("tools_path", ymlToolsPath); }
    public String downloadProxy() { return get("download_proxy", ""); }
    public String cookiesPath() { return get("cookies_path", ""); }
    public double volumeTargetLufs() {
        try {
            return Double.parseDouble(get("volume_target_lufs", String.valueOf(defaultLufs)));
        } catch (NumberFormatException e) {
            return defaultLufs;
        }
    }
    // 转写模型固定 small：一般人只装一个模型，small 的中日文识别质量
    // 直接决定歌词校准/加时间轴的准确性，不提供多规格选择
    public String whisperModel() { return "small"; }
    public String storageRoot() { return get("storage_root", defaultStorageRoot); }
}
