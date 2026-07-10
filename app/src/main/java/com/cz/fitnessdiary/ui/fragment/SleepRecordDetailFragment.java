package com.cz.fitnessdiary.ui.fragment;

import android.app.TimePickerDialog;
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
import com.cz.fitnessdiary.ui.widget.SleepChartView;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.SleepDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

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
    private SleepChartView barSleep;
    private int selectedTab = 0; // 0:Week, 1:Month, 2:Year

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
        MaterialButton btnTabYear = view.findViewById(R.id.btn_tab_year);
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

        ExtendedFloatingActionButton fabAdd = view.findViewById(R.id.fab_add);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> showSleepDialog(null));
        fabAdd.setOnClickListener(v -> showSleepDialog(null));

        btnTabWeek.setOnClickListener(v -> {
            selectedTab = 0;
            updateSleepChart(viewModel.getWeekSeries().getValue());
        });
        btnTabMonth.setOnClickListener(v -> {
            selectedTab = 1;
            updateSleepChart(viewModel.getMonthSeries().getValue());
        });
        btnTabYear.setOnClickListener(v -> {
            selectedTab = 2;
            updateSleepChart(viewModel.getYearSeries().getValue());
        });

        viewModel.getWeekSeries().observe(getViewLifecycleOwner(), values -> {
            if (selectedTab == 0)
                updateSleepChart(values);
        });
        viewModel.getMonthSeries().observe(getViewLifecycleOwner(), values -> {
            if (selectedTab == 1)
                updateSleepChart(values);
        });
        viewModel.getYearSeries().observe(getViewLifecycleOwner(), values -> {
            if (selectedTab == 2)
                updateSleepChart(values);
        });

        viewModel.getSelectedDateRecords().observe(getViewLifecycleOwner(), this::renderRecords);

        // 绑定睡眠分析卡片控件
        TextView tvAvgSleepDuration = view.findViewById(R.id.tv_avg_sleep_duration);
        TextView tvSleepSufficientRatio = view.findViewById(R.id.tv_sleep_sufficient_ratio);
        TextView tvLatestBedtime = view.findViewById(R.id.tv_latest_bedtime);
        TextView tvSleepWarning = view.findViewById(R.id.tv_sleep_warning);
        TextView tvSleepLocalAdvice = view.findViewById(R.id.tv_sleep_local_advice);
        MaterialButton btnSleepAiDiagnosis = view.findViewById(R.id.btn_sleep_ai_diagnosis);

        viewModel.getAvgSleepDuration().observe(getViewLifecycleOwner(), val -> {
            if (tvAvgSleepDuration != null) tvAvgSleepDuration.setText(String.format(Locale.getDefault(), "%.1f h", val));
        });
        viewModel.getSufficientRatio().observe(getViewLifecycleOwner(), ratio -> {
            if (tvSleepSufficientRatio != null) tvSleepSufficientRatio.setText(String.format(Locale.getDefault(), "%.1f%%", ratio));
        });
        viewModel.getLatestBedtime().observe(getViewLifecycleOwner(), time -> {
            if (tvLatestBedtime != null) tvLatestBedtime.setText(time);
        });
        viewModel.getSleepWarning().observe(getViewLifecycleOwner(), warning -> {
            if (tvSleepWarning != null) tvSleepWarning.setText("睡眠异常警告：" + warning);
        });
        viewModel.getSleepAdvice().observe(getViewLifecycleOwner(), advice -> {
            if (tvSleepLocalAdvice != null) tvSleepLocalAdvice.setText(advice);
        });

        if (btnSleepAiDiagnosis != null) {
            btnSleepAiDiagnosis.setOnClickListener(v -> {
                double avgHours = viewModel.getAvgSleepDuration().getValue() != null ? viewModel.getAvgSleepDuration().getValue() : 0.0;
                float suffRatio = viewModel.getSufficientRatio().getValue() != null ? viewModel.getSufficientRatio().getValue() : 0f;
                String latestBedtimeStr = viewModel.getLatestBedtime().getValue() != null ? viewModel.getLatestBedtime().getValue() : "--";
                String sleepWarningStr = viewModel.getSleepWarning().getValue() != null ? viewModel.getSleepWarning().getValue() : "作息规律健康 ✓";

                String prompt = String.format(Locale.getDefault(),
                        "用户近30天睡眠数据统计如下：\n" +
                        "- 平均每日睡眠时长：%.1f 小时\n" +
                        "- 睡眠充足达标率（每日在 6-9 小时之间）：%.1f%%\n" +
                        "- 最晚入睡时间：%s\n" +
                        "- 睡眠作息预警：%s\n\n" +
                        "请扮演专业睡眠健康医学顾问，生成一份睡眠质量调理报告。包含：\n" +
                        "1. 睡眠状态与作息规律评估；\n" +
                        "2. 针对性睡眠环境与作息调整建议；\n" +
                        "3. 睡前仪式与助眠改善动作。\n" +
                        "回答要求结构清晰（Markdown格式展示），语气温柔、具亲和力，且控制在 350 字以内。",
                        avgHours, suffRatio, latestBedtimeStr, sleepWarningStr);

                btnSleepAiDiagnosis.setEnabled(false);
                btnSleepAiDiagnosis.setText("✨ AI 睡眠质量诊断中...");

                String systemInstruction = "你是 FitnessDiary 睡眠健康顾问，一名资深的睡眠医学专家。请提供专业、简明、排版优美、字数在 350 字以内的中文评估报告。";
                com.cz.fitnessdiary.service.DeepSeekService.sendMessage(prompt, systemInstruction, false, null, new com.cz.fitnessdiary.service.AICallback() {
                    @Override
                    public void onSuccess(String response, String reasoning) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                btnSleepAiDiagnosis.setEnabled(true);
                                btnSleepAiDiagnosis.setText("✨ 生成 AI 睡眠健康诊断报告");
                                showReportDialog("✨ AI 睡眠健康评估报告", response);
                            });
                        }
                    }

                    @Override
                    public void onPartialUpdate(String content, String reasoning) {}

                    @Override
                    public void onError(String error) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                btnSleepAiDiagnosis.setEnabled(true);
                                btnSleepAiDiagnosis.setText("✨ 生成 AI 睡眠健康诊断报告");
                                String localReport = generateLocalSleepReport(avgHours, suffRatio, latestBedtimeStr);
                                showReportDialog("📊 睡眠健康调理方案 (本地智能引擎)", localReport);
                            });
                        }
                    }
                });
            });
        }

        viewModel.refreshStatsSeries();
    }

    private void updateSleepChart(List<Float> values) {
        if (values == null)
            return;
        List<String> labels = new ArrayList<>();
        long dayStart = DateUtils
                .getDayStartTimestamp(viewModel.getSelectedDate().getValue() == null ? System.currentTimeMillis()
                        : viewModel.getSelectedDate().getValue());
        SimpleDateFormat df = new SimpleDateFormat("MM-dd", Locale.getDefault());
        SimpleDateFormat yf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        float sum = 0f;
        int count = 0;

        for (int i = values.size() - 1; i >= 0; i--) { // Reverse index for display
            if (selectedTab == 0) { // Week
                labels.add(df.format(new Date(dayStart - i * 24L * 60L * 60L * 1000L)));
            } else if (selectedTab == 1) { // Month
                labels.add(df.format(new Date(dayStart - i * 24L * 60L * 60L * 1000L)));
            } else { // Year
                labels.add(yf.format(new Date(dayStart - i * 30L * 24L * 60L * 60L * 1000L)));
            }
        }
        for (Float v : values) {
            if (v != null && v > 0) {
                sum += v;
                count++;
            }
        }
        float avg = count > 0 ? sum / count : 0f;
        barSleep.setData(values, labels, avg);
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
        tvWindow.setText(
                "最近 " + tf.format(new Date(latest.getStartTime())) + " - " + tf.format(new Date(latest.getEndTime())));
    }

    private void showSleepDialog(@Nullable SleepRecord existing) {
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        final int[] startHour = {23};
        final int[] startMinute = {0};
        final int[] endHour = {7};
        final int[] endMinute = {0};

        if (existing != null) {
            Calendar sc = Calendar.getInstance();
            sc.setTimeInMillis(existing.getStartTime());
            Calendar ec = Calendar.getInstance();
            ec.setTimeInMillis(existing.getEndTime());
            startHour[0] = sc.get(Calendar.HOUR_OF_DAY);
            startMinute[0] = sc.get(Calendar.MINUTE);
            endHour[0] = ec.get(Calendar.HOUR_OF_DAY);
            endMinute[0] = ec.get(Calendar.MINUTE);
        }

        TextView tvStartLabel = new TextView(requireContext());
        tvStartLabel.setText("入睡时间");
        tvStartLabel.setTextSize(14);
        tvStartLabel.setTextColor(0xFF666666);
        tvStartLabel.setPadding(0, 0, 0, 8);
        root.addView(tvStartLabel);

        TextView tvStart = new TextView(requireContext());
        tvStart.setText(String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]));
        tvStart.setTextSize(18);
        tvStart.setPadding(0, 0, 0, 24);
        tvStart.setOnClickListener(v -> {
            new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
                startHour[0] = hourOfDay;
                startMinute[0] = minute;
                tvStart.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
            }, startHour[0], startMinute[0], true).show();
        });
        root.addView(tvStart);

        TextView tvEndLabel = new TextView(requireContext());
        tvEndLabel.setText("起床时间");
        tvEndLabel.setTextSize(14);
        tvEndLabel.setTextColor(0xFF666666);
        tvEndLabel.setPadding(0, 0, 0, 8);
        root.addView(tvEndLabel);

        TextView tvEnd = new TextView(requireContext());
        tvEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]));
        tvEnd.setTextSize(18);
        tvEnd.setPadding(0, 0, 0, 24);
        tvEnd.setOnClickListener(v -> {
            new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
                endHour[0] = hourOfDay;
                endMinute[0] = minute;
                tvEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
            }, endHour[0], endMinute[0], true).show();
        });
        root.addView(tvEnd);

        EditText etQuality = new EditText(requireContext());
        etQuality.setHint("睡眠质量 1-5");
        etQuality.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (existing != null) {
            etQuality.setText(String.valueOf(existing.getQuality()));
        } else {
            etQuality.setText("3");
        }
        root.addView(etQuality);

        EditText etNote = new EditText(requireContext());
        etNote.setHint("备注");
        if (existing != null) {
            etNote.setText(existing.getNotes());
        }
        root.addView(etNote);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? "新增睡眠" : "编辑睡眠")
                .setView(root)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int sh = startHour[0];
                        int sm = startMinute[0];
                        int eh = endHour[0];
                        int em = endMinute[0];
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
                        if (start.getTimeInMillis() >= end.getTimeInMillis())
                            start.add(Calendar.DAY_OF_YEAR, -1);

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
                        Toast.makeText(getContext(), "请填写完整的睡眠信息", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String generateLocalSleepReport(double avgHours, float suffRatio, String latestBedtimeStr) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🩺 睡眠状态与作息评估\n");
        if (avgHours < 6.0) {
            sb.append("近30天您的日均睡眠量为**").append(String.format(Locale.getDefault(), "%.1f 小时", avgHours)).append("**，明显低于健康的成年人睡眠标准。这极易导致日间疲劳和免疫力下降。\n\n");
        } else if (suffRatio < 50.0f) {
            sb.append("近30天您的睡眠达标率偏低（**").append(String.format(Locale.getDefault(), "%.1f%%", suffRatio)).append("**），表明您的作息质量波动剧烈，缺乏规律的生物钟节奏。\n\n");
        } else {
            sb.append("近30天您的睡眠质量表现**相当优异**，规律达标率高达 ").append(String.format(Locale.getDefault(), "%.1f%%", suffRatio)).append("。高规律睡眠能有效促进身体组织修复 and 能量代谢恢复。\n\n");
        }

        sb.append("### 🛌 睡眠环境与作息调整建议\n");
        sb.append("- **光照重塑昼夜节律**：清晨起床后接受 15 分钟户外自然光照，抑制褪黑素分泌，拉大昼夜生物钟相位差。\n");
        sb.append("- **卧室降温与降噪**：卧室温度维持在 18-22°C 左右最利于快速入眠，可使用防噪耳塞或遮光窗帘。\n");

        sb.append("\n### 🧘 睡前仪式与助眠动作\n");
        sb.append("- **睡前 30 分钟冥想**：平躺于床上，进行 5 分钟的深呼吸吸气 4 秒、屏气 7 秒、呼气 8 秒（4-7-8 呼吸法），降低中枢神经过度兴奋。\n");
        sb.append("- **禁止蓝光刺激**：睡前 1 小时严格停用一切发光电子屏幕（如手机、电脑），避免蓝光阻碍褪黑素天然合成。");
        return sb.toString();
    }

    private void showReportDialog(String title, String content) {
        if (getContext() == null) return;
        TextView tv = new TextView(getContext());
        tv.setTextSize(14f);
        tv.setPadding(48, 36, 48, 36);
        tv.setLineSpacing(1.3f, 1.3f);
        
        int textColor = 0xFF212121;
        try {
            textColor = getResources().getColor(R.color.text_primary);
        } catch (Exception ignored) {}
        tv.setTextColor(textColor);

        String formatted = content
                .replace("\n", "<br/>")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("### (.*?)<br/>", "<b><font color='#673AB7'>$1</font></b><br/>");

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
