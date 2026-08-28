package com.musicdreamer.common.api;

import lombok.Getter;

/**
 * 全局统一错误码。分段：1xxx 系统、2xxx 用户、3xxx 歌曲、4xxx 上传、5xxx 参数。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),

    SYSTEM_ERROR(1000, "系统内部错误"),
    SERVICE_UNAVAILABLE(1001, "服务暂时不可用"),
    RATE_LIMITED(1002, "接口限流"),
    API_DEPRECATED(1003, "接口版本已废弃"),

    USER_NOT_FOUND(2001, "用户不存在"),
    USERNAME_EXISTS(2002, "用户名已存在"),
    PASSWORD_WRONG(2003, "密码错误"),
    EMAIL_NOT_VERIFIED(2004, "邮箱未验证"),
    USER_DISABLED(2005, "用户已被禁用"),
    TOKEN_EXPIRED(2006, "Token已过期"),
    NO_PERMISSION(2007, "权限不足"),
    WX_CODE_INVALID(2008, "微信登录凭证无效或已过期"),
    WX_ALREADY_BOUND(2009, "该微信已绑定其他账号"),

    SONG_NOT_FOUND(3001, "歌曲不存在"),
    SONG_UNDER_REVIEW(3002, "歌曲审核中不可播放"),
    SONG_TAKEN_DOWN(3003, "歌曲已被下架"),
    SONG_FORMAT_ERROR(3004, "歌曲文件格式错误"),
    SONG_FILE_TOO_LARGE(3005, "歌曲文件过大"),

    FILE_TYPE_UNSUPPORTED(4001, "文件类型不支持"),
    FILE_TOO_LARGE(4002, "文件大小超限"),
    FILE_REVIEW_REJECTED(4003, "文件内容审核不通过"),
    STORAGE_FULL(4004, "存储空间不足"),

    PARAM_MISSING(5001, "必填参数缺失"),
    PARAM_FORMAT_ERROR(5002, "参数格式错误"),
    PARAM_OUT_OF_RANGE(5003, "参数值超出范围");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
