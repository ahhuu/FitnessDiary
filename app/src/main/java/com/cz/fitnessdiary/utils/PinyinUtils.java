package com.cz.fitnessdiary.utils;

import android.icu.text.Transliterator;

/**
 * Pinyin search utility using Android's built-in ICU Transliterator (API 26+).
 */
public class PinyinUtils {

    private static Transliterator pinyinTransliterator;

    private static Transliterator getTransliterator() {
        if (pinyinTransliterator == null) {
            pinyinTransliterator = Transliterator.getInstance("Han-Latin/Names; Latin-Ascii; Lower");
        }
        return pinyinTransliterator;
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

        // 2. Full pinyin match (e.g. "jirou" matches "鸡肉")
        String fullPinyin = toPinyin(lowerName);
        if (fullPinyin.contains(q))
            return true;

        // 3. Pinyin initials match (e.g. "jr" matches "鸡肉")
        String initials = toPinyinInitials(lowerName);
        if (initials.contains(q))
            return true;

        return false;
    }

    /**
     * Convert Chinese text to pinyin with tone numbers removed.
     */
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
