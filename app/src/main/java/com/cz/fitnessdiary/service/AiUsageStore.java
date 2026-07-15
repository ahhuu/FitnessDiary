package com.cz.fitnessdiary.service;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small local usage ledger. It stores counters only, never prompts, images or Base64 data. */
public final class AiUsageStore {
    private static final String PREFS = "ai_usage_store";
    private static final String KEY_DAY = "day";
    private static final String KEY_MONTH = "month";
    private static final String KEY_DAY_PROMPT = "day_prompt";
    private static final String KEY_DAY_COMPLETION = "day_completion";
    private static final String KEY_MONTH_PROMPT = "month_prompt";
    private static final String KEY_MONTH_COMPLETION = "month_completion";
    private static final String KEY_CACHE_HIT = "cache_hit";
    private static Context appContext;

    private AiUsageStore() {
    }

    public static synchronized void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static synchronized void record(String provider, int promptTokens, int completionTokens,
            int cacheHitTokens) {
        if (appContext == null) return;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String month = day.substring(0, 7);
        SharedPreferences.Editor editor = prefs.edit();
        boolean sameDay = day.equals(prefs.getString(KEY_DAY, ""));
        boolean sameMonth = month.equals(prefs.getString(KEY_MONTH, ""));
        int dayPrompt = sameDay ? prefs.getInt(KEY_DAY_PROMPT, 0) : 0;
        int dayCompletion = sameDay ? prefs.getInt(KEY_DAY_COMPLETION, 0) : 0;
        int monthPrompt = sameMonth ? prefs.getInt(KEY_MONTH_PROMPT, 0) : 0;
        int monthCompletion = sameMonth ? prefs.getInt(KEY_MONTH_COMPLETION, 0) : 0;
        editor.putString(KEY_DAY, day).putString(KEY_MONTH, month);
        editor.putInt(KEY_DAY_PROMPT, dayPrompt + Math.max(0, promptTokens));
        editor.putInt(KEY_DAY_COMPLETION, dayCompletion + Math.max(0, completionTokens));
        editor.putInt(KEY_MONTH_PROMPT, monthPrompt + Math.max(0, promptTokens));
        editor.putInt(KEY_MONTH_COMPLETION, monthCompletion + Math.max(0, completionTokens));
        editor.putInt(KEY_CACHE_HIT, prefs.getInt(KEY_CACHE_HIT, 0) + Math.max(0, cacheHitTokens));
        editor.apply();
    }

}
