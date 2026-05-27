package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.repository.WaterRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.List;

public class WaterDetailViewModel extends AndroidViewModel {

    private final WaterRecordRepository repository;
    private final UserRepository userRepository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<Integer> waterTarget = new MutableLiveData<>(2000);

    public WaterDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new WaterRecordRepository(application);
        userRepository = new UserRepository(application);
        loadWaterTarget();
    }

    private void loadWaterTarget() {
        new Thread(() -> {
            User user = userRepository.getUserSync();
            int target = (user != null && user.getDailyWaterTarget() > 0) ? user.getDailyWaterTarget() : 2000;
            waterTarget.postValue(target);
        }).start();
    }

    public LiveData<Integer> getWaterTarget() {
        return waterTarget;
    }

    public int getWaterTargetSync() {
        Integer val = waterTarget.getValue();
        return (val != null && val > 0) ? val : 2000;
    }

    public void updateWaterTarget(int newTarget) {
        waterTarget.postValue(newTarget);
        new Thread(() -> {
            User user = userRepository.getUserSync();
            if (user != null) {
                user.setDailyWaterTarget(newTarget);
                userRepository.update(user);
            }
        }).start();
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
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

    public void addWater(int amount, String note) {
        repository.insert(new WaterRecord(amount, buildRecordTimestampForSelectedDate(), note));
    }

    public void updateWater(WaterRecord record) {
        repository.update(record);
    }

    public void deleteWater(WaterRecord record) {
        repository.delete(record);
    }

    private long buildRecordTimestampForSelectedDate() {
        Long selected = selectedDate.getValue();
        long dayStart = selected == null ? DateUtils.getTodayStartTimestamp() : DateUtils.getDayStartTimestamp(selected);
        long now = System.currentTimeMillis();
        if (DateUtils.isToday(dayStart)) {
            return now;
        }
        return dayStart + 12L * 60L * 60L * 1000L;
    }
}
