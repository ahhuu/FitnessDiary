package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.CustomRecord;
import com.cz.fitnessdiary.database.entity.CustomTracker;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.MedicationRecord;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.repository.HomeDashboardRepository;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.utils.DateUtils;

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

    public LiveData<Integer> getTodayWaterTotal() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> repository.getTodayWaterTotal(start, end)));
    }

    public LiveData<WaterRecord> getLatestWater() {
        return repository.getLatestWater();
    }

    public LiveData<Integer> getTodayMedicationTakenCount() {
        return Transformations.switchMap(dayStart,
                start -> Transformations.switchMap(dayEnd, end -> repository.getTodayMedicationTakenCount(start, end)));
    }

    public LiveData<MedicationRecord> getLatestMedication() {
        return repository.getLatestMedication();
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

    public LiveData<List<HabitItem>> getEnabledHabits() {
        return repository.getEnabledHabits();
    }

    public LiveData<List<HabitRecord>> getSelectedDateHabitRecords() {
        return Transformations.switchMap(dayStart, repository::getHabitRecordsByDate);
    }

    public void upsertHabitRecord(long habitId, long dayStart, boolean completed, String source) {
        repository.upsertHabitRecord(new HabitRecord(habitId, DateUtils.getDayStartTimestamp(dayStart), completed, source,
                System.currentTimeMillis()));
    }

    public void addWeight(float weight, String note) {
        repository.addWeight(new WeightRecord(weight, System.currentTimeMillis(), note));
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
        repository.addWater(new WaterRecord(amountMl, System.currentTimeMillis(), note));
    }

    public void addMedication(String name, String dosage, boolean taken, String note) {
        repository.addMedication(new MedicationRecord(name, dosage, taken, System.currentTimeMillis(), note));
    }

    public void addCustomRecord(long trackerId, Double value, String textValue) {
        repository.addCustomRecord(new CustomRecord(trackerId, value, textValue, System.currentTimeMillis()));
    }

    public void addCustomTracker(String name, String unit) {
        repository.addCustomTracker(new CustomTracker(name, unit, "#4CAF50", true, 0));
    }
}