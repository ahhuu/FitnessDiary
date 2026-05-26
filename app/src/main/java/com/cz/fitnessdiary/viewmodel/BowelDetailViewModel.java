package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.dao.BowelMovementDao;
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.repository.BowelMovementRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BowelDetailViewModel extends AndroidViewModel {

    private final BowelMovementRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<Map<Integer, Integer>> bristolDistribution = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Map<String, Integer>> colorDistribution = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Integer> dailyCount = new MutableLiveData<>(0);
    private final MutableLiveData<Float> avgBristol = new MutableLiveData<>(0f);
    private final MutableLiveData<Integer> digestiveHealthScore = new MutableLiveData<>(0);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BowelDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new BowelMovementRepository(application);
        refreshStats();
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshStats();
    }

    public LiveData<Long> getSelectedDate() { return selectedDate; }

    public LiveData<List<BowelMovement>> getAllRecords() {
        return repository.getAllRecords();
    }

    public LiveData<List<BowelMovement>> getRecordsByDate(long startTs, long endTs) {
        return repository.getByDateRange(startTs, endTs);
    }

    public LiveData<Map<Integer, Integer>> getBristolDistribution() { return bristolDistribution; }
    public LiveData<Map<String, Integer>> getColorDistribution() { return colorDistribution; }
    public LiveData<Integer> getDailyCount() { return dailyCount; }
    public LiveData<Float> getAvgBristol() { return avgBristol; }
    public LiveData<Integer> getDigestiveHealthScore() { return digestiveHealthScore; }

    public void addRecord(BowelMovement record) {
        executor.execute(() -> {
            repository.insert(record);
            refreshStats();
        });
    }

    public void updateRecord(BowelMovement record) {
        executor.execute(() -> {
            repository.update(record);
            refreshStats();
        });
    }

    public void deleteRecord(BowelMovement record) {
        executor.execute(() -> {
            repository.delete(record);
            refreshStats();
        });
    }

    public void refreshStats() {
        Long selected = selectedDate.getValue();
        if (selected == null) return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);

        executor.execute(() -> {
            // Month range for distribution
            long monthEnd = dayStart + 30L * 24 * 60 * 60 * 1000;
            long monthStart = dayStart - 30L * 24 * 60 * 60 * 1000;

            // Bristol distribution
            List<BowelMovementDao.BristolCount> bristolCounts =
                    repository.getBristolDistributionSync(monthStart, monthEnd);
            Map<Integer, Integer> bristolMap = new HashMap<>();
            int total = 0;
            float weightedSum = 0;
            for (BowelMovementDao.BristolCount bc : bristolCounts) {
                bristolMap.put(bc.bristolType, bc.count);
                total += bc.count;
                weightedSum += bc.bristolType * bc.count;
            }
            bristolDistribution.postValue(bristolMap);

            // Color distribution
            List<BowelMovementDao.ColorCount> colorCounts =
                    repository.getColorDistributionSync(monthStart, monthEnd);
            Map<String, Integer> colorMap = new HashMap<>();
            for (BowelMovementDao.ColorCount cc : colorCounts) {
                colorMap.put(cc.color, cc.count);
            }
            colorDistribution.postValue(colorMap);

            // Today's count
            long dayEnd = dayStart + 24L * 60 * 60 * 1000;
            List<BowelMovement> todayRecords = repository.getByDateRangeSync(dayStart, dayEnd);
            dailyCount.postValue(todayRecords != null ? todayRecords.size() : 0);

            // Average Bristol and health score
            if (total > 0) {
                avgBristol.postValue(weightedSum / total);
                int score = computeDigestiveHealth(bristolMap, todayRecords);
                digestiveHealthScore.postValue(score);
            } else {
                avgBristol.postValue(0f);
                digestiveHealthScore.postValue(0);
            }
        });
    }

    private int computeDigestiveHealth(Map<Integer, Integer> bristolMap, List<BowelMovement> todayRecords) {
        int score = 70; // base score
        int total = 0;
        for (Map.Entry<Integer, Integer> e : bristolMap.entrySet()) {
            int count = e.getValue();
            total += count;
            switch (e.getKey()) {
                case 3:
                case 4:
                    score += count * 10;
                    break;
                case 2:
                case 5:
                    score -= count * 2;
                    break;
                case 1:
                case 6:
                    score -= count * 5;
                    break;
                case 7:
                    score -= count * 10;
                    break;
            }
        }
        if (total > 0) score = score / Math.max(1, total / 5);

        // Penalty for difficult/urgent feelings in recent records
        if (todayRecords != null) {
            for (BowelMovement bm : todayRecords) {
                if ("DIFFICULT".equals(bm.getProcessFeeling())) score -= 5;
                if ("URGENT".equals(bm.getProcessFeeling())) score -= 3;
                if ("NORMAL".equals(bm.getProcessFeeling())) score += 2;
            }
        }

        return Math.max(0, Math.min(100, score));
    }
}
