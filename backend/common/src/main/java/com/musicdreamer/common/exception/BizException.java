package com.musicdreamer.common.exception;

import com.musicdreamer.common.api.ErrorCode;
import lombok.Getter;

/** 业务异常：携带错误码，由全局异常处理器统一转成 Mess 响应。 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode ec) {
        super(ec.getMessage());
        this.code = ec.getCode();
    }

    public BizException(ErrorCode ec, String message) {
        super(message);
        this.code = ec.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
