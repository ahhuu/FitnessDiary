package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.CustomTrackerDao;
import com.cz.fitnessdiary.database.entity.CustomTracker;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomTrackerRepository {

    private final CustomTrackerDao dao;
    private final ExecutorService executorService;

    public CustomTrackerRepository(Application application) {
        dao = AppDatabase.getInstance(application).customTrackerDao();
        executorService = Executors.newSingleThreadExecutor();
        ensureDefaultTracker();
    }

    private void ensureDefaultTracker() {
        executorService.execute(() -> {
            if (dao.getTrackerCountSync() == 0) {
                dao.insert(new CustomTracker("自定义记录", "次", "#4CAF50", true, 0));
            }
        });
    }

    public LiveData<List<CustomTracker>> getEnabledTrackers() {
        return dao.getEnabledTrackers();
    }

    public LiveData<List<CustomTracker>> getAllTrackers() {
        return dao.getAllTrackers();
    }

    public List<CustomTracker> getAllTrackersSync() {
        return dao.getAllTrackersSync();
    }

    public LiveData<Integer> getEnabledTrackerCount() {
        return dao.getEnabledTrackerCount();
    }

    public void insert(CustomTracker tracker) {
        executorService.execute(() -> dao.insert(tracker));
    }

    public void update(CustomTracker tracker) {
        executorService.execute(() -> dao.update(tracker));
    }
}
