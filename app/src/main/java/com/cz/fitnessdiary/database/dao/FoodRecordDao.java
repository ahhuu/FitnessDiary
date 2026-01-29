package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.FoodRecord;

import java.util.List;

/**
 * 食物记录数据访问对象
 * 提供饮食记录的增删改查操作
 */
@Dao
public interface FoodRecordDao {

    /**
     * 插入新的食物记录
     */
    @Insert
    void insert(FoodRecord foodRecord);

    /**
     * 删除食物记录
     */
    @Delete
    void delete(FoodRecord foodRecord);

    /**
     * 获取指定日期范围的食物记录
     * 
     * @param startDate 开始时间戳
     * @param endDate   结束时间戳
     */
    @Query("SELECT * FROM food_record WHERE record_date >= :startDate AND record_date < :endDate ORDER BY record_date DESC")
    LiveData<List<FoodRecord>> getFoodRecordsByDateRange(long startDate, long endDate);

    /**
     * 计算指定日期范围的总热量
     */
    @Query("SELECT SUM(calories) FROM food_record WHERE record_date >= :startDate AND record_date < :endDate")
    LiveData<Integer> getTotalCaloriesByDateRange(long startDate, long endDate);

    /**
     * 获取所有食物记录
     */
    @Query("SELECT * FROM food_record ORDER BY record_date DESC")
    LiveData<List<FoodRecord>> getAllFoodRecords();

    /**
     * 获取所有有记录的时间戳列表 (用于日历高亮)
     */
    @Query("SELECT record_date FROM food_record")
    LiveData<List<Long>> getAllRecordTimestamps();

    /**
     * 获取所有食物记录 (同步方法)
     */
    @Query("SELECT * FROM food_record ORDER BY record_date DESC")
    List<FoodRecord> getAllFoodRecordsSync();

    /**
     * 计算指定日期范围的总热量（同步方法）- Plan 10
     * /**
     * 计算总记录条数 (同步方法) - v1.2
     */
    @Query("SELECT COUNT(*) FROM food_record")
    int getTotalRecordCountSync();

    @Query("SELECT SUM(calories) FROM food_record WHERE record_date >= :startDate AND record_date < :endDate")
    Integer getTotalCaloriesByDateRangeSync(long startDate, long endDate);
}
