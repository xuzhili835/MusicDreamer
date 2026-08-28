package com.musicdreamer.media.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicdreamer.common.api.Mess;
import com.musicdreamer.media.client.MusicClient;
import com.musicdreamer.media.entity.MediaTask;
import com.musicdreamer.media.entity.SongRequest;
import com.musicdreamer.media.exec.ExecResult;
import com.musicdreamer.media.exec.ProcessExecutor;
import com.musicdreamer.media.mapper.SongRequestMapper;
import com.musicdreamer.media.service.FingerprintService;
import com.musicdreamer.media.service.NetArgs;
import com.musicdreamer.media.service.SettingsService;
import com.musicdreamer.media.service.StorageService;
import com.musicdreamer.media.service.TaskManager;
import com.musicdreamer.media.tools.ToolsLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 下载管线（设计 10.4 图 10-3）：元数据 -> 下载(0-60) -> 转码(60-75) -> 元数据落地
 * -> Feign 入库（失败回 PENDING 重试）-> 后置任务（响度/字幕/转写）。
 * B 站 412 两层防护：保持 yt-dlp 最新 + 自动 buvid cookie（缓存 1 小时）。
 */
@Slf4j
@Component
public class DownloadJob implements TaskManager.TaskRunner {

    private static final Pattern PROGRESS = Pattern.compile("\\[download\\]\\s+(\\d+(?:\\.\\d+)?)%");
    private static final Pattern BILIBILI = Pattern.compile(
            "(bilibili\\.com|m\\.bilibili\\.com|b23\\.tv|(^|/)(BV[0-9A-Za-z]{10})|(^|/)av\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE = Pattern.compile(
            "(youtube\\.com|youtu\\.be|music\\.youtube\\.com)", Pattern.CASE_INSENSITIVE);
    // 求歌入库标记：SongRequestController 在 source_url 追加 reqid=申请ID
    private static final Pattern REQID = Pattern.compile("[?&]reqid=(\\d+)");

    private final TaskManager tasks;
    private final ProcessExecutor executor;
    private final ToolsLocator tools;
    private final SettingsService settings;
    private final StorageService storage;
    private final MusicClient music;
    private final SongRequestMapper requestMapper;
    private final FingerprintService fingerprints;
    private final ObjectMapper json = new ObjectMapper();
    private volatile String buvidFile;
    private volatile long buvidAt = 0;

    private final NetArgs netArgs;

    public DownloadJob(TaskManager tasks, ProcessExecutor executor, ToolsLocator tools,
                       SettingsService settings, StorageService storage, MusicClient music,
                       NetArgs netArgs, SongRequestMapper requestMapper,
                       FingerprintService fingerprints) {
        this.tasks = tasks;
        this.executor = executor;
        this.tools = tools;
        this.settings = settings;
        this.storage = storage;
        this.music = music;
        this.netArgs = netArgs;
        this.requestMapper = requestMapper;
        this.fingerprints = fingerprints;
        tasks.runnerLocator().register(TaskManager.DOWNLOAD, this);
    }

