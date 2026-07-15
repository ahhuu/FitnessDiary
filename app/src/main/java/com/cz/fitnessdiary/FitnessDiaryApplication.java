package com.cz.fitnessdiary;

import android.app.Application;

import com.cz.fitnessdiary.service.CloudApiClient;
import com.cz.fitnessdiary.service.AiUsageStore;

/** App-level entry point. CloudBase uses HTTPS APIs and needs no client SDK initialization. */
public class FitnessDiaryApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CloudApiClient.getInstance().initialize(this);
        AiUsageStore.init(this);
    }
}
