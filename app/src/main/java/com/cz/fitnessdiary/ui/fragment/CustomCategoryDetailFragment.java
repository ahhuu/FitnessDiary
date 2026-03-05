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
import com.cz.fitnessdiary.database.entity.CustomRecord;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.ui.widget.LineSparkView;
import com.cz.fitnessdiary.viewmodel.CustomCategoryDetailViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomCategoryDetailFragment extends Fragment {

    private DetailRecordAdapter adapter;
    private CustomCategoryDetailViewModel viewModel;

    private TextView tvSummary;
    private TextView tvSum;
    private TextView tvEmpty;
    private ProgressBar progressLoading;
    private LineSparkView lineTrend;

    private long targetId;
    private String title;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_custom_category_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        targetId = requireArguments().getLong("targetId", 0L);
        title = requireArguments().getString("title", "分类明细");
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnAdd = view.findViewById(R.id.btn_add);
        TextView tvTitle = view.findViewById(R.id.tv_title);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_records);

        tvSummary = view.findViewById(R.id.tv_summary);
        tvSum = view.findViewById(R.id.tv_sum);
        tvEmpty = view.findViewById(R.id.tv_empty);
        progressLoading = view.findViewById(R.id.progress_loading);
        lineTrend = view.findViewById(R.id.line_trend);

        tvTitle.setText(title);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                showEditDialog((CustomRecord) item.payload);
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                CustomRecord record = (CustomRecord) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除记录")
                        .setMessage("确认删除该记录？")
                        .setPositiveButton("删除", (d, w) -> viewModel.deleteRecord(record))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(CustomCategoryDetailViewModel.class);
        viewModel.setSelectedDate(selectedDate);
        viewModel.setSelectedTrackerId(targetId);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showAddDialog());

        viewModel.getSelectedTrackerTodayCount().observe(getViewLifecycleOwner(), c ->
                tvSummary.setText("今日 " + (c == null ? 0 : c) + " 条"));
        viewModel.getSelectedTrackerTodaySum().observe(getViewLifecycleOwner(), s ->
                tvSum.setText(String.format(Locale.getDefault(), "今日累计 %.1f", s == null ? 0 : s)));
        viewModel.getTrendSeries().observe(getViewLifecycleOwner(), lineTrend::setValues);
        viewModel.getSelectedTrackerRecords().observe(getViewLifecycleOwner(), this::renderRecords);
    }

    private void renderRecords(List<CustomRecord> records) {
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        if (records != null) {
            for (CustomRecord record : records) {
                double value = record.getNumericValue() == null ? 0 : record.getNumericValue();
                items.add(new DetailRecordAdapter.Item(
                        record.getId(),
                        title,
                        format.format(new Date(record.getTimestamp())),
                        String.format(Locale.getDefault(), "%.1f", value),
                        "点击编辑，长按删除",
                        R.drawable.ic_input_add,
                        record));
            }
        }
        adapter.submitList(items);
        progressLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddDialog() {
        EditText et = new EditText(requireContext());
        et.setHint("记录数值");
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("新增记录").setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        double value = Double.parseDouble(et.getText().toString().trim());
                        viewModel.addRecord(targetId, value, null);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showEditDialog(CustomRecord record) {
        EditText et = new EditText(requireContext());
        et.setHint("记录数值");
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setText(String.valueOf(record.getNumericValue() == null ? 0 : record.getNumericValue()));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("编辑记录").setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        double value = Double.parseDouble(et.getText().toString().trim());
                        record.setNumericValue(value);
                        viewModel.updateRecord(record);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }
}
