package com.cz.fitnessdiary.utils;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * 训练动作 MET 值查询表 + 容量计算公式
 *
 * 热量公式: MET × 体重(kg) × 时长(小时)
 * 容量公式: 负重=组×次×重量 | 静力=组×时长 | 自重=组×次×(体重×生物力学系数)
 */
public class ExerciseMetTable {

    // ── 按分类的 MET 值 (30组) ──
    private static final Map<String, Double> CATEGORY_MET = new HashMap<>();

    // ── 特殊动作 MET 覆盖 ──
    private static final Map<String, Double> EXERCISE_OVERRIDES = new HashMap<>();

    // ── 器械动作关键词 ──
    private static final java.util.Set<String> EQUIPMENT_KEYWORDS = new java.util.HashSet<>(
        java.util.Arrays.asList("哑铃", "杠铃", "器械", "壶铃", "拉力器", "负重", "绳索", "史密斯")
    );

    // ── 明确使用器械但名称中无关键词的动作（来自 exercise_library.json）──
    private static final java.util.Set<String> EQUIPMENT_EXERCISE_NAMES = new java.util.HashSet<>(
        java.util.Arrays.asList(
            // 手臂 — 哑铃
            "锤式弯举", "集中弯举",
            // 手臂 — 杠铃
            "窄距卧推",
            // 肩部 — 哑铃
            "阿诺德推举",
            // 肩部 — 器械
            "跪姿面拉",
            // 背部 — 器械
            "面拉", "直臂下压", "坐姿划船",
            // 胸部 — 器械
            "龙门架夹胸",
            // 腿部 — 杠铃
            "保加利亚分腿蹲",
            // 腿部 — 器械
            "倒蹬机", "坐姿腿屈伸", "俯卧腿弯举",
            "坐姿提踵", "站姿提踵", "髋外展机", "髋内收机",
            // 腹部 — 器械
            "悬垂举腿", "健腹轮", "罗马椅侧屈"
        )
    );

    // ── 静力动作关键词 ──
    private static final java.util.Set<String> STATIC_KEYWORDS = new java.util.HashSet<>(
        java.util.Arrays.asList("支撑", "静蹲", "平板", "靠墙")
    );

    private static boolean isStaticExercise(String name) {
        if (name == null) return false;
        for (String kw : STATIC_KEYWORDS) {
            if (name.contains(kw)) return true;
        }
        return false;
    }

    // ── 自重动作生物力学难度系数 (等效推起体重的百分比) ──
    private static final Map<String, Float> BODYWEIGHT_COEFFICIENTS = new HashMap<>();

