package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.CustomTracker;

import java.util.List;

@Dao
public interface CustomTrackerDao {

    @Insert
    long insert(CustomTracker tracker);

    @Update
    void update(CustomTracker tracker);

    @Query("SELECT * FROM custom_tracker WHERE is_enabled = 1 ORDER BY sort_order ASC, id ASC")
    LiveData<List<CustomTracker>> getEnabledTrackers();

    @Query("SELECT * FROM custom_tracker ORDER BY sort_order ASC, id ASC")
    LiveData<List<CustomTracker>> getAllTrackers();

    @Query("SELECT * FROM custom_tracker ORDER BY sort_order ASC, id ASC")
    List<CustomTracker> getAllTrackersSync();

    @Query("SELECT COUNT(*) FROM custom_tracker")
    int getTrackerCountSync();

    @Query("SELECT COUNT(*) FROM custom_tracker WHERE is_enabled = 1")
    LiveData<Integer> getEnabledTrackerCount();
}
