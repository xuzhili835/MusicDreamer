package com.musicdreamer.media;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.musicdreamer")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.musicdreamer.media.client")
@EnableScheduling
@MapperScan("com.musicdreamer.media.mapper")
public class MediaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediaApplication.class, args);
    }
}
