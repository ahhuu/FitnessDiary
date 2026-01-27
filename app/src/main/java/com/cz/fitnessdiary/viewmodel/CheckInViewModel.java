package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.repository.DailyLogRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 打卡 ViewModel - 2.0 升级版
 * 添加连续打卡天数计算
 */
public class CheckInViewModel extends AndroidViewModel {

    private DailyLogRepository dailyLogRepository;
    private com.cz.fitnessdiary.repository.TrainingPlanRepository trainingPlanRepository;
    private LiveData<List<DailyLog>> allLogs;
    private MutableLiveData<Integer> consecutiveDays = new MutableLiveData<>(0);
    private ExecutorService executorService;

    public CheckInViewModel(@NonNull Application application) {
        super(application);
        dailyLogRepository = new DailyLogRepository(application);
        trainingPlanRepository = new com.cz.fitnessdiary.repository.TrainingPlanRepository(application);
        allLogs = dailyLogRepository.getAllLogs();
        executorService = Executors.newSingleThreadExecutor();

        // 计算连续打卡天数
        calculateConsecutiveDays();
    }

    public LiveData<List<DailyLog>> getAllLogs() {
        return allLogs;
    }

    public MutableLiveData<Integer> getConsecutiveDays() {
        return consecutiveDays;
    }

    /**
     * 获取今日需完成的训练计划 (按排期过滤)
     */
    public LiveData<List<com.cz.fitnessdiary.database.entity.TrainingPlan>> getTodayPlans() {
        return androidx.lifecycle.Transformations.map(trainingPlanRepository.getAllPlans(), allPlans -> {
            List<com.cz.fitnessdiary.database.entity.TrainingPlan> todayPlans = new ArrayList<>();
            if (allPlans == null)
                return todayPlans;

            // 获取今天是周几 (1=周一, 7=周日)
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            int androidDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
            // 转换逻辑: Calendar.MONDAY(2)->1, ... Calendar.SUNDAY(1)->7
            int todayIndex = (androidDayOfWeek == java.util.Calendar.SUNDAY) ? 7 : (androidDayOfWeek - 1);

            for (com.cz.fitnessdiary.database.entity.TrainingPlan plan : allPlans) {
                // 检查排期
                String scheduledDays = plan.getScheduledDays();
                // 默认每天都包含 (null 或 "0" 或 空)
                boolean isScheduled = false;
                if (scheduledDays == null || scheduledDays.isEmpty() || scheduledDays.contains("0")) {
                    isScheduled = true;
                } else {
                    // 检查 "1,3,5" 是否包含 String.valueOf(todayIndex)
                    String[] days = scheduledDays.split(",");
                    for (String day : days) {
                        if (day.trim().equals(String.valueOf(todayIndex))) {
                            isScheduled = true;
                            break;
                        }
                    }
                }

                if (isScheduled) {
                    todayPlans.add(plan);
                }
            }
            return todayPlans;
        });
    }

    /**
     * 添加今日打卡
     */
    public void checkInToday(int planId) {
        long todayStart = DateUtils.getTodayStartTimestamp();
        DailyLog log = new DailyLog(planId, todayStart, false);
        dailyLogRepository.insert(log);

        // 重新计算连续天数
        calculateConsecutiveDays();
    }

    /**
     * 更新打卡完成状态
     */
    public void updateCompletionStatus(int logId, boolean isCompleted) {
        dailyLogRepository.updateCompletionStatus(logId, isCompleted);
        // 状态更新后重新计算全勤
        calculateConsecutiveDays();
    }

