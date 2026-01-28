package com.cz.fitnessdiary.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.UserDao;
import com.cz.fitnessdiary.databinding.ActivityMainBinding;

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

        // 初始化时不直接设置 NavigationUI，而是先确定 Graph
        setupDynamicNavigation();
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
}
