package com.musicdreamer.media.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * yt-dlp 网络参数注入（设计 10.4）：必带浏览器 UA；B 站/YouTube 附 Referer；
 * 代理与用户级 cookies 优先；B 站缺 cookies 时自动获取 buvid（缓存 1 小时，412 第二层防护）。
 */
@Slf4j
@Component
public class NetArgs {

    public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private static final Pattern BILIBILI = Pattern.compile(
            "(bilibili\\.com|m\\.bilibili\\.com|b23\\.tv|BV[0-9A-Za-z]{10}|av\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE = Pattern.compile(
            "(youtube\\.com|youtu\\.be)", Pattern.CASE_INSENSITIVE);

    private final SettingsService settings;
    private final StorageService storage;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10)).build();

    private volatile String buvidFile;
    private volatile long buvidAt = 0;

    public NetArgs(SettingsService settings, StorageService storage) {
        this.settings = settings;
        this.storage = storage;
    }

    public List<String> build(String url) {
        List<String> args = new ArrayList<>();
        args.add("--user-agent");
        args.add(UA);
        boolean bili = BILIBILI.matcher(url).find();
        if (bili) {
            args.add("--add-headers");
            args.add("Referer:https://www.bilibili.com/");
        } else if (YOUTUBE.matcher(url).find()) {
            args.add("--add-headers");
            args.add("Referer:https://www.youtube.com/");
        }
        String proxy = settings.downloadProxy();
        if (!proxy.isBlank()) {
            args.add("--proxy");
            args.add(proxy);
        }
        String cookies = settings.cookiesPath();
        if (!cookies.isBlank()) {
            args.add("--cookies");
            args.add(cookies);
        } else if (bili) {
            String file = buvidCookie();
            if (file != null) {
                args.add("--cookies");
                args.add(file);
            }
        }
        return args;
    }

    /** 自动 buvid cookie（Netscape 格式，缓存 1 小时）。 */
    private synchronized String buvidCookie() {
        long now = System.currentTimeMillis();
        if (buvidFile != null && now - buvidAt < 3600_000) {
            return buvidFile;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(
                            URI.create("https://api.bilibili.com/x/frontend/finger/spi"))
                    .header(HttpHeaders.USER_AGENT, UA)
                    .header(HttpHeaders.REFERER, "https://www.bilibili.com/")
                    .timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            com.fasterxml.jackson.databind.JsonNode data =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(resp.body()).path("data");
            String b3 = data.path("b_3").asText(null);
            String b4 = data.path("b_4").asText(null);
            if (b3 == null || b4 == null) {
                return null;
            }
            Path file = storage.dir("task").resolve("bili_cookies.txt");
            long expire = (now + 3600_000) / 1000;
            Files.writeString(file,
                    "# Netscape HTTP Cookie File\n"
                            + ".bilibili.com\tTRUE\t/\tFALSE\t" + expire + "\tbuvid3\t" + b3 + "\n"
                            + ".bilibili.com\tTRUE\t/\tFALSE\t" + expire + "\tbuvid4\t" + b4 + "\n");
            buvidFile = file.toString();
            buvidAt = now;
            return buvidFile;
        } catch (Exception e) {
            log.warn("buvid cookie fetch failed: {}", e.getMessage());
            return null;
        }
    }
}
