package com.movie.backend.exception;

import com.movie.backend.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 @Valid 参数验证异常（用于 @RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数验证失败: {}", message);
        return Result.fail(400, message);
    }

    /**
     * 处理 @Validated 参数验证异常（用于 @RequestParam 和 @PathVariable）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<String> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数约束违反: {}", message);
        return Result.fail(400, message);
    }

    /**
     * 处理表单绑定异常
     */
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("绑定异常: {}", message);
        return Result.fail(400, message);
    }

    /**
     * 处理缺少必要请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<String> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = String.format("缺少必要参数 '%s' (%s)", e.getParameterName(), e.getParameterType());
        log.warn("请求参数缺失: {}", message);
        return Result.fail(400, message);
    }

    /**
     * 处理参数类型不匹配异常（如枚举转换失败）
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public Result<String> handleMethodArgumentTypeMismatchException(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            Object[] constants = requiredType.getEnumConstants();
            String options = java.util.Arrays.stream(constants).map(Object::toString).collect(Collectors.joining(", "));
            return Result.fail(400, String.format("参数 '%s' 取值非法，可选值: %s", e.getName(), options));
        }
        return Result.fail(400, String.format("参数 '%s' 类型不正确", e.getName()));
    }

    /**
     * 处理业务异常（可自定义 BusinessException）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    /**
     * 处理 Redis 缓存超时异常
     */
    @ExceptionHandler(QueryTimeoutException.class)
    public Result<String> handleQueryTimeoutException(QueryTimeoutException e) {
        log.error("Redis 缓存超时: {}", e.getMessage(), e);
        return Result.fail(503, "缓存服务暂时不可用，请稍后重试");
    }

    /**
     * 处理 IO 异常（通常是客户端连接中断）
     */
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException e) {
        // 客户端连接中断（如用户取消请求、刷新页面等）是正常现象，不需要返回响应
        // 只记录 debug 级别日志，避免污染日志
        if (e.getMessage() != null && e.getMessage().contains("你的主机中的软件中止了一个已建立的连接")) {
            log.debug("客户端连接中断: {}", e.getMessage());
        } else {
            log.warn("IO 异常: {}", e.getMessage());
        }
        // 不返回任何内容，因为连接已断开
    }

/**
     * 处理未认证异常（401）
     */
    @ExceptionHandler(UnauthorizedException.class)
    public Result<String> handleUnauthorizedException(UnauthorizedException e) {
        log.warn("未认证访问: {}", e.getMessage());
        return Result.unauthorized(e.getMessage());
    }

    /**
     * 处理业务层主动抛出的禁止访问异常（403）
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public Result<String> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException e) {
        log.warn("禁止访问: {}", e.getMessage());
        return Result.forbidden(e.getMessage() == null ? "无权限访问" : e.getMessage());
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        log.error("运行时错误: ", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }

    /**
     * 处理其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统错误: ", e);
        return Result.fail(500, "系统错误");
    }
}


