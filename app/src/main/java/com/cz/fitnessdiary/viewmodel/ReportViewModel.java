package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.repository.DailyLogRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.repository.SleepRecordRepository;
import com.cz.fitnessdiary.repository.StepRecordRepository;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.repository.WaterRecordRepository;
import com.cz.fitnessdiary.repository.WeightRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 报表系统 ViewModel
 * 聚合训练、饮食、体重、睡眠、步数、喝水数据，支持周/月维度切换
 */
public class ReportViewModel extends AndroidViewModel {

    private final DailyLogRepository    dailyLogRepository;
    private final FoodRecordRepository  foodRecordRepository;
    private final SleepRecordRepository sleepRecordRepository;
    private final UserRepository        userRepository;
    private final WeightRecordRepository weightRepository;
    private final WaterRecordRepository  waterRepository;
    private final StepRecordRepository   stepRepository;
    private final ExecutorService        executorService;

    // ---- 原有字段 ----
    private final MutableLiveData<Integer> trainingDays    = new MutableLiveData<>();
    private final MutableLiveData<Integer> totalWorkouts   = new MutableLiveData<>();
    private final MutableLiveData<String>  trainingSuggestion = new MutableLiveData<>();
    private final MutableLiveData<Integer> avgCaloriesIntake  = new MutableLiveData<>();
    private final MutableLiveData<Integer> targetCalories     = new MutableLiveData<>();
    private final MutableLiveData<String>  dietSuggestion     = new MutableLiveData<>();
    private final MutableLiveData<Float>   avgSleepDuration   = new MutableLiveData<>();
    private final MutableLiveData<Float>   avgSleepQuality    = new MutableLiveData<>();
    private final MutableLiveData<List<Float>> weightTrend    = new MutableLiveData<>();
    private final MutableLiveData<String>  weightSuggestion   = new MutableLiveData<>();

    // ---- 新增：每日趋势数据（供图表使用）----
    /** 每日训练次数列表 (index 0 = 最旧) */
    private final MutableLiveData<List<Integer>> dailyTrainingCounts  = new MutableLiveData<>();
    /** 每日饮食热量列表 */
    private final MutableLiveData<List<Integer>> dailyCaloriesList    = new MutableLiveData<>();
    /** 每日睡眠时长（小时）列表 */
    private final MutableLiveData<List<Float>>   dailySleepList       = new MutableLiveData<>();
    /** 每日喝水量（ml）列表 */
    private final MutableLiveData<List<Integer>> dailyWaterList       = new MutableLiveData<>();
    /** 每日步数列表 */
    private final MutableLiveData<List<Integer>> dailyStepList        = new MutableLiveData<>();
    /** X 轴日期标签 (M/d) */
    private final MutableLiveData<List<String>>  xAxisLabels          = new MutableLiveData<>();
    /** 体重历史记录（recent 30）*/
    private final MutableLiveData<List<WeightRecord>> weightHistory   = new MutableLiveData<>();
    /** 每日目标喝水量（ml）*/
    private final MutableLiveData<Integer> targetWater                = new MutableLiveData<>();

    public ReportViewModel(@NonNull Application application) {
        super(application);
        dailyLogRepository   = new DailyLogRepository(application);
        foodRecordRepository = new FoodRecordRepository(application);
        sleepRecordRepository = new SleepRecordRepository(application);
        userRepository       = new UserRepository(application);
        weightRepository     = new WeightRecordRepository(application);
        waterRepository      = new WaterRecordRepository(application);
        stepRepository       = new StepRecordRepository(application);
        executorService      = Executors.newSingleThreadExecutor();
    }

    // ---- Getters (原有) ----
    public LiveData<Integer> getTrainingDays()    { return trainingDays; }
    public LiveData<Integer> getTotalWorkouts()   { return totalWorkouts; }
    public LiveData<String>  getTrainingSuggestion() { return trainingSuggestion; }
    public LiveData<Integer> getAvgCaloriesIntake()  { return avgCaloriesIntake; }
    public LiveData<Integer> getTargetCalories()     { return targetCalories; }
    public LiveData<String>  getDietSuggestion()     { return dietSuggestion; }
    public LiveData<Float>   getAvgSleepDuration()   { return avgSleepDuration; }
    public LiveData<Float>   getAvgSleepQuality()    { return avgSleepQuality; }
    public LiveData<List<Float>> getWeightTrend()    { return weightTrend; }
    public LiveData<String>  getWeightSuggestion()   { return weightSuggestion; }

