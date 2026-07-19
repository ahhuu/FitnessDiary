package com.cz.fitnessdiary.service;

import android.graphics.Bitmap;

import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;
import com.cz.fitnessdiary.utils.FoodUnitUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;

/** MiMo food-image parser. It accepts both compact JSON and legacy &lt;action&gt; JSON. */
public class FoodImageAnalyzer {
    private String lastImageHash;
    private ImageMealDraft lastDraft;
    public interface AnalyzeCallback {
        void onSuccess(ImageMealDraft draft, String rawResponse, String reasoning);
        void onError(String error);
    }

    public void analyze(Bitmap bitmap, AnalyzeCallback callback) {
        if (bitmap == null) {
            callback.onError("图片为空，请重试");
            return;
        }
        String imageHash = hashBitmap(bitmap);
        if (imageHash.equals(lastImageHash) && lastDraft != null) {
            callback.onSuccess(lastDraft, "{cached:true}", null);
            return;
        }
        MiMoService.sendMessage(
                "识别图片中的全部食物，只输出紧凑 JSON。",
                buildSystemPrompt(), bitmap, null,
                new AICallback() {
                    @Override public void onSuccess(String response, String reasoning) {
                        String text = response == null ? "" : response;
                        ImageMealDraft parsed = parseDraft(text);
                        if (parsed == null && reasoning != null) {
                            parsed = parseDraft(reasoning);
                        }
                        ImageMealDraft result = parsed == null ? fallbackDraft(text, reasoning) : parsed;
                        if (result.getItems() == null || result.getItems().isEmpty()) {
                            callback.onError("AI 返回的识别结果不完整，请重试；这不代表图片模糊");
                            return;
                        }
                        lastImageHash = imageHash;
                        lastDraft = result;
                        callback.onSuccess(result, text, reasoning);
                    }
                    @Override public void onPartialUpdate(String content, String reasoning) { }
                    @Override public void onError(String error) { callback.onError(error); }
        });
    }

    /** Explicit re-identification is the only path that invalidates the recent result cache. */
    public void clearCache() {
        lastImageHash = null;
        lastDraft = null;
    }

    /** Returns true when this exact bitmap already has a local recognition result. */
    public boolean hasCachedResult(Bitmap bitmap) {
        return bitmap != null && lastDraft != null
                && hashBitmap(bitmap).equals(lastImageHash);
    }

