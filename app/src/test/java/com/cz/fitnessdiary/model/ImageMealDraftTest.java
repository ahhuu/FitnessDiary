package com.cz.fitnessdiary.model;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ImageMealDraftTest {
    @Test
    public void totalsAreSumOfIndependentFoodItems() {
        ImageFoodItemDraft eggs = new ImageFoodItemDraft("鸡蛋", 140, 12, 2, 10, 2, "个", "其他");
        ImageFoodItemDraft milk = new ImageFoodItemDraft("牛奶", 150, 8, 12, 5, 300, "ml", "乳制品");
        ImageMealDraft draft = new ImageMealDraft();
        draft.setItems(Arrays.asList(eggs, milk));
        draft.recomputeTotals();
        assertEquals(290, draft.getTotalCalories());
        assertEquals(20d, draft.getTotalProtein(), 0.001d);
        assertEquals(2d, eggs.getAmount(), 0.001d);
        assertEquals("ml", milk.getUnit());
    }

    @Test
    public void confirmationEditorNormalizesServingToGrams() {
        ImageFoodItemDraft rice = new ImageFoodItemDraft("rice", 260, 5, 60, 0.5,
                1, "碗", "主食");
        rice.setEstimatedWeightGrams(200);

        assertEquals(true, rice.normalizeToGramsForEditing());
        assertEquals("g", rice.getRawUnit());
        assertEquals(200d, rice.getAmount(), 0.001d);
        assertEquals(260, rice.getCalories());
        assertEquals(5d, rice.getProtein(), 0.001d);
        assertEquals(60d, rice.getCarbs(), 0.001d);
    }
}
