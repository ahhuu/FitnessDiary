package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.repository.WaterRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WaterDetailViewModel extends AndroidViewModel {

    private final WaterRecordRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<List<Float>> weekSeries = new MutableLiveData<>(new ArrayList<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public WaterDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new WaterRecordRepository(application);
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshWeekSeries();
    }

    public LiveData<Long> getSelectedDate() {
        return selectedDate;
    }

    public LiveData<List<WaterRecord>> getSelectedDateRecords() {
        return Transformations.switchMap(selectedDate,
                start -> repository.getRecordsByDateRange(start, start + 24L * 60L * 60L * 1000L));
    }

    public LiveData<Integer> getTodayTotal() {
        return Transformations.switchMap(selectedDate,
                start -> repository.getTotalAmountByDateRange(start, start + 24L * 60L * 60L * 1000L));
    }

    public LiveData<List<Float>> getWeekSeries() {
        return weekSeries;
    }

    public void refreshWeekSeries() {
        Long selected = selectedDate.getValue();
        if (selected == null) return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);
        executor.execute(() -> {
            List<Float> values = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                long start = dayStart - i * 24L * 60L * 60L * 1000L;
                List<WaterRecord> list = repository.getRecordsByDateRangeSync(start, start + 24L * 60L * 60L * 1000L);
                int sum = 0;
                for (WaterRecord record : list) sum += record.getAmountMl();
                values.add((float) sum);
            }
            weekSeries.postValue(values);
        });
    }

    public void addWater(int amount, String note) {
        repository.insert(new WaterRecord(amount, System.currentTimeMillis(), note));
        refreshWeekSeries();
    }

    public void updateWater(WaterRecord record) {
        repository.update(record);
        refreshWeekSeries();
    }

    public void deleteWater(WaterRecord record) {
        repository.delete(record);
        refreshWeekSeries();
    }
}
