package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.DailyLogDao;
import com.cz.fitnessdiary.database.entity.DailyLog;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 每日打卡记录数据仓库
 * 管理打卡记录的增删改查操作
 */
public class DailyLogRepository {
    
    private DailyLogDao dailyLogDao;
    private ExecutorService executorService;
    
    public DailyLogRepository(Application application) {
        AppDatabase database = AppDatabase.getInstance(application);
        dailyLogDao = database.dailyLogDao();
        executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 获取指定日期的打卡记录
     */
    public LiveData<List<DailyLog>> getLogsByDate(long date) {
        return dailyLogDao.getLogsByDate(date);
    }
    
    /**
     * 插入新的打卡记录
     */
    public void insert(DailyLog log) {
        executorService.execute(() -> dailyLogDao.insert(log));
    }
    
    /**
     * 更新打卡记录
     */
    public void update(DailyLog log) {
        executorService.execute(() -> dailyLogDao.update(log));
    }
    
    /**
     * 更新打卡完成状态
     */
    public void updateCompletionStatus(int logId, boolean isCompleted) {
        executorService.execute(() -> dailyLogDao.updateCompletionStatus(logId, isCompleted));
    }
    
    /**
     * 根据计划ID和日期查询打卡记录
     */
    public DailyLog getLogByPlanAndDate(int planId, long date) {
        try {
            return executorService.submit(() -> dailyLogDao.getLogByPlanAndDate(planId, date)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 获取所有打卡记录（LiveData）
     */
    public LiveData<List<DailyLog>> getAllLogs() {
        return dailyLogDao.getAllLogs();
    }
    
    /**
     * 获取所有打卡记录（同步，用于后台线程）
     */
    public List<DailyLog> getAllLogsSync() {
        return dailyLogDao.getAllLogsSync();
    }
    
    /**
     * 获取指定日期范围的打卡记录
     */
    public LiveData<List<DailyLog>> getLogsByDateRange(long startDate, long endDate) {
        return dailyLogDao.getLogsByDateRange(startDate, endDate);
    }
}
