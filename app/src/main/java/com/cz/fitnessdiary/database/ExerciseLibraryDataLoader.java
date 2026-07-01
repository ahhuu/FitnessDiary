package com.cz.fitnessdiary.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cz.fitnessdiary.database.dao.ExerciseLibraryDao;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class ExerciseLibraryDataLoader {

    private static final String TAG = "ExLibLoader";
    private static final String PREF_NAME = "fitness_diary_prefs";
    private static final String KEY_EXERCISE_LIBRARY_VERSION = "exercise_library_version";
    private static final String JSON_FILE = "exercise_library.json";

    private ExerciseLibraryDataLoader() {}

    public static void loadIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int storedVersion = prefs.getInt(KEY_EXERCISE_LIBRARY_VERSION, 0);

        int jsonVersion;
        try {
            jsonVersion = readJsonVersion(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read exercise library JSON version", e);
            return;
        }

        if (jsonVersion <= storedVersion) return;

        List<ExerciseLibrary> exercises;
        try {
            exercises = parseExercisesFromJson(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse exercise library JSON", e);
            return;
        }

        if (exercises.isEmpty()) return;

        AppDatabase db = AppDatabase.getInstance(context);
        ExerciseLibraryDao dao = db.exerciseLibraryDao();
        // 版本更新时先清空旧数据再导入，确保删除的动作不会残留
        dao.deleteAll();
        dao.insertAll(exercises);

        prefs.edit().putInt(KEY_EXERCISE_LIBRARY_VERSION, jsonVersion).apply();
        Log.i(TAG, "Exercise library updated: " + exercises.size() + " exercises, version " + jsonVersion);
    }

    private static int readJsonVersion(Context context) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(JSON_FILE), "UTF-8")) {
            ExerciseJsonSchema schema = new Gson().fromJson(reader, ExerciseJsonSchema.class);
            return schema != null ? schema.version : 0;
        }
    }

    private static List<ExerciseLibrary> parseExercisesFromJson(Context context) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(JSON_FILE), "UTF-8")) {
            ExerciseJsonSchema schema = new Gson().fromJson(reader, ExerciseJsonSchema.class);
            if (schema == null || schema.exercises == null || schema.exercises.isEmpty()) {
                return new ArrayList<>();
            }

            List<ExerciseLibrary> result = new ArrayList<>(schema.exercises.size());
            for (ExerciseJsonItem item : schema.exercises) {
                result.add(new ExerciseLibrary(
                        item.name,
                        item.bodyPart != null ? item.bodyPart : "其他",
                        item.subCategory != null ? item.subCategory : "通用",
                        item.description != null ? item.description : "",
                        item.difficulty > 0 ? item.difficulty : 1,
                        item.equipment != null ? item.equipment : "无",
                        item.category != null ? item.category : (item.bodyPart + ": " + item.subCategory)));
            }
            return result;
        }
    }

    private static class ExerciseJsonSchema {
        int version;
        List<ExerciseJsonItem> exercises;
    }

    private static class ExerciseJsonItem {
        String name;
        String bodyPart;
        String subCategory;
        String description;
        int difficulty;
        String equipment;
        @SerializedName("category")
        String category;
    }
}
