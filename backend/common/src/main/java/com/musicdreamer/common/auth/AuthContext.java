package com.musicdreamer.common.auth;

import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.exception.BizException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 下游服务的用户上下文：读取网关透传的 X-User-Id / X-User-Role 请求头。
 * 网关已剥离外部伪造的同名头，服务内可信。
 */
public final class AuthContext {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USERNAME = "X-Username";

    public static final int ROLE_USER = 0;
    public static final int ROLE_SINGER = 1;
    public static final int ROLE_ADMIN = 2;

    private AuthContext() {}

    private static String header(String name) {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        return attrs.getRequest().getHeader(name);
    }

    /** 未登录返回 null（匿名可读接口用）。 */
    public static Long getUserId() {
        String v = header(HEADER_USER_ID);
        return v == null || v.isBlank() ? null : Long.valueOf(v);
    }

    /** 未登录返回 -1。 */
    public static int getRole() {
        String v = header(HEADER_USER_ROLE);
        return v == null || v.isBlank() ? -1 : Integer.parseInt(v);
    }

    public static String getUsername() {
        return header(HEADER_USERNAME);
    }

    /** 要求已登录，返回 userId，否则抛 401（2006 语义）。 */
    public static Long requireLogin() {
        Long uid = getUserId();
        if (uid == null) throw new BizException(2001, "用户不存在");
        return uid;
    }

    /** 要求管理员（role=2）。 */
    public static void requireAdmin() {
        if (getRole() != ROLE_ADMIN) throw new BizException(ErrorCode.NO_PERMISSION);
    }

    /** 要求上传者（歌手或管理员，role >= 1）。 */
    public static void requireUploader() {
        if (getRole() < ROLE_SINGER) throw new BizException(ErrorCode.NO_PERMISSION);
    }
}
