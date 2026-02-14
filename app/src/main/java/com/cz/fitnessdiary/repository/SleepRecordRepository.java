package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.SleepRecordDao;
import com.cz.fitnessdiary.database.entity.SleepRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SleepRecordRepository {

    private SleepRecordDao sleepRecordDao;
    private ExecutorService executorService;

    public SleepRecordRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        sleepRecordDao = db.sleepRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(SleepRecord sleepRecord) {
        executorService.execute(() -> sleepRecordDao.insert(sleepRecord));
    }

    public void update(SleepRecord sleepRecord) {
        executorService.execute(() -> sleepRecordDao.update(sleepRecord));
    }

    public void delete(SleepRecord sleepRecord) {
        executorService.execute(() -> sleepRecordDao.delete(sleepRecord));
    }

    public LiveData<List<SleepRecord>> getSleepRecordsByDateRange(long startDate, long endDate) {
        return sleepRecordDao.getSleepRecordsByDateRange(startDate, endDate);
    }

    public LiveData<Long> getTotalSleepDurationByDateRange(long startDate, long endDate) {
        return sleepRecordDao.getTotalSleepDurationByDateRange(startDate, endDate);
    }

    public List<SleepRecord> getSleepRecordsByDateRangeSync(long startDate, long endDate) {
        return sleepRecordDao.getSleepRecordsByDateRangeSync(startDate, endDate);
    }
}