    @Override
    public void run(MediaTask task) {
        String url = task.getSourceUrl();
        // 控制器把导入选项写进 URL 标记（mdlyric=1 / style=xx；wantSubtitle=1 为旧标记，
        // 仅兼容部署前已在队列的任务）。入库查重与歌曲展示要干净链接，先剥掉标记再往下传
        boolean wantSubtitle = url.contains("wantSubtitle=1") || url.contains("#sub");
        boolean wantLyrics = url.contains("mdlyric=1");
        String style = extractStyle(url);
        String customTitle = extractMarker(url, "mtitle"); // bug16：用户自定义歌名
        Long sgid = extractSgid(url); // bug80：求歌入库时归属到同名歌手账号
        String cleanUrl = url.replaceAll("[?&](wantSubtitle=1|mdlyric=1|reqid=\\d+|sgid=\\d+|style=[^&#]*|mtitle=[^&#]*)", "");
        Path taskDir = storage.dir("task");
        String ytdlp = tools.locate(ToolsLocator.YT_DLP);
        String ffmpeg = tools.locate(ToolsLocator.FFMPEG);
        if (ytdlp == null || ffmpeg == null) {
            tasks.finish(task.getId(), "FAILED", "媒体工具未就绪，请先运行 node scripts/fetch-tools.js");
            return;
        }
        try {
            // ① 元数据（30 秒，412 重试一次）；标记参数不发给站点
            tasks.progress(task.getId(), 1, "正在获取视频元数据…");
            JsonNode meta = dumpJson(ytdlp, cleanUrl);
            if (meta == null) {
                tasks.finish(task.getId(), "FAILED", translate("元数据获取失败"));
                return;
            }
            String title = (customTitle != null && !customTitle.isBlank())
                    ? customTitle // 用户指定歌名（bug16）：跳过智能裁剪
                    : smartTitle(textOr(meta, "title", "导入歌曲"));
            long duration = meta.path("duration").asLong(0);
            // ② 下载（进度 0-60）
            tasks.progress(task.getId(), 5, "正在下载音频…");
            String outTpl = taskDir.resolve(task.getId() + "_temp.%(ext)s").toString();
            List<String> cmd = new ArrayList<>(List.of(ytdlp,
                    "-f", "bestaudio/best", "--extract-audio", "--audio-format", "best",
                    "--write-thumbnail", "--convert-thumbnails", "jpg",
                    "--newline", "--no-playlist",
                    "-o", outTpl));
            cmd.addAll(netArgs.build(cleanUrl));
            cmd.add(cleanUrl);
            ExecResult dl = executor.run(cmd, 20 * 60_000L,
                    line -> {
                        Matcher m = PROGRESS.matcher(line);
                        if (m.find()) {
                            int pct = (int) Double.parseDouble(m.group(1));
                            tasks.progress(task.getId(), (int) (pct * 0.6), "正在下载音频… " + pct + "%");
                        }
                    }, TaskManager.cancelKey(task.getId())).get();
            if (dl.getExitCode() != 0) {
                tasks.finish(task.getId(), "FAILED", translate(dl.getStderr() + dl.getStdout()));
                return;
            }
            Path tempFile = findTemp(taskDir, task.getId());
            if (tempFile == null) {
                tasks.finish(task.getId(), "FAILED", "下载产物未找到");
                return;
            }

            // ③ 转码 MP3 320k（进度 60-75，按时间线性）
            tasks.progress(task.getId(), 60, "正在转码 MP3…");
            Path mp3 = taskDir.resolve(task.getId() + "_out.mp3");
            ExecResult tr = executor.run(List.of(ffmpeg, "-y", "-i", tempFile.toString(),
                            "-codec:a", "libmp3lame", "-b:a", "320k", mp3.toString()),
                    10 * 60_000L, null, TaskManager.cancelKey(task.getId())).get();
            Files.deleteIfExists(tempFile);
            if (tr.getExitCode() != 0) {
                tasks.finish(task.getId(), "FAILED", "转码失败：" + translate(tr.getStderr()));
                return;
            }

            // ④ 封面：由 yt-dlp 与音频一起下载，复用 B 站 cookies、代理和反风控参数。
            tasks.progress(task.getId(), 76, "正在保存封面…");
            Path cover = findCover(taskDir, task.getId());
            String coverUrl = cover == null ? null : storage.fileUrl("task", cover.getFileName().toString());

            // ⑤ Feign 入库（status=1 审核中；失败回 PENDING 重试 ≤3 次）
            tasks.progress(task.getId(), 80, "正在写入歌曲库…");
            Map<String, Object> song = new HashMap<>();
            song.put("name", title);
            // bug80：求歌入库带 sgid 标记时归属到同名歌手，否则归操作者（管理员/上传歌手本人）
            song.put("singerId", sgid != null ? sgid : task.getOperator());
            song.put("duration", duration);
            if (style != null && !style.isBlank()) {
                song.put("style", style);
            }
            song.put("fileUrl", storage.fileUrl("task", task.getId() + "_out.mp3"));
            // 临时目录会在任务结束时清理，不能把临时封面地址作为歌曲最终数据。
            song.put("coverUrl", null);
            song.put("fileFormat", "MP3");
            song.put("sourceUrl", cleanUrl);
            Mess created = null;
            try {
                created = music.create(song);
            } catch (Exception e) {
                log.warn("create song feign failed: {}", e.getMessage());
            }
            if (created == null || !created.isOk()) {
                cleanupTemp(task.getId());
                int retry = parseRetry(task.getStage());
                tasks.retryLater(task.getId(), retry + 1);
                return;
            }
            Long songId = asLong(created.getData());
            if (songId == null) {
                tasks.finish(task.getId(), "FAILED", "入库返回异常");
                return;
            }
            // 正式落位：task 目录 -> music/image 目录。命名与歌词同一规范（用户可见
            // 目录可读）：{清洗后的歌名}.ext；同名但属于不同歌曲时兜底加 -songId，
            // 避免互相覆盖造成音频/封面在歌曲间串位
            String stem = MediaJobs.sanitizeFileName(title);
            if (stem.isEmpty()) stem = "未命名";
            Path finalMp3 = storage.dir("music").resolve(stem + ".mp3");
            if (Files.exists(finalMp3)) {
                finalMp3 = storage.dir("music").resolve(stem + "-" + songId + ".mp3");
            }
            Files.move(mp3, finalMp3, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Path finalCover = null;
            if (coverUrl != null) {
                String coverName = stem + ".jpg";
                if (Files.exists(storage.dir("image").resolve(coverName))) {
                    coverName = stem + "-" + songId + ".jpg";
                }
                finalCover = storage.dir("image").resolve(coverName);
                Files.move(cover, finalCover, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            // 正式地址回写：入库时的 fileUrl 指向 task 临时文件，清理后失效，必须换成最终路径
            Map<String, Object> fileBody = new HashMap<>();
            fileBody.put("songId", songId);
            fileBody.put("fileUrl", storage.fileUrl("music", finalMp3.getFileName().toString()));
            if (finalCover != null) {
                fileBody.put("coverUrl", storage.fileUrl("image", finalCover.getFileName().toString()));
            }
            Mess updated = music.updateFile(fileBody);
            if (updated == null || !updated.isOk()) {
                throw new IllegalStateException("歌曲文件地址回写失败");
            }
            tasks.attachSong(task.getId(), songId);
            fulfillRequest(task, songId, url);
            // 听歌识曲指纹：入库即生成（失败不阻断，管理后台可重建补齐）
            fingerprints.ingestQuiet(songId, finalMp3);
            tasks.progress(task.getId(), 85, "入库完成（待审核），执行后置任务…");

            // ⑥ 后置：响度必做；歌词按导入开关（bug7 合并后只有"自动获取歌词"一条链：
            //    LRCLIB 词库 → 视频 CC 字幕 → 本地 AI 转写，cleanUrl 随任务传递供字幕级用）。
            //    wantSubtitle 单独分支仅兼容部署前已在队列的旧任务。
            tasks.submit(TaskManager.LOUDNESS, null, songId, task.getOperator(), "响度分析排队");
            if (wantLyrics) {
                tasks.submit(TaskManager.LYRICS_FETCH, cleanUrl, songId,
                        task.getOperator(), "自动获取歌词排队");
            } else if (wantSubtitle) {
                tasks.submit(TaskManager.SUBTITLE, cleanUrl, songId,
                        task.getOperator(), "字幕下载排队");
            }
            tasks.finish(task.getId(), "SUCCESS", null);
        } catch (Exception e) {
            log.error("download task {} error", task.getId(), e);
            tasks.finish(task.getId(), "FAILED", "任务异常：" + e.getMessage());
        } finally {
            cleanupTemp(task.getId());
        }
    }

    /** 元数据解析（412 风控重试一次）；供下载主流程与导入预览（bug74）共用。 */
    public JsonNode dumpJson(String ytdlp, String url) throws Exception {
        for (int i = 0; i < 2; i++) {
            List<String> cmd = new ArrayList<>(List.of(ytdlp, "--dump-single-json", "--no-playlist"));
            cmd.addAll(netArgs.build(url));
            cmd.add(url);
            ExecResult r = executor.run(cmd, 30_000, null, "dump-" + url.hashCode()).get();
            if (r.getExitCode() == 0 && !r.getStdout().isBlank()) {
                return json.readTree(r.getStdout());
            }
            if (!r.getStderr().contains("412")) {
                return null;
            }
        }
        return null;
    }

    /** 从 URL 标记里取风格（控制器写入 style=xx，URL 编码）。 */
    private static String extractStyle(String url) {
        return extractMarker(url, "style");
    }

    /** bug80：从 URL 标记里取归属歌手 id（控制器写入 sgid=<userId>）。 */
    private static Long extractSgid(String url) {
        Matcher m = Pattern.compile("[?&]sgid=(\\d+)").matcher(url);
        try {
            return m.find() ? Long.parseLong(m.group(1)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从 URL 标记里取任意参数值（URL 编码写入，容错解码）。 */
    private static String extractMarker(String url, String name) {
        Matcher m = Pattern.compile("[?&]" + name + "=([^&#]+)").matcher(url);
        if (!m.find()) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return m.group(1);
        }
    }

    /** 歌名智能裁剪（bug9）：B 站视频标题常带"【4K修复】xxx 无损音质"等修饰，
     *  直接入库会又长又难看。规则：取《》里的歌名；否则去掉【】修饰与站点后缀；
     *  空白收敛；40 字封顶；裁空回落原标题。
     *  bug74：再加一道"散落修饰词"过滤——括号之外的 完整版/官方/MV/4K/无损 等
     *  独立词元也去掉（只删整词匹配，不会误伤正常歌名）。 */
    public static String smartTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String t = raw.trim();
        // 1) 书名号优先：标题里明确标了歌名
        Matcher bk = Pattern.compile("《([^《》]{1,40})》").matcher(t);
        if (bk.find()) {
            return bk.group(1).trim();
        }
        // 2) 去各类括号修饰段（【4K】(Live)[MV]「完整」等）
        t = t.replaceAll("[【\\[（(「『]([^【\\]）)」』]*)[】\\]）)」』]", " ");
        // 3) 去站点后缀
        t = t.replaceAll("(?i)[_\\s|-]*bilibili.*$", "")
                .replaceAll("[｜|]\\s*哔哩哔哩.*$", "")
                .replaceAll("[_\\s|-]*哔哩哔哩.*$", "");
        // 4) bug74：散落修饰词整词过滤（按分隔符切词，只删完全匹配的噪声词）
        t = java.util.Arrays.stream(t.split("[\\s/｜|·,，、\\-－—]+"))
                .filter(tok -> !NOISE_TOKEN.matcher(tok).matches())
                .collect(java.util.stream.Collectors.joining(" "));
        // 5) 空白收敛、去首尾连接符
        t = t.replaceAll("\\s+", " ").trim()
                .replaceAll("^[\\s\\-－—_·.]+|[\\s\\-－—_·.]+$", "");
        if (t.isEmpty()) {
            return raw.trim();
        }
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    /** bug74：作为独立词元出现的修饰词（整词匹配才删）：画质/音质/版本/官方标记等。 */
    private static final Pattern NOISE_TOKEN = Pattern.compile(
            "(?i)(mv|pv|av|official\\s*video|official|music\\s*video|lyrics?\\s*video|lyrics?"
                    + "|audio|audio\\s*only|纯音乐|纯享版?|完整版?|正式版|官方|高清|无损|母带|hi-?res|杜比全景声|杜比"
                    + "|4k|2k|1080p|720p|60帧|修复版?|重制版?|remaster(ed)?|restored|超清|蓝光|音质|视频|试听|伴奏|cover|live)");

    /** 错误转译表（设计 10.5，原始 stderr 绝不给用户）。 */
    private String translate(String raw) {
        String s = raw == null ? "" : raw;
        if (s.contains("412") || s.contains("Precondition")) {
            return "站点风控拦截，请稍后重试；反复出现请配置代理";
        }
        if (s.contains("403")) {
            return "站点拒绝访问，可能需要登录（请在设置中配置 cookies 文件）";
        }
        if (s.contains("timeout") || s.contains("超时") || s.contains("timed out")) {
            return "网络超时，请检查网络或代理设置";
        }
        if (s.contains("Video unavailable")) {
            return "视频不存在或已删除";
        }
        if (s.contains("Private video")) {
            return "视频为私有，无法下载";
        }
        String oneLine = s.lines().filter(l -> !l.isBlank()).reduce((a, b) -> b).orElse("下载失败");
        return "下载失败：" + (oneLine.length() > 120 ? oneLine.substring(0, 120) : oneLine);
    }

    private Path findTemp(Path dir, int taskId) {
        try (var stream = Files.newDirectoryStream(dir, taskId + "_temp*")) {
            for (Path p : stream) {
                String name = p.getFileName().toString().toLowerCase();
                if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png")
                        && !name.endsWith(".webp")) {
                    return p;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Path findCover(Path dir, int taskId) {
        try (var stream = Files.newDirectoryStream(dir, taskId + "_temp*")) {
            for (Path p : stream) {
                String name = p.getFileName().toString().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    return p;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void cleanupTemp(int taskId) {
        try (var stream = Files.newDirectoryStream(storage.dir("task"), taskId + "_*")) {
            for (Path p : stream) {
                if (p.getFileName().toString().contains("_temp") || p.toString().endsWith("_out.mp3")
                        || p.toString().endsWith("_cover.jpg")) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private int parseRetry(String stage) {
        if (stage == null) return 0;
        Matcher m = Pattern.compile("第 (\\d+) 次").matcher(stage);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** 求歌回填：管理员从求歌页发起的下载带 reqid 标记，入库成功即自动完成申请。 */
    private void fulfillRequest(MediaTask task, Long songId, String url) {
        Matcher m = REQID.matcher(url);
        if (!m.find()) {
            return;
        }
        try {
            SongRequest req = requestMapper.selectById(Long.parseLong(m.group(1)));
            if (req == null || req.getStatus() == null || req.getStatus() != 0) {
                return;
            }
            req.setStatus(1);
            req.setResultSongId(songId);
            req.setHandledBy(task.getOperator());
            req.setHandledAt(LocalDateTime.now());
            requestMapper.updateById(req);
        } catch (Exception e) {
            // 回填失败不影响下载主流程；管理员可用手动回填兜底
            log.warn("song request fulfill failed for task {}: {}", task.getId(), e.getMessage());
        }
    }

    private String textOr(JsonNode node, String field, String def) {
        String v = node.path(field).asText(null);
        return v == null || v.isBlank() ? def : v;
    }

    private Long asLong(Object data) {
        if (data instanceof Number n) return n.longValue();
        if (data instanceof Map<?, ?> m && m.get("songId") instanceof Number n) return n.longValue();
        if (data instanceof Map<?, ?> m && m.get("id") instanceof Number n) return n.longValue();
        try {
            return data == null ? null : Long.parseLong(String.valueOf(data));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
