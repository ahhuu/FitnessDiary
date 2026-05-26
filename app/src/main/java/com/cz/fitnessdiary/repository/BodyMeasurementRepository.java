package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.BodyMeasurementDao;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BodyMeasurementRepository {

    private final BodyMeasurementDao dao;
    private final ExecutorService executorService;

    public BodyMeasurementRepository(Application application) {
        dao = AppDatabase.getInstance(application).bodyMeasurementDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(BodyMeasurement record) {
        executorService.execute(() -> dao.insert(record));
    }

    public void update(BodyMeasurement record) {
        executorService.execute(() -> dao.update(record));
    }

    public void delete(BodyMeasurement record) {
        executorService.execute(() -> dao.delete(record));
    }

    public LiveData<BodyMeasurement> getLatestByType(String type) {
        return dao.getLatestByType(type);
    }

    public BodyMeasurement getLatestByTypeSync(String type) {
        return dao.getLatestByTypeSync(type);
    }

    public List<BodyMeasurement> getByTypeAndDateRangeSync(String type, long startTs, long endTs) {
        return dao.getByTypeAndDateRangeSync(type, startTs, endTs);
    }

    public LiveData<List<BodyMeasurement>> getByDateRange(long startTs, long endTs) {
        return dao.getByDateRange(startTs, endTs);
    }

    public LiveData<List<BodyMeasurement>> getAllByType(String type) {
        return dao.getAllByType(type);
    }

    public LiveData<List<BodyMeasurement>> getAllRecords() {
        return dao.getAllRecords();
    }

    public List<BodyMeasurement> getByDateRangeSync(long startTs, long endTs) {
        return dao.getByDateRangeSync(startTs, endTs);
    }

    public LiveData<List<String>> getDistinctTypes() {
        return dao.getDistinctTypes();
    }
}
