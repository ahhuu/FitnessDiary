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
    private final MutableLiveData<Float> waistHeightRatio = new MutableLiveData<>(0f);
    private final MutableLiveData<String> whtrZone = new MutableLiveData<>("");
    private final MutableLiveData<Float> ffmiValue = new MutableLiveData<>(0f);
    private final MutableLiveData<String> ffmiZone = new MutableLiveData<>("");
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
    public LiveData<Float> getWaistHeightRatio() { return waistHeightRatio; }
    public LiveData<String> getWhtrZone() { return whtrZone; }
    public LiveData<Float> getFfmiValue() { return ffmiValue; }
    public LiveData<String> getFfmiZone() { return ffmiZone; }

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
            computeWaistHeightRatio();
            computeFFMI();
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
            computeWaistHeightRatio();
            computeFFMI();
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

    private void computeWaistHeightRatio() {
        BodyMeasurement waist = repository.getLatestByTypeSync("WAIST");
        User user = userRepository.getUserSync();
        if (waist != null && user != null && user.getHeight() > 0) {
            float ratio = waist.getValue() / user.getHeight();
            waistHeightRatio.postValue(ratio);
            
            String zone;
            if (ratio < 0.4f) zone = "偏瘦";
            else if (ratio < 0.5f) zone = "健康区间";
            else if (ratio < 0.6f) zone = "警戒超重";
            else zone = "极高风险";
            whtrZone.postValue(zone);
        } else {
            waistHeightRatio.postValue(0f);
            whtrZone.postValue("");
        }
    }

    private void computeFFMI() {
        BodyMeasurement bf = repository.getLatestByTypeSync("BODY_FAT");
        User user = userRepository.getUserSync();
        if (user == null || user.getHeight() <= 0) {
            ffmiValue.postValue(0f);
            ffmiZone.postValue("");
            return;
        }

        float weight = user.getWeight();
        com.cz.fitnessdiary.database.AppDatabase db = com.cz.fitnessdiary.database.AppDatabase.getInstance(getApplication());
        com.cz.fitnessdiary.database.entity.WeightRecord latestW = db.weightRecordDao().getLatestRecordSync();
        if (latestW != null && latestW.getWeight() > 0) {
            weight = latestW.getWeight();
        }

        if (weight <= 0) {
            ffmiValue.postValue(0f);
            ffmiZone.postValue("");
            return;
        }

        float bfVal = 15f;
        if (bf != null) {
            bfVal = bf.getValue();
        }

        float heightMeters = user.getHeight() / 100f;
        float fatFreeMass = weight * (1f - bfVal / 100f);
        float ffmi = fatFreeMass / (heightMeters * heightMeters);
        
        float normalizedFfmi = ffmi + 6.1f * (1.8f - heightMeters);
        ffmiValue.postValue(normalizedFfmi);

        boolean isMale = user.getGender() == 1;
        String zone;
        if (isMale) {
            if (normalizedFfmi < 18f) zone = "偏瘦/低肌肉量";
            else if (normalizedFfmi < 21f) zone = "普通水平";
            else if (normalizedFfmi < 23f) zone = "肌肉发达";
            else if (normalizedFfmi < 26f) zone = "极其强壮";
            else zone = "顶尖水准";
        } else {
            if (normalizedFfmi < 15f) zone = "偏瘦/低肌肉量";
            else if (normalizedFfmi < 17f) zone = "普通水平";
            else if (normalizedFfmi < 19f) zone = "肌肉发达";
            else if (normalizedFfmi < 22f) zone = "极其强壮";
            else zone = "顶尖水准";
        }
        ffmiZone.postValue(zone);
    }

    public interface CorrelationDataCallback {
        void onLoaded(int avgCalories, double avgProtein, int avgBurnedCalories, int avgSteps, int targetVolumeSets);
    }

    public void loadCorrelationData(String measurementType, CorrelationDataCallback callback) {
        new Thread(() -> {
            long dayStart = DateUtils.getDayStartTimestamp(System.currentTimeMillis());
            long start14DaysAgo = dayStart - 13 * 24 * 60 * 60 * 1000L;
            long endToday = dayStart + 24 * 60 * 60 * 1000L;

            com.cz.fitnessdiary.database.AppDatabase db = com.cz.fitnessdiary.database.AppDatabase.getInstance(getApplication());

            int totalCal = 0;
            double totalProtein = 0;
            int recordedDietDays = 0;
            for (int i = 0; i < 14; i++) {
                long dStart = start14DaysAgo + i * 24 * 60 * 60 * 1000L;
                long dEnd = dStart + 24 * 60 * 60 * 1000L;
                Integer cal = db.foodRecordDao().getTotalCaloriesByDateRangeSync(dStart, dEnd);
                if (cal != null && cal > 0) {
                    totalCal += cal;
                }
                List<com.cz.fitnessdiary.database.entity.FoodRecord> foods = db.foodRecordDao().getByDateRangeSync(dStart, dEnd);
                if (foods != null && !foods.isEmpty()) {
                    recordedDietDays++;
                    for (com.cz.fitnessdiary.database.entity.FoodRecord f : foods) {
                        totalProtein += f.getProtein();
                    }
                }
            }
            int avgCal = totalCal / 14;
            double avgProt = totalProtein / 14;

            int totalSteps = 0;
            for (int i = 0; i < 14; i++) {
                long dStart = start14DaysAgo + i * 24 * 60 * 60 * 1000L;
                com.cz.fitnessdiary.database.entity.StepRecord step = db.stepRecordDao().getByDateSync(dStart);
                if (step != null) {
                    totalSteps += step.getSteps();
                }
            }
            int avgSteps = totalSteps / 14;

            List<com.cz.fitnessdiary.database.entity.DailyLog> logs = db.dailyLogDao().getAllLogsSync();
            List<com.cz.fitnessdiary.database.entity.TrainingPlan> plans = db.trainingPlanDao().getAllPlansList();
            java.util.Map<Integer, com.cz.fitnessdiary.database.entity.TrainingPlan> plansMap = new java.util.HashMap<>();
            for (com.cz.fitnessdiary.database.entity.TrainingPlan p : plans) {
                plansMap.put(p.getPlanId(), p);
            }

            float userWeight = 70f;
            com.cz.fitnessdiary.database.entity.WeightRecord latestW = db.weightRecordDao().getLatestRecordSync();
            if (latestW != null && latestW.getWeight() > 0) {
                userWeight = latestW.getWeight();
            } else {
                User u = userRepository.getUserSync();
                if (u != null && u.getWeight() > 0) {
                    userWeight = u.getWeight();
                }
            }

            int totalBurned = 0;
            int targetVolumeSets = 0;
            
            String partKeyword = "";
            if ("CHEST".equals(measurementType)) partKeyword = "胸";
            else if ("WAIST".equals(measurementType)) partKeyword = "腹";
            else if ("HIP".equals(measurementType)) partKeyword = "臀";
            else if ("ARM".equals(measurementType)) partKeyword = "臂";
            else if ("THIGH".equals(measurementType)) partKeyword = "腿";
            else if ("CALF".equals(measurementType)) partKeyword = "小腿";

            if (logs != null) {
                for (com.cz.fitnessdiary.database.entity.DailyLog log : logs) {
                    if (log.getDate() >= start14DaysAgo && log.getDate() < endToday && log.isCompleted()) {
                        com.cz.fitnessdiary.database.entity.TrainingPlan plan = plansMap.get(log.getPlanId());
                        if (plan != null) {
                            int durationSec = log.getDuration() > 0 ? log.getDuration() : plan.getDuration();
                            if (durationSec <= 0) durationSec = 1800;
                            double met = getMetForCategory(plan.getCategory());
                            double cal = met * 3.5 * userWeight * durationSec / (200.0 * 60.0);
                            totalBurned += (int) cal;

                            String category = plan.getCategory();
                            if (!partKeyword.isEmpty() && category != null && category.contains(partKeyword)) {
                                targetVolumeSets += plan.getSets();
                            }
                        }
                    }
                }
            }
            int avgBurned = totalBurned / 14;

            final int fAvgCal = avgCal;
            final double fAvgProt = avgProt;
            final int fAvgBurned = avgBurned;
            final int fAvgSteps = avgSteps;
            final int fTargetSets = targetVolumeSets;

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                callback.onLoaded(fAvgCal, fAvgProt, fAvgBurned, fAvgSteps, fTargetSets);
            });
        }).start();
    }

    private double getMetForCategory(String category) {
        if (category == null) return 4.0;
        String cat = category.toLowerCase();
        if (cat.contains("有氧") || cat.contains("cardio") || cat.contains("跑步") || cat.contains("骑行"))
            return 7.0;
        if (cat.contains("hiit")) return 8.0;
        if (cat.contains("瑜伽") || cat.contains("拉伸") || cat.contains("yoga")) return 2.5;
        if (cat.contains("力量") || cat.contains("strength")) return 3.5;
        return 4.0;
    }
}
