package com.cz.fitnessdiary.service;

import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;
import com.cz.fitnessdiary.utils.FoodUnitUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a short natural-language diet entry against the local food library.
 * This class is deliberately synchronous and network-free so callers can run it
 * on their existing background executor before deciding whether AI is needed.
 */
public final class DietLibraryTextMatcher {
    private static final String NUMBER = "(?:[0-9]+(?:\\.[0-9]+)?|[零一二两三四五六七八九十百半]+)";
    private static final String UNIT = "(?:kg|千克|克|g|ml|毫升|L|升|个|只|粒|片|块|串|碗|盘|杯|勺|份|包|袋|盒|根|张|把|节)";
    private static final Pattern BEFORE_QUANTITY = Pattern.compile(
            "(?i)(" + NUMBER + ")\\s*(" + UNIT + ")\\s*$");
    private static final Pattern AFTER_QUANTITY = Pattern.compile(
            "(?i)^\\s*(" + NUMBER + ")\\s*(" + UNIT + ")");
    private static final Set<String> COUNT_UNITS = new HashSet<>();

    static {
        COUNT_UNITS.add("个");
        COUNT_UNITS.add("只");
        COUNT_UNITS.add("粒");
        COUNT_UNITS.add("片");
        COUNT_UNITS.add("块");
        COUNT_UNITS.add("串");
        COUNT_UNITS.add("碗");
        COUNT_UNITS.add("盘");
        COUNT_UNITS.add("杯");
        COUNT_UNITS.add("勺");
        COUNT_UNITS.add("份");
        COUNT_UNITS.add("包");
        COUNT_UNITS.add("袋");
        COUNT_UNITS.add("盒");
        COUNT_UNITS.add("根");
        COUNT_UNITS.add("张");
        COUNT_UNITS.add("把");
        COUNT_UNITS.add("节");
    }

    private DietLibraryTextMatcher() {
    }

    public static ImageMealDraft match(String text, List<FoodLibrary> foods) {
        if (text == null || text.trim().isEmpty() || foods == null || foods.isEmpty()) {
            return null;
        }

        String input = text.trim();
        List<FoodLibrary> candidates = new ArrayList<>();
        for (FoodLibrary food : foods) {
            if (food != null && food.getName() != null && !food.getName().trim().isEmpty()) {
                candidates.add(food);
            }
        }
        candidates.sort(Comparator.comparingInt((FoodLibrary food) ->
                food.getName() == null ? 0 : food.getName().trim().length()).reversed());

        List<MatchedFood> candidatesInText = new ArrayList<>();
        for (FoodLibrary food : candidates) {
            String name = food.getName().trim();
            addOccurrences(input, food, name, candidatesInText);
            for (String alias : aliasesFor(name)) {
                addOccurrences(input, food, alias, candidatesInText);
            }
        }
        List<MatchedFood> matches = selectBestCombination(candidatesInText);
        if (matches.isEmpty()) {
            return null;
        }

        matches.sort(Comparator.comparingInt(match -> match.start));
        ImageMealDraft result = new ImageMealDraft();
        result.setSourceType(ImageMealDraft.SOURCE_LIBRARY);
        result.setOriginalText(input);
        result.setMealName(inferMealName(input));
        result.setMealType(1);
        String unmatchedText = buildUnmatchedText(input, matches);
        result.setUnmatchedText(unmatchedText);
        result.setSuggestion(unmatchedText.isEmpty()
                ? "已按本地食物库整理为 " + matches.size() + " 项，请确认份量后保存。"
                : "已找到 " + matches.size() + " 项本地食物，还有一小段内容需要确认；AI 估算可手动选择。\n"
                + "未匹配内容：" + unmatchedText);

        for (MatchedFood matched : matches) {
            result.getItems().add(toDraft(matched.food, matched.quantity));
        }
        result.recomputeTotals();
        return result;
    }

    private static void addOccurrences(String input, FoodLibrary food, String term,
            List<MatchedFood> output) {
        if (term == null || term.trim().isEmpty()) return;
        int from = 0;
        while (from < input.length()) {
            int start = input.indexOf(term, from);
            if (start < 0) break;
            int end = start + term.length();
            if (!containsSameOccurrence(output, start, end, food)) {
                output.add(new MatchedFood(food, start, end, parseQuantity(input, start, end)));
            }
            from = Math.max(end, start + 1);
        }
    }

    private static boolean containsSameOccurrence(List<MatchedFood> matches, int start, int end,
            FoodLibrary food) {
        for (MatchedFood match : matches) {
            if (match.start == start && match.end == end && match.food == food) return true;
        }
        return false;
    }