    // ---- Getters (新增) ----
    public LiveData<List<Integer>> getDailyTrainingCounts() { return dailyTrainingCounts; }
    public LiveData<List<Integer>> getDailyCaloriesList()   { return dailyCaloriesList; }
    public LiveData<List<Float>>   getDailySleepList()      { return dailySleepList; }
    public LiveData<List<Integer>> getDailyWaterList()      { return dailyWaterList; }
    public LiveData<List<Integer>> getDailyStepList()       { return dailyStepList; }
    public LiveData<List<String>>  getXAxisLabels()         { return xAxisLabels; }
    public LiveData<List<WeightRecord>> getWeightHistory()  { return weightHistory; }
    public LiveData<Integer> getTargetWater()               { return targetWater; }

    // -----------------------------------------------------------------------
    /**
     * 加载报表数据（周/月）
     */
    public void loadReportData(boolean isMonth) {
        executorService.execute(() -> {
            long startTime, endTime;
            Calendar calendar = Calendar.getInstance();

            if (isMonth) {
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                startTime = calendar.getTimeInMillis();
                calendar.add(Calendar.MONTH, 1);
                endTime = calendar.getTimeInMillis();
            } else {
                long[] weekDates = DateUtils.getThisWeekDates();
                startTime = weekDates[0];
                endTime   = weekDates[6] + 24 * 3600 * 1000L;
            }

            int days = isMonth ? 30 : 7;

            // ====== 1. 训练数据 ======
            List<DailyLog> allLogs = dailyLogRepository.getAllLogsSync();
            int trainingDaysCnt = 0, workoutsCnt = 0;
            List<String> activeDates = new ArrayList<>();
            List<Integer> dailyTrainList = new ArrayList<>();

            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault());
            List<String> labels = new ArrayList<>();

            for (int i = 0; i < days; i++) {
                Calendar dc = Calendar.getInstance();
                dc.setTimeInMillis(startTime + (long) i * 86400000L);
                dc.set(Calendar.HOUR_OF_DAY, 0); dc.set(Calendar.MINUTE, 0);
                dc.set(Calendar.SECOND, 0); dc.set(Calendar.MILLISECOND, 0);
                long ds = dc.getTimeInMillis(), de = ds + 86400000L;
                labels.add(sdf.format(dc.getTime()));

                int cnt = 0;
                if (allLogs != null) {
                    for (DailyLog log : allLogs) {
                        if (log.getDate() >= startTime && log.getDate() < endTime
                                && log.isCompleted() && i == 0) {
                            // 累计统计（只做一次）
                            String dateStr = DateUtils.formatDate(log.getDate());
                            if (!activeDates.contains(dateStr)) { activeDates.add(dateStr); trainingDaysCnt++; }
                            workoutsCnt++;
                        }
                        if (log.getDate() >= ds && log.getDate() < de && log.isCompleted()) cnt++;
                    }
                }
                dailyTrainList.add(cnt);
            }
            // 重新正确累计（上面循环有问题，单独做一次）
            trainingDaysCnt = 0; workoutsCnt = 0; activeDates.clear();
            if (allLogs != null) {
                for (DailyLog log : allLogs) {
                    if (log.getDate() >= startTime && log.getDate() < endTime && log.isCompleted()) {
                        workoutsCnt++;
                        String ds2 = DateUtils.formatDate(log.getDate());
                        if (!activeDates.contains(ds2)) { activeDates.add(ds2); trainingDaysCnt++; }
                    }
                }
            }
            trainingDays.postValue(trainingDaysCnt);
            totalWorkouts.postValue(workoutsCnt);
            trainingSuggestion.postValue(
                    trainingDaysCnt > (isMonth ? 15 : 4) ? "🔥 高强度训练周期，注意休息！"
                    : trainingDaysCnt > 0 ? "✨ 保持运动习惯，每一滴汗水都算数！"
                    : "💪 开始动起来吧，身体会感谢你！");
            dailyTrainingCounts.postValue(dailyTrainList);
            xAxisLabels.postValue(labels);

            // ====== 2. 饮食数据 ======
            List<FoodRecord> allFoods = foodRecordRepository.getAllRecordsSync();
            List<Integer> calList = new ArrayList<>();
            int totalCal = 0;
            List<String> foodDates = new ArrayList<>();

            for (int i = 0; i < days; i++) {
                Calendar dc = Calendar.getInstance();
                dc.setTimeInMillis(startTime + (long) i * 86400000L);
                dc.set(Calendar.HOUR_OF_DAY, 0); dc.set(Calendar.MINUTE, 0);
                dc.set(Calendar.SECOND, 0); dc.set(Calendar.MILLISECOND, 0);
                long ds = dc.getTimeInMillis(), de = ds + 86400000L;
                int dayCal = 0;
                if (allFoods != null) {
                    for (FoodRecord food : allFoods) {
                        if (food.getRecordDate() >= ds && food.getRecordDate() < de)
                            dayCal += food.getCalories();
                    }
                }
                calList.add(dayCal);
                if (dayCal > 0) {
                    totalCal += dayCal;
                    foodDates.add(String.valueOf(i));
                }
            }
            int avgCal = foodDates.isEmpty() ? 0 : totalCal / foodDates.size();
            avgCaloriesIntake.postValue(avgCal);
            dailyCaloriesList.postValue(calList);

            // ====== 3. 睡眠数据 ======
            List<SleepRecord> sleepRecords =
                    sleepRecordRepository.getSleepRecordsByDateRangeSync(startTime, endTime);
            List<Float> sleepList = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                Calendar dc = Calendar.getInstance();
                dc.setTimeInMillis(startTime + (long) i * 86400000L);
                long ds = getDayStart(dc), de = ds + 86400000L;
                float hrs = 0f;
                if (sleepRecords != null) {
                    for (SleepRecord sr : sleepRecords) {
                        if (sr.getStartTime() >= ds && sr.getStartTime() < de)
                            hrs += sr.getDuration() / 3600f;
                    }
                }
                sleepList.add(hrs);
            }
            dailySleepList.postValue(sleepList);
            float totalSleep = 0; int sleepDays = 0;
            int totalQuality = 0;
            if (sleepRecords != null && !sleepRecords.isEmpty()) {
                for (SleepRecord sr : sleepRecords) {
                    totalSleep += sr.getDuration() / 3600f;
                    totalQuality += sr.getQuality();
                }
                sleepDays = sleepRecords.size();
            }
            avgSleepDuration.postValue(sleepDays > 0 ? totalSleep / sleepDays : 0f);
            avgSleepQuality.postValue(sleepDays > 0 ? (float) totalQuality / sleepDays : 0f);

            // ====== 4. 体重数据 ======
            List<WeightRecord> wList = weightRepository.getRecentRecordsSync(30);
            weightHistory.postValue(wList != null ? wList : new ArrayList<>());
            float curWeight = 65f, curHeight = 175f;

            // ====== 5. 步数数据 ======
            List<Integer> stepList = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                Calendar dc = Calendar.getInstance();
                dc.setTimeInMillis(startTime + (long) i * 86400000L);
                long ds = getDayStart(dc);
                StepRecord sr = stepRepository.getByDateSync(ds);
                stepList.add(sr != null ? sr.getSteps() : 0);
            }
            dailyStepList.postValue(stepList);

