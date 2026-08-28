package com.musicdreamer.login.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮件发送（课设降级策略）：
 * 邮箱未配置（spring.mail.username 为空）时不打断流程，改为日志输出链接/验证码；
 * 发送异常同样仅记录日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    public void sendActivateEmail(String to, String activateLink) {
        String subject = "【悦享音乐】账号激活邮件";
        String text = "欢迎注册悦享音乐！\n请在 24 小时内点击以下链接完成邮箱激活：\n" + activateLink
                + "\n如非本人操作请忽略本邮件。";
        send(to, subject, text, "激活链接 " + activateLink);
    }

    public void sendResetCodeEmail(String to, String code) {
        String subject = "【悦享音乐】找回密码验证码";
        String text = "您正在找回密码，验证码：" + code + "，10 分钟内有效。\n如非本人操作请忽略本邮件。";
        send(to, subject, text, "验证码 " + code);
    }

    private void send(String to, String subject, String text, String fallback) {
        if (from == null || from.isBlank()) {
            log.info("[MAIL-未配置] 收件人={} 主题={} | {}", to, subject, fallback);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("[MAIL] 已发送 收件人={} 主题={}", to, subject);
        } catch (Exception e) {
            log.warn("[MAIL] 发送失败(不阻断流程) 收件人={} 原因={} | {}", to, e.getMessage(), fallback);
        }
    }
}
