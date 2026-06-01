package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.MoodRecordDao;
import com.cz.fitnessdiary.database.entity.MoodRecord;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MoodRecordRepository {

    private final MoodRecordDao dao;
    private final ExecutorService executorService;

    public MoodRecordRepository(Application application) {
        dao = AppDatabase.getInstance(application).moodRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insertOrUpdate(MoodRecord record) {
        executorService.execute(() -> dao.insertOrUpdate(record));
    }

    public MoodRecord getByDateSync(long date) {
        return dao.getByDateSync(date);
    }

    public LiveData<MoodRecord> getByDate(long date) {
        return dao.getByDate(date);
    }
}
