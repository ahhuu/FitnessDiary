package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
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
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.viewmodel.MedicationDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
                showEditDialog((MedicationRecord) item.payload);
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

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showAddDialog());
        btnOpenReminder.setOnClickListener(v -> Toast.makeText(getContext(), "提醒配置入口将在提醒页统一管理", Toast.LENGTH_SHORT).show());

        viewModel.getTakenCount().observe(getViewLifecycleOwner(), count ->
                tvSummary.setText("今日已服药 " + (count == null ? 0 : count) + " 次"));
        viewModel.getUntakenCount().observe(getViewLifecycleOwner(), count ->
                tvUntaken.setText("今日未服药 " + (count == null ? 0 : count) + " 次"));

        viewModel.getSelectedDateRecords().observe(getViewLifecycleOwner(), this::renderRecords);
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
                        record.getDosage() == null || record.getDosage().isEmpty() ? "--" : record.getDosage(),
                        record.isTaken() ? "已服药" : "未服药",
                        R.drawable.ic_hourglass,
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
        CheckBox checkBox = (CheckBox) root.getChildAt(2);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("新增用药").setView(root)
                .setPositiveButton("保存", (d, w) -> {
                    String name = etName.getText() == null ? "" : etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "请输入药名", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String dose = etDose.getText() == null ? "" : etDose.getText().toString().trim();
                    viewModel.addMedication(name, dose, checkBox.isChecked(), null);
                }).setNegativeButton("取消", null).show();
    }

    private void showEditDialog(MedicationRecord record) {
        LinearLayout root = buildMedicationInput(record);
        EditText etName = (EditText) root.getChildAt(0);
        EditText etDose = (EditText) root.getChildAt(1);
        CheckBox checkBox = (CheckBox) root.getChildAt(2);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("编辑用药").setView(root)
                .setPositiveButton("保存", (d, w) -> {
                    String name = etName.getText() == null ? "" : etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "请输入药名", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String dose = etDose.getText() == null ? "" : etDose.getText().toString().trim();
                    record.setName(name);
                    record.setDosage(dose);
                    record.setTaken(checkBox.isChecked());
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
        if (record != null) etName.setText(record.getName());
        root.addView(etName);

        EditText etDose = new EditText(requireContext());
        etDose.setHint("剂量(可选)");
        if (record != null) etDose.setText(record.getDosage());
        root.addView(etDose);

        CheckBox checkBox = new CheckBox(requireContext());
        checkBox.setText("已服用");
        checkBox.setChecked(record == null || record.isTaken());
        root.addView(checkBox);

        return root;
    }
}
