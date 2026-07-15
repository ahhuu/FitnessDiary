package com.cz.fitnessdiary.utils;

import android.content.Context;

/** Preferences for optional AI UI details. Reasoning is hidden by default. */
public final class AiDisplayPreferences {
    private static final String PREFS_NAME = "ai_display_settings";
    private static final String KEY_SHOW_REASONING = "show_reasoning";

    private AiDisplayPreferences() {
    }

    public static boolean isReasoningVisible(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SHOW_REASONING, false);
    }

    public static void setReasoningVisible(Context context, boolean visible) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOW_REASONING, visible)
                .apply();
    }
}