    static {
        // ── 分类 MET 值 ──
        CATEGORY_MET.put("胸部: 徒手", 5.0);
        CATEGORY_MET.put("胸部: 哑铃", 5.0);
        CATEGORY_MET.put("胸部: 杠铃", 5.5);
        CATEGORY_MET.put("胸部: 器械", 4.5);
        CATEGORY_MET.put("背部: 徒手", 5.0);
        CATEGORY_MET.put("背部: 哑铃", 5.0);
        CATEGORY_MET.put("背部: 杠铃", 5.5);
        CATEGORY_MET.put("背部: 器械", 4.5);
        CATEGORY_MET.put("肩部: 徒手", 3.5);
        CATEGORY_MET.put("肩部: 哑铃", 4.0);
        CATEGORY_MET.put("肩部: 杠铃", 4.5);
        CATEGORY_MET.put("肩部: 器械", 3.5);
        CATEGORY_MET.put("手臂: 徒手", 3.5);
        CATEGORY_MET.put("手臂: 哑铃", 3.5);
        CATEGORY_MET.put("手臂: 杠铃", 3.5);
        CATEGORY_MET.put("手臂: 器械", 3.0);
        CATEGORY_MET.put("腿部: 徒手", 5.0);
        CATEGORY_MET.put("腿部: 哑铃", 5.5);
        CATEGORY_MET.put("腿部: 杠铃", 6.0);
        CATEGORY_MET.put("腿部: 器械", 4.5);
        CATEGORY_MET.put("腹部: 徒手", 3.5);
        CATEGORY_MET.put("腹部: 器械", 3.0);
        CATEGORY_MET.put("臀部: 徒手", 3.5);
        CATEGORY_MET.put("臀部: 哑铃", 4.0);
        CATEGORY_MET.put("臀部: 杠铃", 4.5);
        CATEGORY_MET.put("臀部: 器械", 3.5);
        CATEGORY_MET.put("全身: 有氧", 7.0);
        CATEGORY_MET.put("拉伸: 全身拉伸", 2.5);
        CATEGORY_MET.put("拉伸: 上身拉伸", 2.5);
        CATEGORY_MET.put("拉伸: 下身拉伸", 2.5);
        CATEGORY_MET.put("拉伸: 脊柱拉伸", 2.5);

        // ── 特殊动作覆盖 (与分类默认不同的动作) ──
        EXERCISE_OVERRIDES.put("标准俯卧撑", 3.8);
        EXERCISE_OVERRIDES.put("宽距俯卧撑", 3.8);
        EXERCISE_OVERRIDES.put("窄距俯卧撑", 3.8);
        EXERCISE_OVERRIDES.put("上斜俯卧撑", 3.0);
        EXERCISE_OVERRIDES.put("下斜俯卧撑", 4.5);
        EXERCISE_OVERRIDES.put("钻石俯卧撑", 6.0);
        EXERCISE_OVERRIDES.put("击掌俯卧撑", 8.0);
        EXERCISE_OVERRIDES.put("平板支撑", 2.5);
        EXERCISE_OVERRIDES.put("波比跳", 8.0);
        EXERCISE_OVERRIDES.put("开合跳", 8.0);
        EXERCISE_OVERRIDES.put("战绳", 8.0);
        EXERCISE_OVERRIDES.put("俯身登山", 8.0);
        EXERCISE_OVERRIDES.put("双杠臂屈伸", 5.0);
        EXERCISE_OVERRIDES.put("高抬腿", 7.0);
        EXERCISE_OVERRIDES.put("登山者", 8.0);
        EXERCISE_OVERRIDES.put("深蹲跳", 8.0);
        EXERCISE_OVERRIDES.put("弓步蹲跳", 8.0);

        // ── 自重动作生物力学难度系数 (等效推起体重的百分比) ──
        // 胸部
        BODYWEIGHT_COEFFICIENTS.put("标准俯卧撑", 0.70f);
        BODYWEIGHT_COEFFICIENTS.put("宽距俯卧撑", 0.65f);
        BODYWEIGHT_COEFFICIENTS.put("窄距俯卧撑", 0.75f);
        BODYWEIGHT_COEFFICIENTS.put("钻石俯卧撑", 0.75f);
        BODYWEIGHT_COEFFICIENTS.put("砖石俯卧撑", 0.75f);
        BODYWEIGHT_COEFFICIENTS.put("上斜俯卧撑", 0.55f);
        BODYWEIGHT_COEFFICIENTS.put("下斜俯卧撑", 0.80f);
        BODYWEIGHT_COEFFICIENTS.put("击掌俯卧撑", 0.85f);
        BODYWEIGHT_COEFFICIENTS.put("支架俯卧撑", 0.70f);
        // 背部
        BODYWEIGHT_COEFFICIENTS.put("引体向上", 0.95f);
        BODYWEIGHT_COEFFICIENTS.put("澳洲引体向上", 0.60f);
        BODYWEIGHT_COEFFICIENTS.put("双杠臂屈伸", 0.90f);
        BODYWEIGHT_COEFFICIENTS.put("窄距支架臂屈伸", 0.85f);
        BODYWEIGHT_COEFFICIENTS.put("板凳臂屈伸", 0.70f);
        // 腿部
        BODYWEIGHT_COEFFICIENTS.put("自重深蹲", 0.75f);
        BODYWEIGHT_COEFFICIENTS.put("箭步蹲", 0.60f);
        BODYWEIGHT_COEFFICIENTS.put("保加利亚分腿蹲", 0.70f);
        BODYWEIGHT_COEFFICIENTS.put("深蹲跳", 1.0f);
        BODYWEIGHT_COEFFICIENTS.put("弓步蹲跳", 0.95f);
        BODYWEIGHT_COEFFICIENTS.put("靠墙静蹲", 0.40f);
        // 腹部
        BODYWEIGHT_COEFFICIENTS.put("卷腹", 0.30f);
        BODYWEIGHT_COEFFICIENTS.put("V字卷腹", 0.35f);
        BODYWEIGHT_COEFFICIENTS.put("反向卷腹", 0.25f);
        BODYWEIGHT_COEFFICIENTS.put("仰卧举腿", 0.30f);
        BODYWEIGHT_COEFFICIENTS.put("俄罗斯转体", 0.40f);
        BODYWEIGHT_COEFFICIENTS.put("蝴蝶卷腹", 0.30f);
        BODYWEIGHT_COEFFICIENTS.put("平板支撑", 0.25f);
        BODYWEIGHT_COEFFICIENTS.put("俯身登山", 0.65f);
        BODYWEIGHT_COEFFICIENTS.put("悬垂举腿", 0.45f);
        BODYWEIGHT_COEFFICIENTS.put("龙旗", 0.60f);
        BODYWEIGHT_COEFFICIENTS.put("动态平板支撑", 0.35f);
        BODYWEIGHT_COEFFICIENTS.put("交替抬腿", 0.30f);
        BODYWEIGHT_COEFFICIENTS.put("卷腹摸脚", 0.30f);
        BODYWEIGHT_COEFFICIENTS.put("仰卧交替抬腿", 0.30f);
        // 全身
        BODYWEIGHT_COEFFICIENTS.put("波比跳", 0.95f);
        BODYWEIGHT_COEFFICIENTS.put("开合跳", 0.50f);
        BODYWEIGHT_COEFFICIENTS.put("高抬腿", 0.55f);
    }

