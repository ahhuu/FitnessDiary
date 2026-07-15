package com.cz.fitnessdiary.service;

import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AiDietTextAnalyzerTest {
    @Test
    public void convertsNutritionBasisToActualPortionTotals() throws Exception {
        String json = "{\"meal_name\":\"午餐\",\"items\":["
                + "{\"name\":\"鸡蛋\",\"amount\":2,\"unit\":\"个\","
                + "\"basis\":\"PER_UNIT\",\"calories\":70,\"protein\":6,\"carbs\":1,\"fat\":5},"
                + "{\"name\":\"牛奶\",\"amount\":300,\"unit\":\"ml\","
                + "\"basis\":\"PER_100ML\",\"estimated_weight_g\":300,"
                + "\"calories\":50,\"protein\":3,\"carbs\":5,\"fat\":2}]}";

        ImageMealDraft draft = AiDietTextAnalyzer.parse(json);

        assertEquals(290, draft.getTotalCalories());
        assertEquals(2, draft.getItems().size());
        ImageFoodItemDraft eggs = draft.getItems().get(0);
        assertEquals(140, eggs.getCalories());
        assertEquals("个", eggs.getUnit());
        assertEquals(ImageFoodItemDraft.BASIS_PER_UNIT, eggs.getNutritionBasis());

        eggs.setAmount(3d);
        eggs.recalculateNutrition();
        assertEquals(210, eggs.getCalories());
    }

    @Test
    public void missingUnitRequiresReview() throws Exception {
        String json = "{\"items\":[{\"name\":\"米饭\",\"amount\":1,"
                + "\"calories\":200,\"protein\":4,\"carbs\":45,\"fat\":1}]}";

        ImageMealDraft draft = AiDietTextAnalyzer.parse(json);

        assertTrue(draft.getItems().get(0).isNeedsReview());
    }

    @Test
    public void unknownUnitIsRetainedAndRequiresReview() throws Exception {
        ImageMealDraft draft = AiDietTextAnalyzer.parse(
                "{\"items\":[{\"name\":\"蛋白粉\",\"amount\":1,\"unit\":\"scoop\",\"calories\":100}]}" );

        ImageFoodItemDraft item = draft.getItems().get(0);
        assertEquals("scoop", item.getRawUnit());
        assertEquals(com.cz.fitnessdiary.utils.FoodUnitUtils.UNKNOWN, item.getUnit());
        assertTrue(item.isNeedsReview());
    }
}
