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
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.databinding.ActivityMainBinding;
import com.cz.fitnessdiary.utils.ReminderManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private String pendingReminderModuleType;
    private long pendingReminderTargetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        captureReminderRoute(getIntent());
        checkAndRequestPermissions();
        setupDynamicNavigation();

        Executors.newSingleThreadExecutor().execute(() -> AppDatabase.updateOfficialFoodLibrary(getApplicationContext()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureReminderRoute(intent);
        routeToReminderTargetIfNeeded();
    }

    private void captureReminderRoute(Intent intent) {
        if (intent == null) {
            return;
        }
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
        } else if ("MEDICATION".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.medicationRecordDetailFragment;
        } else if ("HABIT".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.habitRecordDetailFragment;
        } else if ("CUSTOM_TRACKER".equalsIgnoreCase(pendingReminderModuleType)) {
            destination = R.id.customCategoryDetailFragment;
            args.putString("title", "自定义分类");
        }
        try {
            navController.navigate(destination, args);
        } catch (Exception ignored) {
        }
        pendingReminderModuleType = null;
        pendingReminderTargetId = 0L;
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean postNotifications = result.getOrDefault(Manifest.permission.POST_NOTIFICATIONS, false);
                if (!Boolean.TRUE.equals(postNotifications) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(this, "未开启通知，您将无法收到训练提醒", Toast.LENGTH_LONG).show();
                }
            });

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!permissionsNeeded.isEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
        }
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
            });
        });
    }

    public void onRegistrationComplete() {
        androidx.navigation.NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph);
        graph.setStartDestination(R.id.mainHomeFragment);
        navController.setGraph(graph);
        routeToReminderTargetIfNeeded();
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
}