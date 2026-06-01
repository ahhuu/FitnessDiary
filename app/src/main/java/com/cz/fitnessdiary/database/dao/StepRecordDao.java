package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.StepRecord;

@Dao
public interface StepRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(StepRecord record);

    @Query("SELECT * FROM step_record WHERE date = :date LIMIT 1")
    StepRecord getByDateSync(long date);

    @Query("SELECT * FROM step_record WHERE date = :date LIMIT 1")
    LiveData<StepRecord> getByDate(long date);

    @Query("SELECT * FROM step_record ORDER BY date DESC LIMIT 7")
    LiveData<java.util.List<StepRecord>> getRecentWeek();

    @Query("SELECT * FROM step_record ORDER BY date DESC LIMIT 30")
    LiveData<java.util.List<StepRecord>> getRecentMonth();

    @Delete
    void delete(StepRecord record);
}
