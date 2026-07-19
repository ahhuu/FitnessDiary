package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.ChallengeEntity;
import com.cz.fitnessdiary.database.entity.ChallengeRecordEntity;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.User;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class ChallengeManager {

    private static final String PREF = "challenge_prefs";

    public static final String TYPE_FAT_LOSS = "FAT_LOSS";
    public static final String TYPE_MUSCLE_GAIN = "MUSCLE_GAIN";
    public static final String TYPE_EARLY_SLEEP = "EARLY_SLEEP";
    public static final String TYPE_WATER_MASTER = "WATER_MASTER";

    public static class Challenge {
        public String id;
        public String name;
        public String desc;
        public String emoji;
        public int category;
        public int maxFails;
        public String bindCard;
        public int totalDays = 21;
        public int targetDays = 21;
        public int freezeTickets = 2;
        public int reminderHour = -1;
        public int reminderMinute = -1;

        public Challenge() {}

        public Challenge(String id, String name, String desc, String emoji, int category, int maxFails, String bindCard) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.emoji = emoji;
            this.category = category;
            this.maxFails = maxFails;
            this.bindCard = bindCard;
        }
    }

    public static List<Challenge> getPresetChallenges() {
        List<Challenge> list = new ArrayList<>();
        list.add(new Challenge(TYPE_FAT_LOSS, "减脂冲刺", "21天每日热量不超标 · 累计3天超标则失败", "🔥", 0, 3, "FAT_LOSS"));
        list.add(new Challenge(TYPE_WATER_MASTER, "饮水达人", "21天饮水≥2000ml · 累计3天不达标则失败", "💧", 0, 3, "WATER_MASTER"));
        list.add(new Challenge("DIET_SUGAR", "控糖抗糖", "21天控制糖分零食摄入 · 累计2天越界则失败", "🍬", 0, 2, "NONE"));

        list.add(new Challenge(TYPE_MUSCLE_GAIN, "健身达人", "21天运动不间断 · 连续2天中断则失败", "💪", 1, 2, "MUSCLE_GAIN"));
        list.add(new Challenge("WALK_10K", "万步达人", "21天每日步数达10000步 · 累计3天不达标则失败", "🏃", 1, 3, "STEP"));
        list.add(new Challenge("DAILY_STRETCH", "日常拉伸", "21天每日完成身体拉伸 · 累计2天中断则失败", "🧘", 1, 2, "NONE"));

        list.add(new Challenge(TYPE_EARLY_SLEEP, "早睡挑战", "21天23:00前入睡 · 累计3天晚睡则失败", "🌙", 2, 3, "EARLY_SLEEP"));
        list.add(new Challenge("EARLY_WAKE", "高效早起", "21天早晨7:30前起床 · 累计3天迟起则失败", "⏰", 2, 3, "NONE"));
        list.add(new Challenge("MINDFULNESS", "冥想静心", "21天每日完成10分钟正念 · 累计2天不达标则失败", "🧘", 2, 2, "NONE"));

        list.add(new Challenge("MOOD_TRACKER", "情绪日记", "21天每天记录情绪，累计3次未记录挑战失败", "😌", 3, 3, "MOOD"));

        // --- New Preset Challenges ---
        list.add(new Challenge("WEIGHT_TRACK", "每日称重", "21天每天记录体重，累计3天不记录失败", "⚖️", 3, 3, "WEIGHT")); // Category 3 (身心/健康)
        list.add(new Challenge("MUSCLE_DIET", "增肌饮食", "每日高蛋白与高碳水达标，累计3天未达标失败", "🥩", 0, 3, "MUSCLE_DIET")); // Category 0 (饮食)
        list.add(new Challenge("MEDICATION", "规律用药", "每日必须服用完所有清单药物/补剂，累计3天遗漏失败", "💊", 3, 3, "MEDICATION")); // Category 3 (身心/健康)

        return list;
    }

    public static Challenge mapEntityToChallenge(ChallengeEntity e) {
        Challenge c = new Challenge(e.templateId, e.name, e.desc, e.emoji, e.category, e.maxFails, e.bindCard);
        c.totalDays = e.totalDays;
        c.targetDays = e.targetDays;
        c.freezeTickets = e.freezeTickets;
        return c;
    }

    public static void migrateLegacySharedPreferencesSync(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        boolean migrated = sp.getBoolean("migrated_to_room", false);
        if (migrated) return;

        String status = sp.getString("status", null);
        if ("ACTIVE".equals(status)) {
            ChallengeEntity entity = new ChallengeEntity();
            entity.templateId = sp.getString("type", "");
            entity.name = sp.getString("active_name", getTypeName(entity.templateId));
            entity.desc = sp.getString("active_desc", getTypeDesc(entity.templateId));
            entity.emoji = sp.getString("active_emoji", getTypeEmoji(entity.templateId));
            entity.category = 0;
            entity.maxFails = sp.getInt("active_max_fails", 3);
            entity.bindCard = sp.getString("active_bind_card", "NONE");
            entity.status = "ACTIVE";
            entity.startTime = sp.getLong("start", DateUtils.getTodayStartTimestamp());
            entity.failsCount = sp.getInt("fails", 0);
            entity.lastCheckDate = sp.getLong("last_check", entity.startTime);
            entity.freezeTickets = 2;

            long id = AppDatabase.getInstance(context).challengeDao().insert(entity);

            // Migrate history
            long start = entity.startTime;
            long today = DateUtils.getTodayStartTimestamp();
            for (int i = 0; i < entity.totalDays; i++) {
                long dayStart = start + i * 86400000L;
                if (dayStart > today) break;

                boolean success = false;
                if (dayStart == today) {
                    success = sp.getBoolean("checked_day_" + today, false);
                } else {
                    String key = "history_day_" + dayStart;
                    if (sp.contains(key)) {
                        success = sp.getBoolean(key, true);
                    }
                }
                if (success) {
                    ChallengeRecordEntity r = new ChallengeRecordEntity();
                    r.challengeId = (int) id;
                    r.recordDate = dayStart;
                    r.isCompleted = 1;
                    r.isFrozen = 0;
                    AppDatabase.getInstance(context).challengeRecordDao().insert(r);
                }
            }
        }

        sp.edit().putBoolean("migrated_to_room", true).apply();
    }

    private static String getTypeName(String type) {
        if (type == null) return "";
        switch (type) {
            case TYPE_FAT_LOSS: return "减脂冲刺";
            case TYPE_MUSCLE_GAIN: return "健身达人";
            case TYPE_EARLY_SLEEP: return "早睡挑战";
            case TYPE_WATER_MASTER: return "饮水达人";
            default: return "自定义挑战";
        }
    }

    private static String getTypeDesc(String type) {
        if (type == null) return "";
        switch (type) {
            case TYPE_FAT_LOSS: return "21天每日热量不超标 · 累计3天超标则失败";
            case TYPE_MUSCLE_GAIN: return "21天运动不间断 · 连续2天中断则失败";
            case TYPE_EARLY_SLEEP: return "21天23:00前入睡 · 累计3天晚睡则失败";
            case TYPE_WATER_MASTER: return "21天饮水≥2000ml · 累计3天不达标则失败";
            default: return "";
        }
    }

    private static String getTypeEmoji(String type) {
        if (type == null) return "";
        switch (type) {
            case TYPE_FAT_LOSS: return "🔥";
            case TYPE_MUSCLE_GAIN: return "💪";
            case TYPE_EARLY_SLEEP: return "🌙";
            case TYPE_WATER_MASTER: return "💧";
            default: return "🎯";
        }
    }

    // ─────────────────────── DB Interations ───────────────────────

    public static List<Challenge> getCustomChallengesSync(Context context) {
        List<ChallengeEntity> templates = AppDatabase.getInstance(context).challengeDao().getCustomTemplatesSync();
        List<Challenge> result = new ArrayList<>();
        if (templates != null) {
            for (ChallengeEntity e : templates) {
                result.add(mapEntityToChallenge(e));
            }
        }
        return result;
    }

    public static void addCustomChallengeSync(Context context, Challenge challenge) {
        if (challenge.id == null) {
            challenge.id = "CUSTOM_" + UUID.randomUUID().toString();
        }
        ChallengeEntity entity = new ChallengeEntity();
        entity.templateId = challenge.id;
        entity.name = challenge.name;
        entity.desc = challenge.desc;
        entity.emoji = challenge.emoji;
        entity.category = challenge.category;
        entity.maxFails = challenge.maxFails;
        entity.bindCard = challenge.bindCard;
        entity.totalDays = challenge.totalDays;
        entity.targetDays = challenge.targetDays;
        entity.status = "TEMPLATE";
        entity.startTime = 0;
        entity.failsCount = 0;
        entity.lastCheckDate = 0;
        entity.freezeTickets = challenge.freezeTickets;
        entity.reminderHour = challenge.reminderHour;
        entity.reminderMinute = challenge.reminderMinute;

        AppDatabase.getInstance(context).challengeDao().insert(entity);
    }

    public static void updateCustomChallengeSync(Context context, Challenge challenge) {
        ChallengeEntity entity = AppDatabase.getInstance(context).challengeDao().getTemplateByTemplateIdSync(challenge.id);
        if (entity != null) {
            entity.name = challenge.name;
            entity.desc = challenge.desc;
            entity.emoji = challenge.emoji;
            entity.category = challenge.category;
            entity.maxFails = challenge.maxFails;
            entity.bindCard = challenge.bindCard;
            entity.totalDays = challenge.totalDays;
            entity.targetDays = challenge.targetDays;
            entity.freezeTickets = challenge.freezeTickets;
            entity.reminderHour = challenge.reminderHour;
            entity.reminderMinute = challenge.reminderMinute;
            AppDatabase.getInstance(context).challengeDao().update(entity);
        }
    }

    public static void deleteCustomChallengeSync(Context context, String templateId) {
        AppDatabase.getInstance(context).challengeDao().deleteTemplateByTemplateId(templateId);
    }

    public static List<ChallengeEntity> getCompletedChallengesSync(Context context) {
        return AppDatabase.getInstance(context).challengeDao().getCompletedChallengesSync();
    }

    public static void startSync(Context context, Challenge c) {
        List<ChallengeEntity> actives = getActiveChallengesSync(context);
        for (ChallengeEntity active : actives) {
            if (active.templateId.equals(c.id)) {
                // Prevent duplicate running of the same template
                return;
            }
        }

        ChallengeEntity entity = new ChallengeEntity();
        entity.templateId = c.id;
        entity.name = c.name;
        entity.desc = c.desc;
        entity.emoji = c.emoji;
        entity.category = c.category;
        entity.maxFails = c.maxFails;
        entity.bindCard = c.bindCard;
        entity.totalDays = c.totalDays;
        entity.targetDays = c.targetDays;
        entity.status = "ACTIVE";
        entity.startTime = DateUtils.getTodayStartTimestamp();
        entity.failsCount = 0;
        entity.lastCheckDate = 0;
        entity.freezeTickets = c.freezeTickets;
        entity.reminderHour = c.reminderHour;
        entity.reminderMinute = c.reminderMinute;

        AppDatabase.getInstance(context).challengeDao().insert(entity);
    }

    public static void resetActiveSync(Context context, int instanceId) {
        ChallengeEntity existing = AppDatabase.getInstance(context).challengeDao().getByIdSync(instanceId);
        if (existing != null && "ACTIVE".equals(existing.status)) {
            existing.status = "FAILED";
            AppDatabase.getInstance(context).challengeDao().update(existing);
        }
    }

    public static ChallengeEntity getActiveChallengeSync(Context context) {
        migrateLegacySharedPreferencesSync(context);
        return AppDatabase.getInstance(context).challengeDao().getActiveChallengeSync();
    }

    public static List<ChallengeEntity> getActiveChallengesSync(Context context) {
        migrateLegacySharedPreferencesSync(context);
        return AppDatabase.getInstance(context).challengeDao().getActiveChallengesSync();
    }

    public static void checkInTodaySync(Context context, int instanceId) {
        ChallengeEntity active = AppDatabase.getInstance(context).challengeDao().getByIdSync(instanceId);
        if (active == null || !"ACTIVE".equals(active.status)) return;

        long today = DateUtils.getTodayStartTimestamp();
        ChallengeRecordEntity record = AppDatabase.getInstance(context).challengeRecordDao().getRecordSync(active.id, today);
        if (record == null) {
            record = new ChallengeRecordEntity();
            record.challengeId = active.id;
            record.recordDate = today;
            record.isCompleted = 1;
            record.isFrozen = 0;
            AppDatabase.getInstance(context).challengeRecordDao().insert(record);
        } else {
            record.isCompleted = 1;
            AppDatabase.getInstance(context).challengeRecordDao().update(record);
        }
    }

    public static void useFreezeTicketTodaySync(Context context, int instanceId) {
        ChallengeEntity active = AppDatabase.getInstance(context).challengeDao().getByIdSync(instanceId);
        if (active == null || !"ACTIVE".equals(active.status) || active.freezeTickets <= 0) return;

        long today = DateUtils.getTodayStartTimestamp();
        ChallengeRecordEntity record = AppDatabase.getInstance(context).challengeRecordDao().getRecordSync(active.id, today);
        if (record == null) {
            record = new ChallengeRecordEntity();
            record.challengeId = active.id;
            record.recordDate = today;
            record.isCompleted = 0;
            record.isFrozen = 1;
            AppDatabase.getInstance(context).challengeRecordDao().insert(record);
        } else {
            record.isFrozen = 1;
            AppDatabase.getInstance(context).challengeRecordDao().update(record);
        }

        active.freezeTickets--;
        AppDatabase.getInstance(context).challengeDao().update(active);
    }

    // 0-Not Started, 1-Completed, 2-Failed, 3-Frozen
    public static int[] getDayTrackingStatusSync(Context context, ChallengeEntity active) {
        if (active == null) return new int[0];
        int[] result = new int[active.totalDays];

        long start = active.startTime;
        long today = DateUtils.getTodayStartTimestamp();

        List<ChallengeRecordEntity> records = AppDatabase.getInstance(context).challengeRecordDao().getRecordsByChallengeIdSync(active.id);

        for (int i = 0; i < active.totalDays; i++) {
            long dayStart = start + i * 86400000L;
            if (dayStart > today) {
                result[i] = 0;
            } else if (dayStart == today) {
                boolean completed = false;
                boolean frozen = false;
                for (ChallengeRecordEntity r : records) {
                    if (r.recordDate == dayStart) {
                        if (r.isCompleted == 1) completed = true;
                        if (r.isFrozen == 1) frozen = true;
                    }
                }

                if (!completed && !frozen) {
                    completed = isCheckInCompletedInternalSync(context, active.bindCard, dayStart, dayStart + 86400000L);
                }

                if (completed) result[i] = 1;
                else if (frozen) result[i] = 3;
                else result[i] = 0;
            } else {
                boolean found = false;
                for (ChallengeRecordEntity r : records) {
                    if (r.recordDate == dayStart) {
                        found = true;
                        if (r.isCompleted == 1) result[i] = 1;
                        else if (r.isFrozen == 1) result[i] = 3;
                        else result[i] = 2;
                        break;
                    }
                }
                if (!found) {
                    result[i] = 2;
                }
            }
        }
        return result;
    }

    public static boolean isCheckedTodaySync(Context context, ChallengeEntity active) {
        if (active == null) return false;
        long today = DateUtils.getTodayStartTimestamp();

        ChallengeRecordEntity r = AppDatabase.getInstance(context).challengeRecordDao().getRecordSync(active.id, today);
        if (r != null && (r.isCompleted == 1 || r.isFrozen == 1)) return true;

        return isCheckInCompletedInternalSync(context, active.bindCard, today, today + 86400000L);
    }

    public static int getProgressDays(ChallengeEntity active) {
        if (active == null || active.startTime == 0) return 0;
        return (int) ((DateUtils.getTodayStartTimestamp() - active.startTime) / 86400000L) + 1;
    }

    public static int getStreak(Context context, ChallengeEntity active) {
        if (active == null) return 0;
        int[] tracking = getDayTrackingStatusSync(context, active);
        int progress = getProgressDays(active);
        int streak = 0;
        boolean checkedToday = isCheckedTodaySync(context, active);
        int startIdx = checkedToday ? progress - 1 : progress - 2;
        if (startIdx >= active.totalDays) startIdx = active.totalDays - 1;
        if (startIdx < 0) return 0;

        for (int i = startIdx; i >= 0; i--) {
            if (tracking[i] == 1) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    public static void checkTodaySync(Context context) {
        List<ChallengeEntity> actives = getActiveChallengesSync(context);
        for (ChallengeEntity active : actives) {
            checkSingleChallengeTodaySync(context, active);
        }
    }

    private static void checkSingleChallengeTodaySync(Context context, ChallengeEntity active) {
        if (active == null || !"ACTIVE".equals(active.status)) return;

        long today = DateUtils.getTodayStartTimestamp();
        long lastCheck = active.lastCheckDate;
        if (lastCheck == today) return;

        if (lastCheck == 0) {
            active.lastCheckDate = today;
            AppDatabase.getInstance(context).challengeDao().update(active);
            return;
        }

        long start = active.startTime;
        int fails = active.failsCount;
        int maxFails = active.maxFails;

        long tempCheck = lastCheck;
        while (tempCheck <= today) { // Check up to today as well for progress limits
            if (!"ACTIVE".equals(active.status)) break;

            // Only simulate historical days (tempCheck < today). Today is not yet missed.
            if (tempCheck < today) {
                long checkStart = tempCheck;
                long checkEnd = tempCheck + 86400000L;

                int daysPassed = (int) ((checkStart - start) / 86400000L) + 1;

                ChallengeRecordEntity record = AppDatabase.getInstance(context).challengeRecordDao().getRecordSync(active.id, checkStart);
                boolean success = false;
                boolean frozen = false;

                if (record != null) {
                    if (record.isCompleted == 1) success = true;
                    if (record.isFrozen == 1) frozen = true;
                } else {
                    success = isCheckInCompletedInternalSync(context, active.bindCard, checkStart, checkEnd);
                }

                if (!success && !frozen) {
                    fails++;
                    active.failsCount = fails;
                    if (record == null) {
                        record = new ChallengeRecordEntity();
                        record.challengeId = active.id;
                        record.recordDate = checkStart;
                        record.isCompleted = 0;
                        record.isFrozen = 0;
                        AppDatabase.getInstance(context).challengeRecordDao().insert(record);
                    }

                    // Fail Check 1: Discipline Constraint (maxFails)
                    if (fails > maxFails) {
                        active.status = "FAILED";
                        break;
                    }
                } else if (success) {
                    if (record == null) {
                        record = new ChallengeRecordEntity();
                        record.challengeId = active.id;
                        record.recordDate = checkStart;
                        record.isCompleted = 1;
                        record.isFrozen = 0;
                        AppDatabase.getInstance(context).challengeRecordDao().insert(record);
                    }
                }
            }

            // Always calculate current progress to check Math Constraint or Success
            int completedCount = 0;
            List<ChallengeRecordEntity> allRecords = AppDatabase.getInstance(context).challengeRecordDao().getRecordsByChallengeIdSync(active.id);
            for (ChallengeRecordEntity r : allRecords) {
                if (r.isCompleted == 1) completedCount++;
            }

            // Success check
            if (completedCount >= active.targetDays) {
                active.status = "COMPLETED";
                break;
            }

            // Fail Check 2: Math Constraint (Insufficient remaining days)
            int currentDaysPassed = (int) ((tempCheck - start) / 86400000L) + 1;
            int remainingDays = active.totalDays - currentDaysPassed;
            // Since we can still check in on `tempCheck` (if it's today), remainingDays includes today.
            // If it's a past day, it calculates whether at the end of that day it was impossible.
            if (remainingDays < 0) remainingDays = 0;

            // Only count today as an opportunity if we haven't already checked in today
            boolean checkedToday = false;
            if (tempCheck == today) {
                ChallengeRecordEntity todayRec = AppDatabase.getInstance(context).challengeRecordDao().getRecordSync(active.id, today);
                if (todayRec != null && (todayRec.isCompleted == 1 || todayRec.isFrozen == 1)) {
                    checkedToday = true;
                }
            }

            int potentialMax = completedCount + remainingDays;
            if (tempCheck == today && checkedToday) {
                 // We already checked in today, so we can't check in again today
                 potentialMax = completedCount + remainingDays; // remainingDays does NOT include today?
                 // Wait, remainingDays = totalDays - currentDaysPassed.
                 // Example: total=3, day=3, remaining=0. potentialMax = completedCount + 0.
            } else if (tempCheck == today && !checkedToday) {
                 // can still check in today!
                 // remaining = totalDays - currentDaysPassed.
                 // potential = completedCount + remaining + 1?
                 // Wait, currentDaysPassed is 1-indexed. So if total=3, current=1, remaining=2.
                 // You can check in today, tomorrow, day 3 (total 3 opportunities).
                 // So remaining opportunities = remainingDays + 1 (since current day hasn't been used)
                 potentialMax = completedCount + remainingDays + 1;
            }

            if (potentialMax < active.targetDays) {
                active.status = "FAILED";
                break;
            }

            tempCheck += 86400000L;
        }

        active.lastCheckDate = today;
        AppDatabase.getInstance(context).challengeDao().update(active);
    }

    private static boolean isCheckInCompletedInternalSync(Context context, String bindCard, long startMillis, long endMillis) {
        ICheckInStrategy strategy = CheckInStrategyFactory.getStrategy(bindCard);
        if (strategy != null) {
            return strategy.isCompleted(context, startMillis, endMillis);
        }
        return false;
    }

    private static class CheckInStrategyFactory {
        static ICheckInStrategy getStrategy(String bindCard) {
            switch (bindCard) {
                case "FAT_LOSS": return new FatLossStrategy();
                case "MUSCLE_GAIN": return new MuscleGainStrategy();
                case "EARLY_SLEEP": return new EarlySleepStrategy();
                case "WATER_MASTER": return new WaterMasterStrategy();
                case "STEP": return new StepStrategy();
                case "MOOD": return new MoodStrategy();
                case "WEIGHT": return new WeightStrategy();
                case "MUSCLE_DIET": return new MuscleDietStrategy();
                case "MEDICATION": return new MedicationStrategy();
                default: return null;
            }
        }
    }

    private static class WeightStrategy implements ICheckInStrategy {
        @Override
        public boolean isCompleted(Context context, long startMillis, long endMillis) {
            AppDatabase db = AppDatabase.getInstance(context);
            try {
                List<com.cz.fitnessdiary.database.entity.WeightRecord> records = db.weightRecordDao().getRecordsByDateRangeSync(startMillis, endMillis);
                return records != null && !records.isEmpty();
            } catch (Exception ignored) {}
            return false;
        }
    }

    private static class MuscleDietStrategy implements ICheckInStrategy {
        @Override
        public boolean isCompleted(Context context, long startMillis, long endMillis) {
            AppDatabase db = AppDatabase.getInstance(context);
            double targetProtein = 80.0;
            double targetCarbs = 150.0;
            try {
                User user = db.userDao().getUserSync();
                if (user != null) {
                    targetProtein = user.getTargetProtein();
                    targetCarbs = user.getTargetCarbs();
                }
            } catch (Exception ignored) {}

            double consumedProtein = 0;
            double consumedCarbs = 0;
            List<FoodRecord> foods = db.foodRecordDao().getByDateRangeSync(startMillis, endMillis);
            if (foods != null) {
                for (FoodRecord f : foods) {
                    consumedProtein += f.getProtein();
                    consumedCarbs += f.getCarbs();
                }
            }
            return consumedProtein >= targetProtein && consumedCarbs >= targetCarbs;
        }
    }

    private static class MedicationStrategy implements ICheckInStrategy {
        @Override
        public boolean isCompleted(Context context, long startMillis, long endMillis) {
            AppDatabase db = AppDatabase.getInstance(context);
            try {
                // Number of medications taken today
                int takenCount = db.medicationRecordDao().getTakenCountByDateRangeSync(startMillis, endMillis);
                // Number of medications NOT taken today
                int untakenCount = db.medicationRecordDao().getUntakenCountByDateRangeSync(startMillis, endMillis);

                // Success if there's at least one taken medication, and ZERO untaken medications.
                return takenCount > 0 && untakenCount == 0;
            } catch (Exception ignored) {}
            return false;
        }
    }

    private static class FatLossStrategy implements ICheckInStrategy {
        @Override
        public boolean isCompleted(Context context, long startMillis, long endMillis) {
            AppDatabase db = AppDatabase.getInstance(context);
                int targetCal = 2000;
                try {
                    User user = db.userDao().getUserSync();
                    if (user != null && user.getTargetCalories() > 0) {
                        targetCal = user.getTargetCalories();
                    }
                } catch (Exception ignored) {}

                int consumed = 0;
                List<FoodRecord> foods = db.foodRecordDao().getByDateRangeSync(startMillis, endMillis);
                if (foods != null) {
                    for (FoodRecord f : foods) consumed += f.getCalories();
                }
            return consumed > 0 && consumed <= targetCal;
        }
    }

    private static class MuscleGainStrategy implements ICheckInStrategy {
        @Override
        public boolean isCompleted(Context context, long startMillis, long endMillis) {
            AppDatabase db = AppDatabase.getInstance(context);
                int done = db.dailyLogDao().getTodayCompletedCountSync(startMillis);
            return done > 0;
        }
    }

    private static class EarlySleepStrategy implements ICheckInStrategy {
        @Override
        public boolean isCompleted(Context context, long startMillis, long endMillis) {
            AppDatabase db = AppDatabase.getInstance(context);
                List<SleepRecord> sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(startMillis, endMillis);
                if (sleeps != null && !sleeps.isEmpty()) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(sleeps.get(0).getStartTime());
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    return hour < 23;
                }
            return false;
        }
    }

    private static class WaterMasterStrategy implements ICheckInStrategy {
        @Override
        public boolean isCompleted(Context context, long startMillis, long endMillis) {
            AppDatabase db = AppDatabase.getInstance(context);
                int ml = db.waterRecordDao().getTodayTotalSync(startMillis, endMillis);
                int targetWater = 2000;
                try {
                    User user = db.userDao().getUserSync();
                    if (user != null && user.getDailyWaterTarget() > 0) {
                        targetWater = user.getDailyWaterTarget();
                    }
                } catch (Exception ignored) {}
            return ml >= targetWater;
        }
    }

    private static class StepStrategy implements ICheckInStrategy {
        @Override
        public boolean isCompleted(Context context, long startMillis, long endMillis) {
            AppDatabase db = AppDatabase.getInstance(context);
                int steps = 0;
                try {
                    StepRecord record = db.stepRecordDao().getByDateSync(startMillis);
                    if (record != null) steps = record.getSteps();
                } catch (Exception ignored) {}
            return steps >= 10000;
        }
    }

    private static class MoodStrategy implements ICheckInStrategy {
        @Override
        public boolean isCompleted(Context context, long startMillis, long endMillis) {
            AppDatabase db = AppDatabase.getInstance(context);
                try {
                    com.cz.fitnessdiary.database.entity.MoodRecord m = db.moodRecordDao().getByDateSync(startMillis);
                    return m != null;
                } catch (Exception ignored) {}
            return false;
        }
    }
}
