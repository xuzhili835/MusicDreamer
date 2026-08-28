package com.musicdreamer.setting;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Mod_setting 设置中心服务（端口 8005）。
 * sys_setting 配置读写（60 秒内存缓存）与 announcement 公告管理（设计文档第 11 章）。
 */
@SpringBootApplication(scanBasePackages = "com.musicdreamer")
@EnableDiscoveryClient
@MapperScan("com.musicdreamer.setting.mapper")
public class SettingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettingApplication.class, args);
    }
}
