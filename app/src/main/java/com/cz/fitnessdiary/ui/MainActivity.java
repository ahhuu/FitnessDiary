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
        
        // 使用 ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 获取 NavController
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }
        
        // 设置底部导航栏
        NavigationUI.setupWithNavController(binding.bottomNav, navController);
        
        // 检查用户是否已注册
        checkUserRegistration();
    }
    
    /**
     * 检查用户注册状态
     * 如果未注册，导航到欢迎页
     */
    private void checkUserRegistration() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase database = AppDatabase.getInstance(getApplicationContext());
            UserDao userDao = database.userDao();
            int registeredCount = userDao.getRegisteredUserCount();
            
            runOnUiThread(() -> {
                if (registeredCount == 0) {
                    // 未注册，导航到欢迎页，并隐藏底部导航栏
                    binding.bottomNav.setVisibility(android.view.View.GONE);
                    if (navController != null) {
                        navController.navigate(R.id.welcomeFragment);
                    }
                } else {
                    // 已注册，显示底部导航栏
                    binding.bottomNav.setVisibility(android.view.View.VISIBLE);
                }
            });
        });
    }
    
    /**
     * 注册完成后调用，显示底部导航栏并导航到首页
     */
    public void onRegistrationComplete() {
        binding.bottomNav.setVisibility(android.view.View.VISIBLE);
        if (navController != null) {
            navController.navigate(R.id.checkInFragment);
        }
    }
}
