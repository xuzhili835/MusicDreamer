package com.musicdreamer.like;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Mod_like 收藏点赞服务（:8009）。
 * 承载 F019—F022：歌曲收藏写 collect 表，
 * 冗余计数经 Feign 通知 musicService8002 维护 song.collect_count。
 */
@SpringBootApplication(scanBasePackages = "com.musicdreamer")
@EnableDiscoveryClient
@MapperScan("com.musicdreamer.like.mapper")
@EnableFeignClients(basePackages = "com.musicdreamer.like.client")
public class LikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LikeApplication.class, args);
    }
}
