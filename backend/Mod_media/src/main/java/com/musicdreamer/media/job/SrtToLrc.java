package com.musicdreamer.media.job;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 字幕转 LRC（设计 10.7）：SRT/VTT 时间轴起始时间 + 正文拼接，输出 [mm:ss.xx] 行。 */
public final class SrtToLrc {

    private static final Pattern SRT_TIME =
            Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->.*");
    private static final Pattern LRC_LINE =
            Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)");

    private SrtToLrc() {}

    public static String convert(java.nio.file.Path file) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(file);
            List<String> out = new ArrayList<>();
            Long currentTime = null;
            StringBuilder text = new StringBuilder();
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    if (currentTime != null && text.length() > 0) {
                        out.add(fmt(currentTime, text.toString()));
                    }
                    currentTime = null;
                    text.setLength(0);
                    continue;
                }
                if (line.matches("\\d+")) {
                    continue; // SRT 序号行
                }
                Matcher m = SRT_TIME.matcher(line);
                if (m.matches()) {
                    currentTime = Integer.parseInt(m.group(1)) * 3600_000L
                            + Integer.parseInt(m.group(2)) * 60_000L
                            + Integer.parseInt(m.group(3)) * 1000L
                            + Integer.parseInt(m.group(4).length() == 3 ? m.group(4) : m.group(4) + "00".substring(0, 3 - m.group(4).length()));
                    continue;
                }
                if (line.startsWith("WEBVTT") || line.startsWith("NOTE") || line.startsWith("Kind:") || line.startsWith("Language:")) {
                    continue;
                }
                text.append(line.isEmpty() ? "" : (text.length() > 0 ? " " : "")).append(line);
            }
            if (currentTime != null && text.length() > 0) {
                out.add(fmt(currentTime, text.toString()));
            }
            // 稳妥：如果产物本身已是 LRC 时间标签格式，直接透传
            if (out.isEmpty()) {
                List<String> lrc = new ArrayList<>();
                for (String line : lines) {
                    if (LRC_LINE.matcher(line).matches()) {
                        lrc.add(line);
                    }
                }
                if (!lrc.isEmpty()) {
                    return String.join("\n", lrc) + "\n";
                }
            }
            out.sort((a, b) -> Long.compare(timeOf(a), timeOf(b)));
            return out.isEmpty() ? "" : String.join("\n", out) + "\n";
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmt(long ms, String text) {
        long total = ms / 1000;
        int centis = (int) (ms % 1000 / 10);
        return String.format("[%02d:%02d.%02d]%s", total / 60, total % 60, centis, text);
    }

    private static long timeOf(String lrcLine) {
        Matcher m = LRC_LINE.matcher(lrcLine);
        if (m.matches()) {
            return Long.parseLong(m.group(1)) * 60_000L + Long.parseLong(m.group(2)) * 1000L;
        }
        return 0;
    }
}
