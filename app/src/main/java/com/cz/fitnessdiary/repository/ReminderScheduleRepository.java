package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.ReminderScheduleDao;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderScheduleRepository {

    private final ReminderScheduleDao dao;
    private final ExecutorService executorService;

    public ReminderScheduleRepository(Application application) {
        dao = AppDatabase.getInstance(application).reminderScheduleDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(ReminderSchedule schedule) {
        executorService.execute(() -> dao.insert(schedule));
    }

    public void update(ReminderSchedule schedule) {
        executorService.execute(() -> dao.update(schedule));
    }

    public void deleteById(long scheduleId) {
        executorService.execute(() -> dao.deleteById(scheduleId));
    }

    public LiveData<List<ReminderSchedule>> getEnabledSchedulesByModule(String moduleType) {
        return dao.getEnabledSchedulesByModule(moduleType);
    }

    public List<ReminderSchedule> getEnabledSchedulesSync() {
        return dao.getEnabledSchedulesSync();
    }

    public ReminderSchedule getByIdSync(long scheduleId) {
        return dao.getByIdSync(scheduleId);
    }
}
