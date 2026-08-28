package com.musicdreamer.media.client;

import com.musicdreamer.common.api.Mess;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/** Mod_music 内部接口（Feign，绕过网关）。 */
@FeignClient(name = "musicService8002", path = "/api/v1/song/internal",
        fallback = MusicClient.MusicClientFallback.class)
public interface MusicClient {

    @GetMapping("/{id}")
    Mess findById(@org.springframework.web.bind.annotation.PathVariable("id") Long id);

    @GetMapping("/by-source")
    Mess findBySource(@RequestParam("url") String url);

    @PostMapping("/create")
    Mess create(@RequestBody Map<String, Object> song);

    @PostMapping("/loudness")
    Mess updateLoudness(@RequestBody Map<String, Object> body);

    @PostMapping("/lyric")
    Mess updateLyric(@RequestBody Map<String, Object> body);

    /** 在线词库命中信息回写（LRCLIB id/链接，复盘时间戳用）。 */
    @PostMapping("/lyric-source")
    Mess updateLyricSource(@RequestBody Map<String, Object> body);

    /** 音频/封面正式地址回写（下载落位 music/{id}.mp3 后）。 */
    @PostMapping("/file")
    Mess updateFile(@RequestBody Map<String, Object> body);

    class MusicClientFallback implements MusicClient {
        @Override public Mess findById(Long id) { return null; }
        @Override public Mess findBySource(String url) { return null; }
        @Override public Mess create(Map<String, Object> song) { return null; }
        @Override public Mess updateLoudness(Map<String, Object> body) { return null; }
        @Override public Mess updateLyric(Map<String, Object> body) { return null; }
        @Override public Mess updateLyricSource(Map<String, Object> body) { return null; }
        @Override public Mess updateFile(Map<String, Object> body) { return null; }
    }
}
