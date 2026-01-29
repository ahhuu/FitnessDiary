package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.repository.DailyLogRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 分析页 ViewModel
 * 负责聚合打卡、饮食数据，提供给图表使用
 */
public class AnalysisViewModel extends AndroidViewModel {

    private DailyLogRepository dailyLogRepository;
    private FoodRecordRepository foodRecordRepository;
    private ExecutorService executorService;

    private MutableLiveData<List<Integer>> weeklyTrainingCount = new MutableLiveData<>();
    private MutableLiveData<List<Float>> dailyCalories = new MutableLiveData<>();
    private MutableLiveData<String> insightMessage = new MutableLiveData<>();

    public AnalysisViewModel(@NonNull Application application) {
        super(application);
        dailyLogRepository = new DailyLogRepository(application);
        foodRecordRepository = new FoodRecordRepository(application);
        executorService = Executors.newSingleThreadExecutor();

        loadWeeklyData();
    }

    public LiveData<List<Integer>> getWeeklyTrainingCount() {
        return weeklyTrainingCount;
    }

    public LiveData<List<Float>> getDailyCalories() {
        return dailyCalories;
    }

    public LiveData<String> getInsightMessage() {
        return insightMessage;
    }

    public void loadWeeklyData() {
        executorService.execute(() -> {
            // 1. 获取本周日期范围
            long[] weekDates = DateUtils.getThisWeekDates();

            // 2. 统计每日完成的打卡数
            List<DailyLog> allLogs = dailyLogRepository.getAllLogsSync();
            List<Integer> counts = new ArrayList<>();
            int totalTrainings = 0;

            for (long date : weekDates) {
                int count = 0;
                if (allLogs != null) {
                    for (DailyLog log : allLogs) {
                        if (DateUtils.isSameDay(log.getDate(), date) && log.isCompleted()) {
                            count++;
                        }
                    }
                }
                counts.add(count);
                totalTrainings += count;
            }
            weeklyTrainingCount.postValue(counts);

            // 3. 生成简单的建议
            if (totalTrainings < 3) {
                insightMessage.postValue("教授，本周训练频次较低，建议适当增加有氧或基础训练，保持体能状态。");
            } else if (totalTrainings > 15) {
                insightMessage.postValue("本周训练非常刻苦！请注意休息恢复，避免过度训练。");
            } else {
                insightMessage.postValue("训练节奏控制得很好，继续保持当前的强度！");
            }
        });
    }
}
