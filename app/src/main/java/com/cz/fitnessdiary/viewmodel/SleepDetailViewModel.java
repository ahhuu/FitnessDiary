package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.repository.SleepRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SleepDetailViewModel extends AndroidViewModel {

    private final SleepRecordRepository repository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<List<Float>> weekSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Float>> monthSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Float>> yearSeries = new MutableLiveData<>(new ArrayList<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 睡眠看板 LiveData
    private final MutableLiveData<Double> avgSleepDuration = new MutableLiveData<>(0.0);
    private final MutableLiveData<Float> sufficientRatio = new MutableLiveData<>(0.0f);
    private final MutableLiveData<String> latestBedtime = new MutableLiveData<>("--");
    private final MutableLiveData<String> sleepAdvice = new MutableLiveData<>("正在加载本地睡眠建议...");
    private final MutableLiveData<String> sleepWarning = new MutableLiveData<>("暂无警报");

    public SleepDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new SleepRecordRepository(application);
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshStatsSeries();
    }

    public LiveData<Long> getSelectedDate() {
        return selectedDate;
    }

    public LiveData<List<SleepRecord>> getSelectedDateRecords() {
        return Transformations.switchMap(selectedDate,
                start -> repository.getSleepRecordsByDateRange(start, start + 24L * 60L * 60L * 1000L));
    }

    public LiveData<List<Float>> getWeekSeries() {
        return weekSeries;
    }

    public LiveData<List<Float>> getMonthSeries() {
        return monthSeries;
    }

    public LiveData<List<Float>> getYearSeries() {
        return yearSeries;
    }

    public LiveData<Double> getAvgSleepDuration() { return avgSleepDuration; }
    public LiveData<Float> getSufficientRatio() { return sufficientRatio; }
    public LiveData<String> getLatestBedtime() { return latestBedtime; }
    public LiveData<String> getSleepAdvice() { return sleepAdvice; }
    public LiveData<String> getSleepWarning() { return sleepWarning; }

    public void refreshStatsSeries() {
        Long selected = selectedDate.getValue();
        if (selected == null)
            return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);
        executor.execute(() -> {
            List<Float> week = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                long start = dayStart - i * 24L * 60L * 60L * 1000L;
                long end = start + 24L * 60L * 60L * 1000L;
                List<SleepRecord> list = repository.getSleepRecordsByDateRangeSync(start, end);
                long total = 0;
                for (SleepRecord record : list)
                    total += record.getDuration();
                week.add(total / 3600f);
            }
            weekSeries.postValue(week);

            List<Float> month = new ArrayList<>();
            for (int i = 29; i >= 0; i--) {
                long start = dayStart - i * 24L * 60L * 60L * 1000L;
                long end = start + 24L * 60L * 60L * 1000L;
                List<SleepRecord> list = repository.getSleepRecordsByDateRangeSync(start, end);
                long total = 0;
                for (SleepRecord record : list)
                    total += record.getDuration();
                month.add(total / 3600f);
            }
            monthSeries.postValue(month);

            // 聚合近30天睡眠数据
            long start30 = dayStart - 29L * 24L * 60L * 60L * 1000L;
            List<SleepRecord> allRecords = repository.getSleepRecordsByDateRangeSync(start30, dayStart + 24L * 60L * 60L * 1000L);
            
            double totalDurationHours = 0.0;
            int sufficientDays = 0;
            int lateNights = 0;
            int totalDaysWithRecords = 0;
            String latestTimeStr = "--";
            int maxMinutesVal = -1; // 24小时制折合成分钟，用于比较最晚
            
            // 用 Map 按日期合并每一天的总睡眠时长
            java.util.Map<String, Double> dailyMap = new java.util.HashMap<>();
            java.util.Set<String> dateWithRecords = new java.util.HashSet<>();
            
            java.text.SimpleDateFormat formatDay = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault());
            java.text.SimpleDateFormat formatTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
            
            for (SleepRecord r : allRecords) {
                if (r.getDuration() <= 0) continue;
                
                String dayKey = formatDay.format(new Date(r.getStartTime()));
                dateWithRecords.add(dayKey);
                
                double hrs = r.getDuration() / 3600.0;
                totalDurationHours += hrs;
                
                double currentDaySum = dailyMap.containsKey(dayKey) ? dailyMap.get(dayKey) : 0.0;
                dailyMap.put(dayKey, currentDaySum + hrs);
                
                // 判定是否熬夜与最晚入睡
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTimeInMillis(r.getStartTime());
                int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
                int minute = cal.get(java.util.Calendar.MINUTE);
                
                // 折算时间，以中午 12点（720分钟）为相对起点，凌晨（0点-6点）视作 24小时之后
                int minutesFromMidDay = hour * 60 + minute;
                if (hour < 6) {
                    minutesFromMidDay += 24 * 60; // 凌晨视作次日，加 1440 分钟
                }
                
                // 熬夜定义：晚于 23:30 或凌晨入睡
                if ((hour == 23 && minute >= 30) || (hour < 5)) {
                    lateNights++;
                }
                
                if (minutesFromMidDay > maxMinutesVal) {
                    maxMinutesVal = minutesFromMidDay;
                    latestTimeStr = formatTime.format(new Date(r.getStartTime()));
                }
            }
            
            totalDaysWithRecords = dateWithRecords.size();
            double avgHours = totalDaysWithRecords > 0 ? (totalDurationHours / totalDaysWithRecords) : 0.0;
            
            // 计算达标天数（每天总睡眠在 6-9 小时之间）
            for (double daySum : dailyMap.values()) {
                if (daySum >= 6.0 && daySum <= 9.0) {
                    sufficientDays++;
                }
            }
            
            float suffRatioVal = totalDaysWithRecords > 0 ? (sufficientDays * 100.0f / totalDaysWithRecords) : 0.0f;
            
            // 生成警告和建议
            String warningStr = "作息规律健康 ✓";
            StringBuilder adviceSb = new StringBuilder();
            
            if (totalDaysWithRecords == 0) {
                adviceSb.append("暂无近30天睡眠数据，开始记录您的第一笔睡眠吧！");
            } else {
                float lateNightRatio = lateNights * 100.0f / allRecords.size();
                if (avgHours < 6.0) {
                    warningStr = "均值偏低，睡眠时长严重不足 ⚠️";
                    adviceSb.append("检测到您的睡眠量不足。建议每日睡前提前 30 分钟调暗室内光线，减少咖啡因摄入，并通过热水淋浴或温水泡脚来刺激副交感神经，帮助延长深度睡眠。");
                } else if (lateNightRatio > 30.0f) {
                    warningStr = "熬夜频次偏高，生物钟紊乱 ⚠️";
                    adviceSb.append("您近期的入睡时间较晚。熬夜会导致皮质醇激素分泌紊乱。建议每天尝试提前 15 分钟上床，建立听白噪音或冥想等规律睡眠仪式，主动重塑昼夜生物钟。");
                } else {
                    adviceSb.append("太棒了！您的睡眠习惯非常良好。继续保持规律的起居时间，可以在清晨适度进行户外光照，这能帮助巩固高质量的睡眠规律。");
                }
            }
            
            avgSleepDuration.postValue(avgHours);
            sufficientRatio.postValue(suffRatioVal);
            latestBedtime.postValue(latestTimeStr);
            sleepAdvice.postValue(adviceSb.toString());
            sleepWarning.postValue(warningStr);
        });
    }

    public void addSleepRecord(long startTime, long endTime, int quality, String notes) {
        repository.insert(new SleepRecord(startTime, endTime, quality, notes));
        refreshStatsSeries();
    }

    public void updateSleepRecord(SleepRecord record) {
        repository.update(record);
        refreshStatsSeries();
    }

    public void deleteSleepRecord(SleepRecord record) {
        repository.delete(record);
        refreshStatsSeries();
    }
}
