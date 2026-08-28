package com.musicdreamer.media.job;

import com.musicdreamer.media.job.LrcSupport.LrcLine;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌词时间轴算法（与 Sakura Echo 桌面版对齐行为一致）：
 * 1) computeAlignedTimes：带时间轴歌词 × 本地转写的逐句校准——单调配对 +
 *    配对率低时放宽窗口 + 分段偏移（B站剪辑版的间奏结构差异）。
 * 2) mergePlainWithWav：纯文本歌词 × 本地转写——文本用在线的（准确），
 *    拆句与时间用本地的（跟演唱走）：区间 DP 配对，配上的行按转写句边界
 *    拆开各挂时刻，未配上的行前后锚点插值。
 */
final class LyricAligner {

    private LyricAligner() {
    }

    /** 校准结果。 */
    record Aligned(double[] times, double median, int pairs) {
    }

    // ==================== 逐句校准（带时间轴歌词） ====================

    private record Pair(int lrcIdx, double wavTime) {
    }

    /** 单调配对：转写游标只前进 + ±windowSec 时间窗（防副歌相似句错配写出乱序时间轴）。 */
    private static List<Pair> pairMonotonic(List<LrcLine> lrc, List<LrcLine> wav,
                                            double windowSec, double minSim) {
        List<Pair> pairs = new ArrayList<>();
        int cursor = 0;
        for (int li = 0; li < lrc.size(); li++) {
            double lTime = lrc.get(li).time();
            String lText = lrc.get(li).text();
            Double bestTime = null;
            int bestWi = -1;
            double bestSim = 0;
            for (int wi = cursor; wi < wav.size(); wi++) {
                double wTime = wav.get(wi).time();
                if (wTime - lTime > windowSec) break;     // 超出右窗，后面更晚
                if (lTime - wTime > windowSec) continue;  // 早于左窗
                double sim = LrcSupport.textSimilarity(lText, wav.get(wi).text());
                if (sim > bestSim) {
                    bestSim = sim;
                    bestTime = wTime;
                    bestWi = wi;
                }
            }
            if (bestTime != null && bestSim >= minSim) {
                pairs.add(new Pair(li, bestTime));
                cursor = bestWi + 1;
            }
        }
        return pairs;
    }

