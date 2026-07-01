package com.cz.fitnessdiary.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.databinding.FragmentPlanBinding;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.PlanViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 训练历史日历页面 - 第二个主 Tab
 * 提供月度打卡历史，显示饮食卡路里、训练部位、步数以及自选颜色备注，支持三行字段自定义排序。
 */
public class PlanFragment extends Fragment {

    private FragmentPlanBinding binding;
    private PlanViewModel viewModel;
    private Calendar currentCalendar;
    private long selectedDate;
    private CalendarAdapter calendarAdapter;
    // 使用 new Thread() 而非 ExecutorService，避免与 ViewPager2 生命周期冲突导致导航异常

    // 预设备注标签颜色
    private static final String[][] PRESET_COLORS = {
            {"淡紫", "#EAE6F8", "#6A52B5"},
            {"淡粉", "#F8E6EE", "#B5527D"},
            {"燕麦", "#F8F3E6", "#B59352"},
            {"燕麦灰", "#ECEBEA", "#82716B"},
            {"淡青", "#E6EDF8", "#527AB5"}
    };

    // 42天日历数据缓存
    private final List<Long> dates = new ArrayList<>();
    private final Map<Long, List<DailyLog>> dailyLogsMap = new HashMap<>();
    private final Map<Integer, TrainingPlan> plansMap = new HashMap<>();
    private float userWeight = 70f;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPlanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(PlanViewModel.class);

        currentCalendar = Calendar.getInstance();
        selectedDate = DateUtils.getTodayStartTimestamp();

        setupHeaderActions();
        setupCalendarRecyclerView();
        setupMonthNavigation();

