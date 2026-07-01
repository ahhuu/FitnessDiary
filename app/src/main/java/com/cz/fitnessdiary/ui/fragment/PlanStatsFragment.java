package com.cz.fitnessdiary.ui.fragment;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.databinding.FragmentPlanStatsBinding;
import com.cz.fitnessdiary.utils.DateUtils;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 训练历史累计运动与近期卡路里统计页面
 */
public class PlanStatsFragment extends Fragment {

    private FragmentPlanStatsBinding binding;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlanStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 顶栏返回 -> 回退至日历历史页面
        binding.btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // 初始化柱状图外观样式
        setupBarChartStyle(binding.chartCalorieBurn);

        // 异步统计加载并刷新 UI
        loadStatsData();
    }

    /**
     * 初始化柱状图属性 (清爽低饱和度手账感)
     */
    private void setupBarChartStyle(BarChart barChart) {
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setMaxVisibleValueCount(10);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setScaleEnabled(false);
        barChart.setNoDataText("暂无近期训练数据");
        barChart.setNoDataTextColor(0xFF8A8276);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(0xFF333333);
        xAxis.setTextSize(10f);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0xFFE5E5E5);
        leftAxis.setTextColor(0xFF666666);
        leftAxis.setTextSize(10f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setSpaceTop(25f); // 预留 25% 空间放置重叠

        barChart.getAxisRight().setEnabled(false); // 隐藏右侧轴
    }

    /**
     * 异步从数据库读取并统计近期运动/步数消耗和历史数据
     */
    private void loadStatsData() {
        executorService.execute(() -> {
            Context context = getContext();
            if (context == null) return;
            AppDatabase db = AppDatabase.getInstance(context);

            // 1. 读取所有的打卡记录、训练计划
            List<DailyLog> allLogs = db.dailyLogDao().getAllLogsSync();
            List<TrainingPlan> allPlans = db.trainingPlanDao().getAllPlansList();
            
            // 获取最新体重
            double weightKg = 65.0;
            try {
                WeightRecord latestW = db.weightRecordDao().getLatestRecordSync();
                if (latestW != null && latestW.getWeight() > 0) {
                    weightKg = latestW.getWeight();
                } else {
                    User u = db.userDao().getUserSync();
                    if (u != null && u.getWeight() > 0) {
                        weightKg = u.getWeight();
                    }
                }
            } catch (Exception ignored) {}

            // 2. 统计累计指标
            int totalActivePlans = allPlans.size();
            
            // 累计完成训练天数与本月打卡次数
            Set<String> uniqueDays = new HashSet<>();
            int completedMonthCount = 0;
            
            Calendar currentCal = Calendar.getInstance();
            int thisYear = currentCal.get(Calendar.YEAR);
            int thisMonth = currentCal.get(Calendar.MONTH);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Map<Integer, TrainingPlan> planMap = new HashMap<>();
            for (TrainingPlan plan : allPlans) {
                planMap.put(plan.getPlanId(), plan);
            }

            for (DailyLog log : allLogs) {
                if (log.isCompleted()) {
                    uniqueDays.add(sdf.format(new Date(log.getDate())));
                    
                    // 统计本月打卡次数
                    Calendar logCal = Calendar.getInstance();
                    logCal.setTimeInMillis(log.getDate());
                    if (logCal.get(Calendar.YEAR) == thisYear && logCal.get(Calendar.MONTH) == thisMonth) {
                        completedMonthCount++;
                    }
                }
            }
            int totalTrainingDays = uniqueDays.size();

            // 3. 计算最近 7 天的每日消耗卡路里 (运动 + 步数)
            List<BarEntry> entries = new ArrayList<>();
            List<String> xLabels = new ArrayList<>();
            
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -6); // 从 6 天前开始
            
            SimpleDateFormat labelSdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
            
            for (int i = 0; i < 7; i++) {
                long startTs = getStartOfDay(cal.getTimeInMillis());
                long endTs = startTs + 86400000L;
                
                // 统计该天的运动消耗
                double dayCal = 0;
                for (DailyLog log : allLogs) {
                    if (log.isCompleted() && log.getDate() >= startTs && log.getDate() < endTs) {
                        TrainingPlan plan = planMap.get(log.getPlanId());
                        if (plan != null) {
                            int durationSec = log.getDuration() > 0 ? log.getDuration() : plan.getDuration();
                            if (durationSec <= 0) durationSec = 1800; // 默认30分钟
                            
                            double met = getMetForCategory(plan.getCategory());
                            double burn = met * 3.5 * weightKg * durationSec / (200.0 * 60.0);
                            dayCal += burn;
                        }
                    }
                }
                
                // 统计该天步数消耗
                try {
                    StepRecord step = db.stepRecordDao().getByDateSync(startTs);
                    if (step != null && step.getSteps() > 0) {
                        dayCal += (step.getSteps() * 0.04);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                entries.add(new BarEntry(i, (float) Math.round(dayCal)));
                xLabels.add(labelSdf.format(cal.getTime()));
                
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            final int finalTotalDays = totalTrainingDays;
            final int finalMonthCount = completedMonthCount;
            final int finalActivePlans = totalActivePlans;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding == null) return;
                    
                    binding.tvStatsTotalDays.setText(finalTotalDays + " 天");
                    binding.tvStatsMonthCount.setText(finalMonthCount + " 次");
                    binding.tvStatsActivePlans.setText(finalActivePlans + " 项");
                    
                    // 渲染柱状图
                    BarDataSet dataSet = new BarDataSet(entries, "卡路里消耗");
                    dataSet.setColor(0xFF2E6BB3); // 莫兰迪蓝色系
                    dataSet.setDrawValues(true);
                    dataSet.setValueTextColor(0xFF333333);
                    dataSet.setValueTextSize(9f);
                    dataSet.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                        @Override
                        public String getFormattedValue(float value) {
                            return value > 0 ? ((int) value) + " kcal" : "";
                        }
                    });
                    
                    BarData barData = new BarData(dataSet);
                    barData.setBarWidth(0.5f); // 柱子宽度
                    
                    binding.chartCalorieBurn.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                        @Override
                        public String getFormattedValue(float value) {
                            int idx = (int) value;
                            if (idx >= 0 && idx < xLabels.size()) {
                                return xLabels.get(idx);
                            }
                            return "";
                        }
                    });
                    binding.chartCalorieBurn.setData(barData);
                    binding.chartCalorieBurn.invalidate();
                });
            }
        });
    }

    private long getStartOfDay(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private double getMetForCategory(String category) {
        if (category == null) return 4.0;
        String cat = category.toLowerCase();
        if (cat.contains("有氧") || cat.contains("cardio") || cat.contains("跑步") || cat.contains("骑行"))
            return 7.0;
        if (cat.contains("hiit")) return 8.0;
        if (cat.contains("瑜伽") || cat.contains("拉伸") || cat.contains("yoga")) return 2.5;
        if (cat.contains("力量") || cat.contains("strength")) return 3.5;
        return 4.0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
