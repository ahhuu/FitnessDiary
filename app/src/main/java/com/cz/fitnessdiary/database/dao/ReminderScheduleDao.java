package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.ReminderSchedule;

import java.util.List;

@Dao
public interface ReminderScheduleDao {

    @Insert
    long insert(ReminderSchedule schedule);

    @Update
    void update(ReminderSchedule schedule);

    @Query("DELETE FROM reminder_schedule WHERE id = :scheduleId")
    void deleteById(long scheduleId);

    @Query("SELECT * FROM reminder_schedule WHERE is_enabled = 1")
    List<ReminderSchedule> getEnabledSchedulesSync();

    @Query("SELECT * FROM reminder_schedule WHERE module_type = :moduleType AND is_enabled = 1")
    LiveData<List<ReminderSchedule>> getEnabledSchedulesByModule(String moduleType);

    @Query("DELETE FROM reminder_schedule WHERE is_preset = :isPreset")
    void deleteByPreset(boolean isPreset);

    @Query("SELECT COUNT(*) FROM reminder_schedule WHERE is_preset = :isPreset")
    int countByPreset(boolean isPreset);

    @Query("SELECT * FROM reminder_schedule WHERE is_preset = :isPreset ORDER BY sort_order ASC")
    List<ReminderSchedule> getByPreset(boolean isPreset);

    @Query("SELECT * FROM reminder_schedule WHERE id = :scheduleId LIMIT 1")
    ReminderSchedule getByIdSync(long scheduleId);
}
