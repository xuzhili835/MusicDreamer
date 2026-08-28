package com.musicdreamer.media.service;

import com.musicdreamer.media.exec.ExecResult;
import com.musicdreamer.media.exec.ProcessExecutor;
import com.musicdreamer.media.mapper.FingerprintMapper;
import com.musicdreamer.media.mapper.SongFileMapper;
import com.musicdreamer.media.tools.ToolsLocator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 听歌识曲指纹（设计文档二期）：Shazam 式 landmark 算法的纯 Java 实现。
 *
 * 为什么不用 chromaprint/fpcalc（2026-08-26 实测验尸）：其指纹带长时程依赖
 * （帧值取决于起始以来的全部历史），任意位置冷启动切片与全曲指纹完全对不上——
 * 10 秒/60 秒切片同曲比特一致率仅 0.56/0.66，与异曲 0.55 无区分；只有从头
 * 起始的片段才能对齐。它是"文件识别"算法，不是"识曲"算法。
 *
 * 本实现（参数经真实曲库验证：同曲 435 票 / 异曲 5 票，位置精确定位）：
 * PCM(11.025k 单声道) → STFT(FFT 1024 / HOP 512, Hann) → 峰点（自适应阈值
 * mean+1.2σ + ±1 帧 × ±5 bin 邻域极大值，每帧 ≤5）→ 每个锚点向后 ≤63 帧配
 * 对 ≤5 → hash = (f1&lt;&lt;10 | f2)&lt;&lt;6 | Δt → 倒排索引 fp_hash。
 * 查询：片段同法取哈希 → hash IN 拉库内锚点 → 按 (库内帧 − 查询帧) 偏移投票，
 * 最高同偏移票数即命中，票数/查询哈希数为置信度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FingerprintService {

    private static final int FFT = 1024;
    private static final int HOP = 512;
    private static final int SR = 11025;
    private static final int FMIN = 5;
    private static final int FMAX = 400;
    private static final int MAX_PAIR = 63;
    private static final int PEAKS_PER_FRAME = 5;
    private static final int PAIRS_PER_ANCHOR = 5;
    private static final int SQL_BATCH = 500;

    /** 命中判定：绝对票数与置信度双门槛（异曲噪声实测约 0~5 票 / 0.003） */
    public static final int HIT_VOTES = 25;
    public static final double HIT_RATIO = 0.10;
    public static final int LIKELY_VOTES = 8;
    public static final double LIKELY_RATIO = 0.03;

    private static final double[] WIN = new double[FFT];

    static {
        for (int i = 0; i < FFT; i++) {
            WIN[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT - 1));
        }
    }

    private final FingerprintMapper fp;
    private final SongFileMapper songs;
    private final ToolsLocator tools;
    private final ProcessExecutor executor;
    private final StorageService storage;

    private final ExecutorService rebuildPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fp-rebuild");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean rebuilding = new AtomicBoolean(false);
    private volatile Map<String, Object> rebuildState = Map.of();

    /** 识别结果：songId + 对齐票数 + 置信度 + 片段在原曲中的位置。 */
    public record Match(long songId, int votes, double ratio, double offsetSec) {
        public String level() {
            return votes >= HIT_VOTES && ratio >= HIT_RATIO ? "HIT"
                    : votes >= LIKELY_VOTES && ratio >= LIKELY_RATIO ? "LIKELY" : "MISS";
        }
    }

    // ---------- 对外 ----------

    /** 生成指纹写入倒排（先清旧后分批插入）；返回哈希条数，失败抛异常。 */
    public int ingest(Long songId, Path audio) {
        String ffmpeg = tools.locate(ToolsLocator.FFMPEG);
        if (ffmpeg == null) {
            throw new IllegalStateException("ffmpeg 不可用");
        }
        double[] pcm = decodePcm(ffmpeg, audio);
        List<int[]> hashes = hashesOf(pcm);
        List<Map<String, Long>> rows = new ArrayList<>(hashes.size());
        for (int[] h : hashes) {
            if (h[1] > 65535) {
                continue;   // pos 列上限保护（约 50 分钟以上歌曲截断尾段）
            }
            rows.add(Map.of("h", h[0] & 0xFFFFFFFFL, "s", songId, "p", (long) h[1]));
        }
        fp.deleteBySong(songId);
        for (int i = 0; i < rows.size(); i += SQL_BATCH) {
            fp.insertBatch(rows.subList(i, Math.min(rows.size(), i + SQL_BATCH)));
        }
        return rows.size();
    }

    /** 入库钩子用：失败只记日志，绝不阻断主流程。 */
    public void ingestQuiet(Long songId, Path audio) {
        try {
            int n = ingest(songId, audio);
            log.info("fingerprint ingest songId={} hashes={}", songId, n);
        } catch (Exception e) {
            log.warn("fingerprint ingest failed songId={}: {}", songId, e.getMessage());
        }
    }

    /** 识别片段：低于 LIKELY 门槛返回 null。 */
    public Match match(Path clip) {
        String ffmpeg = tools.locate(ToolsLocator.FFMPEG);
        if (ffmpeg == null) {
            throw new IllegalStateException("ffmpeg 不可用");
        }
        List<int[]> q = hashesOf(decodePcm(ffmpeg, clip));
        if (q.isEmpty()) {
            return null;
        }
        // key = songId*100000 + (delta+50000)：同曲同偏移的哈希命中聚成一票
        Map<Long, int[]> votes = new HashMap<>();
        for (int i = 0; i < q.size(); i += SQL_BATCH) {
            List<Integer> hs = new ArrayList<>();
            Map<Integer, List<Integer>> byHash = new HashMap<>();
            for (int[] pair : q.subList(i, Math.min(q.size(), i + SQL_BATCH))) {
                hs.add(pair[0]);
                byHash.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair[1]);
            }
            for (Map<String, Object> row : fp.lookup(hs)) {
                long songId = ((Number) row.get("songId")).longValue();
                int refPos = ((Number) row.get("pos")).intValue();
                int hash = ((Number) row.get("hash")).intValue();
                for (int queryPos : byHash.getOrDefault(hash, List.of())) {
                    long key = songId * 100_000L + (refPos - queryPos + 50_000);
                    votes.computeIfAbsent(key, k -> new int[1])[0]++;
                }
            }
        }
        long bestKey = -1;
        int bestVotes = 0;
        for (Map.Entry<Long, int[]> e : votes.entrySet()) {
            if (e.getValue()[0] > bestVotes) {
                bestVotes = e.getValue()[0];
                bestKey = e.getKey();
            }
        }
        if (bestVotes < LIKELY_VOTES || (double) bestVotes / q.size() < LIKELY_RATIO) {
            return null;
        }
        long songId = bestKey / 100_000L;
        int delta = (int) (bestKey % 100_000L) - 50_000;
        return new Match(songId, bestVotes, (double) bestVotes / q.size(),
                delta * HOP / (double) SR);
    }

    /** 管理端状态：已收录歌曲数 + 重建进度。 */
    public Map<String, Object> status() {
        Map<String, Object> m = new HashMap<>(rebuildState);
        m.put("songs", fp.songCount());
        m.put("running", rebuilding.get());
        return m;
    }

    /** 全量重建（后台线程）：已有重建在进行时返回 false。 */
    public boolean startRebuild() {
        if (!rebuilding.compareAndSet(false, true)) {
            return false;
        }
        rebuildPool.submit(() -> {
            List<Map<String, Object>> files;
            try {
                files = songs.listSongFiles();
            } catch (Exception e) {
                log.warn("fingerprint rebuild list failed: {}", e.getMessage());
                files = List.of();
            }
            int ok = 0;
            int fail = 0;
            int total = files.size();
            rebuildState = Map.of("total", total);
            for (Map<String, Object> f : files) {
                long songId = ((Number) f.get("songId")).longValue();
                Path audio = storage.localPath(String.valueOf(f.get("fileUrl")));
                try {
                    if (audio == null || !Files.isRegularFile(audio)) {
                        throw new IllegalStateException("音频文件缺失");
                    }
                    ingest(songId, audio);
                    ok++;
                } catch (Exception e) {
                    fail++;
                    log.warn("fingerprint rebuild songId={} failed: {}", songId, e.getMessage());
                }
                rebuildState = Map.of("total", total, "done", ok + fail, "ok", ok, "fail", fail);
            }
            rebuilding.set(false);
            log.info("fingerprint rebuild done: total={} ok={} fail={}", total, ok, fail);
        });
        return true;
    }

    // ---------- 内部：信号处理 ----------

    /** ffmpeg 解码为 11.025k 单声道 s16le 原始 PCM 并读为 [-1,1) 浮点。 */
    private double[] decodePcm(String ffmpeg, Path audio) {
        Path out = storage.dir("task").resolve("fp_" + System.nanoTime() + ".pcm");
        try {
            ExecResult r = executor.run(List.of(ffmpeg, "-y", "-v", "error", "-i",
                            audio.toString(), "-ac", "1", "-ar", String.valueOf(SR),
                            "-f", "s16le", out.toString()),
                    180_000L, null, null).join();
            if (r.getExitCode() != 0 || !Files.isRegularFile(out)) {
                throw new IllegalStateException("音频解码失败");
            }
            byte[] b = Files.readAllBytes(out);
            short[] s = new short[b.length / 2];
            ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
            double[] x = new double[s.length];
            for (int i = 0; i < s.length; i++) {
                x[i] = s[i] / 32768.0;
            }
            return x;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("音频解码失败：" + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(out);
            } catch (Exception ignored) {
            }
        }
    }

    /** [hash, 锚点帧] 列表（与 Node 验证原型逐行等价）。 */
    private List<int[]> hashesOf(double[] x) {
        int frames = (x.length - FFT) / HOP;
        if (frames <= 2) {
            return List.of();
        }
        double[][] mag = new double[frames][FMAX + 1];
        double[] th = new double[frames];
        double[] re = new double[FFT];
        double[] im = new double[FFT];
        for (int t = 0; t < frames; t++) {
            for (int i = 0; i < FFT; i++) {
                re[i] = x[t * HOP + i] * WIN[i];
                im[i] = 0;
            }
            fft(re, im);
            double mean = 0;
            for (int k = FMIN; k <= FMAX; k++) {
                mag[t][k] = Math.hypot(re[k], im[k]);
                mean += mag[t][k];
            }
            mean /= (FMAX - FMIN + 1);
            double sq = 0;
            for (int k = FMIN; k <= FMAX; k++) {
                sq += (mag[t][k] - mean) * (mag[t][k] - mean);
            }
            th[t] = mean + Math.sqrt(sq / (FMAX - FMIN + 1)) * 1.2;
        }
        List<int[]> peaks = new ArrayList<>();
        for (int t = 1; t < frames - 1; t++) {
            List<Integer> ks = new ArrayList<>();
            List<Double> ms = new ArrayList<>();
            for (int k = FMIN + 5; k <= FMAX - 5; k++) {
                if (mag[t][k] < th[t]) {
                    continue;
                }
                boolean isMax = true;
                outer:
                for (int dt = -1; dt <= 1; dt++) {
                    for (int dk = -5; dk <= 5; dk++) {
                        if (dt == 0 && dk == 0) {
                            continue;
                        }
                        if (mag[t + dt][k + dk] > mag[t][k]) {
                            isMax = false;
                            break outer;
                        }
                    }
                }
                if (isMax) {
                    ks.add(k);
                    ms.add(mag[t][k]);
                }
            }
            // 按幅值降序取前 5（插入排序，候选量级小）
            for (int i = 1; i < ks.size(); i++) {
                for (int j = i; j > 0 && ms.get(j) > ms.get(j - 1); j--) {
                    int tk = ks.set(j, ks.get(j - 1)); ks.set(j - 1, tk);
                    double tm = ms.set(j, ms.get(j - 1)); ms.set(j - 1, tm);
                }
            }
            for (int i = 0; i < Math.min(PEAKS_PER_FRAME, ks.size()); i++) {
                peaks.add(new int[]{t, ks.get(i)});
            }
        }
        peaks.sort((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < peaks.size(); i++) {
            int t1 = peaks.get(i)[0];
            int k1 = peaks.get(i)[1];
            int pairs = 0;
            for (int j = i + 1; j < peaks.size() && pairs < PAIRS_PER_ANCHOR; j++) {
                int t2 = peaks.get(j)[0];
                int k2 = peaks.get(j)[1];
                if (t2 == t1) {
                    continue;
                }
                if (t2 - t1 > MAX_PAIR) {
                    break;
                }
                out.add(new int[]{(k1 * 1024 + k2) * 64 + (t2 - t1), t1});
                pairs++;
            }
        }
        return out;
    }

    /** 就地 radix-2 FFT（与 Node 验证原型一致）。 */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            for (int i = 0; i < n; i += len) {
                for (int k = 0; k < len / 2; k++) {
                    double c = Math.cos(ang * k);
                    double s = Math.sin(ang * k);
                    int a = i + k;
                    int b = i + k + len / 2;
                    double vr = re[b] * c - im[b] * s;
                    double vi = re[b] * s + im[b] * c;
                    re[b] = re[a] - vr;
                    im[b] = im[a] - vi;
                    re[a] += vr;
                    im[a] += vi;
                }
            }
        }
    }
}