    /**
     * 获取动作的 MET 值
     * 优先级：动作名精确匹配 → 分类匹配 → 粗分类兜底 → 默认 4.0
     */
    public static double getMetForExercise(String exerciseName, String category) {
        // 1. 动作名精确覆盖
        if (exerciseName != null && EXERCISE_OVERRIDES.containsKey(exerciseName)) {
            return EXERCISE_OVERRIDES.get(exerciseName);
        }
        // 2. 分类匹配
        if (category != null && CATEGORY_MET.containsKey(category)) {
            return CATEGORY_MET.get(category);
        }
        // 3. 粗分类兜底
        if (category != null) {
            String cat = category.toLowerCase();
            if (cat.contains("有氧") || cat.contains("cardio") || cat.contains("跑步") || cat.contains("骑行"))
                return 7.0;
            if (cat.contains("hiit")) return 8.0;
            if (cat.contains("瑜伽") || cat.contains("拉伸") || cat.contains("yoga")) return 2.5;
            if (cat.contains("力量") || cat.contains("strength")) return 3.5;
        }
        return 4.0;
    }

    /**
     * 获取动作的 MET 值（无分类时）
     */
    public static double getMetForExercise(String exerciseName) {
        return getMetForExercise(exerciseName, null);
    }

    /**
     * 判断动作是否需要器械 (通过名称或分类关键词匹配)
     * @param name 动作名
     * @param category 分类 (如 "胸部: 哑铃", "腿部: 器械")
     * @return 是否需要器械
     */
    public static boolean isEquipmentExercise(String name, String category) {
        if (category != null) {
            for (String kw : EQUIPMENT_KEYWORDS) {
                if (category.contains(kw)) return true;
            }
        }
        if (name != null) {
            // 精确匹配已知器械动作名
            if (EQUIPMENT_EXERCISE_NAMES.contains(name)) return true;
            // 关键词模糊匹配
            for (String kw : EQUIPMENT_KEYWORDS) {
                if (name.contains(kw)) return true;
            }
        }
        return false;
    }

