package com.musicdreamer.music.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.music.dto.InternalCreateDTO;
import com.musicdreamer.music.service.SongService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 内部接口：/api/v1/song/internal/**（仅供微服务间 Feign 调用，绕过网关直连 8002）。
 * 调用方：Mod_media 下载入库/响度回写/歌词回写，Mod_like/Mod_songList 批量取歌。
 * 安全说明：端口仅在内网（Docker 网络本机）暴露，外部流量经网关时该路径要求登录。
 */
@RestController
@RequestMapping("/api/v1/song/internal")
public class InternalController {

    private final SongService songService;

    public InternalController(SongService songService) {
        this.songService = songService;
    }

    /** 按 ID 取歌曲（Feign）。 */
    @GetMapping("/{id}")
    public Mess findById(@PathVariable("id") Long id) {
        return Mess.ok(songService.internalGet(id));
    }

    /** 按来源 URL 查重（Feign，下载去重用）。 */
    @GetMapping("/by-source")
    public Mess findBySource(@RequestParam("url") String url) {
        return Mess.ok(songService.internalBySource(url));
    }

    /** 批量取歌曲（Feign，歌单/收藏聚合用）。 */
    @PostMapping("/batch")
    public Mess batch(@RequestBody List<Long> ids) {
        return Mess.ok(songService.internalBatch(ids));
    }

    /** 下载完成建歌（Feign，Mod_media DownloadJob 第⑤步）。 */
    @PostMapping("/create")
    public Mess create(@RequestBody InternalCreateDTO dto) {
        return Mess.ok(songService.internalCreate(dto));
    }

    /** 收藏计数增减（Feign，Mod_like）。 */
    @PostMapping("/collect-count")
    public Mess collectCount(@RequestBody Map<String, Object> body) {
        Long songId = asLong(body.get("songId"));
        int delta = asInt(body.get("delta"));
        songService.internalCollectCount(songId, delta);
        return Mess.ok();
    }

    /** 响度分析结果回写（Feign，Mod_media LOUDNESS 任务）。 */
    @PostMapping("/loudness")
    public Mess loudness(@RequestBody Map<String, Object> body) {
        Long songId = asLong(body.get("songId"));
        songService.internalLoudness(songId, asDouble(body.get("gain")), asDouble(body.get("integrated")));
        return Mess.ok();
    }

    /** 音频/封面正式地址回写（Feign，下载落位后 DownloadJob 调用）。 */
    @PostMapping("/file")
    public Mess file(@RequestBody Map<String, Object> body) {
        songService.internalFile(asLong(body.get("songId")),
                (String) body.get("fileUrl"), (String) body.get("coverUrl"));
        return Mess.ok();
    }

    /** 歌词地址回写（Feign，字幕/转写任务产出后）。 */
    @PostMapping("/lyric")
    public Mess lyric(@RequestBody Map<String, Object> body) {
        Long songId = asLong(body.get("songId"));
        String lyricUrl = (String) body.get("lyricUrl");
        songService.internalLyric(songId, lyricUrl);
        return Mess.ok();
    }

    /** 在线词库命中信息回写（Feign，LRCLIB 命中后记录 id/链接，复盘时间戳用）。 */
    @PostMapping("/lyric-source")
    public Mess lyricSource(@RequestBody Map<String, Object> body) {
        songService.internalLyricSource(asLong(body.get("songId")),
                asLong(body.get("sourceId")), (String) body.get("sourceUrl"));
        return Mess.ok();
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static int asInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
