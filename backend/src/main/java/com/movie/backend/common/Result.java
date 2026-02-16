package com.movie.backend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统一 API 响应结构
 * @param <T> 数据类型
 */
@Data
@Schema(description = "统一 API 响应结构")
public class Result<T> {

    @Schema(description = "状态码 (200表示成功，其他为错误码)", example = "200")
    private Integer code;

    @Schema(description = "响应消息", example = "Success")
    private String message;

    @Schema(description = "业务数据载荷")
    private T data;

    @Schema(description = "时间戳", example = "1707734400000")
    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public Result(Integer code, String message, T data) {
        this();
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ========== 成功响应 ==========

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "Success", null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "Success", data);
    }

    /**
     * 成功响应（带消息和数据）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    // ========== 失败响应 ==========

    /**
     * 失败响应（带错误码和消息）
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 失败响应（默认 500 错误码）
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 参数错误（400）
     */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /**
     * 未授权（401）
     */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /**
     * 禁止访问（403）
     */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    /**
     * 资源不存在（404）
     */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }

    // ========== 便捷判断方法 ==========

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return code != null && code == 200;
    }

    /**
     * 是否失败
     */
    public boolean isFailed() {
        return !isSuccess();
    }
}
