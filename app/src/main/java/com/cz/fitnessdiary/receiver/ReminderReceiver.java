package com.cz.fitnessdiary.receiver;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.model.DailyHealthSnapshot;
import com.cz.fitnessdiary.repository.HealthAggregationRepository;
import com.cz.fitnessdiary.ui.MainActivity;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.utils.ReminderManager;
import com.cz.fitnessdiary.utils.SmartReminderHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "training_reminder_channel";
    private static final String CHANNEL_SMART = "smart_reminder_channel";
    private static final int TRAINING_NOTIFICATION_ID = 1001;

    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: action=" + action);

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                    ReminderManager.restoreReminder(appContext);
                    ReminderManager.restoreAll(appContext);
                    ReminderManager.restoreSmartReminders(appContext);
                    return;
                }

                if (ReminderManager.ACTION_REMINDER.equals(action)) {
                    showNotification(appContext, TRAINING_NOTIFICATION_ID, "该训练啦！",
                            "今天的训练目标还没完成，点击开始打卡吧。", null, 0L, CHANNEL_ID);
                    ReminderManager.restoreReminder(appContext);
                    return;
                }

                if (ReminderManager.ACTION_RECORD_REMINDER.equals(action)) {
                    long scheduleId = intent.getLongExtra(ReminderManager.EXTRA_SCHEDULE_ID, 0L);
                    String moduleType = intent.getStringExtra(ReminderManager.EXTRA_MODULE_TYPE);
                    String title = intent.getStringExtra(ReminderManager.EXTRA_TITLE);
                    long targetId = intent.getLongExtra(ReminderManager.EXTRA_TARGET_ID, 0L);
                    String content = intent.getStringExtra(ReminderManager.EXTRA_CONTENT);

                    if (title == null || title.trim().isEmpty()) title = "记录提醒";
                    if (content == null || content.trim().isEmpty()) content = "请完成今日记录";

                    showNotification(appContext, 2000 + (int) (scheduleId % 500), title, content,
                            moduleType, targetId, CHANNEL_ID);
                    ReminderManager.restoreAll(appContext);
                    return;
                }

                // ── Smart notifications ──
                if (ReminderManager.ACTION_MORNING_SUMMARY.equals(action)) {
                    String title = SmartReminderHelper.getMorningTitle();
                    DailyHealthSnapshot snapshot = tryGetTodaySnapshot(appContext, -1);
                    String content = SmartReminderHelper.getMorningContent(appContext, snapshot);
                    showNotification(appContext, 3001, title, content, null, 0L, CHANNEL_SMART);
                    ReminderManager.scheduleMorningSummary(appContext);
                    return;
                }

                if (ReminderManager.ACTION_EVENING_REMINDER.equals(action)) {
                    DailyHealthSnapshot snapshot = tryGetTodaySnapshot(appContext, 0);
                    String title = SmartReminderHelper.getEveningTitle(appContext);
                    String content = SmartReminderHelper.getEveningContent(appContext, snapshot);
                    if (SmartReminderHelper.shouldSendEveningReminder(appContext)) {
                        showNotification(appContext, 3002, title, content, null, 0L, CHANNEL_SMART);
                    }
                    ReminderManager.scheduleEveningReminder(appContext);
                    return;
                }

                if (ReminderManager.ACTION_WEEKLY_REPORT.equals(action)) {
                    String title = "本周健康周报已生成";
                    String content = com.cz.fitnessdiary.utils.WeeklyReportHelper.getSummary(appContext);
                    showNotification(appContext, 3004, title, content, "WEEKLY_REPORT", 0L, CHANNEL_SMART);
                    ReminderManager.restoreAllReminders(appContext);
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling reminder broadcast action=" + action, e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void showNotification(Context context, int notifyId, String title, String content,
                                   String moduleType, long targetId, String channelId) {
        createNotificationChannel(context, channelId);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (moduleType != null) {
            intent.putExtra(ReminderManager.EXTRA_MODULE_TYPE, moduleType);
        }
        if (targetId != 0L) {
            intent.putExtra(ReminderManager.EXTRA_TARGET_ID, targetId);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notifyId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_nav_checkin_filled)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // 设定为高优先级，确保能横幅弹出并发出声音
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat compatManager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    compatManager.notify(notifyId, builder.build());
                } else {
                    Log.e(TAG, "Notification permission not granted");
                }
            } else {
                compatManager.notify(notifyId, builder.build());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException showing notification: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification: " + e.getMessage());
        }
    }

    /**
     * 安全获取健康数据快照，失败时返回 null 以退化到原有文案
     *
     * @param appContext   Application context
     * @param dayOffset    日期偏移：0 今天，-1 昨天，-2 前天……
     * @return DailyHealthSnapshot 或 null
     */
    private DailyHealthSnapshot tryGetTodaySnapshot(Context appContext, int dayOffset) {
        try {
            Application app = (Application) appContext.getApplicationContext();
            HealthAggregationRepository repo = new HealthAggregationRepository(app);
            if (dayOffset == 0) {
                return repo.getTodaySnapshot();
            } else {
                long dateTs = DateUtils.getTodayStartTimestamp() + dayOffset * 86400000L;
                return repo.getDateSnapshot(dateTs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get health snapshot, fallback to default content", e);
            return null;
        }
    }

    private void createNotificationChannel(Context context, String channelId) {
        if (CHANNEL_SMART.equals(channelId)) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_SMART,
                    "智能健康提醒",
                    NotificationManager.IMPORTANCE_HIGH); // 提升为 HIGH
            channel.setDescription("早晨概要、晚间提醒、不活跃挽留、周报推送");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "每日记录提醒",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("用于提醒每日训练、喝水、用药与自定义记录");
        channel.enableLights(true);
        channel.setLightColor(android.graphics.Color.BLUE);
        channel.enableVibration(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}
