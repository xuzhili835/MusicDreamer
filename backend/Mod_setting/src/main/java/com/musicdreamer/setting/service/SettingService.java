package com.musicdreamer.setting.service;

import java.util.Map;

/** 配置中心：全量读取（60 秒内存缓存）与 upsert。 */
public interface SettingService {

    /** 全量配置（已用 Jackson 去掉 JSON 引号）。 */
    Map<String, String> getAll();

    /** upsert 一个配置项并失效本地缓存。 */
    void set(String key, String value);
}
