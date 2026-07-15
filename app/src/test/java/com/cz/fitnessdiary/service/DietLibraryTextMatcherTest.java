package com.cz.fitnessdiary.service;

import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DietLibraryTextMatcherTest {
    @Test
    public void matchesMultipleLibraryFoodsWithoutAi() {
        FoodLibrary egg = new FoodLibrary("\u9e21\u86cb", 140, 12, 2, 10,
                "\u4e2a", 50, "\u86cb\u767d\u8d28");
        FoodLibrary milk = new FoodLibrary("\u725b\u5976", 54, 3.2, 5, 3,
                "ml", 250, "\u996e\u6599");

        ImageMealDraft draft = DietLibraryTextMatcher.match(
                "\u5348\u9910\u5403\u4e862\u4e2a\u9e21\u86cb\u548c300ml\u725b\u5976",
                Arrays.asList(egg, milk));

        assertNotNull(draft);
        assertEquals(2, draft.getItems().size());
        ImageFoodItemDraft first = draft.getItems().get(0);
        assertEquals("\u9e21\u86cb", first.getName());
        assertEquals(2d, first.getAmount(), 0.001d);
        assertEquals("\u4e2a", first.getUnit());
        assertEquals(140, first.getCalories());
        assertFalse(first.isNeedsReview());

        ImageFoodItemDraft second = draft.getItems().get(1);
        assertEquals("ml", second.getUnit());
        assertEquals(300d, second.getAmount(), 0.001d);
        assertTrue(second.isNeedsReview());
    }

    @Test
    public void convertsMassQuantityUsingLibraryNutrition() {
        FoodLibrary chicken = new FoodLibrary("\u9e21\u80f8\u8089", 133, 31, 0, 3.6,
                "g", 100, "\u86cb\u767d\u8d28");

        ImageMealDraft draft = DietLibraryTextMatcher.match(
                "\u5403\u4e86150g\u9e21\u80f8\u8089", Arrays.asList(chicken));

        assertNotNull(draft);
        assertEquals(1, draft.getItems().size());
        ImageFoodItemDraft item = draft.getItems().get(0);
        assertEquals(150d, item.getAmount(), 0.001d);
        assertEquals("g", item.getUnit());
        assertEquals(200, item.getCalories());
        assertEquals(46.5d, item.getProtein(), 0.001d);
        assertFalse(item.isNeedsReview());
    }

    @Test
    public void decomposesCompoundDishInsteadOfTakingGreedySubstring() {
        FoodLibrary curry = new FoodLibrary("咖喱", 80, 1, 8, 4,
                "份", 50, "调味");
        FoodLibrary chickenSteak = new FoodLibrary("鸡排", 260, 20, 12, 15,
                "份", 120, "荤菜");
        FoodLibrary misleadingCompound = new FoodLibrary("咖喱鸡", 300, 25, 8, 18,
                "份", 150, "荤菜");
        FoodLibrary rice = new FoodLibrary("米饭", 116, 2.6, 25.9, 0.3,
                "碗", 150, "主食");

        ImageMealDraft draft = DietLibraryTextMatcher.match("咖喱鸡排饭",
                Arrays.asList(curry, chickenSteak, misleadingCompound, rice));

        assertNotNull(draft);
        assertEquals("咖喱鸡排饭", draft.getMealName());
        assertEquals(3, draft.getItems().size());
        assertEquals("咖喱", draft.getItems().get(0).getName());
        assertEquals("鸡排", draft.getItems().get(1).getName());
        assertEquals("米饭", draft.getItems().get(2).getName());
        assertTrue(draft.getUnmatchedText().isEmpty());
    }

    @Test
    public void returnsNullWhenLibraryHasNoMatch() {
        FoodLibrary rice = new FoodLibrary("\u7c73\u996d", 116, 2.6, 25.9, 0.3,
                "\u7897", 150, "\u4e3b\u98df");

        assertNull(DietLibraryTextMatcher.match("\u4eca\u5929\u5403\u4e86\u9e21\u80f8\u8089",
                Arrays.asList(rice)));
    }
}
