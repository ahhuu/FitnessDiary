package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.CustomRecordDao;
import com.cz.fitnessdiary.database.entity.CustomRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomRecordRepository {

    private final CustomRecordDao dao;
    private final ExecutorService executorService;

    public CustomRecordRepository(Application application) {
        dao = AppDatabase.getInstance(application).customRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(CustomRecord record) {
        executorService.execute(() -> dao.insert(record));
    }

    public void update(CustomRecord record) {
        executorService.execute(() -> dao.update(record));
    }

    public void delete(CustomRecord record) {
        executorService.execute(() -> dao.delete(record));
    }

    public LiveData<List<CustomRecord>> getRecordsByTrackerAndDateRange(long trackerId, long startTs, long endTs) {
        return dao.getRecordsByTrackerAndDateRange(trackerId, startTs, endTs);
    }

    public LiveData<Double> getSumValueByTrackerAndDateRange(long trackerId, long startTs, long endTs) {
        return dao.getSumValueByTrackerAndDateRange(trackerId, startTs, endTs);
    }

    public LiveData<CustomRecord> getLatestRecordByTracker(long trackerId) {
        return dao.getLatestRecordByTracker(trackerId);
    }

    public LiveData<Integer> getRecordCountByDateRange(long startTs, long endTs) {
        return dao.getRecordCountByDateRange(startTs, endTs);
    }

    public LiveData<Integer> getRecordCountByTrackerAndDateRange(long trackerId, long startTs, long endTs) {
        return dao.getRecordCountByTrackerAndDateRange(trackerId, startTs, endTs);
    }

    public List<CustomRecord> getRecordsByTrackerAndDateRangeSync(long trackerId, long startTs, long endTs) {
        return dao.getRecordsByTrackerAndDateRangeSync(trackerId, startTs, endTs);
    }

    public Long getLatestTimestampByTrackerSync(long trackerId) {
        return dao.getLatestTimestampByTrackerSync(trackerId);
    }
}
