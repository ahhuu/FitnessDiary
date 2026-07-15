package com.cz.fitnessdiary.model;

import com.cz.fitnessdiary.utils.FoodUnitUtils;

import java.io.Serializable;
import java.util.Locale;

/** One food item awaiting user confirmation. Nutrition values are totals for amount/unit. */
public class ImageFoodItemDraft implements Serializable {
    public static final String SOURCE_UNKNOWN = "UNKNOWN";
    public static final String SOURCE_LIBRARY = "LIBRARY";
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_MANUAL = "MANUAL";

    public static final String BASIS_TOTAL_PORTION = "TOTAL_PORTION";
    public static final String BASIS_PER_100G = "PER_100G";
    public static final String BASIS_PER_100ML = "PER_100ML";
    public static final String BASIS_PER_UNIT = "PER_UNIT";

    private String name;
    private int calories;
    private double protein;
    private double carbs;
    private double fat;
    private double amount;
    private String unit;
    private String category;
    private String nutritionBasis;
    private double estimatedWeightGrams;
    private double estimatedWeightPerUnitGrams;
    private double estimatedVolumePerUnitMl;
    private double baseCalories;
    private double baseProtein;
    private double baseCarbs;
    private double baseFat;
    private double baseAmount;
    private String baseUnit;
    private String rawUnit;
    private boolean needsReview;
    private boolean sourceNeedsReview;
    private boolean unitConversionConfirmed;
    private String sourceType;

    public ImageFoodItemDraft() {
        this("", 0, 0d, 0d, 0d, 1d, "份", "其他");
    }

    public ImageFoodItemDraft(String name, int calories, double protein, double carbs, double fat,
            String unit, String category) {
        this(name, calories, protein, carbs, fat, 1d, unit, category);
    }

    public ImageFoodItemDraft(String name, int calories, double protein, double carbs, double fat,
            double amount, String unit, String category) {
        this.name = name == null ? "" : name;
        this.calories = Math.max(0, calories);
        this.protein = Math.max(0d, protein);
        this.carbs = Math.max(0d, carbs);
        this.fat = Math.max(0d, fat);
        this.amount = amount > 0 ? amount : 1d;
        this.rawUnit = unit == null ? "" : unit.trim();
        this.unit = FoodUnitUtils.normalize(unit);
        this.category = category == null ? "其他" : category;
        this.nutritionBasis = BASIS_TOTAL_PORTION;
        this.estimatedWeightGrams = 0d;
        this.estimatedWeightPerUnitGrams = 0d;
        this.estimatedVolumePerUnitMl = 0d;
        this.baseCalories = this.calories;
        this.baseProtein = this.protein;
        this.baseCarbs = this.carbs;
        this.baseFat = this.fat;
        this.baseAmount = this.amount;
        this.baseUnit = this.unit;
        this.needsReview = false;
        this.sourceNeedsReview = false;
        this.unitConversionConfirmed = false;
        this.sourceType = SOURCE_UNKNOWN;
    }

