package com.cz.fitnessdiary.ui.guide;

import android.content.Context;
import android.content.SharedPreferences;

public class GuideStateManager {
    private static final String PREF_NAME = "guide_state";
    private static final String KEY_GLOBAL_DONE = "global_onboarding_done";
    private static final String KEY_PAGE_PREFIX = "page_guide_done_";
    private final SharedPreferences prefs;

    public GuideStateManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isGlobalOnboardingDone() {
        return prefs.getBoolean(KEY_GLOBAL_DONE, false);
    }

    public void markGlobalOnboardingDone() {
        prefs.edit().putBoolean(KEY_GLOBAL_DONE, true).apply();
    }

    public boolean isPageGuideDone(String pageKey) {
        return prefs.getBoolean(KEY_PAGE_PREFIX + pageKey, false);
    }

    public void markPageGuideDone(String pageKey) {
        prefs.edit().putBoolean(KEY_PAGE_PREFIX + pageKey, true).apply();
    }

    /** Reset all guides (for testing or "show tips again" option) */
    public void resetAll() {
        prefs.edit().clear().apply();
    }
}