    /**
     * 计算连续打卡天数 (2.0全勤逻辑: 需完成当日所有计划)
     */
    private void calculateConsecutiveDays() {
        executorService.execute(() -> {
            List<DailyLog> logs = dailyLogRepository.getAllLogsSync();
            List<com.cz.fitnessdiary.database.entity.TrainingPlan> allPlans = trainingPlanRepository.getAllPlansSync();

            if (logs == null || logs.isEmpty() || allPlans == null) {
                consecutiveDays.postValue(0);
                return;
            }

            // 提取所有有记录的日期
            List<Long> dates = new ArrayList<>();
            for (DailyLog log : logs) {
                long dayStart = DateUtils.getDayStartTimestamp(log.getDate());
                if (!dates.contains(dayStart)) {
                    dates.add(dayStart);
                }
            }
            // 降序排列
            dates.sort((a, b) -> Long.compare(b, a));

            int streak = 0;
            // 获取今天的日期时间戳
            long todayStart = DateUtils.getTodayStartTimestamp();

            // 检查最近的日期是否是今天或昨天 (如果最近一次打卡是前天，则断开了)
            if (dates.isEmpty()) {
                consecutiveDays.postValue(0);
                return;
            }

            long lastRecordDate = dates.get(0);
            // 这里允许今天还没打卡，但昨天打卡了就算连续
            // 如果最近记录早于昨天，从0开始
            if (todayStart - lastRecordDate > 24 * 60 * 60 * 1000L) {
                consecutiveDays.postValue(0);
                return;
            }

            // 遍历每一天检查是否全勤
            for (Long date : dates) {
                if (isFullAttendance(date, logs, allPlans)) {
                    streak++;
                } else {
                    // 如果遇到一天没有全勤，且不是今天(允许今天进行中)，则中断
                    // 但按"连续坚持天数"定义，只要有一天断了就停
                    // 修正逻辑: 必须是连续的每一天都全勤.
                    // 如果今天是"进行中"，不算断，但不计入 streak? 还是计入?
                    // 这里的逻辑比较主观。通常:
                    // 昨天全勤 + 今天全勤 -> streak+1
                    // 昨天全勤 + 今天未全勤 -> streak不变 (还是昨天的)

                    // 简单起见：遇到非全勤日就停止。
                    // 特例：如果是今天且未全勤，不打断，只是不+1?
                    if (date == todayStart) {
                        continue; // 今天没做完不算断，但也还没+1
                    } else {
                        break; // 过去某天没做完，断了
                    }
                }
            }
            consecutiveDays.postValue(streak);
        });
    }

    // 检查某天是否全勤
    private boolean isFullAttendance(long dateStr, List<DailyLog> allLogs,
            List<com.cz.fitnessdiary.database.entity.TrainingPlan> allPlans) {
        // 1. 找出当天应做的计划
        // 注意：这里用"当前排期"去判断"历史日期"是否全勤，是简化的逻辑
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(dateStr);
        int androidDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        int dayIndex = (androidDayOfWeek == java.util.Calendar.SUNDAY) ? 7 : (androidDayOfWeek - 1);

        List<Integer> targetPlanIds = new ArrayList<>();
        for (com.cz.fitnessdiary.database.entity.TrainingPlan plan : allPlans) {
            String scheduledDays = plan.getScheduledDays();
            boolean isScheduled = false;
            if (scheduledDays == null || scheduledDays.isEmpty() || scheduledDays.contains("0")) {
                isScheduled = true;
            } else {
                String[] days = scheduledDays.split(",");
                for (String day : days) {
                    if (day.trim().equals(String.valueOf(dayIndex))) {
                        isScheduled = true;
                        break;
                    }
                }
            }
            if (isScheduled) {
                targetPlanIds.add(plan.getPlanId());
            }
        }

        if (targetPlanIds.isEmpty())
            return false; // Plan 9 Fix: 无计划时不显示已打卡

        // 2. 检查当天日志
        // 必须所有 targetPlanIds 都有对应的 log 且 completed=true
        int completedCount = 0;
        for (DailyLog log : allLogs) {
            if (DateUtils.getDayStartTimestamp(log.getDate()) == dateStr) {
                if (targetPlanIds.contains(log.getPlanId()) && log.isCompleted()) {
                    completedCount++;
                }
            }
        }

        return completedCount >= targetPlanIds.size();
    }

    /**
     * 获取今日的打卡记录
     */
    public LiveData<List<DailyLog>> getTodayLogs() {
        long todayStart = DateUtils.getTodayStartTimestamp();
        long tomorrowStart = DateUtils.getTomorrowStartTimestamp();
        return dailyLogRepository.getLogsByDateRange(todayStart, tomorrowStart);
    }

    /**
     * 获取本周的打卡日期 (仅显示全勤日)
     */
    public void getThisWeekCheckedDates(WeekCheckedCallback callback) {
        executorService.execute(() -> {
            long[] weekDates = DateUtils.getThisWeekDates();
            List<DailyLog> logs = dailyLogRepository.getAllLogsSync();
            List<com.cz.fitnessdiary.database.entity.TrainingPlan> allPlans = trainingPlanRepository.getAllPlansSync();

            boolean[] checkedDays = new boolean[7];
            if (logs != null && allPlans != null) {
                for (int i = 0; i < 7; i++) {
                    long dayStart = weekDates[i];
                    if (isFullAttendance(dayStart, logs, allPlans)) {
                        checkedDays[i] = true;
                    }
                }
            }

            if (callback != null) {
                callback.onResult(checkedDays);
            }
        });
    }

    public interface WeekCheckedCallback {
        void onResult(boolean[] checkedDays);
    }
}
