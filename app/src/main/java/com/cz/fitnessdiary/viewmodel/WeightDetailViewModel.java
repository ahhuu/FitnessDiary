package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.repository.WeightRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeightDetailViewModel extends AndroidViewModel {

    private final WeightRecordRepository repository;
    private final UserRepository userRepository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<List<Float>> trendSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Float> bmi = new MutableLiveData<>(0f);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public WeightDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new WeightRecordRepository(application);
        userRepository = new UserRepository(application);
        ensureLinkedWeightData();
        refreshTrend();
        computeBmi();
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshTrend();
    }

    public LiveData<List<WeightRecord>> getRecentRecords() {
        return repository.getAllRecords();
    }

    public LiveData<WeightRecord> getLatestRecord() {
        return repository.getLatestRecord();
    }

    public LiveData<List<Float>> getTrendSeries() {
        return trendSeries;
    }

    public LiveData<Float> getBmi() {
        return bmi;
    }

    private void ensureLinkedWeightData() {
        executor.execute(() -> {
            WeightRecord latest = repository.getLatestRecordSync();
            if (latest != null) {
                return;
            }
            User user = userRepository.getUserSync();
            if (user != null && user.getWeight() > 0f) {
                repository.insert(new WeightRecord(user.getWeight(), System.currentTimeMillis(), "历史体重同步"));
            }
        });
    }

    public void refreshTrend() {
        Long selected = selectedDate.getValue();
        if (selected == null) return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);
        executor.execute(() -> {
            long from = dayStart - 6L * 24L * 60L * 60L * 1000L;
            List<WeightRecord> list = repository.getRecordsByDateRangeSync(from, dayStart + 24L * 60L * 60L * 1000L);
            List<Float> values = new ArrayList<>();
            for (WeightRecord record : list) {
                values.add(record.getWeight());
            }
            if (values.isEmpty()) {
                WeightRecord latest = repository.getLatestRecordSync();
                if (latest != null) {
                    values.add(latest.getWeight());
                } else {
                    User user = userRepository.getUserSync();
                    values.add(user == null ? 0f : user.getWeight());
                }
            }
            trendSeries.postValue(values);
            computeBmi();
        });
    }

    private void computeBmi() {
        executor.execute(() -> {
            User user = userRepository.getUserSync();
            WeightRecord latest = repository.getLatestRecordSync();
            float heightCm = user == null ? 0f : user.getHeight();
            if (heightCm <= 0f) {
                bmi.postValue(0f);
                return;
            }
            float currentWeight = latest != null ? latest.getWeight() : (user == null ? 0f : user.getWeight());
            if (currentWeight <= 0f) {
                bmi.postValue(0f);
                return;
            }
            float h = heightCm / 100f;
            bmi.postValue(currentWeight / (h * h));
        });
    }

    public void addWeight(float weight, String note) {
        repository.insert(new WeightRecord(weight, System.currentTimeMillis(), note));
        syncUserWeight(weight);
        refreshTrend();
    }

    public void updateWeight(WeightRecord record) {
        repository.update(record);
        syncUserWeight(record.getWeight());
        refreshTrend();
    }

    public void deleteWeight(WeightRecord record) {
        repository.delete(record);
        executor.execute(() -> {
            WeightRecord latest = repository.getLatestRecordSync();
            if (latest != null) {
                syncUserWeight(latest.getWeight());
            }
        });
        refreshTrend();
    }

    private void syncUserWeight(float weight) {
        executor.execute(() -> {
            User user = userRepository.getUserSync();
            if (user != null && weight > 0f) {
                user.setWeight(weight);
                userRepository.update(user);
            }
        });
    }
}
