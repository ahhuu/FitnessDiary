package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.ExtraExerciseLogDao;
import com.cz.fitnessdiary.database.entity.ExtraExerciseLog;
import com.cz.fitnessdiary.ui.widget.HomeWidgetProvider;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExtraExerciseLogRepository {

    private final ExtraExerciseLogDao dao;
    private final ExecutorService executorService;
    private final Application application;

    public ExtraExerciseLogRepository(Application application) {
        this.application = application;
        dao = AppDatabase.getInstance(application).extraExerciseLogDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<ExtraExerciseLog>> getLogsByDateRange(long startDate, long endDate) {
        return dao.getLogsByDateRange(startDate, endDate);
    }

    public LiveData<List<ExtraExerciseLog>> getAllLogs() {
        return dao.getAllLogs();
    }

    public void insert(ExtraExerciseLog log) {
        executorService.execute(() -> {
            dao.insert(log);
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    public void update(ExtraExerciseLog log) {
        executorService.execute(() -> {
            dao.update(log);
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    public void delete(ExtraExerciseLog log) {
        executorService.execute(() -> {
            dao.delete(log);
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    public List<ExtraExerciseLog> getLogsByDateRangeSync(long startDate, long endDate) {
        return dao.getLogsByDateRangeSync(startDate, endDate);
    }

    public List<ExtraExerciseLog> getAllLogsSync() {
        return dao.getAllLogsSync();
    }

    public List<Long> getAllRecordDatesSync() {
        return dao.getAllRecordDatesSync();
    }
}
