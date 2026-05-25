package com.cz.fitnessdiary.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cz.fitnessdiary.database.dao.FoodLibraryDao;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 assets/food_library.json 加载食物库数据。
 * 通过 SharedPreferences 中的版本号判断是否需要更新，避免每次启动都解析 JSON。
 */
public final class FoodLibraryDataLoader {

    private static final String TAG = "FoodLibLoader";
    private static final String PREF_NAME = "fitness_diary_prefs";
    private static final String KEY_FOOD_LIBRARY_VERSION = "food_library_version";
    private static final String JSON_FILE = "food_library.json";

    private FoodLibraryDataLoader() {
    }

    /**
     * 如果 JSON 版本比本地存储的版本新，则重新加载食物库。
     * 必须在后台线程调用。
     */
    public static void loadIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int storedVersion = prefs.getInt(KEY_FOOD_LIBRARY_VERSION, 0);

        int jsonVersion;
        try {
            jsonVersion = readJsonVersion(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read food library JSON version", e);
            return;
        }

        if (jsonVersion <= storedVersion) {
            return;
        }

        List<FoodLibrary> foods;
        try {
            foods = parseFoodsFromJson(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse food library JSON", e);
            return;
        }

        if (foods.isEmpty()) {
            return;
        }

        AppDatabase db = AppDatabase.getInstance(context);
        FoodLibraryDao dao = db.foodLibraryDao();
        dao.insertAll(foods);

        prefs.edit().putInt(KEY_FOOD_LIBRARY_VERSION, jsonVersion).apply();
        Log.i(TAG, "Food library updated: " + foods.size() + " foods, version " + jsonVersion);
    }

    private static int readJsonVersion(Context context) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(JSON_FILE), "UTF-8")) {
            FoodJsonSchema schema = new Gson().fromJson(reader, FoodJsonSchema.class);
            return schema != null ? schema.version : 0;
        }
    }

    private static List<FoodLibrary> parseFoodsFromJson(Context context) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(JSON_FILE), "UTF-8")) {
            FoodJsonSchema schema = new Gson().fromJson(reader, FoodJsonSchema.class);
            if (schema == null || schema.foods == null || schema.foods.isEmpty()) {
                return new ArrayList<>();
            }

            List<FoodLibrary> result = new ArrayList<>(schema.foods.size());
            for (FoodJsonItem item : schema.foods) {
                result.add(new FoodLibrary(
                        item.name,
                        item.caloriesPer100g,
                        item.proteinPer100g,
                        item.carbsPer100g,
                        item.fatPer100g,
                        item.servingUnit != null ? item.servingUnit : "克",
                        item.weightPerUnit > 0 ? item.weightPerUnit : 100,
                        item.category != null ? item.category : "其他"));
            }
            return result;
        }
    }

    // JSON schema classes for Gson deserialization

    private static class FoodJsonSchema {
        int version;
        List<FoodJsonItem> foods;
    }

    private static class FoodJsonItem {
        String name;
        int caloriesPer100g;
        double proteinPer100g;
        double carbsPer100g;
        @SerializedName("fatPer100g")
        double fatPer100g;
        String servingUnit;
        int weightPerUnit;
        String category;
    }
}
