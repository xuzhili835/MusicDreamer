package com.musicdreamer.like.service;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.like.client.SongClient;
import com.musicdreamer.like.dto.CollectCountDTO;
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
 * batch 用于收藏列表拼装：调用失败或 code!=0 抛 1001 音乐服务暂不可用；
 * collect-count 维护冗余计数：失败仅记日志，不阻塞收藏主流程。
 */
@Slf4j
@Service
public class SongRemoteService {

    private final SongClient songClient;

    public SongRemoteService(SongClient songClient) {
        this.songClient = songClient;
    }

    /** 批量查歌曲详情，返回以 id 为键的 Map；查不到的歌曲不在结果中。 */
    public Map<Long, Map<String, Object>> batchSongs(Collection<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(songIds));
        Mess resp;
        try {
            resp = songClient.findByIds(ids);
        } catch (Exception e) {
            log.error("Feign 调用 musicService8002 失败: {}", e.getMessage());
            throw new BizException(1001, "音乐服务暂不可用");
        }
        if (resp == null || resp.getCode() != 0) {
            throw new BizException(1001, "音乐服务暂不可用");
        }
        return parseBatch(resp.getData());
    }

    /** 通知音乐服务增减 song.collect_count 冗余计数；失败仅日志。 */
    public void notifyCollectCount(Long songId, int delta) {
        try {
            Mess resp = songClient.updateCollectCount(new CollectCountDTO(songId, delta));
            if (resp == null || resp.getCode() != 0) {
                log.warn("collect-count 更新未成功: songId={}, delta={}, resp={}",
                        songId, delta, resp == null ? "null" : resp.getCode() + "/" + resp.getMessage());
            }
        } catch (Exception e) {
            log.error("collect-count Feign 调用失败(不阻塞主流程): songId={}, delta={}, err={}",
                    songId, delta, e.getMessage());
        }
    }

    // ---------- 内部工具 ----------

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

    /** 取字符串字段。 */
    public String str(Map<String, Object> song, String key) {
        if (song == null) return null;
        Object v = song.get(key);
        return v == null ? null : v.toString();
    }

    /** 取 Long 字段（如 singerId）。 */
    public Long lng(Map<String, Object> song, String key) {
        return song == null ? null : toLong(song.get(key));
    }

    /** 取整型字段（如 duration）。 */
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
