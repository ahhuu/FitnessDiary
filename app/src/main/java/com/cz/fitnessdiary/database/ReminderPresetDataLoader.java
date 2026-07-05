package com.cz.fitnessdiary.database;

import android.content.Context;
import android.content.SharedPreferences;

import com.cz.fitnessdiary.database.dao.ReminderScheduleDao;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderPresetDataLoader {

    private static final String PREFS_NAME = "reminder_presets";
    private static final String KEY_PRESETS_LOADED = "presets_loaded_v3";

    private static final String[][] PRESETS = {
            {"☀️ 健康简报推送", "morning_summary", "8", "0", "0,1,2,3,4,5,6", "0", "每日健康简报推送"},
            {"🌙 晚间记录提醒", "evening_reminder", "20", "0", "0,1,2,3,4,5,6", "1", ""},
            {"💧 饮水提醒", "water", "10", "0", "0,1,2,3,4,5,6", "2", ""},
            {"💊 服药打卡提醒", "medication", "9", "0", "0,1,2,3,4,5,6", "3", ""},
            {"🏃 训练提醒", "training", "19", "0", "0,1,2,3,4,5,6", "4", ""},
            {"📊 健康周报", "weekly_report", "9", "0", "1", "5", ""},
    };

    public static void loadIfNeeded(Context context) {
        if (context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PRESETS_LOADED, false)) {
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
            ReminderScheduleDao dao = db.reminderScheduleDao();

            int presetCount = dao.countByPreset(true);
            if (presetCount > 0) {
                // 版本号变了 → 删除旧预设重新插入
                dao.deleteByPreset(true);
            }

            for (String[] p : PRESETS) {
                String content = p.length > 6 ? p[6] : "";
                ReminderSchedule schedule = new ReminderSchedule(
                        p[1],
                        0,
                        Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]),
                        p[4],
                        true,
                        p[0],
                        content,
                        true,
                        Integer.parseInt(p[5])
                );
                dao.insert(schedule);
            }

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_PRESETS_LOADED, true).apply();
        });
        executor.shutdown();
    }
}
