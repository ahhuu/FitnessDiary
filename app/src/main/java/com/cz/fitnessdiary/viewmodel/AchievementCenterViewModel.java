package com.cz.fitnessdiary.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.DailyLogDao;
import com.cz.fitnessdiary.database.dao.FoodRecordDao;
import com.cz.fitnessdiary.database.dao.HabitRecordDao;
import com.cz.fitnessdiary.database.dao.TrainingPlanDao;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.database.entity.MoodRecord;
import com.cz.fitnessdiary.model.Achievement;
import com.cz.fitnessdiary.model.AchievementUnlockEvent;
import com.cz.fitnessdiary.model.DailyMission;
import com.cz.fitnessdiary.model.UiEvent;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AchievementCenterViewModel extends AndroidViewModel {

    private static final String PREF_NAME = "achievement_center_prefs";
    private static final String KEY_SEEN_UNLOCKED_IDS = "seen_unlocked_ids";
    private static final String KEY_UNREAD_UNLOCKED_IDS = "unread_unlocked_ids";
    private static final String KEY_MISSION_CELEBRATED_DAY = "mission_celebrated_day";
    private static final String KEY_MISSION_DEFINITIONS = "mission_definitions";
    private static final String KEY_MISSION_DONE_PREFIX = "mission_done_";
    private static final String TYPE_TRAINING = "training";
    private static final String TYPE_DIET = "diet";
    private static final String TYPE_HABIT = "habit";
    private static final String TYPE_CUSTOM = "custom";

    private final DailyLogDao dailyLogDao;
    private final TrainingPlanDao trainingPlanDao;
    private final FoodRecordDao foodRecordDao;
    private final HabitRecordDao habitRecordDao;
    private final SharedPreferences sp;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<Achievement>> achievements = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<UiEvent<AchievementUnlockEvent>> unlockEvent = new MutableLiveData<>();
    private final MutableLiveData<Integer> unreadUnlockCount = new MutableLiveData<>(0);
    private final MutableLiveData<List<DailyMission>> todayMissions = new MutableLiveData<>(new ArrayList<>());
    private volatile long missionDate = DateUtils.getTodayStartTimestamp();

    private final Queue<AchievementUnlockEvent> unlockQueue = new ArrayDeque<>();
    private boolean showingEvent = false;

    public AchievementCenterViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getInstance(application);
        dailyLogDao = database.dailyLogDao();
        trainingPlanDao = database.trainingPlanDao();
        foodRecordDao = database.foodRecordDao();
        habitRecordDao = database.habitRecordDao();
        sp = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        refreshAll();
    }

    public LiveData<List<Achievement>> getAchievements() {
        return achievements;
    }

    public LiveData<UiEvent<AchievementUnlockEvent>> getUnlockEvent() {
        return unlockEvent;
    }

    public LiveData<Integer> getUnreadUnlockCount() {
        return unreadUnlockCount;
    }

    public LiveData<List<DailyMission>> getTodayMissions() {
        return todayMissions;
    }

    public void setMissionDate(long date) {
        long dayStart = DateUtils.getDayStartTimestamp(date);
        missionDate = dayStart;
        executorService.execute(() -> computeMissionsForDate(dayStart));
    }

    public void replaceMissionDefinitionsFromLines(List<String> lines) {
        executorService.execute(() -> {
            List<MissionDefinition> definitions = parseMissionLines(lines);
            saveMissionDefinitions(definitions);
            clearDoneStateForAllDays();
            computeMissionsForDate(missionDate);
        });
    }

    public void toggleCustomMissionCompletion(String missionId) {
        executorService.execute(() -> {
            if (missionId == null || missionId.isEmpty() || !missionId.startsWith("custom_")) {
                return;
            }
            Set<String> doneSet = readIdSet(getDoneKey(missionDate));
            if (doneSet.contains(missionId)) {
                doneSet.remove(missionId);
            } else {
                doneSet.add(missionId);
            }
            sp.edit().putStringSet(getDoneKey(missionDate), new HashSet<>(doneSet)).apply();
            computeMissionsForDate(missionDate);
        });
    }

    public void refreshAll() {
        executorService.execute(() -> {
            int totalDays = dailyLogDao.getTotalTrainingDays();
            int planCount = 0;
            List<com.cz.fitnessdiary.database.entity.TrainingPlan> plans = trainingPlanDao.getAllPlansList();
            if (plans != null) {
                for (com.cz.fitnessdiary.database.entity.TrainingPlan plan : plans) {
                    String cat = plan.getCategory();
                    if (cat != null && cat.startsWith("自定义-")) {
                        planCount++;
                    }
                }
            }
            int foodCount = foodRecordDao.getTotalRecordCountSync();
            int workoutCount = dailyLogDao.getAllLogsSync().size();

            AppDatabase db = AppDatabase.getInstance(getApplication());
            
            // 1. 统计饮水达标天数 (单日饮水总和 >= 2000 ml)
            List<WaterRecord> allWaters = db.waterRecordDao().getRecentRecordsSync(2000);
            int waterGoalDays = 0;
            if (allWaters != null) {
                java.util.Map<String, Integer> waterMap = new java.util.HashMap<>();
                for (WaterRecord wr : allWaters) {
                    String dKey = DateUtils.formatDate(wr.getTimestamp());
                    waterMap.put(dKey, waterMap.getOrDefault(dKey, 0) + wr.getAmountMl());
                }
                for (int amount : waterMap.values()) {
                    if (amount >= 2000) waterGoalDays++;
                }
            }

            // 2. 统计步数达标天数 (单日步数 >= 10000 步)
            List<StepRecord> allSteps = db.stepRecordDao().getRecordsByDateRangeSync(0, System.currentTimeMillis() + 86400000L);
            int stepGoalDays = 0;
            if (allSteps != null) {
                for (StepRecord sr : allSteps) {
                    if (sr.getSteps() >= 10000) stepGoalDays++;
                }
            }

            // 3. 统计睡眠达标天数 (单次睡眠时长在 7.0 到 9.0 小时之间)
            List<SleepRecord> allSleeps = db.sleepRecordDao().getRecentRecordsSync(1000);
            int sleepGoalDays = 0;
            if (allSleeps != null) {
                for (SleepRecord sr : allSleeps) {
                    float hrs = sr.getDuration() / 3600f;
                    if (hrs >= 7.0f && hrs <= 9.0f) sleepGoalDays++;
                }
            }

            // 4. 统计排便总记录次数
            List<BowelMovement> allBowels = db.bowelMovementDao().getByDateRangeSync(0, System.currentTimeMillis() + 86400000L);
            int bowelCount = allBowels != null ? allBowels.size() : 0;

            // 5. 统计情绪总记录天数
            List<MoodRecord> allMoods = db.moodRecordDao().getAllRecordsSync();
            int moodCount = allMoods != null ? allMoods.size() : 0;

            User user = db.userDao().getUserSync();
            List<Achievement> currentAchievements = buildAchievements(totalDays, planCount, foodCount,
                    workoutCount, waterGoalDays, stepGoalDays, sleepGoalDays, bowelCount, moodCount, user);
            achievements.postValue(currentAchievements);

            handleAchievementUnlocks(currentAchievements);
            computeMissionsForDate(missionDate);
        });
    }

    public void consumeUnlockEvent() {
        showingEvent = false;
        dispatchNextUnlockEvent();
    }

    public void markAchievementsViewed() {
        sp.edit().putStringSet(KEY_UNREAD_UNLOCKED_IDS, new HashSet<>()).apply();
        unreadUnlockCount.postValue(0);
    }

    private List<Achievement> buildAchievements(int totalDays, int planCount, int foodCount,
                                                 int workoutCount, int waterGoalDays, int stepGoalDays,
                                                 int sleepGoalDays, int bowelCount, int moodCount, User user) {
        List<Achievement> list = new ArrayList<>();
        list.add(new Achievement("first_day", "初出茅庐", "完成第一次训练", "🌱", totalDays >= 1));
        list.add(new Achievement("streak_10", "习惯养成", "累计训练 15 天", "🥉", totalDays >= 15));
        list.add(new Achievement("streak_30", "百日筑基", "累计训练 50 天", "🥈", totalDays >= 50));
        list.add(new Achievement("expert", "持之以恒", "累计训练 100 天", "🥇", totalDays >= 100));
        list.add(new Achievement("iron_body", "钢铁意志", "累计训练 365 天", "🏆", totalDays >= 365));
        
        list.add(new Achievement("plan_starter", "初识规划", "创建 5+ 个训练计划", "📄", planCount >= 5));
        list.add(new Achievement("plan_master", "计划大师", "创建 15+ 个训练计划", "📚", planCount >= 15));
        
        list.add(new Achievement("diet_logged", "饮食先锋", "累计 30+ 条饮食记录", "🍎", foodCount >= 30));
        list.add(new Achievement("calorie_buster", "卡路里克星", "累计 150+ 条记录", "🔥", foodCount >= 150));
        list.add(new Achievement("diet_100", "饮食大师", "累计记录 300 次饮食", "🍽️", foodCount >= 300));

        // Personal record achievements
        list.add(new Achievement("workout_100", "百炼成钢", "累计完成 200 次训练", "⚔️", workoutCount >= 200));
        list.add(new Achievement("workout_500", "千锤百炼", "累计完成 1000 次训练", "🛡️", workoutCount >= 1000));

        // 新增扩展维度成就
        list.add(new Achievement("water_master_50", "水合卫士", "累计 50 天饮水达标", "💧", waterGoalDays >= 50));
        list.add(new Achievement("steps_master_30", "万步先行者", "累计 30 天步行达标", "👣", stepGoalDays >= 30));
        list.add(new Achievement("sleep_master_30", "睡眠守护神", "累计 30 天健康睡眠", "💤", sleepGoalDays >= 30));
        list.add(new Achievement("mood_master_30", "心理调节员", "累计打卡每日心情 30 次", "🧠", moodCount >= 30));
        list.add(new Achievement("bowel_master_30", "畅通无阻", "累计打卡排便记录 30 次", "🚽", bowelCount >= 30));

        // Weight goal (depends on user's goal direction)
        boolean weightGoal = checkWeightGoal(user);
        list.add(new Achievement("weight_goal", "体重新突破", getWeightGoalDesc(user), "⚖️", weightGoal));

        // Waist goal
        boolean waistGoal = checkWaistGoal(user);
        list.add(new Achievement("waist_goal", "腰围新突破", getWaistGoalDesc(user), "📏", waistGoal));

        // Perfect day
        boolean perfectDay = checkPerfectDay();
        list.add(new Achievement("perfect_day", "完美一天", "运动+饮食+饮水+习惯全部达标", "🌟", perfectDay));

        // Streak record
        boolean streakRecord = checkStreakRecord();
        list.add(new Achievement("streak_record", "连签破纪录", "连续打卡突破历史最长", "🏅", streakRecord));

        return list;
    }

    private boolean checkWeightGoal(User user) {
        if (user == null) return false;
        AppDatabase db = AppDatabase.getInstance(getApplication());
        List<WeightRecord> records = db.weightRecordDao().getRecentRecordsSync(30);
        if (records == null || records.size() < 2) return false;
        float current = records.get(0).getWeight();
        int goal = user.getGoalType();
        for (int i = 1; i < records.size(); i++) {
            float past = records.get(i).getWeight();
            if (goal == 0 && current < past) return true; // loss: new low
            if (goal == 1 && current > past) return true; // muscle: new high
        }
        if (goal == 2) { // maintain: within 1kg for 30 days
            for (WeightRecord r : records) {
                if (Math.abs(r.getWeight() - current) > 1.0f) return false;
            }
            return records.size() >= 3;
        }
        return false;
    }

    private boolean checkWaistGoal(User user) {
        AppDatabase db = AppDatabase.getInstance(getApplication());
        List<BodyMeasurement> records = db.bodyMeasurementDao().getByTypeAndDateRangeSync(
                "WAIST", 0, System.currentTimeMillis());
        if (records == null || records.size() < 2) return false;
        float current = records.get(0).getValue();
        int goal = user != null ? user.getGoalType() : 0;
        for (int i = 1; i < records.size(); i++) {
            float past = records.get(i).getValue();
            if (goal == 0 && current < past) return true;
        }
        return false;
    }

    private boolean checkPerfectDay() {
        // Check if today has completed training, diet, water, habit, and sleep
        long today = DateUtils.getTodayStartTimestamp();
        long dayEnd = today + 86400000L;
        AppDatabase db = AppDatabase.getInstance(getApplication());
        boolean hasTraining = db.dailyLogDao().getTodayCompletedCountSync(today) > 0;
        boolean hasDiet = db.foodRecordDao().getRecordCountByDateRangeSync(today, dayEnd) > 0;
        boolean hasWater = db.waterRecordDao().getTodayTotalSync(today, dayEnd) > 0;
        boolean hasHabit = db.habitRecordDao().getCompletedCountByDateSync(today) > 0;
        boolean hasSleep = db.sleepRecordDao().getSleepRecordsByDateRangeSync(today, dayEnd) != null
                && !db.sleepRecordDao().getSleepRecordsByDateRangeSync(today, dayEnd).isEmpty();
        return hasTraining && hasDiet && hasWater && hasHabit && hasSleep;
    }

    private boolean checkStreakRecord() {
        AppDatabase db = AppDatabase.getInstance(getApplication());
        long today = DateUtils.getTodayStartTimestamp();
        int streak = 0;
        for (int i = 0; i < 365; i++) {
            long day = today - (long) i * 86400000L;
            if (db.dailyLogDao().getTodayCompletedCountSync(day) > 0) streak++;
            else break;
        }
        // Check if this is a new record: current streak >= previous best + 1
        SharedPreferences sp = getApplication().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int bestStreak = sp.getInt("best_streak", 0);
        if (streak > bestStreak) {
            sp.edit().putInt("best_streak", streak).apply();
            return true;
        }
        return false;
    }

    private String getWeightGoalDesc(User user) {
        if (user == null) return "体重达成目标方向";
        switch (user.getGoalType()) {
            case 0: return "减脂目标：体重创新低";
            case 1: return "增肌目标：体重创新高";
            default: return "维持目标：体重稳定";
        }
    }

    private String getWaistGoalDesc(User user) {
        if (user == null) return "腰围达成目标";
        switch (user.getGoalType()) {
            case 0: return "减脂目标：腰围创新低";
            default: return "腰围保持良好";
        }
    }

    private void handleAchievementUnlocks(List<Achievement> currentAchievements) {
        Set<String> seenIds = readIdSet(KEY_SEEN_UNLOCKED_IDS);
        Set<String> unreadIds = readIdSet(KEY_UNREAD_UNLOCKED_IDS);
        boolean changed = false;

        for (Achievement achievement : currentAchievements) {
            if (!achievement.isUnlocked()) {
                continue;
            }
            if (seenIds.contains(achievement.getId())) {
                continue;
            }
            seenIds.add(achievement.getId());
            unreadIds.add(achievement.getId());
            changed = true;
            unlockQueue.offer(new AchievementUnlockEvent(
                    AchievementUnlockEvent.TYPE_ACHIEVEMENT,
                    achievement.getId(),
                    achievement.getEmoji(),
                    achievement.getTitle(),
                    "新成就已解锁"));
        }

        if (changed) {
            sp.edit()
                    .putStringSet(KEY_SEEN_UNLOCKED_IDS, new HashSet<>(seenIds))
                    .putStringSet(KEY_UNREAD_UNLOCKED_IDS, new HashSet<>(unreadIds))
                    .apply();
        }
        unreadUnlockCount.postValue(unreadIds.size());
        dispatchNextUnlockEvent();
    }

    private void computeMissionsForDate(long dayStart) {
        long dayEnd = dayStart + 24L * 60L * 60L * 1000L;

        List<DailyLog> logs = dailyLogDao.getLogsByDateSync(dayStart);
        List<com.cz.fitnessdiary.database.entity.TrainingPlan> allPlans = trainingPlanDao.getAllPlansList();
        boolean hasTrainingPlanToday = hasTrainingPlanForDate(dayStart, allPlans);
        boolean trainingDone = !hasTrainingPlanToday;
        if (hasTrainingPlanToday && logs != null) {
            for (DailyLog log : logs) {
                if (log.isCompleted()) {
                    trainingDone = true;
                    break;
                }
            }
        }

        boolean dietDone = foodRecordDao.getRecordCountByDateRangeSync(dayStart, dayEnd) > 0;
        boolean habitDone = habitRecordDao.getCompletedCountByDateSync(dayStart) > 0;
        Set<String> doneSet = readIdSet(getDoneKey(dayStart));

        List<DailyMission> missions = new ArrayList<>();
        List<MissionDefinition> definitions = readMissionDefinitions();
        int systemMissionCount = 0;
        int completedSystemMissionCount = 0;
        for (MissionDefinition definition : definitions) {
            boolean completed = false;
            boolean custom = TYPE_CUSTOM.equals(definition.type);
            if (TYPE_TRAINING.equals(definition.type)) {
                completed = trainingDone;
                systemMissionCount++;
            } else if (TYPE_DIET.equals(definition.type)) {
                completed = dietDone;
                systemMissionCount++;
            } else if (TYPE_HABIT.equals(definition.type)) {
                completed = habitDone;
                systemMissionCount++;
            } else if (custom) {
                completed = doneSet.contains(definition.id);
            }
            if (!custom && completed) {
                completedSystemMissionCount++;
            }
            missions.add(new DailyMission(definition.id, definition.title, completed, custom));
        }
        todayMissions.postValue(missions);

        if (systemMissionCount > 0 && completedSystemMissionCount == systemMissionCount && DateUtils.isToday(dayStart)) {
            long celebratedDay = sp.getLong(KEY_MISSION_CELEBRATED_DAY, -1L);
            if (celebratedDay != dayStart) {
                sp.edit().putLong(KEY_MISSION_CELEBRATED_DAY, dayStart).apply();
                unlockQueue.offer(new AchievementUnlockEvent(
                        AchievementUnlockEvent.TYPE_MISSION,
                        "daily_mission_complete",
                        "✅",
                        "今日任务完成",
                        "三项任务已全部达成"));
                dispatchNextUnlockEvent();
            }
        }
    }

    private boolean hasTrainingPlanForDate(long date, List<com.cz.fitnessdiary.database.entity.TrainingPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return false;
        }
        String mode = getCurrentPlanMode();
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(date);
        int androidDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        int dayIndex = (androidDayOfWeek == java.util.Calendar.SUNDAY) ? 7 : (androidDayOfWeek - 1);

        for (com.cz.fitnessdiary.database.entity.TrainingPlan plan : plans) {
            String cat = plan.getCategory();
            if (cat == null || !cat.startsWith(mode + "-")) {
                continue;
            }
            String scheduledDays = plan.getScheduledDays();
            if ("none".equals(scheduledDays)) {
                continue; // 真正不排期，跳过
            }
            if (scheduledDays == null || scheduledDays.isEmpty() || scheduledDays.contains("0")) {
                return true;
            }
            String[] days = scheduledDays.split(",");
            for (String day : days) {
                if (day.trim().equals(String.valueOf(dayIndex))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getCurrentPlanMode() {
        SharedPreferences prefs = getApplication().getSharedPreferences("fitness_diary_prefs", Context.MODE_PRIVATE);
        return prefs.getString("current_plan_mode", "基础");
    }

    private List<MissionDefinition> parseMissionLines(List<String> lines) {
        List<MissionDefinition> list = new ArrayList<>();
        if (lines == null) {
            return list;
        }
        int customIndex = 0;
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String raw = line.trim();
            if (raw.isEmpty()) {
                continue;
            }
            if (raw.startsWith("[训练]")) {
                String title = raw.substring("[训练]".length()).trim();
                if (title.isEmpty()) {
                    title = "完成 1 次训练记录";
                }
                list.add(new MissionDefinition("mission_training", TYPE_TRAINING, title));
                continue;
            }
            if (raw.startsWith("[饮食]")) {
                String title = raw.substring("[饮食]".length()).trim();
                if (title.isEmpty()) {
                    title = "记录 1 次饮食";
                }
                list.add(new MissionDefinition("mission_diet", TYPE_DIET, title));
                continue;
            }
            if (raw.startsWith("[习惯]")) {
                String title = raw.substring("[习惯]".length()).trim();
                if (title.isEmpty()) {
                    title = "完成 1 条习惯";
                }
                list.add(new MissionDefinition("mission_habit", TYPE_HABIT, title));
                continue;
            }
            String customTitle = raw.startsWith("[自定义]") ? raw.substring("[自定义]".length()).trim() : raw;
            if (customTitle.isEmpty()) {
                continue;
            }
            String id = "custom_" + customIndex + "_" + Math.abs(customTitle.hashCode());
            customIndex++;
            list.add(new MissionDefinition(id, TYPE_CUSTOM, customTitle));
        }
        if (list.isEmpty()) {
            list = buildDefaultMissionDefinitions();
        }
        return list;
    }

    private void saveMissionDefinitions(List<MissionDefinition> definitions) {
        org.json.JSONArray array = new org.json.JSONArray();
        for (MissionDefinition definition : definitions) {
            org.json.JSONObject obj = new org.json.JSONObject();
            try {
                obj.put("id", definition.id);
                obj.put("type", definition.type);
                obj.put("title", definition.title);
            } catch (Exception ignored) {
            }
            array.put(obj);
        }
        sp.edit().putString(KEY_MISSION_DEFINITIONS, array.toString()).apply();
    }

    private List<MissionDefinition> readMissionDefinitions() {
        String raw = sp.getString(KEY_MISSION_DEFINITIONS, null);
        if (raw == null || raw.trim().isEmpty()) {
            List<MissionDefinition> defaults = buildDefaultMissionDefinitions();
            saveMissionDefinitions(defaults);
            return defaults;
        }
        List<MissionDefinition> list = new ArrayList<>();
        try {
            org.json.JSONArray array = new org.json.JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String id = obj.optString("id", "").trim();
                String type = obj.optString("type", "").trim();
                String title = obj.optString("title", "").trim();
                if (id.isEmpty() || title.isEmpty()) {
                    continue;
                }
                if (!TYPE_TRAINING.equals(type) && !TYPE_DIET.equals(type) && !TYPE_HABIT.equals(type)
                        && !TYPE_CUSTOM.equals(type)) {
                    continue;
                }
                list.add(new MissionDefinition(id, type, title));
            }
        } catch (Exception ignored) {
        }
        if (list.isEmpty()) {
            list = buildDefaultMissionDefinitions();
            saveMissionDefinitions(list);
        }
        return list;
    }

    private List<MissionDefinition> buildDefaultMissionDefinitions() {
        List<MissionDefinition> list = new ArrayList<>();
        list.add(new MissionDefinition("mission_training", TYPE_TRAINING, "完成 1 次训练记录"));
        list.add(new MissionDefinition("mission_diet", TYPE_DIET, "记录 1 次饮食"));
        list.add(new MissionDefinition("mission_habit", TYPE_HABIT, "完成 1 条习惯"));
        return list;
    }

    private String getDoneKey(long dayStart) {
        return KEY_MISSION_DONE_PREFIX + dayStart;
    }

    private void clearDoneStateForAllDays() {
        SharedPreferences.Editor editor = sp.edit();
        java.util.Map<String, ?> all = sp.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith(KEY_MISSION_DONE_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    private static class MissionDefinition {
        final String id;
        final String type;
        final String title;

        MissionDefinition(String id, String type, String title) {
            this.id = id;
            this.type = type;
            this.title = title;
        }
    }

    private void dispatchNextUnlockEvent() {
        if (showingEvent) {
            return;
        }
        AchievementUnlockEvent next = unlockQueue.poll();
        if (next == null) {
            return;
        }
        showingEvent = true;
        unlockEvent.postValue(new UiEvent<>(next));
    }

    private Set<String> readIdSet(String key) {
        Set<String> set = sp.getStringSet(key, Collections.emptySet());
        if (set == null) {
            return new HashSet<>();
        }
        return new HashSet<>(set);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
