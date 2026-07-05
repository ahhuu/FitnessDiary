package com.cz.fitnessdiary.repository;

import android.app.Application;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.RecipeDao;
import com.cz.fitnessdiary.database.entity.Recipe;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecipeRepository {

    private final RecipeDao recipeDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RecipeRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        recipeDao = db.recipeDao();
    }

    public void insert(Recipe recipe, OnOperationCompleteListener listener) {
        executor.execute(() -> {
            long id = recipeDao.insert(recipe);
            if (listener != null) listener.onSuccess(id);
        });
    }

    public void update(Recipe recipe) {
        executor.execute(() -> recipeDao.update(recipe));
    }

    public void delete(Recipe recipe) {
        executor.execute(() -> recipeDao.delete(recipe));
    }

    public void deleteById(long id) {
        executor.execute(() -> recipeDao.deleteById(id));
    }

    public void getAll(OnDataLoadedListener<List<Recipe>> listener) {
        executor.execute(() -> {
            List<Recipe> list = recipeDao.getAll();
            if (listener != null) listener.onLoaded(list);
        });
    }

    public void getById(long id, OnDataLoadedListener<Recipe> listener) {
        executor.execute(() -> {
            Recipe recipe = recipeDao.getById(id);
            if (listener != null) listener.onLoaded(recipe);
        });
    }

    public interface OnOperationCompleteListener {
        void onSuccess(long result);
    }

    public interface OnDataLoadedListener<T> {
        void onLoaded(T data);
    }
}
