package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.TrainingPlanDao;
import com.cz.fitnessdiary.database.entity.TrainingPlan;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 训练计划数据仓库
 * 管理训练计划的增删改查操作
 */
public class TrainingPlanRepository {

    private TrainingPlanDao trainingPlanDao;
    private LiveData<List<TrainingPlan>> allPlans;
    private ExecutorService executorService;

    public TrainingPlanRepository(Application application) {
        AppDatabase database = AppDatabase.getInstance(application);
        trainingPlanDao = database.trainingPlanDao();
        allPlans = trainingPlanDao.getAllPlans();
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * 获取所有训练计划
     */
    public LiveData<List<TrainingPlan>> getAllPlans() {
        return allPlans;
    }

    /**
     * 获取所有训练计划 (同步)
     */
    public List<TrainingPlan> getAllPlansSync() {
        try {
            return executorService.submit(() -> trainingPlanDao.getAllPlansList()).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 插入新的训练计划
     */
    public void insert(TrainingPlan plan) {
        executorService.execute(() -> trainingPlanDao.insert(plan));
    }

    public void insertAll(List<TrainingPlan> plans) {
        executorService.execute(() -> trainingPlanDao.insertAll(plans));
    }

    /**
     * [v1.2] 将现有无前缀的分类迁移到 '自定义-' 前缀
     * 保证用户备份恢复的数据归类为自定义，而不混入基础库
     */
    public void migrateLegacyToCustom() {
        executorService.execute(() -> {
            List<TrainingPlan> plans = trainingPlanDao.getAllPlansList();
            if (plans != null) {
                for (TrainingPlan plan : plans) {
                    String cat = plan.getCategory();
                    // 如果没有前缀，说明是老版本数据，归类为自定义
                    if (cat != null && !cat.startsWith("基础-") && !cat.startsWith("进阶-") && !cat.startsWith("自定义-")) {
                        plan.setCategory("自定义-" + cat);
                        trainingPlanDao.update(plan);
                    }
                }
            }
        });
    }

    /**
     * 更新训练计划
     */
    public void update(TrainingPlan plan) {
        executorService.execute(() -> trainingPlanDao.update(plan));
    }

    /**
     * 删除训练计划
     */
    public void delete(TrainingPlan plan) {
        executorService.execute(() -> trainingPlanDao.delete(plan));
    }

    /**
     * 根据 ID 获取训练计划
     */
    public TrainingPlan getPlanById(int planId) {
        try {
            return executorService.submit(() -> trainingPlanDao.getPlanById(planId)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 批量更新分类名称
     */
    public void updateCategory(String oldCategory, String newCategory) {
        executorService.execute(() -> trainingPlanDao.updateCategory(oldCategory, newCategory));
    }
}
