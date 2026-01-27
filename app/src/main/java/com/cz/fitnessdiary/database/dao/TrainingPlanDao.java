package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.TrainingPlan;

import java.util.List;

/**
 * 训练计划数据访问对象
 * 提供训练计划的增删改查操作
 */
@Dao
public interface TrainingPlanDao {

    /**
     * 插入新的训练计划
     */
    @Insert
    void insert(TrainingPlan plan);

    /**
     * 更新训练计划 (Plan 10: 用于编辑计划功能)
     */
    @Update
    void update(TrainingPlan plan);

    /**
     * 删除训练计划
     */
    @Delete
    void delete(TrainingPlan plan);

    /**
     * 获取所有训练计划
     * 使用 LiveData 实现自动更新
     */
    @Query("SELECT * FROM training_plan ORDER BY create_time DESC")
    LiveData<List<TrainingPlan>> getAllPlans();

    /**
     * 获取所有训练计划 (同步方法)
     */
    @Query("SELECT * FROM training_plan ORDER BY create_time DESC")
    List<TrainingPlan> getAllPlansList();

    /**
     * 根据ID获取训练计划
     */
    @Query("SELECT * FROM training_plan WHERE planId = :planId")
    TrainingPlan getPlanById(int planId);

    /**
     * 批量更新分类名称 (Plan 28)
     */
    @Query("UPDATE training_plan SET category = :newCategory WHERE category = :oldCategory")
    void updateCategory(String oldCategory, String newCategory);
}
