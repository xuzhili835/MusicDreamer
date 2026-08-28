package com.musicdreamer.media.job;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 歌词基础库：LRC 解析、文本相似度、标题清洗、LRCLIB 在线匹配。
 * 与 Sakura Echo 桌面版的 lyrics.js 行为对齐（同一套清洗规则与打分门禁）。
 */
public final class LrcSupport {

    private LrcSupport() {
    }

    /** 一行歌词；time < 0 表示纯文本行（无时间轴）。 */
    public record LrcLine(double time, String text) {
    }

    /** LRCLIB 命中结果；synced 与 plain 二选一；id 为词库记录 id（复盘时间戳用，可空）。 */
    public record OnlineMatch(String syncedLyrics, String plainLyrics,
                              String trackName, String artistName, boolean noTimeline, Long id) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static final Pattern TIME_TAG =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)");
    private static final Pattern OFFSET_TAG =
            Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTE_PREFIX = Pattern.compile("^[♪♫♩♬]+");

    // ==================== 解析 ====================

    /** 解析 LRC：[offset:] 全量先读；♪ 前缀剥离；哼唱/元数据/纯标签行跳过；按时间排序。 */
    public static List<LrcLine> parseLrc(String content) {
        List<LrcLine> out = new ArrayList<>();
        if (content == null || content.isBlank()) return out;
        String[] lines = content.split("\n", -1);

        double offsetSec = 0;
        for (String line : lines) {
            Matcher om = OFFSET_TAG.matcher(line.trim());
            if (om.find()) offsetSec = parseIntSafe(om.group(1)) / 1000.0;
        }

        boolean hasTimestamps = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            Matcher m = TIME_TAG.matcher(line);
            if (!m.find()) continue;
            hasTimestamps = true;
            int minutes = parseIntSafe(m.group(1));
            int seconds = parseIntSafe(m.group(2));
            String fracStr = m.group(3) == null ? "0" : m.group(3);
            double frac = parseIntSafe(fracStr) / Math.pow(10, fracStr.length());

            String text = m.group(4) == null ? "" : m.group(4).trim();
            text = NOTE_PREFIX.matcher(text).replaceFirst("").trim();
            if (text.matches("[~～ー\\-—\\s]*")) text = "";
            if (text.isEmpty() || isLyricMetadata(text) || isPureTagText(text)) continue;

            out.add(new LrcLine(Math.max(0, minutes * 60 + seconds + frac - offsetSec), text));
        }

        if (!hasTimestamps) {
            // 纯文本歌词：按行保留，time = -1
            List<LrcLine> plain = new ArrayList<>();
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (!line.isEmpty() && !isPureTagText(line)) plain.add(new LrcLine(-1, line));
            }
            return plain;
        }
        out.sort(Comparator.comparingDouble(LrcLine::time));
        return out;
    }

    /** 制作人信息行（作词/作曲等带时间轴的元数据），不是应展示的歌词。 */
    static boolean isLyricMetadata(String text) {
        String normalized = text.replaceAll("[\\s\\u3000]+", " ").trim();
        if (normalized.isEmpty()) return true;
        if (normalized.matches("(?:作词|作曲|编曲|演唱|歌手|原唱|制作|制作人|词|曲)\\s*[:：].*")) return true;
        return normalized.matches("(?:作词|作曲|编曲|演唱|歌手|原唱|制作人?|作者|作家)");
    }

    /** 纯方括号标签文本：[Muziek]/[Music]/[by:whisper.cpp] 等。 */
    static boolean isPureTagText(String text) {
        String t = text.trim();
        return !t.isEmpty() && t.startsWith("[") && t.endsWith("]") && !TIME_TAG.matcher(t).find();
    }

    // ==================== 相似度 ====================

    private static final Pattern SIM_STRIP = Pattern.compile(
            "[\\s♪♫♩♬~～ー\\-—。，、.,！？!?:；;「」『』“”‘’()（）\\[\\]]");

    /** 字符 bigram 的 Dice 系数（0~1）：对齐配对用，措辞常有出入，精确匹配不可行。 */
    public static double textSimilarity(String a, String b) {
        String A = SIM_STRIP.matcher(a == null ? "" : a).replaceAll("");
        String B = SIM_STRIP.matcher(b == null ? "" : b).replaceAll("");
        if (A.length() < 2 || B.length() < 2) return 0;
        Map<String, Integer> ga = bigrams(A), gb = bigrams(B);
        int inter = 0, total = 0;
        for (int v : ga.values()) total += v;
        for (int v : gb.values()) total += v;
        for (Map.Entry<String, Integer> e : ga.entrySet()) {
            inter += Math.min(e.getValue(), gb.getOrDefault(e.getKey(), 0));
        }
        return total == 0 ? 0 : (2.0 * inter) / total;
    }

    private static Map<String, Integer> bigrams(String s) {
        Map<String, Integer> g = new java.util.HashMap<>();
        for (int i = 0; i < s.length() - 1; i++) {
            String p = s.substring(i, i + 2);
            g.merge(p, 1, Integer::sum);
        }
        return g;
    }

    // ==================== 标题清洗 ====================

    private static final Pattern NOISE_FULL = Pattern.compile(
            "(?i)^(?:日推歌单|歌单|官方.*|.*字幕|.*完整版|.*版|MV|PV|MAD|AMV|DL.*|.*Radio|.*Cover|翻自.*)$");
    private static final Pattern NOISE_PART = Pattern.compile("官方|字幕|歌单|完整|高清|MV|PV", Pattern.CASE_INSENSITIVE);

    static boolean isNoise(String s) {
        return NOISE_FULL.matcher(s).matches() || NOISE_PART.matcher(s).find();
    }

    /** B站搬运标题 → 干净的 (歌名, 艺术家)。 */
    public static String[] cleanTitleForLyrics(String rawTitle, String rawArtist) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        String artist = rawArtist == null ? "" : rawArtist.trim();
        if (title.isEmpty()) return new String[]{"", artist};

        // 1. 书名号《》「」『』内优先作为歌名（B站搬运惯例）；英文引号段只是后备
        Matcher m = Pattern.compile("[《「『]([^》」』]{2,60})[》」』]").matcher(title);
        String matched = null, inner = null;
        if (m.find()) {
            matched = m.group(0);
            inner = m.group(1);
        } else {
            Matcher q = Pattern.compile("\"([^\"]{2,60})\"|“([^”]{2,60})”").matcher(title);
            if (q.find()) {
                matched = q.group(0);
                inner = q.group(1) != null ? q.group(1) : q.group(2);
            }
        }
        if (matched != null) {
            title = inner.replace("_", " ").trim();
            String after = rawTitle.substring(rawTitle.indexOf(matched) + matched.length());
            Matcher am = Pattern.compile("[-_]{1,3}\\s*([^-_【】\\[\\]()（）]{2,30}?)[-_]").matcher(after);
            if (am.find()) {
                String cand = am.group(1).replace("_", " ").trim();
                if (!cand.isEmpty() && !isNoise(cand) && artist.isEmpty()) artist = cand;
            }
        } else {
            // 2. 无书名号：剥 【】[..] 装饰段，再按 "A_-_B" / "A - B" 拆艺术家。
            //    装饰段无论能否拆出艺术家都要剥——此前 stem 只在拆分成功时才被
            //    采用，"【4K Hi-Res】晴天-周杰伦" 这类无空格短横线标题会原样
            //    拿去搜在线库，必然 miss 后误降级到本地转写
            String stem = title.replaceAll("[【\\[][^】\\]]*[】\\]]", " ").trim();
            title = stem;
            String[] parts = stem.split("\\s*_+[-–—]_+\\s*|\\s+[-–—]\\s+");
            if (parts.length >= 2) {
                String a = parts[0].replace("_", " ").trim();
                StringBuilder rest = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) rest.append(" - ");
                    rest.append(parts[i]);
                }
                String b = rest.toString().replace("_", " ").trim();
                if (!a.isEmpty() && !b.isEmpty() && !isNoise(a)) {
                    if (artist.isEmpty()) artist = a;
                    title = b;
                } else if (!b.isEmpty()) {
                    title = b;
                }
            }
        }

        // 3. 剥常见搬运后缀与首尾杂音
        title = title.replaceAll("(?i)(日推歌单|歌单)$", "")
                .replaceAll("[_.\\s\\-–—]+$", "")
                .replaceAll("^[_.\\s\\-–—]+", "")
                .replace("_", " ")
                .trim();
        return new String[]{title, artist};
    }

    // ==================== LRCLIB 在线匹配 ====================

    /**
     * LRCLIB 匹配：精确 get → 模糊 search（只用歌名，B站的 artist 多为 UP 主名会污染搜索）。
     * 时长门禁 ±8s 只作用于带时间轴候选；纯文本作为兜底（无对齐问题）。
     * 返回 null 表示无命中。
     */
    public static OnlineMatch matchOnlineLyrics(String rawTitle, String rawArtist, double durationSec) {
        String[] cleaned = cleanTitleForLyrics(rawTitle, rawArtist);
        String title = cleaned[0], artist = cleaned[1];
        if (title.isEmpty()) return null;
        int duration = (int) Math.round(durationSec);

        // 1) 精确匹配
        if (duration > 0 && !artist.isEmpty()) {
            try {
                String url = "https://lrclib.net/api/get?track_name=" + enc(title)
                        + "&artist_name=" + enc(artist) + "&duration=" + duration;
                Map<String, Object> r = httpGetJson(url);
                if (r != null && str(r.get("syncedLyrics")) != null) {
                    return new OnlineMatch(str(r.get("syncedLyrics")), null,
                            str(r.get("trackName")), str(r.get("artistName")), false, lng(r.get("id")));
                }
            } catch (Exception ignored) {
            }
        }

        // 2) 模糊搜索：query 只带歌名；artist 用于候选排序加分
        try {
            List<Map<String, Object>> list = httpGetList("https://lrclib.net/api/search?q=" + enc(title));
            if (list == null || list.isEmpty()) return null;

            // 2a) 带时间轴候选：仅限时长门禁内（版本一致时间轴才有意义）
            List<Map<String, Object>> inGate = new ArrayList<>();
            for (Map<String, Object> r : list) {
                if (str(r.get("syncedLyrics")) == null) continue;
                Double d = dbl(r.get("duration"));
                if (d == null) continue;
                if (duration <= 0 || Math.abs(d - duration) <= 8) inGate.add(r);
            }
            if (!inGate.isEmpty()) {
                inGate.sort(Comparator.comparingDouble(r -> score(r, artist, duration)));
                Map<String, Object> best = inGate.get(0);
                return new OnlineMatch(str(best.get("syncedLyrics")), null,
                        str(best.get("trackName")), str(best.get("artistName")), false, lng(best.get("id")));
            }

            // 2b) 纯文本兜底：本地是 DJ 版/视频版时库里常只有原版同步或变体纯文本
            List<Map<String, Object>> plains = new ArrayList<>();
            for (Map<String, Object> r : list) {
                if (str(r.get("syncedLyrics")) == null && str(r.get("plainLyrics")) != null) plains.add(r);
            }
            if (!plains.isEmpty()) {
                plains.sort(Comparator.comparingDouble(r -> score(r, artist, duration)));
                Map<String, Object> best = plains.get(0);
                return new OnlineMatch(null, str(best.get("plainLyrics")),
                        str(best.get("trackName")), str(best.get("artistName")), true, lng(best.get("id")));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static double score(Map<String, Object> r, String artist, int duration) {
        double s = 0;
        Double d = dbl(r.get("duration"));
        if (duration > 0 && d != null) s += Math.abs(d - duration);
        if (!artist.isEmpty() && str(r.get("artistName")) != null
                && str(r.get("artistName")).contains(artist)) s -= 20;
        String name = str(r.get("trackName"));
        if (name != null && name.matches("(?i).*DJ.*|.*live.*|.*remix.*|.*伴奏.*|.*inst.*")) s += 500;
        return s;
    }

    // ==================== HTTP / 工具 ====================

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static Long lng(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o != null) try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static Double dbl(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> httpGetJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "MusicDreamer/1.0")
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;
        return JSON.readValue(resp.body(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> httpGetList(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "MusicDreamer/1.0")
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;
        Object parsed = JSON.readValue(resp.body(), Object.class);
        return parsed instanceof List ? (List<Map<String, Object>>) parsed : null;
    }
}
