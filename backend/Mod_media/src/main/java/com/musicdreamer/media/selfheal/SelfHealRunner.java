package com.musicdreamer.media.selfheal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicdreamer.media.entity.MediaTask;
import com.musicdreamer.media.mapper.MediaTaskMapper;
import com.musicdreamer.media.service.StorageService;
import com.musicdreamer.media.service.TaskManager;
import com.musicdreamer.media.tools.ToolsLocator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/** 服务重启自愈（设计 10.2 图 10-2）：RUNNING→FAILED、PENDING 保留续跑、清理孤儿临时文件、工具自检。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelfHealRunner implements ApplicationRunner {

    private final MediaTaskMapper mapper;
    private final StorageService storage;
    private final ToolsLocator toolsLocator;
    private final TaskManager taskManager;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 遗留 RUNNING：进程已死，标记失败提示重提
        List<MediaTask> running = mapper.selectList(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getStatus, "RUNNING"));
        for (MediaTask t : running) {
            t.setStatus("FAILED");
            t.setError("服务重启导致任务中断，请重新提交");
            t.setFinishedAt(LocalDateTime.now());
            mapper.updateById(t);
            log.warn("self-heal: task {} -> FAILED (重启中断)", t.getId());
        }

        // 2. 遗留 PENDING：保留，dispatch 启动后自然消费
        long pending = mapper.selectCount(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getStatus, "PENDING"));
        log.info("self-heal: {} PENDING tasks will be resumed", pending);

        // 3. 清理 /data/task 孤儿临时文件（文件名含 _temp）
        Path taskDir = storage.dir("task");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(taskDir, "*_temp*")) {
            int removed = 0;
            for (Path p : stream) {
                Files.deleteIfExists(p);
                removed++;
            }
            if (removed > 0) {
                log.warn("self-heal: removed {} orphan temp files", removed);
            }
        } catch (IOException e) {
            log.warn("self-heal: temp cleanup failed: {}", e.getMessage());
        }

        // 4. 工具自检后开放任务分发
        toolsLocator.selfCheck();
        taskManager.markReady();
        log.info("self-heal done, task dispatcher ready");
    }
}
