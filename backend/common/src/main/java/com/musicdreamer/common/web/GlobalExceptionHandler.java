package com.musicdreamer.common.web;

import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 全局异常处理：业务异常按错误码返回，未知异常兜底 1000 且不泄漏堆栈。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Mess> biz(BizException e) {
        HttpStatus status = httpStatusOf(e.getCode());
        return ResponseEntity.status(status).body(Mess.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Mess> invalid(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ErrorCode.PARAM_MISSING.getMessage()
                : "[" + fe.getField() + "] " + ErrorCode.PARAM_FORMAT_ERROR.getMessage();
        return ResponseEntity.badRequest()
                .body(Mess.fail(ErrorCode.PARAM_FORMAT_ERROR.getCode(), msg));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Mess> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(Mess.fail(ErrorCode.PARAM_FORMAT_ERROR));
    }

    /** 请求体本身不可读（非法 JSON/非法 UTF-8/字段类型错）：归为 400 参数错误，而非兜底 500。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Mess> notReadable(HttpMessageNotReadableException e) {
        log.warn("request body not readable: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Mess.fail(ErrorCode.PARAM_FORMAT_ERROR.getCode(), "请求体格式错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Mess> unknown(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Mess.fail(ErrorCode.SYSTEM_ERROR));
    }

    /** 错误码到 HTTP 状态码的映射，与设计文档 12.3 节一致。 */
    private HttpStatus httpStatusOf(int code) {
        return switch (code / 1000) {
            case 2 -> switch (code) {
                case 2001 -> HttpStatus.NOT_FOUND;
                case 2002 -> HttpStatus.CONFLICT;
                case 2003, 2006 -> HttpStatus.UNAUTHORIZED;
                default -> HttpStatus.FORBIDDEN;
            };
            case 3 -> switch (code) {
                case 3001 -> HttpStatus.NOT_FOUND;
                case 3002 -> HttpStatus.FORBIDDEN;
                case 3003 -> HttpStatus.GONE;
                case 3004 -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.PAYLOAD_TOO_LARGE; // 3005
            };
            case 4 -> switch (code) {
                case 4001 -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                case 4002, 3005 -> HttpStatus.PAYLOAD_TOO_LARGE;
                case 4003 -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.INSUFFICIENT_STORAGE; // 4004
            };
            default -> HttpStatus.BAD_REQUEST; // 5xxx 参数
        };
    }
}
