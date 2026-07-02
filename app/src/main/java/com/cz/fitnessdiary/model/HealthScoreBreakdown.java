package com.cz.fitnessdiary.model;

/**
 * 健康评分明细 - v3.0
 * 从五个维度对每日健康进行评分，满分 100
 */
public class HealthScoreBreakdown {

    public int exerciseScore;       // 运动评分 (0-25)
    public int dietScore;           // 饮食评分 (0-25)
    public int habitsScore;         // 习惯评分 (0-20)
    public int bodyMetricsScore;    // 身体指标评分 (0-15)
    public int consistencyScore;    // 坚持度评分 (0-15)
    public int totalScore;          // 总分 (0-100)

    /**
     * 计算总分，累加五个维度
     */
    public void computeTotal() {
        this.totalScore = this.exerciseScore + this.dietScore + this.habitsScore
                + this.bodyMetricsScore + this.consistencyScore;
    }
}
