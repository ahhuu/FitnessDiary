package com.cz.fitnessdiary.repository;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SocialRepositoryTest {
    @Test
    public void sanitizeHealthSummary_keepsOnlyConfirmedFields() {
        Map<String, Object> input = new HashMap<>();
        input.put("workoutMinutes", 45);
        input.put("checkInDays", 7);
        input.put("steps", 8000);
        input.put("achievement", "完成一周训练");

        Map<String, Object> result = SocialRepository.sanitizeHealthSummary(input);

        assertEquals(4, result.size());
        assertEquals(45L, result.get("workoutMinutes"));
        assertEquals("完成一周训练", result.get("achievement"));
    }

    @Test
    public void sanitizeHealthSummary_rejectsUnsupportedOrInvalidValues() {
        Map<String, Object> unsupported = new HashMap<>();
        unsupported.put("calories", 450);
        assertThrows(IllegalArgumentException.class,
                () -> SocialRepository.sanitizeHealthSummary(unsupported));

        Map<String, Object> negative = new HashMap<>();
        negative.put("steps", -1);
        assertThrows(IllegalArgumentException.class,
                () -> SocialRepository.sanitizeHealthSummary(negative));
    }
}
