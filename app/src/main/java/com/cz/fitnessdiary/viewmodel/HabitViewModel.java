package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.repository.HabitRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.List;

public class HabitViewModel extends AndroidViewModel {

    private final HabitRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());

    public HabitViewModel(@NonNull Application application) {
        super(application);
        repository = new HabitRepository(application);
    }

    public LiveData<List<HabitItem>> getEnabledItems() {
        return repository.getEnabledItems();
    }

    public LiveData<List<HabitItem>> getAllItems() {
        return repository.getAllItems();
    }

    public LiveData<List<HabitRecord>> getSelectedDateRecords() {
        return Transformations.switchMap(selectedDate, repository::getRecordsByDate);
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
    }

    public void upsertRecord(long habitId, long dayStart, boolean completed, String source) {
        repository.upsertRecord(new HabitRecord(habitId, DateUtils.getDayStartTimestamp(dayStart), completed, source,
                System.currentTimeMillis()));
    }

    public void updateItem(HabitItem item) {
        repository.updateItem(item);
    }
}