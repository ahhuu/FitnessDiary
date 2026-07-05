package com.cz.fitnessdiary.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.utils.AnalysisUtils;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.ReportViewModel;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ReportBottomSheetFragment extends BottomSheetDialogFragment {

    private ReportViewModel viewModel;
    private SharedPreferences sp;
    private static final String SP_NAME = "health_report_sp";
    private static final String KEY_LAST_DATE = "last_analysis_date";
    private static final String KEY_LAST_TEXT = "last_analysis_text";

    // Chart colors
    private static final int COLOR_TRAIN  = 0xFF5C8AE6;
    private static final int COLOR_DIET   = 0xFFFF7043;
    private static final int COLOR_WEIGHT = 0xFFAB47BC;
    private static final int COLOR_SLEEP  = 0xFF5C6BC0;
    private static final int COLOR_STEP   = 0xFF26A69A;
    private static final int COLOR_WATER  = 0xFF29B6F6;

    private MaterialButton btnGenerateAnalysis;
    private MaterialCardView cardAnalysisResult;
    private TextView tvAnalysisText;
    private TextView tvTrainingDaysValue;
    private TextView tvCaloriesIntakeValue;
    private TextView tvCaloriesTargetValue;
    private TextView tvWeightBmiValue;
    private TextView tvWeightEmpty;
    private TextView tvSleepAvgValue;
    private TextView tvSleepQualityAvgValue;
    private TextView tvWaterAvg;
    private TextView tvWaterTarget;
    private TextView tvWaterPercent;
    private android.widget.ProgressBar progressWaterCircle;

    private BarChart chartTraining, chartSleep, chartSteps;
    private LineChart chartCalories;

    private boolean isMonthMode = false;
    private List<String> currentLabels = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ReportViewModel.class);
        sp = requireContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);

        initViews(view);
        initCharts();
        setupListeners(view);
        observeData();

        viewModel.loadReportData(false);
    }

    private void initViews(View v) {
        tvTrainingDaysValue    = v.findViewById(R.id.tv_training_days_value);
        tvCaloriesIntakeValue  = v.findViewById(R.id.tv_calories_intake_value);
        tvCaloriesTargetValue  = v.findViewById(R.id.tv_calories_target_value);
        tvSleepAvgValue        = v.findViewById(R.id.tv_sleep_avg_value);
        tvSleepQualityAvgValue = v.findViewById(R.id.tv_sleep_quality_avg_value);
        tvWaterAvg             = v.findViewById(R.id.tv_water_avg);
        tvWaterTarget          = v.findViewById(R.id.tv_water_target);
        tvWaterPercent         = v.findViewById(R.id.tv_water_percent);
        progressWaterCircle    = v.findViewById(R.id.progress_water_circle);
        btnGenerateAnalysis    = v.findViewById(R.id.btn_generate_analysis);
        cardAnalysisResult     = v.findViewById(R.id.card_analysis_result);
        tvAnalysisText         = v.findViewById(R.id.tv_analysis_text);

        chartTraining = v.findViewById(R.id.chart_training);
        chartCalories = v.findViewById(R.id.chart_calories);
        chartSleep    = v.findViewById(R.id.chart_sleep);
        chartSteps    = v.findViewById(R.id.chart_steps);
    }

    private void initCharts() {
        styleBarChart(chartTraining, "暂无训练记录");
        styleLineChart(chartCalories, "暂无饮食记录");
        styleBarChart(chartSleep,     "暂无睡眠记录");
        styleBarChart(chartSteps,     "暂无步数记录");
    }

    private void setupListeners(View view) {
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggle_date_range);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isMonthMode = (checkedId == R.id.btn_month);
                viewModel.loadReportData(isMonthMode);
            }
        });
        btnGenerateAnalysis.setOnClickListener(v -> generateAnalysis());
    }

    private void observeData() {
        // X 轴标签
        viewModel.getXAxisLabels().observe(getViewLifecycleOwner(), labels -> {
            currentLabels = labels != null ? labels : new ArrayList<>();
        });

        // 训练天数
        viewModel.getTrainingDays().observe(getViewLifecycleOwner(), days -> {
            tvTrainingDaysValue.setText(days + " 天");
        });

        // 每日训练次数柱状图
        viewModel.getDailyTrainingCounts().observe(getViewLifecycleOwner(), counts -> {
            if (counts != null && !counts.isEmpty()) {
                renderBarChart(chartTraining, counts, currentLabels, COLOR_TRAIN, null);
            }
        });

        // 饮食平均 & 折线
        viewModel.getAvgCaloriesIntake().observe(getViewLifecycleOwner(), cal -> {
            tvCaloriesIntakeValue.setText(
                    String.format(Locale.getDefault(), "均 %d kcal", cal));
        });
        viewModel.getDailyCaloriesList().observe(getViewLifecycleOwner(), calList -> {
            if (calList != null && !calList.isEmpty()) {
                Integer target = viewModel.getTargetCalories().getValue();
                renderLineChart(chartCalories, toFloatList(calList), currentLabels,
                        COLOR_DIET, target != null ? target.floatValue() : 0f);
            }
        });
        viewModel.getTargetCalories().observe(getViewLifecycleOwner(), target -> {
            tvCaloriesTargetValue.setText("— 虚线为目标 " + target + " kcal");
        });



        // 睡眠柱状图
        viewModel.getAvgSleepDuration().observe(getViewLifecycleOwner(), dur -> {
            tvSleepAvgValue.setText(
                    String.format(Locale.getDefault(), "均 %.1f h", dur));
        });
        viewModel.getAvgSleepQuality().observe(getViewLifecycleOwner(), q -> {
            if (q <= 0) {
                tvSleepQualityAvgValue.setText("平均质量: --");
            } else {
                StringBuilder sb = new StringBuilder("平均质量: ");
                for (int i = 0; i < Math.round(q); i++) sb.append("⭐");
                tvSleepQualityAvgValue.setText(sb.toString());
            }
        });
        viewModel.getDailySleepList().observe(getViewLifecycleOwner(), sleepList -> {
            if (sleepList != null && !sleepList.isEmpty()) {
                // 8小时推荐参考线
                renderBarChartFloat(chartSleep, sleepList, currentLabels, COLOR_SLEEP, 8f);
            }
        });

        // 步数柱状图
        viewModel.getDailyStepList().observe(getViewLifecycleOwner(), stepList -> {
            if (stepList != null && !stepList.isEmpty()) {
                // 10000步参考线
                renderBarChart(chartSteps, stepList, currentLabels, COLOR_STEP, 10000f);
            }
        });

        // 喝水达标率
        viewModel.getDailyWaterList().observe(getViewLifecycleOwner(), waterList -> {
            if (waterList != null && !waterList.isEmpty()) {
                int total = 0;
                for (int w : waterList) total += w;
                int avg = total / waterList.size();
                int target = 2000;
                Integer tgt = viewModel.getTargetWater().getValue();
                if (tgt != null) target = tgt;
                int pct = Math.min((int) (avg * 100.0 / target), 100);
                tvWaterAvg.setText("日均摄入: " + avg + " ml");
                tvWaterTarget.setText("每日目标: " + target + " ml");
                tvWaterPercent.setText(pct + "%");
                progressWaterCircle.setMax(100);
                progressWaterCircle.setProgress(pct);
            }
        });

        // 检查今日是否已分析
        checkAnalysisState();
    }

    // -----------------------------------------------------------------------
    private void renderBarChart(BarChart chart, List<Integer> data,
                                List<String> labels, int color, Float limitVal) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) entries.add(new BarEntry(i, data.get(i)));
        applyBarData(chart, entries, labels, color, limitVal);
    }

    private void renderBarChartFloat(BarChart chart, List<Float> data,
                                     List<String> labels, int color, Float limitVal) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) entries.add(new BarEntry(i, data.get(i)));
        applyBarData(chart, entries, labels, color, limitVal);
    }

    private void applyBarData(BarChart chart, List<BarEntry> entries,
                               List<String> labels, int color, Float limitVal) {
        BarDataSet ds = new BarDataSet(entries, "");
        ds.setColor(color);
        ds.setDrawValues(false);
        ds.setHighlightEnabled(true);
        ds.setHighLightColor(0x44000000);
        BarData data = new BarData(ds);
        data.setBarWidth(0.55f);
        chart.setData(data);

        if (limitVal != null && limitVal > 0) {
            LimitLine ll = new LimitLine(limitVal);
            ll.setLineColor(0xAAFF5252);
            ll.setLineWidth(1f);
            ll.enableDashedLine(8f, 4f, 0f);
            chart.getAxisLeft().addLimitLine(ll);
        }

        if (labels != null && !labels.isEmpty()) {
            int skip = labels.size() > 10 ? 5 : 1;
            String[] disp = new String[labels.size()];
            for (int i = 0; i < labels.size(); i++) disp[i] = i % skip == 0 ? labels.get(i) : "";
            chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(disp));
            chart.getXAxis().setLabelCount(labels.size(), false);
        }
        chart.animateY(600, Easing.EaseOutQuart);
        chart.invalidate();
    }

    private void renderLineChart(LineChart chart, List<Float> data,
                                  List<String> labels, int color, float limitVal) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) entries.add(new Entry(i, data.get(i)));

        LineDataSet ds = new LineDataSet(entries, "");
        ds.setColor(color);
        ds.setCircleColor(color);
        ds.setCircleRadius(3f);
        ds.setLineWidth(2f);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setDrawFilled(true);
        ds.setFillAlpha(30);
        ds.setFillColor(color);
        ds.setHighlightEnabled(true);
        ds.setHighLightColor(0x44000000);
        chart.setData(new LineData(ds));

        if (limitVal > 0) {
            LimitLine ll = new LimitLine(limitVal);
            ll.setLineColor(0xAAFF5252);
            ll.setLineWidth(1f);
            ll.enableDashedLine(8f, 4f, 0f);
            chart.getAxisLeft().addLimitLine(ll);
        }

        if (labels != null && !labels.isEmpty()) {
            int skip = labels.size() > 10 ? 5 : 1;
            String[] disp = new String[labels.size()];
            for (int i = 0; i < labels.size(); i++) disp[i] = i % skip == 0 ? labels.get(i) : "";
            chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(disp));
            chart.getXAxis().setLabelCount(labels.size(), false);
        }
        chart.animateX(700, Easing.EaseInOutQuart);
        chart.invalidate();
    }

    private List<Float> toFloatList(List<Integer> list) {
        List<Float> result = new ArrayList<>();
        for (int i : list) result.add((float) i);
        return result;
    }

    // -----------------------------------------------------------------------
    private void styleBarChart(BarChart c, String noDataText) {
        c.getDescription().setEnabled(false);
        c.setDrawGridBackground(false);
        c.getLegend().setEnabled(false);
        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setDoubleTapToZoomEnabled(false);
        c.getXAxis().setDrawGridLines(false);
        c.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        c.getXAxis().setTextColor(0xFF888888);
        c.getXAxis().setTextSize(9f);
        c.getAxisLeft().setDrawGridLines(true);
        c.getAxisLeft().setGridColor(0x20000000);
        c.getAxisLeft().setTextColor(0xFF888888);
        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisRight().setEnabled(false);
        c.setNoDataText(noDataText);
        c.setNoDataTextColor(0xFF888888);
    }

    private void styleLineChart(LineChart c, String noDataText) {
        c.getDescription().setEnabled(false);
        c.setDrawGridBackground(false);
        c.getLegend().setEnabled(false);
        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setDoubleTapToZoomEnabled(false);
        c.getXAxis().setDrawGridLines(false);
        c.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        c.getXAxis().setTextColor(0xFF888888);
        c.getXAxis().setTextSize(9f);
        c.getAxisLeft().setDrawGridLines(true);
        c.getAxisLeft().setGridColor(0x20000000);
        c.getAxisLeft().setTextColor(0xFF888888);
        c.getAxisRight().setEnabled(false);
        c.setNoDataText(noDataText);
        c.setNoDataTextColor(0xFF888888);
    }

    // -----------------------------------------------------------------------
    private void checkAnalysisState() {
        String today = DateUtils.formatDate(System.currentTimeMillis());
        String lastDate = sp.getString(KEY_LAST_DATE, "");
        if (today.equals(lastDate)) {
            btnGenerateAnalysis.setVisibility(View.GONE);
            cardAnalysisResult.setVisibility(View.VISIBLE);
            tvAnalysisText.setText(sp.getString(KEY_LAST_TEXT, "暂无分析"));
        } else {
            btnGenerateAnalysis.setVisibility(View.VISIBLE);
            cardAnalysisResult.setVisibility(View.GONE);
        }
    }

    private void generateAnalysis() {
        int workoutDays = viewModel.getTrainingDays().getValue() != null
                ? viewModel.getTrainingDays().getValue() : 0;
        int avgCal = viewModel.getAvgCaloriesIntake().getValue() != null
                ? viewModel.getAvgCaloriesIntake().getValue() : 0;
        int targetCal = viewModel.getTargetCalories().getValue() != null
                ? viewModel.getTargetCalories().getValue() : 2000;
        float sleepDur = viewModel.getAvgSleepDuration().getValue() != null
                ? viewModel.getAvgSleepDuration().getValue() : 0f;
        float sleepQ = viewModel.getAvgSleepQuality().getValue() != null
                ? viewModel.getAvgSleepQuality().getValue() : 0f;

        String result = AnalysisUtils.getAnalysisResult(
                workoutDays, avgCal, targetCal, sleepDur, sleepQ, isMonthMode);

        String today = DateUtils.formatDate(System.currentTimeMillis());
        sp.edit().putString(KEY_LAST_DATE, today).putString(KEY_LAST_TEXT, result).apply();

        btnGenerateAnalysis.setVisibility(View.GONE);
        cardAnalysisResult.setVisibility(View.VISIBLE);
        tvAnalysisText.setText(result);
    }
}
