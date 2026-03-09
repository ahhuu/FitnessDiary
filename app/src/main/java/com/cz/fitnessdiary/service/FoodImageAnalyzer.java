package com.cz.fitnessdiary.service;

import android.graphics.Bitmap;
import android.text.TextUtils;

import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FoodImageAnalyzer {

    public interface AnalyzeCallback {
        void onSuccess(ImageMealDraft draft, String rawResponse, String reasoning);
        void onError(String error);
    }

    public void analyze(Bitmap bitmap, AnalyzeCallback callback) {
        if (bitmap == null) {
            callback.onError("图片为空，请重试");
            return;
        }

        String userPrompt = "请识别图片中的所有食物，并给出整餐热量与营养估算。";
        String systemPrompt = buildSystemPrompt();

        QwenService.sendMessage(userPrompt, systemPrompt, bitmap, null, new AICallback() {
            @Override
            public void onSuccess(String response, String reasoning) {
                String text = response == null ? "" : response;
                try {
                    ImageMealDraft draft = parseDraft(text);
                    if (draft == null) {
                        draft = fallbackDraft(text, reasoning);
                    }
                    callback.onSuccess(draft, text, reasoning);
                } catch (Exception e) {
                    callback.onSuccess(fallbackDraft(text, reasoning), text, reasoning);
                }
            }

            @Override
            public void onPartialUpdate(String content, String reasoning) {
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private String buildSystemPrompt() {
        return "你是 FitnessDiary 的食物识别营养助手。"
                + "请识别图片中的食物，并在正文后附加 <action>{...}</action>。"
                + "action 必须是 JSON，结构："
                + "{\"type\":\"FOOD\",\"meal_name\":\"整餐名\",\"items\":[{\"name\":\"食物\",\"calories\":120,\"protein\":8,\"carbs\":15,\"unit\":\"份\",\"category\":\"其他\"}]}。"
                + "请尽量完整识别多个食物。";
    }

    private ImageMealDraft parseDraft(String response) throws Exception {
        String actionJson = extractActionJson(response);
        if (TextUtils.isEmpty(actionJson)) {
            return null;
        }

        JSONObject root = new JSONObject(actionJson);
        if (!"FOOD".equalsIgnoreCase(root.optString("type"))) {
            return null;
        }

        ImageMealDraft draft = new ImageMealDraft();
        draft.setMealName(root.optString("meal_name", "识别餐"));

        JSONArray items = root.optJSONArray("items");
        List<ImageFoodItemDraft> list = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                ImageFoodItemDraft food = new ImageFoodItemDraft(
                        item.optString("name", "未命名食物"),
                        Math.max(0, item.optInt("calories", 0)),
                        Math.max(0d, item.optDouble("protein", 0d)),
                        Math.max(0d, item.optDouble("carbs", 0d)),
                        item.optString("unit", "份"),
                        item.optString("category", "其他"));
                list.add(food);
            }
        }

        if (list.isEmpty()) {
            return null;
        }

        draft.setItems(list);
        draft.recomputeTotals();
        draft.setSuggestion(buildSuggestion(draft));
        return draft;
    }

    private String extractActionJson(String response) {
        if (response == null) return null;
        Matcher matcher = Pattern.compile("<action>(?s:(.*?))</action>").matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private ImageMealDraft fallbackDraft(String response, String reasoning) {
        ImageMealDraft draft = new ImageMealDraft();
        draft.setMealName("图片识别餐");
        List<ImageFoodItemDraft> list = new ArrayList<>();

        com.cz.fitnessdiary.database.entity.FoodLibrary parsed = FoodParser.parseFirstFood(
                (response == null ? "" : response) + "\n" + (reasoning == null ? "" : reasoning));
        if (parsed != null) {
            list.add(new ImageFoodItemDraft(parsed.getName(), parsed.getCaloriesPer100g(),
                    parsed.getProteinPer100g(), parsed.getCarbsPer100g(), "份", parsed.getCategory()));
        }

        if (list.isEmpty()) {
            list.add(new ImageFoodItemDraft("请手动补充食物", 0, 0, 0, "份", "其他"));
            draft.setSuggestion("未能可靠识别，请手动编辑后保存。");
        }

        draft.setItems(list);
        draft.recomputeTotals();
        if (TextUtils.isEmpty(draft.getSuggestion())) {
            draft.setSuggestion(buildSuggestion(draft));
        }
        return draft;
    }

    private String buildSuggestion(ImageMealDraft draft) {
        int c = draft.getTotalCalories();
        if (c <= 0) {
            return "请补充食物明细后再保存。";
        }
        if (c < 350) {
            return "本餐热量偏低，可补充优质蛋白或主食。";
        }
        if (c > 900) {
            return "本餐热量偏高，下一餐建议清淡并增加蔬菜。";
        }
        return "本餐热量适中，保持当前饮食结构。";
    }
}

