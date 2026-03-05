package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.MedicationRecordDao;
import com.cz.fitnessdiary.database.entity.MedicationRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MedicationRecordRepository {

    private final MedicationRecordDao dao;
    private final ExecutorService executorService;

    public MedicationRecordRepository(Application application) {
        dao = AppDatabase.getInstance(application).medicationRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(MedicationRecord record) {
        executorService.execute(() -> dao.insert(record));
    }

    public void update(MedicationRecord record) {
        executorService.execute(() -> dao.update(record));
    }

    public void delete(MedicationRecord record) {
        executorService.execute(() -> dao.delete(record));
    }

    public LiveData<List<MedicationRecord>> getRecordsByDateRange(long startTs, long endTs) {
        return dao.getRecordsByDateRange(startTs, endTs);
    }

    public LiveData<Integer> getTakenCountByDateRange(long startTs, long endTs) {
        return dao.getTakenCountByDateRange(startTs, endTs);
    }

    public LiveData<Integer> getUntakenCountByDateRange(long startTs, long endTs) {
        return dao.getUntakenCountByDateRange(startTs, endTs);
    }

    public LiveData<MedicationRecord> getLatestRecord() {
        return dao.getLatestRecord();
    }

    public List<MedicationRecord> getRecordsByDateRangeSync(long startTs, long endTs) {
        return dao.getRecordsByDateRangeSync(startTs, endTs);
    }
}