    /** Compatibility constructor used by older chat action code. */
    public ImageFoodItemDraft(String name, int calories, double protein, double carbs,
            String unit, String category) {
        this(name, calories, protein, carbs, 0d, 1d, unit, category);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "" : name; }
    public int getCalories() { return calories; }
    public void setCalories(int calories) { this.calories = Math.max(0, calories); }
    public double getProtein() { return protein; }
    public void setProtein(double protein) { this.protein = Math.max(0d, protein); }
    public double getCarbs() { return carbs; }
    public void setCarbs(double carbs) { this.carbs = Math.max(0d, carbs); }
    public double getFat() { return fat; }
    public void setFat(double fat) { this.fat = Math.max(0d, fat); }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount > 0 ? amount : 1d; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) {
        this.rawUnit = unit == null ? "" : unit.trim();
        this.unit = FoodUnitUtils.normalize(unit);
        this.unitConversionConfirmed = false;
    }
    public String getRawUnit() { return rawUnit; }
    public String getDisplayUnit() {
        return FoodUnitUtils.isSupported(rawUnit) ? unit : rawUnit;
    }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category == null ? "其他" : category; }
    public String getNutritionBasis() { return nutritionBasis; }
    public void setNutritionBasis(String nutritionBasis) {
        String value = nutritionBasis == null ? BASIS_TOTAL_PORTION
                : nutritionBasis.trim().toUpperCase(Locale.ROOT);
        if (!BASIS_TOTAL_PORTION.equals(value) && !BASIS_PER_100G.equals(value)
                && !BASIS_PER_100ML.equals(value) && !BASIS_PER_UNIT.equals(value)) {
            value = BASIS_TOTAL_PORTION;
        }
        this.nutritionBasis = value;
    }
    public double getEstimatedWeightGrams() { return estimatedWeightGrams; }
    public void setEstimatedWeightGrams(double value) { this.estimatedWeightGrams = Math.max(0d, value); }
    public double getEstimatedWeightPerUnitGrams() { return estimatedWeightPerUnitGrams; }
    public void setEstimatedWeightPerUnitGrams(double value) {
        this.estimatedWeightPerUnitGrams = Math.max(0d, value);
    }
    public double getEstimatedVolumePerUnitMl() { return estimatedVolumePerUnitMl; }
    public void setEstimatedVolumePerUnitMl(double value) {
        this.estimatedVolumePerUnitMl = Math.max(0d, value);
    }
    public boolean isNeedsReview() { return needsReview; }
    public void setNeedsReview(boolean needsReview) {
        this.sourceNeedsReview = needsReview;
        this.needsReview = needsReview;
    }
    public boolean isUnitConversionConfirmed() { return unitConversionConfirmed; }
    public void setUnitConversionConfirmed(boolean confirmed) {
        this.unitConversionConfirmed = confirmed;
    }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) {
        if (SOURCE_LIBRARY.equals(sourceType) || SOURCE_AI.equals(sourceType)
                || SOURCE_MANUAL.equals(sourceType)) {
            this.sourceType = sourceType;
        } else {
            this.sourceType = SOURCE_UNKNOWN;
        }
    }

    public boolean hasNutritionData() {
        return calories > 0 || protein > 0d || carbs > 0d || fat > 0d;
    }

    /**
     * Converts the current serving-based draft into grams for the confirmation UI.
     * The current nutrition total is rebased to per-100g before recalculation so
     * the displayed grams and the values saved to Room stay consistent.
     */
    public boolean normalizeToGramsForEditing() {
        double grams = estimatedWeightGrams;
        if (grams <= 0d && FoodUnitUtils.isMass(rawUnit)) {
            grams = "kg".equalsIgnoreCase(unit) ? amount * 1000d : amount;
        }
        if (grams <= 0d && estimatedWeightPerUnitGrams > 0d) {
            grams = amount * estimatedWeightPerUnitGrams;
        }
        boolean reliable = grams > 0d;
        if (!reliable) {
            // Keep the editor usable without inventing a nutrition conversion.
            // The explicit review flag prevents silent saving until the user edits it.
            grams = Math.max(1d, amount);
            needsReview = true;
            sourceNeedsReview = true;
        }

        double ratio = grams > 0d ? 100d / grams : 1d;
        setBaseNutrition(calories * ratio, protein * ratio, carbs * ratio, fat * ratio);
        setNutritionBasis(BASIS_PER_100G);
        setAmount(grams);
        setUnit("g");
        setEstimatedWeightGrams(grams);
        recalculateNutrition();
        if (!reliable) {
            needsReview = true;
        }
        return reliable;
    }

    /** Stores the provider's nutrition values before they are scaled to the current amount. */
    public void setBaseNutrition(double calories, double protein, double carbs, double fat) {
        baseCalories = Math.max(0d, calories);
        baseProtein = Math.max(0d, protein);
        baseCarbs = Math.max(0d, carbs);
        baseFat = Math.max(0d, fat);
    }

    /** Captures manually edited totals so later amount changes remain deterministic. */
    public void captureCurrentNutritionAsBase() {
        setNutritionBasis(BASIS_TOTAL_PORTION);
        setBaseNutrition(calories, protein, carbs, fat);
        baseAmount = amount;
        baseUnit = unit;
        sourceNeedsReview = false;
        needsReview = false;
    }

    /**
     * Recalculates current totals from the original nutrition basis and amount/unit.
     * Returns false when the unit conversion cannot be proven safely.
     */
    public boolean recalculateNutrition() {
        double multiplier;
        boolean reliable = FoodUnitUtils.isSupported(rawUnit) && amount > 0d;
        if (BASIS_PER_100G.equals(nutritionBasis)) {
            double weight = resolveWeightGrams();
            reliable = reliable && weight > 0d;
            estimatedWeightGrams = Math.max(0d, weight);
            multiplier = weight / 100d;
        } else if (BASIS_PER_100ML.equals(nutritionBasis)) {
            double volume = resolveVolumeMilliliters();
            reliable = reliable && volume > 0d;
            multiplier = volume / 100d;
        } else if (BASIS_PER_UNIT.equals(nutritionBasis)) {
            reliable = reliable && isCompatibleUnit(baseUnit, unit);
            multiplier = amount;
        } else {
            reliable = reliable && baseAmount > 0d && isCompatibleUnit(baseUnit, unit);
            double currentComparableAmount = comparableAmount(unit, amount);
            double baseComparableAmount = comparableAmount(baseUnit, baseAmount);
            reliable = reliable && baseComparableAmount > 0d;
            multiplier = reliable ? currentComparableAmount / baseComparableAmount : 1d;
        }

        calories = (int) Math.round(baseCalories * multiplier);
        protein = baseProtein * multiplier;
        carbs = baseCarbs * multiplier;
        fat = baseFat * multiplier;
        needsReview = sourceNeedsReview || !reliable;
        return reliable;
    }

    private double resolveWeightGrams() {
        if (FoodUnitUtils.isMass(unit)) {
            return "kg".equalsIgnoreCase(unit) ? amount * 1000d : amount;
        }
        if (estimatedWeightPerUnitGrams > 0d && FoodUnitUtils.isSupported(unit)
                && !FoodUnitUtils.isVolume(unit)) {
            return amount * estimatedWeightPerUnitGrams;
        }
        return 0d;
    }

    private double resolveVolumeMilliliters() {
        if (FoodUnitUtils.isVolume(unit)) {
            return "L".equalsIgnoreCase(unit) ? amount * 1000d : amount;
        }
        return estimatedVolumePerUnitMl > 0d ? amount * estimatedVolumePerUnitMl : 0d;
    }

    private static boolean isCompatibleUnit(String first, String second) {
        if (!FoodUnitUtils.isSupported(first) || !FoodUnitUtils.isSupported(second)) return false;
        if (FoodUnitUtils.isMass(first) && FoodUnitUtils.isMass(second)) return true;
        if (FoodUnitUtils.isVolume(first) && FoodUnitUtils.isVolume(second)) return true;
        return FoodUnitUtils.normalize(first).equals(FoodUnitUtils.normalize(second));
    }

    private static double comparableAmount(String unit, double value) {
        if ("kg".equalsIgnoreCase(unit)) return value * 1000d;
        if ("L".equalsIgnoreCase(unit)) return value * 1000d;
        return value;
    }
}
