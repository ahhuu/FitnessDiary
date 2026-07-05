package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.DailyLogDao;
import com.cz.fitnessdiary.database.dao.TrainingPlanDao;
import com.cz.fitnessdiary.database.dao.FoodRecordDao;
import com.cz.fitnessdiary.database.dao.UserDao;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.model.Achievement;
import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.utils.CalorieCalculatorUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Profile ViewModel - 用户个人信息管理
 * 核心功能：BMI/BMR 计算、目标管理、数据清除
 */
public class ProfileViewModel extends AndroidViewModel {

    private final UserDao userDao;
    private final ExecutorService executorService;

    // LiveData
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();
    private final MutableLiveData<Double> bmi = new MutableLiveData<>();
    private final MutableLiveData<Integer> bmr = new MutableLiveData<>();
    private final MutableLiveData<Integer> tdee = new MutableLiveData<>();

    // Plan 10 新增字段
    private final MutableLiveData<Integer> totalTrainingDays = new MutableLiveData<>(0); // 总训练天数
    private final MutableLiveData<String> userLevel = new MutableLiveData<>(""); // 用户等级
    private final MutableLiveData<java.util.List<Achievement>> achievements = new MutableLiveData<>(); // 成就列表

    private final DailyLogDao dailyLogDao;
    private final TrainingPlanDao trainingPlanDao;
    private final FoodRecordDao foodRecordDao;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getInstance(application);
        userDao = database.userDao();
        dailyLogDao = database.dailyLogDao();
        trainingPlanDao = database.trainingPlanDao();
        foodRecordDao = database.foodRecordDao();
        executorService = Executors.newSingleThreadExecutor();

