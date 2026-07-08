package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.utils.CalorieCalculatorUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

/**
 * 健康透视看板 — 详细解释页面
 * 展示 BMI/BMR/TDEE/饮食目标的计算公式、分级标准和基于用户数据的实时示例。
 * 从 BodyDataDetailBottomSheetFragment 的「健康透视看板」区域点击进入。
 */
public class HealthInsightExplainDialog extends BottomSheetDialogFragment {

    private TextView tvBmiLive, tvBmiIdealRange;
    private TextView tvBmrLive;
    private TextView tvTdeeLive;
    private TextView tvDietSummary;
    private TextView tvParamGenderAge, tvParamGoal, tvParamWeight, tvParamHeight, tvParamActivity, tvParamBmr;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_health_insight_explain, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 绑定视图
        tvBmiLive = view.findViewById(R.id.tv_bmi_live);
        tvBmiIdealRange = view.findViewById(R.id.tv_bmi_ideal_range);
        tvBmrLive = view.findViewById(R.id.tv_bmr_live);
        tvTdeeLive = view.findViewById(R.id.tv_tdee_live);
        tvDietSummary = view.findViewById(R.id.tv_diet_summary);
        tvParamGenderAge = view.findViewById(R.id.tv_param_gender_age);
        tvParamGoal = view.findViewById(R.id.tv_param_goal);
        tvParamWeight = view.findViewById(R.id.tv_param_weight);
        tvParamHeight = view.findViewById(R.id.tv_param_height);
        tvParamActivity = view.findViewById(R.id.tv_param_activity);
        tvParamBmr = view.findViewById(R.id.tv_param_bmr);

        // 返回按钮
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> dismiss());

        // 加载数据
        loadAndPopulate();
    }

    private void loadAndPopulate() {
        new Thread(() -> {
            com.cz.fitnessdiary.database.AppDatabase db =
                    com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
            User user = db.userDao().getUserSync();

            if (user == null || !isAdded()) return;

            // === 基础数据 ===
            float weight = user.getWeight();
            float height = user.getHeight();
            int age = user.getAge();
            int gender = user.getGender();
            float activityLevel = user.getActivityLevel();
            if (activityLevel <= 0) activityLevel = 1.2f;
            String goalStr = user.getGoal();
            if (goalStr == null) goalStr = "保持";

            // === BMI ===
            double heightM = height / 100.0;
            double bmi = (heightM > 0) ? weight / (heightM * heightM) : 0;
            String bmiLevel;
            if (bmi < 18.5) bmiLevel = "偏瘦";
            else if (bmi < 24) bmiLevel = "正常";
            else if (bmi < 28) bmiLevel = "超重";
            else bmiLevel = "肥胖";

            // 建议体重范围 (BMI 18.5-24)
            float idealMin = (float) (18.5 * heightM * heightM);
            float idealMax = (float) (24.0 * heightM * heightM);

            // === BMR (Mifflin-St Jeor) ===
            int bmr = CalorieCalculatorUtils.calculateBMR(gender, weight, height, age);

            // === TDEE ===
            int rawTdee = CalorieCalculatorUtils.calculateTDEE(bmr, activityLevel);

            // === 目标热量 ===
            int goalType;
            if ("减脂".equals(goalStr)) goalType = CalorieCalculatorUtils.GOAL_LOSE_FAT;
            else if ("增肌".equals(goalStr)) goalType = CalorieCalculatorUtils.GOAL_GAIN_MUSCLE;
            else goalType = CalorieCalculatorUtils.GOAL_MAINTAIN;
            int calorieTarget = CalorieCalculatorUtils.calculateTargetCalories(rawTdee, goalType);
            if (calorieTarget < 1200) calorieTarget = 1200;

            // === 蛋白质 ===
            int targetProtein;
            if ("增肌".equals(goalStr)) {
                targetProtein = (int) (weight * 2.0);
            } else if ("减脂".equals(goalStr)) {
                targetProtein = (int) (weight * 1.5);
            } else {
                targetProtein = (int) (weight * 1.2);
            }

            // === 脂肪 ===
            int targetFat = (int) Math.round((calorieTarget * 0.25) / 9.0);

            // === 碳水 ===
            int fatCalories = (int) (calorieTarget * 0.25);
            int proteinCalories = targetProtein * 4;
            int remaining = calorieTarget - fatCalories - proteinCalories;
            int targetCarbs = Math.max(0, remaining / 4);

            // === 活动水平名称 ===
            String activityName = CalorieCalculatorUtils.getActivityLevelName(activityLevel);

            // === 性别/目标文本 ===
            String genderStr = gender == 1 ? "男" : "女";

            // === 更新 UI ===
            if (isAdded()) {
                final double fBmi = bmi;
                final String fBmiLevel = bmiLevel;
                final float fIdealMin = idealMin;
                final float fIdealMax = idealMax;
                final int fBmr = bmr;
                final int fRawTdee = rawTdee;
                final int fCalorieTarget = calorieTarget;
                final int fTargetProtein = targetProtein;
                final int fTargetFat = targetFat;
                final int fTargetCarbs = targetCarbs;
                final String fGenderStr = genderStr;
                final String fGoalStr = goalStr;
                final String fActivityName = activityName;
                final float fActivityLevel = activityLevel;
                final float fWeight = weight;
                final float fHeight = height;
                final int fAge = age;

                requireActivity().runOnUiThread(() -> {
                    // BMI
                    tvBmiLive.setText(String.format(Locale.getDefault(),
                            "您的 BMI：%.1f (%s)", fBmi, fBmiLevel));
                    tvBmiIdealRange.setText(String.format(Locale.getDefault(),
                            "建议体重范围：%.0f - %.0f kg (BMI 18.5-24)", fIdealMin, fIdealMax));

                    // BMR
                    tvBmrLive.setText(String.format(Locale.getDefault(),
                            "您的 BMR：%,d kcal/天", fBmr));

                    // TDEE
                    tvTdeeLive.setText(String.format(Locale.getDefault(),
                            "您的 TDEE：%,d kcal/天  (%s × %.3f)", fRawTdee, fActivityName, fActivityLevel));

                    // 饮食目标汇总
                    tvDietSummary.setText(String.format(Locale.getDefault(),
                            "热量 %,d kcal · 蛋白质 %dg · 脂肪 %dg · 碳水 %dg",
                            fCalorieTarget, fTargetProtein, fTargetFat, fTargetCarbs));

                    // 计算参数
                    tvParamGenderAge.setText(String.format(Locale.getDefault(),
                            "%s · %d岁", fGenderStr, fAge));
                    tvParamGoal.setText(fGoalStr);
                    tvParamWeight.setText(String.format(Locale.getDefault(), "%.1f kg", fWeight));
                    tvParamHeight.setText(String.format(Locale.getDefault(), "%.0f cm", fHeight));
                    tvParamActivity.setText(String.format(Locale.getDefault(),
                            "%.3f %s", fActivityLevel, fActivityName));
                    tvParamBmr.setText(String.format(Locale.getDefault(), "%,d kcal", fBmr));
                });
            }
        }).start();
    }
}