    private static List<String> aliasesFor(String foodName) {
        List<String> aliases = new ArrayList<>();
        if ("米饭".equals(foodName) || "白米饭".equals(foodName)) {
            aliases.add("饭");
        }
        return aliases;
    }

    /**
     * Chooses a non-overlapping combination globally instead of accepting the first
     * longest substring. Coverage is primary; a complete library dish wins ties.
     */
    private static List<MatchedFood> selectBestCombination(List<MatchedFood> candidates) {
        if (candidates.isEmpty()) return new ArrayList<>();
        candidates.sort(Comparator.comparingInt((MatchedFood match) -> match.end)
                .thenComparingInt(match -> match.start));
        Plan[] plans = new Plan[candidates.size() + 1];
        plans[0] = new Plan();
        for (int i = 1; i <= candidates.size(); i++) {
            MatchedFood current = candidates.get(i - 1);
            int previousCount = 0;
            for (int j = 0; j < i - 1; j++) {
                if (candidates.get(j).end <= current.start) previousCount = j + 1;
            }
            Plan take = plans[previousCount].append(current);
            Plan skip = plans[i - 1];
            plans[i] = betterPlan(take, skip);
        }
        return plans[candidates.size()].matches;
    }

    private static Plan betterPlan(Plan first, Plan second) {
        if (second == null) return first;
        if (first.score != second.score) return first.score > second.score ? first : second;
        return first.matches.size() < second.matches.size() ? first : second;
    }

    private static String inferMealName(String input) {
        if (input.length() <= 16 && !input.matches(".*[0-9零一二两三四五六七八九十百半].*")) {
            if (!input.matches(".*(吃了|喝了|今天|早餐|午餐|晚餐|加餐|和|与|还有).*")) {
                return input;
            }
        }
        return "文字饮食记录";
    }

    private static String buildUnmatchedText(String input, List<MatchedFood> matches) {
        StringBuilder remaining = new StringBuilder(input);
        for (MatchedFood match : matches) {
            for (int i = match.start; i < match.end && i < remaining.length(); i++) {
                remaining.setCharAt(i, ' ');
            }
        }
        String cleaned = remaining.toString()
                .replaceAll("[\\s，。；;、,。.：:！!？?和与及还有吃了喝了今天我的是一份早午晚餐]+", "")
                .trim();
        // A one-character residue is usually a compound-dish connector (for
        // example the "排" in "咖喱鸡排饭"), not a useful AI request.
        return cleaned.length() < 2 ? "" : cleaned;
    }

    private static ParsedQuantity parseQuantity(String input, int start, int end) {
        int beforeStart = Math.max(0, start - 12);
        Matcher before = BEFORE_QUANTITY.matcher(input.substring(beforeStart, start));
        if (before.find()) {
            return new ParsedQuantity(parseNumber(before.group(1)), normalizeUnit(before.group(2)), true);
        }

        int afterEnd = Math.min(input.length(), end + 12);
        Matcher after = AFTER_QUANTITY.matcher(input.substring(end, afterEnd));
        if (after.find()) {
            return new ParsedQuantity(parseNumber(after.group(1)), normalizeUnit(after.group(2)), true);
        }
        return new ParsedQuantity(0d, "", false);
    }

