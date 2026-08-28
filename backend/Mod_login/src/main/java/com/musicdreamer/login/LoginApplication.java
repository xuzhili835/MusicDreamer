package com.musicdreamer.login;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Mod_login 用户认证服务（端口 8001）。
 * 注册/激活、登录登出、找回与修改密码、用户信息维护、歌手认证（设计文档第 5 章）。
 */
@SpringBootApplication(scanBasePackages = "com.musicdreamer")
@EnableDiscoveryClient
@MapperScan("com.musicdreamer.login.mapper")
public class LoginApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoginApplication.class, args);
    }
}
