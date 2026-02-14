package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.repository.DailyLogRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.repository.SleepRecordRepository;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 报表系统 ViewModel
 * 负责聚合训练、饮食、身体数据，支持周/月维度切换
 */
public class ReportViewModel extends AndroidViewModel {

    private DailyLogRepository dailyLogRepository;
    private FoodRecordRepository foodRecordRepository;
    private SleepRecordRepository sleepRecordRepository;
    private UserRepository userRepository;
    private ExecutorService executorService;

    // 训练数据
    private MutableLiveData<Integer> trainingDays = new MutableLiveData<>();
    private MutableLiveData<Integer> totalWorkouts = new MutableLiveData<>();
    private MutableLiveData<String> trainingSuggestion = new MutableLiveData<>();

    // 饮食数据
    private MutableLiveData<Integer> avgCaloriesIntake = new MutableLiveData<>();
    private MutableLiveData<Integer> targetCalories = new MutableLiveData<>();
    private MutableLiveData<String> dietSuggestion = new MutableLiveData<>();

    // 睡眠数据 (NEW)
    private MutableLiveData<Float> avgSleepDuration = new MutableLiveData<>();
    private MutableLiveData<Float> avgSleepQuality = new MutableLiveData<>();

    // 体重模拟数据 (因为暂无历史表)
    private MutableLiveData<List<Float>> weightTrend = new MutableLiveData<>();
    private MutableLiveData<String> weightSuggestion = new MutableLiveData<>();

