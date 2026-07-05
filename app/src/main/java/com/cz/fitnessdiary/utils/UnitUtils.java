package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * 单位换算工具类
 * 数据库存储原始单位（kg / kcal），显示层按用户偏好转换
 */
public class UnitUtils {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_WEIGHT_UNIT = "weight_unit";
    private static final String KEY_ENERGY_UNIT = "energy_unit";
    private static final String DEFAULT_WEIGHT_UNIT = "kg";
    private static final String DEFAULT_ENERGY_UNIT = "kcal";

    // ── 换算常量 ──
    private static final float KG_TO_JIN = 2f;
    private static final float KCAL_TO_KJ = 4.184f;

    // ── 读取用户偏好 ──

    public static String getWeightUnit(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_WEIGHT_UNIT, DEFAULT_WEIGHT_UNIT);
    }

    public static String getEnergyUnit(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ENERGY_UNIT, DEFAULT_ENERGY_UNIT);
    }

    public static void setWeightUnit(Context context, String unit) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_WEIGHT_UNIT, unit).apply();
    }

    public static void setEnergyUnit(Context context, String unit) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_ENERGY_UNIT, unit).apply();
    }

    // ── 数值换算 ──

    public static float convertWeight(float kg, String targetUnit) {
        if ("jin".equals(targetUnit)) return kg * KG_TO_JIN;
        return kg;
    }

    public static float convertEnergy(float kcal, String targetUnit) {
        if ("kj".equals(targetUnit)) return kcal * KCAL_TO_KJ;
        return kcal;
    }

    // ── 格式化显示 ──

    public static String formatWeight(float kg, String targetUnit) {
        if ("jin".equals(targetUnit)) {
            return String.format(Locale.getDefault(), "%.1f", kg * KG_TO_JIN);
        }
        return String.format(Locale.getDefault(), "%.1f", kg);
    }

    public static String formatWeight(float kg, Context context) {
        return formatWeight(kg, getWeightUnit(context));
    }

    public static String formatEnergy(float kcal, String targetUnit) {
        if ("kj".equals(targetUnit)) {
            return String.format(Locale.getDefault(), "%.0f", kcal * KCAL_TO_KJ);
        }
        return String.format(Locale.getDefault(), "%.0f", kcal);
    }

    public static String formatEnergy(float kcal, Context context) {
        return formatEnergy(kcal, getEnergyUnit(context));
    }

    // ── 单位符号 ──

    public static String getWeightUnitSymbol(String unit) {
        if ("jin".equals(unit)) return "斤";
        return "千克";
    }

    public static String getWeightUnitSymbol(Context context) {
        return getWeightUnitSymbol(getWeightUnit(context));
    }

    public static String getEnergyUnitSymbol(String unit) {
        if ("kj".equals(unit)) return "千焦";
        return "千卡";
    }

    public static String getEnergyUnitSymbol(Context context) {
        return getEnergyUnitSymbol(getEnergyUnit(context));
    }

    public static String getWeightUnitDisplay(String unit) {
        if ("jin".equals(unit)) return "斤";
        return "千克(kg)";
    }

    public static String getEnergyUnitDisplay(String unit) {
        if ("kj".equals(unit)) return "千焦(kJ)";
        return "千卡(kcal)";
    }

    // ── 主题设置 ──

    private static final String KEY_THEME_MODE = "theme_mode";
    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_SYSTEM = 2;

    public static int getThemeMode(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public static void setThemeMode(Context context, int mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_THEME_MODE, mode).apply();
    }
}
