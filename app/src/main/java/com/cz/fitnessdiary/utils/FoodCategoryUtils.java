package com.cz.fitnessdiary.utils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 食物分类统一入口：
 * 1) 提供与数据库一致的标准分类集合
 * 2) 提供 UI 展示文案（含 emoji）
 * 3) 提供 AI/手工输入分类的归一化映射
 */
public final class FoodCategoryUtils {

    public static final String CAT_STAPLE_RICE = "主食: 基础米面";
    public static final String CAT_STAPLE_PORRIDGE = "主食: 营养粥类";
    public static final String CAT_STAPLE_TUBER = "主食: 根茎薯类";
    public static final String CAT_STAPLE_BUN = "主食: 面点包子";
    public static final String CAT_STAPLE_DUMPLING = "主食: 饺子馄饨";
    public static final String CAT_STAPLE_NOODLE = "主食: 汤粉面条";
    public static final String CAT_STAPLE_FAST = "主食: 西式快餐";
    public static final String CAT_DISH_MEAT = "菜肴: 精选荤菜";
    public static final String CAT_DISH_VEG = "菜肴: 清爽素菜";
    public static final String CAT_PROTEIN_DAILY = "蛋白: 蛋奶豆制品";
    public static final String CAT_PROTEIN_MEAT = "蛋白: 肉类海鲜";
    public static final String CAT_PROTEIN_SUPPLEMENT = "蛋白: 健身补剂";
    public static final String CAT_VEG = "蔬菜: 新鲜时蔬";
    public static final String CAT_FRUIT = "水果: 时令水果";
    public static final String CAT_SNACK = "零食: 包装小吃";
    public static final String CAT_DRINK = "饮料: 咖啡奶茶";
    public static final String CAT_CONDIMENT = "调料/油脂 (Condiments)";
    public static final String CAT_ALCOHOL = "酒精饮料 (Alcohol)";
    public static final String CAT_OTHER = "其他";

    private static final List<String> CANONICAL_CATEGORIES = Arrays.asList(
            CAT_STAPLE_RICE,
            CAT_STAPLE_PORRIDGE,
            CAT_STAPLE_TUBER,
            CAT_STAPLE_BUN,
            CAT_STAPLE_DUMPLING,
            CAT_STAPLE_NOODLE,
            CAT_STAPLE_FAST,
            CAT_DISH_MEAT,
            CAT_DISH_VEG,
            CAT_PROTEIN_DAILY,
            CAT_PROTEIN_MEAT,
            CAT_PROTEIN_SUPPLEMENT,
            CAT_VEG,
            CAT_FRUIT,
            CAT_SNACK,
            CAT_DRINK,
            CAT_CONDIMENT,
            CAT_ALCOHOL,
            CAT_OTHER);

    private static final Map<String, String> DISPLAY_TO_CANONICAL = new LinkedHashMap<>();
    private static final String[] DISPLAY_CATEGORIES;

    static {
        DISPLAY_TO_CANONICAL.put("🍚 " + CAT_STAPLE_RICE, CAT_STAPLE_RICE);
        DISPLAY_TO_CANONICAL.put("🥣 " + CAT_STAPLE_PORRIDGE, CAT_STAPLE_PORRIDGE);
        DISPLAY_TO_CANONICAL.put("🍠 " + CAT_STAPLE_TUBER, CAT_STAPLE_TUBER);
        DISPLAY_TO_CANONICAL.put("🥯 " + CAT_STAPLE_BUN, CAT_STAPLE_BUN);
        DISPLAY_TO_CANONICAL.put("🥟 " + CAT_STAPLE_DUMPLING, CAT_STAPLE_DUMPLING);
        DISPLAY_TO_CANONICAL.put("🍜 " + CAT_STAPLE_NOODLE, CAT_STAPLE_NOODLE);
        DISPLAY_TO_CANONICAL.put("🍔 " + CAT_STAPLE_FAST, CAT_STAPLE_FAST);
        DISPLAY_TO_CANONICAL.put("🍱 " + CAT_DISH_MEAT, CAT_DISH_MEAT);
        DISPLAY_TO_CANONICAL.put("🥗 " + CAT_DISH_VEG, CAT_DISH_VEG);
        DISPLAY_TO_CANONICAL.put("🥛 " + CAT_PROTEIN_DAILY, CAT_PROTEIN_DAILY);
        DISPLAY_TO_CANONICAL.put("🥩 " + CAT_PROTEIN_MEAT, CAT_PROTEIN_MEAT);
        DISPLAY_TO_CANONICAL.put("💪 " + CAT_PROTEIN_SUPPLEMENT, CAT_PROTEIN_SUPPLEMENT);
        DISPLAY_TO_CANONICAL.put("🥦 " + CAT_VEG, CAT_VEG);
        DISPLAY_TO_CANONICAL.put("🍎 " + CAT_FRUIT, CAT_FRUIT);
        DISPLAY_TO_CANONICAL.put("🍿 " + CAT_SNACK, CAT_SNACK);
        DISPLAY_TO_CANONICAL.put("☕ " + CAT_DRINK, CAT_DRINK);
        DISPLAY_TO_CANONICAL.put("🧂 " + CAT_CONDIMENT, CAT_CONDIMENT);
        DISPLAY_TO_CANONICAL.put("🍷 " + CAT_ALCOHOL, CAT_ALCOHOL);
        DISPLAY_TO_CANONICAL.put("❓ " + CAT_OTHER, CAT_OTHER);
        DISPLAY_CATEGORIES = DISPLAY_TO_CANONICAL.keySet().toArray(new String[0]);
    }

