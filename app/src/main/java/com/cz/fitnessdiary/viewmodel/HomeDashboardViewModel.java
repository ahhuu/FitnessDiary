package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.database.entity.CustomRecord;
import com.cz.fitnessdiary.database.entity.CustomTracker;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.MedicationRecord;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.MoodRecord;
import com.cz.fitnessdiary.repository.HomeDashboardRepository;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import android.content.SharedPreferences;

import java.util.List;

public class HomeDashboardViewModel extends AndroidViewModel {

    private final HomeDashboardRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final LiveData<Long> dayStart;
    private final LiveData<Long> dayEnd;

    public HomeDashboardViewModel(@NonNull Application application) {
        super(application);
        repository = new HomeDashboardRepository(application);

        dayStart = Transformations.map(selectedDate, DateUtils::getDayStartTimestamp);
        dayEnd = Transformations.map(dayStart, start -> start + 24 * 60 * 60 * 1000L);
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
    }

    public LiveData<Long> getSelectedDate() {
        return selectedDate;
    }

    public LiveData<WeightRecord> getLatestWeight() {
        return repository.getLatestWeight();
    }

    public LiveData<WeightRecord> getSelectedDateLatestWeight() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> Transformations
                        .map(repository.getWeightRecordsByDateRange(start, end),
                                list -> (list == null || list.isEmpty()) ? null : list.get(0))));
    }

    public LiveData<Integer> getTodayWaterTotal() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> repository.getTodayWaterTotal(start, end)));
    }

    public LiveData<WaterRecord> getLatestWater() {
        return repository.getLatestWater();
    }

    public LiveData<WaterRecord> getSelectedDateLatestWater() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> Transformations
                        .map(repository.getWaterRecordsByDateRange(start, end),
                                list -> (list == null || list.isEmpty()) ? null : list.get(0))));
    }

    public LiveData<Integer> getTodayMedicationTakenCount() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> repository.getTodayMedicationTakenCount(start, end)));
    }

    public LiveData<Integer> getTodayMedicationTotal() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> repository.getTodayMedicationTotal(start, end)));
    }

    public LiveData<MedicationRecord> getLatestMedication() {
        return repository.getLatestMedication();
    }

    public LiveData<MedicationRecord> getSelectedDateLatestMedication() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> Transformations
                        .map(repository.getMedicationRecordsByDateRange(start, end),
                                list -> (list == null || list.isEmpty()) ? null : list.get(0))));
    }

    public LiveData<List<CustomTracker>> getEnabledTrackers() {
        return repository.getEnabledTrackers();
    }

    public LiveData<Integer> getEnabledTrackerCount() {
        return repository.getEnabledTrackerCount();
    }

    public LiveData<Integer> getTodayCustomRecordCount() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> repository.getTodayCustomRecordCount(start, end)));
    }

    public LiveData<Double> getTodayCustomSum(long trackerId) {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> repository.getTodayCustomSum(trackerId, start, end)));
    }

    public LiveData<CustomRecord> getLatestCustomRecord(long trackerId) {
        return repository.getLatestCustomRecord(trackerId);
    }

    public LiveData<CustomRecord> getSelectedDateLatestCustomRecord(long trackerId) {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> Transformations
                        .map(repository.getCustomRecordsByTrackerAndDateRange(trackerId, start, end),
                                list -> (list == null || list.isEmpty()) ? null : list.get(0))));
    }

    public LiveData<List<HabitItem>> getEnabledHabits() {
        return repository.getEnabledHabits();
    }

    public LiveData<List<HabitRecord>> getSelectedDateHabitRecords() {
        return Transformations.switchMap(dayStart, repository::getHabitRecordsByDate);
    }

    public void upsertHabitRecord(long habitId, long dayStart, boolean completed, String source) {
        repository
                .upsertHabitRecord(new HabitRecord(habitId, DateUtils.getDayStartTimestamp(dayStart), completed, source,
                        System.currentTimeMillis()));
    }

    public void addWeight(float weight, String note) {
        repository.addWeight(new WeightRecord(weight, buildRecordTimestampForSelectedDate(), note));
        UserRepository userRepository = new UserRepository(getApplication());
        UserRepository ur = userRepository;
        var userLive = ur.getUser();
        var user = userLive.getValue();
        if (user != null) {
            user.setWeight(weight);
            ur.update(user);
        }
    }

    public void addWater(int amountMl, String note) {
        repository.addWater(new WaterRecord(amountMl, buildRecordTimestampForSelectedDate(), note));
    }

    public void addMedication(String name, String dosage, boolean taken, String note) {
        repository.addMedication(new MedicationRecord(name, dosage, taken, buildRecordTimestampForSelectedDate(), note));
    }

    public void addBowelMovement(BowelMovement record) {
        repository.addBowelMovement(record);
    }

    public void addCustomRecord(long trackerId, Double value, String textValue) {
        repository.addCustomRecord(new CustomRecord(trackerId, value, textValue, buildRecordTimestampForSelectedDate()));
    }

    public void addCustomTracker(String name, String unit) {
        repository.addCustomTracker(new CustomTracker(name, unit, "#4CAF50", true, 0));
    }

    // ── Body Measurement card data ──

    public LiveData<Integer> getTodayMeasurementCount() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> {
                    MutableLiveData<Integer> result = new MutableLiveData<>();
                    new Thread(() -> result.postValue(repository.getTodayMeasurementCount(start, end))).start();
                    return result;
                }));
    }

    public LiveData<String> getLatestMeasurementSummary() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> {
                    MutableLiveData<String> result = new MutableLiveData<>();
                    new Thread(() -> result.postValue(repository.getLatestMeasurementSummary(start, end))).start();
                    return result;
                }));
    }

    public LiveData<Long> getSelectedDateLatestMeasurementTime() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> {
                    MutableLiveData<Long> result = new MutableLiveData<>();
                    new Thread(() -> result.postValue(repository.getLatestMeasurementTime(start, end))).start();
                    return result;
                }));
    }

    // ── Bowel movement card data ──

    public LiveData<Integer> getTodayBowelCount() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> {
                    MutableLiveData<Integer> result = new MutableLiveData<>();
                    new Thread(() -> result.postValue(repository.getTodayBowelCount(start, end))).start();
                    return result;
                }));
    }

    public LiveData<String> getLatestBowelSummary() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> {
                    MutableLiveData<String> result = new MutableLiveData<>();
                    new Thread(() -> result.postValue(repository.getLatestBowelSummary(start, end))).start();
                    return result;
                }));
    }

    public LiveData<Long> getSelectedDateLatestBowelTime() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> {
                    MutableLiveData<Long> result = new MutableLiveData<>();
                    new Thread(() -> result.postValue(repository.getLatestBowelTime(start, end))).start();
                    return result;
                }));
    }

    // ── Menstrual cycle card data ──

    public LiveData<Integer> getCurrentCycleDay() {
        MutableLiveData<Integer> result = new MutableLiveData<>();
        new Thread(() -> result.postValue(repository.getCurrentCycleDay())).start();
        return result;
    }

    public LiveData<String> getMenstrualSummary() {
        MutableLiveData<String> result = new MutableLiveData<>();
        new Thread(() -> result.postValue(repository.getMenstrualSummary())).start();
        return result;
    }

    public LiveData<Long> getSelectedDateLatestMenstrualTime() {
        MutableLiveData<Long> result = new MutableLiveData<>();
        new Thread(() -> result.postValue(repository.getLatestMenstrualTime())).start();
        return result;
    }

    // ── Step card data ──

    public LiveData<StepRecord> getTodayStep() {
        return Transformations.switchMap(dayStart, date -> repository.getStepByDate(date));
    }

    public void setTodaySteps(int steps, int source) {
        new Thread(() -> {
            Long date = selectedDate.getValue();
            long day = date != null ? DateUtils.getDayStartTimestamp(date) : DateUtils.getTodayStartTimestamp();
            StepRecord existing = repository.getStepByDateSync(day);
            if (existing != null) {
                existing.setSteps(steps);
                existing.setSource(source);
                existing.setCreateTime(System.currentTimeMillis());
                repository.insertOrUpdateStep(existing);
            } else {
                repository.insertOrUpdateStep(
                        new StepRecord(day, steps, source, System.currentTimeMillis()));
            }
        }).start();
    }

    public int getStepTarget() {
        SharedPreferences sp = getApplication().getSharedPreferences(
                "fitness_diary_prefs", android.content.Context.MODE_PRIVATE);
        long today = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
        return sp.getInt("step_target_" + today, sp.getInt("step_target", 8000));
    }

    public void setStepTarget(int target) {
        getApplication().getSharedPreferences("fitness_diary_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putInt("step_target", target).apply();
    }

    public LiveData<Integer> getTodayStepCalories() {
        return Transformations.map(getTodayStep(), step -> {
            if (step == null || step.getSteps() <= 0) return 0;
            return (int) (step.getSteps() * 0.04);
        });
    }

    // ── Mood card data ──

    public LiveData<MoodRecord> getTodayMood() {
        return Transformations.switchMap(dayStart, date -> repository.getMoodByDate(date));
    }

    public void setTodayMood(String moodCode) {
        new Thread(() -> {
            Long date = selectedDate.getValue();
            long day = date != null ? DateUtils.getDayStartTimestamp(date) : DateUtils.getTodayStartTimestamp();
            MoodRecord record = new MoodRecord(day, moodCode, null, System.currentTimeMillis());
            repository.insertOrUpdateMood(record);
        }).start();
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
