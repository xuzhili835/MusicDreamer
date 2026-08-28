package com.musicdreamer.setting.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.setting.entity.SysSetting;
import com.musicdreamer.setting.mapper.SysSettingMapper;
import com.musicdreamer.setting.service.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置中心实现（设计文档第 11 章）：
 * - cfg_value 以 JSON 字符串存储（初始数据如 '"-16"'），读取时 Jackson 反序列化去引号；
 * - 内存缓存 60 秒，写入后立即失效，供本服务与 Feign 调用方对齐"本地缓存不超过 60 秒"的生效机制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final SysSettingMapper sysSettingMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Map<String, String> cache;
    private volatile long cacheLoadedAt;

    @Override
    public Map<String, String> getAll() {
        Map<String, String> snapshot = cache;
        if (snapshot == null || System.currentTimeMillis() - cacheLoadedAt > CACHE_TTL_MS) {
            synchronized (this) {
                if (cache == null || System.currentTimeMillis() - cacheLoadedAt > CACHE_TTL_MS) {
                    reload();
                }
                snapshot = cache;
            }
        }
        return snapshot;
    }

    @Override
    public void set(String key, String value) {
        String json;
        try {
            json = objectMapper.writeValueAsString(value == null ? "" : value);
        } catch (Exception e) {
            json = value == null ? "" : value;
        }
        SysSetting existing = sysSettingMapper.selectById(key);
        SysSetting row = new SysSetting();
        row.setCfgKey(key);
        row.setCfgValue(json);
        if (existing == null) {
            sysSettingMapper.insert(row);
        } else {
            sysSettingMapper.updateById(row);
        }
        synchronized (this) {
            cache = null;
        }
        log.info("配置更新 key={} value={}", key, value);
    }

    private void reload() {
        try {
            Map<String, String> fresh = new LinkedHashMap<>();
            for (SysSetting s : sysSettingMapper.selectList(null)) {
                fresh.put(s.getCfgKey(), unquote(s.getCfgValue()));
            }
            cache = Collections.unmodifiableMap(fresh);
            cacheLoadedAt = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("[SETTING] 配置加载失败，保留旧缓存: {}", e.getMessage());
            if (cache == null) {
                cache = Collections.emptyMap();
                cacheLoadedAt = System.currentTimeMillis();
            }
        }
    }

    /** '"-16"' -> -16，'"base"' -> base；非法 JSON 原样返回。 */
    private String unquote(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            return objectMapper.readValue(raw, String.class);
        } catch (Exception e) {
            return raw;
        }
    }
}
