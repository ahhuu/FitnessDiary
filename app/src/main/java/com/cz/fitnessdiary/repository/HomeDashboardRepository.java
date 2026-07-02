package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.dao.BowelMovementDao;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.database.entity.CustomRecord;
import com.cz.fitnessdiary.database.entity.CustomTracker;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.MedicationRecord;
import com.cz.fitnessdiary.database.entity.MenstrualCycle;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.MoodRecord;
import com.cz.fitnessdiary.database.entity.WeightRecord;

import java.util.List;

public class HomeDashboardRepository {

    private final WeightRecordRepository weightRecordRepository;
    private final WaterRecordRepository waterRecordRepository;
    private final MedicationRecordRepository medicationRecordRepository;
    private final CustomTrackerRepository customTrackerRepository;
    private final CustomRecordRepository customRecordRepository;
    private final HabitRepository habitRepository;
    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final BowelMovementRepository bowelMovementRepository;
    private final MenstrualCycleRepository menstrualCycleRepository;
    private final StepRecordRepository stepRecordRepository;
    private final MoodRecordRepository moodRecordRepository;

    public HomeDashboardRepository(Application application) {
        this.weightRecordRepository = new WeightRecordRepository(application);
        this.waterRecordRepository = new WaterRecordRepository(application);
        this.medicationRecordRepository = new MedicationRecordRepository(application);
        this.customTrackerRepository = new CustomTrackerRepository(application);
        this.customRecordRepository = new CustomRecordRepository(application);
        this.habitRepository = new HabitRepository(application);
        this.bodyMeasurementRepository = new BodyMeasurementRepository(application);
        this.bowelMovementRepository = new BowelMovementRepository(application);
        this.menstrualCycleRepository = new MenstrualCycleRepository(application);
        this.stepRecordRepository = new StepRecordRepository(application);
        this.moodRecordRepository = new MoodRecordRepository(application);
    }

    public LiveData<WeightRecord> getLatestWeight() {
        return weightRecordRepository.getLatestRecord();
    }

    public LiveData<List<WeightRecord>> getWeightRecordsByDateRange(long dayStart, long dayEnd) {
        return weightRecordRepository.getRecordsByDateRange(dayStart, dayEnd);
    }

    public LiveData<Integer> getTodayWaterTotal(long dayStart, long dayEnd) {
        return waterRecordRepository.getTotalAmountByDateRange(dayStart, dayEnd);
    }

    public LiveData<WaterRecord> getLatestWater() {
        return waterRecordRepository.getLatestRecord();
    }

    public LiveData<List<WaterRecord>> getWaterRecordsByDateRange(long dayStart, long dayEnd) {
        return waterRecordRepository.getRecordsByDateRange(dayStart, dayEnd);
    }

    public LiveData<Integer> getTodayMedicationTakenCount(long dayStart, long dayEnd) {
        return medicationRecordRepository.getTakenCountByDateRange(dayStart, dayEnd);
    }

    public LiveData<Integer> getTodayMedicationTotal(long dayStart, long dayEnd) {
        return medicationRecordRepository.getTotalDosageByDateRange(dayStart, dayEnd);
    }

    public LiveData<MedicationRecord> getLatestMedication() {
        return medicationRecordRepository.getLatestRecord();
    }

