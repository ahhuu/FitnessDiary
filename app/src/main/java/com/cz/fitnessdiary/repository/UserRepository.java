package com.cz.fitnessdiary.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.UserDao;
import com.cz.fitnessdiary.database.entity.User;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户数据仓库 - 2.0 版本
 * 添加同步查询方法
 */
public class UserRepository {
    
    private UserDao userDao;
    private LiveData<User> user;
    private ExecutorService executorService;
    
    public UserRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        userDao = database.userDao();
        user = userDao.getUser();
        executorService = Executors.newSingleThreadExecutor();
    }
    
    public LiveData<User> getUser() {
        return user;
    }
    
    /**
     * 同步获取用户（在后台线程中调用）
     */
    public User getUserSync() {
        return userDao.getUserSync();
    }
    
    public void insert(User user) {
        executorService.execute(() -> userDao.insert(user));
    }
    
    public void update(User user) {
        executorService.execute(() -> userDao.update(user));
    }
}
