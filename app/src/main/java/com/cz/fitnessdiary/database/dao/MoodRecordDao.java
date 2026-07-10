package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.MoodRecord;

@Dao
public interface MoodRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(MoodRecord record);

    @Query("SELECT * FROM mood_record WHERE date = :date LIMIT 1")
    MoodRecord getByDateSync(long date);

    @Query("SELECT * FROM mood_record WHERE date = :date LIMIT 1")
    LiveData<MoodRecord> getByDate(long date);

    @Query("SELECT * FROM mood_record ORDER BY date DESC")
    java.util.List<MoodRecord> getAllRecordsSync();
}
