package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.entity.CustomRecord;
import com.cz.fitnessdiary.database.entity.CustomTracker;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.MedicationRecord;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.database.entity.WeightRecord;

import java.util.List;

public class HomeDashboardRepository {

    private final WeightRecordRepository weightRecordRepository;
    private final WaterRecordRepository waterRecordRepository;
    private final MedicationRecordRepository medicationRecordRepository;
    private final CustomTrackerRepository customTrackerRepository;
    private final CustomRecordRepository customRecordRepository;
    private final HabitRepository habitRepository;

    public HomeDashboardRepository(Application application) {
        this.weightRecordRepository = new WeightRecordRepository(application);
        this.waterRecordRepository = new WaterRecordRepository(application);
        this.medicationRecordRepository = new MedicationRecordRepository(application);
        this.customTrackerRepository = new CustomTrackerRepository(application);
        this.customRecordRepository = new CustomRecordRepository(application);
        this.habitRepository = new HabitRepository(application);
    }

    public LiveData<WeightRecord> getLatestWeight() {
        return weightRecordRepository.getLatestRecord();
    }

    public LiveData<Integer> getTodayWaterTotal(long dayStart, long dayEnd) {
        return waterRecordRepository.getTotalAmountByDateRange(dayStart, dayEnd);
    }

    public LiveData<WaterRecord> getLatestWater() {
        return waterRecordRepository.getLatestRecord();
    }

    public LiveData<Integer> getTodayMedicationTakenCount(long dayStart, long dayEnd) {
        return medicationRecordRepository.getTakenCountByDateRange(dayStart, dayEnd);
    }

    public LiveData<MedicationRecord> getLatestMedication() {
        return medicationRecordRepository.getLatestRecord();
    }

    public LiveData<List<CustomTracker>> getEnabledTrackers() {
        return customTrackerRepository.getEnabledTrackers();
    }

    public LiveData<Integer> getEnabledTrackerCount() {
        return customTrackerRepository.getEnabledTrackerCount();
    }

    public LiveData<Double> getTodayCustomSum(long trackerId, long dayStart, long dayEnd) {
        return customRecordRepository.getSumValueByTrackerAndDateRange(trackerId, dayStart, dayEnd);
    }

    public LiveData<CustomRecord> getLatestCustomRecord(long trackerId) {
        return customRecordRepository.getLatestRecordByTracker(trackerId);
    }

    public LiveData<Integer> getTodayCustomRecordCount(long dayStart, long dayEnd) {
        return customRecordRepository.getRecordCountByDateRange(dayStart, dayEnd);
    }

    public LiveData<List<HabitItem>> getEnabledHabits() {
        return habitRepository.getEnabledItems();
    }

    public LiveData<List<HabitRecord>> getHabitRecordsByDate(long dayStart) {
        return habitRepository.getRecordsByDate(dayStart);
    }

    public void upsertHabitRecord(HabitRecord record) {
        habitRepository.upsertRecord(record);
    }

    public void addWeight(WeightRecord record) {
        weightRecordRepository.insert(record);
    }

    public void addWater(WaterRecord record) {
        waterRecordRepository.insert(record);
    }

    public void addMedication(MedicationRecord record) {
        medicationRecordRepository.insert(record);
    }

    public void addCustomRecord(CustomRecord record) {
        customRecordRepository.insert(record);
    }

    public void addCustomTracker(CustomTracker tracker) {
        customTrackerRepository.insert(tracker);
    }
}