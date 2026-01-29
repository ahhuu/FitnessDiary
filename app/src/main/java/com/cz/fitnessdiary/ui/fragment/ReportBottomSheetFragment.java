package com.cz.fitnessdiary.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.utils.AnalysisUtils;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.ReportViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;

public class ReportBottomSheetFragment extends BottomSheetDialogFragment {

    private ReportViewModel viewModel;
    private SharedPreferences sp;
    private static final String SP_NAME = "health_report_sp";
    private static final String KEY_LAST_DATE = "last_analysis_date";
    private static final String KEY_LAST_TEXT = "last_analysis_text";

    // UI References
    private TextView tvTrainingDaysValue;
    private ProgressBar progressTrainingDays;

    private ProgressBar progressCaloriesCircle;
    private TextView tvCaloriesPercent, tvCaloriesIntakeValue, tvCaloriesTargetValue;

    private TextView tvWeightBmiValue;

    private MaterialButton btnGenerateAnalysis;
    private MaterialCardView cardAnalysisResult;
    private TextView tvAnalysisText;

    private boolean isMonthMode = false;

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
        setupListeners(view);
        observeData();

        // 默认加载本周数据
        viewModel.loadReportData(false);
    }

    private void initViews(View view) {
        tvTrainingDaysValue = view.findViewById(R.id.tv_training_days_value);
        progressTrainingDays = view.findViewById(R.id.progress_training_days);

        progressCaloriesCircle = view.findViewById(R.id.progress_calories_circle);
        tvCaloriesPercent = view.findViewById(R.id.tv_calories_percent);
        tvCaloriesIntakeValue = view.findViewById(R.id.tv_calories_intake_value);
        tvCaloriesTargetValue = view.findViewById(R.id.tv_calories_target_value);

        tvWeightBmiValue = view.findViewById(R.id.tv_weight_bmi_value);

        btnGenerateAnalysis = view.findViewById(R.id.btn_generate_analysis);
        cardAnalysisResult = view.findViewById(R.id.card_analysis_result);
        tvAnalysisText = view.findViewById(R.id.tv_analysis_text);
    }

    private void setupListeners(View view) {
        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggle_date_range);
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isMonthMode = (checkedId == R.id.btn_month);
                viewModel.loadReportData(isMonthMode);
            }
        });

        btnGenerateAnalysis.setOnClickListener(v -> generateAnalysis());
    }

    private void observeData() {
        // Training
        viewModel.getTrainingDays().observe(getViewLifecycleOwner(), days -> {
            tvTrainingDaysValue.setText(days + " 天");
            progressTrainingDays.setMax(isMonthMode ? 30 : 7);
            progressTrainingDays.setProgress(days);
        });

        // Diet
        viewModel.getAvgCaloriesIntake().observe(getViewLifecycleOwner(), cal -> {
            tvCaloriesIntakeValue.setText("摄入: " + cal);
            updateDietCircle();
        });

        viewModel.getTargetCalories().observe(getViewLifecycleOwner(), target -> {
            tvCaloriesTargetValue.setText("目标: " + target);
            updateDietCircle();
        });

        // Weight & BMI
        viewModel.getWeightSuggestion().observe(getViewLifecycleOwner(), s -> {
            tvWeightBmiValue.setText(s);
        });
    }

    private void updateDietCircle() {
        Integer intake = viewModel.getAvgCaloriesIntake().getValue();
        Integer target = viewModel.getTargetCalories().getValue();
        if (intake != null && target != null && target > 0) {
            int percent = (int) (intake * 100.0 / target);
            tvCaloriesPercent.setText(percent + "%");
            progressCaloriesCircle.setProgress(Math.min(percent, 100));
        }
    }

    private void checkAnalysisState() {
        String today = DateUtils.formatDate(System.currentTimeMillis());
        String lastDate = sp.getString(KEY_LAST_DATE, "");

        if (today.equals(lastDate)) {
            // 今天已分析过
            btnGenerateAnalysis.setVisibility(View.GONE);
            cardAnalysisResult.setVisibility(View.VISIBLE);
            tvAnalysisText.setText(sp.getString(KEY_LAST_TEXT, "暂无分析建议"));
        } else {
            // 今天未分析过
            btnGenerateAnalysis.setVisibility(View.VISIBLE);
            cardAnalysisResult.setVisibility(View.GONE);
        }
    }

    private void generateAnalysis() {
        Integer workoutDays = viewModel.getTrainingDays().getValue();
        Integer avgCal = viewModel.getAvgCaloriesIntake().getValue();
        Integer targetCal = viewModel.getTargetCalories().getValue();

        if (workoutDays == null)
            workoutDays = 0;
        if (avgCal == null)
            avgCal = 0;
        if (targetCal == null)
            targetCal = 2000;

        String result = AnalysisUtils.getAnalysisResult(workoutDays, avgCal, targetCal, isMonthMode);

        // 保存状态
        String today = DateUtils.formatDate(System.currentTimeMillis());
        sp.edit()
                .putString(KEY_LAST_DATE, today)
                .putString(KEY_LAST_TEXT, result)
                .apply();

        // 刷新 UI
        btnGenerateAnalysis.setVisibility(View.GONE);
        cardAnalysisResult.setVisibility(View.VISIBLE);
        tvAnalysisText.setText(result);
    }
}
