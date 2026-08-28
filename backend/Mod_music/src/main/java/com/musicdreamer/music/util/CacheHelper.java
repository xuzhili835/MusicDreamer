package com.musicdreamer.music.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 缓存读写助手：全部 try-catch 降级，Redis 异常不影响主链路（设计 6.2 工程要点）。
 * 读失败返回 null（直查数据库），写失败仅告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheHelper {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public <T> T get(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isEmpty()) return null;
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("redis cache read degraded, key={}: {}", key, e.getMessage());
            return null;
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("redis cache write degraded, key={}: {}", key, e.getMessage());
        }
    }

    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("redis cache evict degraded, key={}: {}", key, e.getMessage());
        }
    }

    /** 按前缀批量失效（keys 扫描仅适合开发规模数据量）。 */
    public void evictByPrefix(String prefix) {
        try {
            java.util.Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("redis cache evict-by-prefix degraded, prefix={}: {}", prefix, e.getMessage());
        }
    }

    /** SETNX 分布式锁，Redis 异常时放行（降级为单机语义）。 */
    public boolean tryLock(String key, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
        } catch (Exception e) {
            log.warn("redis lock degraded, key={}: {}", key, e.getMessage());
            return true;
        }
    }

    public void unlock(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("redis unlock degraded, key={}: {}", key, e.getMessage());
        }
    }
}
