package com.movie.backend.exception;

/**
 * 通用业务异常，允许业务层显式返回对应的业务状态码。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
