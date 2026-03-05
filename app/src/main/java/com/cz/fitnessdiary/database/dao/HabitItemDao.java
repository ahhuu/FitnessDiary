package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.HabitItem;

import java.util.List;

@Dao
public interface HabitItemDao {

    @Insert
    long insert(HabitItem item);

    @Update
    void update(HabitItem item);

    @Query("SELECT * FROM habit_item WHERE is_enabled = 1 ORDER BY sort_order ASC, id ASC")
    LiveData<List<HabitItem>> getEnabledItems();

    @Query("SELECT * FROM habit_item ORDER BY sort_order ASC, id ASC")
    LiveData<List<HabitItem>> getAllItems();

    @Query("SELECT * FROM habit_item ORDER BY sort_order ASC, id ASC")
    List<HabitItem> getAllItemsSync();

    @Query("SELECT COUNT(*) FROM habit_item")
    int getCountSync();
}