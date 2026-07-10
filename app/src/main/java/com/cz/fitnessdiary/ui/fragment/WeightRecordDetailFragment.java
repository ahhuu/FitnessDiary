package com.cz.fitnessdiary.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.ui.widget.WeightChartView;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.utils.HealthScoreCalculator;
import com.cz.fitnessdiary.viewmodel.WeightDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeightRecordDetailFragment extends Fragment {

    private WeightDetailViewModel viewModel;
    private DetailRecordAdapter adapter;

    private TextView tvLatest;
    private TextView tvDelta;
    private TextView tvBmi;
    private TextView tvTargetWeight;
    private TextView tvTargetWeightLabel;
    private TextView tvEmpty;
    private ProgressBar progressLoading;
    private WeightChartView lineWeight;
    private MaterialButton btnTabWeek;
    private MaterialButton btnTabMonth;
    private MaterialButton btnTabYear;
    private int selectedTab = 0; // 0:Week, 1:Month, 2:Year

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_weight_record_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnAdd = view.findViewById(R.id.btn_add);
        MaterialButton btnQuick03 = view.findViewById(R.id.btn_quick_03);
        MaterialButton btnQuick05 = view.findViewById(R.id.btn_quick_05);
        MaterialButton btnQuickCustom = view.findViewById(R.id.btn_quick_custom);
        btnTabWeek = view.findViewById(R.id.btn_tab_week);
        btnTabMonth = view.findViewById(R.id.btn_tab_month);
        btnTabYear = view.findViewById(R.id.btn_tab_year);
        btnTabWeek.setCheckable(true);
        btnTabMonth.setCheckable(true);
        btnTabYear.setCheckable(true);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_records);

        tvLatest = view.findViewById(R.id.tv_latest);
        tvDelta = view.findViewById(R.id.tv_delta);
        tvBmi = view.findViewById(R.id.tv_bmi);
        tvTargetWeight = view.findViewById(R.id.tv_target_weight);
        tvTargetWeightLabel = view.findViewById(R.id.tv_target_weight_label);
        tvEmpty = view.findViewById(R.id.tv_empty);
        progressLoading = view.findViewById(R.id.progress_loading);
        lineWeight = view.findViewById(R.id.line_weight);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                showEditDialog((WeightRecord) item.payload);
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                WeightRecord record = (WeightRecord) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除记录")
                        .setMessage("确认删除这条体重记录？")
                        .setPositiveButton("删除", (d, w) -> viewModel.deleteWeight(record))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(WeightDetailViewModel.class);
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        viewModel.setSelectedDate(selectedDate);

        ExtendedFloatingActionButton fabAdd = view.findViewById(R.id.fab_add);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showAddDialog());
        fabAdd.setOnClickListener(v -> showAddDialog());
        btnQuick03.setOnClickListener(v -> quickAdd(0.3f));
        btnQuick05.setOnClickListener(v -> quickAdd(0.5f));
        btnQuickCustom.setOnClickListener(v -> showAddDialog());

        btnTabWeek.setOnClickListener(v -> switchTab(0));
        btnTabMonth.setOnClickListener(v -> switchTab(1));
        btnTabYear.setOnClickListener(v -> switchTab(2));
        applyTabSelection();

        MaterialButton btnEditTarget = view.findViewById(R.id.btn_edit_target);
        btnEditTarget.setOnClickListener(v -> showEditTargetWeightDialog());

        // Load target weight display
        loadTargetWeight();

        viewModel.getWeekSeries().observe(getViewLifecycleOwner(), values -> {
            if (selectedTab == 0)
                updateWeightChart(values);
        });
        viewModel.getMonthSeries().observe(getViewLifecycleOwner(), values -> {
            if (selectedTab == 1)
                updateWeightChart(values);
        });
        viewModel.getYearSeries().observe(getViewLifecycleOwner(), values -> {
            if (selectedTab == 2)
                updateWeightChart(values);
        });

        viewModel.getRecentRecords().observe(getViewLifecycleOwner(), this::renderRecords);
        viewModel.getBmi().observe(getViewLifecycleOwner(), bmi -> {
            if (bmi == null || bmi <= 0f)
                tvBmi.setText("BMI --");
            else
                tvBmi.setText(String.format(Locale.getDefault(), "BMI %.1f", bmi));
        });

        // 绑定体重分析卡片控件
        TextView tvWeightGoalStatus = view.findViewById(R.id.tv_weight_goal_status);
        TextView tvWeightTrendVal = view.findViewById(R.id.tv_weight_trend_val);
        TextView tvWeightProgressPct = view.findViewById(R.id.tv_weight_progress_pct);
        TextView tvWeightBmi = view.findViewById(R.id.tv_weight_bmi);
        TextView tvWeightLocalAdvice = view.findViewById(R.id.tv_weight_local_advice);
        MaterialButton btnWeightAiDiagnosis = view.findViewById(R.id.btn_weight_ai_diagnosis);

        viewModel.getWeightGoalType().observe(getViewLifecycleOwner(), goalType -> {
            if (tvWeightGoalStatus != null) {
                String typeStr = "保持";
                if (goalType == 0) typeStr = "减脂";
                else if (goalType == 1) typeStr = "增肌";
                tvWeightGoalStatus.setText(typeStr);
            }
        });
        viewModel.getWeightTrendVal().observe(getViewLifecycleOwner(), trend -> {
            if (tvWeightTrendVal != null) {
                String trendStr = String.format(Locale.getDefault(), "%+.1f kg", trend);
                tvWeightTrendVal.setText(trendStr);
                if (trend < 0) {
                    tvWeightTrendVal.setTextColor(0xFF4CAF50); // 绿色
                } else if (trend > 0) {
                    tvWeightTrendVal.setTextColor(0xFFF44336); // 红色
                } else {
                    tvWeightTrendVal.setTextColor(0xFF757575); // 灰色
                }
            }
        });
        viewModel.getGoalProgressPct().observe(getViewLifecycleOwner(), pct -> {
            if (tvWeightProgressPct != null) {
                tvWeightProgressPct.setText(String.format(Locale.getDefault(), "%.1f%%", pct));
            }
        });
        viewModel.getWeightBmi().observe(getViewLifecycleOwner(), bmiVal -> {
            if (tvWeightBmi != null) {
                String level = "正常";
                if (bmiVal < 18.5f) level = "偏瘦";
                else if (bmiVal >= 28f) level = "肥胖";
                else if (bmiVal >= 24f) level = "偏重";
                tvWeightBmi.setText(String.format(Locale.getDefault(), "当前最新 BMI：%.1f (%s)", bmiVal, level));
            }
        });
        viewModel.getWeightAdvice().observe(getViewLifecycleOwner(), advice -> {
            if (tvWeightLocalAdvice != null) {
                tvWeightLocalAdvice.setText(advice);
            }
        });

        if (btnWeightAiDiagnosis != null) {
            btnWeightAiDiagnosis.setOnClickListener(v -> {
                int gType = viewModel.getWeightGoalType().getValue() != null ? viewModel.getWeightGoalType().getValue() : 0;
                float trendVal = viewModel.getWeightTrendVal().getValue() != null ? viewModel.getWeightTrendVal().getValue() : 0f;
                float progressPct = viewModel.getGoalProgressPct().getValue() != null ? viewModel.getGoalProgressPct().getValue() : 0f;
                float bmiVal = viewModel.getWeightBmi().getValue() != null ? viewModel.getWeightBmi().getValue() : 0f;
                String adviceStr = viewModel.getWeightAdvice().getValue() != null ? viewModel.getWeightAdvice().getValue() : "";

                String goalName = "保持";
                if (gType == 0) goalName = "减脂";
                else if (gType == 1) goalName = "增肌";

                String prompt = String.format(Locale.getDefault(),
                        "用户体重指标与健身目标分析数据如下：\n" +
                        "- 健身目标：%s\n" +
                        "- 近30天体重增减变化：%+.1f kg\n" +
                        "- 目标进度完成率：%.1f%%\n" +
                        "- 当前最新 BMI：%.1f\n" +
                        "- 本地初步运动建议：%s\n\n" +
                        "请扮演资深运动营养学顾问与健身教练，生成一份体重与目标分析报告。包含：\n" +
                        "1. 当前体重与进度状态点评；\n" +
                        "2. 增肌或减脂的膳食红利与卡路里宏量摄入指导；\n" +
                        "3. 运动训练安排（有氧与力量比例）。\n" +
                        "回答要求结构清晰（Markdown格式展示），语气专业硬核且具鼓励性，且控制在 350 字以内。",
                        goalName, trendVal, progressPct, bmiVal, adviceStr);

                btnWeightAiDiagnosis.setEnabled(false);
                btnWeightAiDiagnosis.setText("✨ AI 体重健康诊断中...");

                String systemInstruction = "你是 FitnessDiary 运动营养与增肌减脂专家。请提供专业、简明、排版优美、字数在 350 字以内的中文评估报告。";
                com.cz.fitnessdiary.service.DeepSeekService.sendMessage(prompt, systemInstruction, false, null, new com.cz.fitnessdiary.service.AICallback() {
                    @Override
                    public void onSuccess(String response, String reasoning) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                btnWeightAiDiagnosis.setEnabled(true);
                                btnWeightAiDiagnosis.setText("✨ 生成 AI 体重与目标分析报告");
                                showReportDialog("✨ AI 体重与目标分析评估", response);
                            });
                        }
                    }

                    @Override
                    public void onPartialUpdate(String content, String reasoning) {}

                    @Override
                    public void onError(String error) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                btnWeightAiDiagnosis.setEnabled(true);
                                btnWeightAiDiagnosis.setText("✨ 生成 AI 体重与目标分析报告");
                                String localReport = generateLocalWeightReport(gType, trendVal, progressPct, bmiVal);
                                showReportDialog("📊 体重调理方案 (本地智能引擎)", localReport);
                            });
                        }
                    }
                });
            });
        }
    }

    private void updateWeightChart(List<Float> values) {
        if (values == null)
            return;
        lineWeight.setXAxisLabelInterval(selectedTab == 2 ? 4 : 0);
        lineWeight.setData(values, buildLabels(values.size()));
    }

    private void switchTab(int tab) {
        selectedTab = tab;
        applyTabSelection();
        if (selectedTab == 0) {
            updateWeightChart(viewModel.getWeekSeries().getValue());
        } else if (selectedTab == 1) {
            updateWeightChart(viewModel.getMonthSeries().getValue());
        } else {
            updateWeightChart(viewModel.getYearSeries().getValue());
        }
    }

    private void applyTabSelection() {
        if (btnTabWeek == null || btnTabMonth == null || btnTabYear == null) {
            return;
        }
        styleTabButton(btnTabWeek, selectedTab == 0);
        styleTabButton(btnTabMonth, selectedTab == 1);
        styleTabButton(btnTabYear, selectedTab == 2);
    }

    private void styleTabButton(MaterialButton button, boolean selected) {
        button.setChecked(selected);
        if (selected) {
            button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.cat_weight_primary));
            button.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        } else {
            button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.cat_weight_bg));
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.cat_weight_primary));
        }
    }

    private List<String> buildLabels(int size) {
        List<String> labels = new ArrayList<>();
        long dayStart = DateUtils
                .getDayStartTimestamp(viewModel.getSelectedDate().getValue() == null ? System.currentTimeMillis()
                        : viewModel.getSelectedDate().getValue());
        SimpleDateFormat df = new SimpleDateFormat("MM-dd", Locale.getDefault());
        SimpleDateFormat yf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());

        if (selectedTab == 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(dayStart);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            int offset = (dayOfWeek == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dayOfWeek);
            calendar.add(Calendar.DAY_OF_MONTH, offset);
            for (int i = 0; i < size; i++) {
                labels.add(df.format(new Date(calendar.getTimeInMillis())));
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }
        } else if (selectedTab == 1) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(dayStart);
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            for (int i = 0; i < size; i++) {
                labels.add(df.format(new Date(calendar.getTimeInMillis())));
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }
        } else {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(dayStart);
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.add(Calendar.MONTH, -(size - 1));
            for (int i = 0; i < size; i++) {
                labels.add(yf.format(new Date(calendar.getTimeInMillis())));
                calendar.add(Calendar.MONTH, 1);
            }
        }
        return labels;
    }

    private void quickAdd(float delta) {
        List<DetailRecordAdapter.Item> current = adapter.getCurrentList();
        if (current == null || current.isEmpty()) {
            Toast.makeText(getContext(), "请先添加一条体重记录", Toast.LENGTH_SHORT).show();
            return;
        }
        WeightRecord latest = (WeightRecord) current.get(0).payload;
        viewModel.addWeight(latest.getWeight() + delta, "快捷");
    }

    private void renderRecords(List<WeightRecord> records) {
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        if (records != null) {
            for (WeightRecord record : records) {
                items.add(new DetailRecordAdapter.Item(
                        record.getId(),
                        "体重",
                        format.format(new Date(record.getTimestamp())),
                        String.format(Locale.getDefault(), "%.1f kg", record.getWeight()),
                        record.getNote() == null ? "点击编辑，长按删除" : record.getNote(),
                        R.drawable.ic_hero_weight,
                        record));
            }
        }
        adapter.submitList(items);
        if (items.isEmpty()) {
            tvLatest.setText("最近体重 -- kg");
            tvDelta.setText("较上次 --");
        } else {
            WeightRecord latest = (WeightRecord) items.get(0).payload;
            tvLatest.setText(String.format(Locale.getDefault(), "最近体重 %.1f kg", latest.getWeight()));
            if (items.size() >= 2) {
                WeightRecord prev = (WeightRecord) items.get(1).payload;
                float delta = latest.getWeight() - prev.getWeight();
                tvDelta.setText(String.format(Locale.getDefault(), "较上次 %+,.1f kg", delta));
            } else {
                tvDelta.setText("较上次 --");
            }
        }

        progressLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddDialog() {
        EditText et = new EditText(requireContext());
        et.setHint("体重(kg)");
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("添加体重").setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        float value = Float.parseFloat(et.getText().toString().trim());
                        if (value <= 0f) {
                            Toast.makeText(getContext(), "请输入大于 0 的数值", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        viewModel.addWeight(value, null);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showEditDialog(WeightRecord record) {
        EditText et = new EditText(requireContext());
        et.setHint("体重(kg)");
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setText(String.format(Locale.getDefault(), "%.1f", record.getWeight()));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("编辑体重").setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        float value = Float.parseFloat(et.getText().toString().trim());
                        if (value <= 0f) {
                            Toast.makeText(getContext(), "请输入大于 0 的数值", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        record.setWeight(value);
                        viewModel.updateWeight(record);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    // ═══════════════════════ Target Weight ═══════════════════════

    private void loadTargetWeight() {
        // First check custom target from SharedPreferences (no DB needed)
        SharedPreferences prefs = requireContext().getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE);
        float customTarget = prefs.getFloat("target_weight_kg", -1f);
        if (customTarget > 0) {
            displayTargetWeight(customTarget, true);
            return;
        }

        // Auto-calculate: needs DB access -> use background thread
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());
                User user = db.userDao().getUserSync();
                if (user != null && isAdded()) {
                    float autoTarget = computeTargetWeightFromUser(user);
                    requireActivity().runOnUiThread(() -> {
                        if (isAdded()) {
                            displayTargetWeight(autoTarget, false);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void displayTargetWeight(float target, boolean isCustom) {
        if (!isAdded()) return;
        if (target > 0) {
            tvTargetWeight.setText(String.format(Locale.getDefault(), "目标体重: %.1f kg", target));
            tvTargetWeightLabel.setText(isCustom ? "目标体重(自定义)" : "目标体重(基于BMI健康范围)");
        } else {
            tvTargetWeight.setText("目标体重: 请先录入身高体重");
            tvTargetWeightLabel.setText("");
        }
    }

    private float computeTargetWeightFromUser(User user) {
        if (user == null || user.getWeight() <= 0 || user.getHeight() <= 0) return 0f;

        HealthScoreCalculator.UserProfile profile = new HealthScoreCalculator.UserProfile();
        profile.weightKg = user.getWeight();
        profile.heightCm = user.getHeight();

        // Determine goal type
        int goalType = user.getGoalType();
        if (goalType == 0) {
            profile.goalType = "lose";
        } else if (goalType == 1) {
            profile.goalType = "gain";
        } else {
            profile.goalType = "maintain";
        }

        // Check for custom target in prefs
        SharedPreferences prefs = requireContext().getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE);
        float customTarget = prefs.getFloat("target_weight_kg", -1f);
        if (customTarget > 0) {
            return customTarget;
        }

        return HealthScoreCalculator.computeTargetWeight(profile);
    }

    private void showEditTargetWeightDialog() {
        SharedPreferences prefs = requireContext().getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE);
        float currentTarget = prefs.getFloat("target_weight_kg", -1f);

        EditText et = new EditText(requireContext());
        et.setHint("目标体重(kg)");
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (currentTarget > 0) {
            et.setText(String.format(Locale.getDefault(), "%.1f", currentTarget));
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑目标体重")
                .setMessage("设置您的目标体重。留空或设为0将使用BMI健康范围自动计算。")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String input = et.getText().toString().trim();
                    if (input.isEmpty()) {
                        // Clear custom target to use auto-calculation
                        prefs.edit().remove("target_weight_kg").apply();
                    } else {
                        try {
                            float value = Float.parseFloat(input);
                            if (value <= 0) {
                                prefs.edit().remove("target_weight_kg").apply();
                            } else {
                                prefs.edit().putFloat("target_weight_kg", value).apply();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(getContext(), "请输入有效数字", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    loadTargetWeight();
                    Toast.makeText(getContext(), "目标体重已更新", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String generateLocalWeightReport(int gType, float trendVal, float progressPct, float bmiVal) {
        StringBuilder sb = new StringBuilder();
        String goalName = (gType == 0) ? "减脂" : ((gType == 1) ? "增肌" : "健康保持");
        
        sb.append("### 🩺 体重趋势与 ").append(goalName).append(" 评估\n");
        sb.append("用户当前健身目标为**").append(goalName).append("**，近30天体重变化为 **").append(String.format(Locale.getDefault(), "%+.1f kg", trendVal)).append("**，当前进度为 **").append(String.format(Locale.getDefault(), "%.1f%%", progressPct)).append("**，BMI 值为 ").append(String.format(Locale.getDefault(), "%.1f", bmiVal)).append("。\n\n");

        sb.append("### 🥗 膳食营养与热量赤字指导\n");
        if (gType == 0) { // 减脂
            sb.append("- **热量缺口**：保持每日 300-500 kcal 热量赤字，保证碳水摄入占总热量 45%-50%。\n");
            sb.append("- **优质蛋白**：摄入 1.5g/kg 体重的蛋白质（鸡胸肉、鱼虾、蛋清），减缓因掉体重带来的肌肉流失。\n");
        } else if (gType == 1) { // 增肌
            sb.append("- **热量盈余**：维持每日 200-300 kcal 的热量多余储备，碳水占 55% 以上以保证充沛的训练体能。\n");
            sb.append("- **足量蛋白与碳水**：运动后 30 分钟内补充 20-30g 蛋白质和 50g 快碳，促进肌糖原合成。\n");
        } else {
            sb.append("- **能量平衡**：每日卡路里摄入与总消耗（TDEE）齐平，维持三大宏量营养素合理占比。\n");
        }

        sb.append("\n### 🏋️ 运动训练安排 (有氧与力量比例)\n");
        if (gType == 0) { // 减脂
            sb.append("- **阻力训练**：每周进行 3-4 次力量抗阻练习（占 60% 训练精力），先无氧后有氧。\n");
            sb.append("- **HIIT与有氧**：每周配合 2-3 次 20-30 分钟的慢跑或划船机，加速体脂消耗。\n");
        } else if (gType == 1) { // 增肌
            sb.append("- **大重量多关节复合动作**：以深蹲、硬拉、卧推等核心大肌群训练为主（占 85% 训练精力）。\n");
            sb.append("- **控制有氧**：每周最多进行 1 次中低强度有氧（如散步），避免能量被过多消耗。\n");
        } else {
            sb.append("- **混合训练**：每周 2 次中等强度抗阻与 2 次常规慢跑或游泳，维持心肺与肌肉适能。");
        }
        return sb.toString();
    }

    private void showReportDialog(String title, String content) {
        if (getContext() == null) return;
        TextView tv = new TextView(getContext());
        tv.setTextSize(14f);
        tv.setPadding(48, 36, 48, 36);
        tv.setLineSpacing(1.3f, 1.3f);
        
        int textColor = 0xFF212121;
        try {
            textColor = getResources().getColor(R.color.text_primary);
        } catch (Exception ignored) {}
        tv.setTextColor(textColor);

        String formatted = content
                .replace("\n", "<br/>")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("### (.*?)<br/>", "<b><font color='#4CAF50'>$1</font></b><br/>");

        tv.setText(android.text.Html.fromHtml(formatted, android.text.Html.FROM_HTML_MODE_LEGACY));

        android.widget.ScrollView sv = new android.widget.ScrollView(getContext());
        sv.addView(tv);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("我知道了", null)
                .show();
    }
}
