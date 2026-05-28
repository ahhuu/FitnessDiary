package com.cz.fitnessdiary.ui.fragment;

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
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.ui.adapter.DetailRecordAdapter;
import com.cz.fitnessdiary.ui.widget.BristolChartView;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.BowelDetailViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BowelMovementDetailFragment extends Fragment {

    private BowelDetailViewModel viewModel;
    private DetailRecordAdapter adapter;
    private BristolChartView bristolChart;
    private TextView tvHeaderSummary, tvTodayCount, tvLatestBristol;
    private TextView tvHealthScore, tvAvgBristol, tvColorSummary, tvEmpty;

    private static final int[] BRISTOL_CHIP_IDS = {
            R.id.chip_b1, R.id.chip_b2, R.id.chip_b3, R.id.chip_b4, R.id.chip_b5, R.id.chip_b6, R.id.chip_b7
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bowel_movement_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        bristolChart = view.findViewById(R.id.chart_bristol);
        tvHeaderSummary = view.findViewById(R.id.tv_header_summary);
        tvTodayCount = view.findViewById(R.id.tv_today_count);
        tvLatestBristol = view.findViewById(R.id.tv_latest_bristol);
        tvHealthScore = view.findViewById(R.id.tv_health_score);
        tvAvgBristol = view.findViewById(R.id.tv_avg_bristol);
        tvColorSummary = view.findViewById(R.id.tv_color_summary);
        tvEmpty = view.findViewById(R.id.tv_empty);
        RecyclerView rvRecords = view.findViewById(R.id.rv_records);
        ExtendedFloatingActionButton fabAdd = view.findViewById(R.id.fab_add);

        adapter = new DetailRecordAdapter(new DetailRecordAdapter.OnItemActionListener() {
            @Override
            public void onClick(DetailRecordAdapter.Item item) {
                showEditDialog((BowelMovement) item.payload);
            }

            @Override
            public void onLongClick(DetailRecordAdapter.Item item) {
                BowelMovement record = (BowelMovement) item.payload;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除记录")
                        .setMessage("确认删除这条便便记录？")
                        .setPositiveButton("删除", (d, w) -> viewModel.deleteRecord(record))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        rvRecords.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecords.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(BowelDetailViewModel.class);
        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        viewModel.setSelectedDate(selectedDate);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        fabAdd.setOnClickListener(v -> showAddDialog());

        // Observe data
        viewModel.getAllRecords().observe(getViewLifecycleOwner(), this::renderRecords);
        viewModel.getBristolDistribution().observe(getViewLifecycleOwner(), bristolChart::setData);
        viewModel.getDailyCount().observe(getViewLifecycleOwner(), count -> {
            tvTodayCount.setText("今日次数：" + (count != null ? count : 0));
            tvHeaderSummary.setText("今日 " + (count != null ? count : 0) + " 次");
        });
        viewModel.getAvgBristol().observe(getViewLifecycleOwner(), avg -> {
            if (avg != null && avg > 0) {
                tvAvgBristol.setText(String.format(Locale.getDefault(), "平均分型：%.1f", avg));
            }
        });
        viewModel.getDigestiveHealthScore().observe(getViewLifecycleOwner(), score -> {
            if (score != null) {
                String desc;
                if (score >= 80) desc = "优秀";
                else if (score >= 60) desc = "良好";
                else if (score >= 40) desc = "一般";
                else desc = "需关注";
                tvHealthScore.setText(score + " 分 · " + desc);
            }
        });
        viewModel.getColorDistribution().observe(getViewLifecycleOwner(), colors -> {
            if (colors != null && !colors.isEmpty()) {
                StringBuilder sb = new StringBuilder("颜色分布：");
                for (Map.Entry<String, Integer> e : colors.entrySet()) {
                    sb.append(colorName(e.getKey())).append("(").append(e.getValue()).append(") ");
                }
                tvColorSummary.setText(sb.toString().trim());
            }
        });
    }

    private void renderRecords(List<BowelMovement> records) {
        List<DetailRecordAdapter.Item> items = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        if (records != null) {
            for (BowelMovement r : records) {
                String bristolName = "Type " + r.getBristolType();
                switch (r.getBristolType()) {
                    case 1: bristolName = "1-坚果状(便秘)"; break;
                    case 2: bristolName = "2-干裂香肠(便秘)"; break;
                    case 3: bristolName = "3-玉米状(正常)"; break;
                    case 4: bristolName = "4-香蕉状(理想)"; break;
                    case 5: bristolName = "5-软团状(偏稀)"; break;
                    case 6: bristolName = "6-糊状(腹泻)"; break;
                    case 7: bristolName = "7-水状(腹泻)"; break;
                }
                String subtitle = (r.getColor() != null ? colorName(r.getColor()) : "") +
                        (r.getProcessFeeling() != null ? " · " + feelingName(r.getProcessFeeling()) : "");
                items.add(new DetailRecordAdapter.Item(
                        r.getId(), bristolName,
                        sdf.format(new Date(r.getTimestamp())),
                        (r.getDurationSeconds() > 0 ? r.getDurationSeconds() + "分钟" : ""),
                        subtitle, R.drawable.ic_hero_bowel, r));
            }
        }
        adapter.submitList(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        // Update latest Bristol display
        if (records != null && !records.isEmpty()) {
            BowelMovement latest = records.get(0);
            tvLatestBristol.setText("最近分型：Type " + latest.getBristolType());
        }
    }

    private void showAddDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_bowel_movement, null);
        EditText etDuration = dialogView.findViewById(R.id.et_duration);
        EditText etNote = dialogView.findViewById(R.id.et_note);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加便便记录")
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    int bristolType = getCheckedBristol(dialogView);
                    String color = getCheckedString(dialogView,
                            new int[]{R.id.chip_brown, R.id.chip_green, R.id.chip_yellow, R.id.chip_red, R.id.chip_black},
                            new String[]{"BROWN", "GREEN", "YELLOW", "RED", "BLACK"});
                    String volume = getCheckedString(dialogView,
                            new int[]{R.id.chip_small, R.id.chip_medium, R.id.chip_large},
                            new String[]{"SMALL", "MEDIUM", "LARGE"});
                    String smell = getCheckedString(dialogView,
                            new int[]{R.id.chip_normal_smell, R.id.chip_strong, R.id.chip_sour},
                            new String[]{"NORMAL", "STRONG", "SOUR"});
                    String feeling = getCheckedString(dialogView,
                            new int[]{R.id.chip_normal_feel, R.id.chip_difficult, R.id.chip_incomplete, R.id.chip_urgent},
                            new String[]{"NORMAL", "DIFFICULT", "INCOMPLETE", "URGENT"});
                    int duration = 0;
                    try { duration = Integer.parseInt(etDuration.getText().toString().trim()); } catch (Exception ignored) {}

                    BowelMovement record = new BowelMovement(bristolType, color, volume, smell, feeling,
                            duration, System.currentTimeMillis(), etNote.getText().toString().trim());
                    viewModel.addRecord(record);
                    Toast.makeText(getContext(), "已添加", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditDialog(BowelMovement record) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_bowel_movement, null);
        EditText etDuration = dialogView.findViewById(R.id.et_duration);
        EditText etNote = dialogView.findViewById(R.id.et_note);

        // Pre-fill
        if (record.getBristolType() >= 1 && record.getBristolType() <= 7) {
            ((Chip) dialogView.findViewById(BRISTOL_CHIP_IDS[record.getBristolType() - 1])).setChecked(true);
        }
        preSelectChip(dialogView, record.getColor(),
                new int[]{R.id.chip_brown, R.id.chip_green, R.id.chip_yellow, R.id.chip_red, R.id.chip_black},
                new String[]{"BROWN", "GREEN", "YELLOW", "RED", "BLACK"});
        preSelectChip(dialogView, record.getVolume(),
                new int[]{R.id.chip_small, R.id.chip_medium, R.id.chip_large},
                new String[]{"SMALL", "MEDIUM", "LARGE"});
        preSelectChip(dialogView, record.getSmell(),
                new int[]{R.id.chip_normal_smell, R.id.chip_strong, R.id.chip_sour},
                new String[]{"NORMAL", "STRONG", "SOUR"});
        preSelectChip(dialogView, record.getProcessFeeling(),
                new int[]{R.id.chip_normal_feel, R.id.chip_difficult, R.id.chip_incomplete, R.id.chip_urgent},
                new String[]{"NORMAL", "DIFFICULT", "INCOMPLETE", "URGENT"});
        etDuration.setText(String.valueOf(record.getDurationSeconds()));
        etNote.setText(record.getNote() != null ? record.getNote() : "");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑便便记录")
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    int bristolType = getCheckedBristol(dialogView);
                    String color = getCheckedString(dialogView,
                            new int[]{R.id.chip_brown, R.id.chip_green, R.id.chip_yellow, R.id.chip_red, R.id.chip_black},
                            new String[]{"BROWN", "GREEN", "YELLOW", "RED", "BLACK"});
                    String volume = getCheckedString(dialogView,
                            new int[]{R.id.chip_small, R.id.chip_medium, R.id.chip_large},
                            new String[]{"SMALL", "MEDIUM", "LARGE"});
                    String smell = getCheckedString(dialogView,
                            new int[]{R.id.chip_normal_smell, R.id.chip_strong, R.id.chip_sour},
                            new String[]{"NORMAL", "STRONG", "SOUR"});
                    String feeling = getCheckedString(dialogView,
                            new int[]{R.id.chip_normal_feel, R.id.chip_difficult, R.id.chip_incomplete, R.id.chip_urgent},
                            new String[]{"NORMAL", "DIFFICULT", "INCOMPLETE", "URGENT"});
                    int duration = 0;
                    try { duration = Integer.parseInt(etDuration.getText().toString().trim()); } catch (Exception ignored) {}

                    record.setBristolType(bristolType);
                    record.setColor(color);
                    record.setVolume(volume);
                    record.setSmell(smell);
                    record.setProcessFeeling(feeling);
                    record.setDurationSeconds(duration);
                    record.setNote(etNote.getText().toString().trim());
                    viewModel.updateRecord(record);
                    Toast.makeText(getContext(), "已更新", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int getCheckedBristol(View root) {
        for (int i = 0; i < BRISTOL_CHIP_IDS.length; i++) {
            Chip chip = root.findViewById(BRISTOL_CHIP_IDS[i]);
            if (chip != null && chip.isChecked()) return i + 1;
        }
        return 4; // default
    }

    private String getCheckedString(View root, int[] ids, String[] values) {
        for (int i = 0; i < ids.length; i++) {
            Chip chip = root.findViewById(ids[i]);
            if (chip != null && chip.isChecked()) return values[i];
        }
        return values[0];
    }

    private void preSelectChip(View root, String value, int[] ids, String[] values) {
        if (value == null) return;
        for (int i = 0; i < values.length; i++) {
            if (value.equals(values[i])) {
                Chip chip = root.findViewById(ids[i]);
                if (chip != null) chip.setChecked(true);
                return;
            }
        }
    }

    private String colorName(String code) {
        switch (code) {
            case "BROWN": return "棕色";
            case "GREEN": return "绿色";
            case "YELLOW": return "黄色";
            case "RED": return "红色";
            case "BLACK": return "黑色";
            case "WHITE": return "白色";
            case "GREY": return "灰色";
            default: return code;
        }
    }

    private String feelingName(String code) {
        switch (code) {
            case "NORMAL": return "正常";
            case "DIFFICULT": return "困难";
            case "INCOMPLETE": return "不尽";
            case "URGENT": return "急迫";
            default: return code;
        }
    }
}
