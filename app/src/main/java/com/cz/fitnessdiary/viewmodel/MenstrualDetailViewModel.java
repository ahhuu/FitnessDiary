package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.MenstrualCycle;
import com.cz.fitnessdiary.repository.MenstrualCycleRepository;
import com.cz.fitnessdiary.ui.widget.MenstrualCycleChartView;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MenstrualDetailViewModel extends AndroidViewModel {

    private final MenstrualCycleRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<List<MenstrualCycleChartView.CycleBarData>> cycleBarData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Float> avgCycleLength = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> avgPeriodDuration = new MutableLiveData<>(0f);
    private final MutableLiveData<Long> nextPredictedDate = new MutableLiveData<>(0L);
    private final MutableLiveData<String> regularityDesc = new MutableLiveData<>("");
    private final MutableLiveData<Integer> currentCycleDay = new MutableLiveData<>(0);
    private final MutableLiveData<Map<String, Integer>> symptomFrequency = new MutableLiveData<>(new HashMap<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MenstrualDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new MenstrualCycleRepository(application);
        refreshAnalysis();
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshAnalysis();
    }

    public LiveData<Long> getSelectedDate() { return selectedDate; }

    public LiveData<List<MenstrualCycle>> getAllRecords() {
        return repository.getAllRecords();
    }

    public LiveData<List<MenstrualCycle>> getRecordsByDate(long startTs, long endTs) {
        return repository.getByDateRange(startTs, endTs);
    }

    public LiveData<List<MenstrualCycleChartView.CycleBarData>> getCycleBarData() { return cycleBarData; }
    public LiveData<Float> getAvgCycleLength() { return avgCycleLength; }
    public LiveData<Float> getAvgPeriodDuration() { return avgPeriodDuration; }
    public LiveData<Long> getNextPredictedDate() { return nextPredictedDate; }
    public LiveData<String> getRegularityDesc() { return regularityDesc; }
    public LiveData<Integer> getCurrentCycleDay() { return currentCycleDay; }
    public LiveData<Map<String, Integer>> getSymptomFrequency() { return symptomFrequency; }

    public void addRecord(MenstrualCycle record) {
        executor.execute(() -> {
            repository.insert(record);
            refreshAnalysis();
        });
    }

    public void updateRecord(MenstrualCycle record) {
        executor.execute(() -> {
            repository.update(record);
            refreshAnalysis();
        });
    }

    public void deleteRecord(MenstrualCycle record) {
        executor.execute(() -> {
            repository.delete(record);
            refreshAnalysis();
        });
    }

    public void refreshAnalysis() {
        Long selected = selectedDate.getValue();
        if (selected == null) return;

        executor.execute(() -> {
            List<MenstrualCycle> allRecords = repository.getAllRecordsSync();
            if (allRecords == null || allRecords.isEmpty()) {
                avgCycleLength.postValue(0f);
                avgPeriodDuration.postValue(0f);
                nextPredictedDate.postValue(0L);
                regularityDesc.postValue("暂无数据");
                currentCycleDay.postValue(0);
                return;
            }

            // Build cycle bar data
            List<MenstrualCycleChartView.CycleBarData> barData = new ArrayList<>();
            List<Integer> cycleLengths = new ArrayList<>();
            List<Integer> periodDurations = new ArrayList<>();
            Map<String, Integer> symptomCounts = new HashMap<>();

            for (int i = 0; i < allRecords.size(); i++) {
                MenstrualCycle cycle = allRecords.get(i);
                int duration = cycle.getEndDate() != null
                        ? (int) ((cycle.getEndDate() - cycle.getStartDate()) / (24 * 60 * 60 * 1000))
                        : (int) ((System.currentTimeMillis() - cycle.getStartDate()) / (24 * 60 * 60 * 1000));
                if (duration > 0) {
                    periodDurations.add(duration);
                }

                // Calc cycle length (gap from start of this cycle to start of previous)
                if (i < allRecords.size() - 1) {
                    MenstrualCycle prev = allRecords.get(i + 1);
                    int cycleLen = (int) ((cycle.getStartDate() - prev.getStartDate()) / (24 * 60 * 60 * 1000));
                    if (cycleLen > 0 && cycleLen < 100) {
                        cycleLengths.add(cycleLen);
                    }
                }

                barData.add(0, new MenstrualCycleChartView.CycleBarData(
                        cycle.getStartDate(), duration,
                        cycle.getFlowIntensity() != null ? cycle.getFlowIntensity() : "MEDIUM"));

                // Count symptoms
                if (cycle.getSymptoms() != null && !cycle.getSymptoms().isEmpty()) {
                    for (String s : cycle.getSymptoms().split(",")) {
                        String sym = s.trim();
                        if (!sym.isEmpty()) {
                            symptomCounts.put(sym, symptomCounts.getOrDefault(sym, 0) + 1);
                        }
                    }
                }
            }

            cycleBarData.postValue(barData);

            // Average cycle length
            if (!cycleLengths.isEmpty()) {
                float sum = 0;
                for (int cl : cycleLengths) sum += cl;
                avgCycleLength.postValue(sum / cycleLengths.size());
            }

            // Average period duration
            if (!periodDurations.isEmpty()) {
                float sum = 0;
                for (int pd : periodDurations) sum += pd;
                avgPeriodDuration.postValue(sum / periodDurations.size());
            }

            // Regularity
            if (cycleLengths.size() >= 2) {
                float mean = 0;
                for (int cl : cycleLengths) mean += cl;
                mean /= cycleLengths.size();
                float variance = 0;
                for (int cl : cycleLengths) variance += (cl - mean) * (cl - mean);
                variance /= cycleLengths.size();
                float stdDev = (float) Math.sqrt(variance);
                String desc;
                if (stdDev < 3) desc = "规律";
                else if (stdDev < 7) desc = "较规律";
                else desc = "不规律";
                regularityDesc.postValue(desc);
            }

            // Next predicted date
            if (!cycleLengths.isEmpty() && !allRecords.isEmpty()) {
                MenstrualCycle latest = allRecords.get(0);
                float avgLen = 0;
                int count = Math.min(cycleLengths.size(), 6);
                for (int i = 0; i < count; i++) {
                    avgLen += cycleLengths.get(i);
                }
                avgLen /= count;
                long predicted = latest.getStartDate() + (long) (avgLen * 24 * 60 * 60 * 1000);
                nextPredictedDate.postValue(predicted);
            }

            // Current cycle day
            MenstrualCycle ongoing = repository.getLatestOngoingSync();
            if (ongoing != null) {
                int day = (int) ((System.currentTimeMillis() - ongoing.getStartDate()) / (24 * 60 * 60 * 1000)) + 1;
                currentCycleDay.postValue(day);
            } else if (!allRecords.isEmpty()) {
                MenstrualCycle latest = allRecords.get(0);
                if (latest.getEndDate() != null) {
                    int day = (int) ((System.currentTimeMillis() - latest.getEndDate()) / (24 * 60 * 60 * 1000)) + 1;
                    currentCycleDay.postValue(day);
                }
            }

            symptomFrequency.postValue(symptomCounts);
        });
    }
}
