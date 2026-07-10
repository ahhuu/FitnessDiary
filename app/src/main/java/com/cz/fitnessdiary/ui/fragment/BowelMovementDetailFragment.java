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
    private android.widget.ImageView ivHeaderIcon;
    private TextView tvConstipationRatio, tvNormalRatio, tvDiarrheaRatio;
    private TextView tvAvgDuration, tvColorAlert, tvLocalAdvice;
    private com.google.android.material.button.MaterialButton btnAiDiagnosis;

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
        ivHeaderIcon = view.findViewById(R.id.iv_header_icon);
        tvHealthScore = view.findViewById(R.id.tv_health_score);
        tvAvgBristol = view.findViewById(R.id.tv_avg_bristol);
        tvColorSummary = view.findViewById(R.id.tv_color_summary);
        tvEmpty = view.findViewById(R.id.tv_empty);
        tvConstipationRatio = view.findViewById(R.id.tv_constipation_ratio);
        tvNormalRatio = view.findViewById(R.id.tv_normal_ratio);
        tvDiarrheaRatio = view.findViewById(R.id.tv_diarrhea_ratio);
        tvAvgDuration = view.findViewById(R.id.tv_avg_duration);
        tvColorAlert = view.findViewById(R.id.tv_color_alert);
        tvLocalAdvice = view.findViewById(R.id.tv_local_advice);
        btnAiDiagnosis = view.findViewById(R.id.btn_ai_diagnosis);
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
        android.os.Bundle args = getArguments();
        long selectedDate = args != null ? args.getLong("selectedDate", System.currentTimeMillis()) : System.currentTimeMillis();
        viewModel.setSelectedDate(selectedDate);

        btnBack.setOnClickListener(v -> androidx.navigation.Navigation.findNavController(v).navigateUp());
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

        // Observe newly added digestive ratios, average duration, warning and local advice
        viewModel.getConstipationRatio().observe(getViewLifecycleOwner(), ratio -> {
            if (tvConstipationRatio != null) tvConstipationRatio.setText(String.format(Locale.getDefault(), "%.0f%%", ratio));
        });
        viewModel.getNormalRatio().observe(getViewLifecycleOwner(), ratio -> {
            if (tvNormalRatio != null) tvNormalRatio.setText(String.format(Locale.getDefault(), "%.0f%%", ratio));
        });
        viewModel.getDiarrheaRatio().observe(getViewLifecycleOwner(), ratio -> {
            if (tvDiarrheaRatio != null) tvDiarrheaRatio.setText(String.format(Locale.getDefault(), "%.0f%%", ratio));
        });
        viewModel.getAvgDurationSeconds().observe(getViewLifecycleOwner(), avg -> {
            if (tvAvgDuration != null) {
                if (avg > 0) {
                    tvAvgDuration.setText(String.format(Locale.getDefault(), "平均排便时间：%.1f 分钟", avg));
                } else {
                    tvAvgDuration.setText("平均排便时间：暂无数据");
                }
            }
        });
        viewModel.getColorAlert().observe(getViewLifecycleOwner(), alert -> {
            if (tvColorAlert != null) tvColorAlert.setText("便便颜色警告：" + alert);
        });
        viewModel.getLocalAdvice().observe(getViewLifecycleOwner(), advice -> {
            if (tvLocalAdvice != null) tvLocalAdvice.setText(advice);
        });

        if (btnAiDiagnosis != null) {
            btnAiDiagnosis.setOnClickListener(v -> {
                float constiRatio = viewModel.getConstipationRatio().getValue() != null ? viewModel.getConstipationRatio().getValue() : 0f;
                float normRatio = viewModel.getNormalRatio().getValue() != null ? viewModel.getNormalRatio().getValue() : 100f;
                float diarrRatio = viewModel.getDiarrheaRatio().getValue() != null ? viewModel.getDiarrheaRatio().getValue() : 0f;
                float avgDur = viewModel.getAvgDurationSeconds().getValue() != null ? viewModel.getAvgDurationSeconds().getValue() : 0f;
                String colorAlertStr = viewModel.getColorAlert().getValue() != null ? viewModel.getColorAlert().getValue() : "正常 ✓";

                String prompt = String.format(Locale.getDefault(),
                        "用户近30天便便规律统计如下：\n" +
                        "- 便秘占比（Bristol 1-2 型）：%.1f%%\n" +
                        "- 正常占比（Bristol 3-5 型）：%.1f%%\n" +
                        "- 腹泻占比（Bristol 6-7 型）：%.1f%%\n" +
                        "- 平均排便时长：%.1f 分钟\n" +
                        "- 便便颜色预警：%s\n\n" +
                        "请扮演专业消化科医生，生成一份胃肠健康评估与调理报告。包含：\n" +
                        "1. 胃肠状态综合评估；\n" +
                        "2. 针对性饮食调理建议（膳食纤维、饮水安排等）；\n" +
                        "3. 生活习惯与运动指导建议。\n" +
                        "回答要求结构清晰（Markdown格式展示），语气温柔、具亲和力，且控制在 350 字以内。",
                        constiRatio, normRatio, diarrRatio, avgDur, colorAlertStr);

                btnAiDiagnosis.setEnabled(false);
                btnAiDiagnosis.setText("✨ AI 胃肠健康诊断报告生成中...");

                String systemInstruction = "你是 FitnessDiary 消化健康智能顾问，一名资深的消化内科医生。请提供专业、简明、排版优美、字数在 350 字以内的中文评估报告。";
                com.cz.fitnessdiary.service.DeepSeekService.sendMessage(prompt, systemInstruction, false, null, new com.cz.fitnessdiary.service.AICallback() {
                    @Override
                    public void onSuccess(String response, String reasoning) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                btnAiDiagnosis.setEnabled(true);
                                btnAiDiagnosis.setText("✨ 生成 AI 胃肠健康诊断报告");
                                showReportDialog("✨ AI 胃肠健康诊断报告", response);
                            });
                        }
                    }

                    @Override
                    public void onPartialUpdate(String content, String reasoning) {}

                    @Override
                    public void onError(String error) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                btnAiDiagnosis.setEnabled(true);
                                btnAiDiagnosis.setText("✨ 生成 AI 胃肠健康诊断报告");
                                String localReport = generateLocalMedicalReport(constiRatio, normRatio, diarrRatio, avgDur, colorAlertStr);
                                showReportDialog("📊 胃肠健康诊断报告 (本地智能引擎)", localReport);
                            });
                        }
                    }
                });
            });
        }
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
                        subtitle, getBristolIconRes(r.getBristolType()), r));
            }
        }
        adapter.submitList(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        // Update latest Bristol display
        if (records != null && !records.isEmpty()) {
            BowelMovement latest = records.get(0);
            tvLatestBristol.setText("最近分型：Type " + latest.getBristolType());
            if (ivHeaderIcon != null) {
                ivHeaderIcon.setImageResource(getBristolIconRes(latest.getBristolType()));
            }
        } else {
            tvLatestBristol.setText("暂无记录");
            if (ivHeaderIcon != null) {
                ivHeaderIcon.setImageResource(R.drawable.ic_hero_bowel);
            }
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
    private int getBristolIconRes(int type) {
        return com.cz.fitnessdiary.utils.AnalysisUtils.getBristolIconRes(type);
    }

    private String generateLocalMedicalReport(float constiRatio, float normRatio, float diarrRatio, float avgDur, String colorAlert) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🩺 胃肠状态综合评估\n");
        if (constiRatio > 30f) {
            sb.append("近30天您的排便呈现**较明显的便秘特征**（便秘占比达 ").append(String.format(Locale.getDefault(), "%.0f%%", constiRatio)).append("）。大肠水分被过度吸收，导致便便干结、排出困难。\n\n");
        } else if (diarrRatio > 30f) {
            sb.append("近30天您的排便呈现**较明显的腹泻特征**（腹泻占比达 ").append(String.format(Locale.getDefault(), "%.0f%%", diarrRatio)).append("）。肠道蠕动过快，可能伴随轻度菌群失调或消化不良。\n\n");
        } else {
            sb.append("近30天您的肠道处于**非常健康稳定的状态**（正常比例高达 ").append(String.format(Locale.getDefault(), "%.0f%%", normRatio)).append("）。肠道微生物屏障与运动节律运行极佳。\n\n");
        }

        if (avgDur > 10f) {
            sb.append("⚠️ **排便规律警示**：您的平均排便时间偏长（约 ").append(String.format(Locale.getDefault(), "%.1f分钟", avgDur)).append("），建议上厕所时不要看手机，久坐久蹲容易诱发痔疮或盆底肌疲劳。\n\n");
        }

        sb.append("### 🥗 针对性饮食调理建议\n");
        if (constiRatio > 30f) {
            sb.append("- **增加不可溶性膳食纤维**：多吃大麦、红薯、火龙果和燕麦麸皮，增加大便体积。\n");
            sb.append("- **科学饮水**：保证每日饮水 2000-2500ml，清晨空腹饮用 300ml 温水唤醒肠道。\n");
        } else if (diarrRatio > 30f) {
            sb.append("- **少食多餐与低渣饮食**：减少生冷瓜果、油炸及辛辣食物的刺激，多吃米粥、烂面条等温和易消化的食物。\n");
            sb.append("- **补液盐与益生菌**：适量补充常温淡盐水，可连续服用双歧杆菌等益生菌调理肠道菌群。\n");
        } else {
            sb.append("- **维持目前膳食结构**：保证每天摄入一拳头大小的水果和一盘新鲜绿色蔬菜，粗细搭配。\n");
        }

        sb.append("\n### 🏃 生活习惯与运动指导\n");
        sb.append("- **顺时针腹部按摩**：以脐周为中心，顺时针方向轻揉按摩腹部，每日 5-10 分钟以促进胃肠蠕动。\n");
        sb.append("- **有氧快走**：每日饭后半小时坚持散步或快走 20 分钟，物理活动有助于激活肠胃。\n");
        sb.append("- **定期体检**：若便便颜色预警持续出现异常，或伴有突发腹痛、不明消瘦，请前往消化内科及时面诊。");
        return sb.toString();
    }

    private void showReportDialog(String title, String content) {
        if (getContext() == null) return;
        TextView tv = new TextView(getContext());
        tv.setTextSize(14f);
        tv.setPadding(48, 36, 48, 36);
        tv.setLineSpacing(1.3f, 1.3f);
        
        // 尝试从应用 Theme 中读取或直接使用标准颜色
        int textColor = 0xFF212121;
        try {
            textColor = getResources().getColor(R.color.text_primary);
        } catch (Exception ignored) {}
        tv.setTextColor(textColor);

        String formatted = content
                .replace("\n", "<br/>")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("### (.*?)<br/>", "<b><font color='#4CAF50'>$1</font></b><br/>");

        tv.setText(android.text.Html.fromHtml(formatted, android.text.Html.FROM_HTML_MODE_LEGACY));

        android.widget.ScrollView sv = new android.widget.ScrollView(getContext());
        sv.addView(tv);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("我知道了", null)
                .show();
    }
}