    private static ImageFoodItemDraft toDraft(FoodLibrary food, ParsedQuantity parsed) {
        String libraryUnit = normalizeUnit(food.getServingUnit());
        boolean libraryUnitSupported = isConfirmableUnit(libraryUnit);
        String unit = parsed.explicit ? parsed.unit : libraryUnit;
        if (unit == null || unit.isEmpty()) {
            unit = FoodUnitUtils.UNKNOWN;
        }
        boolean needsReview = !libraryUnitSupported;

        double amount;
        if (parsed.explicit && parsed.amount > 0d) {
            amount = parsed.amount;
        } else if (FoodUnitUtils.isMass(unit) || FoodUnitUtils.isVolume(unit)) {
            amount = Math.max(1d, food.getWeightPerUnit());
        } else {
            amount = 1d;
        }

        double estimatedWeight = 0d;
        if (FoodUnitUtils.isMass(unit)) {
            estimatedWeight = "kg".equalsIgnoreCase(unit) ? amount * 1000d : amount;
        } else if (FoodUnitUtils.isVolume(unit)) {
            // FoodLibrary is stored per 100 g. Keep the local match, but make
            // the density assumption visible so the user can correct it.
            estimatedWeight = amount;
            needsReview = true;
        } else if (libraryUnit.equals(unit) && food.getWeightPerUnit() > 0) {
            estimatedWeight = amount * food.getWeightPerUnit();
        } else if (!parsed.explicit && food.getWeightPerUnit() > 0) {
            estimatedWeight = amount * food.getWeightPerUnit();
        } else {
            needsReview = true;
        }

        ImageFoodItemDraft item = new ImageFoodItemDraft(
                food.getName(),
                Math.max(0, food.getCaloriesPer100g()),
                Math.max(0d, food.getProteinPer100g()),
                Math.max(0d, food.getCarbsPer100g()),
                Math.max(0d, food.getFatPer100g()),
                amount,
                unit,
                food.getCategory() == null ? "\u5176\u4ed6" : food.getCategory());
        item.setNutritionBasis(ImageFoodItemDraft.BASIS_PER_100G);
        item.setBaseNutrition(food.getCaloriesPer100g(), food.getProteinPer100g(),
                food.getCarbsPer100g(), food.getFatPer100g());
        item.setEstimatedWeightGrams(estimatedWeight);
        if (estimatedWeight > 0d && amount > 0d && !FoodUnitUtils.isMass(unit)
                && !FoodUnitUtils.isVolume(unit)) {
            item.setEstimatedWeightPerUnitGrams(estimatedWeight / amount);
        }
        item.setNeedsReview(needsReview || !isConfirmableUnit(unit));
        item.recalculateNutrition();
        return item;
    }

    private static String normalizeUnit(String raw) {
        if (raw == null) {
            return "";
        }
        String unit = raw.trim();
        if (unit.isEmpty()) {
            return "";
        }
        if ("克".equals(unit)) return "g";
        if ("千克".equals(unit)) return "kg";
        if ("毫升".equals(unit)) return "ml";
        if ("升".equals(unit)) return "L";
        if ("个".equals(unit) || "只".equals(unit) || "粒".equals(unit)) return "个";
        if ("碗".equals(unit) || "盘".equals(unit) || "杯".equals(unit)
                || "勺".equals(unit) || "份".equals(unit) || "包".equals(unit)
                || "袋".equals(unit) || "盒".equals(unit) || "片".equals(unit)
                || "块".equals(unit) || "串".equals(unit) || "根".equals(unit)
                || "张".equals(unit) || "把".equals(unit) || "节".equals(unit)) {
            return unit;
        }
        String normalized = FoodUnitUtils.normalize(unit);
        return FoodUnitUtils.isSupported(normalized) ? normalized : unit;
    }

    private static boolean isConfirmableUnit(String unit) {
        return "g".equals(unit) || "kg".equals(unit) || "ml".equals(unit) || "L".equals(unit)
                || COUNT_UNITS.contains(unit);
    }

    private static double parseNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0d;
        String value = raw.trim();
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            // Continue with the small Chinese-number parser below.
        }
        if ("半".equals(value)) return 0.5d;
        int total = 0;
        int section = 0;
        int number = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            int digit = chineseDigit(current);
            if (digit >= 0) {
                number = digit;
                continue;
            }
            if (current == '十' || current == '百') {
                int multiplier = current == '十' ? 10 : 100;
                section += (number == 0 ? 1 : number) * multiplier;
                number = 0;
            }
        }
        total += section + number;
        return total > 0 ? total : 0d;
    }

    private static int chineseDigit(char value) {
        switch (value) {
            case '零': return 0;
            case '一': return 1;
            case '二':
            case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    private static final class MatchedFood {
        final FoodLibrary food;
        final int start;
        final int end;
        final ParsedQuantity quantity;

        MatchedFood(FoodLibrary food, int start, int end, ParsedQuantity quantity) {
            this.food = food;
            this.start = start;
            this.end = end;
            this.quantity = quantity;
        }
    }

    private static final class ParsedQuantity {
        final double amount;
        final String unit;
        final boolean explicit;

        ParsedQuantity(double amount, String unit, boolean explicit) {
            this.amount = amount;
            this.unit = unit == null ? "" : unit;
            this.explicit = explicit;
        }
    }

    private static final class Plan {
        final List<MatchedFood> matches;
        final int score;

        Plan() {
            this.matches = new ArrayList<>();
            this.score = 0;
        }

        Plan(List<MatchedFood> matches, int score) {
            this.matches = matches;
            this.score = score;
        }

        Plan append(MatchedFood match) {
            List<MatchedFood> next = new ArrayList<>(matches);
            next.add(match);
            int length = match.end - match.start;
            return new Plan(next, score + length * 100 + length * length);
        }
    }
}
