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
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.HabitDetailViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HabitRecordDetailFragment extends Fragment {

    private HabitDetailViewModel viewModel;
    private DetailRecordAdapter adapter;

    private final List<HabitItem> habitItems = new ArrayList<>();
    private final Map<Long, HabitRecord> recordMap = new HashMap<>();
    private Map<Long, HabitDetailViewModel.HabitStat> statMap = new HashMap<>();

    private TextView tvSummary;
    private TextView tvRate;
    private TextView tvEmpty;
    private ProgressBar progressLoading;
    private long selectedDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_habit_record_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnAdd = view.findViewById(R.id.btn_add);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_records);

        tvSummary = view.findViewById(R.id.tv_summary);
        tvRate = view.findViewById(R.id.tv_rate);
        tvEmpty = view.findViewById(R.id.tv_empty);
        progressLoading = view.findViewById(R.id.progress_loading);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                HabitItem habitItem = (HabitItem) item.payload;
                HabitRecord current = recordMap.get(habitItem.getId());
                boolean nowDone = current != null && current.isCompleted();
                viewModel.upsertRecord(habitItem.getId(), selectedDate, !nowDone, "manual");
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                HabitItem habitItem = (HabitItem) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("习惯设置")
                        .setItems(new String[] { habitItem.isEnabled() ? "停用" : "启用" }, (d, which) -> {
                            habitItem.setEnabled(!habitItem.isEnabled());
                            viewModel.updateItem(habitItem);
                        })
                        .show();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        selectedDate = DateUtils.getDayStartTimestamp(requireArguments().getLong("selectedDate", System.currentTimeMillis()));
        viewModel = new ViewModelProvider(this).get(HabitDetailViewModel.class);
        viewModel.setSelectedDate(selectedDate);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showAddHabitDialog());

        viewModel.getEnabledItems().observe(getViewLifecycleOwner(), items -> {
            habitItems.clear();
            if (items != null) habitItems.addAll(items);
            render();
            viewModel.refreshStats();
        });

        viewModel.getSelectedDateRecords().observe(getViewLifecycleOwner(), records -> {
            recordMap.clear();
            if (records != null) {
                for (HabitRecord record : records) recordMap.put(record.getHabitId(), record);
            }
            render();
        });

        viewModel.getHabitStats().observe(getViewLifecycleOwner(), stats -> {
            statMap = stats == null ? new HashMap<>() : stats;
            render();
        });
    }

    private void render() {
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        int completed = 0;
        int rateSum = 0;

        for (HabitItem item : habitItems) {
            HabitRecord record = recordMap.get(item.getId());
            boolean done = record != null && record.isCompleted();
            if (done) completed++;

            HabitDetailViewModel.HabitStat stat = statMap.get(item.getId());
            int streak = stat == null ? 0 : stat.streak;
            int rate = stat == null ? 0 : stat.completionRate;
            rateSum += rate;

            items.add(new DetailRecordAdapter.Item(
                    item.getId(),
                    item.getName(),
                    done ? "今日已完成" : "今日未完成",
                    "连续 " + streak + " 天",
                    String.format(Locale.getDefault(), "完成率 %d%%，点击切换", rate),
                    R.drawable.ic_ach_plan_starter,
                    item
            ));
        }

        adapter.submitList(items);
        progressLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        int total = habitItems.size();
        int avgRate = total == 0 ? 0 : rateSum / total;
        tvSummary.setText("今日完成 " + completed + " / " + total);
        tvRate.setText("平均完成率 " + avgRate + "%");
    }

    private void showAddHabitDialog() {
        EditText et = new EditText(requireContext());
        et.setHint("习惯名称");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加习惯")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String name = et.getText() == null ? "" : et.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "请输入习惯名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.addHabitItem(name);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