    private String hashBitmap(Bitmap bitmap) {
        try {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(output.toByteArray());
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) builder.append(String.format(java.util.Locale.US, "%02x", value));
            return builder.toString();
        } catch (Exception ignored) {
            return bitmap.getWidth() + "x" + bitmap.getHeight();
        }
    }

    private String buildSystemPrompt() {
        return "你是 FitnessDiary 食物识别器。只输出 JSON，不要 Markdown、解释或 reply。"
                + "结构:{\"type\":\"FOOD\",\"meal_name\":\"整餐\",\"items\":["
                + "{\"name\":\"鸡蛋\",\"amount\":2,\"unit\":\"个\",\"basis\":\"TOTAL_PORTION\","
                + "\"calories\":140,\"protein\":12,\"carbs\":2,\"fat\":10,\"estimated_weight_g\":100,\"category\":\"蛋白: 蛋奶豆制品\",\"needs_review\":false}]}."
                + "固体质量用g/kg，液体用ml/L，个体食物用个/只/片，米饭面条用碗/盘/份，包装食品用包/袋/盒。"
                + "营养数值必须对应 amount 的实际总量；若只能给每100g/100ml/每单位，填写 basis，无法判断数量时用1份并 needs_review=true。"
                + "category 必须从标准分类中选择（如 主食: 基础米面, 菜肴: 精选荤菜, 蛋白: 蛋奶豆制品, 水果: 时令水果, 蔬菜: 新鲜时蔬, 零食: 包装小吃, 饮料: 咖啡奶茶, 调料/油脂 (Condiments) 等）。";
    }

    private ImageMealDraft parseDraft(String response) {
        try {
            JsonObject root = FoodActionParser.parseAction(response);
            if (root == null || !"FOOD".equalsIgnoreCase(stringValue(root, "type", ""))) return null;
            ImageMealDraft draft = new ImageMealDraft();
            draft.setSourceType(ImageMealDraft.SOURCE_IMAGE);
            draft.setMealName(firstNonEmpty(root, "识别餐食", "meal_name", "mealName", "title"));
            JsonArray items = arrayValue(root, "items");
            if (items == null) items = arrayValue(root, "food_items");
            if (items == null) items = arrayValue(root, "foods");
            List<ImageFoodItemDraft> list = new ArrayList<>();
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JsonObject raw = items.get(i).isJsonObject() ? items.get(i).getAsJsonObject() : null;
                    if (raw == null) continue;
                    String name = firstNonEmpty(raw, "", "name", "food_name", "foodName", "title").trim();
                    if (name.isEmpty()) continue;
                    String rawUnit = firstNonEmpty(raw, "", "unit", "serving_unit", "servingUnit").trim();
                    String unit = inferUnit(name, rawUnit);
                    double amount = firstNumber(raw, 1d, "amount", "servings", "quantity", "count");
                    if (amount <= 0) amount = 1d;
                    String basis = firstNonEmpty(raw, ImageFoodItemDraft.BASIS_TOTAL_PORTION,
                            "basis", "nutrition_basis");
                    double weight = firstNumber(raw, 0d, "estimated_weight_g", "estimatedWeightGrams");
                    boolean review = firstBoolean(raw, false, "needs_review", "needsReview");
                    double calories = firstNumber(raw, 0d, "calories", "kcal");
                    double protein = firstNumber(raw, 0d, "protein", "protein_g");
                    double carbs = firstNumber(raw, 0d, "carbs", "carbohydrates", "carbs_g");
                    double fat = firstNumber(raw, 0d, "fat", "fat_g");
                    ImageFoodItemDraft food = new ImageFoodItemDraft(name,
                            (int) Math.round(calories), protein, carbs, fat, amount,
                            rawUnit.isEmpty() ? unit : rawUnit, stringValue(raw, "category", "其他"));
                    food.setNutritionBasis(basis);
                    food.setBaseNutrition(calories, protein, carbs, fat);
                    food.setEstimatedWeightGrams(weight);
                    if (weight > 0d && amount > 0d && !FoodUnitUtils.isMass(unit)
                            && !FoodUnitUtils.isVolume(unit)) {
                        food.setEstimatedWeightPerUnitGrams(weight / amount);
                    }
                    food.setNeedsReview(review || rawUnit.isEmpty() || !FoodUnitUtils.isSupported(rawUnit));
                    food.recalculateNutrition();
                    list.add(food);
                }
            }
            if (list.isEmpty()) return null;
            draft.setItems(list);
            draft.recomputeTotals();
            String suggestion = firstNonEmpty(root, "", "suggestion", "reply");
            draft.setSuggestion(suggestion.isEmpty() ? buildSuggestion(draft) : suggestion);
            return draft;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonEmpty(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            String value = stringValue(object, key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return fallback == null ? "" : fallback;
    }

    private double firstNumber(JsonObject object, double fallback, String... keys) {
        for (String key : keys) {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) continue;
            try {
                return object.get(key).getAsDouble();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private boolean firstBoolean(JsonObject object, boolean fallback, String... keys) {
        for (String key : keys) {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) continue;
            try {
                return object.get(key).getAsBoolean();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private String stringValue(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback == null ? "" : fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback == null ? "" : fallback;
        }
    }

    private JsonArray arrayValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private String inferUnit(String name, String rawUnit) {
        if (!isBlank(rawUnit) && FoodUnitUtils.isSupported(rawUnit)) {
            return FoodUnitUtils.normalize(rawUnit);
        }
        if (name.contains("牛奶") || name.contains("饮料") || name.contains("汤") || name.contains("咖啡")) return "ml";
        if (name.contains("鸡蛋") || name.contains("香蕉") || name.contains("苹果")) return "个";
        if (name.contains("面包") || name.contains("吐司")) return "片";
        if (name.contains("米饭") || name.contains("面条") || name.contains("盖饭")) return "碗";
        return "份";
    }

    private ImageMealDraft fallbackDraft(String response, String reasoning) {
        ImageMealDraft draft = new ImageMealDraft();
        draft.setSourceType(ImageMealDraft.SOURCE_IMAGE);
        List<ImageFoodItemDraft> list = new ArrayList<>();
        FoodLibrary parsed = FoodParser.parseFirstFood((response == null ? "" : response) + "\n"
                + (reasoning == null ? "" : reasoning));
        if (parsed != null) {
            ImageFoodItemDraft item = new ImageFoodItemDraft(parsed.getName(), parsed.getCaloriesPer100g(),
                    parsed.getProteinPer100g(), parsed.getCarbsPer100g(), parsed.getFatPer100g(), 1d,
                    parsed.getServingUnit(), parsed.getCategory());
            item.setNeedsReview(true);
            list.add(item);
        }
        if (list.isEmpty()) {
            draft.setSuggestion("识别结果不完整，请确认食物、数量和单位后再保存。");
        }
        draft.setItems(list);
        draft.recomputeTotals();
        if (isBlank(draft.getSuggestion())) draft.setSuggestion(buildSuggestion(draft));
        return draft;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String buildSuggestion(ImageMealDraft draft) {
        int calories = draft.getTotalCalories();
        if (calories <= 0) return "请补充食物明细后再保存。";
        if (calories < 350) return "本餐热量偏低，可补充优质蛋白或主食。";
        if (calories > 900) return "本餐热量偏高，下餐可清淡并增加蔬菜。";
        return "本餐热量适中，请确认数量和单位。";
    }
}
