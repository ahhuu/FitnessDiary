package com.cz.fitnessdiary;

import android.app.Application;

import com.cz.fitnessdiary.service.CloudApiClient;
import com.cz.fitnessdiary.service.AiUsageStore;

/** App-level entry point. CloudBase uses HTTPS APIs and needs no client SDK initialization. */
public class FitnessDiaryApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize theme early to avoid flashes during splash screen
        int themeMode = com.cz.fitnessdiary.utils.UnitUtils.getThemeMode(this);
        if (themeMode == com.cz.fitnessdiary.utils.UnitUtils.THEME_LIGHT) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeMode == com.cz.fitnessdiary.utils.UnitUtils.THEME_DARK) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

        CloudApiClient.getInstance().initialize(this);
        AiUsageStore.init(this);
    }
}
