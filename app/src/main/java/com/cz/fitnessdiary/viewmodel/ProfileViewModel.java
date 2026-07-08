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
    private final com.cz.fitnessdiary.database.dao.WeightRecordDao weightRecordDao;
    private final com.cz.fitnessdiary.database.dao.BodyMeasurementDao bodyMeasurementDao;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getInstance(application);
        userDao = database.userDao();
        dailyLogDao = database.dailyLogDao();
        trainingPlanDao = database.trainingPlanDao();
        foodRecordDao = database.foodRecordDao();
        weightRecordDao = database.weightRecordDao();
        bodyMeasurementDao = database.bodyMeasurementDao();
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
            // 0. 加载当前用户（成就判定需要）
            User user = userDao.getUserSync();

            // 1. 计算总训练天数
            int trainingDays = dailyLogDao.getTotalTrainingDays();

            // 2. 计算饮食记录总天数
            int dietDays = foodRecordDao.getTotalDietDaysSync();

            // 3. 判定成就（需在计算等级前获取已解锁数）
            java.util.List<Achievement> achievementList = checkAchievements(trainingDays, user);
            int unlockedCount = 0;
            for (Achievement a : achievementList) {
                if (a.isUnlocked()) unlockedCount++;
            }

            // 4. 计算综合等级（三维加权 + 活跃度修正）
            String level = calculateLevel(trainingDays, dietDays, unlockedCount);
            userLevel.postValue(level);

            // 5. 总训练天数单独展示用
            totalTrainingDays.postValue(trainingDays);

            // 6. 成就列表
            achievements.postValue(achievementList);
        });
    }

    /**
     * 方案A + 活跃度修正：三维加权积分 × 练食均衡系数
     *
     * 基础分 = 训练天数×1.5 + 饮食天数×1.0 + 已解锁成就数×5
     * 活跃度 = min(饮食天数 / max(训练天数, 1), 1.0)   // 均衡才满分
     * 最终积分 = 基础分 × (1.0 + 活跃度 × 0.3)
     *
     * 活跃度修正含义：只练不吃效率打折，练吃均衡额外加成（最多+30%）
     */
    private String calculateLevel(int trainingDays, int dietDays, int unlockedAchievements) {
        double trainingScore = trainingDays * 1.5;
        double dietScore = dietDays * 1.0;
        double achievementBonus = unlockedAchievements * 2.0;

        double baseScore = trainingScore + dietScore + achievementBonus;

        double activityRatio = trainingDays > 0
                ? Math.min((double) dietDays / trainingDays, 1.0)
                : (dietDays > 0 ? 1.0 : 0.0);
        double finalScore = baseScore * (1.0 + activityRatio * 0.3);
        int score = (int) Math.round(finalScore);

        if (score <= 50) {
            return "Lv.0 初来乍到 🌱";
        } else if (score <= 130) {
            return "Lv.1 见习铁友 🐣";
        } else if (score <= 280) {
            return "Lv.2 进阶训练生 🔨";
        } else if (score <= 550) {
            return "Lv.3 健身达人 💪";
        } else if (score <= 1000) {
            return "Lv.4 塑形精英 ⭐";
        } else if (score <= 1800) {
            return "Lv.5 钢铁之躯 🛡️";
        } else if (score <= 3200) {
            return "Lv.6 蜕变大师 🔥";
        } else {
            return "Lv.7 传奇缔造者 👑";
        }
    }

    /**
     * Plan 10: 检查成就解锁状态
     */
    private java.util.List<Achievement> checkAchievements(int totalDays, User user) {
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
        // 成就: 饮食先锋 (累计 10+ 条饮食记录)
        boolean dietStarter = foodCount >= 10;
        list.add(new Achievement("diet_logged", "饮食先锋", "累计 10+ 条饮食记录", "🍎", dietStarter));

        // 成就: 卡路里克星 (累计 50+ 条记录)
        boolean calorieBuster = foodCount >= 50;
        list.add(new Achievement("calorie_buster", "卡路里克星", "累计 50+ 条记录", "🔥", calorieBuster));

        // 成就: 饮食大师 (累计 100 条记录)
        boolean diet100 = foodCount >= 100;
        list.add(new Achievement("diet_100", "饮食大师", "累计记录100次饮食", "🍽️", diet100));

        // --- 训练次数系列 ---
        int workoutCount = dailyLogDao.getTotalWorkoutCountSync();
        // 成就: 百炼成钢 (累计100次训练打卡)
        boolean workout100 = workoutCount >= 100;
        list.add(new Achievement("workout_100", "百炼成钢", "累计完成100次训练", "⚔️", workout100));

        // 成就: 千锤百炼 (累计500次)
        boolean workout500 = workoutCount >= 500;
        list.add(new Achievement("workout_500", "千锤百炼", "累计完成500次训练", "🛡️", workout500));

        // --- 连签记录 ---
        android.content.SharedPreferences sp = getApplication().getSharedPreferences("fitness_diary_prefs",
                android.content.Context.MODE_PRIVATE);
        int recordStreak = sp.getInt("record_consecutive_days", 0);
        boolean streakRecord = recordStreak >= 7;
        list.add(new Achievement("streak_record", "连签破纪录", "连续打卡突破7天", "🏅", streakRecord));

        // --- 体重目标 ---
        boolean weightGoal = false;
        if (user != null && user.getWeight() > 0) {
            java.util.List<com.cz.fitnessdiary.database.entity.WeightRecord> weightRecords =
                    weightRecordDao.getRecentRecordsSync(30);
            if (weightRecords != null && weightRecords.size() >= 2) {
                float currentW = weightRecords.get(0).getWeight();
                int goal = user.getGoalType();
                for (int i = 1; i < weightRecords.size(); i++) {
                    float pastW = weightRecords.get(i).getWeight();
                    if (goal == 0 && currentW < pastW) { weightGoal = true; break; }      // 减脂：新低
                    if (goal == 1 && currentW > pastW) { weightGoal = true; break; }      // 增肌：新高
                }
                if (goal == 2) { // 保持：30天内波动 < 1kg
                    weightGoal = true;
                    for (com.cz.fitnessdiary.database.entity.WeightRecord r : weightRecords) {
                        if (Math.abs(r.getWeight() - currentW) > 1.0f) { weightGoal = false; break; }
                    }
                    weightGoal = weightGoal && weightRecords.size() >= 3;
                }
            }
        }
        list.add(new Achievement("weight_goal", "体重新突破",
                getWeightGoalDesc(user), "⚖️", weightGoal));

        // --- 腰围目标 ---
        boolean waistGoal = false;
        java.util.List<com.cz.fitnessdiary.database.entity.BodyMeasurement> waistRecords =
                bodyMeasurementDao.getByTypeAndDateRangeSync("WAIST", 0, System.currentTimeMillis());
        if (waistRecords != null && waistRecords.size() >= 2) {
            float currentWaist = waistRecords.get(0).getValue();
            int goal = user != null ? user.getGoalType() : 0;
            for (int i = 1; i < waistRecords.size(); i++) {
                if (goal == 0 && currentWaist < waistRecords.get(i).getValue()) {
                    waistGoal = true; break;
                }
            }
        }
        list.add(new Achievement("waist_goal", "腰围新突破",
                getWaistGoalDesc(user), "📏", waistGoal));

        return list;
    }

    private String getWeightGoalDesc(User user) {
        if (user == null) return "体重取得新突破";
        int g = user.getGoalType();
        if (g == 0) return "减脂目标：体重创新低";
        if (g == 1) return "增肌目标：体重创新高";
        return "保持目标：30天体重稳定";
    }

    private String getWaistGoalDesc(User user) {
        if (user == null) return "腰围取得新突破";
        int g = user != null ? user.getGoalType() : 0;
        if (g == 0) return "减脂目标：腰围创新低";
        return "腰围持续优化";
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
