package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.cz.fitnessdiary.database.entity.FavoriteFood;
import com.cz.fitnessdiary.database.entity.Recipe;
import com.cz.fitnessdiary.repository.FavoriteFoodRepository;
import com.cz.fitnessdiary.repository.RecipeRepository;

import java.util.ArrayList;
import java.util.List;

public class RecipeViewModel extends AndroidViewModel {

    private final RecipeRepository recipeRepo;
    private final FavoriteFoodRepository favRepo;

    private List<Recipe> recipeList = new ArrayList<>();
    private List<FavoriteFood> favList = new ArrayList<>();

    public RecipeViewModel(@NonNull Application application) {
        super(application);
        recipeRepo = new RecipeRepository(application);
        favRepo = new FavoriteFoodRepository(application);
    }

    public void loadRecipes(Runnable onDone) {
        recipeRepo.getAll(data -> {
            recipeList = data != null ? data : new ArrayList<>();
            if (onDone != null) onDone.run();
        });
    }

    public List<Recipe> getRecipeList() { return recipeList; }

    public void saveRecipe(Recipe recipe) {
        recipeRepo.insert(recipe, id -> {});
    }

    public void updateRecipe(Recipe recipe) {
        recipeRepo.update(recipe);
    }

    public void deleteRecipe(Recipe recipe) {
        recipeRepo.delete(recipe);
    }

    public void deleteRecipeById(long id) {
        recipeRepo.deleteById(id);
    }

    public void loadFavoriteFoods(Runnable onDone) {
        favRepo.getAll(data -> {
            favList = data != null ? data : new ArrayList<>();
            if (onDone != null) onDone.run();
        });
    }

    public List<FavoriteFood> getFavoriteFoodList() { return favList; }

    public void addFavoriteFood(FavoriteFood food) {
        favRepo.insert(food);
    }

    public void deleteFavoriteFood(FavoriteFood food) {
        favRepo.delete(food);
    }

    public void deleteFavoriteFoodById(long id) {
        favRepo.deleteById(id);
    }

    public void isFoodFavorited(String name, FavoriteFoodRepository.OnDataLoadedListener<Boolean> listener) {
        favRepo.existsByName(name, listener);
    }
}
