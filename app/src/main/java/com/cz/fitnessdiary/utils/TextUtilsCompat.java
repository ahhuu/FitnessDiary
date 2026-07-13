package com.cz.fitnessdiary.utils;

/** Small Java 8-compatible text helpers for the app's API 26 minimum. */
public final class TextUtilsCompat {
    private TextUtilsCompat() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
