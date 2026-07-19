package com.cz.fitnessdiary.utils;

import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.ExtraExerciseLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 肌肉疲劳度与恢复计算引擎
 */
public class MuscleFatigueCalculator {

    // 疲劳值上限
    public static final float MAX_FATIGUE = 100f;
    // 基础动作增加的疲劳值
    public static final float BASE_FATIGUE_PER_EXERCISE = 30f;

    /**
     * 计算肌肉疲劳度
     * @param logs 近期的打卡记录 (DailyLog)
     * @param plans 所有对应的训练计划 (用于获取 category 作为 bodyPart)
     * @param extraLogs 近期的额外动作记录
     * @param currentTimestamp 当天的0点时间戳 (用于计算衰减)
     * @return 各部位的疲劳度 (0f - 100f)
     */
    public static Map<String, Float> calculateFatigueMap(
            List<DailyLog> logs,
            Map<Integer, TrainingPlan> plans,
            List<ExtraExerciseLog> extraLogs,
            long currentTimestamp) {

        Map<String, Float> rawFatigue = new HashMap<>();

        // 1. 处理 ExtraExerciseLog
        for (ExtraExerciseLog ex : extraLogs) {
            if (!ex.isCompleted()) continue;
            String part = ex.getBodyPart();
            if (part == null || part.trim().isEmpty()) continue;

            float decay = calculateDecay(ex.getDate(), currentTimestamp);
            if (decay > 0) {
                // 如果有组数记录，按组数增加疲劳度，否则用基础值
                float added = (ex.getSets() > 0) ? (ex.getSets() * 15f) : BASE_FATIGUE_PER_EXERCISE;
                rawFatigue.put(part.trim(), rawFatigue.getOrDefault(part.trim(), 0f) + added * decay);
            }
        }

        // 2. 处理 DailyLog
        for (DailyLog log : logs) {
            if (!log.isCompleted()) continue;
            TrainingPlan plan = plans.get(log.getPlanId());
            if (plan != null) {
                String part = plan.getCategory(); // 比如 "胸部"
                if (part == null || part.trim().isEmpty()) continue;

                float decay = calculateDecay(log.getDate(), currentTimestamp);
                if (decay > 0) {
                    float added = (log.getActualSets() > 0) ? (log.getActualSets() * 15f) : (BASE_FATIGUE_PER_EXERCISE * 2);
                    rawFatigue.put(part.trim(), rawFatigue.getOrDefault(part.trim(), 0f) + added * decay);
                }
            }
        }

        // 3. 规范化并限制上限
        Map<String, Float> result = new HashMap<>();
        for (Map.Entry<String, Float> entry : rawFatigue.entrySet()) {
            float val = Math.min(entry.getValue(), MAX_FATIGUE);
            result.put(entry.getKey(), val);
        }

        return result;
    }

    /**
     * 计算48小时衰减系数
     * @param recordDate 记录当天的0点时间戳
     * @param currentTimestamp 当前时间的0点时间戳
     * @return 衰减系数 (0f - 1.0f)
     */
    private static float calculateDecay(long recordDate, long currentTimestamp) {
        long diffDays = (currentTimestamp - recordDate) / (24 * 60 * 60 * 1000L);
        if (diffDays <= 0) return 1.0f;     // 今天: 100%
        if (diffDays == 1) return 0.6f;     // 昨天: 60%
        if (diffDays == 2) return 0.2f;     // 前天: 20% (因为如果是前天晚上练的，到今天下午还没满48小时)
        return 0f;                          // 大于等于3天: 0% 恢复
    }
}
