package com.musicdreamer.upload.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源映射：/data/** -> 存储目录（音频/封面/歌词）。
 * 播放接口下发的 fileUrl（如 /data/music/1.mp3）经网关 /data 路由转发到本服务。
 */
@Configuration
public class WebStaticConfig implements WebMvcConfigurer {

    @Value("${upload.storage-root:./data}")
    private String storageRoot;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        registry.addResourceHandler("/data/**")
                .addResourceLocations("file:" + root.toString().replace('\\', '/') + "/");
    }
}
