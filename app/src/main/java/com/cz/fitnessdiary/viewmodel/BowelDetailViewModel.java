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
    private final MutableLiveData<Float> constipationRatio = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> normalRatio = new MutableLiveData<>(100f);
    private final MutableLiveData<Float> diarrheaRatio = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> avgDurationSeconds = new MutableLiveData<>(0f);
    private final MutableLiveData<String> colorAlert = new MutableLiveData<>("正常 ✓");
    private final MutableLiveData<String> localAdvice = new MutableLiveData<>("暂无充足数据分析");
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

            // Calculate ratios, durations, warnings, advice
            List<BowelMovement> list30 = repository.getByDateRangeSync(monthStart, monthEnd);
            int durationCount = 0;
            long durationSum = 0;
            int constipationCount = 0;
            int normalCount = 0;
            int diarrheaCount = 0;
            boolean hasDangerColor = false;

            if (list30 != null && !list30.isEmpty()) {
                for (BowelMovement bm : list30) {
                    if (bm.getDurationSeconds() > 0) {
                        durationCount++;
                        durationSum += bm.getDurationSeconds();
                    }
                    int type = bm.getBristolType();
                    if (type <= 2) {
                        constipationCount++;
                    } else if (type <= 5) {
                        normalCount++;
                    } else {
                        diarrheaCount++;
                    }
                    String color = bm.getColor();
                    if ("RED".equals(color) || "BLACK".equals(color) || "WHITE".equals(color)) {
                        hasDangerColor = true;
                    }
                }
            }

            int totalCount = constipationCount + normalCount + diarrheaCount;
            float constiRatio = 0f;
            float normRatio = 100f;
            float diarrRatio = 0f;
            if (totalCount > 0) {
                constiRatio = (constipationCount * 100f) / totalCount;
                normRatio = (normalCount * 100f) / totalCount;
                diarrRatio = (diarrheaCount * 100f) / totalCount;
            }
            constipationRatio.postValue(constiRatio);
            normalRatio.postValue(normRatio);
            diarrheaRatio.postValue(diarrRatio);

            float avgDur = durationCount > 0 ? (float) durationSum / durationCount : 0f;
            avgDurationSeconds.postValue(avgDur);

            String colorAlertStr = hasDangerColor ? "⚠️ 注意：检测到红色/黑色/白色便便，存在消化道出血或胆道梗阻风险，若持续出现请及时就医！" : "正常 ✓";
            colorAlert.postValue(colorAlertStr);

            String advice = "✨ 胃肠小建议：";
            if (totalCount == 0) {
                advice += "暂无充足排便记录，多记录便便特征能获得更精准的健康调理建议哦！";
            } else {
                if (constiRatio > 30f) {
                    advice += "您的便便偏干硬（便秘占比高）。建议每天多喝水（2L以上），增加膳食纤维摄入（多吃燕麦、火龙果、绿色蔬菜），并在清晨空腹喝一杯温水，促进肠道蠕动。";
                } else if (diarrRatio > 30f) {
                    advice += "您的排便偏稀软（腹泻占比高）。建议近期饮食以清淡、易消化为主，避免生冷辛辣。可适当补充益生菌，若腹泻严重或伴有发热请及时就医。";
                } else {
                    advice += "您的肠道非常健康！大部分便便呈现健康的香蕉状或正常形态。请保持良好的作息与均衡饮食！";
                }
            }
            localAdvice.postValue(advice);
        });
    }

    public LiveData<Float> getConstipationRatio() { return constipationRatio; }
    public LiveData<Float> getNormalRatio() { return normalRatio; }
    public LiveData<Float> getDiarrheaRatio() { return diarrheaRatio; }
    public LiveData<Float> getAvgDurationSeconds() { return avgDurationSeconds; }
    public LiveData<String> getColorAlert() { return colorAlert; }
    public LiveData<String> getLocalAdvice() { return localAdvice; }

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
