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
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.ui.widget.BarSparkView;
import com.cz.fitnessdiary.ui.widget.WaterCupProgressView;
import com.cz.fitnessdiary.viewmodel.WaterDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WaterRecordDetailFragment extends Fragment {

    private static final int TARGET_WATER = 1600;

    private WaterDetailViewModel viewModel;
    private DetailRecordAdapter adapter;

    private TextView tvToday;
    private TextView tvTarget;
    private TextView tvLeft;
    private TextView tvEmpty;
    private ProgressBar progressLoading;
    private WaterCupProgressView waterCup;
    private BarSparkView barWeek;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_water_record_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnAdd = view.findViewById(R.id.btn_add);
        MaterialButton btnQuick100 = view.findViewById(R.id.btn_quick_100);
        MaterialButton btnQuick200 = view.findViewById(R.id.btn_quick_200);
        MaterialButton btnQuick400 = view.findViewById(R.id.btn_quick_400);
        MaterialButton btnQuickCustom = view.findViewById(R.id.btn_quick_custom);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_records);

        tvToday = view.findViewById(R.id.tv_today);
        tvTarget = view.findViewById(R.id.tv_target);
        tvLeft = view.findViewById(R.id.tv_left);
        tvEmpty = view.findViewById(R.id.tv_empty);
        progressLoading = view.findViewById(R.id.progress_loading);
        waterCup = view.findViewById(R.id.water_cup);
        barWeek = view.findViewById(R.id.bar_week);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                WaterRecord record = (WaterRecord) item.payload;
                showEditDialog(record);
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                WaterRecord record = (WaterRecord) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除记录")
                        .setMessage("确认删除这条喝水记录？")
                        .setPositiveButton("删除", (d, w) -> viewModel.deleteWater(record))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(WaterDetailViewModel.class);
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        viewModel.setSelectedDate(selectedDate);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showAddDialog());
        btnQuick100.setOnClickListener(v -> viewModel.addWater(100, "快捷"));
        btnQuick200.setOnClickListener(v -> viewModel.addWater(200, "快捷"));
        btnQuick400.setOnClickListener(v -> viewModel.addWater(400, "快捷"));
        btnQuickCustom.setOnClickListener(v -> showAddDialog());

        viewModel.getTodayTotal().observe(getViewLifecycleOwner(), total -> {
            int t = total == null ? 0 : total;
            tvToday.setText("今日 " + t + "ml");
            tvTarget.setText("目标 " + TARGET_WATER + "ml");
            int left = Math.max(0, TARGET_WATER - t);
            tvLeft.setText("剩余 " + left + "ml");
            waterCup.setProgress(t, TARGET_WATER);
        });

        viewModel.getSelectedDateRecords().observe(getViewLifecycleOwner(), this::renderRecords);
        viewModel.getWeekSeries().observe(getViewLifecycleOwner(), barWeek::setValues);
        viewModel.refreshWeekSeries();
    }

    private void renderRecords(List<WaterRecord> records) {
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        if (records != null) {
            for (WaterRecord record : records) {
                items.add(new DetailRecordAdapter.Item(
                        record.getId(),
                        "喝水",
                        format.format(new Date(record.getTimestamp())),
                        record.getAmountMl() + " ml",
                        record.getNote() == null ? "点击编辑，长按删除" : record.getNote(),
                        R.drawable.ic_add,
                        record));
            }
        }
        adapter.submitList(items);
        progressLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddDialog() {
        EditText et = new EditText(requireContext());
        et.setHint("喝水量(ml)");
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("记录喝水").setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int value = Integer.parseInt(et.getText().toString().trim());
                        if (value <= 0) {
                            Toast.makeText(getContext(), "请输入大于 0 的数值", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        viewModel.addWater(value, null);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showEditDialog(WaterRecord record) {
        EditText et = new EditText(requireContext());
        et.setHint("喝水量(ml)");
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(record.getAmountMl()));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("编辑喝水记录").setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int value = Integer.parseInt(et.getText().toString().trim());
                        if (value <= 0) {
                            Toast.makeText(getContext(), "请输入大于 0 的数值", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        record.setAmountMl(value);
                        viewModel.updateWater(record);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }
}