    /**
     * 校准计算。audioDuration = 本地音源时长：
     *  - 与在线轴最后一句相差 <= 2s 判"同版本"：只做全局微移（±2.5s 封顶），
     *    不逐句改写——whisper 逐句时刻噪声大于在线轴自身的相对节奏误差，
     *    曾因单锚点抖动几秒把 32~47s 段整段推移（花人局 5:33 vs 5:32）
     *  - 时长差大（剪辑/搬运版）：配对 + 孤峰锚点剔除 + 锚点间线性插值偏移
     * 返回 null 表示不可校准。
     */
    static Aligned computeAlignedTimes(List<LrcLine> lrc, List<LrcLine> wav, Double audioDuration) {
        Double lastLrcTime = null;
        for (int i = lrc.size() - 1; i >= 0; i--) {
            if (lrc.get(i).time() >= 0) { lastLrcTime = lrc.get(i).time(); break; }
        }
        boolean sameVersion = audioDuration != null && lastLrcTime != null
                && Math.abs(audioDuration - lastLrcTime) <= 2;

        List<Pair> pairs = pairMonotonic(lrc, wav, 10, 0.35);
        // 放宽窗口仅当疑似结构差异：同版本绝不放宽（20s 窗会把副歌相似句配远）
        if (!sameVersion && pairs.size() < lrc.size() * 0.5 && lrc.size() >= 8) {
            List<Pair> wider = pairMonotonic(lrc, wav, 20, 0.35);
            if (wider.size() > pairs.size()) pairs = wider;
        }
        if (pairs.size() < 2 || pairs.size() < lrc.size() * 0.3) return null;

        List<Double> deltas = new ArrayList<>();
        for (Pair p : pairs) deltas.add(lrc.get(p.lrcIdx()).time() - p.wavTime());
        deltas.sort(Double::compare);
        double median = deltas.get(deltas.size() / 2);
        if (Math.abs(median) > 30) return null;

        double[] times = new double[lrc.size()];
        // 同版本：全局微移（在线轴相对节奏可信）
        if (sameVersion) {
            double shift = Math.max(-2.5, Math.min(2.5, median));
            for (int i = 0; i < lrc.size(); i++) times[i] = Math.max(0, lrc.get(i).time() - shift);
            return new Aligned(times, median, pairs.size());
        }

        // 结构差异版：锚点剔野（真实剪辑差是阶跃——边界锚点只背离一侧邻居；
        // whisper 单句抖动是孤峰——与两侧都差 >3s）→ 锚点间线性插值偏移
        record Anchor(int lrcIdx, double wavTime, double delta) {}
        List<Anchor> anchors = new ArrayList<>();
        for (Pair p : pairs) {
            anchors.add(new Anchor(p.lrcIdx(), p.wavTime(), lrc.get(p.lrcIdx()).time() - p.wavTime()));
        }
        List<Anchor> kept = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            Anchor a = anchors.get(i);
            Double prev = i > 0 ? anchors.get(i - 1).delta() : null;
            Double next = i < anchors.size() - 1 ? anchors.get(i + 1).delta() : null;
            if (prev != null && next != null
                    && Math.abs(a.delta() - prev) > 3 && Math.abs(a.delta() - next) > 3) continue;
            if (prev == null && next != null
                    && Math.abs(a.delta() - next) > 5 && Math.abs(a.delta() - median) > 4) continue;
            if (next == null && prev != null
                    && Math.abs(a.delta() - prev) > 5 && Math.abs(a.delta() - median) > 4) continue;
            kept.add(a);
        }
        java.util.Map<Integer, Double> pairedTime = new java.util.HashMap<>();
        for (Anchor a : kept) pairedTime.put(a.lrcIdx(), a.wavTime());
        for (int i = 0; i < lrc.size(); i++) {
            if (pairedTime.containsKey(i)) {
                times[i] = pairedTime.get(i);
                continue;
            }
            Anchor prevA = null, nextA = null;
            for (Anchor a : kept) {
                if (a.lrcIdx() < i) prevA = a;
                else if (a.lrcIdx() > i) { nextA = a; break; }
            }
            double delta;
            if (prevA != null && nextA != null) {
                double span = lrc.get(nextA.lrcIdx()).time() - lrc.get(prevA.lrcIdx()).time();
                double r = span > 0 ? (lrc.get(i).time() - lrc.get(prevA.lrcIdx()).time()) / span : 0;
                r = Math.max(0, Math.min(1, r));
                delta = prevA.delta() + (nextA.delta() - prevA.delta()) * r;
            } else if (nextA != null) delta = nextA.delta();
            else if (prevA != null) delta = prevA.delta();
            else delta = median;
            times[i] = Math.max(0, lrc.get(i).time() - delta);
        }
        return new Aligned(times, median, pairs.size());
    }

    // ==================== 纯文本合并加时间轴 ====================

    /** 合成结果。 */
    record Merged(List<LrcLine> lines, int matched, int total) {
    }

    private static final int MAXSPAN = 6;        // 一个在线行最多吸收的转写行数
    private static final double GAP_PLAIN = 0.15, GAP_WAV = 0.02;

    /**
     * 纯文本 × 转写合并：Needleman-Wunsch 式区间 DP——在线行吸收连续 1..MAXSPAN
     * 句转写；允许"在线行无匹配"与"转写行多余"两种跳过。配上的行按各转写行
     * 字数比例拆开分别挂时刻；未配上的行保留整行、时间由前后锚点插值。
     */
    static Merged mergePlainWithWav(List<LrcLine> plain, List<LrcLine> wav) {
        int n = plain.size(), m = wav.size();
        if (n == 0 || m == 0) {
            List<LrcLine> lines = new ArrayList<>();
            for (LrcLine l : plain) lines.add(new LrcLine(0, l.text()));
            return new Merged(lines, 0, n);
        }
        double minSim = 0.4;
        double[][][] sim = new double[n][m][MAXSPAN];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                for (int len = 1; len <= MAXSPAN; len++) {
                    int a = j - len + 1;
                    if (a < 0) break;
                    StringBuilder joined = new StringBuilder();
                    for (int k = a; k <= j; k++) joined.append(wav.get(k).text());
                    sim[i][j][len - 1] = LrcSupport.textSimilarity(plain.get(i).text(), joined.toString());
                }
            }
        }

        double NEG = Double.NEGATIVE_INFINITY;
        double[][] dp = new double[n + 1][m + 1];
        int[][] backK = new int[n + 1][m + 1];
        // back[i][j]：0 = 吸收区间 (backK, j]；1 = plainGap；2 = wavGap；-1 = 起点
        byte[][] back = new byte[n + 1][m + 1];
        for (double[] row : dp) java.util.Arrays.fill(row, NEG);
        for (int[] row : backK) java.util.Arrays.fill(row, -1);
        // 允许开头跳过多余转写行（前奏哼唱/幻觉）
        for (int j = 0; j <= m; j++) dp[0][j] = -j * GAP_WAV;

        for (int i = 1; i <= n; i++) {
            for (int j = 0; j <= m; j++) {
                double best = NEG;
                byte dir = -1;
                int bestK = -1;
                for (int len = 1; len <= Math.min(MAXSPAN, j); len++) {
                    int k = j - len;
                    if (dp[i - 1][k] == NEG) continue;
                    double s = sim[i - 1][j - 1][len - 1];
                    double v = dp[i - 1][k] + (s >= minSim ? s - minSim : -0.5);
                    if (v > best) {
                        best = v;
                        dir = 0;
                        bestK = k;
                    }
                }
                if (dp[i - 1][j] != NEG && dp[i - 1][j] - GAP_PLAIN > best) {
                    best = dp[i - 1][j] - GAP_PLAIN;
                    dir = 1;
                }
                if (j > 0 && dp[i][j - 1] != NEG && dp[i][j - 1] - GAP_WAV > best) {
                    best = dp[i][j - 1] - GAP_WAV;
                    dir = 2;
                }
                dp[i][j] = best;
                back[i][j] = dir;
                backK[i][j] = bestK;
            }
        }

        // 终态：允许尾部剩余转写行不吸收（尾奏哼唱）
        int endJ = m;
        double bestEnd = NEG;
        for (int j = 0; j <= m; j++) {
            if (dp[n][j] == NEG) continue;
            double v = dp[n][j] - (m - j) * GAP_WAV;
            if (v > bestEnd) {
                bestEnd = v;
                endJ = j;
            }
        }

        // 回溯：plain 行 → 转写索引闭区间 [k, j-1]（DP 域 (k, j] 覆盖 wav k..j-1）
        int[][] spanOf = new int[n][];
        int i = n, j = endJ;
        while (i > 0) {
            byte dir = back[i][j];
            if (dir == 1) {
                i--;
            } else if (dir == 2) {
                j--;
            } else if (dir == 0) {
                spanOf[i - 1] = new int[]{backK[i][j], j - 1};
                j = backK[i][j];
                i--;
            } else {
                break;
            }
        }

        // 生成合成行
        List<LrcLine> out = new ArrayList<>();
        int matched = 0;
        for (int pi = 0; pi < n; pi++) {
            String text = plain.get(pi).text();
            int[] span = spanOf[pi];
            if (span == null) {
                out.add(new LrcLine(-1, text));   // 时间稍后插值
                continue;
            }
            matched++;
            int segCount = span[1] - span[0] + 1;
            if (segCount == 1) {
                out.add(new LrcLine(wav.get(span[0]).time(), text));
            } else {
                // 按各转写行字数比例把在线文本拆开，分别挂转写时刻
                int totalChars = 0;
                for (int si = span[0]; si <= span[1]; si++) {
                    totalChars += wav.get(si).text().replaceAll("\\s", "").length();
                }
                totalChars = Math.max(totalChars, 1);
                String plainChars = text.replaceAll("\\s", "");
                int pos = 0;
                for (int si = span[0]; si <= span[1]; si++) {
                    boolean isLast = si == span[1];
                    String chunk;
                    if (isLast) {
                        chunk = plainChars.substring(Math.min(pos, plainChars.length()));
                    } else {
                        int take = Math.max(1, (int) Math.round(
                                plainChars.length() * (double) wav.get(si).text().replaceAll("\\s", "").length() / totalChars));
                        int end = Math.min(plainChars.length(), pos + take);
                        chunk = plainChars.substring(Math.min(pos, plainChars.length()), end);
                        pos = end;
                    }
                    if (!chunk.isBlank()) out.add(new LrcLine(wav.get(si).time(), chunk.trim()));
                }
            }
        }

        // 未配对行插值（前后锚点线性；首部倒推/尾部顺延）——用列表下标找锚点
        List<Integer> anchorIdx = new ArrayList<>();
        for (int oi = 0; oi < out.size(); oi++) {
            if (out.get(oi).time() >= 0) anchorIdx.add(oi);
        }
        if (!anchorIdx.isEmpty()) {
            int first = anchorIdx.get(0), last = anchorIdx.get(anchorIdx.size() - 1);
            int rows = Math.max(last - first, 1);
            double avgGap = (out.get(last).time() - out.get(first).time()) / rows;
            for (int oi = 0; oi < out.size(); oi++) {
                if (out.get(oi).time() >= 0) continue;
                Integer prev = null, next = null;
                for (int ai : anchorIdx) {
                    if (ai < oi) prev = ai;
                    else if (ai > oi) {
                        next = ai;
                        break;
                    }
                }
                double t;
                if (prev != null && next != null) {
                    t = out.get(prev).time() + (out.get(next).time() - out.get(prev).time())
                            * (oi - prev) / (double) (next - prev);
                } else if (prev != null) {
                    t = out.get(prev).time() + avgGap;
                } else if (next != null) {
                    t = Math.max(0, out.get(next).time() - avgGap * (next - oi));
                } else {
                    t = 0;
                }
                out.set(oi, new LrcLine(t, out.get(oi).text()));
            }
        } else {
            for (int oi = 0; oi < out.size(); oi++) {
                out.set(oi, new LrcLine(0, out.get(oi).text()));
            }
        }
        for (int k = 1; k < out.size(); k++) {
            if (out.get(k).time() < out.get(k - 1).time()) {
                out.set(k, new LrcLine(out.get(k - 1).time(), out.get(k).text()));
            }
        }
        return new Merged(out, matched, n);
    }

    // ==================== 写回 ====================

    /** 秒 → [mm:ss.xx] */
    static String formatLrcTime(double t) {
        double clamped = Math.max(0, t);
        int mm = (int) Math.floor(clamped / 60);
        double ss = clamped - mm * 60;
        return String.format("[%02d:%05.2f]", mm, ss);
    }

    /**
     * 校准写回：按"原始内容中的歌词行"（有文本且非元数据，与解析端口径一致）
     * 逐行替换时间戳；伴奏空行按整体偏移平移；元数据/[Muziek] 行原样保留。
     * 写回前强制单调不减。
     */
    static String writeAlignedLyrics(String originalContent, double[] alignedTimes, double globalShift) {
        double[] times = new double[alignedTimes.length];
        for (int i = 0; i < alignedTimes.length; i++) times[i] = Math.max(0, alignedTimes[i]);
        for (int i = 1; i < times.length; i++) times[i] = Math.max(times[i], times[i - 1]);

        List<String> out = new ArrayList<>();
        int i = 0;
        java.util.regex.Matcher tagM = java.util.regex.Pattern
                .compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]").matcher("");
        for (String raw : originalContent.split("\n", -1)) {
            String trimmed = raw.trim();
            if (trimmed.matches("(?i)^\\[offset:.*")) continue;
            java.util.regex.Matcher tm = (tagM.reset(trimmed)).find() ? tagM : null;
            if (tm != null) {
                String rest = trimmed.substring(tm.end());
                String body = rest.trim();
                String stripped = body.replaceAll("^[♪♫♩♬]+", "").trim();
                boolean isHum = stripped.matches("[~～ー\\-—\\s]*");
                if (!body.isEmpty() && !isHum && !LrcSupport.isLyricMetadata(stripped)
                        && !LrcSupport.isPureTagText(stripped)) {
                    if (i < times.length) {
                        out.add(formatLrcTime(times[i++]) + rest);
                    } else {
                        out.add(raw);
                    }
                } else if (body.isEmpty() || isHum) {
                    // 伴奏/间奏标记行：按整体偏移平移，保持标记准确
                    double orig = Integer.parseInt(tm.group(1)) * 60 + Integer.parseInt(tm.group(2))
                            + frac(tm.group(3));
                    out.add(formatLrcTime(Math.max(0, orig - globalShift)) + rest);
                } else {
                    out.add(raw);
                }
            } else {
                out.add(raw);
            }
        }
        return String.join("\n", out);
    }

    /** 合并结果写盘内容：保留头部标签（[source:] 等）+ 合成行。 */
    static String writeMergedLyrics(List<LrcLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (LrcLine l : lines) {
            sb.append(formatLrcTime(l.time())).append(' ').append(l.text()).append('\n');
        }
        return sb.toString();
    }

    private static double frac(String s) {
        if (s == null || s.isEmpty()) return 0;
        return Integer.parseInt(s) / Math.pow(10, s.length());
    }
}
