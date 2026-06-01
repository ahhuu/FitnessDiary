package com.cz.fitnessdiary.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.repository.StepRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.List;

public class StepDetailViewModel extends AndroidViewModel {

    private final StepRecordRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final LiveData<Long> dayStart;

    public StepDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new StepRecordRepository(application);
        dayStart = Transformations.map(selectedDate, d -> d);
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
    }

    public LiveData<StepRecord> getTodayStep() {
        return Transformations.switchMap(selectedDate, repository::getByDate);
    }

    public LiveData<List<StepRecord>> getRecentRecords() {
        return repository.getRecentMonth();
    }

    public void insertOrUpdate(StepRecord record) {
        repository.insertOrUpdate(record);
    }

    public void delete(StepRecord record) {
        repository.delete(record);
    }

    public int getStepTarget() {
        SharedPreferences sp = getApplication().getSharedPreferences(
                "fitness_diary_prefs", android.content.Context.MODE_PRIVATE);
        return sp.getInt("step_target", 8000);
    }

    public void setStepTarget(int target) {
        getApplication().getSharedPreferences("fitness_diary_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putInt("step_target", target).apply();
    }

    public void setTodaySteps(int steps) {
        new Thread(() -> {
            Long date = selectedDate.getValue();
            long day = date != null ? date : DateUtils.getTodayStartTimestamp();
            StepRecord existing = repository.getByDateSync(day);
            if (existing != null) {
                existing.setSteps(steps);
                existing.setSource(1);
                existing.setCreateTime(System.currentTimeMillis());
                repository.insertOrUpdate(existing);
            } else {
                repository.insertOrUpdate(new StepRecord(day, steps, 1, System.currentTimeMillis()));
            }
        }).start();
    }
}