            // ====== 6. 喝水数据 ======
            List<WaterRecord> wRecords =
                    waterRepository.getRecordsByDateRangeSync(startTime, endTime);
            List<Integer> waterList = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                Calendar dc = Calendar.getInstance();
                dc.setTimeInMillis(startTime + (long) i * 86400000L);
                long ds = getDayStart(dc), de = ds + 86400000L;
                int ml = 0;
                if (wRecords != null) {
                    for (WaterRecord wr : wRecords)
                        if (wr.getTimestamp() >= ds && wr.getTimestamp() < de)
                            ml += wr.getAmountMl();
                }
                waterList.add(ml);
            }
            dailyWaterList.postValue(waterList);

            // ====== 7. 用户目标 ======
            User user = userRepository.getUserSync();
            int target = 2000;
            if (user != null) {
                curWeight  = (float) user.getWeight();
                curHeight  = (float) user.getHeight();
                target     = user.getDailyCalorieTarget();
                if (target <= 0)
                    target = (int) (10 * curWeight + 6.25 * curHeight - 5 * user.getAge() + 5);
            }
            targetCalories.postValue(target);
            targetWater.postValue(2000); // 默认 2000ml 目标

            dietSuggestion.postValue(avgCal <= 0 ? "🥗 暂无饮食记录"
                    : avgCal > target ? "🥗 热量略超标，建议增加有氧或控制晚餐"
                    : "🥗 热量控制良好，保持均衡饮食");

            float bmi = curWeight / ((curHeight / 100) * (curHeight / 100));
            String bmiStatus = bmi < 18.5 ? "偏瘦" : (bmi < 24 ? "正常" : "偏重");
            weightSuggestion.postValue(String.format("BMI %.1f（%s）", bmi, bmiStatus));
        });
    }

    private long getDayStart(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
