package com.cz.fitnessdiary.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Canonical food units used by AI drafts and FoodRecord. */
public final class FoodUnitUtils {
    public static final String UNKNOWN = "UNKNOWN";

    private static final Set<String> UNITS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "g", "kg", "ml", "L", "个", "只", "片", "块", "串", "碗", "盘", "杯", "勺", "份",
            "包", "袋", "盒", "根", "张", "把", "节")));

    private FoodUnitUtils() {
    }

    public static String normalize(String unit) {
        if (unit == null) return UNKNOWN;
        String value = unit.trim();
        if (value.isEmpty()) return UNKNOWN;
        if ("克".equals(value)) return "g";
        if ("千克".equals(value)) return "kg";
        if ("毫升".equals(value)) return "ml";
        if ("升".equals(value)) return "L";
        if ("粒".equals(value)) return "个";
        if ("l".equals(value)) return "L";
        return UNITS.contains(value) ? value : UNKNOWN;
    }

    public static boolean isSupported(String unit) {
        String normalized = normalize(unit);
        return !UNKNOWN.equals(normalized) && UNITS.contains(normalized);
    }

    public static boolean isMass(String unit) {
        String normalized = normalize(unit).toLowerCase(Locale.ROOT);
        return "g".equals(normalized) || "kg".equals(normalized);
    }

    public static boolean isVolume(String unit) {
        String normalized = normalize(unit).toLowerCase(Locale.ROOT);
        return "ml".equals(normalized) || "l".equals(normalized);
    }

    public static boolean isReliableLibraryUnit(String unit, double estimatedWeightGrams) {
        return isMass(unit);
    }

    /**
     * Container/count units may only be copied into the library after the user
     * has explicitly confirmed the estimated conversion on the confirmation page.
     */
    public static boolean isReliableLibraryUnit(String unit, double estimatedWeightGrams,
            boolean userConfirmedConversion) {
        return isMass(unit) || (userConfirmedConversion && estimatedWeightGrams > 0
                && isSupported(unit) && !isVolume(unit));
    }
}