    public ReportViewModel(@NonNull Application application) {
        super(application);
        dailyLogRepository = new DailyLogRepository(application);
        foodRecordRepository = new FoodRecordRepository(application);
        sleepRecordRepository = new SleepRecordRepository(application);
        userRepository = new UserRepository(application);
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<Integer> getTrainingDays() {
        return trainingDays;
    }

    public LiveData<Integer> getTotalWorkouts() {
        return totalWorkouts;
    }

    public LiveData<String> getTrainingSuggestion() {
        return trainingSuggestion;
    }

    public LiveData<Integer> getAvgCaloriesIntake() {
        return avgCaloriesIntake;
    }

    public LiveData<Integer> getTargetCalories() {
        return targetCalories;
    }

    public LiveData<String> getDietSuggestion() {
        return dietSuggestion;
    }

    public LiveData<List<Float>> getWeightTrend() {
        return weightTrend;
    }

    public LiveData<String> getWeightSuggestion() {
        return weightSuggestion;
    }

    public LiveData<Float> getAvgSleepDuration() {
        return avgSleepDuration;
    }

    public LiveData<Float> getAvgSleepQuality() {
        return avgSleepQuality;
    }

    /**
     * 加载报表数据
     * 
     * @param isMonth true=本月, false=本周
     */
    public void loadReportData(boolean isMonth) {
        executorService.execute(() -> {
            long startTime, endTime;
            Calendar calendar = Calendar.getInstance();

            // 设置时间范围
            if (isMonth) {
                calendar.set(Calendar.HOUR_OF_DAY, 0); // Reset time part for consistency
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
                endTime = weekDates[6] + 24 * 3600 * 1000L;
            }

            // 1. 计算训练数据
            List<DailyLog> allLogs = dailyLogRepository.getAllLogsSync();
            int days = 0;
            int workouts = 0;
            if (allLogs != null) {
                List<String> activeDates = new ArrayList<>();
                for (DailyLog log : allLogs) {
                    if (log.getDate() >= startTime && log.getDate() < endTime && log.isCompleted()) {
                        workouts++;
                        String dateStr = DateUtils.formatDate(log.getDate());
                        if (!activeDates.contains(dateStr)) {
                            activeDates.add(dateStr);
                            days++;
                        }
                    }
                }
            }
            trainingDays.postValue(days);
            totalWorkouts.postValue(workouts);

            // 生成训练建议
            if (days > (isMonth ? 15 : 4)) {
                trainingSuggestion.postValue("🔥 高强度训练周期，注意休息与营养补充。");
            } else if (days > 0) {
                trainingSuggestion.postValue("✨ 保持运动习惯，每一滴汗水都算数！");
            } else {
                trainingSuggestion.postValue("💪 下个周期动起来，身体会感谢努力的你！");
            }

            // 2. 计算饮食数据 (使用真实数据)
            List<FoodRecord> allFoods = foodRecordRepository.getAllRecordsSync();
            int totalCal = 0;
            List<String> foodDates = new ArrayList<>();

            if (allFoods != null) {
                for (FoodRecord food : allFoods) {
                    if (food.getRecordDate() >= startTime && food.getRecordDate() < endTime) {
                        totalCal += food.getCalories();
                        String d = DateUtils.formatDate(food.getRecordDate());
                        if (!foodDates.contains(d)) {
                            foodDates.add(d);
                        }
                    }
                }
            }
            // 计算平均摄入 (如果有记录天数 > 0，则除以天数；否则为 0)
            int avgCal = foodDates.isEmpty() ? 0 : (totalCal / foodDates.size());
            avgCaloriesIntake.postValue(avgCal);

            // 3. 计算睡眠数据 (NEW)
            List<SleepRecord> sleepRecords = sleepRecordRepository.getSleepRecordsByDateRangeSync(startTime, endTime);
            if (sleepRecords != null && !sleepRecords.isEmpty()) {
                long totalDuration = 0;
                int totalQuality = 0;
                for (SleepRecord record : sleepRecords) {
                    totalDuration += record.getDuration();
                    totalQuality += record.getQuality();
                }
                avgSleepDuration.postValue((float) totalDuration / sleepRecords.size() / 3600f); // 转换为小时
                avgSleepQuality.postValue((float) totalQuality / sleepRecords.size());
            } else {
                avgSleepDuration.postValue(0f);
                avgSleepQuality.postValue(0f);
            }

            // 获取 BMR/目标热量
            User user = userRepository.getUserSync();
            int target = 2000;
            float currentWeight = 65f;
            float currentHeight = 175f;
            if (user != null) {
                // 使用用户配置的每日目标 (包含活动系数和目标修正)
                target = user.getDailyCalorieTarget();
                if (target <= 0) {
                    // 如果尚未计算，使用 BMR 公式保底
                    target = (int) (10 * user.getWeight() + 6.25 * user.getHeight() - 5 * user.getAge() + 5);
                    if (user.getGender() == 0)
                        target -= 166;
                }

                targetCalories.postValue(target);
                currentWeight = (float) user.getWeight();
                currentHeight = (float) user.getHeight();

                if (avgCal <= 0) {
                    dietSuggestion.postValue("🥗 暂无饮食记录，建议开启打卡生活。");
                } else {
                    dietSuggestion.postValue(avgCal > target
                            ? "🥗 热量略超标，建议增加有氧运动或控制晚餐。"
                            : "🥗 热量控制良好，保持均衡饮食。");
                }
            } else {
                dietSuggestion.postValue("🥗 完善个人信息后可获取更精准的建议。");
            }

            // 4. 体重趋势 (暂无历史表，仅展示当前体重平直线)
            // 真实场景应查询 WeightRepository
            List<Float> realTrend = new ArrayList<>();
            // 生成 7 个点 (周) 或 30 个点 (月) 的平滑线，以模拟图表占位，但数值为真实当前体重
            // 避免随机波动误导用户
            int pointsInfo = isMonth ? 30 : 7;
            for (int i = 0; i < pointsInfo; i++) {
                realTrend.add(currentWeight);
            }
            weightTrend.postValue(realTrend);

            // BMI
            float bmi = currentWeight / ((currentHeight / 100) * (currentHeight / 100));
            String bmiStatus = bmi < 18.5 ? "偏瘦" : (bmi < 24 ? "正常" : "偏重");
            weightSuggestion.postValue(String.format("BMI %.1f (%s)，暂无历史体重变化。", bmi, bmiStatus));
        });
    }
}
