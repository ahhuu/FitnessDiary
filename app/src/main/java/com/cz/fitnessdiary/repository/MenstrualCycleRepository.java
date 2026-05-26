package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.MenstrualCycleDao;
import com.cz.fitnessdiary.database.entity.MenstrualCycle;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MenstrualCycleRepository {

    private final MenstrualCycleDao dao;
    private final ExecutorService executorService;

    public MenstrualCycleRepository(Application application) {
        dao = AppDatabase.getInstance(application).menstrualCycleDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(MenstrualCycle record) {
        executorService.execute(() -> dao.insert(record));
    }

    public void update(MenstrualCycle record) {
        executorService.execute(() -> dao.update(record));
    }

    public void delete(MenstrualCycle record) {
        executorService.execute(() -> dao.delete(record));
    }

    public LiveData<List<MenstrualCycle>> getAllRecords() {
        return dao.getAllRecords();
    }

    public List<MenstrualCycle> getAllRecordsSync() {
        return dao.getAllRecordsSync();
    }

    public LiveData<List<MenstrualCycle>> getByDateRange(long startTs, long endTs) {
        return dao.getByDateRange(startTs, endTs);
    }

    public MenstrualCycle getLatestOngoingSync() {
        return dao.getLatestOngoingSync();
    }

    public MenstrualCycle getLatestSync() {
        return dao.getLatestSync();
    }

    public MenstrualCycle getCycleForDateSync(long date) {
        return dao.getCycleForDateSync(date);
    }
}