        loadUserData();
        loadGameificationData(); // Plan 10: 加载游戏化数据
    }

    /**
     * 加载用户数据
     */
    private void loadUserData() {
        executorService.execute(() -> {
            User user = userDao.getUserSync();
            if (user != null) {
                currentUser.postValue(user);
                calculateMetrics(user);
            }
        });
    }

    /**
     * 计算 BMI、BMR、TDEE
     */
    private void calculateMetrics(User user) {
        // BMI = 体重(kg) / 身高(m)^2
        double heightInMeters = user.getHeight() / 100.0;
        double bmiValue = user.getWeight() / (heightInMeters * heightInMeters);
        bmi.postValue(Math.round(bmiValue * 10) / 10.0);

        // [核心修复] 统一使用工具类计算，确保四舍五入逻辑全局一致
        int bmrValue = CalorieCalculatorUtils.calculateBMR(user.getGender(), user.getWeight(), user.getHeight(),
                user.getAge());
        bmr.postValue(bmrValue);

        // TDEE (每日总消耗) = BMR * 活动系数
        float activityFactor = user.getActivityLevel();
        if (activityFactor <= 0)
            activityFactor = 1.2f;
        int tdeeValue = CalorieCalculatorUtils.calculateTDEE(bmrValue, activityFactor);

        // 根据目标调整推荐热量
        int goalType = 2; // 默认保持
        if ("减脂".equals(user.getGoal())) {
            goalType = CalorieCalculatorUtils.GOAL_LOSE_FAT;
        } else if ("增肌".equals(user.getGoal())) {
            goalType = CalorieCalculatorUtils.GOAL_GAIN_MUSCLE;
        }
        tdeeValue = CalorieCalculatorUtils.calculateTargetCalories(tdeeValue, goalType);

        // 确保 TDEE 不低于基础代谢 (安全性兜底)
        if (tdeeValue < 1200)
            tdeeValue = 1200;

        tdee.postValue(tdeeValue);

        // 更新数据库中的目标热量
        user.setTargetCalories(tdeeValue);

        // === Plan 8 新增: 计算宏量营养素目标 ===
        int targetProtein;
        int targetCarbs;
        int targetFat = Math.max(0, (int) Math.round((tdeeValue * 0.25) / 9.0));

        // 简单估算逻辑
        if ("增肌".equals(user.getGoal())) {
            // 增肌: 蛋白 2.0g/kg, 碳水较高
            targetProtein = (int) (user.getWeight() * 2.0);
            // 剩余热量分配: 蛋白热量 = protein * 4. 假设脂肪占比 25%. 剩余给碳水
            // 简化算法: 直接给一个较高的碳水比例或固定值, 这里按 "剩余热量/4" 粗算
            // 脂肪 = TDEE * 0.25; 碳水 = (TDEE - 蛋白*4 - 脂肪) / 4
            int fatCalories = (int) (tdeeValue * 0.25);
            int proteinCalories = targetProtein * 4;
            int remaining = tdeeValue - fatCalories - proteinCalories;
            targetCarbs = Math.max(0, remaining / 4);

        } else if ("减脂".equals(user.getGoal())) {
            // 减脂: 蛋白 1.5g/kg (保护肌肉), 碳水较低
            targetProtein = (int) (user.getWeight() * 1.5);
            // 脂肪依旧 25%, 剩余给碳水
            int fatCalories = (int) (tdeeValue * 0.25);
            int proteinCalories = targetProtein * 4;
            int remaining = tdeeValue - fatCalories - proteinCalories;
            targetCarbs = Math.max(0, remaining / 4);
        } else {
            // 保持: 蛋白 1.2g/kg
            targetProtein = (int) (user.getWeight() * 1.2);
            int fatCalories = (int) (tdeeValue * 0.25);
            int proteinCalories = targetProtein * 4;
            int remaining = tdeeValue - fatCalories - proteinCalories;
            targetCarbs = Math.max(0, remaining / 4);
        }

        user.setTargetProtein(targetProtein);
        user.setTargetCarbs(targetCarbs);
        user.setTargetFat(targetFat);

        userDao.update(user);
    }

    /**
     * 更新体重
     */
    public void updateWeight(double weight) {
        executorService.execute(() -> {
            User user = userDao.getUserSync();
            if (user != null) {
                user.setWeight((float) weight);
                userDao.update(user);
                currentUser.postValue(user);
                calculateMetrics(user);
            }
        });
    }

    /**
     * 更新身高
     */
    public void updateHeight(int height) {
        executorService.execute(() -> {
            User user = userDao.getUserSync();
            if (user != null) {
                user.setHeight((float) height);
                userDao.update(user);
                currentUser.postValue(user);
                calculateMetrics(user);
            }
        });
    }

    /**
     * 切换目标（减脂/增肌/保持）
     */
    public void updateGoal(String goal) {
        executorService.execute(() -> {
            User user = userDao.getUserSync();
            if (user != null) {
                user.setGoal(goal);
                userDao.update(user);
                currentUser.postValue(user);
                calculateMetrics(user); // 重新计算 TDEE
            }
        });
    }

    /**
     * 更新头像 URI
     */
    public void updateAvatarUri(String avatarUri) {
        executorService.execute(() -> {
            User user = userDao.getUserSync();
            if (user != null) {
                user.setAvatarUri(avatarUri);
                userDao.update(user);
                currentUser.postValue(user);
            }
        });
    }

    /**
     * 更新用户名
     */
    public void updateNickname(String nickname) {
        executorService.execute(() -> {
            User user = userDao.getUserSync();
            if (user != null) {
                user.setNickname(nickname);
                userDao.update(user);
                currentUser.postValue(user);
            }
        });
    }

    /**
     * Plan 34: 更新年龄
     */
    public void updateAge(int age) {
        executorService.execute(() -> {
            User user = userDao.getUserSync();
            if (user != null) {
                user.setAge(age);
                userDao.update(user);
                currentUser.postValue(user);
                calculateMetrics(user);
            }
        });
    }

    /**
     * Plan 34: 更新性别
     */
    public void updateGender(int gender) {
        executorService.execute(() -> {
            User user = userDao.getUserSync();
            if (user != null) {
                user.setGender(gender);
                userDao.update(user);
                currentUser.postValue(user);
                calculateMetrics(user);
            }
        });
    }

    /**
     * Plan 34: 更新活动水平
     */
    public void updateActivityLevel(float activityLevel) {
        executorService.execute(() -> {
            User user = userDao.getUserSync();
            if (user != null) {
                user.setActivityLevel(activityLevel);
                userDao.update(user);
                currentUser.postValue(user);
                calculateMetrics(user);
            }
        });
    }

    /**
     * 清除所有数据（测试用）
     */
    public void clearAllData() {
        executorService.execute(() -> {
            AppDatabase database = AppDatabase.getInstance(getApplication());
            database.clearAllTables();
        });
    }

    /**
     * Plan 10: 加载游戏化数据
     */
    private void loadGameificationData() {
        executorService.execute(() -> {
            // 1. 计算总训练天数
            int totalDays = dailyLogDao.getTotalTrainingDays();
            totalTrainingDays.postValue(totalDays);

            // 2. 根据天数计算等级
            String level = calculateLevel(totalDays);
            userLevel.postValue(level);

            // 3. 判定成就
            java.util.List<Achievement> achievementList = checkAchievements(totalDays);
            achievements.postValue(achievementList);
        });
    }

    /**
     * Plan 10: 根据训练天数计算等级
     */
    private String calculateLevel(int totalDays) {
        if (totalDays == 0) {
            return "Lv.0 新手";
        } else if (totalDays <= 15) {
            return "Lv.1 见习铁友 🐣";
        } else if (totalDays <= 30) {
            return "Lv.2 训练生 🔨";
        } else if (totalDays <= 60) {
            return "Lv.3 健身达人 💪";
        } else {
            return "Lv.4 钢铁之躯 🏆";
        }
    }

    /**
     * Plan 10: 检查成就解锁状态
     */
    private java.util.List<Achievement> checkAchievements(int totalDays) {
        java.util.List<Achievement> list = new java.util.ArrayList<>();

        // 基础数据查询
        int planCount = 0;
        java.util.List<com.cz.fitnessdiary.database.entity.TrainingPlan> plans = trainingPlanDao.getAllPlansList();
        if (plans != null) {
            for (com.cz.fitnessdiary.database.entity.TrainingPlan plan : plans) {
                String cat = plan.getCategory();
                if (cat != null && cat.startsWith("自定义-")) {
                    planCount++;
                }
            }
        }
        int foodCount = foodRecordDao.getTotalRecordCountSync();

        // --- 训练天数系列 ---
        // 成就 1: 初出茅庐
        boolean firstDay = totalDays >= 1;
        list.add(new Achievement("first_day", "初出茅庐", "完成第一次训练", "🌱", firstDay));

        // 成就 2: 渐入佳境 (累计 10 天)
        boolean streak10 = totalDays >= 10;
        list.add(new Achievement("streak_10", "渐入佳境", "累计训练 10 天", "🥉", streak10));

        // 成就 3: 健身达人 (累计 30 天)
        boolean streak30 = totalDays >= 30;
        list.add(new Achievement("streak_30", "健身达人", "累计训练 30 天", "🥈", streak30));

        // 成就 7: 健身专家 (60 天)
        boolean expert = totalDays >= 60;
        list.add(new Achievement("expert", "健身专家", "累计训练 60 天", "🥇", expert));

        // 成就 8: 钢铁之躯 (100 天)
        boolean ironBody = totalDays >= 100;
        list.add(new Achievement("iron_body", "钢铁之躯", "累计训练 100 天", "🏆", ironBody));

        // --- 计划系列 ---
        // 成就: 初识规划 (3+ 计划)
        boolean planStarter = planCount >= 3;
        list.add(new Achievement("plan_starter", "初识规划", "创建 3+ 个训练计划", "📄", planStarter));

        // 成就: 计划大师 (10+ 计划)
        boolean planMaster = planCount >= 10;
        list.add(new Achievement("plan_master", "计划大师", "创建 10+ 个训练计划", "📚", planMaster));

        // --- 饮食系列 ---
        // 成就 5: 饮食先锋 (累计 10+ 条饮食记录)
        boolean dietStarter = foodCount >= 10;
        list.add(new Achievement("diet_logged", "饮食先锋", "累计 10+ 条饮食记录", "🍎", dietStarter));

        // 成就 6: 卡路里克星 (累计 50+ 条饮食记录)
        boolean calorieBuster = foodCount >= 50;
        list.add(new Achievement("calorie_buster", "卡路里克星", "累计 50+ 条记录", "🔥", calorieBuster));

        return list;
    }

    // Getters
    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    public LiveData<Double> getBmi() {
        return bmi;
    }

    public LiveData<Integer> getBmr() {
        return bmr;
    }

    public LiveData<Integer> getTdee() {
        return tdee;
    }

    // Plan 10 Getters
    public LiveData<Integer> getTotalTrainingDays() {
        return totalTrainingDays;
    }

    public LiveData<String> getUserLevel() {
        return userLevel;
    }

    public LiveData<java.util.List<Achievement>> getAchievements() {
        return achievements;
    }

    public void refreshGameificationData() {
        loadGameificationData();
    }

    public void refreshUser() {
        loadUserData();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
