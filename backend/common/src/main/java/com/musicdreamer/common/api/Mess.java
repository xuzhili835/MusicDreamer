package com.musicdreamer.common.api;

import lombok.Data;

import java.io.Serializable;

/**
 * 系统统一响应封装（Mess）。成功 code=0，失败为 1xxx~5xxx 业务错误码。
 */
@Data
public class Mess implements Serializable {

    private int code;
    private String message;
    private Object data;
    private long timestamp = System.currentTimeMillis();
    private String path;
    private String method;

    public static Mess ok() { return ok(null); }

    public static Mess ok(Object data) {
        Mess m = new Mess();
        m.code = ErrorCode.SUCCESS.getCode();
        m.message = "success";
        m.data = data;
        return m;
    }

    public static Mess fail(int code, String message) {
        Mess m = new Mess();
        m.code = code;
        m.message = message;
        return m;
    }

    public static Mess fail(ErrorCode ec) {
        return fail(ec.getCode(), ec.getMessage());
    }

    public boolean isOk() { return code == ErrorCode.SUCCESS.getCode(); }
}
