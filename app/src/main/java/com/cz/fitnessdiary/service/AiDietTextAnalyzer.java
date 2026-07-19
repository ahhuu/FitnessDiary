package com.cz.fitnessdiary.service;

import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;
import com.cz.fitnessdiary.utils.FoodUnitUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/** Text-to-food draft parser. It deliberately sends no chat history. */
public final class AiDietTextAnalyzer {
    private static final String SYSTEM_PROMPT =
            "你是 FitnessDiary 饮食记录解析器。只返回 JSON，不要 Markdown。"
                    + "JSON 格式为 {\"meal_name\":\"\",\"items\":[{"
                    + "\"name\":\"苹果\",\"amount\":1,\"unit\":\"个\",\"calories\":52,\"protein\":0.2,\"carbs\":14,\"fat\":0.2,"
                    + "\"estimated_weight_g\":150,\"basis\":\"TOTAL_PORTION\",\"category\":\"水果: 时令水果\",\"needs_review\":false}],"
                    + "\"reply\":\"简短友善评价\"}"
                    + "注意：固体重量必须用 g/kg，液体必须用 ml/L，个体食物用 个/片/只 等合理单位。如果给出的是每100g的营养，必须设置 basis 字段为 PER_100G 且给出预估总重量 estimated_weight_g。category 必须从标准分类中选择（如 主食: 基础米面, 菜肴: 精选荤菜, 蛋白: 蛋奶豆制品, 水果: 时令水果, 蔬菜: 新鲜时蔬, 零食: 包装小吃, 饮料: 咖啡奶茶, 调料/油脂 等）。";

    public interface Callback {
        void onSuccess(ImageMealDraft draft);

        void onError(String message);
    }

    private AiDietTextAnalyzer() {
    }

    public static void analyze(String text, Callback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onError("请先描述吃了什么");
            return;
        }
        String prompt = "请解析以下饮食描述，只输出紧凑 JSON：\n" + text.trim();
        DeepSeekService.sendMessageWithPolicy(prompt, SYSTEM_PROMPT, false, null,
                true, new AICallback() {
                    @Override
                    public void onSuccess(String response, String reasoning) {
                        try {
                            ImageMealDraft draft = parse(response);
                            if (draft.getItems() == null || draft.getItems().isEmpty()) {
                                callback.onError("没有解析到明确的食物，请补充数量或单位");
                                return;
                            }
                            callback.onSuccess(draft);
                        } catch (Exception error) {
                            callback.onError("饮食结果格式异常，请重试或改用手动添加");
                        }
                    }

                    @Override
                    public void onPartialUpdate(String content, String reasoning) {
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error == null || error.trim().isEmpty()
                                ? "饮食识别失败，请稍后重试" : error);
                    }
                });
    }

    static ImageMealDraft parse(String response) throws Exception {
        String normalized = response == null ? "" : response.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        JsonObject root = JsonParser.parseString(normalized).getAsJsonObject();
        JsonObject action = object(root, "action");
        if (action != null) {
            root = action;
        }
        JsonArray items = array(root, "items");
        if (items == null) {
            items = array(root, "food_items");
        }
        if (items == null) {
            items = array(root, "foods");
        }

        ImageMealDraft draft = new ImageMealDraft();
        draft.setSourceType(ImageMealDraft.SOURCE_TEXT);
        draft.setMealName(string(root, "meal_name", string(root, "mealName", "文字饮食记录")));
        draft.setSuggestion(string(root, "reply", string(root, "suggestion", "")));
        if (items == null) {
            return draft;
        }

        for (int i = 0; i < items.size(); i++) {
            JsonElement rawItem = items.get(i);
            JsonObject item = rawItem != null && rawItem.isJsonObject() ? rawItem.getAsJsonObject() : null;
            if (item == null) {
                continue;
            }
            String name = string(item, "name", string(item, "food_name", "")).trim();
            if (name.isEmpty()) {
                continue;
            }
            String rawUnit = string(item, "unit", "").trim();
            String unit = FoodUnitUtils.normalize(rawUnit);
            double amount = positive(number(item, "amount", number(item, "servings", 1d)), 1d);
            String basis = string(item, "basis", string(item, "nutrition_basis",
                    ImageFoodItemDraft.BASIS_TOTAL_PORTION)).toUpperCase(Locale.ROOT);
            if (!isValidBasis(basis)) {
                basis = ImageFoodItemDraft.BASIS_TOTAL_PORTION;
            }
            double estimatedWeight = positive(number(item, "estimated_weight_g",
                    number(item, "estimatedWeightGrams", 0d)), 0d);
            double calories = positive(number(item, "calories", 0d), 0d);
            double protein = positive(number(item, "protein", 0d), 0d);
            double carbs = positive(number(item, "carbs", 0d), 0d);
            double fat = positive(number(item, "fat", 0d), 0d);
            ImageFoodItemDraft draftItem = new ImageFoodItemDraft(name,
                    (int) Math.round(calories), protein, carbs, fat,
                    amount, rawUnit.isEmpty() ? unit : rawUnit,
                    string(item, "category", "其他"));
            draftItem.setNutritionBasis(basis);
            draftItem.setBaseNutrition(calories, protein, carbs, fat);
            draftItem.setEstimatedWeightGrams(estimatedWeight);
            if (estimatedWeight > 0d && amount > 0d && !FoodUnitUtils.isMass(unit)
                    && !FoodUnitUtils.isVolume(unit)) {
                draftItem.setEstimatedWeightPerUnitGrams(estimatedWeight / amount);
            }
            boolean needsReview = bool(item, "needs_review", bool(item, "needsReview", false));
            needsReview = needsReview || rawUnit.isEmpty() || !FoodUnitUtils.isSupported(rawUnit);
            draftItem.setNeedsReview(needsReview);
            draftItem.recalculateNutrition();
            draft.getItems().add(draftItem);
        }
        draft.recomputeTotals();
        return draft;
    }

    private static boolean isValidBasis(String basis) {
        return ImageFoodItemDraft.BASIS_TOTAL_PORTION.equals(basis)
                || ImageFoodItemDraft.BASIS_PER_100G.equals(basis)
                || ImageFoodItemDraft.BASIS_PER_100ML.equals(basis)
                || ImageFoodItemDraft.BASIS_PER_UNIT.equals(basis);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String string(JsonObject parent, String key, String fallback) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && !value.isJsonNull() ? value.getAsString() : fallback;
    }

    private static double number(JsonObject parent, String key, double fallback) {
        JsonElement value = parent == null ? null : parent.get(key);
        try {
            return value != null && !value.isJsonNull() ? value.getAsDouble() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject parent, String key, boolean fallback) {
        JsonElement value = parent == null ? null : parent.get(key);
        try {
            return value != null && !value.isJsonNull() ? value.getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double positive(double value, double fallback) {
        return Double.isNaN(value) || Double.isInfinite(value) || value < 0 ? fallback : value;
    }
}
