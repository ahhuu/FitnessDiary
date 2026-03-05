package com.cz.fitnessdiary.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.ui.MainActivity;
import com.cz.fitnessdiary.utils.ReminderManager;

/**
 * 提醒广播接收器
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "training_reminder_channel";
    private static final int TRAINING_NOTIFICATION_ID = 1001;

    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: action=" + action);

        Context appContext = context.getApplicationContext();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            ReminderManager.restoreReminder(appContext);
            ReminderManager.restoreAll(appContext);
            return;
        }

        if (ReminderManager.ACTION_REMINDER.equals(action)) {
            Toast.makeText(appContext, "训练提醒！", Toast.LENGTH_LONG).show();
            showNotification(appContext, TRAINING_NOTIFICATION_ID, "该训练啦！", "今天的训练目标还没完成，点击开始打卡吧。", null, 0L);
            ReminderManager.restoreReminder(appContext);
            return;
        }

        if (ReminderManager.ACTION_RECORD_REMINDER.equals(action)) {
            long scheduleId = intent.getLongExtra(ReminderManager.EXTRA_SCHEDULE_ID, 0L);
            String moduleType = intent.getStringExtra(ReminderManager.EXTRA_MODULE_TYPE);
            String title = intent.getStringExtra(ReminderManager.EXTRA_TITLE);
            long targetId = intent.getLongExtra(ReminderManager.EXTRA_TARGET_ID, 0L);
            String content = intent.getStringExtra(ReminderManager.EXTRA_CONTENT);

            if (title == null || title.trim().isEmpty()) {
                title = "记录提醒";
            }
            if (content == null || content.trim().isEmpty()) {
                content = "请完成今日记录";
            }

            showNotification(appContext, 2000 + (int) (scheduleId % 500), title, content, moduleType, targetId);
            ReminderManager.restoreAll(appContext);
        }
    }

    private void showNotification(Context context, int notifyId, String title, String content, String moduleType, long targetId) {
        createNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (moduleType != null) {
            intent.putExtra(ReminderManager.EXTRA_MODULE_TYPE, moduleType);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notifyId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_checkin_filled)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat compatManager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (compatManager.areNotificationsEnabled()) {
                    compatManager.notify(notifyId, builder.build());
                } else {
                    Log.e(TAG, "Notification permission not granted");
                }
            } else {
                compatManager.notify(notifyId, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification: " + e.getMessage());
        }
    }

    private void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "每日记录提醒",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("用于提醒每日训练、喝水、用药与自定义记录");
        channel.enableLights(true);
        channel.setLightColor(android.graphics.Color.BLUE);
        channel.enableVibration(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}
