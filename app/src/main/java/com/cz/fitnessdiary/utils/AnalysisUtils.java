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

    // ── Body Measurement helpers ──

    public static String getMeasurementTypeName(String type) {
        switch (type) {
            case "BODY_FAT": return "体脂率";
            case "CHEST": return "胸围";
            case "WAIST": return "腰围";
            case "HIP": return "臀围";
            case "ARM": return "臂围";
            case "THIGH": return "大腿围";
            case "CALF": return "小腿围";
            default: return type;
        }
    }

    public static String getMeasurementMethod(String type) {
        switch (type) {
            case "BODY_FAT":
                return "使用体脂秤或皮脂卡尺测量。站立放松，体脂秤需光脚接触电极；卡尺法测量腹部、三头肌、大腿前侧等部位皮褶厚度后计算。建议早晨空腹、排尿后测量。";
            case "CHEST":
                return "站立放松，软尺绕过胸背部，前方经过乳头水平位（男性）或胸围最大处（女性），保持软尺水平，正常呼吸，在呼气末读数。";
            case "WAIST":
                return "站立放松，双脚并拢，软尺绕过肚脐上方约1cm处水平一周。保持正常呼吸，不要收腹，在呼气末读数。此为WHO推荐测量位。";
            case "HIP":
                return "站立双脚并拢，软尺绕过臀部最突出处水平一周。保持软尺紧贴但不压迫皮肤。";
            case "ARM":
                return "站立放松，手臂自然下垂，软尺绕过上臂中部（肩峰到鹰嘴中点）水平一周。可在屈臂和非屈臂两种状态分别测量。";
            case "THIGH":
                return "站立双脚分开与肩同宽，软尺绕过臀横纹下方大腿最粗处水平一周。保持软尺水平，双腿均匀承重。";
            case "CALF":
                return "站立或坐姿，软尺绕过小腿最粗处水平一周。保持软尺水平不倾斜，双腿均匀承重。早晚差异较大，建议固定时间测量。";
            default: return "";
        }
    }

    public static String getBodyFatZone(float bf, boolean isMale) {
        if (isMale) {
            if (bf < 6) return "必需脂肪";
            if (bf < 14) return "运动员";
            if (bf < 18) return "健康";
            if (bf < 25) return "正常偏高";
            return "肥胖";
        } else {
            if (bf < 14) return "必需脂肪";
            if (bf < 21) return "运动员";
            if (bf < 25) return "健康";
            if (bf < 32) return "正常偏高";
            return "肥胖";
        }
    }

    public static String getWaistHipRatioAssessment(float whr, boolean isMale) {
        if (isMale) {
            if (whr < 0.90) return "健康";
            if (whr < 0.95) return "轻微风险";
            return "高风险";
        } else {
            if (whr < 0.80) return "健康";
            if (whr < 0.85) return "轻微风险";
            return "高风险";
        }
    }

    // ── Bowel movement helpers ──

    public static String getBristolTypeName(int type) {
        switch (type) {
            case 1: return "坚果状 (严重便秘)";
            case 2: return "干裂香肠状 (便秘)";
            case 3: return "玉米状 (正常)";
            case 4: return "香蕉状 (理想)";
            case 5: return "软团状 (偏稀)";
            case 6: return "糊状 (腹泻)";
            case 7: return "水状 (严重腹泻)";
            default: return "未知";
        }
    }

    public static String getBristolCategory(int type) {
        if (type <= 2) return "便秘";
        if (type <= 4) return "正常";
        if (type == 5) return "偏稀";
        return "腹泻";
    }

    public static String getColorName(String color) {
        switch (color) {
            case "BROWN": return "棕色";
            case "GREEN": return "绿色";
            case "YELLOW": return "黄色";
            case "RED": return "红色";
            case "BLACK": return "黑色";
            case "WHITE": return "白色";
            case "GREY": return "灰色";
            default: return color;
        }
    }

    // ── Menstrual cycle helpers ──

    public static String getFlowIntensityName(String flow) {
        switch (flow) {
            case "LIGHT": return "少量";
            case "MEDIUM": return "中等";
            case "HEAVY": return "大量";
            default: return flow;
        }
    }

    public static String getSymptomName(String code) {
        switch (code) {
            case "CRAMPS": return "腹痛";
            case "BLOATING": return "腹胀";
            case "HEADACHE": return "头痛";
            case "FATIGUE": return "疲劳";
            case "BREAST_TENDERNESS": return "乳房胀痛";
            case "NAUSEA": return "恶心";
            case "BACK_PAIN": return "背痛";
            case "ACNE": return "痘痘";
            default: return code;
        }
    }

    public static String getMoodName(String code) {
        switch (code) {
            case "HAPPY": return "开心";
            case "SAD": return "低落";
            case "IRRITABLE": return "易怒";
            case "ANXIOUS": return "焦虑";
            case "CALM": return "平静";
            case "TIRED": return "疲倦";
            case "ENERGETIC": return "精力充沛";
            default: return code;
        }
    }

    public static String getRegularityDescription(float stdDev) {
        if (stdDev < 3) return "规律";
        if (stdDev < 7) return "较规律";
        return "不规律";
    }
}
