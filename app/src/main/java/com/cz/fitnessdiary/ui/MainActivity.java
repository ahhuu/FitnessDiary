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
import androidx.navigation.ui.NavigationUI;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.UserDao;
import com.cz.fitnessdiary.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主 Activity
 * 包含 NavHostFragment 和 BottomNavigationView
 * 启动时检查用户是否已注册，未注册则显示欢迎页
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // [v1.2] 集中申请权限
        checkAndRequestPermissions();

        // 初始化时不直接设置 NavigationUI，而是先确定 Graph
        setupDynamicNavigation();

        // [v1.2] 启动时同步最新食物库
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.updateOfficialFoodLibrary(getApplicationContext());
        });
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean postNotifications = result.getOrDefault(Manifest.permission.POST_NOTIFICATIONS, false);
                if (Boolean.TRUE.equals(postNotifications)) {
                    // 通知权限已授予
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(this, "未开启通知，您将无法收到训练提醒", Toast.LENGTH_LONG).show();
                }
            });

    /**
     * [v1.2] 检查并集中申请权限（通知、照片、视频）
     */
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // 1. 通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // 2. 媒体图片权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else {
            // Android 12 及以下
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
        }
    }

    /**
     * [v1.2] 针对 Android 12+ 检查并引导开启精确闹钟权限
     * 设置为 public 以便从 ProfileFragment 调用
     * 
     * @return 如果缺失权限并已弹出引导对话框，返回 true
     */
    public boolean checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                new AlertDialog.Builder(this)
                        .setTitle("需要“闹钟与提醒”权限")
                        .setMessage("为了确保训练提醒准时弹出，应用需要开启精确闹钟权限。请在接下来的系统设置中手动开启。")
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

    /**
     * 动态设置导航图
     */
    private void setupDynamicNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            // 异步检查注册状态
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase database = AppDatabase.getInstance(getApplicationContext());
                int registeredCount = database.userDao().getRegisteredUserCount();

                runOnUiThread(() -> {
                    // 获取现有的配置
                    androidx.navigation.NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph);

                    if (registeredCount == 0) {
                        // 未注册：设欢迎页为起点
                        graph.setStartDestination(R.id.welcomeFragment);
                    } else {
                        // 已注册：设主页大容器为起点
                        graph.setStartDestination(R.id.mainHomeFragment);
                    }

                    // 应用导航图
                    navController.setGraph(graph);
                });
            });
        }
    }

    /**
     * 注册完成后调用
     */
    public void onRegistrationComplete() {
        // 重要：重新设置 Graph，将起点改为系统主页
        androidx.navigation.NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph);
        graph.setStartDestination(R.id.mainHomeFragment);
        navController.setGraph(graph);
    }

    /**
     * [v1.2] 针对国产系统弹出自启动引导对话框
     */
    public void showAutoStartGuidance() {
        // [v1.2] 检查是否已经引导过，避免重复打扰
        if (com.cz.fitnessdiary.utils.ReminderManager.isAutoStartGuided(this)) {
            Toast.makeText(this, "训练提醒已开启", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("重要：开启自启动权限")
                .setMessage("为了确保手机在锁屏或后台时能准时收到提醒，建议您在接下来的设置中开启本应用的“自启动”和“后台弹出界面”权限。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    // 标记为已引导
                    com.cz.fitnessdiary.utils.ReminderManager.setAutoStartGuided(this, true);
                    // 尝试跳转到应用详情页
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "无法自动打开设置，请手动开启", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("我知道了", (dialog, which) -> {
                    // 即使不设置，也标记为已引导，避免后续每次开启都弹窗
                    com.cz.fitnessdiary.utils.ReminderManager.setAutoStartGuided(this, true);
                })
                .show();
    }
}
