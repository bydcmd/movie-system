package com.movie.backend.common;

public enum TrendPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    TOTAL;

    public static TrendPeriod from(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        for (TrendPeriod period : TrendPeriod.values()) {
            if (period.name().equals(normalized)) {
                return period;
            }
        }
        return null;
    }
}
