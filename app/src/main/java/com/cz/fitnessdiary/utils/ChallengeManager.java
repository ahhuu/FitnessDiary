package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.cz.fitnessdiary.database.AppDatabase;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChallengeManager {

    private static final String PREF = "challenge_prefs";
    private static final String KEY_TYPE = "type";
    private static final String KEY_START = "start";
    private static final String KEY_STATUS = "status"; // ACTIVE, COMPLETED, FAILED
    private static final String KEY_FAILS = "fails";
    private static final String KEY_LAST_CHECK = "last_check";

    public static final String TYPE_FAT_LOSS = "FAT_LOSS";
    public static final String TYPE_MUSCLE_GAIN = "MUSCLE_GAIN";
    public static final String TYPE_EARLY_SLEEP = "EARLY_SLEEP";
    public static final String TYPE_WATER_MASTER = "WATER_MASTER";

    // 挑战的数据结构类
    public static class Challenge {
        public String id;
        public String name;
        public String desc;
        public String emoji;
        public int category; // 0-饮食轻食, 1-日常运动, 2-作息健康, 3-我的定制
        public int maxFails;
        public String bindCard; // FAT_LOSS / MUSCLE_GAIN / EARLY_SLEEP / WATER_MASTER / STEP / NONE

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

    // 获取所有预设挑战
    public static List<Challenge> getPresetChallenges() {
        List<Challenge> list = new ArrayList<>();
        // 0. 饮食轻食 板块
        list.add(new Challenge(TYPE_FAT_LOSS, "减脂冲刺", "21天每日热量不超标 · 累计3天超标则失败", "🔥", 0, 3, "FAT_LOSS"));
        list.add(new Challenge(TYPE_WATER_MASTER, "饮水达人", "21天饮水≥2000ml · 累计3天不达标则失败", "💧", 0, 3, "WATER_MASTER"));
        list.add(new Challenge("DIET_SUGAR", "控糖抗糖", "21天控制糖分零食摄入 · 累计2天越界则失败", "🍬", 0, 2, "NONE"));

        // 1. 日常运动 板块
        list.add(new Challenge(TYPE_MUSCLE_GAIN, "健身达人", "21天运动不间断 · 连续2天中断则失败", "💪", 1, 2, "MUSCLE_GAIN"));
        list.add(new Challenge("WALK_10K", "万步达人", "21天每日步数达10000步 · 累计3天不达标则失败", "🏃", 1, 3, "STEP"));
        list.add(new Challenge("DAILY_STRETCH", "日常拉伸", "21天每日完成身体拉伸 · 累计2天中断则失败", "🧘", 1, 2, "NONE"));

        // 2. 作息健康 板块
        list.add(new Challenge(TYPE_EARLY_SLEEP, "早睡挑战", "21天23:00前入睡 · 累计3天晚睡则失败", "🌙", 2, 3, "EARLY_SLEEP"));
        list.add(new Challenge("EARLY_WAKE", "高效早起", "21天早晨7:30前起床 · 累计3天迟起则失败", "⏰", 2, 3, "NONE"));
        list.add(new Challenge("MINDFULNESS", "冥想静心", "21天每日完成10分钟正念 · 累计2天不达标则失败", "🧘", 2, 2, "NONE"));

        return list;
    }

    // 兼容旧版，保留基本映射
    public static String getTypeName(String type) {
        if (type == null) return "";
        switch (type) {
            case TYPE_FAT_LOSS: return "减脂冲刺";
            case TYPE_MUSCLE_GAIN: return "健身达人";
            case TYPE_EARLY_SLEEP: return "早睡挑战";
            case TYPE_WATER_MASTER: return "饮水达人";
            default: return "";
        }
    }

    public static String getTypeDesc(String type) {
        if (type == null) return "";
        switch (type) {
            case TYPE_FAT_LOSS: return "21天每日热量不超标 · 累计3天超标则失败";
            case TYPE_MUSCLE_GAIN: return "21天运动不间断 · 连续2天中断则失败";
            case TYPE_EARLY_SLEEP: return "21天23:00前入睡 · 累计3天晚睡则失败";
            case TYPE_WATER_MASTER: return "21天饮水≥2000ml · 累计3天不达标则失败";
            default: return "";
        }
    }

    public static String getTypeEmoji(String type) {
        if (type == null) return "";
        switch (type) {
            case TYPE_FAT_LOSS: return "🔥";
            case TYPE_MUSCLE_GAIN: return "💪";
            case TYPE_EARLY_SLEEP: return "🌙";
            case TYPE_WATER_MASTER: return "💧";
            default: return "";
        }
    }

    // ─────────────────────── 自定义挑战持久化 (SharedPreferences JSON) ───────────────────────

    public static List<Challenge> getCustomChallenges(Context context) {
        try {
            SharedPreferences sp = getPref(context);
            String json = sp.getString("custom_challenges_json", null);
            if (json == null) return new ArrayList<>();
            Gson gson = new Gson();
            return gson.fromJson(json, new TypeToken<List<Challenge>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void addCustomChallenge(Context context, Challenge challenge) {
        try {
            List<Challenge> list = getCustomChallenges(context);
            if (challenge.id == null) {
                challenge.id = "CUSTOM_" + UUID.randomUUID().toString();
            }
            list.add(challenge);
            SharedPreferences sp = getPref(context);
            sp.edit().putString("custom_challenges_json", new Gson().toJson(list)).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteCustomChallenge(Context context, String id) {
        try {
            List<Challenge> list = getCustomChallenges(context);
            Challenge target = null;
            for (Challenge c : list) {
                if (c.id.equals(id)) {
                    target = c;
                    break;
                }
            }
            if (target != null) {
                list.remove(target);
                SharedPreferences sp = getPref(context);
                sp.edit().putString("custom_challenges_json", new Gson().toJson(list)).apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────── 挑战运行中控制 ───────────────────────

    public static String getStatus(Context context) {
        return getPref(context).getString(KEY_STATUS, null);
    }

    public static String getActiveType(Context context) {
        return getPref(context).getString(KEY_TYPE, null);
    }

    public static long getStartDate(Context context) {
        return getPref(context).getLong(KEY_START, 0);
    }

    public static int getFailDays(Context context) {
        return getPref(context).getInt(KEY_FAILS, 0);
    }

    public static String getActiveName(Context context) {
        SharedPreferences sp = getPref(context);
        String name = sp.getString("active_name", null);
        if (name != null) return name;
        return getTypeName(sp.getString(KEY_TYPE, null));
    }

    public static String getActiveDesc(Context context) {
        SharedPreferences sp = getPref(context);
        String desc = sp.getString("active_desc", null);
        if (desc != null) return desc;
        return getTypeDesc(sp.getString(KEY_TYPE, null));
    }

    public static String getActiveEmoji(Context context) {
        SharedPreferences sp = getPref(context);
        String emoji = sp.getString("active_emoji", null);
        if (emoji != null) return emoji;
        return getTypeEmoji(sp.getString(KEY_TYPE, null));
    }

    public static int getActiveMaxFails(Context context) {
        SharedPreferences sp = getPref(context);
        return sp.getInt("active_max_fails", 3);
    }

    public static int getProgressDays(Context context) {
        long start = getStartDate(context);
        if (start == 0) return 0;
        return (int) ((DateUtils.getTodayStartTimestamp() - start) / 86400000L) + 1;
    }

    // 启动挑战并记录完整属性
    public static void start(Context context, Challenge c) {
        getPref(context).edit()
                .putString(KEY_TYPE, c.id)
                .putLong(KEY_START, DateUtils.getTodayStartTimestamp())
                .putString(KEY_STATUS, "ACTIVE")
                .putInt(KEY_FAILS, 0)
                .putLong(KEY_LAST_CHECK, DateUtils.getTodayStartTimestamp()) // 启动当天作为 initial lastCheck
                .putString("active_name", c.name)
                .putString("active_desc", c.desc)
                .putString("active_emoji", c.emoji)
                .putInt("active_max_fails", c.maxFails)
                .putString("active_bind_card", c.bindCard)
                .apply();
        
        // 清理打卡轨迹
        clearAllTrackingRecords(context);
    }

    // 保持对老旧单参数 start 接口的兼容
    public static void start(Context context, String type) {
        List<Challenge> presets = getPresetChallenges();
        Challenge target = null;
        for (Challenge c : presets) {
            if (c.id.equals(type)) {
                target = c;
                break;
            }
        }
        if (target == null) {
            // 自定义挑战查一查
            for (Challenge c : getCustomChallenges(context)) {
                if (c.id.equals(type)) {
                    target = c;
                    break;
                }
            }
        }
        if (target != null) {
            start(context, target);
        } else {
            // 如果都查不到， fallback 到旧配置
            getPref(context).edit()
                    .putString(KEY_TYPE, type)
                    .putLong(KEY_START, DateUtils.getTodayStartTimestamp())
                    .putString(KEY_STATUS, "ACTIVE")
                    .putInt(KEY_FAILS, 0)
                    .putLong(KEY_LAST_CHECK, DateUtils.getTodayStartTimestamp())
                    .putString("active_name", getTypeName(type))
                    .putString("active_desc", getTypeDesc(type))
                    .putString("active_emoji", getTypeEmoji(type))
                    .putInt("active_max_fails", TYPE_MUSCLE_GAIN.equals(type) ? 2 : 3)
                    .putString("active_bind_card", getLegacyBindCard(type))
                    .apply();
            clearAllTrackingRecords(context);
        }
    }

    private static String getLegacyBindCard(String type) {
        if (type == null) return "NONE";
        switch (type) {
            case TYPE_FAT_LOSS: return "FAT_LOSS";
            case TYPE_MUSCLE_GAIN: return "MUSCLE_GAIN";
            case TYPE_EARLY_SLEEP: return "EARLY_SLEEP";
            case TYPE_WATER_MASTER: return "WATER_MASTER";
            default: return "NONE";
        }
    }

    public static void reset(Context context) {
        getPref(context).edit().clear().apply();
    }

    // 手动打卡逻辑 (针对 NONE 类型校验)
    public static void checkInToday(Context context) {
        SharedPreferences sp = getPref(context);
        long today = DateUtils.getTodayStartTimestamp();
        sp.edit().putBoolean("checked_day_" + today, true).apply();
    }


    // ─────────────────────── 21天打卡轨迹详细读取 ───────────────────────

    // 供子线程调用的同步版本（内含 DB 查询，不可在主线程调用）
    public static int[] getDayTrackingStatusSync(Context context) {
        int[] result = new int[21];
        SharedPreferences sp = getPref(context);
        String status = sp.getString(KEY_STATUS, null);
        if (status == null) return result;

        long start = sp.getLong(KEY_START, 0);
        if (start == 0) return result;

        long today = DateUtils.getTodayStartTimestamp();

        for (int i = 0; i < 21; i++) {
            long dayStart = start + i * 86400000L;
            if (dayStart > today) {
                result[i] = 0;
            } else if (dayStart == today) {
                result[i] = isCheckedTodaySync(context) ? 1 : 0;
            } else {
                String key = "history_day_" + dayStart;
                if (sp.contains(key)) {
                    result[i] = sp.getBoolean(key, true) ? 1 : 2;
                } else {
                    result[i] = 2;
                }
            }
        }
        return result;
    }

    // 供子线程调用的今日打卡状态检查（内含 DB 查询，不可在主线程调用）
    public static boolean isCheckedTodaySync(Context context) {
        SharedPreferences sp = getPref(context);
        long today = DateUtils.getTodayStartTimestamp();
        String bindCard = sp.getString("active_bind_card", "NONE");
        if (!"NONE".equals(bindCard)) {
            return isCheckInCompletedSync(context, bindCard, today, today + 86400000L);
        }
        return sp.getBoolean("checked_day_" + today, false);
    }

    // 旧版（仅供 NONE 类型手动打卡场景在主线程调用，不查 DB）
    public static boolean isCheckedToday(Context context) {
        SharedPreferences sp = getPref(context);
        long today = DateUtils.getTodayStartTimestamp();
        // 对于非手动打卡类型，只返回 SP 中的标记（不触发 DB）
        return sp.getBoolean("checked_day_" + today, false);
    }

    // 获取21天每一天的打卡状态：0-未开始，1-成功，2-失败（仅读 SP，不查 DB）
    public static int[] getDayTrackingStatus(Context context) {
        int[] result = new int[21];
        SharedPreferences sp = getPref(context);
        String status = sp.getString(KEY_STATUS, null);
        if (status == null) return result;

        long start = sp.getLong(KEY_START, 0);
        if (start == 0) return result;

        long today = DateUtils.getTodayStartTimestamp();

        for (int i = 0; i < 21; i++) {
            long dayStart = start + i * 86400000L;
            if (dayStart > today) {
                result[i] = 0;
            } else if (dayStart == today) {
                result[i] = isCheckedToday(context) ? 1 : 0;
            } else {
                String key = "history_day_" + dayStart;
                if (sp.contains(key)) {
                    result[i] = sp.getBoolean(key, true) ? 1 : 2;
                } else {
                    result[i] = 2;
                }
            }
        }
        return result;
    }

    private static void clearAllTrackingRecords(Context context) {
        SharedPreferences sp = getPref(context);
        SharedPreferences.Editor editor = sp.edit();
        for (String key : sp.getAll().keySet()) {
            if (key.startsWith("history_day_") || key.startsWith("checked_day_")) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    // ─────────────────────── 结算昨天多日离线补偿核心算法 ───────────────────────

    public static void checkToday(Context context) {
        SharedPreferences sp = getPref(context);
        String status = sp.getString(KEY_STATUS, null);
        if (!"ACTIVE".equals(status)) return;

        long today = DateUtils.getTodayStartTimestamp();
        long lastCheck = sp.getLong(KEY_LAST_CHECK, 0);
        if (lastCheck == today) return;

        // 如果是第一天启动，初始化 lastCheck 即可，跳过结算
        if (lastCheck == 0) {
            sp.edit().putLong(KEY_LAST_CHECK, today).apply();
            return;
        }

        long start = sp.getLong(KEY_START, 0);
        if (start == 0) return;

        String bindCard = sp.getString("active_bind_card", getLegacyBindCard(sp.getString(KEY_TYPE, null)));
        int maxFails = sp.getInt("active_max_fails", 3);
        int fails = sp.getInt(KEY_FAILS, 0);

        SharedPreferences.Editor editor = sp.edit();

        // 追赶结算历史中的每一天 (lastCheck 到 today 前一天)
        long tempCheck = lastCheck;
        while (tempCheck < today) {
            long checkStart = tempCheck;
            long checkEnd = tempCheck + 86400000L;

            int daysPassed = (int) ((checkStart - start) / 86400000L) + 1;
            if (daysPassed > 21) {
                // 已结出 challenge 的范围
                break;
            }

            // 校验那一天是否达标，达标则返回 true，不达标/失败返回 false
            boolean success = isCheckInCompletedSync(context, bindCard, checkStart, checkEnd);
            
            // 写入历史状态
            editor.putBoolean("history_day_" + checkStart, success);

            if (!success) {
                fails++;
                editor.putInt(KEY_FAILS, fails);
                if (fails >= maxFails) {
                    editor.putString(KEY_STATUS, "FAILED");
                    break;
                }
            }

            // 若已经是第21天被结算完，且无失败状态，则直接晋级
            if (daysPassed == 21 && fails < maxFails) {
                editor.putString(KEY_STATUS, "COMPLETED");
            }

            tempCheck += 86400000L;
        }

        // 更新 lastCheck 到今天，表示截止今天0点以前的日期都已经结算完毕
        editor.putLong(KEY_LAST_CHECK, today);
        editor.apply();
    }

    // 同步校验特定时间区间数据是否满足挑战打卡标准
    private static boolean isCheckInCompletedSync(Context context, String bindCard, long startMillis, long endMillis) {
        AppDatabase db = AppDatabase.getInstance(context);
        
        switch (bindCard) {
            case "FAT_LOSS": {
                // 减脂热量校验：若消耗大于目标热量，则判定失败；如果没有消耗（未记饮食），则这里按没超标算成功，或者按原逻辑
                int targetCal = 2000;
                try {
                    com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();
                    if (user != null && user.getDailyCalorieTarget() > 0) {
                        targetCal = user.getDailyCalorieTarget();
                    }
                } catch (Exception ignored) {}
                
                int consumed = 0;
                java.util.List<com.cz.fitnessdiary.database.entity.FoodRecord> foods = db.foodRecordDao().getByDateRangeSync(startMillis, endMillis);
                if (foods != null) {
                    for (com.cz.fitnessdiary.database.entity.FoodRecord f : foods) {
                        consumed += f.getCalories();
                    }
                }
                return consumed > 0 && consumed <= targetCal; // 必须有记录且热量不超标
            }
            case "MUSCLE_GAIN": {
                // 运动校验：今天完成的运动数大于0
                int done = db.dailyLogDao().getTodayCompletedCountSync(startMillis);
                return done > 0;
            }
            case "EARLY_SLEEP": {
                // 早睡校验：存在入睡时间小于 23:00 的记录
                java.util.List<com.cz.fitnessdiary.database.entity.SleepRecord> sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(startMillis, endMillis);
                if (sleeps != null && !sleeps.isEmpty()) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(sleeps.get(0).getStartTime());
                    int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
                    // 23点前入睡算达标，或者凌晨0点到早起时间段（看怎么算，正常是 calendar 得到的 hour >= 23 算晚睡）
                    return hour < 23;
                }
                return false; // 无睡眠记录则失败
            }
            case "WATER_MASTER": {
                // 饮水校验：总水量达标
                int ml = db.waterRecordDao().getTodayTotalSync(startMillis, endMillis);
                int targetWater = 2000;
                try {
                    com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();
                    if (user != null && user.getDailyWaterTarget() > 0) {
                        targetWater = user.getDailyWaterTarget();
                    }
                } catch (Exception ignored) {}
                return ml >= targetWater;
            }
            case "STEP": {
                // 步数校验
                int steps = 0;
                try {
                    com.cz.fitnessdiary.database.entity.StepRecord record = db.stepRecordDao().getByDateSync(startMillis);
                    if (record != null) {
                        steps = record.getSteps();
                    }
                } catch (Exception ignored) {}
                return steps >= 10000;
            }
            case "NONE":
            default: {
                // 手动打卡：从 checked_day_ 标记中读取
                SharedPreferences sp = getPref(context);
                return sp.getBoolean("checked_day_" + startMillis, false);
            }
        }
    }

    private static SharedPreferences getPref(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}

