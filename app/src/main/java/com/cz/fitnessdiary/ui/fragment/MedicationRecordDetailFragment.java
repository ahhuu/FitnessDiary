package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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
import com.cz.fitnessdiary.database.entity.MedicationRecord;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.utils.ReminderManager;
import com.cz.fitnessdiary.viewmodel.MedicationDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MedicationRecordDetailFragment extends Fragment {

    private MedicationDetailViewModel viewModel;
    private DetailRecordAdapter adapter;

    private TextView tvSummary;
    private TextView tvUntaken;
    private TextView tvEmpty;
    private ProgressBar progressLoading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_medication_record_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnAdd = view.findViewById(R.id.btn_add);
        MaterialButton btnOpenReminder = view.findViewById(R.id.btn_open_reminder);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_records);

        tvSummary = view.findViewById(R.id.tv_summary);
        tvUntaken = view.findViewById(R.id.tv_untaken);
        tvEmpty = view.findViewById(R.id.tv_empty);
        progressLoading = view.findViewById(R.id.progress_loading);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                // 需求5：单击直接快速切换已服/未服
                MedicationRecord record = (MedicationRecord) item.payload;
                viewModel.toggleTaken(record);
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                MedicationRecord record = (MedicationRecord) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("记录操作")
                        .setItems(new String[] { record.isTaken() ? "标记未服药" : "标记已服药", "删除" }, (d, which) -> {
                            if (which == 0) {
                                viewModel.toggleTaken(record);
                            } else {
                                viewModel.deleteMedication(record);
                            }
                        })
                        .show();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(MedicationDetailViewModel.class);
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        viewModel.setSelectedDate(selectedDate);

        ExtendedFloatingActionButton fabAdd = view.findViewById(R.id.fab_add);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showAddDialog());
        fabAdd.setOnClickListener(v -> showAddDialog());
        // 需求5：设置提醒弹窗（与训练提醒一致）
        btnOpenReminder.setOnClickListener(v -> showReminderDialog());

        viewModel.getTakenCount().observe(getViewLifecycleOwner(),
                count -> updateSummary(count, viewModel.getTotalDosageGoal().getValue()));
        viewModel.getTotalDosageGoal().observe(getViewLifecycleOwner(),
                goal -> updateSummary(viewModel.getTakenCount().getValue(), goal));

        viewModel.getSelectedDateRecords().observe(getViewLifecycleOwner(), this::renderRecords);
    }

    private void updateSummary(Integer taken, Integer goal) {
        int t = taken == null ? 0 : taken;
        int g = goal == null ? 0 : goal;
        tvSummary.setText(String.format(Locale.getDefault(), "今日已服药 %d / 共 %d 次", t, g));
        if (g > 0 && t >= g) {
            tvSummary.append(" (已完成)");
            tvSummary.setTextColor(0xFF4CAF50); // Green
        } else {
            tvSummary.setTextColor(requireContext().getColor(R.color.cat_med_primary));
        }
    }

    private void renderRecords(List<MedicationRecord> records) {
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        if (records != null) {
            for (MedicationRecord record : records) {
                items.add(new DetailRecordAdapter.Item(
                        record.getId(),
                        record.getName() == null ? "用药" : record.getName(),
                        format.format(new Date(record.getTimestamp())),
                        String.format(Locale.getDefault(), "%s (设为 %d 次)",
                                (record.getDosage() == null || record.getDosage().isEmpty() ? "--"
                                        : record.getDosage()),
                                record.getDailyTotal()),
                        record.isTaken() ? "已服药 (点击取消)" : "未服药 (点击标记已服)",
                        record.isTaken() ? R.drawable.circle_checked : R.drawable.circle_unchecked,
                        record));
            }
        }
        adapter.submitList(items);
        progressLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddDialog() {
        LinearLayout root = buildMedicationInput(null);
        EditText etName = (EditText) root.getChildAt(0);
        EditText etDose = (EditText) root.getChildAt(1);

        new MaterialAlertDialogBuilder(requireContext()).setTitle("新增用药").setView(root)
                .setPositiveButton("保存", (d, w) -> {
                    String name = etName.getText() == null ? "" : etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "请输入药名", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String dose = etDose.getText() == null ? "" : etDose.getText().toString().trim();
                    EditText etTotal = (EditText) root.getChildAt(2);
                    int total = 1;
                    try {
                        total = Integer.parseInt(etTotal.getText().toString());
                    } catch (Exception ignored) {
                    }
                    CheckBox cb = (CheckBox) root.getChildAt(3);
                    viewModel.addMedication(name, dose, total, cb.isChecked(), null);
                }).setNegativeButton("取消", null).show();
    }

    private void showEditDialog(MedicationRecord record) {
        LinearLayout root = buildMedicationInput(record);
        EditText etName = (EditText) root.getChildAt(0);
        EditText etDose = (EditText) root.getChildAt(1);

        new MaterialAlertDialogBuilder(requireContext()).setTitle("编辑用药").setView(root)
                .setPositiveButton("保存", (d, w) -> {
                    String name = etName.getText() == null ? "" : etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "请输入药名", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String dose = etDose.getText() == null ? "" : etDose.getText().toString().trim();
                    EditText etTotal = (EditText) root.getChildAt(2);
                    int total = 1;
                    try {
                        total = Integer.parseInt(etTotal.getText().toString());
                    } catch (Exception ignored) {
                    }
                    CheckBox cb = (CheckBox) root.getChildAt(3);
                    record.setName(name);
                    record.setDosage(dose);
                    record.setDailyTotal(total);
                    record.setTaken(cb.isChecked());
                    viewModel.updateMedication(record);
                }).setNegativeButton("取消", null).show();
    }

    private LinearLayout buildMedicationInput(@Nullable MedicationRecord record) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);

        EditText etName = new EditText(requireContext());
        etName.setHint("药名");
        if (record != null)
            etName.setText(record.getName());
        root.addView(etName);

        EditText etDose = new EditText(requireContext());
        etDose.setHint("剂量(可选)");
        if (record != null)
            etDose.setText(record.getDosage());
        root.addView(etDose);

        EditText etTotal = new EditText(requireContext());
        etTotal.setHint("每日需服药次数(默认1)");
        etTotal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (record != null)
            etTotal.setText(String.valueOf(record.getDailyTotal()));
        else
            etTotal.setText("1");
        root.addView(etTotal);

        CheckBox cbTaken = new CheckBox(requireContext());
        cbTaken.setText("本次已服用");
        cbTaken.setChecked(record != null && record.isTaken());
        root.addView(cbTaken);

        return root;
    }

    private void showReminderDialog() {
        com.google.android.material.timepicker.MaterialTimePicker timePicker = new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setHour(8)
                .setMinute(0)
                .setTitleText("设置每日用药提醒时间")
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            int hour = timePicker.getHour();
            int minute = timePicker.getMinute();

            // 构建 ReminderSchedule (module, targetId, h, m, days, enabled, title, text)
            ReminderSchedule schedule = new ReminderSchedule(
                    "medication",
                    System.currentTimeMillis(), // 临时 targetId
                    hour,
                    minute,
                    "1,2,3,4,5,6,7", // 每天
                    true,
                    "用药提醒",
                    "该吃药了，请按时服药哦！");

            // 存入数据库并在后台真正配置闹钟
            new Thread(() -> {
                com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext())
                        .reminderScheduleDao()
                        .insert(schedule);

                requireActivity().runOnUiThread(() -> {
                    ReminderManager.schedule(requireContext(), schedule);
                    Toast.makeText(requireContext(),
                            "已设置每日 " + String.format(Locale.getDefault(), "%02d:%02d", hour, minute) + " 的用药提醒",
                            Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        timePicker.show(getParentFragmentManager(), "MedicationTimePicker");
    }
}
