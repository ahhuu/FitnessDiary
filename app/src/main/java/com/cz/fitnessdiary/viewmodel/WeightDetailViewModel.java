package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.repository.WeightRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeightDetailViewModel extends AndroidViewModel {

    private final WeightRecordRepository repository;
    private final UserRepository userRepository;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(DateUtils.getTodayStartTimestamp());
    private final MutableLiveData<List<Float>> weekSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Float>> monthSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Float>> yearSeries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Float> bmi = new MutableLiveData<>(0f);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 新增体重数据分析 LiveData
    private final MutableLiveData<Integer> weightGoalType = new MutableLiveData<>(0);
    private final MutableLiveData<Float> weightTrendVal = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> goalProgressPct = new MutableLiveData<>(0f);
    private final MutableLiveData<String> weightAdvice = new MutableLiveData<>("正在分析体重变化规律...");
    private final MutableLiveData<Float> weightBmi = new MutableLiveData<>(0f);

    public WeightDetailViewModel(@NonNull Application application) {
        super(application);
        repository = new WeightRecordRepository(application);
        userRepository = new UserRepository(application);
        ensureLinkedWeightData();
        refreshTrend();
        computeBmi();
    }

    public void setSelectedDate(long ts) {
        selectedDate.setValue(DateUtils.getDayStartTimestamp(ts));
        refreshTrend();
    }

    public LiveData<Long> getSelectedDate() {
        return selectedDate;
    }

    public LiveData<List<WeightRecord>> getRecentRecords() {
        return repository.getAllRecords();
    }

    public LiveData<WeightRecord> getLatestRecord() {
        return repository.getLatestRecord();
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

    public LiveData<Float> getBmi() {
        return bmi;
    }

    public LiveData<Integer> getWeightGoalType() { return weightGoalType; }
    public LiveData<Float> getWeightTrendVal() { return weightTrendVal; }
    public LiveData<Float> getGoalProgressPct() { return goalProgressPct; }
    public LiveData<String> getWeightAdvice() { return weightAdvice; }
    public LiveData<Float> getWeightBmi() { return weightBmi; }

    private void ensureLinkedWeightData() {
        executor.execute(() -> {
            WeightRecord latest = repository.getLatestRecordSync();
            if (latest != null) {
                return;
            }
            User user = userRepository.getUserSync();
            if (user != null && user.getWeight() > 0f) {
                repository.insert(new WeightRecord(user.getWeight(), System.currentTimeMillis(), "历史体重同步"));
            }
        });
    }

    public void refreshTrend() {
        Long selected = selectedDate.getValue();
        if (selected == null)
            return;
        long dayStart = DateUtils.getDayStartTimestamp(selected);
        executor.execute(() -> {
            Calendar selectedCal = Calendar.getInstance();
            selectedCal.setTimeInMillis(dayStart);

            long weekStart = getWeekStart(dayStart);
            weekSeries.postValue(buildDailySeries(weekStart, 7));

            Calendar monthStartCal = (Calendar) selectedCal.clone();
            monthStartCal.set(Calendar.DAY_OF_MONTH, 1);
            monthStartCal.set(Calendar.HOUR_OF_DAY, 0);
            monthStartCal.set(Calendar.MINUTE, 0);
            monthStartCal.set(Calendar.SECOND, 0);
            monthStartCal.set(Calendar.MILLISECOND, 0);
            long monthStart = monthStartCal.getTimeInMillis();
            int daysInMonth = monthStartCal.getActualMaximum(Calendar.DAY_OF_MONTH);
            monthSeries.postValue(buildDailySeries(monthStart, daysInMonth));

            Calendar yearStartCal = (Calendar) monthStartCal.clone();
            yearStartCal.add(Calendar.MONTH, -11);
            yearSeries.postValue(buildMonthlySeries(yearStartCal.getTimeInMillis(), 12));

            // 计算近30天统计指标
            User user = userRepository.getUserSync();
            if (user != null) {
                int gType = user.getGoalType();
                weightGoalType.postValue(gType);

                // 读取最新体重
                WeightRecord latestRec = repository.getLatestRecordSync();
                float currentW = latestRec != null ? latestRec.getWeight() : user.getWeight();

                // 获取近30天记录
                long start30 = dayStart - 29L * 24L * 60L * 60L * 1000L;
                List<WeightRecord> allRecs = repository.getRecordsByDateRangeSync(start30, dayStart + 24L * 60L * 60L * 1000L);
                
                // 计算月度增减
                float trendVal = 0f;
                float firstW = currentW;
                if (allRecs != null && !allRecs.isEmpty()) {
                    firstW = allRecs.get(0).getWeight(); // 30天前首个体重
                    trendVal = currentW - firstW;
                }
                weightTrendVal.postValue(trendVal);

                // 目标体重计算
                float targetW = 0f;
                android.content.SharedPreferences prefs = getApplication().getSharedPreferences("health_score_prefs", android.content.Context.MODE_PRIVATE);
                float customTarget = prefs.getFloat("target_weight_kg", -1f);
                if (customTarget > 0) {
                    targetW = customTarget;
                } else if (user.getHeight() > 0 && user.getWeight() > 0) {
                    com.cz.fitnessdiary.utils.HealthScoreCalculator.UserProfile profile = new com.cz.fitnessdiary.utils.HealthScoreCalculator.UserProfile();
                    profile.weightKg = user.getWeight();
                    profile.heightCm = user.getHeight();
                    profile.goalType = (gType == 0) ? "lose" : ((gType == 1) ? "gain" : "maintain");
                    targetW = com.cz.fitnessdiary.utils.HealthScoreCalculator.computeTargetWeight(profile);
                }

                // 初始体重：如果没有记录，就用 user.getWeight()。
                float initialW = user.getWeight();
                WeightRecord oldestRec = repository.getOldestRecordSync();
                if (oldestRec != null && oldestRec.getWeight() > 0f) {
                    initialW = oldestRec.getWeight();
                }

                // 计算进度
                float progressPct = 0f;
                if (targetW > 0f && initialW > 0f) {
                    if (gType == 0) { // 减脂
                        if (initialW > targetW) {
                            progressPct = (initialW - currentW) / (initialW - targetW) * 100f;
                        } else {
                            progressPct = currentW <= targetW ? 100f : 0f;
                        }
                    } else if (gType == 1) { // 增肌
                        if (targetW > initialW) {
                            progressPct = (currentW - initialW) / (targetW - initialW) * 100f;
                        } else {
                            progressPct = currentW >= targetW ? 100f : 0f;
                        }
                    } else { // 保持
                        float diff = Math.abs(currentW - targetW);
                        progressPct = Math.max(0f, 100f - diff * 20f);
                    }
                }
                if (progressPct < 0f) progressPct = 0f;
                if (progressPct > 100f) progressPct = 100f;
                goalProgressPct.postValue(progressPct);

                // 计算 BMI
                float bmiVal = 0f;
                if (user.getHeight() > 0 && currentW > 0) {
                    float h = user.getHeight() / 100f;
                    bmiVal = currentW / (h * h);
                }
                weightBmi.postValue(bmiVal);

                // 生成本地指导建议
                StringBuilder adviceSb = new StringBuilder();
                if (gType == 0) { // 减脂
                    adviceSb.append("当前健身目标：**减脂**。");
                    if (trendVal < 0) {
                        adviceSb.append("近30天体重呈下降趋势，减重速度适中。请保持每日 300-500 kcal 的卡路里赤字，并配合适量力量训练（抗阻运动）以防止肌肉流失。");
                    } else if (trendVal > 0) {
                        adviceSb.append("近30天体重呈上升趋势。请检查是否有隐性卡路里摄入，建议精确记录饮食，限制精制碳水，增加膳食纤维以增强饱腹感。");
                    } else {
                        adviceSb.append("近30天体重无明显波动。建议微调运动强度或适当降低 100-200 kcal 碳水摄入，以突破瓶颈期。");
                    }
                } else if (gType == 1) { // 增肌
                    adviceSb.append("当前健身目标：**增肌**。");
                    if (trendVal > 0) {
                        adviceSb.append("近30天体重呈稳步增长趋势。请确保每日摄入 1.6-2.0g/kg 体重的优质蛋白质，并增加渐进负荷力量训练，促进骨骼肌合成。");
                    } else if (trendVal < 0) {
                        adviceSb.append("近30天体重呈下降趋势。这会导致肌肉流失，请增加热量盈余（每日多吃 200-300 kcal），确保碳水化合物和健康脂肪摄入充足。");
                    } else {
                        adviceSb.append("近30天体重无波动。建议增加每餐蛋白质分量，或者在运动后补充一餐高碳高蛋白，打破无增长状态。");
                    }
                } else { // 保持
                    adviceSb.append("当前健身目标：**健康保持**。");
                    if (Math.abs(trendVal) <= 1.0f) {
                        adviceSb.append("体重非常稳定（月波动在 1.0kg 内），您的身体代谢维持能力极其优异！请继续保持目前的膳食与日常活动规律。");
                    } else {
                        adviceSb.append("体重波动已超出 1.0kg。请注意规律作息，避免情绪压力引起的暴饮暴食或水分滞留，保持卡路里收支平衡。");
                    }
                }
                weightAdvice.postValue(adviceSb.toString());
            }

            computeBmi();
        });
    }

    private long getWeekStart(long dayStart) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dayStart);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int offset = (dayOfWeek == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dayOfWeek);
        calendar.add(Calendar.DAY_OF_MONTH, offset);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private List<Float> buildDailySeries(long startDay, int dayCount) {
        List<Float> series = new ArrayList<>();
        long rangeEnd = startDay + dayCount * 24L * 60L * 60L * 1000L;
        float carryWeight = resolveInitialWeight(startDay, rangeEnd);

        for (int i = 0; i < dayCount; i++) {
            long start = startDay + i * 24L * 60L * 60L * 1000L;
            long end = start + 24L * 60L * 60L * 1000L;
            List<WeightRecord> list = repository.getRecordsByDateRangeSync(start, end);
            if (!list.isEmpty()) {
                carryWeight = list.get(list.size() - 1).getWeight();
            }
            series.add(carryWeight);
        }
        return series;
    }

    private List<Float> buildMonthlySeries(long startMonth, int monthCount) {
        List<Float> series = new ArrayList<>();
        Calendar rangeEndCalendar = Calendar.getInstance();
        rangeEndCalendar.setTimeInMillis(startMonth);
        rangeEndCalendar.add(Calendar.MONTH, monthCount);
        float carryWeight = resolveInitialWeight(startMonth, rangeEndCalendar.getTimeInMillis());

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startMonth);

        for (int i = 0; i < monthCount; i++) {
            long monthStart = calendar.getTimeInMillis();
            calendar.add(Calendar.MONTH, 1);
            long nextMonthStart = calendar.getTimeInMillis();

            List<WeightRecord> list = repository.getRecordsByDateRangeSync(monthStart, nextMonthStart);
            if (!list.isEmpty()) {
                carryWeight = list.get(list.size() - 1).getWeight();
            }
            series.add(carryWeight);
        }
        return series;
    }

    private float resolveInitialWeight(long rangeStart, long rangeEnd) {
        WeightRecord before = repository.getLatestRecordBeforeSync(rangeStart);
        if (before != null && before.getWeight() > 0f) {
            return before.getWeight();
        }

        List<WeightRecord> inRange = repository.getRecordsByDateRangeSync(rangeStart, rangeEnd);
        if (!inRange.isEmpty()) {
            WeightRecord firstInRange = inRange.get(0);
            if (firstInRange.getWeight() > 0f) {
                return firstInRange.getWeight();
            }
        }

        User user = userRepository.getUserSync();
        if (user != null && user.getWeight() > 0f) {
            return user.getWeight();
        }
        return 0f;
    }

    private void computeBmi() {
        executor.execute(() -> {
            User user = userRepository.getUserSync();
            WeightRecord latest = repository.getLatestRecordSync();
            float heightCm = user == null ? 0f : user.getHeight();
            if (heightCm <= 0f) {
                bmi.postValue(0f);
                return;
            }
            float currentWeight = latest != null ? latest.getWeight() : (user == null ? 0f : user.getWeight());
            if (currentWeight <= 0f) {
                bmi.postValue(0f);
                return;
            }
            float h = heightCm / 100f;
            bmi.postValue(currentWeight / (h * h));
        });
    }

    public void addWeight(float weight, String note) {
        repository.insert(new WeightRecord(weight, System.currentTimeMillis(), note));
        syncUserWeight(weight);
        refreshTrend();
    }

    public void updateWeight(WeightRecord record) {
        repository.update(record);
        syncUserWeight(record.getWeight());
        refreshTrend();
    }

    public void deleteWeight(WeightRecord record) {
        repository.delete(record);
        executor.execute(() -> {
            WeightRecord latest = repository.getLatestRecordSync();
            if (latest != null) {
                syncUserWeight(latest.getWeight());
            }
        });
        refreshTrend();
    }

    private void syncUserWeight(float weight) {
        executor.execute(() -> {
            User user = userRepository.getUserSync();
            if (user != null && weight > 0f) {
                user.setWeight(weight);
                userRepository.update(user);
            }
        });
    }
}
