package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.MenstrualCycle;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.ui.widget.MenstrualCycleChartView;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.MenstrualDetailViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MenstrualRecordDetailFragment extends Fragment {

    private MenstrualDetailViewModel viewModel;
    private DetailRecordAdapter adapter;
    private MenstrualCycleChartView cycleChart;
    private TextView tvHeaderSummary, tvCurrentCycleDay, tvNextPredicted;
    private TextView tvAvgCycleLength, tvAvgPeriodDuration, tvRegularity, tvSymptomSummary, tvEmpty;

    private long pendingStartDate = 0;
    private long pendingEndDate = 0;

    private static final String[] SYMPTOM_KEYS = {"CRAMPS", "BLOATING", "HEADACHE", "FATIGUE",
            "BREAST_TENDERNESS", "NAUSEA", "BACK_PAIN", "ACNE"};
    private static final String[] SYMPTOM_NAMES = {"腹痛", "腹胀", "头痛", "疲劳", "乳房胀痛", "恶心", "背痛", "痘痘"};
    private static final String[] MOOD_KEYS = {"HAPPY", "SAD", "IRRITABLE", "ANXIOUS", "CALM", "TIRED", "ENERGETIC"};
    private static final String[] MOOD_NAMES = {"开心", "低落", "易怒", "焦虑", "平静", "疲倦", "精力充沛"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menstrual_record_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        cycleChart = view.findViewById(R.id.chart_cycle);
        tvHeaderSummary = view.findViewById(R.id.tv_header_summary);
        tvCurrentCycleDay = view.findViewById(R.id.tv_current_cycle_day);
        tvNextPredicted = view.findViewById(R.id.tv_next_predicted);
        tvAvgCycleLength = view.findViewById(R.id.tv_avg_cycle_length);
        tvAvgPeriodDuration = view.findViewById(R.id.tv_avg_period_duration);
        tvRegularity = view.findViewById(R.id.tv_regularity);
        tvSymptomSummary = view.findViewById(R.id.tv_symptom_summary);
        tvEmpty = view.findViewById(R.id.tv_empty);
        RecyclerView rvRecords = view.findViewById(R.id.rv_records);
        ExtendedFloatingActionButton fabAdd = view.findViewById(R.id.fab_add);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                showEditDialog((MenstrualCycle) item.payload);
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                MenstrualCycle record = (MenstrualCycle) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除记录")
                        .setMessage("确认删除这条经期记录？")
                        .setPositiveButton("删除", (d, w) -> viewModel.deleteRecord(record))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        rvRecords.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecords.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(MenstrualDetailViewModel.class);
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        viewModel.setSelectedDate(selectedDate);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        fabAdd.setOnClickListener(v -> showAddDialog());

        // Observe data
        viewModel.getAllRecords().observe(getViewLifecycleOwner(), this::renderRecords);
        viewModel.getCycleBarData().observe(getViewLifecycleOwner(), data -> {
            Float avgLen = viewModel.getAvgCycleLength().getValue();
            cycleChart.setData(data, avgLen != null ? avgLen : 0);
        });
        viewModel.getCurrentCycleDay().observe(getViewLifecycleOwner(), day -> {
            if (day != null && day > 0) {
                tvCurrentCycleDay.setText("周期第 " + day + " 天");
            }
        });
        viewModel.getNextPredictedDate().observe(getViewLifecycleOwner(), ts -> {
            if (ts != null && ts > 0) {
                tvNextPredicted.setText("预测下次：" +
                        new SimpleDateFormat("MM月dd日", Locale.getDefault()).format(new Date(ts)));
            }
        });
        viewModel.getAvgCycleLength().observe(getViewLifecycleOwner(), len -> {
            if (len != null && len > 0) {
                tvAvgCycleLength.setText(String.format(Locale.getDefault(), "平均周期：%.1f 天", len));
            }
        });
        viewModel.getAvgPeriodDuration().observe(getViewLifecycleOwner(), dur -> {
            if (dur != null && dur > 0) {
                tvAvgPeriodDuration.setText(String.format(Locale.getDefault(), "平均经期：%.1f 天", dur));
            }
        });
        viewModel.getRegularityDesc().observe(getViewLifecycleOwner(), desc -> {
            tvRegularity.setText("规律性：" + (desc != null ? desc : "--"));
        });
        viewModel.getSymptomFrequency().observe(getViewLifecycleOwner(), freq -> {
            if (freq != null && !freq.isEmpty()) {
                StringBuilder sb = new StringBuilder("常见症状：");
                String top = null;
                int topCount = 0;
                for (Map.Entry<String, Integer> e : freq.entrySet()) {
                    if (e.getValue() > topCount) {
                        topCount = e.getValue();
                        top = symptomName(e.getKey());
                    }
                }
                sb.append(top != null ? top : "");
                tvSymptomSummary.setText(sb.toString());
            }
        });
    }

    private void renderRecords(List<MenstrualCycle> records) {
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
        if (records != null) {
            for (MenstrualCycle r : records) {
                String title = sdf.format(new Date(r.getStartDate()));
                if (r.getEndDate() != null) {
                    title += " ~ " + sdf.format(new Date(r.getEndDate()));
                } else {
                    title += " ~ 持续中";
                }
                int duration = 0;
                if (r.getEndDate() != null) {
                    duration = (int) ((r.getEndDate() - r.getStartDate()) / (24 * 60 * 60 * 1000));
                }
                String flowName = r.getFlowIntensity() != null ? flowName(r.getFlowIntensity()) : "";
                items.add(new DetailRecordAdapter.Item(
                        r.getId(), title,
                        flowName,
                        duration + "天",
                        r.getNotes(), 0, r));
            }
        }
        adapter.submitList(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_menstrual, null);
        TextView tvStartDate = dialogView.findViewById(R.id.tv_start_date);
        TextView tvEndDate = dialogView.findViewById(R.id.tv_end_date);
        CheckBox cbOngoing = dialogView.findViewById(R.id.cb_ongoing);
        EditText etNotes = dialogView.findViewById(R.id.et_notes);
        LinearLayout layoutSymptoms = dialogView.findViewById(R.id.layout_symptoms);
        LinearLayout layoutMood = dialogView.findViewById(R.id.layout_mood);

        pendingStartDate = System.currentTimeMillis();
        pendingEndDate = 0;
        tvStartDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(pendingStartDate)));

        // Date pickers
        dialogView.findViewById(R.id.btn_pick_start).setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().build();
            picker.addOnPositiveButtonClickListener(sel -> {
                pendingStartDate = sel;
                tvStartDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(sel)));
            });
            picker.show(getParentFragmentManager(), "start_date");
        });
        dialogView.findViewById(R.id.btn_pick_end).setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().build();
            picker.addOnPositiveButtonClickListener(sel -> {
                pendingEndDate = sel;
                tvEndDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(sel)));
            });
            picker.show(getParentFragmentManager(), "end_date");
        });

        // Build symptom checkboxes
        for (int i = 0; i < SYMPTOM_KEYS.length; i++) {
            CheckBox cb = new CheckBox(requireContext());
            cb.setTag(SYMPTOM_KEYS[i]);
            cb.setText(SYMPTOM_NAMES[i]);
            layoutSymptoms.addView(cb);
        }

        // Build mood checkboxes
        for (int i = 0; i < MOOD_KEYS.length; i++) {
            CheckBox cb = new CheckBox(requireContext());
            cb.setTag(MOOD_KEYS[i]);
            cb.setText(MOOD_NAMES[i]);
            layoutMood.addView(cb);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加经期记录")
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    String flow = getCheckedFlow(dialogView);
                    Long endDate = cbOngoing.isChecked() ? null : (pendingEndDate > 0 ? pendingEndDate : null);
                    String symptoms = getCheckedList(layoutSymptoms);
                    String mood = getCheckedList(layoutMood);

                    MenstrualCycle record = new MenstrualCycle(pendingStartDate, endDate,
                            flow, symptoms, mood, etNotes.getText().toString().trim(), System.currentTimeMillis());
                    viewModel.addRecord(record);
                    Toast.makeText(getContext(), "已添加", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditDialog(MenstrualCycle record) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_menstrual, null);
        TextView tvStartDate = dialogView.findViewById(R.id.tv_start_date);
        TextView tvEndDate = dialogView.findViewById(R.id.tv_end_date);
        CheckBox cbOngoing = dialogView.findViewById(R.id.cb_ongoing);
        EditText etNotes = dialogView.findViewById(R.id.et_notes);
        LinearLayout layoutSymptoms = dialogView.findViewById(R.id.layout_symptoms);
        LinearLayout layoutMood = dialogView.findViewById(R.id.layout_mood);

        pendingStartDate = record.getStartDate();
        pendingEndDate = record.getEndDate() != null ? record.getEndDate() : 0;
        tvStartDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(pendingStartDate)));
        tvEndDate.setText(pendingEndDate > 0 ?
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(pendingEndDate)) : "请选择日期");
        cbOngoing.setChecked(record.getEndDate() == null);

        dialogView.findViewById(R.id.btn_pick_start).setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().build();
            picker.addOnPositiveButtonClickListener(sel -> {
                pendingStartDate = sel;
                tvStartDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(sel)));
            });
            picker.show(getParentFragmentManager(), "start_date");
        });
        dialogView.findViewById(R.id.btn_pick_end).setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().build();
            picker.addOnPositiveButtonClickListener(sel -> {
                pendingEndDate = sel;
                tvEndDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(sel)));
            });
            picker.show(getParentFragmentManager(), "end_date");
        });

        // Pre-select flow
        if ("LIGHT".equals(record.getFlowIntensity())) {
            ((Chip) dialogView.findViewById(R.id.chip_light)).setChecked(true);
        } else if ("HEAVY".equals(record.getFlowIntensity())) {
            ((Chip) dialogView.findViewById(R.id.chip_heavy)).setChecked(true);
        }

        // Build checkboxes with pre-selection
        String[] existingSymptoms = record.getSymptoms() != null ? record.getSymptoms().split(",") : new String[0];
        for (int i = 0; i < SYMPTOM_KEYS.length; i++) {
            CheckBox cb = new CheckBox(requireContext());
            cb.setTag(SYMPTOM_KEYS[i]);
            cb.setText(SYMPTOM_NAMES[i]);
            for (String es : existingSymptoms) {
                if (SYMPTOM_KEYS[i].equals(es.trim())) cb.setChecked(true);
            }
            layoutSymptoms.addView(cb);
        }

        String[] existingMood = record.getMood() != null ? record.getMood().split(",") : new String[0];
        for (int i = 0; i < MOOD_KEYS.length; i++) {
            CheckBox cb = new CheckBox(requireContext());
            cb.setTag(MOOD_KEYS[i]);
            cb.setText(MOOD_NAMES[i]);
            for (String em : existingMood) {
                if (MOOD_KEYS[i].equals(em.trim())) cb.setChecked(true);
            }
            layoutMood.addView(cb);
        }

        etNotes.setText(record.getNotes() != null ? record.getNotes() : "");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑经期记录")
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    String flow = getCheckedFlow(dialogView);
                    Long endDate = cbOngoing.isChecked() ? null : (pendingEndDate > 0 ? pendingEndDate : null);
                    String symptoms = getCheckedList(layoutSymptoms);
                    String mood = getCheckedList(layoutMood);

                    record.setStartDate(pendingStartDate);
                    record.setEndDate(endDate);
                    record.setFlowIntensity(flow);
                    record.setSymptoms(symptoms);
                    record.setMood(mood);
                    record.setNotes(etNotes.getText().toString().trim());
                    viewModel.updateRecord(record);
                    Toast.makeText(getContext(), "已更新", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String getCheckedFlow(View root) {
        if (((Chip) root.findViewById(R.id.chip_light)).isChecked()) return "LIGHT";
        if (((Chip) root.findViewById(R.id.chip_heavy)).isChecked()) return "HEAVY";
        return "MEDIUM";
    }

    private String getCheckedList(LinearLayout layout) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof CheckBox && ((CheckBox) child).isChecked()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(child.getTag());
            }
        }
        return sb.toString();
    }

    private String flowName(String code) {
        switch (code) {
            case "LIGHT": return "少量";
            case "MEDIUM": return "中等";
            case "HEAVY": return "大量";
            default: return code;
        }
    }

    private String symptomName(String code) {
        for (int i = 0; i < SYMPTOM_KEYS.length; i++) {
            if (SYMPTOM_KEYS[i].equals(code)) return SYMPTOM_NAMES[i];
        }
        return code;
    }
}
