package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.ui.widget.BarSparkView;
import com.cz.fitnessdiary.viewmodel.SleepDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SleepRecordDetailFragment extends Fragment {

    private SleepDetailViewModel viewModel;
    private DetailRecordAdapter adapter;

    private TextView tvSummary;
    private TextView tvWindow;
    private TextView tvEmpty;
    private ProgressBar progressLoading;
    private BarSparkView barSleep;
    private boolean showingMonth = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sleep_record_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnAdd = view.findViewById(R.id.btn_add);
        MaterialButton btnTabWeek = view.findViewById(R.id.btn_tab_week);
        MaterialButton btnTabMonth = view.findViewById(R.id.btn_tab_month);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_records);

        tvSummary = view.findViewById(R.id.tv_summary);
        tvWindow = view.findViewById(R.id.tv_window);
        tvEmpty = view.findViewById(R.id.tv_empty);
        progressLoading = view.findViewById(R.id.progress_loading);
        barSleep = view.findViewById(R.id.bar_sleep);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                showSleepDialog((SleepRecord) item.payload);
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                SleepRecord record = (SleepRecord) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除睡眠记录")
                        .setMessage("确认删除该记录？")
                        .setPositiveButton("删除", (d, w) -> viewModel.deleteSleepRecord(record))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(SleepDetailViewModel.class);
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        viewModel.setSelectedDate(selectedDate);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showSleepDialog(null));

        btnTabWeek.setOnClickListener(v -> {
            showingMonth = false;
            viewModel.getWeekSeries().observe(getViewLifecycleOwner(), barSleep::setValues);
        });
        btnTabMonth.setOnClickListener(v -> {
            showingMonth = true;
            viewModel.getMonthSeries().observe(getViewLifecycleOwner(), barSleep::setValues);
        });

        viewModel.getWeekSeries().observe(getViewLifecycleOwner(), values -> {
            if (!showingMonth) barSleep.setValues(values);
        });
        viewModel.getMonthSeries().observe(getViewLifecycleOwner(), values -> {
            if (showingMonth) barSleep.setValues(values);
        });

        viewModel.getSelectedDateRecords().observe(getViewLifecycleOwner(), this::renderRecords);
        viewModel.refreshStatsSeries();
    }

    private void renderRecords(List<SleepRecord> records) {
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        SimpleDateFormat tf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        long total = 0;
        if (records != null) {
            for (SleepRecord record : records) {
                total += record.getDuration();
                items.add(new DetailRecordAdapter.Item(
                        record.getId(),
                        "睡眠",
                        tf.format(new Date(record.getStartTime())) + " - " + tf.format(new Date(record.getEndTime())),
                        (record.getDuration() / 3600) + "h " + ((record.getDuration() % 3600) / 60) + "m",
                        "质量 " + record.getQuality() + " 星",
                        R.drawable.ic_hero_moon,
                        record));
            }
        }

        adapter.submitList(items);
        progressLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        if (items.isEmpty()) {
            tvSummary.setText("平均睡眠 0h 0m");
            tvWindow.setText("最近入睡/起床 --");
            return;
        }

        long avg = total / items.size();
        tvSummary.setText("平均睡眠 " + (avg / 3600) + "h " + ((avg % 3600) / 60) + "m");
        SleepRecord latest = (SleepRecord) items.get(0).payload;
        tvWindow.setText("最近 " + tf.format(new Date(latest.getStartTime())) + " - " + tf.format(new Date(latest.getEndTime())));
    }

    private void showSleepDialog(@Nullable SleepRecord existing) {
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        EditText etStart = new EditText(requireContext());
        etStart.setHint("入睡时间 HH:mm");
        etStart.setInputType(InputType.TYPE_CLASS_DATETIME);
        root.addView(etStart);

        EditText etEnd = new EditText(requireContext());
        etEnd.setHint("起床时间 HH:mm");
        etEnd.setInputType(InputType.TYPE_CLASS_DATETIME);
        root.addView(etEnd);

        EditText etQuality = new EditText(requireContext());
        etQuality.setHint("睡眠质量 1-5");
        etQuality.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(etQuality);

        EditText etNote = new EditText(requireContext());
        etNote.setHint("备注");
        root.addView(etNote);

        if (existing != null) {
            Calendar sc = Calendar.getInstance();
            sc.setTimeInMillis(existing.getStartTime());
            Calendar ec = Calendar.getInstance();
            ec.setTimeInMillis(existing.getEndTime());
            etStart.setText(String.format(Locale.getDefault(), "%02d:%02d", sc.get(Calendar.HOUR_OF_DAY), sc.get(Calendar.MINUTE)));
            etEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", ec.get(Calendar.HOUR_OF_DAY), ec.get(Calendar.MINUTE)));
            etQuality.setText(String.valueOf(existing.getQuality()));
            etNote.setText(existing.getNotes());
        } else {
            etStart.setText("23:00");
            etEnd.setText("07:00");
            etQuality.setText("3");
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? "新增睡眠" : "编辑睡眠")
                .setView(root)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        String[] s = etStart.getText().toString().trim().split(":");
                        String[] e = etEnd.getText().toString().trim().split(":");
                        int sh = Integer.parseInt(s[0]);
                        int sm = Integer.parseInt(s[1]);
                        int eh = Integer.parseInt(e[0]);
                        int em = Integer.parseInt(e[1]);
                        int quality = Math.max(1, Math.min(5, Integer.parseInt(etQuality.getText().toString().trim())));
                        String note = etNote.getText() == null ? "" : etNote.getText().toString().trim();

                        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
                        Calendar end = Calendar.getInstance();
                        end.setTimeInMillis(selectedDate);
                        end.set(Calendar.HOUR_OF_DAY, eh);
                        end.set(Calendar.MINUTE, em);
                        end.set(Calendar.SECOND, 0);
                        end.set(Calendar.MILLISECOND, 0);

                        Calendar start = (Calendar) end.clone();
                        start.set(Calendar.HOUR_OF_DAY, sh);
                        start.set(Calendar.MINUTE, sm);
                        if (start.getTimeInMillis() >= end.getTimeInMillis()) start.add(Calendar.DAY_OF_YEAR, -1);

                        if (existing == null) {
                            viewModel.addSleepRecord(start.getTimeInMillis(), end.getTimeInMillis(), quality, note);
                        } else {
                            existing.setStartTime(start.getTimeInMillis());
                            existing.setEndTime(end.getTimeInMillis());
                            existing.setDuration((end.getTimeInMillis() - start.getTimeInMillis()) / 1000);
                            existing.setQuality(quality);
                            existing.setNotes(note);
                            viewModel.updateSleepRecord(existing);
                        }
                    } catch (Exception ex) {
                        Toast.makeText(getContext(), "请输入正确时间格式，例如 23:00", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
