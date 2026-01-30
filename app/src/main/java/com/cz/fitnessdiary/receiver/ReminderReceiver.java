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
 * 负责接收闹钟广播并弹出通知
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "training_reminder_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: action=" + action);

        // 使用 ApplicationContext 避免 Receiver 生命周期限制导致的 Context 泄漏或失效
        Context appContext = context.getApplicationContext();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // 开机自启，恢复闹钟
            ReminderManager.restoreReminder(appContext);
        } else if (ReminderManager.ACTION_REMINDER.equals(action)) {
            // [v1.2] 测试阶段 Toast
            Toast.makeText(appContext, "训练提醒！", Toast.LENGTH_LONG).show();

            // 闹钟触发，显示通知
            showNotification(appContext);
            // 设置明天的闹钟（循环）
            ReminderManager.restoreReminder(appContext);
        }
    }

    private void showNotification(Context context) {
        // [v1.2] 统一渠道创建
        createNotificationChannel(context);

        // 点击通知跳转到 App
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_checkin_filled)
                .setContentTitle("该训练啦！💪")
                .setContentText("今天的训练目标还没完成，点击开始打卡吧。")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏可见性
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat compatManager = NotificationManagerCompat.from(context);
            // 检查权限确认 (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (compatManager.areNotificationsEnabled()) {
                    compatManager.notify(NOTIFICATION_ID, builder.build());
                    Log.d(TAG, "Notification sent via NotificationManagerCompat");
                } else {
                    Log.e(TAG, "Notification permission not granted for background process");
                }
            } else {
                // Android 13 以下不需要 POST_NOTIFICATIONS 运行时权限，但 lint 可能会报错
                // noinspection MissingPermission
                compatManager.notify(NOTIFICATION_ID, builder.build());
                Log.d(TAG, "Notification sent via NotificationManagerCompat (Pre-13)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification: " + e.getMessage());
        }
    }

    private void createNotificationChannel(Context context) {
        // minSdkVersion >= 26，无需检查 Build.VERSION_CODES.O
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "每日训练提醒",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("用于提醒每日健身训练");
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
