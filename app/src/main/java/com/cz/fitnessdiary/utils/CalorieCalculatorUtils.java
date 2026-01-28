package com.cz.fitnessdiary.utils;

/**
 * 卡路里计算工具类
 * 实现智能卡路里计算系统，包括 BMR、TDEE 和目标卡路里计算
 */
public class CalorieCalculatorUtils {

    // 目标类型常量
    public static final int GOAL_LOSE_FAT = 0; // 减脂
    public static final int GOAL_GAIN_MUSCLE = 1; // 增肌
    public static final int GOAL_MAINTAIN = 2; // 保持

    // 性别常量
    public static final int GENDER_FEMALE = 0;
    public static final int GENDER_MALE = 1;

    /**
     * 计算基础代谢率 (BMR - Basal Metabolic Rate)
     * 使用 Mifflin-St Jeor 公式
     * 
     * 公式：
     * 男性：BMR = 10 × 体重(kg) + 6.25 × 身高(cm) - 5 × 年龄 + 5
     * 女性：BMR = 10 × 体重(kg) + 6.25 × 身高(cm) - 5 × 年龄 - 161
     * 
     * @param gender 性别 (0=女, 1=男)
     * @param weight 体重 (kg)
     * @param height 身高 (cm)
     * @param age    年龄
     * @return BMR 基础代谢率 (kcal/day)
     */
    public static int calculateBMR(int gender, float weight, float height, int age) {
        // 零值校验：如果基本数据不全，返回 0
        if (weight <= 0 || height <= 0 || age <= 0) {
            return 0;
        }

        double bmr;
        if (gender == GENDER_MALE) {
            bmr = 10 * weight + 6.25 * height - 5 * age + 5;
        } else {
            bmr = 10 * weight + 6.25 * height - 5 * age - 161;
        }
        return (int) Math.round(bmr);
    }

    /**
     * 计算每日总能量消耗 (TDEE - Total Daily Energy Expenditure)
     * TDEE = BMR × 活动系数
     * 
     * 活动系数参考：
     * 1.2 - 久坐（很少或不运动）
     * 1.375 - 轻度活动（每周运动1-3天）
     * 1.55 - 中度活动（每周运动3-5天）
     * 1.725 - 高度活动（每周运动6-7天）
     * 1.9 - 极高活动（体力劳动或每天2次训练）
     * 
     * @param bmr           基础代谢率
     * @param activityLevel 活动系数
     * @return TDEE 每日总能量消耗 (kcal/day)
     */
    public static int calculateTDEE(int bmr, float activityLevel) {
        return (int) Math.round(bmr * activityLevel);
    }

    /**
     * 根据健身目标计算每日目标卡路里
     * 
     * 减脂：TDEE - 500 (创造热量缺口)
     * 增肌：TDEE + 300 (创造热量盈余)
     * 保持：TDEE (维持现状)
     * 
     * @param tdee     每日总能量消耗
     * @param goalType 目标类型 (0=减脂, 1=增肌, 2=保持)
     * @return 每日目标卡路里 (kcal/day)
     */
    public static int calculateTargetCalories(int tdee, int goalType) {
        switch (goalType) {
            case GOAL_LOSE_FAT:
                return tdee - 500;
            case GOAL_GAIN_MUSCLE:
                return tdee + 300;
            case GOAL_MAINTAIN:
            default:
                return tdee;
        }
    }

    /**
     * 计算卡路里进度百分比
     * 
     * @param consumed 已摄入卡路里
     * @param target   目标卡路里
     * @return 进度百分比 (0-100+)
     */
    public static float calculateProgress(int consumed, int target) {
        if (target <= 0)
            return 0;
        return (consumed * 100f) / target;
    }

    /**
     * 生成智能反馈消息
     * 
     * @param consumed 已摄入卡路里
     * @param target   目标卡路里
     * @param goalType 目标类型
     * @return 反馈消息
     */
    public static String getCalorieDifferenceMessage(int consumed, int target, int goalType) {
        int difference = target - consumed;

        if (goalType == GOAL_LOSE_FAT) {
            // 减脂模式
            if (difference > 0) {
                return String.format("今日热量缺口 %d 千卡，继续保持！💪", difference);
            } else if (difference == 0) {
                return "今日摄入刚好达标！👍";
            } else {
                return String.format("今日超出目标 %d 千卡，明天注意控制哦~", Math.abs(difference));
            }
        } else if (goalType == GOAL_GAIN_MUSCLE) {
            // 增肌模式
            if (difference > 0) {
                return String.format("还需摄入 %d 千卡才能达标哦~", difference);
            } else if (difference == 0) {
                return "完美达标，增肌效果MAX！💪";
            } else {
                return String.format("今日超出 %d 千卡，注意营养平衡！", Math.abs(difference));
            }
        } else {
            // 保持模式
            if (Math.abs(difference) <= 50) {
                return "今日摄入很平衡！😊";
            } else if (difference > 0) {
                return String.format("今日还可以摄入 %d 千卡", difference);
            } else {
                return String.format("今日超出 %d 千卡", Math.abs(difference));
            }
        }
    }

    /**
     * 获取活动系数对应的描述
     */
    public static String getActivityLevelName(float activityLevel) {
        if (activityLevel <= 1.2f)
            return "久坐";
        if (activityLevel <= 1.375f)
            return "轻度活动";
        if (activityLevel <= 1.55f)
            return "中度活动";
        if (activityLevel <= 1.725f)
            return "高度活动";
        return "极高活动";
    }

    /**
     * 获取目标类型对应的描述
     */
    public static String getGoalTypeName(int goalType) {
        switch (goalType) {
            case GOAL_LOSE_FAT:
                return "减脂";
            case GOAL_GAIN_MUSCLE:
                return "增肌";
            case GOAL_MAINTAIN:
                return "保持";
            default:
                return "未知";
        }
    }
}
