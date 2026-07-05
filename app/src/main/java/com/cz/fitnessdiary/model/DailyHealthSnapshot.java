package com.cz.fitnessdiary.model;

/**
 * 每日健康数据快照 - v3.0
 * 聚合当日运动、饮食、身体指标等核心健康数据
 */
public class DailyHealthSnapshot {

    // 运动与热量
    public int exerciseCalories;   // 运动消耗（大卡）
    public int dietCalories;       // 饮食摄入（大卡）
    public int stepCalories;       // 步数消耗（大卡）
    public int bmr;                // 基础代谢（大卡）

    // 睡眠
    public double sleepHours;      // 睡眠时长（小时）
    public int sleepQuality;       // 睡眠质量 (1-5, 0=无数据)

    // 生活记录
    public int waterMl;            // 饮水量（毫升）
    public int steps;              // 步数
    public int moodLevel;          // 心情等级 (1-5)
    public int medicationTaken;    // 已服药次数
    public int medicationTotal;    // 应服药总次数
    public int bowelCount;         // 排便次数

    // 身体指标
    public float weightKg;         // 体重（公斤）
    public float weightTrend;      // 体重趋势变化
    public float bodyFat;          // 体脂率（%）
    public float bodyFatTrend;     // 体脂率趋势变化（正=下降）

    // 营养素摄入
    public int todayProtein;       // 每日蛋白质摄入（克）
    public int todayCarbs;         // 每日碳水摄入（克）
    public int todayFat;           // 脂肪 (g)

    // 训练计划
    public int completedPlans;     // 已完成计划数
    public int totalPlans;         // 总计划数
    public int consecutiveDays;    // 连续打卡天数
    public int activeDays7;        // 过去7天活跃天数（任一健康记录）
    public String currentPlanName; // 当前计划名称

    // 综合评分
    public int healthScore;        // 健康总分 (0-100)
    public int energyBalance;      // 热量平衡（正=盈余，负=赤字）

    /**
     * 计算热量平衡值
     * energyBalance = dietCalories - (bmr + exerciseCalories + stepCalories)
     */
    public void computeEnergyBalance() {
        this.energyBalance = this.dietCalories - (this.bmr + this.exerciseCalories + this.stepCalories);
    }
}
