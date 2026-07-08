package com.cz.fitnessdiary.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cz.fitnessdiary.utils.UnitUtils;

public class BodyDataDetailBottomSheetFragment extends BottomSheetDialogFragment {

    // Tab 布局
    private TabLayout tabLayout;
    private View layoutTabData, layoutTabChart;

    // 数据 Tab 控件
    private TextView tvValWeight, tvValTargetWeight, tvValBodyFat, tvValHeight;
    private TextView tvMeasureNeck, tvMeasureChest, tvMeasureArm, tvMeasureHip;
    private TextView tvMeasureShoulder, tvMeasureWaist, tvMeasureThigh, tvMeasureCalf;
    private TextView tvValGenderAge, tvValBmi, tvValBmr, tvValTdee;

    // 图表 Tab 控件
    private LinearLayout layoutChartIndicators;
    private TextView tvChartLatest, tvChartDiff, tvChartAvg;
    private LineChart chartBodyMetric;
    private View tvChartEmpty;
    private View btnQuickAddRecord;

    // 数据库与上下文
    private AppDatabase db;
    private User currentUser;
    private OnDataUpdatedListener listener;
    private SharedPreferences sharedPrefs;

    // 当前选中的图表指标 (默认为 "体重")
    private String selectedIndicator = "体重";

    public interface OnDataUpdatedListener {
        void onUpdated();
    }

