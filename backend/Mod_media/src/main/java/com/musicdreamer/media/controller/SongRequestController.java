package com.musicdreamer.media.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.media.entity.MediaTask;
import com.musicdreamer.media.entity.SongRequest;
import com.musicdreamer.media.exec.ExecResult;
import com.musicdreamer.media.exec.ProcessExecutor;
import com.musicdreamer.media.mapper.CrossReadMapper;
import com.musicdreamer.media.mapper.SongRequestMapper;
import com.musicdreamer.media.service.NetArgs;
import com.musicdreamer.media.service.TaskManager;
import com.musicdreamer.media.tools.ToolsLocator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 求歌申请 + bilisearch 候选搜索（听歌识曲一期，设计文档 4.5/4.6）。
 * 权限不变式：求歌提交是"读"人人可用；候选搜索与下载入库只发生在管理员手里。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class SongRequestController {

    /** 求歌入库只接受 B 站链接（候选即来自 bilisearch） */
    private static final Pattern BILI_URL = Pattern.compile(
            "(bilibili\\.com|b23\\.tv|BV[0-9A-Za-z]{10})", Pattern.CASE_INSENSITIVE);

    private final SongRequestMapper requests;
    private final TaskManager tasks;
    private final ToolsLocator tools;
    private final ProcessExecutor executor;
    private final NetArgs netArgs;
    private final CrossReadMapper crossRead;
    private final ObjectMapper json = new ObjectMapper();

    // ---------- 用户侧 ----------

    @PostMapping("/requests")
    public Mess submit(@RequestBody RequestDTO dto) {
        AuthContext.requireLogin();
        String title = trimToNull(dto.getTitle());
        if (title == null) {
            return Mess.fail(5003, "歌名不能为空");
        }
        if (title.length() > 200 || over(dto.getArtist(), 200) || over(dto.getCoverUrl(), 500)) {
            return Mess.fail(5003, "内容过长");
        }
        // 同一首歌的待处理申请不重复堆积
        Long uid = AuthContext.getUserId();
        Long dup = requests.selectCount(new LambdaQueryWrapper<SongRequest>()
                .eq(SongRequest::getUserId, uid)
                .eq(SongRequest::getTitle, title)
                .eq(SongRequest::getStatus, 0));
        if (dup != null && dup > 0) {
            return Mess.fail(5003, "这首歌已在你的待处理求歌里");
        }
        SongRequest r = new SongRequest();
        r.setUserId(uid);
        r.setTitle(title);
        r.setArtist(trimToNull(dto.getArtist()));
        r.setCoverUrl(trimToNull(dto.getCoverUrl()));
        r.setSource(dto.getSource() == null ? 0 : Math.max(0, Math.min(2, dto.getSource())));
        r.setStatus(0);
        r.setCreatedAt(LocalDateTime.now());
        requests.insert(r);
        return Mess.ok(Map.of("id", r.getId()));
    }

    @GetMapping("/requests/mine")
    public Mess mine() {
        AuthContext.requireLogin();
        List<SongRequest> list = requests.selectList(new LambdaQueryWrapper<SongRequest>()
                .eq(SongRequest::getUserId, AuthContext.getUserId())
                .orderByDesc(SongRequest::getId)
                .last("limit 50"));
        return Mess.ok(list);
    }

    // ---------- 管理员侧 ----------

    @GetMapping("/requests")
    public Mess list(@RequestParam(required = false) Integer status) {
        AuthContext.requireAdmin();
        LambdaQueryWrapper<SongRequest> qw = new LambdaQueryWrapper<SongRequest>()
                .orderByAsc(SongRequest::getStatus)   // 待处理在前
                .orderByDesc(SongRequest::getId)
                .last("limit 200");
        qw.eq(status != null, SongRequest::getStatus, status);
        return Mess.ok(requests.selectList(qw));
    }

    @PutMapping("/requests/{id}/reject")
    public Mess reject(@PathVariable Long id, @RequestBody RequestDTO dto) {
        AuthContext.requireAdmin();
        SongRequest r = requests.selectById(id);
        if (r == null) {
            return Mess.fail(5003, "申请不存在");
        }
        if (r.getStatus() != 0) {
            return Mess.fail(5003, "该申请已处理");
        }
        r.setStatus(2);
        r.setRejectReason(trimToNull(dto.getReason()));
        r.setHandledBy(AuthContext.getUserId());
        r.setHandledAt(LocalDateTime.now());
        requests.updateById(r);
        return Mess.ok(null);
    }

    /** 手动回填（下载自动回填失败时的兜底）：把已入库的歌曲 ID 关联到申请。 */
    @PutMapping("/requests/{id}/fulfill")
    public Mess fulfill(@PathVariable Long id, @RequestBody RequestDTO dto) {
        AuthContext.requireAdmin();
        if (dto.getSongId() == null) {
            return Mess.fail(5003, "缺少歌曲ID");
        }
        SongRequest r = requests.selectById(id);
        if (r == null) {
            return Mess.fail(5003, "申请不存在");
        }
        if (r.getStatus() != 0) {
            return Mess.fail(5003, "该申请已处理");
        }
        r.setStatus(1);
        r.setResultSongId(dto.getSongId());
        r.setHandledBy(AuthContext.getUserId());
        r.setHandledAt(LocalDateTime.now());
        requests.updateById(r);
        return Mess.ok(null);
    }

    /**
     * 求歌入库：候选视频 URL → 下载任务。source_url 追加 reqid 标记，
     * DownloadJob 成功入库后据此自动回填申请状态（沿用 wantSubtitle 的 URL 标记约定）。
     * bug85：本地曲库已有同名已发布歌曲时直接完成求歌，不再下载；
     * bug80：填了歌手的求歌，入库归属到同名歌手账号（没有则提示前端可一键创建）。
     */
    @PostMapping("/requests/{id}/download")
    public Mess download(@PathVariable Long id, @RequestBody RequestDTO dto) {
        AuthContext.requireAdmin();
        SongRequest r = requests.selectById(id);
        if (r == null) {
            return Mess.fail(5003, "申请不存在");
        }
        if (r.getStatus() != 0) {
            return Mess.fail(5003, "该申请已处理");
        }
        String url = trimToNull(dto.getUrl());
        if (url == null || !BILI_URL.matcher(url).find()) {
            return Mess.fail(5003, "不支持的链接，请从候选列表选择");
        }
        // bug85：本地命中——直接关联已有歌曲并完成申请
        if (r.getTitle() != null && !r.getTitle().isBlank()) {
            Map<String, Object> local = crossRead.findPublishedSongByName(r.getTitle().trim());
            if (local != null && local.get("id") instanceof Number n) {
                r.setStatus(1);
                r.setResultSongId(n.longValue());
                r.setHandledBy(AuthContext.getUserId());
                r.setHandledAt(LocalDateTime.now());
                requests.updateById(r);
                return Mess.ok(Map.of("localSongId", n.longValue()));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (r.getArtist() != null && !r.getArtist().isBlank()) {
            Long sgid = crossRead.findSingerIdByNickname(r.getArtist().trim());
            if (sgid != null) {
                url += (url.contains("?") ? "&" : "?") + "sgid=" + sgid;
            } else {
                // 歌手账号不存在：不建任务，让前端引导管理员一键创建后重试
                data.put("missingSinger", r.getArtist().trim());
                return Mess.ok(data);
            }
        }
        url += (url.contains("?") ? "&" : "?") + "reqid=" + id;
        MediaTask t = tasks.submit(TaskManager.DOWNLOAD, url, null,
                AuthContext.getUserId(), "排队中…");
        data.put("taskId", t.getId());
        return Mess.ok(data);
    }

    /** bug80：删除求歌记录（管理员清理误提/测试记录；已处理的历史记录同样可清）。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/requests/{id}")
    public Mess remove(@PathVariable Long id) {
        AuthContext.requireAdmin();
        requests.deleteById(id);
        return Mess.ok(null);
    }

    /**
     * bilisearch 候选搜索：yt-dlp 解析搜索前 5 条的完整元数据。
     * 不用 --flat-playlist——B 站搜索的 flat 条目只有 id/url，没有标题/
     * 时长/封面，撑不起候选卡片；全解析实测约 4~8 秒。
     * B 站请求头/buvid cookies 走 NetArgs 统一注入（裸跑会被风控拦成空结果）。
     */
    @GetMapping("/recognize/search")
    public Mess search(@RequestParam String kw) {
        AuthContext.requireAdmin();
        String k = trimToNull(kw);
        if (k == null) {
            return Mess.fail(5003, "关键词不能为空");
        }
        if (k.length() > 60) {
            k = k.substring(0, 60);
        }
        String ytdlp = tools.locate(ToolsLocator.YT_DLP);
        if (ytdlp == null) {
            return Mess.fail(5003, "yt-dlp 未安装");
        }
        List<String> cmd = new ArrayList<>(List.of(ytdlp, "-J", "--ignore-errors", "--playlist-end", "5",
                "bilisearch5:" + k));
        cmd.addAll(netArgs.build("https://www.bilibili.com"));
        ExecResult r = executor.run(cmd, 45_000L, null, null).join();
        // 解析先行（P1-5）：单条坏结果（如 B 站课程页 extractor 不支持）只跳过该条，
        // 不再拖挂整个搜索；仅当"退出码非零且一条都没解析到"才判失败
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode entries = json.readTree(r.getStdout()).path("entries");
            if (entries.isArray()) {
                for (JsonNode e : entries) {
                    if (out.size() >= 5) {
                        break;
                    }
                    String vid = e.path("id").asText("");
                    String url = firstNonBlank(
                            e.path("webpage_url").asText(null),
                            e.path("url").asText(null),
                            vid.startsWith("BV") ? "https://www.bilibili.com/video/" + vid : null);
                    String title = e.path("title").asText(null);
                    if (url == null || title == null) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("url", url);
                    item.put("title", title);
                    double dur = e.path("duration").asDouble(0);
                    item.put("duration", dur);
                    // bug85：超长视频大概率是合集/串烧（不是一首歌），标出来让管理员避开
                    item.put("tooLong", dur > 600);
                    item.put("uploader", e.path("uploader").asText(""));
                    item.put("cover", firstNonBlank(
                            e.path("thumbnail").asText(null),
                            thumbnailOf(e)));
                    out.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("bilisearch parse failed: {}", e.getMessage());
        }
        if (out.isEmpty()) {
            if (r.getExitCode() != 0) {
                log.warn("bilisearch failed: {}", brief(r.getStderr()));
                return Mess.fail(5003, "搜索失败：" + brief(r.getStderr()));
            }
        } else if (r.getExitCode() != 0) {
            log.info("bilisearch exit={} but {} candidates parsed, bad entries skipped",
                    r.getExitCode(), out.size());
        }
        return Mess.ok(out);
    }

    // ---------- 私有 ----------

    private static String thumbnailOf(JsonNode e) {
        JsonNode ts = e.path("thumbnails");
        if (ts.isArray() && ts.size() > 0) {
            return ts.get(ts.size() - 1).path("url").asText(null);
        }
        return null;
    }

    private static String firstNonBlank(String... vs) {
        for (String v : vs) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean over(String s, int max) {
        return s != null && s.length() > max;
    }

    private static String brief(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }

    @Data
    public static class RequestDTO {
        private String title;
        private String artist;
        private String coverUrl;
        private Integer source;
        private String reason;
        private String url;
        private Long songId;
    }
}
