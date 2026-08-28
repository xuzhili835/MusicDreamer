package com.musicdreamer.media.job;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.musicdreamer.common.api.Mess;
import com.musicdreamer.media.client.MusicClient;
import com.musicdreamer.media.entity.MediaTask;
import com.musicdreamer.media.exec.ExecResult;
import com.musicdreamer.media.exec.ProcessExecutor;
import com.musicdreamer.media.service.NetArgs;
import com.musicdreamer.media.service.SettingsService;
import com.musicdreamer.media.service.StorageService;
import com.musicdreamer.media.service.TaskManager;
import com.musicdreamer.media.tools.ToolsLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 响度分析（EBU R128）+ AI 转写 + 字幕下载（设计 10.6/10.7）。 */
@Slf4j
@Component
public class MediaJobs {

    private static final Pattern LUFS_MAIN = Pattern.compile(
            "Summary:[\\s\\S]*?Integrated loudness:[\\s\\S]*?I:\\s+(-?\\d+\\.?\\d*)\\s+LUFS");
    private static final Pattern LUFS_FALLBACK = Pattern.compile("I:\\s+(-?\\d+\\.?\\d*)\\s+LUFS");
    private static final Pattern WHISPER_PROGRESS = Pattern.compile("progress\\s*=\\s*(\\d+)%");
    // whisper.cpp 默认只吃 4 线程：按逻辑核数给（上限 8 防超订阅回退），多核机器提速明显
    private static final int WHISPER_THREADS = Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));

    private final TaskManager tasks;
    private final ProcessExecutor executor;
    private final ToolsLocator tools;
    private final StorageService storage;
    private final SettingsService settings;
    private final MusicClient music;
    private final NetArgs netArgs;

    public MediaJobs(TaskManager tasks, ProcessExecutor executor, ToolsLocator tools,
                     StorageService storage, SettingsService settings, MusicClient music,
                     NetArgs netArgs) {
        this.tasks = tasks;
        this.executor = executor;
        this.tools = tools;
        this.storage = storage;
        this.settings = settings;
        this.music = music;
        this.netArgs = netArgs;
        tasks.runnerLocator().register(TaskManager.LOUDNESS, this::loudness);
        tasks.runnerLocator().register(TaskManager.TRANSCRIBE, this::transcribe);
        tasks.runnerLocator().register(TaskManager.SUBTITLE, this::subtitle);
        tasks.runnerLocator().register(TaskManager.LYRICS_FETCH, this::lyricsFetch);
    }

    /**
     * 一键获取歌词：在线歌词库（LRCLIB）优先 → 命中同步歌词则逐句校准到本地
     * 演唱时刻（B站搬运版常有剪辑/偏移）；命中纯文本则用本地识别拆句加时间轴
     * （文本用在线的、拆句与时间用本地的）；未命中则本地 AI 转写。
     */
    public void lyricsFetch(MediaTask task) {
        Long songId = task.getSongId();
        Map<String, Object> song = fetchSong(songId);
        String title = String.valueOf(song.getOrDefault("name", ""));
        String artist = String.valueOf(song.getOrDefault("singerName", ""));
        double duration = song.get("duration") instanceof Number n ? n.doubleValue() : 0;

        // 1) 在线匹配（网络失败静默降级到本地识别）
        tasks.progress(task.getId(), 10, "正在匹配在线歌词库…");
        LrcSupport.OnlineMatch m = null;
        try {
            m = LrcSupport.matchOnlineLyrics(title, artist, duration);
        } catch (Exception e) {
            log.warn("lrclib match degraded: {}", e.getMessage());
        }

        try {
            if (m != null) {
                // 词库记录 id 写进文件头并回写 song 表（bug8：复盘时间戳对照）
                writeBackLyricSource(song, m);
            }
            if (m != null && m.syncedLyrics() != null) {
                tasks.progress(task.getId(), 25, "已命中在线歌词，正在本地校准时间轴…");
                List<LrcSupport.LrcLine> lrc = LrcSupport.parseLrc(m.syncedLyrics());
                List<LrcSupport.LrcLine> wav = transcribeForAlign(task, song);
                LyricAligner.Aligned aligned = wav == null ? null
                        : LyricAligner.computeAlignedTimes(lrc, wav, duration > 0 ? duration : null);
                String content;
                String note;
                if (aligned != null) {
                    content = LyricAligner.writeAlignedLyrics(m.syncedLyrics(), aligned.times(), aligned.median());
                    note = String.format("已逐句校准 %d/%d 句（整体偏移 %.1fs）", aligned.pairs(), lrc.size(), aligned.median());
                } else {
                    content = m.syncedLyrics();
                    note = wav == null ? "本地校准不可用，保留在线时间轴" : "本地校准未完成，保留在线时间轴";
                }
                saveLyric(song, lrclibHeader(m) + content);
                tasks.finish(task.getId(), "SUCCESS", note + "（" + m.trackName() + "）");
                return;
            }

            if (m != null && m.plainLyrics() != null) {
                tasks.progress(task.getId(), 25, "在线歌词无时间轴，正在用本地识别添加…");
                // bug64：本地转写可能耗时数分钟，没有中间进度用户会以为卡死
                tasks.progress(task.getId(), 35, "本地AI预转写中（较长歌曲请耐心等待）…");
                List<LrcSupport.LrcLine> plain = LrcSupport.parseLrc(m.plainLyrics());
                List<LrcSupport.LrcLine> wav = transcribeForAlign(task, song);
                if (wav != null) {
                    LyricAligner.Merged merged = LyricAligner.mergePlainWithWav(plain, wav);
                    int need = Math.max(3, (int) Math.ceil(plain.size() * 0.25));
                    if (merged.matched() >= need) {
                        String splitNote = merged.lines().size() > plain.size()
                                ? String.format("，已按演唱拆分为 %d 行", merged.lines().size()) : "";
                        saveLyric(song, lrclibHeader(m) + LyricAligner.writeMergedLyrics(merged.lines()));
                        tasks.finish(task.getId(), "SUCCESS",
                                String.format("已用本地识别添加时间轴（%d/%d 句对齐%s）（%s）",
                                        merged.matched(), plain.size(), splitNote, m.trackName()));
                        return;
                    }
                    // 对不上：宁可保留纯文本，不写错误时间轴
                    saveLyric(song, lrclibHeader(m) + m.plainLyrics());
                    tasks.finish(task.getId(), "SUCCESS",
                            String.format("在线歌词与本地识别对不上（%d/%d 句），保留纯文本（%s）",
                                    merged.matched(), plain.size(), m.trackName()));
                    return;
                }
                saveLyric(song, lrclibHeader(m) + m.plainLyrics());
                tasks.finish(task.getId(), "SUCCESS", "已保存在线纯文本歌词（本地识别不可用，无时间轴）（" + m.trackName() + "）");
                return;
            }

            // 1.5) 词库无命中 → 先试来源视频的 CC 字幕（合并后的歌词链路：词库 → 字幕 → 转写）
            String sourceUrl = task.getSourceUrl();
            if (sourceUrl != null && !sourceUrl.isBlank() && trySubtitleAsLyrics(task, song, sourceUrl)) {
                return;
            }

            // 2) 字幕也没有 → 本地 AI 转写
            tasks.progress(task.getId(), 25, "在线无歌词，本地 AI 识别中…");
            transcribe(task);
        } catch (IllegalStateException e) {
            // 歌曲不存在或 Feign 调用失败
            tasks.finish(task.getId(), "FAILED", "歌曲信息获取失败：" + e.getMessage());
            log.error("lyricsFetch failed for songId={}: {}", task.getSongId(), e.getMessage(), e);
        } catch (Exception e) {
            tasks.finish(task.getId(), "FAILED", "获取歌词异常：" + e.getMessage());
            log.error("lyricsFetch exception for songId={}: {}", task.getSongId(), e.getMessage(), e);
        }
    }

    /** 为对齐/加时间轴跑一次快速整首转写，返回解析后的歌词行；失败返回 null。 */
    private List<LrcSupport.LrcLine> transcribeForAlign(MediaTask task, Map<String, Object> song) {
        Long songId = task.getSongId();
        String ffmpeg = tools.locate(ToolsLocator.FFMPEG);
        String whisper = tools.locate(ToolsLocator.WHISPER);
        Path model = findModel(settings.whisperModel());
        if (whisper == null || model == null) return null;
        Path mp3 = storage.localPath((String) song.get("fileUrl"));
        if (mp3 == null || !Files.isRegularFile(mp3)) return null;
        Path taskDir = storage.dir("task");
        Path wav = taskDir.resolve(task.getId() + "_align.wav");
        Path outPrefix = taskDir.resolve(task.getId() + "_align");
        try {
            // 清掉可能残留的上次产物，防止 whisper 失败时读到陈旧 LRC
            Files.deleteIfExists(taskDir.resolve(task.getId() + "_align.lrc"));
            ExecResult pre = executor.run(List.of(ffmpeg, "-y", "-i", mp3.toString(),
                            "-ar", "16000", "-ac", "1", wav.toString()),
                    5 * 60_000L, null, TaskManager.cancelKey(task.getId())).get();
            if (pre.getExitCode() != 0) return null;
            // 对齐用的快转写走贪心解码（时间戳不受影响，文本只要够模糊匹配即可），
            // 并显式给线程数——whisper.cpp 默认只吃 4 线程，多核机器白白闲置
            // （束搜索版整首要数分钟，贪心+8 线程可压到几十秒）
            ExecResult tr = executor.run(List.of(whisper, "-m", model.toString(), "-f", wav.toString(),
                            "-l", "auto", "-tp", "0", "-ml", "40",
                            "-t", String.valueOf(WHISPER_THREADS),
                            "-olrc", "-of", outPrefix.toString(), "-pp"),
                    30 * 60_000L,
                    line -> {
                        Matcher pm = WHISPER_PROGRESS.matcher(line);
                        if (pm.find()) {
                            tasks.progress(task.getId(),
                                    25 + Integer.parseInt(pm.group(1)) * 6 / 10, "本地快转写中… " + pm.group(1) + "%");
                        }
                    }, TaskManager.cancelKey(task.getId())).get();
            if (tr == null || tr.getExitCode() != 0) return null;
            Path lrc = taskDir.resolve(task.getId() + "_align.lrc");
            if (!Files.isRegularFile(lrc)) return null;
            return LrcSupport.parseLrc(Files.readString(lrc));
        } catch (Exception e) {
            log.warn("align transcribe degraded: {}", e.getMessage());
            return null;
        } finally {
            try {
                Files.deleteIfExists(wav);
                Files.deleteIfExists(taskDir.resolve(task.getId() + "_align.lrc"));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 歌词落盘与回写。文件命名（用户可见目录，拒绝纯数字名）：
     * 1) 歌曲已有 lyricUrl → 沿用原路径重取覆盖（路径稳定，不改名）；
     * 2) 否则用 歌名.lrc；磁盘上已有同名（他人同名歌）则 歌名-songId.lrc 兜底。
     */
    private Path resolveLyricFile(Map<String, Object> song) {
        Long songId = ((Number) song.get("id")).longValue();
        Object existing = song.get("lyricUrl");
        if (existing != null && String.valueOf(existing).endsWith(".lrc")) {
            Path p = storage.localPath(String.valueOf(existing));
            if (p != null && p.startsWith(storage.dir("lyric"))) {
                return p;
            }
        }
        String base = sanitizeFileName(String.valueOf(song.getOrDefault("name", "")));
        if (base.isEmpty()) {
            base = "未命名";
        }
        Path named = storage.dir("lyric").resolve(base + ".lrc");
        if (Files.isRegularFile(named)) {
            named = storage.dir("lyric").resolve(base + "-" + songId + ".lrc");
        }
        return named;
    }

    /** 文件名清洗：去 Windows 非法字符，结尾不得为点/空格，限长防路径超限。
     *  包内共享——DownloadJob 的音频/封面命名与歌词同一套规范。 */
    static String sanitizeFileName(String s) {
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
        cleaned = cleaned.replaceAll("[.。\\s]+$", "");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private void saveLyric(Map<String, Object> song, String content) throws Exception {
        Path finalLrc = resolveLyricFile(song);
        Files.writeString(finalLrc, content == null ? "" : toSimple(content));
        writeBackLyric(song, finalLrc);
    }

    /** 歌词繁→简（bug19）：LRCLIB/whisper/平台字幕都可能出繁体，落盘前统一转简体。
     *  时间戳与 [source:] 等标签是 ASCII，转换天然无感；转换异常原样返回不阻断。 */
    static String toSimple(String lrc) {
        if (lrc == null || lrc.isEmpty()) {
            return lrc;
        }
        try {
            return ZhConverterUtil.toSimple(lrc);
        } catch (Exception e) {
            return lrc;
        }
    }

    /** 歌词文件保存（转写产物落正式目录，繁→简后写入）并回写 song.lyric_url。 */
    private void saveLyricFrom(Map<String, Object> song, Path sourceLrc) throws Exception {
        Path finalLrc = resolveLyricFile(song);
        Files.writeString(finalLrc, toSimple(Files.readString(sourceLrc)));
        Files.deleteIfExists(sourceLrc);
        writeBackLyric(song, finalLrc);
    }

    /** 回写 song.lyric_url：Feign 瞬断重试 3 次——曾因服务重启窗口一次失败导致
     *  文件已落盘但数据库指针丢失（歌曲 1/2 歌词"看不见"事故）。 */
    private void writeBackLyric(Map<String, Object> song, Path finalLrc) {
        Long songId = ((Number) song.get("id")).longValue();
        String url = storage.fileUrl("lyric", finalLrc.getFileName().toString());
        for (int i = 0; i < 3; i++) {
            try {
                music.updateLyric(Map.of("songId", songId, "lyricUrl", url));
                return;
            } catch (Exception e) {
                log.warn("updateLyric 重试 {}/3：{}", i + 1, e.getMessage());
                try {
                    Thread.sleep(500L * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("updateLyric 三次均失败，songId={} lyricUrl={}（文件已落盘，可重取歌词修复指针）", songId, url);
    }

    /** LRCLIB 文件头：来源标记 + 词库记录 id（无 id 时只有来源行）。 */
    private static String lrclibHeader(LrcSupport.OnlineMatch m) {
        String h = "[source:lrclib]\n";
        return m.id() == null ? h : h + "[lrclib:" + m.id() + "]\n";
    }

    /** 词库命中信息回写 song.lyric_source_id/url（bug8，失败不阻断歌词主流程）。 */
    private void writeBackLyricSource(Map<String, Object> song, LrcSupport.OnlineMatch m) {
        if (m == null || m.id() == null) {
            return;
        }
        try {
            music.updateLyricSource(Map.of(
                    "songId", ((Number) song.get("id")).longValue(),
                    "sourceId", m.id(),
                    "sourceUrl", "https://lrclib.net/api/get/" + m.id()));
        } catch (Exception e) {
            log.warn("updateLyricSource 失败（不影响歌词落盘）: {}", e.getMessage());
        }
    }

    /** 词库无命中时尝试来源视频 CC 字幕当歌词（bug7 合并链路：词库 → 字幕 → 转写）。
     *  拿到字幕并落盘返回 true；无字幕/任何失败返回 false 交给下一级。 */
    private boolean trySubtitleAsLyrics(MediaTask task, Map<String, Object> song, String url) {
        String ytdlp = tools.locate(ToolsLocator.YT_DLP);
        if (ytdlp == null) {
            return false;
        }
        try {
            tasks.progress(task.getId(), 20, "在线无歌词，正在尝试视频字幕…");
            Path taskDir = storage.dir("task");
            String out = taskDir.resolve(task.getId() + "_sub").toString();
            List<String> cmd = new ArrayList<>(List.of(ytdlp, "--write-subs", "--skip-download",
                    "--sub-lang", "zh-Hans,zh,en", "-o", out));
            cmd.addAll(netArgs.build(url));
            cmd.add(url);
            ExecResult r = executor.run(cmd, 5 * 60_000L, null, TaskManager.cancelKey(task.getId())).join();
            if (r.getExitCode() != 0) {
                return false;
            }
            Path srt = findSubtitle(taskDir, task.getId());
            if (srt == null) {
                return false;
            }
            String lrcText = SrtToLrc.convert(srt);
            Files.deleteIfExists(srt);
            if (lrcText == null || lrcText.isBlank()) {
                return false;
            }
            saveLyric(song, "[source:subtitle]\n" + lrcText);
            tasks.finish(task.getId(), "SUCCESS", "词库无命中，已用视频字幕作歌词");
            return true;
        } catch (Exception e) {
            log.warn("subtitle-as-lyrics degraded: {}", e.getMessage());
            return false;
        }
    }

    /** 响度分析：ffmpeg ebur128 结果在 stderr（约束三），增益=目标-实测，回写 song 表。 */
    public void loudness(MediaTask task) {
        Long songId = task.getSongId();
        String ffmpeg = tools.locate(ToolsLocator.FFMPEG);
        Map<String, Object> song = fetchSong(songId);
        Path file = storage.localPath((String) song.get("fileUrl"));
        if (ffmpeg == null || file == null || !Files.isRegularFile(file)) {
            tasks.finish(task.getId(), "FAILED", "音频文件或 ffmpeg 不可用");
            return;
        }
        tasks.progress(task.getId(), 20, "正在分析响度…");
        ExecResult r = executor.run(List.of(ffmpeg, "-i", file.toString(),
                        "-filter_complex", "ebur128", "-f", "null", "-"),
                10 * 60_000L, null, TaskManager.cancelKey(task.getId())).join();
        if (r.getExitCode() != 0) {
            tasks.finish(task.getId(), "FAILED", "响度分析失败：" + brief(r.getStderr()));
            return;
        }
        Double integrated = parseLufs(r.getStderr());
        if (integrated == null) {
            tasks.finish(task.getId(), "FAILED", "未能解析响度结果");
            return;
        }
        double gain = settings.volumeTargetLufs() - integrated;
        tasks.progress(task.getId(), 80, "正在回写增益 " + String.format("%.1f", gain) + " dB…");
        call(() -> music.updateLoudness(Map.of("songId", songId, "gain", gain, "integrated", integrated)));
        tasks.finish(task.getId(), "SUCCESS", null);
    }

    /** AI 转写：16kHz 单声道 WAV -> whisper LRC -> 回写歌词；模型缺失走降级提示（F042-1）。 */
    public void transcribe(MediaTask task) {
        Long songId = task.getSongId();
        String ffmpeg = tools.locate(ToolsLocator.FFMPEG);
        String whisper = tools.locate(ToolsLocator.WHISPER);
        Path model = findModel(settings.whisperModel());
        if (whisper == null || model == null) {
            tasks.finish(task.getId(), "FAILED", "请先在管理端-模型管理中下载 whisper 模型并安装 whisper-cli");
            return;
        }
        Map<String, Object> song = fetchSong(songId);
        Path mp3 = storage.localPath((String) song.get("fileUrl"));
        if (mp3 == null || !Files.isRegularFile(mp3)) {
            tasks.finish(task.getId(), "FAILED", "音频文件不可用");
            return;
        }
        Path taskDir = storage.dir("task");
        Path wav = taskDir.resolve(task.getId() + "_temp.wav");
        try {
            tasks.progress(task.getId(), 10, "预处理音频…");
            ExecResult pre = executor.run(List.of(ffmpeg, "-y", "-i", mp3.toString(),
                            "-ar", "16000", "-ac", "1", wav.toString()),
                    5 * 60_000L, null, TaskManager.cancelKey(task.getId())).get();
            if (pre.getExitCode() != 0) {
                tasks.finish(task.getId(), "FAILED", "音频预处理失败");
                return;
            }
            Path outPrefix = taskDir.resolve(String.valueOf(task.getId()));
            tasks.progress(task.getId(), 20, "AI 识别中…");
            ExecResult tr = executor.run(List.of(whisper,
                            "-m", model.toString(), "-f", wav.toString(),
                            "-l", "auto", "-t", String.valueOf(WHISPER_THREADS),
                            "-olrc", "-of", outPrefix.toString(), "-pp"),
                    30 * 60_000L,
                    line -> {
                        Matcher m = WHISPER_PROGRESS.matcher(line);
                        if (m.find()) {
                            tasks.progress(task.getId(), 20 + Integer.parseInt(m.group(1)) * 7 / 10,
                                    "AI 识别中… " + m.group(1) + "%");
                        }
                    }, TaskManager.cancelKey(task.getId())).get();
            if (tr == null || tr.getExitCode() != 0) {
                // getNow(null) 时代留下的隐患：tr 为 null 时不能再取 stderr；executor
                // 超时也会完成 future（非 null），null 仅作防御
                tasks.finish(task.getId(), "FAILED", "转写失败：" + brief(tr == null ? null : tr.getStderr()));
                return;
            }
            Path lrc = taskDir.resolve(task.getId() + ".lrc");
            if (!Files.isRegularFile(lrc)) {
                tasks.finish(task.getId(), "FAILED", "未生成歌词产物");
                return;
            }
            Path finalLrc = resolveLyricFile(song);
            Files.move(lrc, finalLrc, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tasks.progress(task.getId(), 90, "回写歌词…");
            writeBackLyric(song, finalLrc);
            tasks.finish(task.getId(), "SUCCESS", null);
        } catch (Exception e) {
            tasks.finish(task.getId(), "FAILED", "转写异常：" + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(wav);
                Files.deleteIfExists(taskDir.resolve(task.getId() + ".lrc"));
            } catch (Exception ignored) {
            }
        }
    }

    /** 字幕下载：仅拉字幕轨；无字幕静默成功（不算失败）。 */
    public void subtitle(MediaTask task) {
        String url = task.getSourceUrl();
        String ytdlp = tools.locate(ToolsLocator.YT_DLP);
        if (ytdlp == null || url == null) {
            tasks.finish(task.getId(), "FAILED", "yt-dlp 不可用或来源缺失");
            return;
        }
        Path taskDir = storage.dir("task");
        String out = taskDir.resolve(task.getId() + "_sub").toString();
        tasks.progress(task.getId(), 30, "正在下载字幕…");
        List<String> cmd = new ArrayList<>(List.of(ytdlp, "--write-subs", "--skip-download",
                "--sub-lang", "zh-Hans,zh,en", "-o", out));
        cmd.addAll(netArgs.build(url));
        // 此前漏加视频地址：yt-dlp 收到一堆参数却没有 URL，直接打印 usage 报错
        cmd.add(url);
        ExecResult r = executor.run(cmd, 5 * 60_000L, null, TaskManager.cancelKey(task.getId())).join();
        if (r.getExitCode() != 0) {
            tasks.finish(task.getId(), "FAILED", "字幕下载失败：" + brief(r.getStderr()));
            return;
        }
        Path srt = findSubtitle(taskDir, task.getId());
        if (srt == null) {
            tasks.finish(task.getId(), "SUCCESS", null); // 无字幕：静默成功
            return;
        }
        String lrcText = SrtToLrc.convert(srt);
        try {
            // 落盘与歌词统一命名（{歌名}.lrc + 撞名兜底 + 回写重试）
            if (task.getSongId() != null) {
                saveLyric(fetchSong(task.getSongId()),
                        "[source:subtitle]\n" + (lrcText == null ? "" : lrcText));
            }
            tasks.finish(task.getId(), "SUCCESS", null);
        } catch (Exception e) {
            tasks.finish(task.getId(), "FAILED", "字幕入库失败：" + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(srt);
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- 私有 ----------

    private Map<String, Object> fetchSong(Long songId) {
        if (songId == null) {
            throw new IllegalStateException("任务缺少 songId");
        }
        Mess m = music.findById(songId);
        if (m != null && m.isOk() && m.getData() instanceof Map<?, ?> raw) {
            Map<String, Object> song = new java.util.HashMap<>();
            raw.forEach((k, v) -> song.put(String.valueOf(k), v));
            return song;
        }
        throw new IllegalStateException("歌曲不存在（id=" + songId + "）");
    }

    private Path findModel(String spec) {
        for (String name : new String[]{"ggml-" + spec + ".bin", "ggml-" + spec + ".q5_1.bin"}) {
            Path p = storage.modelsDir().resolve(name);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private Path findSubtitle(Path dir, int taskId) {
        for (String suffix : new String[]{".zh-Hans.srt", ".zh.srt", ".en.srt", ".srt",
                ".zh-Hans.vtt", ".zh.vtt", ".vtt"}) {
            Path p = dir.resolve(taskId + "_sub" + suffix);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        try (var stream = Files.newDirectoryStream(dir, taskId + "_sub*")) {
            for (Path p : stream) {
                return p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Double parseLufs(String stderr) {
        Matcher m = LUFS_MAIN.matcher(stderr);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        m = LUFS_FALLBACK.matcher(stderr);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return null;
    }

    private void call(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("feign callback degraded: {}", e.getMessage());
        }
    }

    private String brief(String s) {
        if (s == null) return "";
        String line = s.lines().filter(l -> !l.isBlank()).reduce((a, b) -> b).orElse("");
        return line.length() > 120 ? line.substring(0, 120) : line;
    }
}