    /**
     * 三模式容量计算（内部使用，含生物力学系数）
     * @param name 动作名（用于查自重系数）
     * @param sets 组数
     * @param reps 次数 (静力动作 reps=1)
     * @param weight 负重 (kg, 用户手动设定, 0=自重)
     * @param planDuration 计划预设时长 (秒, 用于静力动作)
     * @param userWeight 用户体重 (kg, 自重动作需要)
     * @return 容量值
     */
    public static int calculateVolume(String name, int sets, int reps, float weight,
                                       int planDuration, float userWeight) {
        if (weight > 0) {
            // 负重动作: 组 × 次 × 用户输入的重量(kg)
            return (int) (sets * reps * weight);
        } else if ((reps == 1 || isStaticExercise(name)) && planDuration > 0) {
            // 静力动作 (平板支撑等): 组 × 时长(秒)
            return sets * planDuration;
        } else if (isEquipmentExercise(name, null)) {
            // 器械动作但未设重量→0 (用户还没设置重量)
            return 0;
        } else {
            // 自重动作: 组 × 次 × (体重 × 生物力学系数)
            float coeff = getBodyweightCoefficient(name);
            float load = userWeight * coeff;
            return (int) (sets * reps * load);
        }
    }

    /**
     * 获取动作的生物力学难度系数 (默认0.60)
     */
    public static float getBodyweightCoefficient(String exerciseName) {
        if (exerciseName != null && BODYWEIGHT_COEFFICIENTS.containsKey(exerciseName)) {
            return BODYWEIGHT_COEFFICIENTS.get(exerciseName);
        }
        return 0.60f; // 未知自重动作默认等效推起60%体重
    }

    /**
     * 获取容量单位字符串
     */
    public static String getVolumeUnit(int sets, int reps, float weight, int planDuration, String name) {
        if (weight > 0) return "kg";
        if ((reps == 1 || isStaticExercise(name)) && planDuration > 0) return "秒";
        if (isEquipmentExercise(name, null)) return "";  // 未设重量的器械动作
        return "次";
    }

    /**
     * 解析动作时长: log时长 > plan时长 > 用户目标时长 > 360秒(6分钟)
     */
    /**
     * 解析单动作时长: log > plan > 智能估算(组数×次数×3秒 + 组间休息) > 120秒
     * @param sets 组数 (估算用)
     * @param reps 次数 (估算用)
     */
    public static int resolveDuration(int logDuration, int planDuration, int sets, int reps, Context context) {
        if (logDuration > 0) return logDuration;
        if (planDuration > 0) return planDuration;
        // 智能估算: 每组每动作3秒 + 组间休息60秒
        if (sets > 0 && reps > 0) {
            int estimated = sets * reps * 3 + (sets - 1) * 60;
            return Math.max(estimated, 120); // 最少2分钟
        }
        return 360; // 无法估算时兜底6分钟
    }

    /**
     * 解析单动作时长 (无组数次数的简化版)
     */
    public static int resolveDuration(int logDuration, int planDuration, Context context) {
        return resolveDuration(logDuration, planDuration, 0, 0, context);
    }

    /**
     * 解析当天总时长: 该日target_minutes > 各动作累计
     * 按日期独立存储，互不干扰
     */
    public static int resolveTotalDuration(int accumulatedSec, long dateTs, Context context) {
        String dateKey = "target_minutes_" + dateTs;
        int targetMin = context.getSharedPreferences("fitness_diary_prefs", Context.MODE_PRIVATE)
                .getInt(dateKey, 0);
        if (targetMin > 0) return targetMin * 60;
        return accumulatedSec;
    }

    /**
     * 格式化容量显示 (不暴露自重kg数，避免误解)
     */
    public static String formatVolume(int volume, String unit, float weight,
                                       String name, int sets, int reps) {
        if ("kg".equals(unit)) {
            // 负重动作：直接显示 kg
            if (volume >= 1000) return String.format("%.1f吨", volume / 1000.0);
            return volume + " kg";
        } else if ("秒".equals(unit)) {
            // 静力动作
            if (volume >= 60) return (volume / 60) + "分" + (volume % 60 > 0 ? volume % 60 + "秒" : "");
            return volume + "秒";
        }
        // 自重动作：显示 次数 + 等效负荷百分比
        float coeff = getBodyweightCoefficient(name);
        int pct = (int) (coeff * 100);
        return sets * reps + " 次 (≈" + pct + "%体重)";
    }
}
