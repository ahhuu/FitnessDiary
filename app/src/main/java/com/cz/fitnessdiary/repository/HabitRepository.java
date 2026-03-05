package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.HabitItemDao;
import com.cz.fitnessdiary.database.dao.HabitRecordDao;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HabitRepository {

    public static final String RULE_DAILY_CHECKIN = "DAILY_CHECKIN";
    public static final String RULE_BREAKFAST = "BREAKFAST";
    public static final String RULE_EARLY_SLEEP = "EARLY_SLEEP";

    private final HabitItemDao habitItemDao;
    private final HabitRecordDao habitRecordDao;
    private final ExecutorService executorService;

    public HabitRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        habitItemDao = db.habitItemDao();
        habitRecordDao = db.habitRecordDao();
        executorService = Executors.newSingleThreadExecutor();
        ensureDefaultHabits();
    }

    private void ensureDefaultHabits() {
        executorService.execute(() -> {
            if (habitItemDao.getCountSync() == 0) {
                habitItemDao.insert(new HabitItem("每日打卡", true, true, 0, RULE_DAILY_CHECKIN));
                habitItemDao.insert(new HabitItem("早餐", true, true, 1, RULE_BREAKFAST));
                habitItemDao.insert(new HabitItem("早睡", true, true, 2, RULE_EARLY_SLEEP));
            }
        });
    }

    public LiveData<List<HabitItem>> getEnabledItems() {
        return habitItemDao.getEnabledItems();
    }

    public LiveData<List<HabitItem>> getAllItems() {
        return habitItemDao.getAllItems();
    }

    public List<HabitItem> getAllItemsSync() {
        return habitItemDao.getAllItemsSync();
    }

    public LiveData<List<HabitRecord>> getRecordsByDate(long date) {
        return habitRecordDao.getRecordsByDate(date);
    }

    public List<HabitRecord> getRecordsByDateSync(long date) {
        return habitRecordDao.getRecordsByDateSync(date);
    }

    public void upsertRecord(HabitRecord record) {
        executorService.execute(() -> habitRecordDao.upsert(record));
    }

    public void updateItem(HabitItem item) {
        executorService.execute(() -> habitItemDao.update(item));
    }

    public void insertItem(HabitItem item) {
        executorService.execute(() -> habitItemDao.insert(item));
    }

    public int getCompletedCountSync(long habitId) {
        return habitRecordDao.getCompletedCountSync(habitId);
    }

    public List<HabitRecord> getRecentByHabitSync(long habitId, int limit) {
        return habitRecordDao.getRecentByHabitSync(habitId, limit);
    }

    public int getCompletedCountByHabitAndDateRangeSync(long habitId, long startTs, long endTs) {
        return habitRecordDao.getCompletedCountByHabitAndDateRangeSync(habitId, startTs, endTs);
    }

    public int getRecordCountByHabitAndDateRangeSync(long habitId, long startTs, long endTs) {
        return habitRecordDao.getRecordCountByHabitAndDateRangeSync(habitId, startTs, endTs);
    }
}
