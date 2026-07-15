package com.cz.fitnessdiary.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.cz.fitnessdiary.database.entity.User;

/**
 * 本地图片识别使用次数展示缓存，不承担真实额度或会员权益校验。
 * 服务端代理必须按认证账号执行额度和会员校验。
 */
public final class FoodImageQuotaStore {
    private static final String PREFS_NAME = "food_image_quota";
    private static final String COUNT_PREFIX = "count_";

    private final SharedPreferences preferences;

    public FoodImageQuotaStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getUsedCount(@Nullable User user) {
        if (user == null) return 0;
        return preferences.getInt(COUNT_PREFIX + userKey(user), 0);
    }

    /** Records a request for local display only; the server remains authoritative. */
    public synchronized void recordAttempt(@Nullable User user) {
        if (user == null) return;
        String key = userKey(user);
        int used = preferences.getInt(COUNT_PREFIX + key, 0);
        preferences.edit().putInt(COUNT_PREFIX + key, used + 1).apply();
    }

    private String userKey(@Nullable User user) {
        if (user == null) return "";
        if (user.getCloudUserId() != null && !user.getCloudUserId().trim().isEmpty()) {
            return "cloud_" + user.getCloudUserId().trim();
        }
        return "local_" + user.getUid();
    }
}
