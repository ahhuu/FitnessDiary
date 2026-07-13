package com.cz.fitnessdiary.ui.bottomSheet;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.BottomSheetDateSummaryBinding;
import com.cz.fitnessdiary.model.DailyHealthSnapshot;
import com.cz.fitnessdiary.repository.HealthAggregationRepository;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.utils.ExerciseMetTable;
import com.cz.fitnessdiary.utils.TrainingRecordUtils;
import com.cz.fitnessdiary.viewmodel.CheckInViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.Locale;

/**
 * 跨模块日期摘要 BottomSheet - v3.0
 * 在日历点击日期时弹出，展示该日的训练、饮食、习惯和体重汇总信息
 */
public class DateSummaryBottomSheet extends BottomSheetDialogFragment {

    public interface OnNoteSavedListener {
        void onNoteSaved(long date, String notes);
    }

    private OnNoteSavedListener noteSavedListener;
    private BottomSheetDateSummaryBinding binding;
    private final long dateTimestamp;
    private HealthAggregationRepository repository;
    private AppDatabase database;
    private String selectedColor = null;

    // 8 preset colors for note color picker
    private static final String[][] COLOR_PICKER_COLORS = {
        {"#E57373", "Red"},
        {"#F06292", "Pink"},
        {"#BA68C8", "Purple"},
        {"#64B5F6", "Blue"},
        {"#4DB6AC", "Teal"},
        {"#81C784", "Green"},
        {"#FFD54F", "Yellow"},
        {"#FF8A65", "Orange"}
    };

    public DateSummaryBottomSheet(long dateTimestamp) {
        this.dateTimestamp = dateTimestamp;
    }

    public void setOnNoteSavedListener(OnNoteSavedListener listener) {
        this.noteSavedListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDateSummaryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new HealthAggregationRepository(requireActivity().getApplication());
        database = AppDatabase.getInstance(requireContext());

        // 设置日期标题
        binding.tvSummaryDate.setText(DateUtils.formatFullDate(dateTimestamp));

        // 设置备注保存按钮和删除按钮
        binding.btnSaveNote.setOnClickListener(v -> saveNotes());
        binding.btnDeleteNote.setOnClickListener(v -> deleteNotes());

        // 加载已保存的备注
        loadNotes();

        // 初始化颜色选择器
        setupColorPicker();

        // 后台加载数据
        loadDateData();
    }

    private void loadDateData() {
        new Thread(() -> {
            try {
                DailyHealthSnapshot snapshot = repository.getDateSnapshot(dateTimestamp);

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> populateUi(snapshot));
                }

                // Load training details
                loadTrainingDetails();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadTrainingDetails() {
        long dateEnd = dateTimestamp + 86400000L;
        List<TrainingRecordUtils.Entry> entries = TrainingRecordUtils.getCompletedEntries(
                database, dateTimestamp, dateEnd);

        if (entries.isEmpty()) {
            return;
        }

        int totalVolume = 0;
        int totalDurationSec = 0;
        int totalCalories = 0;
        double totalWeightedMet = 0;  // Σ(MET × dur) for weighted average
        int totalDurForMet = 0;       // Σ(dur) for weighted average
        float userWeight = 70f;

        // Get user weight
        com.cz.fitnessdiary.database.entity.User u = database.userDao().getUserSync();
        if (u != null && u.getWeight() > 0) {
            userWeight = u.getWeight();
        }

        StringBuilder detailsHtml = new StringBuilder();

        for (TrainingRecordUtils.Entry entry : entries) {

            // Find matching plan
            String name = entry.name;
            int sets = entry.sets;
            int reps = entry.reps;
            float weight = entry.weight;
            // 时长推算: 实际>预设>默认6分钟
            int logDuration = ExerciseMetTable.resolveDuration(
                    entry.duration, 0, sets, reps, requireContext());
            int volume = ExerciseMetTable.calculateVolume(name, sets, reps, weight, logDuration, userWeight);
            String volumeUnit = ExerciseMetTable.getVolumeUnit(sets, reps, weight, logDuration, name);

            totalVolume += volume;
            totalDurationSec += logDuration;

            // Calculate calories (使用已推算的logDuration)
            int durSec = logDuration;
            double met = ExerciseMetTable.getMetForExercise(entry.name, entry.category);
            int cal = (int) (met * userWeight * (durSec / 3600.0));
            totalCalories += cal;
            totalWeightedMet += met * durSec;
            totalDurForMet += durSec;

            String line = name + " | " + sets + "组x" + reps + "次";
            if (weight > 0) line += " @" + (int) weight + "kg";
            if (logDuration > 0) line += " " + String.format(Locale.getDefault(), "%.1f", logDuration / 60.0f) + "min";
            if (volume > 0) line += " 容量" + ExerciseMetTable.formatVolume(volume, volumeUnit, weight, name, sets, reps);

            final String exerciseLine = line;
            final int exerciseCal = cal;
            final int exerciseVolume = volume;

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    addExerciseDetailRow(exerciseLine, exerciseCal, exerciseVolume);
                });
            }
        }

