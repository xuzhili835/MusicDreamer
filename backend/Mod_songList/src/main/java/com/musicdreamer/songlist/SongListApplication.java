package com.musicdreamer.songlist;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Mod_songList 歌单服务（:8008）。
 * 承载 F023—F030：歌单 CRUD、歌单歌曲管理、歌单收藏、公开歌单广场。
 */
@SpringBootApplication(scanBasePackages = "com.musicdreamer")
@EnableDiscoveryClient
@MapperScan("com.musicdreamer.songlist.mapper")
@EnableFeignClients(basePackages = "com.musicdreamer.songlist.client")
public class SongListApplication {

    public static void main(String[] args) {
        SpringApplication.run(SongListApplication.class, args);
    }
}
