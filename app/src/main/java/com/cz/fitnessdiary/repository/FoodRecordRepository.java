package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.FoodRecordDao;
import com.cz.fitnessdiary.database.entity.FoodRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 食物记录数据仓库
 * 管理饮食记录的增删改查操作
 */
public class FoodRecordRepository {
    
    private FoodRecordDao foodRecordDao;
    private ExecutorService executorService;
    
    public FoodRecordRepository(Application application) {
        AppDatabase database = AppDatabase.getInstance(application);
        foodRecordDao = database.foodRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 获取指定日期范围的食物记录
     */
    public LiveData<List<FoodRecord>> getFoodRecordsByDateRange(long startDate, long endDate) {
        return foodRecordDao.getFoodRecordsByDateRange(startDate, endDate);
    }
    
    /**
     * 获取指定日期范围的总热量
     */
    public LiveData<Integer> getTotalCaloriesByDateRange(long startDate, long endDate) {
        return foodRecordDao.getTotalCaloriesByDateRange(startDate, endDate);
    }
    
    /**
     * 获取指定日期范围的食物记录（别名方法）
     */
    public LiveData<List<FoodRecord>> getRecordsByDateRange(long startDate, long endDate) {
        return foodRecordDao.getFoodRecordsByDateRange(startDate, endDate);
    }
    
    /**
     * 插入新的食物记录
     */
    public void insert(FoodRecord foodRecord) {
        executorService.execute(() -> foodRecordDao.insert(foodRecord));
    }
    
    /**
     * 删除食物记录
     */
    public void delete(FoodRecord foodRecord) {
        executorService.execute(() -> foodRecordDao.delete(foodRecord));
    }
    
    /**
     * 获取所有食物记录
     */
    public LiveData<List<FoodRecord>> getAllFoodRecords() {
        return foodRecordDao.getAllFoodRecords();
    }
}
