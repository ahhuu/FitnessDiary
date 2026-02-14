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
    public static String getAnalysisResult(int workoutDays, int avgCalories, int targetCalories,
            float avgSleepDuration, float avgSleepQuality, boolean isMonth) {
        StringBuilder sb = new StringBuilder();

        // 1. 训练维度分析
        if (isMonth) {
            if (workoutDays >= 20) {
                sb.append("🔥 您本月的训练表现简直是“健身达in”级别！极高的自律性让您的肌肉储备和代谢水平处于巅峰状态。");
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

        sb.append("\n\n");

        // 3. 睡眠维度分析 (NEW)
        if (avgSleepDuration <= 0) {
            sb.append("🌙 睡眠方面，暂无记录。良好的睡眠是身体修复和荷尔蒙分泌的基石，建议从今晚开始记录。");
        } else {
            if (avgSleepDuration < 6) {
                sb.append(String.format(Locale.getDefault(),
                        "🌙 睡眠严重不足：平均仅 %.1f 小时。长期欠薪会导致皮质醇上升，直接抑制增肌并诱发暴食。请强制增加休息时间。", avgSleepDuration));
            } else if (avgSleepQuality < 3.5) {
                sb.append("🌙 睡眠质量欠佳：虽然时长尚可，但反馈质量偏低。建议睡前1小时关掉电子设备，尝试冥想或温水浴，提升深度睡眠比例。");
            } else {
                sb.append(String.format(Locale.getDefault(), "🌙 优质睡眠：平均 %.1f 小时且质量上乘。这极大助力了您的神经系统恢复，是高强度训练后的最佳补剂。",
                        avgSleepDuration));
            }
        }

        return sb.toString();
    }
}
