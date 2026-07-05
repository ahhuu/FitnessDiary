package com.cz.fitnessdiary.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.ExerciseLibraryDataLoader;
import com.cz.fitnessdiary.database.FoodLibraryDataLoader;
import com.cz.fitnessdiary.database.ReminderPresetDataLoader;
import com.cz.fitnessdiary.databinding.ActivityMainBinding;
import com.cz.fitnessdiary.utils.ReminderManager;
import com.cz.fitnessdiary.utils.UnitUtils;

import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private String pendingReminderModuleType;
    private long pendingReminderTargetId;
    private String pendingShortcutId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Theme must be set before super.onCreate()
        int themeMode = UnitUtils.getThemeMode(this);
        if (themeMode == UnitUtils.THEME_LIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeMode == UnitUtils.THEME_DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        captureReminderRoute(getIntent());
        if (getIntent() != null) pendingShortcutId = getIntent().getStringExtra("shortcut_id");
        setupDynamicNavigation();

        Executors.newSingleThreadExecutor().execute(() -> {
            FoodLibraryDataLoader.loadIfNeeded(getApplicationContext());
            ExerciseLibraryDataLoader.loadIfNeeded(getApplicationContext());
            ReminderPresetDataLoader.loadIfNeeded(getApplicationContext());
            // Schedule all enabled reminders from DB after presets are loaded
            ReminderManager.restoreAllReminders(getApplicationContext());
            if (ReminderManager.isSmartReminderEnabled(getApplicationContext())) {
                ReminderManager.restoreSmartReminders(getApplicationContext());
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureReminderRoute(intent);
        routeToReminderTargetIfNeeded();
    }

    private void captureReminderRoute(Intent intent) {
        if (intent == null) return;
        pendingReminderModuleType = intent.getStringExtra(ReminderManager.EXTRA_MODULE_TYPE);
        pendingReminderTargetId = intent.getLongExtra(ReminderManager.EXTRA_TARGET_ID, 0L);
    }

    private void routeToReminderTargetIfNeeded() {
        if (navController == null || pendingReminderModuleType == null || pendingReminderModuleType.trim().isEmpty()) {
            return;
        }
        int destination = R.id.checkInFragment;
        Bundle args = new Bundle();
        args.putLong("targetId", pendingReminderTargetId);
        if ("WATER".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.waterRecordDetailFragment;
        } else if ("SPORT".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.sportRecordDetailFragment;
        } else if ("DIET".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.dietFragment;
        } else if ("SLEEP".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.sleepRecordDetailFragment;
        } else if ("MEDICATION".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.medicationRecordDetailFragment;
        } else if ("HABIT".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.habitRecordDetailFragment;
        } else if ("CUSTOM_TRACKER".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.customCategoryDetailFragment;
            args.putString("title", "自定义分类");
        } else if ("WEEKLY_REPORT".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.mainHomeFragment;
            args.putBoolean("showWeeklyReport", true);
        }
        try {
            navController.navigate(destination, args);
        } catch (Exception ignored) {
        }
        pendingReminderModuleType = null;
        pendingReminderTargetId = 0L;
    }

    private void routeShortcut() {
        if (pendingShortcutId == null) return;
        int dest = R.id.checkInFragment;
        Bundle args = new Bundle();
        args.putLong("selectedDate", System.currentTimeMillis());
        switch (pendingShortcutId) {
            case "quick_checkin": dest = R.id.sportRecordDetailFragment; break;
            case "quick_diet": dest = R.id.dietFragment; break;
            case "quick_water": dest = R.id.waterRecordDetailFragment; break;
            case "quick_sport": dest = R.id.sportRecordDetailFragment; break;
        }
        try { navController.navigate(dest, args); } catch (Exception ignored) {}
        pendingShortcutId = null;
    }

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!Boolean.TRUE.equals(granted) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(this, "未开启通知，您将无法收到提醒", Toast.LENGTH_LONG).show();
                }
            });

    public boolean requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        return true;
    }

    public boolean checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                new AlertDialog.Builder(this)
                        .setTitle("需要“闹钟与提醒”权限")
                        .setMessage("为了确保提醒准时弹出，应用需要开启精确闹钟权限。请在系统设置中手动开启。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("以后再说", null)
                        .show();
                return true;
            }
        }
        return false;
    }

    private void setupDynamicNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            return;
        }
        navController = navHostFragment.getNavController();

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase database = AppDatabase.getInstance(getApplicationContext());
            int registeredCount = database.userDao().getRegisteredUserCount();
            runOnUiThread(() -> {
                androidx.navigation.NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph);
                graph.setStartDestination(registeredCount == 0 ? R.id.welcomeFragment : R.id.mainHomeFragment);
                navController.setGraph(graph);
                routeToReminderTargetIfNeeded();
                routeShortcut();
            });
        });
    }

    public void onRegistrationComplete() {
        androidx.navigation.NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph);
        graph.setStartDestination(R.id.mainHomeFragment);
        navController.setGraph(graph);
        routeToReminderTargetIfNeeded();
        routeShortcut();
    }

    public void showAutoStartGuidance() {
        if (ReminderManager.isAutoStartGuided(this)) {
            Toast.makeText(this, "训练提醒已开启", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("重要：开启自启动权限")
                .setMessage("为了确保手机在锁屏或后台时能准时收到提醒，建议开启本应用的自启动与后台弹出权限。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    ReminderManager.setAutoStartGuided(this, true);
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "无法自动打开设置，请手动开启", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("我知道了", (dialog, which) -> ReminderManager.setAutoStartGuided(this, true))
                .show();
    }

    @Override
    public void onBackPressed() {
        // 直接操作 NavController 的返回栈，绕过 OnBackPressedDispatcher
        // 解决部分设备/ROM 上 NavHostFragment 的 OnBackPressedCallback
        // 未能正确拦截返回键导致直接 finish Activity 的问题
        if (navController != null && navController.popBackStack()) {
            return;
        }
        super.onBackPressed();
    }
}
