package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.DailyLog;

import java.util.List;

/**
 * 每日打卡记录数据访问对象 - 2.0 升级版
 * 提供打卡记录的增删改查操作
 * 注意：数据库列名是 "date" 而不是 "log_date"
 */
@Dao
public interface DailyLogDao {

    /**
     * 插入新的打卡记录
     */
    @Insert
    void insert(DailyLog log);

    /**
     * 更新打卡记录
     */
    @Update
    void update(DailyLog log);

    /**
     * 获取指定日期的所有打卡记录
     * 
     * @param date 日期（0点的时间戳）
     */
    @Query("SELECT * FROM daily_log WHERE date = :date")
    LiveData<List<DailyLog>> getLogsByDate(long date);

    /**
     * 根据计划ID和日期查询打卡记录
     */
    @Query("SELECT * FROM daily_log WHERE plan_id = :planId AND date = :date LIMIT 1")
    DailyLog getLogByPlanAndDate(int planId, long date);

    /**
     * 更新打卡完成状态
     */
    @Query("UPDATE daily_log SET is_completed = :isCompleted WHERE logId = :logId")
    void updateCompletionStatus(int logId, boolean isCompleted);

    /**
     * 删除指定日期之前的所有记录（用于数据清理）
     */
    @Query("DELETE FROM daily_log WHERE date < :date")
    void deleteOldLogs(long date);

    /**
     * 获取所有打卡记录（LiveData）
     */
    @Query("SELECT * FROM daily_log ORDER BY date DESC")
    LiveData<List<DailyLog>> getAllLogs();

    /**
     * 获取所有打卡记录（同步）
     */
    @Query("SELECT * FROM daily_log ORDER BY date DESC")
    List<DailyLog> getAllLogsSync();

    /**
     * 获取指定日期范围的打卡记录
     */
    @Query("SELECT * FROM daily_log WHERE date >= :startDate AND date < :endDate ORDER BY date DESC")
    LiveData<List<DailyLog>> getLogsByDateRange(long startDate, long endDate);

    /**
     * 获取总训练天数（不重复的日期数）- Plan 10
     * 用于动态等级系统
     */
    @Query("SELECT COUNT(DISTINCT date) FROM daily_log")
    int getTotalTrainingDays();
}
