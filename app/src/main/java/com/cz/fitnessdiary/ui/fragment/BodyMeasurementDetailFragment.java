package com.cz.fitnessdiary.ui.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.ui.widget.MeasurementChartView;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.BodyMeasurementDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BodyMeasurementDetailFragment extends Fragment {

    private BodyMeasurementDetailViewModel viewModel;
    private DetailRecordAdapter adapter;
    private MeasurementChartView chartView;
    private TextView tvHeaderSummary, tvWhr, tvBodyfatZone, tvEmpty, tvMeasureMethod;
    private MaterialButton btnTabWeek, btnTabMonth, btnTabYear;
    private ChipGroup chipTypeGroup;
    private int selectedTab = 0;

    private static final String[] TYPES = {"BODY_FAT", "CHEST", "WAIST", "HIP", "ARM", "THIGH", "CALF"};
    private static final String[] TYPE_NAMES = {"体脂率", "胸围", "腰围", "臀围", "臂围", "大腿围", "小腿围"};
    private static final String[] TYPE_UNITS = {"%", "cm", "cm", "cm", "cm", "cm", "cm"};
    private static final int[] TYPE_COLORS = {
            0xFF00BCD4, 0xFF00BCD4, 0xFF00BCD4, 0xFF00BCD4, 0xFF00BCD4, 0xFF00BCD4, 0xFF00BCD4
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_body_measurement_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnTabWeek = view.findViewById(R.id.btn_tab_week);
        btnTabMonth = view.findViewById(R.id.btn_tab_month);
        btnTabYear = view.findViewById(R.id.btn_tab_year);
        chartView = view.findViewById(R.id.chart_measurement);
        tvHeaderSummary = view.findViewById(R.id.tv_header_summary);
        tvWhr = view.findViewById(R.id.tv_whr);
        tvBodyfatZone = view.findViewById(R.id.tv_bodyfat_zone);
        tvEmpty = view.findViewById(R.id.tv_empty);
        tvMeasureMethod = view.findViewById(R.id.tv_measure_method);
        chipTypeGroup = view.findViewById(R.id.chip_measure_type);
        RecyclerView rvRecords = view.findViewById(R.id.rv_records);
        ExtendedFloatingActionButton fabAdd = view.findViewById(R.id.fab_add);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                showEditDialog((BodyMeasurement) item.payload);
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                BodyMeasurement record = (BodyMeasurement) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除记录")
                        .setMessage("确认删除这条围度记录？")
                        .setPositiveButton("删除", (d, w) -> viewModel.deleteRecord(record))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        rvRecords.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecords.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(BodyMeasurementDetailViewModel.class);
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        viewModel.setSelectedDate(selectedDate);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        fabAdd.setOnClickListener(v -> showAddDialog());

        btnTabWeek.setOnClickListener(v -> switchTab(0));
        btnTabMonth.setOnClickListener(v -> switchTab(1));
        btnTabYear.setOnClickListener(v -> switchTab(2));
        applyTabSelection();

        // Type chip selection
        chipTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int idx = 0;
            if (checkedId == R.id.chip_body_fat) idx = 0;
            else if (checkedId == R.id.chip_chest) idx = 1;
            else if (checkedId == R.id.chip_waist) idx = 2;
            else if (checkedId == R.id.chip_hip) idx = 3;
            else if (checkedId == R.id.chip_arm) idx = 4;
            else if (checkedId == R.id.chip_thigh) idx = 5;
            else if (checkedId == R.id.chip_calf) idx = 6;
            viewModel.setSelectedType(TYPES[idx]);
            chartView.setUnit(TYPE_UNITS[idx]);
            chartView.setLineColor(TYPE_COLORS[idx]);
            tvMeasureMethod.setText(com.cz.fitnessdiary.utils.AnalysisUtils.getMeasurementMethod(TYPES[idx]));
        });

        // Observe chart data
        viewModel.getWeekSeries().observe(getViewLifecycleOwner(), values -> {
            if (selectedTab == 0) updateChart(values);
        });
        viewModel.getMonthSeries().observe(getViewLifecycleOwner(), values -> {
            if (selectedTab == 1) updateChart(values);
        });
        viewModel.getYearSeries().observe(getViewLifecycleOwner(), values -> {
            if (selectedTab == 2) updateChart(values);
        });

        // Observe records
        viewModel.getRecordsByDate(0, System.currentTimeMillis() + 86400000L)
                .observe(getViewLifecycleOwner(), this::renderRecords);

        // Observe analysis
        viewModel.getWaistHipRatio().observe(getViewLifecycleOwner(), whr -> {
            if (whr != null && whr > 0) {
                tvWhr.setText(String.format(Locale.getDefault(), "腰臀比：%.2f", whr));
            }
        });
        viewModel.getBodyFatZone().observe(getViewLifecycleOwner(), zone -> {
            if (zone != null && !zone.isEmpty()) {
                tvBodyfatZone.setText("体脂区间：" + zone);
            }
        });
        viewModel.getLatestValues().observe(getViewLifecycleOwner(), this::updateLatestGrid);
    }

    private void updateChart(List<Float> values) {
        if (values == null) return;
        chartView.setData(values, buildLabels(values.size()));
    }

    private void switchTab(int tab) {
        selectedTab = tab;
        applyTabSelection();
        viewModel.refreshTrend();
    }

    private void applyTabSelection() {
        btnTabWeek.setChecked(selectedTab == 0);
        btnTabMonth.setChecked(selectedTab == 1);
        btnTabYear.setChecked(selectedTab == 2);
    }

    private List<String> buildLabels(int count) {
        List<String> labels = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        if (selectedTab == 0) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            for (int i = 0; i < count; i++) {
                labels.add(new SimpleDateFormat("dd", Locale.getDefault()).format(cal.getTime()));
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        } else if (selectedTab == 1) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
            for (int i = 0; i < count; i++) {
                labels.add(String.valueOf(i + 1));
            }
        } else {
            cal.set(Calendar.MONTH, 0);
            for (int i = 0; i < count; i++) {
                labels.add((i + 1) + "月");
            }
        }
        return labels;
    }

    private void renderRecords(List<BodyMeasurement> records) {
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        if (records != null) {
            for (BodyMeasurement r : records) {
                String typeName = r.getMeasurementType();
                for (int i = 0; i < TYPES.length; i++) {
                    if (TYPES[i].equals(typeName)) { typeName = TYPE_NAMES[i]; break; }
                }
                items.add(new DetailRecordAdapter.Item(
                        r.getId(), typeName,
                        sdf.format(new Date(r.getTimestamp())),
                        String.format(Locale.getDefault(), "%.1f %s", r.getValue(), r.getUnit()),
                        r.getNote(), 0, r));
            }
        }
        adapter.submitList(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateLatestGrid(Map<String, Float> values) {
        // Update header summary with count
        int count = 0;
        if (values != null) {
            for (Float v : values.values()) if (v != null && v > 0) count++;
        }
        tvHeaderSummary.setText("已记录 " + count + " 项数据");
    }

    private void showAddDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_body_measurement, null);
        ChipGroup chipType = dialogView.findViewById(R.id.chip_type);
        EditText etValue = dialogView.findViewById(R.id.et_value);
        TextView tvUnit = dialogView.findViewById(R.id.tv_unit);
        EditText etNote = dialogView.findViewById(R.id.et_note);
        View layoutQuickCalc = dialogView.findViewById(R.id.layout_quick_calc);
        TextView tvCalcInfo = dialogView.findViewById(R.id.tv_calc_info);
        View btnQuickCalc = dialogView.findViewById(R.id.btn_quick_calc);

        // Load user data on background thread
        final float[] userWeight = {0};
        final float[] userHeight = {0};
        final int[] userAge = {0};
        final int[] userGender = {0};
        new Thread(() -> {
            com.cz.fitnessdiary.database.entity.User user =
                    new com.cz.fitnessdiary.repository.UserRepository(requireActivity().getApplication()).getUserSync();
            if (user != null) {
                userWeight[0] = user.getWeight();
                userHeight[0] = user.getHeight();
                userAge[0] = user.getAge();
                userGender[0] = user.getGender();
            }
            requireActivity().runOnUiThread(() ->
                    updateCalcInfo(tvCalcInfo, userWeight[0], userHeight[0], userAge[0], userGender[0]));
        }).start();

        final int[] selectedIdx = {0}; // default BODY_FAT

        // Set initial visibility for default type (body fat)
        layoutQuickCalc.setVisibility(View.VISIBLE);

        chipType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_body_fat) selectedIdx[0] = 0;
            else if (checkedId == R.id.chip_chest) selectedIdx[0] = 1;
            else if (checkedId == R.id.chip_waist) selectedIdx[0] = 2;
            else if (checkedId == R.id.chip_hip) selectedIdx[0] = 3;
            else if (checkedId == R.id.chip_arm) selectedIdx[0] = 4;
            else if (checkedId == R.id.chip_thigh) selectedIdx[0] = 5;
            else if (checkedId == R.id.chip_calf) selectedIdx[0] = 6;
            tvUnit.setText(TYPE_UNITS[selectedIdx[0]]);
            layoutQuickCalc.setVisibility(selectedIdx[0] == 0 ? View.VISIBLE : View.GONE);
            if (selectedIdx[0] == 0) updateCalcInfo(tvCalcInfo, userWeight[0], userHeight[0], userAge[0], userGender[0]);
        });

        btnQuickCalc.setOnClickListener(v -> {
            if (userHeight[0] <= 0 || userWeight[0] <= 0) {
                Toast.makeText(getContext(), "请先在个人资料中填写身高和体重", Toast.LENGTH_SHORT).show();
                return;
            }
            float bmi = userWeight[0] / ((userHeight[0] / 100f) * (userHeight[0] / 100f));
            float bodyFat = 1.20f * bmi + 0.23f * userAge[0] - 10.8f * userGender[0] - 5.4f;
            etValue.setText(String.format(Locale.getDefault(), "%.1f", Math.max(0, bodyFat)));
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加围度记录")
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        float value = Float.parseFloat(etValue.getText().toString().trim());
                        viewModel.addRecord(TYPES[selectedIdx[0]], value, TYPE_UNITS[selectedIdx[0]],
                                etNote.getText().toString().trim());
                        Toast.makeText(getContext(), "已添加", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数值", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateCalcInfo(TextView tvCalcInfo, float weight, float height, int age, int gender) {
        if (height > 0 && weight > 0) {
            tvCalcInfo.setText(String.format(Locale.getDefault(),
                    "身高%.0fcm 体重%.0fkg 年龄%d %s",
                    height, weight, age, gender == 1 ? "男" : "女"));
        } else {
            tvCalcInfo.setText("需在个人资料中填写身高体重");
        }
    }

    private void showEditDialog(BodyMeasurement record) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_body_measurement, null);
        ChipGroup chipType = dialogView.findViewById(R.id.chip_type);
        EditText etValue = dialogView.findViewById(R.id.et_value);
        TextView tvUnit = dialogView.findViewById(R.id.tv_unit);
        EditText etNote = dialogView.findViewById(R.id.et_note);

        // Pre-select type
        final int typeIdx;
        int found = 0;
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(record.getMeasurementType())) { found = i; break; }
        }
        typeIdx = found;
        int[] chipIds = {R.id.chip_body_fat, R.id.chip_chest, R.id.chip_waist, R.id.chip_hip, R.id.chip_arm, R.id.chip_thigh, R.id.chip_calf};
        ((Chip) chipType.findViewById(chipIds[typeIdx])).setChecked(true);
        tvUnit.setText(TYPE_UNITS[typeIdx]);
        etValue.setText(String.valueOf(record.getValue()));
        etNote.setText(record.getNote() != null ? record.getNote() : "");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑围度记录")
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        float value = Float.parseFloat(etValue.getText().toString().trim());
                        record.setValue(value);
                        record.setNote(etNote.getText().toString().trim());
                        record.setUnit(TYPE_UNITS[typeIdx]);
                        viewModel.updateRecord(record);
                        Toast.makeText(getContext(), "已更新", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数值", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
