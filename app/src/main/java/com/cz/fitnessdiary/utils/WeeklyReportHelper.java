package com.cz.fitnessdiary.utils;

import android.content.Context;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.User;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 周报数据聚合与 AI prompt 构建
 */
public class WeeklyReportHelper {

    public static String getSummary(Context context) {
        long now = System.currentTimeMillis();
        long weekStart = getWeekStart(now);
        long weekEnd = weekStart + 7L * 86400000L;

        AppDatabase db = AppDatabase.getInstance(context);

        // Training
        List<TrainingRecordUtils.Entry> entries = TrainingRecordUtils.getCompletedEntries(
                db, weekStart, weekEnd);
        int trainingDays = 0;
        java.util.Set<Long> trainingDates = new java.util.HashSet<>();
        for (TrainingRecordUtils.Entry entry : entries) {
            trainingDates.add(DateUtils.getDayStartTimestamp(entry.date));
        }
        trainingDays = trainingDates.size();

        // Diet
        List<FoodRecord> foods = db.foodRecordDao().getAllFoodRecordsSync();
        int totalCal = 0;
        int foodDays = 0;
        for (FoodRecord f : foods) {
            if (f.getRecordDate() >= weekStart && f.getRecordDate() < weekEnd) {
                totalCal += f.getCalories();
                foodDays++;
            }
        }
        int avgCal = foodDays > 0 ? totalCal / Math.max(1, trainingDays) : 0;

        // Sleep
        float avgSleep = 0;
        List<SleepRecord> sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(weekStart, weekEnd);
        if (sleeps != null && !sleeps.isEmpty()) {
            float sum = 0;
            for (SleepRecord s : sleeps) {
                sum += s.getDuration() / 3600f;
            }
            avgSleep = sum / sleeps.size();
        }

        // Build summary line
        StringBuilder sb = new StringBuilder();
        sb.append("本周训练").append(trainingDays).append("天 · ");
        if (avgCal > 0) sb.append("日均热量").append(avgCal).append("千卡 · ");
        if (avgSleep > 0) sb.append("平均睡眠").append(String.format(Locale.getDefault(), "%.1f", avgSleep)).append("h");
        sb.append(" 点击查看完整周报");

        return sb.toString().trim();
    }

    public static String buildAIPrompt(Context context) {
        long now = System.currentTimeMillis();
        long weekStart = getWeekStart(now);
        long weekEnd = weekStart + 7L * 86400000L;

        AppDatabase db = AppDatabase.getInstance(context);
        User user = db.userDao().getUserSync();

        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下真实数据生成本周健康周报：\n\n");

        // User profile
        if (user != null) {
            sb.append("用户：").append(user.getNickname() != null ? user.getNickname() : "用户").append("\n");
            sb.append("身高：").append(user.getHeight()).append("cm 体重：").append(user.getWeight()).append("kg\n");
            sb.append("年龄：").append(user.getAge()).append(" 性别：").append(user.getGender() == 1 ? "男" : "女").append("\n\n");
        }

        // Training
        List<TrainingRecordUtils.Entry> entries = TrainingRecordUtils.getCompletedEntries(
                db, weekStart, weekEnd);
        java.util.Set<Long> trainingDates = new java.util.HashSet<>();
        for (TrainingRecordUtils.Entry entry : entries) {
            trainingDates.add(DateUtils.getDayStartTimestamp(entry.date));
        }
        int trainingDays = trainingDates.size();
        int totalWorkouts = entries.size();
        sb.append("本周训练：").append(trainingDays).append("天，完成").append(totalWorkouts).append("项\n");

        // Diet
        List<FoodRecord> foods = db.foodRecordDao().getAllFoodRecordsSync();
        int totalCal = 0, foodRecords = 0;
        for (FoodRecord f : foods) {
            if (f.getRecordDate() >= weekStart && f.getRecordDate() < weekEnd) {
                totalCal += f.getCalories();
                foodRecords++;
            }
        }
        int targetCal = user != null && user.getDailyCalorieTarget() > 0 ? user.getDailyCalorieTarget() : 2000;
        sb.append("本周饮食：").append(foodRecords).append("条记录，日均约")
                .append(trainingDays > 0 ? totalCal / trainingDays : 0).append("千卡（目标").append(targetCal).append("）\n");

        // Sleep
        List<SleepRecord> sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(weekStart, weekEnd);
        if (sleeps != null && !sleeps.isEmpty()) {
            float sum = 0;
            float sumQ = 0;
            for (SleepRecord s : sleeps) {
                sum += s.getDuration() / 3600f;
                sumQ += s.getQuality();
            }
            sb.append("本周睡眠：平均").append(String.format(Locale.getDefault(), "%.1f", sum / sleeps.size()))
                    .append("h，质量").append(String.format(Locale.getDefault(), "%.1f", sumQ / sleeps.size())).append("/5\n");
        }

        // Weight
        var weightRecords = db.weightRecordDao().getRecentRecordsSync(7);
        if (weightRecords != null && !weightRecords.isEmpty()) {
            sb.append("最新体重：").append(weightRecords.get(0).getWeight()).append("kg\n");
        }

        // Body measurements
        List<BodyMeasurement> measurements = db.bodyMeasurementDao().getByDateRangeSync(weekStart, weekEnd);
        if (measurements != null && !measurements.isEmpty()) {
            sb.append("本周围度记录：").append(measurements.size()).append("条\n");
        }

        // Bowel
        List<BowelMovement> bowels = db.bowelMovementDao().getByDateRangeSync(weekStart, weekEnd);
        if (bowels != null && !bowels.isEmpty()) {
            int avgBristol = 0;
            for (BowelMovement b : bowels) avgBristol += b.getBristolType();
            avgBristol /= bowels.size();
            sb.append("本周便便：").append(bowels.size()).append("次，平均分型").append(avgBristol).append("\n");
        }

        sb.append("\n请给出：\n1) 本周总体评价（2-3句话）\n2) 训练、饮食、睡眠各维度分析\n3) 下周改进建议（3条具体可执行的建议）\n4) 一句励志结语\n请用中文回复，简洁专业。");

        return sb.toString();
    }

    private static long getWeekStart(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

}
