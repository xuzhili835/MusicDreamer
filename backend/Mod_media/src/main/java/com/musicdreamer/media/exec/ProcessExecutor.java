package com.musicdreamer.media.exec;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 进程执行器：一切外部工具调用的唯一出口（设计 10.1 节六条强约束）。
 * 一、数组参数：cmd 为参数列表，绝不拼接 shell 字符串。
 * 二、全异步：ProcessBuilder + CompletableFuture，严禁同步阻塞。
 * 三、stderr 必须收集：单独守护线程读取（ffmpeg ebur128 结果在 stderr）。
 * 四、stdout 按行回调 + 累计缓冲上限 10MB（防 dump-json 巨输出）。
 * 五、超时定时器强制销毁；cancel 按 cancelKey kill 登记进程。
 * 六、输出统一 UTF-8，stdout 与 stderr 分离收集。
 */
@Slf4j
@Component
public class ProcessExecutor {

    private static final int MAX_BUFFER = 10 * 1024 * 1024;

    private final Map<String, Process> running = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "exec-timer");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger aliveGauge;

    public ProcessExecutor(MeterRegistry registry) {
        this.aliveGauge = registry.gauge("media_process_running", new AtomicInteger(0));
    }

    public CompletableFuture<ExecResult> run(List<String> cmd, long timeoutMs,
                                             Consumer<String> onLine, String cancelKey) {
        CompletableFuture<ExecResult> future = new CompletableFuture<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false); // 六：双流分离
            Process process = pb.start();
            if (cancelKey != null) {
                running.put(cancelKey, process);
            }
            aliveGauge.incrementAndGet();

            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread outThread = readStream(process.getInputStream(), line -> {
                if (out.length() < MAX_BUFFER) {
                    out.append(line).append('\n');
                }
                if (onLine != null) {
                    onLine.accept(line); // 四：按行回调
                }
            });
            Thread errThread = readStream(process.getErrorStream(), line -> {
                if (err.length() < MAX_BUFFER) {
                    err.append(line).append('\n');
                }
            });

            ScheduledFuture<?> timer = timers.schedule(() -> { // 五：超时强杀
                process.destroyForcibly();
            }, timeoutMs, TimeUnit.MILLISECONDS);

            CompletableFuture.runAsync(() -> {
                try {
                    int code = process.waitFor();
                    timer.cancel(false);
                    outThread.join(5000);
                    errThread.join(5000);
                    boolean timedOut = !timer.isCancelled() && code != 0
                            && !process.isAlive() && process.exitValue() != 0 && timer.isDone();
                    future.complete(ExecResult.builder()
                            .exitCode(code)
                            .stdout(out.toString())
                            .stderr(err.toString())
                            .timedOut(code != 0 && timerDone(timer))
                            .build());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.complete(ExecResult.builder().exitCode(-1)
                            .stdout(out.toString()).stderr(err.toString())
                            .timedOut(false).killed(true).build());
                } finally {
                    if (cancelKey != null) {
                        running.remove(cancelKey);
                    }
                    aliveGauge.decrementAndGet();
                }
            }, runnable -> {
                Thread t = new Thread(runnable, "exec-wait-" + (cancelKey == null ? "?" : cancelKey));
                t.setDaemon(true);
                t.start();
            });
        } catch (Exception e) {
            log.error("process start failed: {}", cmd, e);
            future.completeExceptionally(e);
        }
        return future;
    }

    private boolean timerDone(ScheduledFuture<?> timer) {
        try {
            return timer.get(0, TimeUnit.MILLISECONDS) == null;
        } catch (Exception e) {
            return false;
        }
    }

    private Thread readStream(java.io.InputStream is, Consumer<String> onLine) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) { // 六：UTF-8
                String line;
                while ((line = reader.readLine()) != null) {
                    onLine.accept(line);
                }
            } catch (Exception ignored) {
                // 进程被 kill 时流关闭，属正常
            }
        }, "exec-read");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** 取消：销毁登记进程（调用方负责清理临时文件与状态流转）。 */
    public boolean cancel(String cancelKey) {
        Process p = running.remove(cancelKey);
        if (p != null) {
            p.destroyForcibly();
            aliveGauge.decrementAndGet();
            return true;
        }
        return false;
    }

    public boolean isRunning(String cancelKey) {
        return running.containsKey(cancelKey);
    }
}
