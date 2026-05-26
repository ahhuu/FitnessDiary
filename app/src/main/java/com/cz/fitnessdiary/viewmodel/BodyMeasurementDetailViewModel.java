package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.repository.BodyMeasurementRepository;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BodyMeasurementDetailViewModel extends AndroidViewModel {

    private final BodyMeasurementRepository repository;
    private final UserRepository userRepository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<String> selectedType = new MutableLiveData<>("BODY_FAT");
    private final MutableLiveData<List<Float>> weekSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Float>> monthSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Float>> yearSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, Float>> latestValues = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Float> waistHipRatio = new MutableLiveData<>(0f);
    private final MutableLiveData<String> bodyFatZone = new MutableLiveData<>("");
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String[] ALL_TYPES = {"BODY_FAT", "CHEST", "WAIST", "HIP", "ARM", "THIGH", "CALF"};

    public BodyMeasurementDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new BodyMeasurementRepository(application);
        userRepository = new UserRepository(application);
        refreshTrend();
        refreshLatestValues();
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshTrend();
    }

    public LiveData<Long> getSelectedDate() { return selectedDate; }

    public void setSelectedType(String type) {
        selectedType.setValue(type);
        refreshTrend();
    }

    public LiveData<String> getSelectedType() { return selectedType; }

    public LiveData<List<BodyMeasurement>> getRecordsByType(String type) {
        return repository.getAllByType(type);
    }

    public LiveData<List<BodyMeasurement>> getRecordsByDate(long startTs, long endTs) {
        return repository.getByDateRange(startTs, endTs);
    }

    public LiveData<List<Float>> getWeekSeries() { return weekSeries; }
    public LiveData<List<Float>> getMonthSeries() { return monthSeries; }
    public LiveData<List<Float>> getYearSeries() { return yearSeries; }
    public LiveData<Map<String, Float>> getLatestValues() { return latestValues; }
    public LiveData<Float> getWaistHipRatio() { return waistHipRatio; }
    public LiveData<String> getBodyFatZone() { return bodyFatZone; }

    public void addRecord(String type, float value, String unit, String note) {
        executor.execute(() -> {
            repository.insert(new BodyMeasurement(type, value, unit, System.currentTimeMillis(), note));
            refreshTrend();
            refreshLatestValues();
        });
    }

    public void updateRecord(BodyMeasurement record) {
        executor.execute(() -> {
            repository.update(record);
            refreshTrend();
            refreshLatestValues();
        });
    }

    public void deleteRecord(BodyMeasurement record) {
        executor.execute(() -> {
            repository.delete(record);
            refreshTrend();
            refreshLatestValues();
        });
    }

    public void refreshTrend() {
        Long selected = selectedDate.getValue();
        String type = selectedType.getValue();
        if (selected == null || type == null) return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);
        executor.execute(() -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dayStart);

            long weekStart = getWeekStart(dayStart);
            weekSeries.postValue(buildDailySeries(type, weekStart, 7));

            cal.set(Calendar.DAY_OF_MONTH, 1);
            long monthStart = cal.getTimeInMillis();
            int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            monthSeries.postValue(buildDailySeries(type, monthStart, daysInMonth));

            cal.set(Calendar.MONTH, 0);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            long yearStart = cal.getTimeInMillis();
            yearSeries.postValue(buildMonthlySeries(type, yearStart, 12));

            computeWaistHipRatio();
            computeBodyFatZone();
        });
    }

    private void refreshLatestValues() {
        executor.execute(() -> {
            Map<String, Float> map = new HashMap<>();
            for (String t : ALL_TYPES) {
                BodyMeasurement latest = repository.getLatestByTypeSync(t);
                if (latest != null) map.put(t, latest.getValue());
            }
            latestValues.postValue(map);
            computeWaistHipRatio();
            computeBodyFatZone();
        });
    }

    private void computeWaistHipRatio() {
        BodyMeasurement waist = repository.getLatestByTypeSync("WAIST");
        BodyMeasurement hip = repository.getLatestByTypeSync("HIP");
        if (waist != null && hip != null && hip.getValue() > 0) {
            waistHipRatio.postValue(waist.getValue() / hip.getValue());
        }
    }

    private void computeBodyFatZone() {
        BodyMeasurement bf = repository.getLatestByTypeSync("BODY_FAT");
        if (bf == null) {
            bodyFatZone.postValue("");
            return;
        }
        User user = userRepository.getUserSync();
        boolean isMale = user != null && user.getGender() == 1;
        float val = bf.getValue();
        String zone;
        if (isMale) {
            if (val < 6) zone = "必需脂肪";
            else if (val < 14) zone = "运动员";
            else if (val < 18) zone = "健康";
            else if (val < 25) zone = "正常偏高";
            else zone = "肥胖";
        } else {
            if (val < 14) zone = "必需脂肪";
            else if (val < 21) zone = "运动员";
            else if (val < 25) zone = "健康";
            else if (val < 32) zone = "正常偏高";
            else zone = "肥胖";
        }
        bodyFatZone.postValue(zone);
    }

    private List<Float> buildDailySeries(String type, long startDate, int days) {
        List<Float> series = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < days; i++) {
            cal.setTimeInMillis(startDate);
            cal.add(Calendar.DAY_OF_MONTH, i);
            long dayStart = cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_MONTH, 1);
            long dayEnd = cal.getTimeInMillis();
            List<BodyMeasurement> records = repository.getByTypeAndDateRangeSync(type, dayStart, dayEnd);
            if (!records.isEmpty()) {
                series.add(records.get(records.size() - 1).getValue());
            } else {
                series.add(null);
            }
        }
        return series;
    }

    private List<Float> buildMonthlySeries(String type, long yearStart, int months) {
        List<Float> series = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < months; i++) {
            cal.setTimeInMillis(yearStart);
            cal.add(Calendar.MONTH, i);
            long monthStart = cal.getTimeInMillis();
            cal.add(Calendar.MONTH, 1);
            long monthEnd = cal.getTimeInMillis();
            List<BodyMeasurement> records = repository.getByTypeAndDateRangeSync(type, monthStart, monthEnd);
            if (!records.isEmpty()) {
                float sum = 0;
                for (BodyMeasurement r : records) sum += r.getValue();
                series.add(sum / records.size());
            } else {
                series.add(null);
            }
        }
        return series;
    }

    private long getWeekStart(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
