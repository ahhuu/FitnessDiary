package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.repository.SleepRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SleepDetailViewModel extends AndroidViewModel {

    private final SleepRecordRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<List<Float>> weekSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Float>> monthSeries = new MutableLiveData<>(new ArrayList<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SleepDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new SleepRecordRepository(application);
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshStatsSeries();
    }

    public LiveData<List<SleepRecord>> getSelectedDateRecords() {
        return Transformations.switchMap(selectedDate,
                start -> repository.getSleepRecordsByDateRange(start, start + 24L * 60L * 60L * 1000L));
    }

    public LiveData<List<Float>> getWeekSeries() {
        return weekSeries;
    }

    public LiveData<List<Float>> getMonthSeries() {
        return monthSeries;
    }

    public void refreshStatsSeries() {
        Long selected = selectedDate.getValue();
        if (selected == null) return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);
        executor.execute(() -> {
            List<Float> week = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                long start = dayStart - i * 24L * 60L * 60L * 1000L;
                long end = start + 24L * 60L * 60L * 1000L;
                List<SleepRecord> list = repository.getSleepRecordsByDateRangeSync(start, end);
                long total = 0;
                for (SleepRecord record : list) total += record.getDuration();
                week.add(total / 3600f);
            }
            weekSeries.postValue(week);

            List<Float> month = new ArrayList<>();
            for (int i = 29; i >= 0; i--) {
                long start = dayStart - i * 24L * 60L * 60L * 1000L;
                long end = start + 24L * 60L * 60L * 1000L;
                List<SleepRecord> list = repository.getSleepRecordsByDateRangeSync(start, end);
                long total = 0;
                for (SleepRecord record : list) total += record.getDuration();
                month.add(total / 3600f);
            }
            monthSeries.postValue(month);
        });
    }

    public void addSleepRecord(long startTime, long endTime, int quality, String notes) {
        repository.insert(new SleepRecord(startTime, endTime, quality, notes));
        refreshStatsSeries();
    }

    public void updateSleepRecord(SleepRecord record) {
        repository.update(record);
        refreshStatsSeries();
    }

    public void deleteSleepRecord(SleepRecord record) {
        repository.delete(record);
        refreshStatsSeries();
    }
}
