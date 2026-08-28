package com.musicdreamer.media.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicdreamer.media.exec.ExecResult;
import com.musicdreamer.media.exec.ProcessExecutor;
import com.musicdreamer.media.mapper.SongFileMapper;
import com.musicdreamer.media.tools.ToolsLocator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 外置识别（ACRCloud，设计三期）：本地指纹 MISS 时的兜底层。
 *
 * 配置放 {storage_root}/acrcloud.properties（data/ 目录已被 gitignore，
 * 密钥不入库、不提交、不进日志）。三项任一为空即视为关闭，识别链路自动退化为
 * 本地 MISS → 求歌，不影响任何已有功能。
 *
 * 协议：POST https://{host}/v1/identify（multipart），
 * signature = base64(hmac_sha1(secret, "POST\n/v1/identify\n{key}\naudio\n1\n{ts}"))。
 * 拿到歌名后回查本地已发布歌曲（规范化模糊匹配），命中直接返回本地歌，
 * 未命中返回歌名由前端预填求歌。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcrService {

    private final SettingsService settings;
    private final StorageService storage;
    private final ToolsLocator tools;
    private final ProcessExecutor executor;
    private final SongFileMapper songs;
    private final ObjectMapper json = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile Properties conf;
    private volatile long confAt = 0;
    private volatile List<Map<String, Object>> published;
    private volatile long publishedAt = 0;

    /** 外置识别结果：歌名/歌手/专辑 + 片段在原曲中的位置。 */
    public record AcrHit(String title, String artist, String album, Double offsetSec) {}

    public boolean enabled() {
        return !host().isEmpty() && !key().isEmpty() && !secret().isEmpty();
    }

    /** 识别片段（webm/任意容器 → ffmpeg 转单声道 mp3 截 15 秒 → ACRCloud）。失败返回 null 不抛出。 */
    public AcrHit identifyQuiet(Path clip) {
        try {
            return identify(clip);
        } catch (Exception e) {
            log.warn("acrcloud identify failed: {}", e.getMessage());
            return null;
        }
    }

    private AcrHit identify(Path clip) throws Exception {
        if (!enabled()) {
            return null;
        }
        String ffmpeg = tools.locate(ToolsLocator.FFMPEG);
        if (ffmpeg == null) {
            return null;
        }
        Path mp3 = storage.dir("task").resolve("acr_" + System.nanoTime() + ".mp3");
        try {
            ExecResult r = executor.run(List.of(ffmpeg, "-y", "-v", "error", "-i",
                            clip.toString(), "-t", "15", "-ac", "1", "-ar", "44100",
                            "-b:a", "64k", mp3.toString()),
                    60_000L, null, null).join();
            if (r.getExitCode() != 0 || !Files.isRegularFile(mp3)) {
                log.warn("acrcloud clip convert failed: {}", brief(r));
                return null;
            }
            byte[] audio = Files.readAllBytes(mp3);
            HttpResponse<String> resp = http.send(buildRequest(audio), HttpResponse.BodyHandlers.ofString());
            JsonNode root = json.readTree(resp.body());
            int code = root.path("status").path("code").asInt(-1);
            if (resp.statusCode() != 200 || code != 0) {
                // 常见：额度耗尽/密钥错误——记日志方便排查，不影响本地识别主链路
                log.warn("acrcloud respond status={} body={}", resp.statusCode(),
                        resp.body().length() > 300 ? resp.body().substring(0, 300) : resp.body());
                return null;
            }
            JsonNode music = root.path("metadata").path("music");
            if (music.isArray()) {
                for (JsonNode m : music) {
                    String title = m.path("title").asText("").trim();
                    if (title.isEmpty()) {
                        continue;
                    }
                    List<String> artists = new ArrayList<>();
                    m.path("artists").forEach(a -> {
                        String n = a.path("name").asText("").trim();
                        if (!n.isEmpty()) {
                            artists.add(n);
                        }
                    });
                    double off = m.path("play_offset_ms").asDouble(0) / 1000.0;
                    return new AcrHit(title, String.join("/", artists),
                            m.path("album").path("name").asText("").trim(), off);
                }
            }
            return null;
        } finally {
            try {
                Files.deleteIfExists(mp3);
            } catch (Exception ignored) {
            }
        }
    }

    private HttpRequest buildRequest(byte[] audio) throws Exception {
        String host = host();
        String key = key();
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String toSign = "POST\n/v1/identify\n" + key + "\naudio\n1\n" + ts;
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String sig = Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
        String boundary = "----mdacr" + System.nanoTime();
        List<byte[]> parts = new ArrayList<>();
        parts.add(field(boundary, "access_key", key));
        parts.add(field(boundary, "data_type", "audio"));
        parts.add(field(boundary, "signature_version", "1"));
        parts.add(field(boundary, "signature", sig));
        parts.add(field(boundary, "timestamp", ts));
        parts.add(field(boundary, "sample_bytes", String.valueOf(audio.length)));
        parts.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"sample\"; "
                + "filename=\"clip.mp3\"\r\nContent-Type: audio/mpeg\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        parts.add(audio);
        parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        // 拼成单个 byte[] 用 ofByteArray 发送：ofByteArrays 会走 transfer-encoding: chunked，
        // ACRCloud /v1/identify 不接受 chunked（3002 Invalid http content type）
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(audio.length + 4096);
        for (byte[] part : parts) {
            bos.write(part, 0, part.length);
        }
        return HttpRequest.newBuilder(URI.create("https://" + host + "/v1/identify"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bos.toByteArray()))
                .timeout(Duration.ofSeconds(12))
                .build();
    }

    private static byte[] field(String boundary, String name, String value) {
        return ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String brief(ExecResult r) {
        String s = r.getStderr();
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) : s).replace("\n", " ");
    }

    // ---------- 歌名回查本地 ----------

    /** 已发布歌曲（title/artist 规范化匹配用，60 秒缓存；曲库规模小，内存比对足够）。 */
    private List<Map<String, Object>> published() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> p = published;
        if (p == null || now - publishedAt > 60_000) {
            try {
                p = songs.listPublishedSongs();
            } catch (Exception e) {
                log.warn("acrcloud local list failed: {}", e.getMessage());
                p = List.of();
            }
            published = p;
            publishedAt = now;
        }
        return p;
    }

    /**
     * 外置歌名 → 本地已发布歌曲。三级放宽：
     * 核心名（去括号注记/feat. 段）+ 歌手交叉包含 → 全名规范化相等 → 核心名相等（不比歌手）。
     */
    public Map<String, Object> matchLocal(String title, String artist) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String qt = norm(title);
        String qc = core(title);
        String qa = core(artist);
        Map<String, Object> loose = null;
        for (Map<String, Object> row : published()) {
            String name = String.valueOf(row.getOrDefault("name", ""));
            String singer = String.valueOf(row.getOrDefault("singerName", ""));
            String nc = core(name);
            if (nc.equals(qc)) {
                String sc = core(singer);
                if (!qa.isEmpty() && (sc.contains(qa) || qa.contains(sc))) {
                    return row;
                }
                if (loose == null) {
                    loose = row;
                }
            } else if (qt.equals(norm(name)) && loose == null) {
                loose = row;
            }
        }
        return loose;
    }

    /** 基础规范化：小写 + 去空白（含全角空格）。 */
    static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[\\s\\u3000]+", "");
    }

    /** 核心名：再去各类括号内的注记（(Live)/【MV】…）与 feat./ft. 客串段。 */
    static String core(String s) {
        String t = norm(s).replaceAll("[（(【\\[][^）)】\\]]*[）)】\\]]", "");
        int i = Math.max(Math.max(t.indexOf("feat."), t.indexOf("ft.")), t.indexOf("featuring"));
        return i > 0 ? t.substring(0, i) : t;
    }

    // ---------- 配置 ----------

    private Properties conf() {
        long now = System.currentTimeMillis();
        Properties p = conf;
        if (p == null || now - confAt > 60_000) {
            p = new Properties();
            Path f = Paths.get(settings.storageRoot(), "acrcloud.properties");
            if (Files.isRegularFile(f)) {
                try (InputStream in = Files.newInputStream(f)) {
                    p.load(in);
                } catch (Exception e) {
                    log.warn("acrcloud properties load failed: {}", e.getMessage());
                }
            }
            conf = p;
            confAt = now;
        }
        return p;
    }

    private String host() {
        String h = conf().getProperty("host", "").trim();
        return h.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    private String key() {
        return conf().getProperty("access_key", "").trim();
    }

    private String secret() {
        return conf().getProperty("access_secret", "").trim();
    }

    /** 管理端可见的外置识别状态（不含密钥本身）。 */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled());
        m.put("host", host().isEmpty() ? null : host());
        return m;
    }
}
