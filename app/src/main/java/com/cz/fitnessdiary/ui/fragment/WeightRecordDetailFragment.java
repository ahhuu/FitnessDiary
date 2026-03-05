package com.cz.fitnessdiary.ui.fragment;

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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.ui.widget.LineSparkView;
import com.cz.fitnessdiary.viewmodel.WeightDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeightRecordDetailFragment extends Fragment {

    private WeightDetailViewModel viewModel;
    private DetailRecordAdapter adapter;

    private TextView tvLatest;
    private TextView tvDelta;
    private TextView tvBmi;
    private TextView tvEmpty;
    private ProgressBar progressLoading;
    private LineSparkView lineWeight;

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
        RecyclerView recyclerView = view.findViewById(R.id.recycler_records);

        tvLatest = view.findViewById(R.id.tv_latest);
        tvDelta = view.findViewById(R.id.tv_delta);
        tvBmi = view.findViewById(R.id.tv_bmi);
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

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showAddDialog());
        btnQuick03.setOnClickListener(v -> quickAdd(0.3f));
        btnQuick05.setOnClickListener(v -> quickAdd(0.5f));
        btnQuickCustom.setOnClickListener(v -> showAddDialog());

        viewModel.getRecentRecords().observe(getViewLifecycleOwner(), this::renderRecords);
        viewModel.getTrendSeries().observe(getViewLifecycleOwner(), lineWeight::setValues);
        viewModel.getBmi().observe(getViewLifecycleOwner(), bmi -> {
            if (bmi == null || bmi <= 0f) tvBmi.setText("BMI --");
            else tvBmi.setText(String.format(Locale.getDefault(), "BMI %.1f", bmi));
        });
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
                        R.drawable.ic_hero_dumbbell,
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
}
