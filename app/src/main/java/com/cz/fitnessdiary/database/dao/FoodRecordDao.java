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
     * 计算指定日期范围的总热量（同步方法）- Plan 10
     * 用于成就判定
     */
    @Query("SELECT SUM(calories) FROM food_record WHERE record_date >= :startDate AND record_date < :endDate")
    Integer getTotalCaloriesByDateRangeSync(long startDate, long endDate);
}
