package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.repository.HabitRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HabitDetailViewModel extends AndroidViewModel {

    public static class HabitStat {
        public int streak;
        public int completionRate;

        public HabitStat(int streak, int completionRate) {
            this.streak = streak;
            this.completionRate = completionRate;
        }
    }

    private final HabitRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<Map<Long, HabitStat>> habitStats = new MutableLiveData<>(new HashMap<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public HabitDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new HabitRepository(application);
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshStats();
    }

    public LiveData<Long> getSelectedDate() {
        return selectedDate;
    }

    public LiveData<List<HabitItem>> getEnabledItems() {
        return repository.getEnabledItems();
    }

    public LiveData<List<HabitRecord>> getSelectedDateRecords() {
        Long date = selectedDate.getValue();
        if (date == null)
            date = DateUtils.getTodayStartTimestamp();
        return repository.getRecordsByDate(date);
    }

    public LiveData<Map<Long, HabitStat>> getHabitStats() {
        return habitStats;
    }

    public void refreshStats() {
        Long selected = selectedDate.getValue();
        if (selected == null)
            return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);
        executor.execute(() -> {
            Map<Long, HabitStat> map = new HashMap<>();
            List<HabitItem> items = repository.getAllItemsSync();
            long rangeStart = dayStart - 29L * 24L * 60L * 60L * 1000L;
            for (HabitItem item : items) {
                if (!item.isEnabled())
                    continue;
                int completed = repository.getCompletedCountByHabitAndDateRangeSync(item.getId(), rangeStart,
                        dayStart + 24L * 60L * 60L * 1000L);

                // 以最近 30 天为统计标准，同时对新添加习惯进行分母保护
                long createTime = item.getCreateTime();
                int expectedTotalDays = 30; // 默认以 30 天为分母
                if (createTime <= 0) {
                    expectedTotalDays = 1; // 暂无创建时间记录（容错），默认 1 天
                } else {
                    // 自创建日期到当前选择日期的自然天数（含当天 +1）
                    long createDayStart = DateUtils.getDayStartTimestamp(createTime);
                    int daysSinceCreation = (int) ((dayStart - createDayStart) / (24L * 60L * 60L * 1000L)) + 1;
                    expectedTotalDays = Math.max(1, Math.min(30, daysSinceCreation));
                }

                int rate = Math.round(completed * 100f / expectedTotalDays);

                List<HabitRecord> recent = repository.getRecentByHabitSync(item.getId(), 60);
                Set<Long> doneDays = new HashSet<>();
                for (HabitRecord record : recent) {
                    if (record.isCompleted())
                        doneDays.add(record.getRecordDate());
                }
                int streak = 0;
                for (int i = 0; i < 60; i++) {
                    long day = dayStart - i * 24L * 60L * 60L * 1000L;
                    if (doneDays.contains(day))
                        streak++;
                    else
                        break;
                }
                map.put(item.getId(), new HabitStat(streak, rate));
            }
            habitStats.postValue(map);
        });
    }

    public void upsertRecord(long habitId, long dayStart, boolean completed, String source) {
        executor.execute(() -> {
            repository.upsertRecordSync(new HabitRecord(habitId, dayStart, completed, source, System.currentTimeMillis()));
        });
        refreshStats();
    }

    public void updateItem(HabitItem item) {
        repository.updateItem(item);
        refreshStats();
    }

    public void addHabitItem(String name, String description) {
        executor.execute(() -> {
            int sort = repository.getAllItemsSync().size();
            HabitItem item = new HabitItem(name, false, true, sort, "MANUAL");
            item.setDescription(description);
            item.setCreateTime(System.currentTimeMillis());
            repository.insertItem(item);
            refreshStats();
        });
    }
}
