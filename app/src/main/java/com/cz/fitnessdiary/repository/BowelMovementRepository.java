package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.BowelMovementDao;
import com.cz.fitnessdiary.database.entity.BowelMovement;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BowelMovementRepository {

    private final BowelMovementDao dao;
    private final ExecutorService executorService;

    public BowelMovementRepository(Application application) {
        dao = AppDatabase.getInstance(application).bowelMovementDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(BowelMovement record) {
        executorService.execute(() -> dao.insert(record));
    }

    public void update(BowelMovement record) {
        executorService.execute(() -> dao.update(record));
    }

    public void delete(BowelMovement record) {
        executorService.execute(() -> dao.delete(record));
    }

    public LiveData<List<BowelMovement>> getAllRecords() {
        return dao.getAllRecords();
    }

    public LiveData<List<BowelMovement>> getByDateRange(long startTs, long endTs) {
        return dao.getByDateRange(startTs, endTs);
    }

    public List<BowelMovement> getByDateRangeSync(long startTs, long endTs) {
        return dao.getByDateRangeSync(startTs, endTs);
    }

    public BowelMovement getLatestSync() {
        return dao.getLatestSync();
    }

    public BowelMovement getLatestByDateSync(long startTs, long endTs) {
        return dao.getLatestByDateSync(startTs, endTs);
    }

    public List<BowelMovementDao.BristolCount> getBristolDistributionSync(long startTs, long endTs) {
        return dao.getBristolDistributionSync(startTs, endTs);
    }

    public List<BowelMovementDao.ColorCount> getColorDistributionSync(long startTs, long endTs) {
        return dao.getColorDistributionSync(startTs, endTs);
    }
}
