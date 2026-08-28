package com.musicdreamer.media.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.media.entity.MediaTask;
import com.musicdreamer.media.service.SettingsService;
import com.musicdreamer.media.service.StorageService;
import com.musicdreamer.media.service.TaskManager;
import com.musicdreamer.media.tools.ToolsLocator;
import com.musicdreamer.media.tools.ToolsMgmtJob;
import com.musicdreamer.media.tools.ToolsStatusCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工具与模型管理 API（设计 10.3/10.8，管理员）。 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class ToolsController {

    private final ToolsLocator tools;
    private final TaskManager tasks;
    private final SettingsService settings;
    private final StorageService storage;
    private final ToolsStatusCache statusCache;

    // 转写模型只提供 small：中日文识别质量直接决定歌词校准/加时间轴的
    // 准确性，多规格选择对普通用户只是负担（占磁盘 + 选错质量差）
    private static final List<Map<String, String>> MODELS = List.of(
            Map.of("key", "small", "label", "标准（唯一规格）", "sizeText", "约466MB"));

    @GetMapping("/tools/status")
    public Mess toolsStatus() {
        AuthContext.requireAdmin();
        long now = System.currentTimeMillis();
        List<Map<String, Object>> cached = statusCache.status();
        if (cached != null && now - statusCache.statusAt() < 300_000) {
            return Mess.ok(cached);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (String tool : List.of(ToolsLocator.YT_DLP, ToolsLocator.FFMPEG,
                ToolsLocator.FFPROBE, ToolsLocator.WHISPER)) {
            String path = tools.locate(tool);
            String version = null;
            if (path != null) {
                version = tools.execVersion(path, tools.probeArgs(tool));
                // whisper-cli 的 --help 首行是 usage:，构建无版本号时给友好文案
                if (version != null && (version.startsWith("usage:") || version.startsWith("error:"))) {
                    version = "已安装（该构建无版本号）";
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool);
            item.put("path", path);
            item.put("version", version);
            item.put("alive", version != null);
            list.add(item);
        }
        statusCache.putStatus(list);
        return Mess.ok(list);
    }

    /** 通用安装/更新：没有就下载，有就更新（读 tools.manifest.json 装到 tools/bin/）。 */
    @PostMapping("/tools/install/{tool}")
    public Mess toolInstall(@PathVariable String tool) {
        AuthContext.requireAdmin();
        if (!List.of(ToolsLocator.YT_DLP, ToolsLocator.FFMPEG,
                ToolsLocator.FFPROBE, ToolsLocator.WHISPER).contains(tool)) {
            return Mess.fail(5003, "未知工具");
        }
        MediaTask t = tasks.submit(TaskManager.TOOL_INSTALL, null, null,
                AuthContext.getUserId(), "安装工具 " + tool);
        return Mess.ok(Map.of("taskId", t.getId()));
    }

    @PostMapping("/tools/update-ytdlp")
    public Mess updateYtdlp() {
        AuthContext.requireAdmin();
        MediaTask t = tasks.submit(TaskManager.TOOL_UPDATE, null, null,
                AuthContext.getUserId(), "准备更新 yt-dlp…");
        return Mess.ok(Map.of("taskId", t.getId()));
    }

    @GetMapping("/models")
    public Mess models() {
        AuthContext.requireLogin();
        String current = settings.whisperModel();
        Path dir = storage.modelsDir();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, String> m : MODELS) {
            String key = m.get("key");
            Map<String, Object> item = new LinkedHashMap<>(m);
            // 使用中 = 当前选中且文件真实存在，避免"使用中却带下载按钮"的矛盾态
            boolean downloaded = Files.isRegularFile(dir.resolve("ggml-" + key + ".bin"));
            item.put("downloaded", downloaded);
            item.put("isCurrent", key.equals(current) && downloaded);
            list.add(item);
        }
        return Mess.ok(list);
    }

    @PostMapping("/models/{key}/download")
    public Mess modelDownload(@PathVariable String key) {
        AuthContext.requireAdmin();
        if (MODELS.stream().noneMatch(m -> m.get("key").equals(key))) {
            return Mess.fail(5003, "未知模型规格");
        }
        MediaTask t = tasks.submit(TaskManager.MODEL_DOWNLOAD, null, null,
                AuthContext.getUserId(), "下载模型 " + key);
        return Mess.ok(Map.of("taskId", t.getId()));
    }

    @PostMapping("/models/{key}/use")
    public Mess modelUse(@PathVariable String key) {
        AuthContext.requireAdmin();
        if (!"small".equals(key)) {
            return Mess.fail(5003, "仅支持 small 模型");
        }
        try {
            Path marker = storage.modelsDir().resolve("current.txt");
            Files.writeString(marker, key);
        } catch (Exception e) {
            log.warn("current model marker write failed: {}", e.getMessage());
        }
        return Mess.ok(Map.of("key", key));
    }

    @PostMapping("/models/{key}/delete")
    public Mess modelDelete(@PathVariable String key) {
        // small 是唯一规格且恒为当前使用，不提供删除
        return Mess.fail(2007, "唯一模型不允许删除");
    }
}
