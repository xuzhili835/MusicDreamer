package com.musicdreamer.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(scanBasePackages = "com.musicdreamer")
@EnableDiscoveryClient
@MapperScan("com.musicdreamer.upload.mapper")
public class UploadApplication {
    public static void main(String[] args) {
        SpringApplication.run(UploadApplication.class, args);
    }
}
