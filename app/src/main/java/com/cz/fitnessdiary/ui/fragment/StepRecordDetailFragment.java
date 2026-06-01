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
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.StepDetailViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StepRecordDetailFragment extends Fragment {

    private StepDetailViewModel viewModel;
    private DetailRecordAdapter adapter;

    private TextView tvStepLarge;
    private TextView tvTargetInfo;
    private TextView tvStepCalories;
    private TextView tvEmpty;
    private ProgressBar progressStepDetail;
    private ProgressBar progressLoading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step_record_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnTarget = view.findViewById(R.id.btn_target);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_records);

        tvStepLarge = view.findViewById(R.id.tv_step_large);
        tvTargetInfo = view.findViewById(R.id.tv_target_info);
        tvStepCalories = view.findViewById(R.id.tv_step_calories);
        tvEmpty = view.findViewById(R.id.tv_empty);
        progressStepDetail = view.findViewById(R.id.progress_step_detail);
        progressLoading = view.findViewById(R.id.progress_loading);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                StepRecord record = (StepRecord) item.payload;
                showEditDialog(record);
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                StepRecord record = (StepRecord) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除记录")
                        .setMessage("确认删除这条步数记录？")
                        .setPositiveButton("删除", (d, w) -> viewModel.delete(record))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(StepDetailViewModel.class);
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        viewModel.setSelectedDate(selectedDate);

        ExtendedFloatingActionButton fabAdd = view.findViewById(R.id.fab_add);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnTarget.setOnClickListener(v -> showTargetDialog());
        fabAdd.setOnClickListener(v -> showAddDialog());

        view.findViewById(R.id.btn_quick_3000).setOnClickListener(v -> quickAdd(3000));
        view.findViewById(R.id.btn_quick_5000).setOnClickListener(v -> quickAdd(5000));
        view.findViewById(R.id.btn_quick_custom).setOnClickListener(v -> showAddDialog());

        viewModel.getTodayStep().observe(getViewLifecycleOwner(), this::updateSummary);
        viewModel.getRecentRecords().observe(getViewLifecycleOwner(), this::renderRecords);
    }

    private void updateSummary(StepRecord step) {
        int target = viewModel.getStepTarget();
        int steps = (step != null) ? step.getSteps() : 0;
        int pct = target > 0 ? Math.min(steps * 100 / target, 100) : 0;
        int cal = (int) (steps * 0.04);

        tvStepLarge.setText(String.valueOf(steps));
        tvTargetInfo.setText("目标 " + target + " 步 · " + pct + "%");
        tvStepCalories.setText("≈" + cal + " 千卡");
        progressStepDetail.setProgress(pct);
    }

    private void renderRecords(List<StepRecord> records) {
        if (progressLoading != null) progressLoading.setVisibility(View.GONE);
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        SimpleDateFormat dateFmt = new SimpleDateFormat("MM月dd日", Locale.getDefault());

        if (records != null) {
            for (StepRecord r : records) {
                String title = r.getSteps() + " 步";
                String subtitle = dateFmt.format(new Date(r.getDate()));
                String right = (int) (r.getSteps() * 0.04) + "千卡";
                String sourceLabel = r.getSource() == 0 ? "自动" : r.getSource() == 1 ? "手动" : "混合";
                items.add(new DetailRecordAdapter.Item(
                        r.getId(), title, subtitle + " · " + sourceLabel, right, "", 0, r));
            }
        }

        adapter.submitList(items);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void quickAdd(int addSteps) {
        StepRecord existing = viewModel.getTodayStep().getValue();
        int current = (existing != null) ? existing.getSteps() : 0;
        viewModel.setTodaySteps(current + addSteps);
    }

    private void showAddDialog() {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("输入步数");
        StepRecord existing = viewModel.getTodayStep().getValue();
        if (existing != null && existing.getSteps() > 0) {
            et.setText(String.valueOf(existing.getSteps()));
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("记录步数")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int steps = Integer.parseInt(et.getText().toString().trim());
                        viewModel.setTodaySteps(steps);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditDialog(StepRecord record) {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(record.getSteps()));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑步数")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int steps = Integer.parseInt(et.getText().toString().trim());
                        record.setSteps(steps);
                        record.setSource(1);
                        record.setCreateTime(System.currentTimeMillis());
                        viewModel.insertOrUpdate(record);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTargetDialog() {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("目标步数");
        et.setText(String.valueOf(viewModel.getStepTarget()));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("设置每日步数目标")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int target = Integer.parseInt(et.getText().toString().trim());
                        if (target > 0) {
                            viewModel.setStepTarget(target);
                            updateSummary(viewModel.getTodayStep().getValue());
                        }
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
