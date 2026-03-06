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
import com.cz.fitnessdiary.database.entity.DailyLog;
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
            int planCount = trainingPlanDao.getAllPlansList().size();
            int foodCount = foodRecordDao.getTotalRecordCountSync();
            List<Achievement> currentAchievements = buildAchievements(totalDays, planCount, foodCount);
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

    private List<Achievement> buildAchievements(int totalDays, int planCount, int foodCount) {
        List<Achievement> list = new ArrayList<>();
        list.add(new Achievement("first_day", "初出茅庐", "完成第一次训练", "🌱", totalDays >= 1));
        list.add(new Achievement("streak_10", "渐入佳境", "累计训练 10 天", "🥉", totalDays >= 10));
        list.add(new Achievement("streak_30", "健身达人", "累计训练 30 天", "🥈", totalDays >= 30));
        list.add(new Achievement("expert", "健身专家", "累计训练 60 天", "🥇", totalDays >= 60));
        list.add(new Achievement("iron_body", "钢铁之躯", "累计训练 100 天", "🏆", totalDays >= 100));
        list.add(new Achievement("plan_starter", "初识规划", "创建 3+ 个训练计划", "📄", planCount >= 3));
        list.add(new Achievement("plan_master", "计划大师", "创建 10+ 个训练计划", "📚", planCount >= 10));
        list.add(new Achievement("diet_logged", "饮食先锋", "累计 10+ 条饮食记录", "🍎", foodCount >= 10));
        list.add(new Achievement("calorie_buster", "卡路里克星", "累计 50+ 条记录", "🔥", foodCount >= 50));
        return list;
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
