package com.movie.backend.common;

/**
 * 用户账号状态常量。
 * 为兼容历史数据，null 按正常状态处理。
 */
public final class UserStatus {

    public static final int ACTIVE = 0;
    public static final int FROZEN = 1;
    public static final int CANCELLED = 2;

    private UserStatus() {
    }

    public static boolean isActive(Integer status) {
        return status == null || status == ACTIVE;
    }

    public static boolean isFrozen(Integer status) {
        return status != null && status == FROZEN;
    }

    public static boolean isCancelled(Integer status) {
        return status != null && status == CANCELLED;
    }
}
