package com.musicdreamer.songlist.service;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.songlist.client.SongClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 音乐服务（musicService8002）远程访问封装。
 * 统一契约：Feign 调用失败或 code!=0 抛 1001 音乐服务暂不可用；
 * 歌曲不存在或未发布（status!=2）抛 3001。
 * 响应体 Mess 的 data 为 LinkedHashMap。
 */
@Slf4j
@Service
public class SongRemoteService {

    private final SongClient songClient;

    public SongRemoteService(SongClient songClient) {
        this.songClient = songClient;
    }

    /**
     * 校验歌曲存在且已发布（status=2），返回歌曲数据 Map。
     * 不存在/未发布 → 3001 “歌曲不存在或未发布”。
     */
    public Map<String, Object> requirePublishedSong(Long songId) {
        Mess resp = call(() -> songClient.findById(songId));
        // 音乐服务明确回了“歌曲不存在” → 透传业务语义
        if (resp != null && resp.getCode() == 3001) {
            throw new BizException(3001, "歌曲不存在或未发布");
        }
        checkAvailable(resp);
        Map<String, Object> data = asMap(resp.getData());
        if (data == null || toLong(data.get("id")) == null || toInt(data.get("status")) != 2) {
            throw new BizException(3001, "歌曲不存在或未发布");
        }
        return data;
    }

    /**
     * 批量查歌曲详情，返回以 id 为键的 Map；查不到的歌曲不在结果中。
     * 服务不可用抛 1001。
     */
    public Map<Long, Map<String, Object>> batchSongs(Collection<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(songIds));
        Mess resp = call(() -> songClient.findByIds(ids));
        checkAvailable(resp);
        return parseBatch(resp.getData());
    }

    // ---------- 内部工具 ----------

    private interface Invocation {
        Mess invoke();
    }

    private Mess call(Invocation invocation) {
        try {
            return invocation.invoke();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Feign 调用 musicService8002 失败: {}", e.getMessage());
            throw new BizException(1001, "音乐服务暂不可用");
        }
    }

    private void checkAvailable(Mess resp) {
        if (resp == null || resp.getCode() != 0) {
            throw new BizException(1001, "音乐服务暂不可用");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<String, Object>> parseBatch(Object data) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        if (data instanceof Collection) {
            for (Object o : (Collection<Object>) data) {
                Map<String, Object> m = asMap(o);
                Long id = m == null ? null : toLong(m.get("id"));
                if (id != null) {
                    result.put(id, m);
                }
            }
        } else {
            // 兼容单个对象或以 id 为键的 Map 返回
            Map<String, Object> m = asMap(data);
            if (m != null) {
                if (toLong(m.get("id")) != null) {
                    result.put(toLong(m.get("id")), m);
                } else {
                    for (Object o : m.values()) {
                        Map<String, Object> sm = asMap(o);
                        Long id = sm == null ? null : toLong(sm.get("id"));
                        if (id != null) {
                            result.put(id, sm);
                        }
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return null;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.valueOf(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object o) {
        Long v = toLong(o);
        return v == null ? null : v.intValue();
    }

    /** 取字符串字段，兼容 null。 */
    public String str(Map<String, Object> song, String key) {
        if (song == null) return null;
        Object v = song.get(key);
        return v == null ? null : v.toString();
    }

    /** 取 Long 字段（如 singerId），兼容 null。 */
    public Long lng(Map<String, Object> song, String key) {
        return song == null ? null : toLong(song.get(key));
    }

    /** 取整型字段（如 duration），兼容 null。 */
    public Integer integer(Map<String, Object> song, String key) {
        return song == null ? null : toInt(song.get(key));
    }

    /** 歌手名：优先 singerName，其次 singer/nickname。 */
    public String singerName(Map<String, Object> song) {
        String v = str(song, "singerName");
        if (v == null) v = str(song, "singer");
        if (v == null) v = str(song, "nickname");
        return v;
    }
}