        // 默认加载日历数据
        loadCalendarData();
    }

    private void setupHeaderActions() {
        // 右上角：报表按钮 -> 通过最外层顶级 NavHostFragment 100% 稳定跳转 (解决 ViewPager2 缓存脱链问题)
        binding.btnAnalytics.setOnClickListener(v -> {
            try {
                androidx.navigation.fragment.NavHostFragment navHostFragment = (androidx.navigation.fragment.NavHostFragment) 
                        requireActivity().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                if (navHostFragment != null) {
                    navHostFragment.getNavController().navigate(R.id.planStatsFragment);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "跳转报表失败", Toast.LENGTH_SHORT).show();
            }
        });

        // 右上角：计划管理按钮 -> 通过最外层顶级 NavHostFragment 100% 稳定跳转
        binding.btnPlanManage.setOnClickListener(v -> {
            try {
                androidx.navigation.fragment.NavHostFragment navHostFragment = (androidx.navigation.fragment.NavHostFragment) 
                        requireActivity().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                if (navHostFragment != null) {
                    navHostFragment.getNavController().navigate(R.id.planManageFragment);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "跳转计划管理失败", Toast.LENGTH_SHORT).show();
            }
        });

        // 右上角：动作库按钮 -> 通过最外层顶级 NavHostFragment 100% 稳定跳转
        binding.btnExerciseLibrary.setOnClickListener(v -> {
            try {
                androidx.navigation.fragment.NavHostFragment navHostFragment = (androidx.navigation.fragment.NavHostFragment) 
                        requireActivity().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                if (navHostFragment != null) {
                    navHostFragment.getNavController().navigate(R.id.exerciseLibraryFragment);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "跳转动作库失败", Toast.LENGTH_SHORT).show();
            }
        });

        // 右上角：设置显示顺序
        binding.btnSettings.setOnClickListener(v -> {
            showDisplaySettingsDialog();
        });

        // 选中卡片底部的编辑备注
        binding.btnEditNote.setOnClickListener(v -> {
            showEditNoteDialog(selectedDate);
        });
    }

    private void setupCalendarRecyclerView() {
        binding.rvCalendarGrid.setLayoutManager(new GridLayoutManager(getContext(), 7));
        calendarAdapter = new CalendarAdapter();
        binding.rvCalendarGrid.setAdapter(calendarAdapter);
    }

    private void setupMonthNavigation() {
        updateMonthLabel();

        // 点击左上角大字年月自由选择日期
        binding.layoutMonthSelector.setOnClickListener(v -> showDatePicker());

        // 左右键选择月份暂时保留在 XML 中作为微调
        // 这里只是更新年月
    }

    private void updateMonthLabel() {
        int year = currentCalendar.get(Calendar.YEAR);
        int month = currentCalendar.get(Calendar.MONTH) + 1;
        binding.tvCalendarMonth.setText(String.format(Locale.getDefault(), "%d 月", month));
        binding.tvCalendarYear.setText(String.format(Locale.getDefault(), "%d 年", year));
    }

    private void showDatePicker() {
        com.google.android.material.datepicker.MaterialDatePicker<Long> datePicker =
                com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择日期")
                .setSelection(com.cz.fitnessdiary.utils.DateUtils.localToUtcDayStart(selectedDate))
                .setCalendarConstraints(new com.google.android.material.datepicker.CalendarConstraints.Builder()
                        .setValidator(com.google.android.material.datepicker.DateValidatorPointBackward.now())
                        .build())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            long localDate = com.cz.fitnessdiary.utils.DateUtils.getDayStartTimestamp(selection);
            selectedDate = localDate;
            currentCalendar.setTimeInMillis(localDate);
            updateMonthLabel();
            loadCalendarData();
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    /**
     * 一次性在子线程中加载日历 42 个格子所需的全部数据 (包括饮食总热量、运动及步数)
     */
    private void loadCalendarData() {
        int year = currentCalendar.get(Calendar.YEAR);
        int month = currentCalendar.get(Calendar.MONTH);

        final Context ctx = getContext();
        if (ctx == null) return;

        new Thread(() -> {
            // 1. 生成 42 天时间戳
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, 1);

            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun, 2=Mon...
            int offset = dayOfWeek - Calendar.MONDAY;
            if (offset < 0) {
                offset += 7;
            }
            cal.add(Calendar.DAY_OF_MONTH, -offset);

            List<Long> tempDates = new ArrayList<>();
            for (int i = 0; i < 42; i++) {
                tempDates.add(DateUtils.getDayStartTimestamp(cal.getTimeInMillis()));
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }

            long startDate = tempDates.get(0);
            long endDate = tempDates.get(tempDates.size() - 1) + 86400000L;

            AppDatabase db = AppDatabase.getInstance(ctx);

            // 2. 加载所有的计划以便缓存
            List<TrainingPlan> plans = db.trainingPlanDao().getAllPlansList();
            Map<Integer, TrainingPlan> tempPlansMap = new HashMap<>();
            for (TrainingPlan p : plans) {
                tempPlansMap.put(p.getPlanId(), p);
            }

            // 3. 加载最新的体重
            float tempWeight = 70f;
            WeightRecord latestW = db.weightRecordDao().getLatestRecordSync();
            if (latestW != null && latestW.getWeight() > 0) {
                tempWeight = latestW.getWeight();
            } else {
                com.cz.fitnessdiary.database.entity.User u = db.userDao().getUserSync();
                if (u != null && u.getWeight() > 0) {
                    tempWeight = u.getWeight();
                }
            }

            // 4. 加载整月每日训练数据用于日历展示
            Map<Long, List<DailyLog>> tempLogsMap = new HashMap<>();

            List<DailyLog> logs = db.dailyLogDao().getAllLogsSync();

            for (Long day : tempDates) {
                long dStart = day;

                // 运动
                for (DailyLog log : logs) {
                    if (log.getDate() == dStart) {
                        tempLogsMap.computeIfAbsent(dStart, k -> new ArrayList<>()).add(log);
                    }
                }
            }

            final List<Long> finalDates = tempDates;
            final Map<Long, List<DailyLog>> finalLogsMap = tempLogsMap;
            final Map<Integer, TrainingPlan> finalPlansMap = tempPlansMap;
            final float finalWeight = tempWeight;

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    dates.clear();
                    dates.addAll(finalDates);
                    dailyLogsMap.clear();
                    dailyLogsMap.putAll(finalLogsMap);
                    plansMap.clear();
                    plansMap.putAll(finalPlansMap);
                    userWeight = finalWeight;

                    calendarAdapter.notifyDataSetChanged();
                    loadDayDetailCard(selectedDate);
                });
            }
        }).start();
    }

    private void loadDayDetailCard(long dateStartTs) {
        long dateEndTs = dateStartTs + 86400000L;
        binding.tvDetailDateLabel.setText(String.format("%s 记录详情", DateUtils.formatDate(dateStartTs)));

        // 从 SharedPreferences 中读取备注
        SharedPreferences noteSp = requireContext().getSharedPreferences("calendar_notes", Context.MODE_PRIVATE);
        String rawNote = noteSp.getString(DateUtils.formatDate(dateStartTs), "");
        
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(6f));
        binding.tvDetailNote.setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f));
        
        if (!rawNote.isEmpty()) {
            String[] parts = rawNote.split("\\|", 2);
            binding.tvDetailNote.setText("📝 " + parts[0]);
            binding.btnEditNote.setText("修改备注");
            
            String bgColor = parts.length > 1 ? parts[1] : "#ECEBEA";
            String textColor = "#82716B";
            for (String[] preset : PRESET_COLORS) {
                if (preset[1].equalsIgnoreCase(bgColor)) {
                    textColor = preset[2];
                    break;
                }
            }
            gd.setColor(Color.parseColor(bgColor));
            binding.tvDetailNote.setTextColor(Color.parseColor(textColor));
        } else {
            binding.tvDetailNote.setText("📝 暂无备注");
            binding.btnEditNote.setText("添加备注");
            gd.setColor(0xFFF2EFE8); // 柔灰底
            binding.tvDetailNote.setTextColor(0xFF8A8276); // 柔灰字
        }
        binding.tvDetailNote.setBackground(gd);

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());

            // 运动
            List<DailyLog> dayLogs = db.dailyLogDao().getLogsByDateSync(dateStartTs);
            int workoutCalories = 0;
            StringBuilder sportNameBuilder = new StringBuilder();
            int completedCount = 0;

            for (DailyLog log : dayLogs) {
                if (log.isCompleted()) {
                    completedCount++;
                    TrainingPlan plan = plansMap.get(log.getPlanId());
                    int durationSec = log.getDuration() > 0 ? log.getDuration() : (plan != null ? plan.getDuration() : 1800);
                    if (durationSec <= 0) durationSec = 1800;

                    double met = 4.0;
                    if (plan != null) {
                        met = getMetForCategory(plan.getCategory());
                        if (sportNameBuilder.length() > 0) sportNameBuilder.append("、");
                        sportNameBuilder.append(plan.getName());
                    }
                    double cal = met * 3.5 * userWeight * durationSec / (200.0 * 60.0);
                    workoutCalories += (int) cal;
                }
            }

            int totalVolume = 0;
            int totalSets = 0;
            int totalDuration = 0;

            for (DailyLog log : dayLogs) {
                if (log.isCompleted()) {
                    TrainingPlan plan = plansMap.get(log.getPlanId());
                    if (plan != null) {
                        int sets = plan.getSets();
                        int reps = plan.getReps();
                        float weight = plan.getWeight();
                        float effectiveWeight = weight > 0 ? weight : userWeight;
                        totalVolume += (int) (sets * reps * effectiveWeight);
                        totalSets += sets;
                    }
                    totalDuration += log.getDuration();
                }
            }

            final int finalWorkoutCal = workoutCalories;
            final int finalCompleted = completedCount;
            final String finalSports = sportNameBuilder.toString();
            final int finalVolume = totalVolume;
            final int finalSets = totalSets;
            final int finalDuration = totalDuration;

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (finalCompleted > 0) {
                        binding.tvDetailSportSummary.setText(String.format("🏋️ 今日运动：%s (已打卡 %d 项)", finalSports, finalCompleted));
                        binding.tvDetailSportStats.setText(String.format("运动消耗：%d kcal | 锻炼容量：%s kg", finalWorkoutCal, formatVolume(finalVolume)));
                    } else {
                        binding.tvDetailSportSummary.setText("🏋️ 今日运动：无训练记录");
                        binding.tvDetailSportStats.setText("运动消耗：0 kcal | 锻炼容量：0 kg");
                    }

                    binding.tvDetailVolume.setText(formatVolume(finalVolume) + " kg");
                    binding.tvDetailTotalSets.setText(finalSets + " 组");
                    binding.tvDetailDuration.setText((finalDuration / 60) + " 分钟");
                });
            }
        }).start();
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

    /**
     * 弹出配置 Dialog，允许配置第 1-3 行显示的项目和顺序
     */
    private void showDisplaySettingsDialog() {
        SharedPreferences sp = requireContext().getSharedPreferences("calendar_config", Context.MODE_PRIVATE);
        
        android.widget.LinearLayout container = new android.widget.LinearLayout(getContext());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(dpToPx(24f), dpToPx(16f), dpToPx(24f), dpToPx(16f));

        String[] displayNames = {"训练部位", "锻炼容量", "完成项数", "不显示"};
        String[] values = {"workout", "volume", "count", "none"};

        Spinner spinner1 = createConfigSpinner(displayNames, values, sp.getString("display_row_1", "volume"));
        Spinner spinner2 = createConfigSpinner(displayNames, values, sp.getString("display_row_2", "workout"));
        Spinner spinner3 = createConfigSpinner(displayNames, values, sp.getString("display_row_3", "count"));

        container.addView(createLabelTextView("第一行显示项目："));
        container.addView(spinner1);
        container.addView(createLabelTextView("第二行显示项目："));
        container.addView(spinner2);
        container.addView(createLabelTextView("第三行显示项目："));
        container.addView(spinner3);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("日历单元格自定义设置")
                .setView(container)
                .setPositiveButton("保存并刷新", (dialog, which) -> {
                    sp.edit()
                            .putString("display_row_1", values[spinner1.getSelectedItemPosition()])
                            .putString("display_row_2", values[spinner2.getSelectedItemPosition()])
                            .putString("display_row_3", values[spinner3.getSelectedItemPosition()])
                            .apply();
                    loadCalendarData();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private TextView createLabelTextView(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(getResources().getColor(R.color.text_primary));
        tv.setTextSize(14f);
        tv.setPadding(0, dpToPx(10f), 0, dpToPx(4f));
        return tv;
    }

    private Spinner createConfigSpinner(String[] displayNames, String[] values, String currentVal) {
        Spinner spinner = new Spinner(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int selectPos = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentVal)) {
                selectPos = i;
                break;
            }
        }
        spinner.setSelection(selectPos);
        return spinner;
    }

    /**
     * 弹出编辑备注 Dialog，支持自选 5 种莫兰迪淡雅底色
     */
    private void showEditNoteDialog(long dateTs) {
        String dateKey = DateUtils.formatDate(dateTs);
        SharedPreferences noteSp = requireContext().getSharedPreferences("calendar_notes", Context.MODE_PRIVATE);
        
        String raw = noteSp.getString(dateKey, "");
        String initialText = "";
        String initialColor = PRESET_COLORS[0][1];

        if (!raw.isEmpty()) {
            String[] parts = raw.split("\\|", 2);
            initialText = parts[0];
            if (parts.length > 1) {
                initialColor = parts[1];
            }
        }

        android.widget.LinearLayout container = new android.widget.LinearLayout(getContext());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(dpToPx(24f), dpToPx(16f), dpToPx(24f), dpToPx(16f));

        EditText etNote = new EditText(getContext());
        etNote.setHint("输入标签或短备注 (例如: 出差、感冒、聚餐)");
        etNote.setText(initialText);
        container.addView(etNote);

        TextView labelColor = new TextView(getContext());
        labelColor.setText("选择备注背景色：");
        labelColor.setTextColor(getResources().getColor(R.color.text_primary));
        labelColor.setPadding(0, dpToPx(16f), 0, dpToPx(8f));
        container.addView(labelColor);

        // 颜色圆形块选择器容器
        android.widget.LinearLayout colorContainer = new android.widget.LinearLayout(getContext());
        colorContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        colorContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        final String[] selectedColor = {initialColor};
        final List<View> colorViews = new ArrayList<>();

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int index = i;
            View circle = new View(getContext());
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    dpToPx(32f), dpToPx(32f));
            lp.setMargins(0, 0, dpToPx(12f), 0);
            circle.setLayoutParams(lp);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(Color.parseColor(PRESET_COLORS[i][1]));

            // 初始化时高亮已选颜色
            if (PRESET_COLORS[i][1].equalsIgnoreCase(selectedColor[0])) {
                gd.setStroke(dpToPx(2.5f), 0xFF559664); // 选中描绿边
            }
            circle.setBackground(gd);

            circle.setOnClickListener(v -> {
                selectedColor[0] = PRESET_COLORS[index][1];
                // 刷新所有圆圈的状态
                for (int j = 0; j < colorViews.size(); j++) {
                    GradientDrawable innerGd = new GradientDrawable();
                    innerGd.setShape(GradientDrawable.OVAL);
                    innerGd.setColor(Color.parseColor(PRESET_COLORS[j][1]));
                    if (j == index) {
                        innerGd.setStroke(dpToPx(2.5f), 0xFF559664);
                    }
                    colorViews.get(j).setBackground(innerGd);
                }
            });

            colorContainer.addView(circle);
            colorViews.add(circle);
        }
        container.addView(colorContainer);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加日期标签/备注")
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String noteText = etNote.getText().toString().trim();
                    if (!noteText.isEmpty()) {
                        noteSp.edit().putString(dateKey, noteText + "|" + selectedColor[0]).apply();
                    } else {
                        noteSp.edit().remove(dateKey).apply();
                    }
                    loadCalendarData();
                })
                .setNegativeButton("清除备注", (dialog, which) -> {
                    noteSp.edit().remove(dateKey).apply();
                    loadCalendarData();
                })
                .show();
    }

    private int dpToPx(float dp) {
        if (getContext() == null) return 0;
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // executorService removed - using new Thread() instead
        binding = null;
    }

    // ────────────────────── 适配器 ──────────────────────
    private class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 格子拉大，提供充裕的三行小标签叠放高度 (76dp)
            android.widget.LinearLayout container = new android.widget.LinearLayout(getContext());
            container.setOrientation(android.widget.LinearLayout.VERTICAL);
            container.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
            container.setPadding(0, dpToPx(4f), 0, dpToPx(4f)); // 左右 Padding 归零，最大化释放单元格宽度
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(90f))); // 高度升到 90dp，彻底解决第三行显示不全

            TextView tvDate = new TextView(getContext());
            tvDate.setTextSize(12f);
            tvDate.setGravity(android.view.Gravity.CENTER);
            tvDate.setTextColor(0xFF3C3730);
            android.widget.LinearLayout.LayoutParams lpDate = new android.widget.LinearLayout.LayoutParams(
                    dpToPx(22f), dpToPx(22f));
            tvDate.setLayoutParams(lpDate);

            android.widget.LinearLayout layoutLabels = new android.widget.LinearLayout(getContext());
            layoutLabels.setOrientation(android.widget.LinearLayout.VERTICAL);
            layoutLabels.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            android.widget.LinearLayout.LayoutParams lpLabels = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutLabels.setLayoutParams(lpLabels);

            container.addView(tvDate);
            container.addView(layoutLabels);

            return new ViewHolder(container, tvDate, layoutLabels);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (dates.isEmpty()) return;
            long cellTime = dates.get(position);
            Calendar cellCal = Calendar.getInstance();
            cellCal.setTimeInMillis(cellTime);

            int dayOfMonth = cellCal.get(Calendar.DAY_OF_MONTH);
            holder.tvDate.setText(String.valueOf(dayOfMonth));

            // 1. 判断非本月
            int targetMonth = currentCalendar.get(Calendar.MONTH);
            boolean isCurrentMonth = cellCal.get(Calendar.MONTH) == targetMonth;
            if (isCurrentMonth) {
                holder.tvDate.setTextColor(getResources().getColor(R.color.text_primary));
            } else {
                holder.tvDate.setTextColor(0xFFB3ABA0);
            }

            // 2. 清空动态标签容器并重新注入
            holder.layoutLabels.removeAllViews();

            SharedPreferences sp = requireContext().getSharedPreferences("calendar_config", Context.MODE_PRIVATE);
            String row1 = sp.getString("display_row_1", "volume");
            String row2 = sp.getString("display_row_2", "workout");
            String row3 = sp.getString("display_row_3", "count");

            bindLabelByConfig(holder.layoutLabels, row1, cellTime);
            bindLabelByConfig(holder.layoutLabels, row2, cellTime);
            bindLabelByConfig(holder.layoutLabels, row3, cellTime);

            // 自动读取并无条件展示备注胶囊，有备注则显示，无则不显示 (放在最下方)
            SharedPreferences noteSp = requireContext().getSharedPreferences("calendar_notes", Context.MODE_PRIVATE);
            String rawNoteText = noteSp.getString(DateUtils.formatDate(cellTime), "");
            if (!rawNoteText.isEmpty()) {
                String[] parts = rawNoteText.split("\\|", 2);
                String text = parts[0];
                String bgColor = parts.length > 1 ? parts[1] : "#ECEBEA";
                
                String textColor = "#82716B";
                for (String[] preset : PRESET_COLORS) {
                    if (preset[1].equalsIgnoreCase(bgColor)) {
                        textColor = preset[2];
                        break;
                    }
                }
                holder.layoutLabels.addView(createLabelView(text, bgColor, textColor));
            }

            // 3. 当前日期高亮
            boolean isToday = DateUtils.isToday(cellTime);
            boolean isSelected = DateUtils.isSameDay(cellTime, selectedDate);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);

            if (isSelected) {
                gd.setStroke(dpToPx(1.5f), 0xFF559664); // 选中绿色边框
            }

            if (isToday) {
                gd.setColor(0xFFF2EFE8); // 今天填充浅灰底色
            } else {
                gd.setColor(Color.TRANSPARENT);
            }
            holder.tvDate.setBackground(gd);

            // 4. 点击联动
            holder.itemView.setOnClickListener(v -> {
                selectedDate = cellTime;
                notifyDataSetChanged();
                loadDayDetailCard(cellTime);
            });
        }

        private void bindLabelByConfig(android.widget.LinearLayout container, String config, long cellTime) {
            if ("none".equals(config)) return;

            switch (config) {
                case "volume":
                    List<DailyLog> vLogs = dailyLogsMap.get(cellTime);
                    if (vLogs != null) {
                        int totalVolume = 0;
                        for (DailyLog log : vLogs) {
                            if (log.isCompleted()) {
                                TrainingPlan plan = plansMap.get(log.getPlanId());
                                if (plan != null) {
                                    float effectiveWeight = plan.getWeight() > 0 ? plan.getWeight() : userWeight;
                                    totalVolume += (int) (plan.getSets() * plan.getReps() * effectiveWeight);
                                }
                            }
                        }
                        if (totalVolume > 0) {
                            // 锻炼容量：蓝色系标签
                            container.addView(createLabelView(formatVolume(totalVolume) + "kg", "#E8EFF5", "#2E6BB3"));
                        }
                    }
                    break;
                case "workout":
                    List<DailyLog> logs = dailyLogsMap.get(cellTime);
                    if (logs != null) {
                        List<String> categories = new ArrayList<>();
                        for (DailyLog log : logs) {
                            if (log.isCompleted()) {
                                TrainingPlan plan = plansMap.get(log.getPlanId());
                                if (plan != null && plan.getCategory() != null) {
                                    String cat = plan.getCategory();
                                    if (cat.contains("-")) {
                                        String[] parts = cat.split("-");
                                        cat = parts[parts.length - 1]; // 去除前缀，拿取最后一截的真实部位名称
                                    }
                                    if (cat.endsWith("部")) {
                                        cat = cat.substring(0, cat.length() - 1); // 腹部 -> 腹，胸部 -> 胸
                                    }
                                    if (!categories.contains(cat)) {
                                        categories.add(cat);
                                    }
                                }
                            }
                        }
                        StringBuilder sb = new StringBuilder();
                        for (String cat : categories) {
                            sb.append(cat); // 直接连写，不用空格隔开，腾出最宽空间
                        }
                        if (sb.length() > 0) {
                            // 运动：显示部位简写，背景橘黄色系
                            container.addView(createLabelView(sb.toString(), "#F5ECE6", "#D6621B"));
                        }
                    }
                    break;
                case "count":
                    List<DailyLog> cLogs = dailyLogsMap.get(cellTime);
                    if (cLogs != null) {
                        int count = 0;
                        for (DailyLog log : cLogs) {
                            if (log.isCompleted()) count++;
                        }
                        if (count > 0) {
                            // 完成项数：绿色系标签
                            container.addView(createLabelView(count + "项", "#E2EDE4", "#559664"));
                        }
                    }
                    break;
            }
        }

        private TextView createLabelView(String text, String bgColorHex, String textColorHex) {
            TextView tv = new TextView(getContext());
            tv.setText(text);
            tv.setTextSize(8f); // 缩小字号到 8sp，高精细度，窄屏防遮挡
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextColor(Color.parseColor(textColorHex));
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(dpToPx(4f));
            gd.setColor(Color.parseColor(bgColorHex));
            tv.setBackground(gd);

            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(13f)); // 高度缩为 13dp
            lp.setMargins(0, dpToPx(2f), 0, 0);
            tv.setLayoutParams(lp);
            tv.setPadding(dpToPx(1f), 0, dpToPx(1f), 0); // 内部 Padding 降到最小，挤出字符空间

            return tv;
        }

        @Override
        public int getItemCount() {
            return dates.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate;
            android.widget.LinearLayout layoutLabels;

            public ViewHolder(@NonNull View itemView, TextView tvDate, android.widget.LinearLayout layoutLabels) {
                super(itemView);
                this.tvDate = tvDate;
                this.layoutLabels = layoutLabels;
            }
        }
    }
}
