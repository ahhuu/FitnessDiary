package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.StepRecordDao;
import com.cz.fitnessdiary.database.entity.StepRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StepRecordRepository {

    private final StepRecordDao dao;
    private final ExecutorService executorService;

    public StepRecordRepository(Application application) {
        dao = AppDatabase.getInstance(application).stepRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insertOrUpdate(StepRecord record) {
        executorService.execute(() -> dao.insertOrUpdate(record));
    }

    public StepRecord getByDateSync(long date) {
        return dao.getByDateSync(date);
    }

    public LiveData<StepRecord> getByDate(long date) {
        return dao.getByDate(date);
    }

    public LiveData<List<StepRecord>> getRecentMonth() {
        return dao.getRecentMonth();
    }

    public void delete(StepRecord record) {
        executorService.execute(() -> dao.delete(record));
    }
}
