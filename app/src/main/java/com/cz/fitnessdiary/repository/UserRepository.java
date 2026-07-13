package com.cz.fitnessdiary.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.UserDao;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.ui.widget.HomeWidgetProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户数据仓库 - 2.0 版本
 * 添加同步查询方法
 */
public class UserRepository {
    
    private final UserDao userDao;
    private final LiveData<User> user;
    private final ExecutorService executorService;
    private final Context context;

    public UserRepository(Context context) {
        this.context = context;
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
        executorService.execute(() -> {
            userDao.insert(user);
            HomeWidgetProvider.requestRefresh(context);
        });
    }

    public void update(User user) {
        executorService.execute(() -> {
            userDao.update(user);
            HomeWidgetProvider.requestRefresh(context);
        });
    }

    public void bindCloudAccount(String cloudUserId, long boundAt, OperationCallback callback) {
        executorService.execute(() -> {
            User localUser = userDao.getUserSync();
            if (localUser == null) {
                callback.onComplete(false);
                return;
            }
            callback.onComplete(userDao.bindCloudAccount(localUser.getUid(), cloudUserId, boundAt) == 1);
        });
    }

    public void unbindCloudAccount(String cloudUserId, OperationCallback callback) {
        executorService.execute(() -> {
            User localUser = userDao.getUserSync();
            if (localUser == null) {
                callback.onComplete(false);
                return;
            }
            callback.onComplete(userDao.unbindCloudAccount(localUser.getUid(), cloudUserId) == 1);
        });
    }

    public interface OperationCallback {
        void onComplete(boolean success);
    }
}
