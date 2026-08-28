package com.musicdreamer.music.util;

import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/** 请求上下文工具：取客户端 IP（经网关/反向代理优先 X-Forwarded-For）。 */
public final class WebUtil {

    private WebUtil() {}

    public static HttpServletRequest currentRequest() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