    public void setOnDataUpdatedListener(OnDataUpdatedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_body_data_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = AppDatabase.getInstance(requireContext());
        sharedPrefs = requireContext().getSharedPreferences("fitness_body_prefs", Context.MODE_PRIVATE);

        // 绑定 Tab 切换
        tabLayout = view.findViewById(R.id.tab_layout);
        layoutTabData = view.findViewById(R.id.layout_tab_data);
        layoutTabChart = view.findViewById(R.id.layout_tab_chart);

        // 绑定数据 Tab 视图
        tvValWeight = view.findViewById(R.id.tv_val_weight);
        tvValTargetWeight = view.findViewById(R.id.tv_val_target_weight);
        tvValBodyFat = view.findViewById(R.id.tv_val_body_fat);
        tvValHeight = view.findViewById(R.id.tv_val_height);
        tvMeasureNeck = view.findViewById(R.id.tv_measure_neck);
        tvMeasureChest = view.findViewById(R.id.tv_measure_chest);
        tvMeasureArm = view.findViewById(R.id.tv_measure_arm);
        tvMeasureHip = view.findViewById(R.id.tv_measure_hip);
        tvMeasureShoulder = view.findViewById(R.id.tv_measure_shoulder);
        tvMeasureWaist = view.findViewById(R.id.tv_measure_waist);
        tvMeasureThigh = view.findViewById(R.id.tv_measure_thigh);
        tvMeasureCalf = view.findViewById(R.id.tv_measure_calf);
        tvValGenderAge = view.findViewById(R.id.tv_val_gender_age);
        tvValBmi = view.findViewById(R.id.tv_val_bmi);
        tvValBmr = view.findViewById(R.id.tv_val_bmr);
        tvValTdee = view.findViewById(R.id.tv_val_tdee);

        // 绑定图表 Tab 视图
        layoutChartIndicators = view.findViewById(R.id.layout_chart_indicators);
        tvChartLatest = view.findViewById(R.id.tv_chart_latest);
        tvChartDiff = view.findViewById(R.id.tv_chart_diff);
        tvChartAvg = view.findViewById(R.id.tv_chart_avg);
        chartBodyMetric = view.findViewById(R.id.chart_body_metric);
        tvChartEmpty = view.findViewById(R.id.tv_chart_empty);
        btnQuickAddRecord = view.findViewById(R.id.btn_quick_add_record);

        setupTabs();
        loadBaseUserData();
        setupMeasureClickListeners();
        setupChartIndicators();

        // 健康透视看板点击 → 弹出解释页面
        View cardHealthInsight = view.findViewById(R.id.card_health_insight);
        if (cardHealthInsight != null) {
            cardHealthInsight.setOnClickListener(v ->
                new HealthInsightExplainDialog().show(getChildFragmentManager(), "HealthInsightExplain"));
        }
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    layoutTabData.setVisibility(View.VISIBLE);
                    layoutTabChart.setVisibility(View.GONE);
                    loadBaseUserData(); // 切换回来时刷新数据
                } else {
                    layoutTabData.setVisibility(View.GONE);
                    layoutTabChart.setVisibility(View.VISIBLE);
                    refreshChartData(); // 切换到图表页时刷新图表
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadBaseUserData() {
        new Thread(() -> {
            currentUser = db.userDao().getUserSync();
            if (currentUser == null) {
                currentUser = new User("新用户", 170.0f, 65.0f, true);
                db.userDao().insert(currentUser);
            }

            // 读取各项围度最新记录
            Map<String, String> values = new HashMap<>();
            String[] types = {"脖围", "胸围", "臂围", "臀围", "肩宽", "腰围", "大腿围", "小腿围", "体脂率"};
            for (String t : types) {
                BodyMeasurement latest = getLatestMeasurementSync(t);
                if (latest != null) {
                    values.put(t, String.format(Locale.getDefault(), "%.1f %s", latest.getValue(), latest.getUnit()));
                } else {
                    values.put(t, "--");
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 填充身高
                    tvValHeight.setText(String.format(Locale.getDefault(), "%.1f 厘米", currentUser.getHeight()));

                    // 填充体重
                    tvValWeight.setText(UnitUtils.formatWeight(currentUser.getWeight(), requireContext()) + " " + UnitUtils.getWeightUnitSymbol(requireContext()));

                    // 目标体重 (与首页打卡/WeightRecordDetailFragment 强同步)
                    SharedPreferences scorePrefs = requireContext().getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE);
                    float targetW = scorePrefs.getFloat("target_weight_kg", currentUser.getWeight() > 0 ? currentUser.getWeight() * 0.9f : 60f);
                    tvValTargetWeight.setText(UnitUtils.formatWeight(targetW, requireContext()) + " " + UnitUtils.getWeightUnitSymbol(requireContext()));

                    // 体脂率与连线数据
                    tvValBodyFat.setText(values.get("体脂率").replace(" %", "%"));
                    tvMeasureNeck.setText("脖围: " + values.get("脖围"));
                    tvMeasureChest.setText("胸围: " + values.get("胸围"));
                    tvMeasureArm.setText("臂围: " + values.get("臂围"));
                    tvMeasureHip.setText("臀围: " + values.get("臀围"));
                    tvMeasureShoulder.setText("肩宽: " + values.get("肩宽"));
                    tvMeasureWaist.setText("腰围: " + values.get("腰围"));
                    tvMeasureThigh.setText("大腿: " + values.get("大腿围"));
                    tvMeasureCalf.setText("小腿: " + values.get("小腿围"));

                    // 1. 性别与年龄看板
                    String genderStr = currentUser.getGender() == 1 ? "男" : "女";
                    tvValGenderAge.setText(String.format(Locale.getDefault(), "%d 岁 · %s", currentUser.getAge(), genderStr));

                    // 2. BMI 质量指数看板
                    double heightM = currentUser.getHeight() / 100.0;
                    double bmi = (heightM > 0) ? (currentUser.getWeight() / (heightM * heightM)) : 0;
                    String bmiLevel = "正常";
                    if (bmi < 18.5) {
                        bmiLevel = "偏瘦";
                    } else if (bmi < 24) {
                        bmiLevel = "正常";
                    } else if (bmi < 28) {
                        bmiLevel = "超重";
                    } else {
                        bmiLevel = "肥胖";
                    }
                    tvValBmi.setText(String.format(Locale.getDefault(), "%.1f (%s)", bmi, bmiLevel));

                    // 3. 基础代谢 BMR 看板
                    double bmrVal;
                    if (currentUser.getGender() == 1) {
                        bmrVal = 10.0 * currentUser.getWeight() + 6.25 * currentUser.getHeight() - 5.0 * currentUser.getAge() + 5.0;
                    } else {
                        bmrVal = 10.0 * currentUser.getWeight() + 6.25 * currentUser.getHeight() - 5.0 * currentUser.getAge() - 161.0;
                    }
                    tvValBmr.setText(String.format(Locale.getDefault(), "%.0f kcal", bmrVal));

                    // 4. 每日总能耗 TDEE 看板
                    double tdeeVal = bmrVal * currentUser.getActivityLevel();
                    tvValTdee.setText(String.format(Locale.getDefault(), "%.0f kcal", tdeeVal));
                });
            }
        }).start();
    }

    private void setupMeasureClickListeners() {
        // 身高卡片点击 - 联动拉起编辑基础身体数据 Dialog
        tvValHeight.setOnClickListener(v -> showBaseInfoDialog());

        // 体重卡片点击 - 录入当前体重
        tvValWeight.setOnClickListener(v -> showInputDialog("记录体重", "当前体重 (" + UnitUtils.getWeightUnitSymbol(requireContext()) + ")", val -> {
            new Thread(() -> {
                currentUser.setWeight(val);
                db.userDao().update(currentUser);
                WeightRecord wr = new WeightRecord(val, System.currentTimeMillis(), "身体数据中心同步体重");
                db.weightRecordDao().insert(wr);
                loadBaseUserData();
                if (listener != null) listener.onUpdated();
            }).start();
        }));

        // 目标体重点击 - 设置目标体重
        tvValTargetWeight.setOnClickListener(v -> showInputDialog("目标体重", "设定目标体重 (" + UnitUtils.getWeightUnitSymbol(requireContext()) + ")", val -> {
            SharedPreferences scorePrefs = requireContext().getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE);
            scorePrefs.edit().putFloat("target_weight_kg", val).apply();
            loadBaseUserData();
            if (listener != null) listener.onUpdated();
        }));

        // 体脂率点击
        tvValBodyFat.setOnClickListener(v -> showInputDialog("记录体脂率", "当前体脂率 (%)", val -> {
            saveMeasurement("BODY_FAT", val, "%");
        }));

        // 各围度点击
        tvMeasureNeck.setOnClickListener(v -> showInputDialog("记录脖围", "当前脖围 (cm)", val -> saveMeasurement("脖围", val, "cm")));
        tvMeasureChest.setOnClickListener(v -> showInputDialog("记录胸围", "当前胸围 (cm)", val -> saveMeasurement("胸围", val, "cm")));
        tvMeasureArm.setOnClickListener(v -> showInputDialog("记录臂围", "当前臂围 (cm)", val -> saveMeasurement("臂围", val, "cm")));
        tvMeasureHip.setOnClickListener(v -> showInputDialog("记录臀围", "当前臀围 (cm)", val -> saveMeasurement("臀围", val, "cm")));
        tvMeasureShoulder.setOnClickListener(v -> showInputDialog("记录肩宽", "当前肩宽 (cm)", val -> saveMeasurement("肩宽", val, "cm")));
        tvMeasureWaist.setOnClickListener(v -> showInputDialog("记录腰围", "当前腰围 (cm)", val -> saveMeasurement("腰围", val, "cm")));
        tvMeasureThigh.setOnClickListener(v -> showInputDialog("记录大腿围", "大腿围 (cm)", val -> saveMeasurement("大腿围", val, "cm")));
        tvMeasureCalf.setOnClickListener(v -> showInputDialog("记录小腿围", "小腿围 (cm)", val -> saveMeasurement("小腿围", val, "cm")));
    }

    private interface InputCallback {
        void onInput(float value);
    }

    private void showInputDialog(String title, String hint, InputCallback callback) {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint(hint);
        et.setPadding(48, 24, 48, 24);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 24, 48, 0);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        et.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        container.addView(et);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(container)
                .setPositiveButton("保存", (d, w) -> {
                    String input = et.getText().toString().trim();
                    if (!input.isEmpty()) {
                        try {
                            float val = Float.parseFloat(input);
                            if (val > 0) {
                                callback.onInput(val);
                            }
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveMeasurement(String type, float val, String unit) {
        new Thread(() -> {
            String dbType = mapIndicatorToDbType(type);
            BodyMeasurement bm = new BodyMeasurement(dbType, val, unit, System.currentTimeMillis(), "身体数据中心连线录入");
            db.bodyMeasurementDao().insert(bm);
            loadBaseUserData();
            if (listener != null) listener.onUpdated();
        }).start();
    }

    private void showBaseInfoDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        EditText etH = new EditText(requireContext());
        etH.setHint("身高 (cm)");
        etH.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etH.setText(currentUser != null ? String.valueOf(currentUser.getHeight()) : "170.0");
        etH.setLayoutParams(lp);
        layout.addView(etH);

        EditText etAge = new EditText(requireContext());
        etAge.setHint("年龄");
        etAge.setInputType(InputType.TYPE_CLASS_NUMBER);
        etAge.setText(currentUser != null ? String.valueOf(currentUser.getAge()) : "25");
        etAge.setPadding(0, 24, 0, 0);
        etAge.setLayoutParams(lp);
        layout.addView(etAge);

        AutoCompleteTextView spinGender = new AutoCompleteTextView(requireContext());
        spinGender.setHint("性别");
        spinGender.setText(currentUser != null && currentUser.getGender() == 1 ? "男" : "女", false);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, new String[]{"男", "女"});
        spinGender.setAdapter(adapter);
        spinGender.setPadding(0, 24, 0, 0);
        spinGender.setLayoutParams(lp);
        layout.addView(spinGender);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑基础身体数据")
                .setView(layout)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        float h = Float.parseFloat(etH.getText().toString().trim());
                        int age = Integer.parseInt(etAge.getText().toString().trim());
                        int gender = "男".equals(spinGender.getText().toString()) ? 1 : 0;
                        new Thread(() -> {
                            currentUser.setHeight(h);
                            currentUser.setAge(age);
                            currentUser.setGender(gender);
                            db.userDao().update(currentUser);
                            loadBaseUserData();
                            if (listener != null) listener.onUpdated();
                        }).start();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // --- 图表 Tab 业务 ---
    private final String[] indicators = {"体重", "体脂率", "腰围", "臀围", "胸围", "大腿围", "小腿围", "臂围", "肩宽", "脖围"};

    private void setupChartIndicators() {
        layoutChartIndicators.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (String key : indicators) {
            TextView tv = new TextView(requireContext());
            tv.setText(key);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding((int)(16 * density), (int)(8 * density), (int)(16 * density), (int)(8 * density));
            tv.setClickable(true);
            tv.setFocusable(true);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins((int)(6 * density), (int)(4 * density), (int)(6 * density), (int)(4 * density));
            tv.setLayoutParams(lp);
            
            // 背景高亮
            updateIndicatorSelectionBg(tv, key.equals(selectedIndicator));

            tv.setOnClickListener(v -> {
                selectedIndicator = key;
                setupChartIndicators(); // 重新刷底色
                refreshChartData();
            });
            layoutChartIndicators.addView(tv);
        }

        btnQuickAddRecord.setOnClickListener(v -> {
            showInputDialog("记录" + selectedIndicator, "当前数值", val -> {
                if ("体重".equals(selectedIndicator)) {
                    new Thread(() -> {
                        currentUser.setWeight(val);
                        db.userDao().update(currentUser);
                        WeightRecord wr = new WeightRecord(val, System.currentTimeMillis(), "图表页快捷录入");
                        db.weightRecordDao().insert(wr);
                        refreshChartData();
                        if (listener != null) listener.onUpdated();
                    }).start();
                } else {
                    String dbType = "体脂率".equals(selectedIndicator) ? "BODY_FAT" : selectedIndicator;
                    String unit = "体脂率".equals(selectedIndicator) ? "%" : "cm";
                    new Thread(() -> {
                        BodyMeasurement bm = new BodyMeasurement(dbType, val, unit, System.currentTimeMillis(), "图表页快捷录入");
                        db.bodyMeasurementDao().insert(bm);
                        refreshChartData();
                    }).start();
                }
            });
        });
    }

    private void updateIndicatorSelectionBg(TextView tv, boolean selected) {
        float density = getResources().getDisplayMetrics().density;
        if (selected) {
            android.graphics.drawable.GradientDrawable selectedBg = new android.graphics.drawable.GradientDrawable();
            selectedBg.setColor(getResources().getColor(R.color.fitnessdiary_primary, null));
            selectedBg.setCornerRadius(18 * density);
            tv.setBackground(selectedBg);
            tv.setTextColor(android.graphics.Color.WHITE);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            android.graphics.drawable.GradientDrawable unselectedBg = new android.graphics.drawable.GradientDrawable();
            unselectedBg.setColor(getResources().getColor(R.color.fitnessdiary_surface, null));
            unselectedBg.setCornerRadius(18 * density);
            tv.setBackground(unselectedBg);
            tv.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tv.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void refreshChartData() {
        new Thread(() -> {
            List<Entry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("M/d", Locale.getDefault());
            float latestVal = 0, prevVal = 0, sumVal = 0;
            // Determine display unit for chart values - convert weight if needed
            final String weightUnitPref = getContext() != null ? UnitUtils.getWeightUnit(getContext()) : "kg";
            String unit = "体重".equals(selectedIndicator) ? UnitUtils.getWeightUnitSymbol(weightUnitPref) : ("体脂率".equals(selectedIndicator) ? "%" : "cm");

            if ("体重".equals(selectedIndicator)) {
                // 读取体重历史 (过去 30 天，升序)
                long today = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
                long startTs = today - 30 * 86400000L;
                List<WeightRecord> records = db.weightRecordDao().getRecordsByDateRangeSync(startTs, today + 86400000L);
                if (records != null && !records.isEmpty()) {
                    for (int i = 0; i < records.size(); i++) {
                        WeightRecord r = records.get(i);
                        entries.add(new Entry(i, r.getWeight()));
                        labels.add(sdf.format(r.getTimestamp()));
                        sumVal += r.getWeight();
                    }
                    latestVal = records.get(records.size() - 1).getWeight();
                    if (records.size() > 1) {
                        prevVal = records.get(records.size() - 2).getWeight();
                    }
                }
            } else {
                // 读取围度历史
                List<BodyMeasurement> queryList = db.bodyMeasurementDao().getByDateRangeSync(0, System.currentTimeMillis() + 86400000L);
                String dbType = mapIndicatorToDbType(selectedIndicator);
                List<BodyMeasurement> filtered = new ArrayList<>();
                for (BodyMeasurement bm : queryList) {
                    String type = bm.getMeasurementType();
                    if (dbType.equals(type) || selectedIndicator.equals(type) ||
                        ("ARM".equals(dbType) && "手臂围".equals(type))) {
                        filtered.add(bm);
                    }
                }
                if (!filtered.isEmpty()) {
                    // 按时间升序排序
                    Collections.sort(filtered, (b1, b2) -> Long.compare(b1.getTimestamp(), b2.getTimestamp()));
                    for (int i = 0; i < filtered.size(); i++) {
                        BodyMeasurement r = filtered.get(i);
                        entries.add(new Entry(i, r.getValue()));
                        labels.add(sdf.format(r.getTimestamp()));
                        sumVal += r.getValue();
                    }
                    latestVal = filtered.get(filtered.size() - 1).getValue();
                    if (filtered.size() > 1) {
                        prevVal = filtered.get(filtered.size() - 2).getValue();
                    }
                }
            }

            final List<Entry> finalEntries = entries;
            final List<String> finalLabels = labels;
            // Convert weight values for display text (chart entries stay in kg)
            float displayLatest = latestVal, displayPrev = prevVal, displaySum = sumVal;
            if ("体重".equals(selectedIndicator) && "lbs".equals(weightUnitPref)) {
                displayLatest = com.cz.fitnessdiary.utils.UnitUtils.convertWeight(displayLatest, "lbs");
                displayPrev = com.cz.fitnessdiary.utils.UnitUtils.convertWeight(displayPrev, "lbs");
                displaySum = com.cz.fitnessdiary.utils.UnitUtils.convertWeight(displaySum, "lbs");
            }
            final float fLatest = displayLatest, fPrev = displayPrev, fSum = displaySum;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (finalEntries.isEmpty()) {
                        tvChartEmpty.setVisibility(View.VISIBLE);
                        chartBodyMetric.setVisibility(View.GONE);
                        tvChartLatest.setText("--");
                        tvChartDiff.setText("--");
                        tvChartAvg.setText("--");
                        return;
                    }

                    tvChartEmpty.setVisibility(View.GONE);
                    chartBodyMetric.setVisibility(View.VISIBLE);

                    // 渲染看板数据
                    tvChartLatest.setText(String.format(Locale.getDefault(), "%.1f%s", fLatest, unit));
                    if (fPrev > 0) {
                        float diff = fLatest - fPrev;
                        String sign = diff > 0 ? "+" : "";
                        tvChartDiff.setText(String.format(Locale.getDefault(), "%s%.1f%s", sign, diff, unit));
                        // 较前次变动配色：增加如果是负数采用绿色（代表减重/减少围度成功，在运动App里这一般是好的变动）的友好视觉引导
                        if ("体重".equals(selectedIndicator) || "体脂率".equals(selectedIndicator) || "腰围".equals(selectedIndicator)) {
                            tvChartDiff.setTextColor(diff > 0 ? 0xFFFF5722 : 0xFF4CAF50); // 减重为绿，增重为橙
                        } else {
                            tvChartDiff.setTextColor(diff > 0 ? 0xFF4CAF50 : 0xFFFF5722); // 围度增长（如胸/臂/肩）为绿，缩水为橙
                        }
                    } else {
                        tvChartDiff.setText("--");
                        tvChartDiff.setTextColor(getResources().getColor(R.color.text_secondary, null));
                    }
                    tvChartAvg.setText(String.format(Locale.getDefault(), "%.1f%s", (fSum / finalEntries.size()), unit));

                    // 绘制曲线 (品牌绿主打，辅以贝塞尔平滑与镂空锚点)
                    int primaryColor = getResources().getColor(R.color.fitnessdiary_primary, null);
                    LineDataSet dataSet = new LineDataSet(finalEntries, selectedIndicator);
                    dataSet.setColor(primaryColor);
                    dataSet.setLineWidth(2.2f);
                    dataSet.setCircleColor(primaryColor);
                    dataSet.setCircleRadius(4.5f);
                    dataSet.setDrawCircleHole(true);
                    dataSet.setCircleHoleColor(android.graphics.Color.WHITE);
                    dataSet.setCircleHoleRadius(2.2f);
                    
                    // 面积渐变填充 (20% 不透明度淡绿自然过渡到全透明)
                    dataSet.setDrawFilled(true);
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {
                            (primaryColor & 0x00FFFFFF) | 0x33000000,
                            (primaryColor & 0x00FFFFFF) | 0x00000000
                        }
                    );
                    dataSet.setFillDrawable(gd);
                    
                    dataSet.setValueTextSize(9f);
                    dataSet.setValueTextColor(getResources().getColor(R.color.text_secondary, null));
                    dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                    LineData lineData = new LineData(dataSet);
                    chartBodyMetric.setData(lineData);

                    // X 轴美化
                    XAxis xAxis = chartBodyMetric.getXAxis();
                    xAxis.setValueFormatter(new IndexAxisValueFormatter(finalLabels));
                    xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                    xAxis.setGranularity(1f);
                    xAxis.setDrawGridLines(false);
                    xAxis.setTextColor(getResources().getColor(R.color.text_secondary, null));
                    xAxis.setTextSize(9f);

                    // Y 轴美化 (移除多余轴线，设定 15% 动态留白，防顶格贴边)
                    YAxis yAxis = chartBodyMetric.getAxisLeft();
                    yAxis.setSpaceTop(15f);
                    yAxis.setSpaceBottom(15f);
                    yAxis.setDrawGridLines(true);
                    yAxis.setGridColor(0x0D000000); // 极弱透明度的水平网格线
                    yAxis.setTextColor(getResources().getColor(R.color.text_secondary, null));
                    yAxis.setTextSize(9f);
                    yAxis.setDrawAxisLine(false);

                    chartBodyMetric.getAxisRight().setEnabled(false);
                    chartBodyMetric.getDescription().setEnabled(false);
                    chartBodyMetric.getLegend().setEnabled(false);
                    chartBodyMetric.animateY(600);
                    chartBodyMetric.invalidate();
                });
            }

        }).start();
    }

    private BodyMeasurement getLatestMeasurementSync(String uiType) {
        String dbType = mapIndicatorToDbType(uiType);
        BodyMeasurement m1 = db.bodyMeasurementDao().getLatestByTypeSync(dbType);
        BodyMeasurement m2 = null;
        if (!uiType.equals(dbType)) {
            m2 = db.bodyMeasurementDao().getLatestByTypeSync(uiType);
        }
        BodyMeasurement m3 = null;
        if ("臂围".equals(uiType) || "ARM".equals(dbType)) {
            m3 = db.bodyMeasurementDao().getLatestByTypeSync("手臂围");
        }
        
        BodyMeasurement latest = m1;
        if (m2 != null) {
            if (latest == null || m2.getTimestamp() > latest.getTimestamp()) {
                latest = m2;
            }
        }
        if (m3 != null) {
            if (latest == null || m3.getTimestamp() > latest.getTimestamp()) {
                latest = m3;
            }
        }
        return latest;
    }

    private String mapIndicatorToDbType(String indicator) {
        if ("体脂率".equals(indicator) || "BODY_FAT".equals(indicator)) return "BODY_FAT";
        if ("胸围".equals(indicator) || "CHEST".equals(indicator)) return "CHEST";
        if ("腰围".equals(indicator) || "WAIST".equals(indicator)) return "WAIST";
        if ("臀围".equals(indicator) || "HIP".equals(indicator)) return "HIP";
        if ("臂围".equals(indicator) || "ARM".equals(indicator)) return "ARM";
        if ("大腿围".equals(indicator) || "THIGH".equals(indicator)) return "THIGH";
        if ("小腿围".equals(indicator) || "CALF".equals(indicator)) return "CALF";
        return indicator;
    }
}
