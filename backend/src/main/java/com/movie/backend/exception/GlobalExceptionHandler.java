package com.movie.backend.exception;

import com.movie.backend.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    public ResponseEntity<Result<String>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数验证失败: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 处理 @Validated 参数验证异常（用于 @RequestParam 和 @PathVariable）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<String>> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数约束违反: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 处理表单绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<String>> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("绑定异常: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 处理缺少必要请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<String>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = String.format("缺少必要参数 '%s' (%s)", e.getParameterName(), e.getParameterType());
        log.warn("请求参数缺失: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 处理参数类型不匹配异常（如枚举转换失败）
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<String>> handleMethodArgumentTypeMismatchException(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            Object[] constants = requiredType.getEnumConstants();
            String options = java.util.Arrays.stream(constants).map(Object::toString).collect(Collectors.joining(", "));
            return buildResponse(HttpStatus.BAD_REQUEST, String.format("参数 '%s' 取值非法，可选值: %s", e.getName(), options));
        }
        return buildResponse(HttpStatus.BAD_REQUEST, String.format("参数 '%s' 类型不正确", e.getName()));
    }

    /**
     * 处理业务异常（可自定义 BusinessException）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<String>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 处理显式业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<String>> handleBusinessException(BusinessException e) {
        HttpStatus status = HttpStatus.resolve(e.getCode());
        HttpStatus resolvedStatus = status == null ? HttpStatus.BAD_REQUEST : status;
        log.warn("业务异常 [{}]: {}", resolvedStatus.value(), e.getMessage());
        return buildResponse(resolvedStatus, e.getMessage());
    }

    /**
     * 处理 Redis 缓存超时异常
     */
    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<Result<String>> handleQueryTimeoutException(QueryTimeoutException e) {
        log.error("Redis 缓存超时: {}", e.getMessage(), e);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "缓存服务暂时不可用，请稍后重试");
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
    public ResponseEntity<Result<String>> handleUnauthorizedException(UnauthorizedException e) {
        log.warn("未认证访问: {}", e.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * 处理业务层主动抛出的禁止访问异常（403）
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Result<String>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException e) {
        log.warn("禁止访问: {}", e.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage() == null ? "无权限访问" : e.getMessage());
    }

    /**
     * 处理资源不存在异常（404）
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Result<String>> handleNotFoundException(Exception e) {
        String message = e.getMessage() == null ? "请求资源不存在" : e.getMessage();
        log.warn("资源不存在: {}", message);
        return buildResponse(HttpStatus.NOT_FOUND, message);
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<String>> handleRuntimeException(RuntimeException e) {
        log.error("运行时错误: ", e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");
    }

    /**
     * 处理其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<String>> handleException(Exception e) {
        log.error("系统错误: ", e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "系统错误");
    }

    private ResponseEntity<Result<String>> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Result.fail(status.value(), message));
    }
}


