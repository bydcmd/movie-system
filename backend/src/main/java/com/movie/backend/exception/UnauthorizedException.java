package com.movie.backend.exception;

/**
 * 未认证异常（401）
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