        // 如果用户设了当天训练总时长，按加权平均MET重算总消耗
        int targetMin = requireContext().getSharedPreferences("fitness_diary_prefs", android.content.Context.MODE_PRIVATE)
                .getInt("target_minutes_" + dateTimestamp, 0);
        if (targetMin > 0 && totalDurForMet > 0) {
            double avgMet = totalWeightedMet / totalDurForMet;
            totalCalories = (int) (avgMet * userWeight * (targetMin / 60.0));
        }

        // 加上步数消耗，与首页运动消耗口径保持一致（运动消耗 + 步数消耗）
        com.cz.fitnessdiary.database.entity.StepRecord stepRecord = database.stepRecordDao().getByDateSync(dateTimestamp);
        int stepCal = stepRecord != null ? (int) (stepRecord.getSteps() * 0.04f) : 0;
        totalCalories += stepCal;

        // Update total stats
        final int finalTotalVolume = totalVolume;
        final int finalTotalDurationSec = totalDurationSec;
        final int finalTotalCalories = totalCalories;

        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                if (finalTotalVolume > 0 || finalTotalDurationSec > 0) {
                    binding.layoutTrainingTotals.setVisibility(View.VISIBLE);
                    binding.tvTotalVolume.setText("总容量: " + finalTotalVolume);
                    int displayDuration = ExerciseMetTable.resolveTotalDuration(
                            finalTotalDurationSec, dateTimestamp, requireContext());
                    binding.tvTotalDuration.setText("总时长: " + String.format(Locale.getDefault(), "%.1f", displayDuration / 60.0f) + " 分钟");
                }
                // Update calorie display
                binding.tvSummaryExerciseCalories.setText(String.format(Locale.getDefault(),
                        "消耗：%d kcal", finalTotalCalories));
            });
        }
    }

    private void addExerciseDetailRow(String detail, int calories, int volume) {
        TextView tvDetail = new TextView(requireContext());
        tvDetail.setText(detail);
        tvDetail.setTextColor(0xFF3C3730);
        tvDetail.setTextSize(12f);
        tvDetail.setPadding(dpToPx(4f), dpToPx(2f), dpToPx(4f), dpToPx(2f));

        binding.layoutExerciseDetails.setVisibility(View.VISIBLE);
        binding.layoutExerciseDetails.addView(tvDetail);
    }

    private void populateUi(DailyHealthSnapshot s) {
        // 健康评分徽章
        setupHealthScoreBadge(s.healthScore);

        // 训练行
        if (s.completedPlans > 0) {
            binding.tvSummaryCompletedPlans.setText(String.format(Locale.getDefault(),
                    "完成计划：%d 项", s.completedPlans));
        } else {
            binding.tvSummaryCompletedPlans.setText("完成计划：0 项");
        }
        int totalExerciseCal = s.exerciseCalories + s.stepCalories;
        binding.tvSummaryExerciseCalories.setText(String.format(Locale.getDefault(),
                "消耗：%d kcal", totalExerciseCal));

        // 饮食行
        if (s.dietCalories > 0) {
            binding.tvSummaryDietCalories.setText(String.format(Locale.getDefault(),
                    "摄入：%d kcal", s.dietCalories));
            // 蛋白质
            if (s.todayProtein > 0) {
                binding.tvSummaryDietProtein.setVisibility(View.VISIBLE);
                binding.tvSummaryDietProtein.setText(String.format(Locale.getDefault(),
                        "🥩%dg", s.todayProtein));
            } else {
                binding.tvSummaryDietProtein.setVisibility(View.GONE);
            }
            // 碳水
            if (s.todayCarbs > 0) {
                binding.tvSummaryDietCarbs.setVisibility(View.VISIBLE);
                binding.tvSummaryDietCarbs.setText(String.format(Locale.getDefault(),
                        "🍚%dg", s.todayCarbs));
            } else {
                binding.tvSummaryDietCarbs.setVisibility(View.GONE);
            }
            // 脂肪
            if (s.todayFat > 0) {
                binding.tvSummaryDietFat.setVisibility(View.VISIBLE);
                binding.tvSummaryDietFat.setText(String.format(Locale.getDefault(),
                        "🧈%dg", s.todayFat));
            } else {
                binding.tvSummaryDietFat.setVisibility(View.GONE);
            }
        } else {
            binding.tvSummaryDietCalories.setText("暂无饮食记录");
            binding.tvSummaryDietProtein.setVisibility(View.GONE);
            binding.tvSummaryDietCarbs.setVisibility(View.GONE);
            binding.tvSummaryDietFat.setVisibility(View.GONE);
        }

        // 习惯行 - 睡眠 / 饮水 / 步数 / 心情
        String sleepText;
        if (s.sleepHours > 0) {
            sleepText = String.format(Locale.getDefault(), "😴 %.1fh", s.sleepHours);
        } else {
            sleepText = "😴 --";
        }
        binding.tvSummarySleep.setText(sleepText);

        String waterText;
        if (s.waterMl > 0) {
            waterText = String.format(Locale.getDefault(), "💧 %dml", s.waterMl);
        } else {
            waterText = "💧 --";
        }
        binding.tvSummaryWater.setText(waterText);

        String stepsText;
        if (s.steps > 0) {
            stepsText = String.format(Locale.getDefault(), "👣 %d", s.steps);
        } else {
            stepsText = "👣 --";
        }
        binding.tvSummarySteps.setText(stepsText);

        String moodText;
        if (s.moodLevel > 0) {
            String moodEmoji = moodLevelToEmoji(s.moodLevel);
            moodText = moodEmoji + " " + s.moodLevel;
        } else {
            moodText = "😐 --";
        }
        binding.tvSummaryMood.setText(moodText);

        // 体重 + 体脂行
        if (s.weightKg > 0) {
            binding.layoutWeightSection.setVisibility(View.VISIBLE);
            String weightText = String.format(Locale.getDefault(),
                    "%.1f kg", s.weightKg);
            if (s.weightTrend != 0) {
                String trendSymbol = s.weightTrend > 0 ? "↓" : "↑";
                weightText += String.format(Locale.getDefault(),
                        " (%s%.1f)", trendSymbol, Math.abs(s.weightTrend));
            }
            binding.tvSummaryWeight.setText(weightText);
            // 体脂率同行显示，带趋势
            if (s.bodyFat > 0) {
                binding.tvSummaryBodyFat.setVisibility(View.VISIBLE);
                String bfText = String.format(Locale.getDefault(),
                        "体脂 %.1f%%", s.bodyFat);
                if (s.bodyFatTrend != 0) {
                    String bfTrend = s.bodyFatTrend > 0 ? "↓" : "↑";
                    bfText += String.format(Locale.getDefault(),
                            " (%s%.1f%%)", bfTrend, Math.abs(s.bodyFatTrend));
                }
                binding.tvSummaryBodyFat.setText(bfText);
            } else {
                binding.tvSummaryBodyFat.setVisibility(View.GONE);
            }
        } else {
            binding.layoutWeightSection.setVisibility(View.GONE);
        }

        // 显示备注区域
        if (s.completedPlans > 0 || s.dietCalories > 0 || s.waterMl > 0 || s.steps > 0 || s.weightKg > 0) {
            binding.layoutNotesSection.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 根据健康评分设置徽章背景色
     */
    private void setupHealthScoreBadge(int score) {
        int bgColor;
        if (score >= 80) {
            bgColor = 0xFF559664; // 优秀 - 绿色
        } else if (score >= 60) {
            bgColor = 0xFFD6A340; // 良好 - 金色
        } else if (score >= 40) {
            bgColor = 0xFFD6621B; // 一般 - 橙色
        } else {
            bgColor = 0xFFB5525B; // 需关注 - 红色
        }

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(12f));
        gd.setColor(bgColor);
        binding.tvHealthScore.setBackground(gd);

        if (score > 0) {
            binding.tvHealthScore.setText("评分 " + score);
        } else {
            binding.tvHealthScore.setText("--");
        }
    }

    /**
     * 将心情等级映射为表情符号
     */
    private static String moodLevelToEmoji(int level) {
        switch (level) {
            case 5: return "😄";
            case 4: return "🙂";
            case 3: return "😐";
            case 2: return "😔";
            case 1: return "😢";
            default: return "😐";
        }
    }

    /**
     * 保存备注到 SharedPreferences (与旧版 PlanFragment 日历备注共用 "calendar_notes" key 以兼容已有数据)
     */
    private void saveNotes() {
        String notes = binding.etNotes.getText().toString().trim();
        SharedPreferences sp = requireContext().getSharedPreferences("calendar_notes", android.content.Context.MODE_PRIVATE);
        String dateKey = DateUtils.formatDate(dateTimestamp);
        if (!notes.isEmpty()) {
            // 格式为 "text|color"，保存颜色和文本
            String colorToSave = selectedColor != null ? selectedColor : "";
            String value = colorToSave.isEmpty() ? notes : notes + "|" + colorToSave;
            sp.edit().putString(dateKey, value).apply();
        } else {
            sp.edit().remove(dateKey).apply();
        }
        Toast.makeText(getContext(), "备注已保存", Toast.LENGTH_SHORT).show();
        // Update button states after saving
        updateNoteButtonStates(!notes.isEmpty());
        // Notify listener for instant refresh
        if (noteSavedListener != null) {
            noteSavedListener.onNoteSaved(dateTimestamp, notes);
        }
    }

    /**
     * 删除备注
     */
    private void deleteNotes() {
        SharedPreferences sp = requireContext().getSharedPreferences("calendar_notes", android.content.Context.MODE_PRIVATE);
        String dateKey = DateUtils.formatDate(dateTimestamp);
        sp.edit().remove(dateKey).apply();
        binding.etNotes.setText("");
        Toast.makeText(getContext(), "备注已删除", Toast.LENGTH_SHORT).show();
        updateNoteButtonStates(false);
        // Notify listener for instant refresh
        if (noteSavedListener != null) {
            noteSavedListener.onNoteSaved(dateTimestamp, "");
        }
    }

    /**
     * 根据是否有备注内容更新按钮状态
     */
    private void updateNoteButtonStates(boolean hasNotes) {
        if (hasNotes) {
            binding.btnSaveNote.setText("修改备注");
            binding.btnDeleteNote.setVisibility(View.VISIBLE);
        } else {
            binding.btnSaveNote.setText("保存备注");
            binding.btnDeleteNote.setVisibility(View.GONE);
        }
    }

    /**
     * 加载已保存的备注 (从 "calendar_notes" 读取)
     */
    private void loadNotes() {
        SharedPreferences sp = requireContext().getSharedPreferences("calendar_notes", android.content.Context.MODE_PRIVATE);
        String dateKey = DateUtils.formatDate(dateTimestamp);
        String savedNotes = sp.getString(dateKey, "");
        if (!savedNotes.isEmpty()) {
            // Show the notes section immediately when there's a saved note
            binding.layoutNotesSection.setVisibility(View.VISIBLE);
            // 格式为 "text|color"，截取文本部分
            String[] parts = savedNotes.split("\\|", 2);
            binding.etNotes.setText(parts[0]);
            if (parts.length > 1 && !parts[1].isEmpty()) {
                selectedColor = parts[1];
                // Update color picker highlight if it's already initialized
                if (binding.layoutColorPicker.getChildCount() > 0) {
                    updateColorPickerSelection(parts[1]);
                }
            }
            updateNoteButtonStates(true);
        } else {
            selectedColor = null;
            updateNoteButtonStates(false);
        }
    }

    /**
     * 初始化颜色选择器：在 layout_color_picker 中添加 8 个颜色圆圈
     */
    private void setupColorPicker() {
        LinearLayout colorPickerLayout = binding.layoutColorPicker;
        colorPickerLayout.removeAllViews();

        int dotSize = dpToPx(32f);
        int margin = dpToPx(4f);

        for (int i = 0; i < COLOR_PICKER_COLORS.length; i++) {
            final String colorHex = COLOR_PICKER_COLORS[i][0];

            View colorDot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.setMargins(margin, 0, margin, 0);
            colorDot.setLayoutParams(lp);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(Color.parseColor(colorHex));
            colorDot.setBackground(gd);

            colorDot.setTag(colorHex);
            colorDot.setOnClickListener(v -> {
                String hex = (String) v.getTag();
                selectedColor = hex;
                updateColorPickerSelection(hex);
            });

            colorPickerLayout.addView(colorDot);
        }

        // Always show the color picker
        colorPickerLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 更新颜色选择器的选中状态：高亮选中的颜色圆圈
     */
    private void updateColorPickerSelection(String selectedHex) {
        LinearLayout colorPickerLayout = binding.layoutColorPicker;
        int highlightStroke = dpToPx(2.5f);
        int noStroke = dpToPx(0f);

        for (int i = 0; i < colorPickerLayout.getChildCount(); i++) {
            View child = colorPickerLayout.getChildAt(i);
            String hex = (String) child.getTag();
            if (child.getBackground() instanceof GradientDrawable) {
                GradientDrawable gd = (GradientDrawable) child.getBackground();
                if (hex != null && hex.equals(selectedHex)) {
                    gd.setStroke(highlightStroke, 0xFF3C3730);
                } else {
                    gd.setStroke(noStroke, Color.TRANSPARENT);
                }
            }
        }
    }

    /**
     * 格式化锻炼容量数字，超过 10000 时显示 "10.0k" 格式
     */
    private String formatVolume(int volume) {
        if (volume >= 10000) {
            return String.format(Locale.getDefault(), "%.1fk", volume / 1000.0);
        }
        return String.valueOf(volume);
    }

    private int dpToPx(float dp) {
        if (getContext() == null) return 0;
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
