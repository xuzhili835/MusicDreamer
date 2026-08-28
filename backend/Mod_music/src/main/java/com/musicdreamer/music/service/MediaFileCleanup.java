package com.musicdreamer.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 删除歌曲时的磁盘文件清理：调 Mod_media 内部接口删除音频/封面/歌词。
 * Mod_music 未引 Feign，用 JDK HttpClient 直连（Docker 下经 media.base-url 环境变量指向容器名）。
 * 尽力而为：DB 行已删，清理失败最多留下孤儿文件，不影响正确性，仅记日志。
 */
@Slf4j
@Service
public class MediaFileCleanup {

    @Value("${media.base-url:http://localhost:8010}")
    private String baseUrl;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper json = new ObjectMapper();

    public void deleteFiles(String fileUrl, String coverUrl, String lyricUrl) {
        try {
            Map<String, String> body = new HashMap<>();
            if (fileUrl != null && !fileUrl.isBlank()) body.put("fileUrl", fileUrl);
            if (coverUrl != null && !coverUrl.isBlank()) body.put("coverUrl", coverUrl);
            if (lyricUrl != null && !lyricUrl.isBlank()) body.put("lyricUrl", lyricUrl);
            if (body.isEmpty()) return;

            HttpRequest req = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/api/v1/media/internal/delete-files"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("delete-files 非 200：{} {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("歌曲文件清理失败（不影响删除结果）：{}", e.getMessage());
        }
    }
}