    private FoodCategoryUtils() {
    }

    public static List<String> getCanonicalCategories() {
        return CANONICAL_CATEGORIES;
    }

    public static String[] getDisplayCategories() {
        return DISPLAY_CATEGORIES;
    }

    public static String toDisplayCategory(String category) {
        String canonical = normalizeCategory(category);
        for (Map.Entry<String, String> entry : DISPLAY_TO_CANONICAL.entrySet()) {
            if (entry.getValue().equals(canonical)) {
                return entry.getKey();
            }
        }
        return "❓ " + CAT_OTHER;
    }

    public static String normalizeCategory(String rawInput) {
        if (rawInput == null) {
            return CAT_OTHER;
        }
        String cleaned = stripEmoji(rawInput).trim();
        if (cleaned.isEmpty()) {
            return CAT_OTHER;
        }

        // 直接匹配标准分类
        for (String canonical : CANONICAL_CATEGORIES) {
            if (canonical.equals(cleaned)) {
                return canonical;
            }
        }

        // 展示项反查
        if (DISPLAY_TO_CANONICAL.containsKey(rawInput)) {
            return DISPLAY_TO_CANONICAL.get(rawInput);
        }
        if (DISPLAY_TO_CANONICAL.containsKey(cleaned)) {
            return DISPLAY_TO_CANONICAL.get(cleaned);
        }

        String source = cleaned.toLowerCase();

        if (source.contains("主食")) {
            if (source.contains("粥")) {
                return CAT_STAPLE_PORRIDGE;
            }
            if (source.contains("薯") || source.contains("根茎") || source.contains("红薯") || source.contains("土豆")
                    || source.contains("玉米")) {
                return CAT_STAPLE_TUBER;
            }
            if (source.contains("包子") || source.contains("面点") || source.contains("馒头") || source.contains("面包")
                    || source.contains("油条")) {
                return CAT_STAPLE_BUN;
            }
            if (source.contains("饺") || source.contains("馄饨") || source.contains("抄手")) {
                return CAT_STAPLE_DUMPLING;
            }
            if (source.contains("面条") || source.contains("汤粉") || source.contains("米线") || source.contains("粉")) {
                return CAT_STAPLE_NOODLE;
            }
            if (source.contains("快餐") || source.contains("汉堡") || source.contains("披萨")) {
                return CAT_STAPLE_FAST;
            }
            return CAT_STAPLE_RICE;
        }

        if (source.contains("菜肴") || source.contains("家常菜") || source.contains("荤菜") || source.contains("素菜")) {
            if (source.contains("素")) {
                return CAT_DISH_VEG;
            }
            return CAT_DISH_MEAT;
        }

        if (source.contains("蛋白") || source.contains("蛋奶") || source.contains("豆制品")
                || source.contains("肉类") || source.contains("海鲜") || source.contains("补剂")) {
            if (source.contains("补剂")) {
                return CAT_PROTEIN_SUPPLEMENT;
            }
            if (source.contains("肉类") || source.contains("海鲜")) {
                return CAT_PROTEIN_MEAT;
            }
            return CAT_PROTEIN_DAILY;
        }

        if (source.contains("蔬菜") || source.contains("时蔬")) {
            return CAT_VEG;
        }
        if (source.contains("水果")) {
            return CAT_FRUIT;
        }

        if (source.contains("零食") || source.contains("小吃")) {
            return CAT_SNACK;
        }
        if (source.contains("饮料") || source.contains("咖啡") || source.contains("奶茶")) {
            return CAT_DRINK;
        }

        if (source.contains("调料") || source.contains("油脂") || source.contains("condiment")) {
            return CAT_CONDIMENT;
        }

        if (source.contains("酒精") || source.contains("alcohol") || source.contains("啤酒") || source.contains("红酒")
                || source.contains("白酒")) {
            return CAT_ALCOHOL;
        }

        if (source.contains("其他")) {
            return CAT_OTHER;
        }
        return CAT_OTHER;
    }

    private static String stripEmoji(String value) {
        return value.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+", "").trim();
    }
}
