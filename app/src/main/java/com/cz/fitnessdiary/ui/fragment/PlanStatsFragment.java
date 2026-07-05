package com.cz.fitnessdiary.ui.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.databinding.FragmentPlanStatsBinding;
import com.cz.fitnessdiary.repository.DailyLogRepository;
import com.cz.fitnessdiary.repository.TrainingPlanRepository;
import com.cz.fitnessdiary.repository.WeightRecordRepository;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.utils.ExerciseMetTable;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 历史累计与统计页面
 * 展示：大数字概览（累计天、期间次数、最长连续）、每日训练次数柱状图、
 *       每日时长折线图、体重趋势折线图、分类饼图、Top3 排行
 */
public class PlanStatsFragment extends Fragment {

    private FragmentPlanStatsBinding binding;
    private DailyLogRepository logRepo;
    private WeightRecordRepository weightRepo;
    private TrainingPlanRepository planRepo;
    private ExecutorService executor;
    private boolean isMonth = false;

    private static final int COLOR_PRIMARY = 0xFF5C8AE6;
    private static final int COLOR_SUCCESS = 0xFF43A047;
    private static final int COLOR_WEIGHT  = 0xFFAB47BC;
    private static final int[] PIE_COLORS  = {
            0xFF5C8AE6, 0xFF43A047, 0xFFFF7043, 0xFFFFCA28,
            0xFFAB47BC, 0xFF26C6DA, 0xFFEF5350, 0xFF8D6E63
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlanStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        logRepo    = new DailyLogRepository(requireActivity().getApplication());
        weightRepo = new WeightRecordRepository(requireActivity().getApplication());
        planRepo   = new TrainingPlanRepository(requireActivity().getApplication());
        executor   = Executors.newSingleThreadExecutor();

        // 返回按钮
        binding.btnBack.setOnClickListener(v -> {
            try {
                androidx.navigation.fragment.NavHostFragment navHost =
                        (androidx.navigation.fragment.NavHostFragment)
                        requireActivity().getSupportFragmentManager()
                                .findFragmentById(R.id.nav_host_fragment);
                if (navHost != null) navHost.getNavController().popBackStack();
            } catch (Exception e) {
                requireActivity().onBackPressed();
            }
        });

        // 周/月切换
        binding.toggleRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isMonth = (checkedId == R.id.btn_30day);
                loadData();
            }
        });

        initChartStyles();
        loadData();
    }

    // -----------------------------------------------------------------------
    private void initChartStyles() {
        styleBarChart(binding.chartDailySessions);
        styleLineChart(binding.chartDuration);
        stylePieChart(binding.chartCategory);
    }

    private void styleBarChart(BarChart c) {
        c.getDescription().setEnabled(false);
        c.setDrawGridBackground(false);
        c.getLegend().setEnabled(false);
        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setDoubleTapToZoomEnabled(false);
        c.getXAxis().setDrawGridLines(false);
        c.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        c.getXAxis().setTextColor(0xFF888888);
        c.getXAxis().setTextSize(9f);
        c.getAxisLeft().setDrawGridLines(true);
        c.getAxisLeft().setGridColor(0x20000000);
        c.getAxisLeft().setTextColor(0xFF888888);
        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setGranularity(1f);
        c.getAxisRight().setEnabled(false);
        c.setNoDataText("暂无训练记录");
        c.setNoDataTextColor(0xFF888888);
    }

    private void styleLineChart(LineChart c) {
        c.getDescription().setEnabled(false);
        c.setDrawGridBackground(false);
        c.getLegend().setEnabled(false);
        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setDoubleTapToZoomEnabled(false);
        c.getXAxis().setDrawGridLines(false);
        c.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        c.getXAxis().setTextColor(0xFF888888);
        c.getXAxis().setTextSize(9f);
        c.getAxisLeft().setDrawGridLines(true);
        c.getAxisLeft().setGridColor(0x20000000);
        c.getAxisLeft().setTextColor(0xFF888888);
        c.getAxisRight().setEnabled(false);
        c.setNoDataText("暂无数据");
        c.setNoDataTextColor(0xFF888888);
    }

    private void stylePieChart(PieChart c) {
        c.getDescription().setEnabled(false);
        c.setDrawHoleEnabled(true);
        c.setHoleColor(Color.TRANSPARENT);
        c.setHoleRadius(48f);
        c.setTransparentCircleRadius(52f);
        c.setDrawCenterText(true);
        c.setCenterText("部位\n分布");
        c.setCenterTextColor(0xFF555555);
        c.setCenterTextSize(12f);
        // 在切片上直接显示部位名（短字，清晰可读）
        c.setDrawEntryLabels(true);
        c.setEntryLabelColor(Color.WHITE);
        c.setEntryLabelTextSize(11f);
        // 不显示图例
        c.getLegend().setEnabled(false);
        c.setRotationEnabled(false);
        c.setNoDataText("暂无训练记录");
        c.setNoDataTextColor(0xFF888888);
    }

    // -----------------------------------------------------------------------
    private void loadData() {
        executor.execute(() -> {
            int days = isMonth ? 30 : 7;
            long endTime   = System.currentTimeMillis();
            long startTime = endTime - (long) days * 24 * 3600 * 1000L;

            List<DailyLog>     allLogs  = logRepo.getAllLogsSync();
            List<TrainingPlan> allPlans = planRepo.getAllPlansSync();

            Map<Integer, TrainingPlan> planMap = new HashMap<>();
            if (allPlans != null) {
                for (TrainingPlan p : allPlans) planMap.put(p.getPlanId(), p);
            }

            // 期间完成日志
            List<DailyLog> periodLogs = new ArrayList<>();
            if (allLogs != null) {
                for (DailyLog log : allLogs) {
                    if (log.getDate() >= startTime && log.getDate() < endTime && log.isCompleted())
                        periodLogs.add(log);
                }
            }

            // —— 大数字 ——
            Set<String> allDateSet = new HashSet<>();
            if (allLogs != null) {
                for (DailyLog log : allLogs) {
                    if (log.isCompleted()) allDateSet.add(DateUtils.formatDate(log.getDate()));
                }
            }
            final int totalDays   = allDateSet.size();
            final int periodCount = periodLogs.size();
            final int streak      = calcMaxStreak(allLogs);

            // —— 每日次数柱状图 ——
            SimpleDateFormat sdf = new SimpleDateFormat("M/d", Locale.getDefault());
            final String[]      xLabels     = new String[days];
            final List<BarEntry> barEntries  = new ArrayList<>();
            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < days; i++) {
                cal.setTimeInMillis(startTime + (long) i * 24 * 3600 * 1000L);
                long ds = dayStart(cal), de = ds + 86400000L;
                int cnt = 0;
                for (DailyLog log : periodLogs)
                    if (log.getDate() >= ds && log.getDate() < de) cnt++;
                xLabels[i] = sdf.format(cal.getTime());
                barEntries.add(new BarEntry(i, cnt));
            }

            // —— 每日时长折线图 ——
            final List<Entry> durationEntries = new ArrayList<>();
            int totalSec = 0, dDays = 0;
            for (int i = 0; i < days; i++) {
                cal.setTimeInMillis(startTime + (long) i * 24 * 3600 * 1000L);
                long ds = dayStart(cal), de = ds + 86400000L;
                int sec = 0;
                for (DailyLog log : periodLogs) {
                    if (log.getDate() >= ds && log.getDate() < de) {
                        TrainingPlan plan = planMap.get(log.getPlanId());
                        sec += ExerciseMetTable.resolveDuration(
                                log.getDuration(),
                                plan != null ? plan.getDuration() : 0,
                                plan != null ? plan.getSets() : 0,
                                plan != null ? plan.getReps() : 0,
                                requireContext());
                    }
                }
                durationEntries.add(new Entry(i, sec / 60f));
                if (sec > 0) { totalSec += sec; dDays++; }
            }
            final float avgMin = dDays > 0 ? totalSec / 60f / dDays : 0;

            // —— 体重折线图 ——
            List<WeightRecord> wRecords = weightRepo.getRecentRecordsSync(30);
            final List<Entry>  weightEntries = new ArrayList<>();
            final List<String> weightLabels  = new ArrayList<>();
            float minW = Float.MAX_VALUE, maxW = -Float.MAX_VALUE;
            if (wRecords != null) {
                Collections.reverse(wRecords);
                for (int i = 0; i < wRecords.size(); i++) {
                    WeightRecord wr = wRecords.get(i);
                    weightEntries.add(new Entry(i, wr.getWeight()));
                    weightLabels.add(sdf.format(wr.getTimestamp()));
                    if (wr.getWeight() < minW) minW = wr.getWeight();
                    if (wr.getWeight() > maxW) maxW = wr.getWeight();
                }
            }
            final float fMinW = weightEntries.isEmpty() ? 0 : minW;
            final float fMaxW = weightEntries.isEmpty() ? 0 : maxW;
            final String[] wLabels = weightLabels.toArray(new String[0]);

            // —— 训练分类饼图（仅按部位分组，去掉进阶/自定义等前缀）——
            Map<String, Integer> catMap = new HashMap<>();
            for (DailyLog log : periodLogs) {
                TrainingPlan plan = planMap.get(log.getPlanId());
                String rawCat = (plan != null && plan.getCategory() != null
                        && !plan.getCategory().isEmpty())
                        ? plan.getCategory() : "其他";
                // 提取部位关键词（取最后一个 '-' 后的内容并规范化）
                String bodyPart = extractBodyPart(rawCat);
                // 如果分类提取失败，从动作名称推断部位
                if ("其他".equals(bodyPart) && plan != null && plan.getName() != null) {
                    bodyPart = extractBodyPart(plan.getName());
                }
                catMap.put(bodyPart, catMap.getOrDefault(bodyPart, 0) + 1);
            }
            final List<PieEntry> pieEntries = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : catMap.entrySet())
                pieEntries.add(new PieEntry(entry.getValue(), entry.getKey()));

            // —— Top3 ——
            Map<Integer, Integer> cntMap = new HashMap<>();
            for (DailyLog log : periodLogs)
                cntMap.put(log.getPlanId(), cntMap.getOrDefault(log.getPlanId(), 0) + 1);
            List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(cntMap.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            final String t1n = sorted.size() > 0 ? planName(planMap, sorted.get(0).getKey()) : null;
            final int    t1c = sorted.size() > 0 ? sorted.get(0).getValue() : 0;
            final String t2n = sorted.size() > 1 ? planName(planMap, sorted.get(1).getKey()) : null;
            final int    t2c = sorted.size() > 1 ? sorted.get(1).getValue() : 0;
            final String t3n = sorted.size() > 2 ? planName(planMap, sorted.get(2).getKey()) : null;
            final int    t3c = sorted.size() > 2 ? sorted.get(2).getValue() : 0;

            // —— 更新 UI ——
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || binding == null) return;

                binding.tvTotalDays.setText(String.valueOf(totalDays));
                binding.tvPeriodCount.setText(String.valueOf(periodCount));
                binding.tvPeriodLabel.setText(isMonth ? "30天完成" : "7天完成");
                binding.tvStreak.setText(String.valueOf(streak));
                binding.tvTotalSessionsLabel.setText("共 " + periodCount + " 次");
                binding.tvAvgDurationLabel.setText(
                        String.format(Locale.getDefault(), "均 %.0f 分", avgMin));



                renderBarChart(binding.chartDailySessions, barEntries, xLabels);
                renderLineChart(binding.chartDuration, durationEntries, xLabels, COLOR_SUCCESS);
                if (!pieEntries.isEmpty()) renderPieChart(binding.chartCategory, pieEntries);

                // Top3
                binding.tvTopEmpty.setVisibility(t1n != null ? View.GONE : View.VISIBLE);
                if (t1n != null) {
                    binding.rowTop1.setVisibility(View.VISIBLE);
                    binding.tvTop1Name.setText(t1n);
                    binding.tvTop1Count.setText(t1c + " 次");
                }
                if (t2n != null) {
                    binding.rowTop2.setVisibility(View.VISIBLE);
                    binding.tvTop2Name.setText(t2n);
                    binding.tvTop2Count.setText(t2c + " 次");
                }
                if (t3n != null) {
                    binding.rowTop3.setVisibility(View.VISIBLE);
                    binding.tvTop3Name.setText(t3n);
                    binding.tvTop3Count.setText(t3c + " 次");
                }
            });
        });
    }

    // -----------------------------------------------------------------------
    private void renderBarChart(BarChart chart, List<BarEntry> entries, String[] labels) {
        BarDataSet ds = new BarDataSet(entries, "");
        ds.setColor(COLOR_PRIMARY);
        ds.setDrawValues(false);
        ds.setHighlightEnabled(true);
        ds.setHighLightColor(0x44000000);
        BarData data = new BarData(ds);
        data.setBarWidth(0.55f);
        chart.setData(data);
        int skip = labels.length > 10 ? 5 : 1;
        String[] dl = new String[labels.length];
        for (int i = 0; i < labels.length; i++) dl[i] = i % skip == 0 ? labels[i] : "";
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dl));
        chart.getXAxis().setLabelCount(labels.length, false);
        chart.animateY(600, Easing.EaseOutQuart);
        chart.invalidate();
    }

    private void renderLineChart(LineChart chart, List<Entry> entries, String[] labels, int color) {
        LineDataSet ds = new LineDataSet(entries, "");
        ds.setColor(color);
        ds.setCircleColor(color);
        ds.setCircleRadius(3f);
        ds.setLineWidth(2f);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setDrawFilled(true);
        ds.setFillAlpha(30);
        ds.setFillColor(color);
        ds.setHighlightEnabled(true);
        ds.setHighLightColor(0x44000000);
        chart.setData(new LineData(ds));
        int skip = labels.length > 10 ? 5 : 1;
        String[] dl = new String[labels.length];
        for (int i = 0; i < labels.length; i++) dl[i] = i % skip == 0 ? labels[i] : "";
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dl));
        chart.getXAxis().setLabelCount(labels.length, false);
        chart.animateX(700, Easing.EaseInOutQuart);
        chart.invalidate();
    }

    private void renderPieChart(PieChart chart, List<PieEntry> entries) {
        PieDataSet ds = new PieDataSet(entries, "");
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) colors.add(PIE_COLORS[i % PIE_COLORS.length]);
        ds.setColors(colors);
        ds.setSliceSpace(2f);
        ds.setDrawValues(false); // 不显示次数数字，切片上只显示部位名
        chart.setData(new PieData(ds));
        chart.animateY(800, Easing.EaseInOutQuart);
        chart.invalidate();
    }

    /**
     * 从 category 字段提取部位关键词
     * 规则：按 "-" 拆分，取最后一段，单字规范化为双字部位名
     * 示例："进阶-臀部" → "臀部"，"自定义-新手徒手计划-1-腹" → "腹部"
     */
    private String extractBodyPart(String category) {
        if (category == null || category.isEmpty()) return "其他";
        String[] parts = category.split("-");
        String last = parts[parts.length - 1].trim();
        // 单字部位规范化
        java.util.Map<String, String> norm = new java.util.HashMap<>();
        norm.put("胸", "胸部"); norm.put("背", "背部");
        norm.put("腿", "腿部"); norm.put("臀", "臀部");
        norm.put("腹", "腹部"); norm.put("肩", "肩部");
        norm.put("臂", "手臂"); norm.put("手", "手臂");
        norm.put("腰", "腰部"); norm.put("核", "核心");
        if (norm.containsKey(last)) return norm.get(last);
        // 如果最后一段是纯数字（如 "1"），取倒数第二段
        if (last.matches("\\d+") && parts.length >= 2) {
            String prev = parts[parts.length - 2].trim();
            if (norm.containsKey(prev)) return norm.get(prev);
            // 检查已知部位词
            for (String key : new String[]{"胸部","背部","腿部","臀部","腹部","肩部","手臂","有氧","全身","核心"}) {
                if (prev.contains(key)) return key;
            }
            return prev;
        }
        // 检查是否包含已知部位词
        for (String key : new String[]{"胸部","背部","腿部","臀部","腹部","肩部","手臂","有氧","全身","核心"}) {
            if (last.contains(key)) return key;
        }
        // 超长且不匹配已知部位 → 归类为"其他"（损坏数据修复后的兜底）
        if (last.length() > 3) {
            for (String key : new String[]{"胸","背","腿","臀","腹","肩","臂","腰"}) {
                if (last.contains(key)) return key + "部";
            }
            return "其他";
        }
        return last;
    }

    // -----------------------------------------------------------------------
    private int calcMaxStreak(List<DailyLog> logs) {
        if (logs == null || logs.isEmpty()) return 0;
        Set<String> ds = new HashSet<>();
        for (DailyLog log : logs) if (log.isCompleted()) ds.add(DateUtils.formatDate(log.getDate()));
        List<String> sorted = new ArrayList<>(ds);
        Collections.sort(sorted);
        int max = 1, cur = 1;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (int i = 1; i < sorted.size(); i++) {
            try {
                long prev = sdf.parse(sorted.get(i - 1)).getTime();
                long curr = sdf.parse(sorted.get(i)).getTime();
                if (curr - prev == 86400000L) { cur++; max = Math.max(max, cur); }
                else cur = 1;
            } catch (Exception ignored) {}
        }
        return sorted.isEmpty() ? 0 : max;
    }

    private long dayStart(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String planName(Map<Integer, TrainingPlan> map, int id) {
        TrainingPlan p = map.get(id);
        return (p != null && p.getName() != null) ? p.getName() : "未知训练";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) executor.shutdownNow();
        binding = null;
    }
}
