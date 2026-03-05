package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.WaterRecordDao;
import com.cz.fitnessdiary.database.entity.WaterRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WaterRecordRepository {

    private final WaterRecordDao dao;
    private final ExecutorService executorService;

    public WaterRecordRepository(Application application) {
        dao = AppDatabase.getInstance(application).waterRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(WaterRecord record) {
        executorService.execute(() -> dao.insert(record));
    }

    public void update(WaterRecord record) {
        executorService.execute(() -> dao.update(record));
    }

    public void delete(WaterRecord record) {
        executorService.execute(() -> dao.delete(record));
    }

    public LiveData<List<WaterRecord>> getRecordsByDateRange(long startTs, long endTs) {
        return dao.getRecordsByDateRange(startTs, endTs);
    }

    public LiveData<Integer> getTotalAmountByDateRange(long startTs, long endTs) {
        return dao.getTotalAmountByDateRange(startTs, endTs);
    }

    public LiveData<WaterRecord> getLatestRecord() {
        return dao.getLatestRecord();
    }

    public List<WaterRecord> getRecordsByDateRangeSync(long startTs, long endTs) {
        return dao.getRecordsByDateRangeSync(startTs, endTs);
    }
}
