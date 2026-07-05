package com.cz.fitnessdiary.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.FavoriteFood;

import java.util.List;

@Dao
public interface FavoriteFoodDao {

    @Insert
    long insert(FavoriteFood favoriteFood);

    @Query("SELECT * FROM favorite_food ORDER BY created_at DESC")
    List<FavoriteFood> getAll();

    @Query("SELECT * FROM favorite_food WHERE id = :id")
    FavoriteFood getById(long id);

    @Query("SELECT * FROM favorite_food WHERE food_name LIKE '%' || :keyword || '%' ORDER BY created_at DESC")
    List<FavoriteFood> searchByName(String keyword);

    @androidx.room.Update
    void update(FavoriteFood favoriteFood);

    @Delete
    void delete(FavoriteFood favoriteFood);

    @Query("DELETE FROM favorite_food WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT COUNT(*) FROM favorite_food WHERE food_name = :foodName")
    int countByName(String foodName);
}