    public LiveData<List<MedicationRecord>> getMedicationRecordsByDateRange(long dayStart, long dayEnd) {
        return medicationRecordRepository.getRecordsByDateRange(dayStart, dayEnd);
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

    public LiveData<List<CustomRecord>> getCustomRecordsByTrackerAndDateRange(long trackerId, long dayStart, long dayEnd) {
        return customRecordRepository.getRecordsByTrackerAndDateRange(trackerId, dayStart, dayEnd);
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

    public void addBowelMovement(BowelMovement record) {
        bowelMovementRepository.insert(record);
    }

    // ── Body Measurement ──

    public List<BodyMeasurement> getMeasurementsByDateRangeSync(long dayStart, long dayEnd) {
        return bodyMeasurementRepository.getByDateRangeSync(dayStart, dayEnd);
    }

    public int getTodayMeasurementCount(long dayStart, long dayEnd) {
        List<BodyMeasurement> list = bodyMeasurementRepository.getByDateRangeSync(dayStart, dayEnd);
        return list != null ? list.size() : 0;
    }

    public String getLatestMeasurementSummary(long dayStart, long dayEnd) {
        List<BodyMeasurement> list = bodyMeasurementRepository.getByDateRangeSync(dayStart, dayEnd);
        if (list == null || list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (BodyMeasurement m : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(com.cz.fitnessdiary.utils.AnalysisUtils.getMeasurementTypeName(m.getMeasurementType()));
            sb.append(" ").append(String.format(java.util.Locale.getDefault(), "%.1f", m.getValue()));
        }
        return sb.toString();
    }

    public Long getLatestMeasurementTime(long dayStart, long dayEnd) {
        List<BodyMeasurement> list = bodyMeasurementRepository.getByDateRangeSync(dayStart, dayEnd);
        if (list == null || list.isEmpty()) return null;
        long max = 0;
        for (BodyMeasurement m : list) {
            if (m.getTimestamp() > max) max = m.getTimestamp();
        }
        return max;
    }

    // ── Bowel Movement ──

    public int getTodayBowelCount(long dayStart, long dayEnd) {
        List<BowelMovement> list = bowelMovementRepository.getByDateRangeSync(dayStart, dayEnd);
        return list != null ? list.size() : 0;
    }

    public BowelMovement getLatestBowelMovement() {
        return bowelMovementRepository.getLatestSync();
    }

    public String getLatestBowelSummary(long dayStart, long dayEnd) {
        BowelMovement latest = bowelMovementRepository.getLatestByDateSync(dayStart, dayEnd);
        if (latest == null) return null;
        return com.cz.fitnessdiary.utils.AnalysisUtils.getBristolTypeName(latest.getBristolType());
    }

    public Long getLatestBowelTime(long dayStart, long dayEnd) {
        BowelMovement latest = bowelMovementRepository.getLatestByDateSync(dayStart, dayEnd);
        return latest != null ? latest.getTimestamp() : null;
    }

    // ── Menstrual Cycle ──

    public MenstrualCycle getLatestMenstrualCycle() {
        return menstrualCycleRepository.getLatestSync();
    }

    public int getCurrentCycleDay() {
        MenstrualCycle ongoing = menstrualCycleRepository.getLatestOngoingSync();
        if (ongoing != null) {
            return (int) ((System.currentTimeMillis() - ongoing.getStartDate()) / (24 * 60 * 60 * 1000)) + 1;
        }
        MenstrualCycle latest = menstrualCycleRepository.getLatestSync();
        if (latest != null && latest.getEndDate() != null) {
            return (int) ((System.currentTimeMillis() - latest.getEndDate()) / (24 * 60 * 60 * 1000)) + 1;
        }
        return 0;
    }

    public String getMenstrualSummary() {
        MenstrualCycle ongoing = menstrualCycleRepository.getLatestOngoingSync();
        if (ongoing != null) {
            int day = (int) ((System.currentTimeMillis() - ongoing.getStartDate()) / (24 * 60 * 60 * 1000)) + 1;
            return "经期第" + day + "天";
        }
        MenstrualCycle latest = menstrualCycleRepository.getLatestSync();
        if (latest != null) {
            long ref = latest.getEndDate() != null ? latest.getEndDate() : latest.getStartDate();
            int day = (int) ((System.currentTimeMillis() - ref) / (24 * 60 * 60 * 1000)) + 1;
            return "距上次" + day + "天";
        }
        return "点击查看经期明细";
    }

    public Long getLatestMenstrualTime() {
        MenstrualCycle latest = menstrualCycleRepository.getLatestSync();
        return latest != null ? latest.getTimestamp() : null;
    }

    // ── Step Record ──

    public LiveData<StepRecord> getStepByDate(long date) {
        return stepRecordRepository.getByDate(date);
    }

    public void insertOrUpdateStep(StepRecord record) {
        stepRecordRepository.insertOrUpdate(record);
    }

    public StepRecord getStepByDateSync(long date) {
        return stepRecordRepository.getByDateSync(date);
    }

    // ── Mood Record ──

    public LiveData<MoodRecord> getMoodByDate(long date) {
        return moodRecordRepository.getByDate(date);
    }

    public void insertOrUpdateMood(MoodRecord record) {
        moodRecordRepository.insertOrUpdate(record);
    }

    public MoodRecord getMoodByDateSync(long date) {
        return moodRecordRepository.getByDateSync(date);
    }
}
