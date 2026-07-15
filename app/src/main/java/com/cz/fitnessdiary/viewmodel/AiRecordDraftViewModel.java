package com.cz.fitnessdiary.viewmodel;

import androidx.lifecycle.ViewModel;

import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the latest uncommitted AI draft in memory so editing or reopening does
 * not trigger another network request. It intentionally does not persist images
 * or drafts to Room.
 */
public class AiRecordDraftViewModel extends ViewModel {
    private String dietTextKey;
    private ImageMealDraft dietDraft;

    public ImageMealDraft getCachedDietDraft(String text) {
        if (text == null || !text.equals(dietTextKey) || dietDraft == null) {
            return null;
        }
        return copyMealDraft(dietDraft);
    }

    public void cacheDietDraft(String text, ImageMealDraft draft) {
        dietTextKey = text;
        dietDraft = copyMealDraft(draft);
    }

    public void clearDietDraft() {
        dietTextKey = null;
        dietDraft = null;
    }

    private ImageMealDraft copyMealDraft(ImageMealDraft source) {
        if (source == null) {
            return null;
        }
        ImageMealDraft copy = new ImageMealDraft();
        copy.setMealName(source.getMealName());
        copy.setMealType(source.getMealType());
        copy.setServings(source.getServings());
        copy.setServingUnit(source.getServingUnit());
        copy.setSuggestion(source.getSuggestion());
        List<ImageFoodItemDraft> items = new ArrayList<>();
        if (source.getItems() != null) {
            for (ImageFoodItemDraft item : source.getItems()) {
                if (item == null) {
                    continue;
                }
                ImageFoodItemDraft itemCopy = new ImageFoodItemDraft(
                        item.getName(), item.getCalories(), item.getProtein(), item.getCarbs(), item.getFat(),
                        item.getAmount(), item.getUnit(), item.getCategory());
                itemCopy.setNutritionBasis(item.getNutritionBasis());
                itemCopy.setEstimatedWeightGrams(item.getEstimatedWeightGrams());
                itemCopy.setNeedsReview(item.isNeedsReview());
                items.add(itemCopy);
            }
        }
        copy.setItems(items);
        copy.recomputeTotals();
        return copy;
    }
}
