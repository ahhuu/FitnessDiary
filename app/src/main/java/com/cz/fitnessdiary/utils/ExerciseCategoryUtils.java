package com.cz.fitnessdiary.utils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExerciseCategoryUtils {

    public static final String CAT_CHEST_BW = "胸部: 徒手";
    public static final String CAT_CHEST_DB = "胸部: 哑铃";
    public static final String CAT_CHEST_BB = "胸部: 杠铃";
    public static final String CAT_CHEST_MACHINE = "胸部: 器械";

    public static final String CAT_BACK_BW = "背部: 徒手";
    public static final String CAT_BACK_DB = "背部: 哑铃";
    public static final String CAT_BACK_BB = "背部: 杠铃";
    public static final String CAT_BACK_MACHINE = "背部: 器械";
    public static final String CAT_BACK_FLEX = "背部: 柔韧";

    public static final String CAT_SHOULDER_DB = "肩部: 哑铃";
    public static final String CAT_SHOULDER_BB = "肩部: 杠铃";
    public static final String CAT_SHOULDER_MACHINE = "肩部: 器械";

    public static final String CAT_ARM_DB = "手臂: 哑铃";
    public static final String CAT_ARM_BB = "手臂: 杠铃";
    public static final String CAT_ARM_MACHINE = "手臂: 器械";
    public static final String CAT_ARM_BW = "手臂: 徒手";

    public static final String CAT_LEG_BB = "腿部: 杠铃";
    public static final String CAT_LEG_BW = "腿部: 徒手";
    public static final String CAT_LEG_DB = "腿部: 哑铃";
    public static final String CAT_LEG_MACHINE = "腿部: 器械";
    public static final String CAT_LEG_FLEX = "腿部: 柔韧";

    public static final String CAT_ABS_BW = "腹部: 徒手";
    public static final String CAT_ABS_MACHINE = "腹部: 器械";

    public static final String CAT_GLUTE_BW = "臀部: 徒手";
    public static final String CAT_GLUTE_BB = "臀部: 杠铃";
    public static final String CAT_GLUTE_DB = "臀部: 哑铃";
    public static final String CAT_GLUTE_MACHINE = "臀部: 器械";
    public static final String CAT_GLUTE_FLEX = "臀部: 柔韧";

    public static final String CAT_FULL_CARDIO = "全身: 有氧";
    public static final String CAT_FULL_FLEX = "全身: 柔韧";

    // 大类 → 默认子类
    public static final Map<String, String> BODYPART_DEFAULT_SUBCATEGORY = new LinkedHashMap<>();

    static {
        BODYPART_DEFAULT_SUBCATEGORY.put("胸部", "徒手");
        BODYPART_DEFAULT_SUBCATEGORY.put("背部", "徒手");
        BODYPART_DEFAULT_SUBCATEGORY.put("肩部", "哑铃");
        BODYPART_DEFAULT_SUBCATEGORY.put("手臂", "哑铃");
        BODYPART_DEFAULT_SUBCATEGORY.put("腿部", "徒手");
        BODYPART_DEFAULT_SUBCATEGORY.put("腹部", "徒手");
        BODYPART_DEFAULT_SUBCATEGORY.put("臀部", "徒手");
        BODYPART_DEFAULT_SUBCATEGORY.put("全身", "有氧");
    }

    public static final List<String> BODYPART_ORDER = Arrays.asList(
            "胸部", "背部", "肩部", "手臂", "腿部", "腹部", "臀部", "全身");

    private static final Map<String, String> DISPLAY_TO_CANONICAL = new LinkedHashMap<>();
    private static final String[] DISPLAY_CATEGORIES;

    static {
        DISPLAY_TO_CANONICAL.put("胸 " + CAT_CHEST_BW, CAT_CHEST_BW);
        DISPLAY_TO_CANONICAL.put("胸 " + CAT_CHEST_DB, CAT_CHEST_DB);
        DISPLAY_TO_CANONICAL.put("胸 " + CAT_CHEST_BB, CAT_CHEST_BB);
        DISPLAY_TO_CANONICAL.put("胸 " + CAT_CHEST_MACHINE, CAT_CHEST_MACHINE);
        DISPLAY_TO_CANONICAL.put("背 " + CAT_BACK_BW, CAT_BACK_BW);
        DISPLAY_TO_CANONICAL.put("背 " + CAT_BACK_DB, CAT_BACK_DB);
        DISPLAY_TO_CANONICAL.put("背 " + CAT_BACK_BB, CAT_BACK_BB);
        DISPLAY_TO_CANONICAL.put("背 " + CAT_BACK_MACHINE, CAT_BACK_MACHINE);
        DISPLAY_TO_CANONICAL.put("肩 " + CAT_SHOULDER_DB, CAT_SHOULDER_DB);
        DISPLAY_TO_CANONICAL.put("肩 " + CAT_SHOULDER_BB, CAT_SHOULDER_BB);
        DISPLAY_TO_CANONICAL.put("肩 " + CAT_SHOULDER_MACHINE, CAT_SHOULDER_MACHINE);
        DISPLAY_TO_CANONICAL.put("臂 " + CAT_ARM_DB, CAT_ARM_DB);
        DISPLAY_TO_CANONICAL.put("臂 " + CAT_ARM_BB, CAT_ARM_BB);
        DISPLAY_TO_CANONICAL.put("臂 " + CAT_ARM_MACHINE, CAT_ARM_MACHINE);
        DISPLAY_TO_CANONICAL.put("臂 " + CAT_ARM_BW, CAT_ARM_BW);
        DISPLAY_TO_CANONICAL.put("腿 " + CAT_LEG_BB, CAT_LEG_BB);
        DISPLAY_TO_CANONICAL.put("腿 " + CAT_LEG_BW, CAT_LEG_BW);
        DISPLAY_TO_CANONICAL.put("腿 " + CAT_LEG_DB, CAT_LEG_DB);
        DISPLAY_TO_CANONICAL.put("腿 " + CAT_LEG_MACHINE, CAT_LEG_MACHINE);
        DISPLAY_TO_CANONICAL.put("腹 " + CAT_ABS_BW, CAT_ABS_BW);
        DISPLAY_TO_CANONICAL.put("腹 " + CAT_ABS_MACHINE, CAT_ABS_MACHINE);
        DISPLAY_TO_CANONICAL.put("臀 " + CAT_GLUTE_BW, CAT_GLUTE_BW);
        DISPLAY_TO_CANONICAL.put("臀 " + CAT_GLUTE_BB, CAT_GLUTE_BB);
        DISPLAY_TO_CANONICAL.put("臀 " + CAT_GLUTE_DB, CAT_GLUTE_DB);
        DISPLAY_TO_CANONICAL.put("臀 " + CAT_GLUTE_MACHINE, CAT_GLUTE_MACHINE);
        DISPLAY_TO_CANONICAL.put("全 " + CAT_FULL_CARDIO, CAT_FULL_CARDIO);
        DISPLAY_TO_CANONICAL.put("全 " + CAT_FULL_FLEX, CAT_FULL_FLEX);
        DISPLAY_CATEGORIES = DISPLAY_TO_CANONICAL.keySet().toArray(new String[0]);
    }

    private ExerciseCategoryUtils() {}

    public static List<String> getBodyPartOrder() { return BODYPART_ORDER; }

    public static String toDisplayCategory(String category) {
        for (Map.Entry<String, String> entry : DISPLAY_TO_CANONICAL.entrySet()) {
            if (entry.getValue().equals(category)) return entry.getKey();
        }
        return category;
    }

    public static String normalizeCategory(String rawInput) {
        if (rawInput == null) return "全身: 有氧";
        String cleaned = stripEmoji(rawInput).trim();
        if (cleaned.isEmpty()) return "全身: 有氧";

        for (Map.Entry<String, String> entry : DISPLAY_TO_CANONICAL.entrySet()) {
            if (entry.getValue().equals(cleaned) || entry.getKey().equals(cleaned))
                return entry.getValue();
        }

        String source = cleaned.toLowerCase();
        if (source.contains("胸")) {
            if (source.contains("徒手")) return CAT_CHEST_BW;
            if (source.contains("哑铃")) return CAT_CHEST_DB;
            if (source.contains("杠铃")) return CAT_CHEST_BB;
            if (source.contains("器械")) return CAT_CHEST_MACHINE;
            return CAT_CHEST_BW;
        }
        if (source.contains("背")) {
            if (source.contains("徒手")) return CAT_BACK_BW;
            if (source.contains("哑铃")) return CAT_BACK_DB;
            if (source.contains("杠铃")) return CAT_BACK_BB;
            if (source.contains("器械")) return CAT_BACK_MACHINE;
            return CAT_BACK_BW;
        }
        if (source.contains("肩")) {
            if (source.contains("杠铃")) return CAT_SHOULDER_BB;
            if (source.contains("器械")) return CAT_SHOULDER_MACHINE;
            return CAT_SHOULDER_DB;
        }
        if (source.contains("臂") || source.contains("手") || source.contains("二头") || source.contains("三头")) {
            if (source.contains("徒手")) return CAT_ARM_BW;
            if (source.contains("杠铃")) return CAT_ARM_BB;
            if (source.contains("器械")) return CAT_ARM_MACHINE;
            return CAT_ARM_DB;
        }
        if (source.contains("腿") || source.contains("小腿") || source.contains("大腿")) {
            if (source.contains("杠铃")) return CAT_LEG_BB;
            if (source.contains("哑铃")) return CAT_LEG_DB;
            if (source.contains("器械")) return CAT_LEG_MACHINE;
            return CAT_LEG_BW;
        }
        if (source.contains("腹") || source.contains("核心")) {
            if (source.contains("器械")) return CAT_ABS_MACHINE;
            return CAT_ABS_BW;
        }
        if (source.contains("臀")) {
            if (source.contains("杠铃")) return CAT_GLUTE_BB;
            if (source.contains("哑铃")) return CAT_GLUTE_DB;
            if (source.contains("器械")) return CAT_GLUTE_MACHINE;
            return CAT_GLUTE_BW;
        }
        if (source.contains("有氧") || source.contains("全身")) return CAT_FULL_CARDIO;
        if (source.contains("柔韧") || source.contains("拉伸") || source.contains("瑜伽")) return CAT_FULL_FLEX;

        return "全身: 有氧";
    }

    private static String stripEmoji(String value) {
        return value.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+", "").trim();
    }
}
