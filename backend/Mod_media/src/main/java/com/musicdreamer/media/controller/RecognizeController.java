package com.musicdreamer.media.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.media.client.MusicClient;
import com.musicdreamer.media.service.AcrService;
import com.musicdreamer.media.service.FingerprintService;
import com.musicdreamer.media.service.StorageService;
import com.musicdreamer.media.tools.ToolsLocator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 听歌识曲（设计文档二期+三期）：收一段录音，本库 landmark 指纹匹配；
 * 本地 MISS 且是最后一轮探测（last=1）时动用 ACRCloud 外置识别兜底——
 * 歌名先回查本地曲库（命中直接返回本地歌），没有才交给前端预填求歌。
 * 所有登录用户可用；指纹库的管理（重建/状态）仅管理员。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class RecognizeController {

    private final FingerprintService fingerprints;
    private final AcrService acr;
    private final ToolsLocator tools;
    private final StorageService storage;
    private final MusicClient music;

    @PostMapping("/recognize")
    public Mess recognize(@RequestParam("file") MultipartFile file,
                          @RequestParam(value = "source", required = false, defaultValue = "file") String source,
                          @RequestParam(value = "last", required = false, defaultValue = "false") boolean last) {
        AuthContext.requireLogin();
        if (file == null || file.isEmpty()) {
            return Mess.fail(5003, "缺少音频片段");
        }
        if (file.getSize() > 20_000_000L) {
            return Mess.fail(5003, "片段过大（限 20MB）");
        }
        if (tools.locate(ToolsLocator.FFMPEG) == null) {
            return Mess.fail(5003, "识别组件（ffmpeg）未安装，请联系管理员");
        }
        Path temp = storage.dir("task").resolve("recog_" + System.nanoTime() + ".bin");
        try {
            file.transferTo(temp);
            FingerprintService.Match m = fingerprints.match(temp);
            if (m != null) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("matched", true);
                data.put("level", m.level());
                data.put("confidence", Math.round(m.ratio() * 1000) / 10.0);
                data.put("votes", m.votes());
                data.put("offsetSec", Math.round(m.offsetSec() * 10) / 10.0);
                data.put("song", songVo(m.songId()));
                return Mess.ok(data);
            }
            // 本地 MISS：只有最后一轮探测（last）才动用外置识别，省第三方额度
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("matched", false);
            data.put("level", "MISS");
            if (last && acr.enabled()) {
                AcrService.AcrHit hit = acr.identifyQuiet(temp);
                if (hit != null) {
                    Map<String, Object> local = acr.matchLocal(hit.title(), hit.artist());
                    if (local != null) {
                        // 第三方认出的歌本地就有：直接返回本地歌（可立即播放）
                        data.put("matched", true);
                        data.put("level", "HIT");
                        data.put("via", "external");
                        data.put("offsetSec", Math.round(hit.offsetSec() * 10) / 10.0);
                        data.put("external", externalMap(hit));
                        data.put("song", songVoFromRow(local));
                        return Mess.ok(data);
                    }
                    // 本地没有：返回歌名，前端预填求歌
                    data.put("external", externalMap(hit));
                }
            }
            return Mess.ok(data);
        } catch (IllegalStateException e) {
            return Mess.fail(5003, e.getMessage());
        } catch (Exception e) {
            log.warn("recognize failed: {}", e.getMessage());
            return Mess.fail(5003, "识别失败，请重试");
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
        }
    }

    @GetMapping("/fingerprints/status")
    public Mess status() {
        AuthContext.requireAdmin();
        Map<String, Object> m = new LinkedHashMap<>(fingerprints.status());
        m.put("external", acr.status());
        return Mess.ok(m);
    }

    @PostMapping("/fingerprints/rebuild")
    public Mess rebuild() {
        AuthContext.requireAdmin();
        if (!fingerprints.startRebuild()) {
            return Mess.fail(5003, "重建已在进行中");
        }
        return Mess.ok(Map.of("started", true));
    }

    private Map<String, Object> externalMap(AcrService.AcrHit hit) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("title", hit.title());
        e.put("artist", hit.artist());
        e.put("album", hit.album());
        return e;
    }

    /** 识别结果只带展示需要的字段。 */
    private Map<String, Object> songVo(long songId) {
        Mess m = music.findById(songId);
        if (m != null && m.isOk() && m.getData() instanceof Map<?, ?> raw) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", songId);
            vo.put("name", String.valueOf(raw.get("name")));
            Object singer = raw.get("singerName");
            vo.put("singerName", singer == null ? "" : String.valueOf(singer));
            vo.put("coverUrl", raw.get("coverUrl"));
            vo.put("duration", raw.get("duration"));
            return vo;
        }
        throw new IllegalStateException("歌曲信息获取失败");
    }

    /** 外置识别回查本地命中：直接用查到的行组装 VO（不再绕 Feign）。 */
    private Map<String, Object> songVoFromRow(Map<String, Object> row) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", row.get("songId"));
        vo.put("name", row.get("name"));
        Object singer = row.get("singerName");
        vo.put("singerName", singer == null ? "" : String.valueOf(singer));
        vo.put("coverUrl", row.get("coverUrl"));
        vo.put("duration", row.get("duration"));
        return vo;
    }
}
