package com.cz.fitnessdiary.utils;

import java.util.Locale;

/**
 * 智能分析工具类
 * 根据训练和饮食数据生成个性化建议
 */
public class AnalysisUtils {

    /**
     * 生成综合分析建议
     *
     * @param workoutDays    训练天数
     * @param avgCalories    平均摄入热量
     * @param targetCalories 目标热量
     * @param isMonth        是否是月度数据
     * @return 建议文案
     */
    public static String getAnalysisResult(int workoutDays, int avgCalories, int targetCalories, boolean isMonth) {
        StringBuilder sb = new StringBuilder();

        // 1. 训练维度分析
        if (isMonth) {
            if (workoutDays >= 20) {
                sb.append("🔥 您本月的训练表现简直是“健身达人”级别！极高的自律性让您的肌肉储备和代谢水平处于巅峰状态。");
            } else if (workoutDays >= 12) {
                sb.append("✨ 训练节奏掌握得不错。稳定的频率是长期进步的关键，继续保持这种生活节奏。");
            } else if (workoutDays > 0) {
                sb.append("💪 训练次数略少。如果您正处于忙碌期，可以尝试缩短单次时长但提高强度的早起训练。");
            } else {
                sb.append("⚠️ 警报！您已经一个月没有开启训练模式了。哪怕是从每天10分钟的拉伸开始，也要重新唤醒肌肉。");
            }
        } else {
            if (workoutDays >= 5) {
                sb.append("🔥 本周训练强度拉满！您的身体正处于高效合成期，请务必保证足够的深度睡眠。");
            } else if (workoutDays >= 3) {
                sb.append("✨ 训练频率达标。对于维持体型和心肺健康来说，每周3-4次是黄金比例。");
            } else if (workoutDays > 0) {
                sb.append("💪 训练量偏低。建议利用周末进行一次较长时间的反抗阻力训练，提升代谢。");
            } else {
                sb.append("🧘 这是一个休息周吗？适当的放松有益恢复，但下周别忘了穿上你的运动鞋！");
            }
        }

        sb.append("\n\n");

        // 2. 饮食维度分析
        if (avgCalories <= 0) {
            sb.append("🥗 饮食方面，由于缺乏近期的热量记录，暂时无法评估数据。记得打卡每餐，让分析更精准哦！");
        } else {
            float ratio = (float) avgCalories / targetCalories;
            if (ratio > 1.1) {
                sb.append(String.format(Locale.getDefault(),
                        "🥗 热量消耗告急：实际摄入超出目标 %.0f%%。建议减少精制碳水和高脂零食，增加非淀粉类蔬菜的比例，防止脂肪堆积。", (ratio - 1) * 100));
            } else if (ratio < 0.8) {
                sb.append("🥗 热量摄入不足：您的摄入低于身体基础需求。长期热量缺口过大会导致代谢降低和疲劳，建议适当补充优质脂肪和复合碳水。");
            } else {
                sb.append("🥗 营养平衡完美：热量摄入紧贴目标曲线。保持这种优质的宏量营养素比例，有助于体脂管理和肌肉修复。");
            }
        }

        return sb.toString();
    }
}
