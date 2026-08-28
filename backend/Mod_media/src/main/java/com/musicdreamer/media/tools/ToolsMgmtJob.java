package com.musicdreamer.media.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicdreamer.media.entity.MediaTask;
import com.musicdreamer.media.exec.ProcessExecutor;
import com.musicdreamer.media.service.StorageService;
import com.musicdreamer.media.service.TaskManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 工具/模型管理任务：TOOL_INSTALL（通用安装/更新，读 tools.manifest.json，
 * 全部装到 tools/bin/）、TOOL_UPDATE（yt-dlp 快速更新，保留）、MODEL_DOWNLOAD。
 * 可靠性口径：.part 流式 + 体积校验 + 原子替换 + 完成后清探测缓存。
 */
@Slf4j
@Component
public class ToolsMgmtJob {

    private final TaskManager tasks;
    private final ProcessExecutor executor;
    private final ToolsLocator toolsLocator;
    private final StorageService storage;
    private final ToolsStatusCache statusCache;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15)).build();

    public ToolsMgmtJob(TaskManager tasks, ProcessExecutor executor,
                        ToolsLocator toolsLocator, StorageService storage,
                        ToolsStatusCache statusCache) {
        this.tasks = tasks;
        this.executor = executor;
        this.toolsLocator = toolsLocator;
        this.storage = storage;
        this.statusCache = statusCache;
        tasks.runnerLocator().register(TaskManager.TOOL_UPDATE, this::toolUpdate);
        tasks.runnerLocator().register(TaskManager.MODEL_DOWNLOAD, this::modelDownload);
        tasks.runnerLocator().register(TaskManager.TOOL_INSTALL, this::toolInstall);
    }

    /**
     * 通用工具安装/更新：按 tools.manifest.json 拉取（GitHub 直链 + ghproxy
     * 镜像回退），zip 包按 binary 清单挑文件解到 tools/bin/，单文件原子替换。
     * ffprobe 归属 ffmpeg 包。完成后清 locate/状态缓存，管理页即时反映。
     */
    public void toolInstall(MediaTask task) {
        String tool = task.getStage() == null ? "" : task.getStage().replace("安装工具 ", "");
        // 逻辑名 -> manifest 名（ffprobe 随 ffmpeg 包分发）
        String manifestName = switch (tool) {
            case ToolsLocator.FFMPEG, ToolsLocator.FFPROBE -> "ffmpeg";
            case ToolsLocator.WHISPER -> "whisper-cli";
            default -> tool;
        };
        try {
            Path toolsDir = toolsDir();
            Map<String, Object> entry = manifestTool(toolsDir, manifestName);
            if (entry == null) {
                tasks.finish(task.getId(), "FAILED", "manifest 中没有工具 " + manifestName);
                return;
            }
            boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
            @SuppressWarnings("unchecked")
            Map<String, Object> plat = (Map<String, Object>) entry.get(win ? "win" : "linux");
            if (plat == null) {
                tasks.finish(task.getId(), "FAILED", "manifest 缺少 " + (win ? "win" : "linux") + " 配置（Linux 请走容器内 apt）");
                return;
            }
            String url = String.valueOf(plat.get("url"));
            long minBytes = ((Number) plat.getOrDefault("minBytes", 1_000_000)).longValue();
            // 源列表：直链 -> 显式 mirror -> github 代理镜像
            List<String> urls = new ArrayList<>(List.of(url));
            if (plat.get("mirror") != null) urls.add(1, String.valueOf(plat.get("mirror")));
            for (String p : new String[]{"https://ghproxy.net/", "https://gh-proxy.com/", "https://ghproxy.com/"}) {
                if (url.contains("github.com") && !url.contains("ghproxy")) urls.add(p + url);
            }

            boolean unzip = Boolean.TRUE.equals(plat.get("unzip"));
            Path binDir = toolsDir.resolve("bin");
            Files.createDirectories(binDir);
            Path dest = binDir.resolve("." + manifestName + "-install" + (unzip ? ".zip" : "") + ".part");

            tasks.progress(task.getId(), 5, "正在下载 " + manifestName + "…");
            if (!downloadAny(urls, dest, minBytes, task.getId(), 5, 70)) {
                tasks.finish(task.getId(), "FAILED", "下载失败（直链与镜像均不可用）");
                return;
            }
            if (unzip) {
                tasks.progress(task.getId(), 75, "解压安装到 tools/bin/…");
                List<String> binary = (List<String>) plat.getOrDefault("binary", List.of());
                unzipPick(dest, binDir, binary);
                Files.deleteIfExists(dest);
                if (binary.stream().anyMatch(b -> !Files.isRegularFile(binDir.resolve(b)))) {
                    tasks.finish(task.getId(), "FAILED", "压缩包中未找到全部预期文件");
                    return;
                }
            } else {
                String target = String.valueOf(plat.getOrDefault("target", "bin/" + manifestName + ".exe"));
                Path out = toolsDir.resolve(target);
                Files.createDirectories(out.getParent());
                Files.move(dest, out, StandardCopyOption.REPLACE_EXISTING);
            }
            toolsLocator.invalidate();
            statusCache.invalidate();
            String version = toolsLocator.execVersion(
                    toolsLocator.locate(tool), toolsLocator.probeArgs(tool));
            String note = version == null ? "已安装（版本探测待重启进程后生效）"
                    : "已安装：" + version.split("\n")[0];
            tasks.progress(task.getId(), 100, note);
            tasks.finish(task.getId(), "SUCCESS", note);
        } catch (Exception e) {
            log.warn("tool install {} failed", tool, e);
            tasks.finish(task.getId(), "FAILED", "安装失败：" + e.getMessage());
        }
    }

    /** 读 tools.manifest.json（与 tools/ 同目录），返回 tools[] 里 name 匹配的条目。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> manifestTool(Path toolsDir, String name) throws Exception {
        for (Path p : new Path[]{
                toolsDir.getParent().resolve("tools.manifest.json"),
                Paths.get("tools.manifest.json"),
                Paths.get("../tools.manifest.json")}) {
            if (!Files.isRegularFile(p)) continue;
            Map<String, Object> root = json.readValue(p.toFile(), Map.class);
            for (Object o : (List<Object>) root.get("tools")) {
                Map<String, Object> m = (Map<String, Object>) o;
                if (name.equals(m.get("name"))) return m;
            }
            return null;
        }
        return null;
    }

    /** 从 zip 中按文件名（取条目路径最后一段匹配）挑出 binary 清单内的文件解到 bin/。 */
    private void unzipPick(Path zip, Path binDir, List<String> binary) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String base = e.getName().replace('\\', '/');
                base = base.substring(base.lastIndexOf('/') + 1);
                if (!binary.contains(base)) continue;
                Path out = binDir.resolve(base);
                Files.deleteIfExists(out);
                try (var os = Files.newOutputStream(out)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) os.write(buf, 0, n);
                }
            }
        }
    }

    /** yt-dlp 自动更新：下载 .part（失败切镜像）-> >5MB 校验 -> 原子替换 -> --version 验证（失败回滚）。 */
    public void toolUpdate(MediaTask task) {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        String asset = win ? "yt-dlp.exe" : "yt-dlp_linux";
        String base = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + asset;
        List<String> urls = List.of(base, "https://ghproxy.com/" + base);
        Path toolsDir = toolsDir();
        Path target = toolsDir.resolve(win ? "yt-dlp.exe" : "yt-dlp");
        try {
            tasks.progress(task.getId(), 10, "正在下载最新 yt-dlp…");
            if (!downloadAny(urls, toolsDir.resolve(asset + ".new.part"), 5_000_000, task.getId(), 10, 80)) {
                tasks.finish(task.getId(), "FAILED", "下载失败（直链与镜像均不可用）");
                return;
            }
            tasks.progress(task.getId(), 82, "校验并替换…");
            Path part = toolsDir.resolve(asset + ".new.part");
            Path bak = toolsDir.resolve(target.getFileName() + ".bak");
            Files.deleteIfExists(bak);
            if (Files.exists(target)) {
                Files.move(target, bak, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            String version = toolsLocator.execVersion(target.toString());
            if (version == null) {
                Files.deleteIfExists(target);
                if (Files.exists(bak)) {
                    Files.move(bak, target, StandardCopyOption.REPLACE_EXISTING);
                }
                tasks.finish(task.getId(), "FAILED", "新版本验证失败，已回滚");
                return;
            }
            Files.deleteIfExists(bak);
            toolsLocator.invalidate();
            statusCache.invalidate();
            tasks.finish(task.getId(), "SUCCESS", null);
            log.info("yt-dlp updated to {}", version);
        } catch (Exception e) {
            tasks.finish(task.getId(), "FAILED", "更新失败：" + e.getMessage());
        }
    }

    /** whisper 模型下载：hf-mirror 优先、huggingface 兜底、>1MB 校验。 */
    public void modelDownload(MediaTask task) {
        String key = task.getStage() == null ? "base" : task.getStage().replace("下载模型 ", "");
        String base = "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-" + key + ".bin";
        List<String> urls = List.of(base,
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-" + key + ".bin");
        Path dir = storage.modelsDir();
        Path part = dir.resolve("ggml-" + key + ".bin.part");
        try {
            tasks.progress(task.getId(), 5, "正在下载模型 " + key + "…");
            if (!downloadAny(urls, part, 1_000_000, task.getId(), 5, 95)) {
                tasks.finish(task.getId(), "FAILED", "模型下载失败（镜像与官方源均不可用）");
                return;
            }
            Files.move(part, dir.resolve("ggml-" + key + ".bin"), StandardCopyOption.REPLACE_EXISTING);
            tasks.finish(task.getId(), "SUCCESS", null);
        } catch (Exception e) {
            tasks.finish(task.getId(), "FAILED", "模型下载失败：" + e.getMessage());
        }
    }

    private boolean downloadAny(List<String> urls, Path dest, long minBytes,
                                int taskId, int fromPct, int toPct) throws Exception {
        for (String url : urls) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "MusicDreamer-Updater")
                        .timeout(Duration.ofMinutes(30)).build();
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) {
                    continue;
                }
                long total = resp.headers().firstValueAsLong("Content-Length").orElse(0);
                long done = 0;
                try (InputStream in = resp.body()) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    long lastTick = 0;
                    try (var out = Files.newOutputStream(dest)) {
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            done += n;
                            long now = System.currentTimeMillis();
                            if (total > 0 && now - lastTick > 500) {
                                lastTick = now;
                                int pct = (int) (fromPct + done * (toPct - fromPct) / total);
                                tasks.progress(taskId, pct, "下载中… " + pct + "%");
                            }
                        }
                    }
                }
                if (done < minBytes) {
                    Files.deleteIfExists(dest);
                    continue;
                }
                return true;
            } catch (Exception e) {
                log.warn("download source failed {}: {}", url, e.getMessage());
                Files.deleteIfExists(dest);
            }
        }
        return false;
    }

    private Path toolsDir() {
        for (String cand : new String[]{"tools", "../tools"}) {
            Path p = Paths.get(cand).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        try {
            return Files.createDirectories(Paths.get("tools").toAbsolutePath().normalize());
        } catch (Exception e) {
            throw new IllegalStateException("无法创建 tools 目录", e);
        }
    }
}
