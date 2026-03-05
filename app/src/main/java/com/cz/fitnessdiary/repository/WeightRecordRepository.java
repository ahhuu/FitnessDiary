package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.WeightRecordDao;
import com.cz.fitnessdiary.database.entity.WeightRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeightRecordRepository {

    private final WeightRecordDao dao;
    private final ExecutorService executorService;

    public WeightRecordRepository(Application application) {
        dao = AppDatabase.getInstance(application).weightRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(WeightRecord record) {
        executorService.execute(() -> dao.insert(record));
    }

    public void update(WeightRecord record) {
        executorService.execute(() -> dao.update(record));
    }

    public void delete(WeightRecord record) {
        executorService.execute(() -> dao.delete(record));
    }

    public LiveData<WeightRecord> getLatestRecord() {
        return dao.getLatestRecord();
    }

    public WeightRecord getLatestRecordSync() {
        return dao.getLatestRecordSync();
    }

    public LiveData<List<WeightRecord>> getRecordsByDateRange(long startTs, long endTs) {
        return dao.getRecordsByDateRange(startTs, endTs);
    }

    public LiveData<List<WeightRecord>> getAllRecords() {
        return dao.getAllRecords();
    }

    public List<WeightRecord> getRecordsByDateRangeSync(long startTs, long endTs) {
        return dao.getRecordsByDateRangeSync(startTs, endTs);
    }

    public List<WeightRecord> getRecentRecordsSync(int limit) {
        return dao.getRecentRecordsSync(limit);
    }
}
