package com.musicdreamer.music;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mod_music 音乐业务服务（歌曲/审核/搜索/播放统计/推荐/评论/举报），端口 8002。
 */
@SpringBootApplication(scanBasePackages = "com.musicdreamer")
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.musicdreamer.music.mapper")
public class MusicApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicApplication.class, args);
    }
}
