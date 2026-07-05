package com.cz.fitnessdiary.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.Recipe;

import java.util.List;

@Dao
public interface RecipeDao {

    @Insert
    long insert(Recipe recipe);

    @Query("SELECT * FROM recipe ORDER BY updated_at DESC")
    List<Recipe> getAll();

    @Query("SELECT * FROM recipe WHERE id = :id")
    Recipe getById(long id);

    @Query("SELECT * FROM recipe WHERE meal_type = :mealType ORDER BY updated_at DESC")
    List<Recipe> getByMealType(int mealType);

    @Query("SELECT * FROM recipe WHERE is_favorite = 1 ORDER BY updated_at DESC")
    List<Recipe> getFavorites();

    @Delete
    void delete(Recipe recipe);

    @androidx.room.Update
    void update(Recipe recipe);

    @Query("DELETE FROM recipe WHERE id = :id")
    void deleteById(long id);
}
