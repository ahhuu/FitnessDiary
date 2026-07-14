package com.cz.fitnessdiary.utils;

import android.os.Build;
import androidx.annotation.RequiresApi;

/**
 * Pinyin search utility using Android's built-in ICU Transliterator (API 29+).
 * Safely degrades on API 26-28.
 */
public class PinyinUtils {

    // Use Object to prevent Class verification errors on API < 29
    private static Object pinyinTransliterator;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static android.icu.text.Transliterator getTransliterator() {
        if (pinyinTransliterator == null) {
            pinyinTransliterator = android.icu.text.Transliterator.getInstance("Han-Latin/Names; Latin-Ascii; Lower");
        }
        return (android.icu.text.Transliterator) pinyinTransliterator;
    }

    private PinyinUtils() {
    }

    /**
     * Check if a food name matches the search query.
     * Supports: exact Chinese match, pinyin full match, pinyin initial match.
     */
    public static boolean matches(String foodName, String query) {
        if (foodName == null || query == null) return true;
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) return true;

        String lowerName = foodName.toLowerCase();

        // 1. Direct match
        if (lowerName.contains(q))
            return true;

        // On API < 29, transliterator is not available, so we just fall back to direct match
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 2. Full pinyin match (e.g. "jirou" matches "鸡肉")
            String fullPinyin = toPinyin(lowerName);
            if (fullPinyin.contains(q))
                return true;

            // 3. Pinyin initials match (e.g. "jr" matches "鸡肉")
            String initials = toPinyinInitials(lowerName);
            if (initials.contains(q))
                return true;
        }

        return false;
    }

    /**
     * Convert Chinese text to pinyin with tone numbers removed.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static String toPinyin(String text) {
        try {
            return getTransliterator().transliterate(text).replaceAll("[^a-z]", "");
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * Get pinyin initials only (first letter of each character's pinyin).
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static String toPinyinInitials(String text) {
        try {
            String pinyin = getTransliterator().transliterate(text);
            StringBuilder initials = new StringBuilder();
            boolean startOfWord = true;
            for (int i = 0; i < pinyin.length(); i++) {
                char c = pinyin.charAt(i);
                if (c >= 'a' && c <= 'z') {
                    if (startOfWord) {
                        initials.append(c);
                        startOfWord = false;
                    }
                } else {
                    startOfWord = true;
                }
            }
            return initials.toString();
        } catch (Exception e) {
            return text;
        }
    }
}
