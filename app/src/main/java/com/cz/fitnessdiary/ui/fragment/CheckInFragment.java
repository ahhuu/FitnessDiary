package com.cz.fitnessdiary.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.databinding.FragmentCheckinBinding;
import com.cz.fitnessdiary.model.DailyMission;
import com.cz.fitnessdiary.ui.adapter.EditCardsAdapter;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.AchievementCenterViewModel;
import com.cz.fitnessdiary.viewmodel.CheckInViewModel;
import com.cz.fitnessdiary.viewmodel.DietViewModel;
import com.cz.fitnessdiary.viewmodel.HomeDashboardViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import android.widget.FrameLayout;

public class CheckInFragment extends Fragment {
    private static final String PREF_HOME_CARDS = "home_card_prefs";
    private static final String KEY_SHOW_WATER = "show_water";
    private static final String KEY_SHOW_SLEEP = "show_sleep";
    private static final String KEY_SHOW_HABIT = "show_habit";
    private static final String KEY_SHOW_MEDICATION = "show_medication";
    private static final String KEY_SHOW_WEIGHT = "show_weight";
    private static final String KEY_SHOW_CUSTOM = "show_custom";
    private static final String KEY_SMALL_ORDER = "small_order";

    private static final String CARD_WATER = "water";
    private static final String CARD_SLEEP = "sleep";
    private static final String CARD_HABIT = "habit";
    private static final String CARD_MEDICATION = "medication";
    private static final String CARD_WEIGHT = "weight";
    private static final String CARD_CUSTOM = "custom";

    private FragmentCheckinBinding binding;
    private CheckInViewModel checkInViewModel;
    private HomeDashboardViewModel homeDashboardViewModel;
    private DietViewModel dietViewModel;
    private AchievementCenterViewModel achievementCenterViewModel;
    private final List<TrainingPlan> currentPlans = new ArrayList<>();
    private final List<DailyLog> currentLogs = new ArrayList<>();
    private final List<String> smallCardOrder = new ArrayList<>();
    private final List<HabitItem> habitItems = new ArrayList<>();
    private final Map<Long, HabitRecord> habitRecords = new HashMap<>();
    private final List<DailyMission> latestMissions = new ArrayList<>();
    private final Map<String, View> cachedCards = new HashMap<>();
    private final Map<Long, View> customTrackerViews = new HashMap<>(); // [v1.3] 动态分类卡片缓存
    private long lastNavigateTs = 0L;
    private List<String> lastEnabledCardIds = new ArrayList<>(); // [v2.3] 缓存记录，防止频繁刷新
    private com.cz.fitnessdiary.ui.adapter.DateNavigatorAdapter dateNavAdapter;
    private boolean isHeaderExpanded = false;
    private int currentWaterTotal = 0;

    public CheckInFragment() {
        super();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentCheckinBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkInViewModel = new ViewModelProvider(this).get(CheckInViewModel.class);
        homeDashboardViewModel = new ViewModelProvider(this).get(HomeDashboardViewModel.class);
        dietViewModel = new ViewModelProvider(this).get(DietViewModel.class);
        achievementCenterViewModel = new ViewModelProvider(requireActivity()).get(AchievementCenterViewModel.class);
        setupActions();
        cacheCards();
        setupDateNavigation();
        observeData();
        loadCardConfig();
        applyCardConfig();
        updateDateHeader();
        achievementCenterViewModel.refreshAll();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (achievementCenterViewModel != null && binding != null) {
            // 避免与详情页返回动画竞争，延后刷新以减少退出瞬间卡顿感
            binding.getRoot().postDelayed(() -> {
                if (isAdded() && achievementCenterViewModel != null) {
                    achievementCenterViewModel.refreshAll();
                }
            }, 260L);
        }
    }

    private void setupDateNavigation() {
        // 下拉刷新监听：触发展开/收起头部
        binding.swipeRefresh.setOnRefreshListener(() -> {
            toggleDateHeader();
            binding.swipeRefresh.setRefreshing(false);
        });

        // 点击日期区域展开
        binding.dateTextArea.setOnClickListener(v -> toggleDateHeader());

        // 初始化日期列表 (过去30天)
        List<Long> dates = new ArrayList<>();
        long today = DateUtils.getTodayStartTimestamp();
        for (int i = 0; i < 30; i++) {
            dates.add(today - i * 24 * 60 * 60 * 1000L);
        }
        Collections.reverse(dates); // 时间轴从旧到新

        dateNavAdapter = new com.cz.fitnessdiary.ui.adapter.DateNavigatorAdapter(dates, today, ts -> {
            checkInViewModel.setSelectedDate(ts);
            toggleDateHeader();
        });
        binding.rvDateNav.setAdapter(dateNavAdapter);
        binding.rvDateNav.scrollToPosition(dates.size() - 1);

        // 回到今天按钮
        binding.btnBackToToday.setOnClickListener(v -> {
            checkInViewModel.setSelectedDate(DateUtils.getTodayStartTimestamp());
            toggleDateHeader();
        });

        // 观察日期变化
        checkInViewModel.getSelectedDate().observe(getViewLifecycleOwner(), ts -> {
            dateNavAdapter.setSelectedDate(ts);

            // 同步更新其他 ViewModel 的日期以触发数据重载
            dietViewModel.setSelectedDate(ts);
            homeDashboardViewModel.setSelectedDate(ts);
            achievementCenterViewModel.setMissionDate(ts);

            updateDateHeader();
            // 选中的不是今天则显示“回到今天”
            binding.btnBackToToday.setVisibility(DateUtils.isToday(ts) ? View.GONE : View.VISIBLE);
        });
    }

    private void toggleDateHeader() {
        isHeaderExpanded = !isHeaderExpanded;
        android.transition.TransitionManager.beginDelayedTransition(binding.headerContainer);
        binding.llDateSelector.setVisibility(isHeaderExpanded ? View.VISIBLE : View.GONE);
    }

    private void cacheCards() {
        View pool = binding.getRoot(); // 重新从根视图获取，无论它们在哪里
        cachedCards.put(CARD_WATER, pool.findViewById(R.id.card_water));
        cachedCards.put(CARD_SLEEP, pool.findViewById(R.id.card_sleep));
        cachedCards.put(CARD_HABIT, pool.findViewById(R.id.card_habit));
        cachedCards.put(CARD_MEDICATION, pool.findViewById(R.id.card_medication));
        cachedCards.put(CARD_WEIGHT, pool.findViewById(R.id.card_weight_small));
        // [v2.2] 饮食摘要观察
        // 这里的观察已移至底部逻辑
        dietViewModel.getTodayTotalCalories().observe(getViewLifecycleOwner(), calories -> {
            int consumed = calories != null ? calories : 0;
            binding.tvDietConsumed.setText(consumed + " 千卡");
        });

        dietViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            updateDietProgress();
        });

        dietViewModel.getSmartFeedback().observe(getViewLifecycleOwner(), feedback -> {
            if (feedback != null) {
                binding.tvDietFeedback.setText(feedback);
            }
        });

        dietViewModel.getProgressColor().observe(getViewLifecycleOwner(), colorRes -> {
            binding.progressDietToday.setIndicatorColor(getResources().getColor(R.color.diet_primary, null));
        });

        // 饮食数据变化时，同步更新大圆环
        dietViewModel.getTodayTotalCalories().observe(getViewLifecycleOwner(), calories -> {
            updateDietProgress();
            updateOverallProgress();
        });
    }

    private void updateDietProgress() {
        Integer consumed = dietViewModel.getTodayTotalCalories().getValue();
        User user = dietViewModel.getCurrentUser().getValue();
        if (consumed == null)
            consumed = 0;

        int target = 2000; // 默认
        if (user != null && user.getDailyCalorieTarget() > 0) {
            target = user.getDailyCalorieTarget();
        }

        int progress = (int) ((consumed * 100.0) / target);
        binding.progressDietToday.setProgress(Math.min(progress, 100));
    }

    private void updateDateHeader() {
        Long ts = checkInViewModel.getSelectedDate().getValue();
        if (ts == null)
            ts = DateUtils.getTodayStartTimestamp();
        binding.tvTodayDate.setText(DateUtils.formatFullDate(ts));

        if (DateUtils.isToday(ts)) {
            User user = checkInViewModel.getUser().getValue();
            String name = (user != null && user.getNickname() != null) ? user.getNickname() : "同学";
            binding.tvWelcome.setText("你好，" + name + "！");
            binding.tvTotalLabel.setText("今日总览");
        } else {
            binding.tvWelcome.setText("历史回顾");
            binding.tvTotalLabel.setText("当日总览");
        }
    }

    private void setupActions() {
        v(R.id.fl_total_circle).setOnClickListener(v -> showOverallProgressDetails());
        v(R.id.card_sport).setOnClickListener(v -> openDetail(R.id.sportRecordDetailFragment));
        v(R.id.card_diet).setOnClickListener(v -> {
            Fragment parent = getParentFragment();
            if (parent instanceof MainHomeFragment) {
                ((MainHomeFragment) parent).switchToTab(2); // 饮食页
            }
        });
        v(R.id.card_water).setOnClickListener(v -> openDetail(R.id.waterRecordDetailFragment));
        v(R.id.card_sleep).setOnClickListener(v -> openDetail(R.id.sleepRecordDetailFragment));
        v(R.id.card_habit).setOnClickListener(v -> openDetail(R.id.habitRecordDetailFragment));
        v(R.id.card_medication).setOnClickListener(v -> openDetail(R.id.medicationRecordDetailFragment));
        v(R.id.card_weight_small).setOnClickListener(v -> openDetail(R.id.weightRecordDetailFragment));

        binding.btnAddSport.setOnClickListener(v -> openDetail(R.id.sportRecordDetailFragment));
        v(R.id.btn_add_water).setOnClickListener(
                v -> quickNumberInput("记录喝水(ml)", value -> homeDashboardViewModel.addWater(value.intValue(), null)));
        v(R.id.btn_add_weight).setOnClickListener(
                v -> quickNumberInput("添加体重(kg)", value -> homeDashboardViewModel.addWeight(value.floatValue(), null)));
        v(R.id.btn_add_medication).setOnClickListener(
                v -> quickTextInput("添加用药", text -> homeDashboardViewModel.addMedication(text, "", true, null)));
        v(R.id.btn_add_sleep).setOnClickListener(v -> openDetail(R.id.sleepRecordDetailFragment));
        v(R.id.btn_add_habit).setOnClickListener(v -> openDetail(R.id.habitRecordDetailFragment));
        binding.btnEditMissions.setOnClickListener(v -> showEditMissionsDialog());

        binding.btnEditHomeCards.setOnClickListener(v -> showEditCardsDialog());
    }

    private interface NumberConsumer {
        void accept(Double v);
    }

    private interface TextConsumer {
        void accept(String v);
    }

    private void quickNumberInput(String title, NumberConsumer c) {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        c.accept(Double.parseDouble(et.getText().toString().trim()));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void quickTextInput(String title, TextConsumer c) {
        EditText et = new EditText(requireContext());
        new MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String s = et.getText() == null ? "" : et.getText().toString().trim();
                    if (s.isEmpty()) {
                        Toast.makeText(getContext(), "请输入内容", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    c.accept(s);
                }).setNegativeButton("取消", null).show();
    }

    private void observeData() {
        checkInViewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            updateDateHeader();
        });
        checkInViewModel.getSelectedDatePlans().observe(getViewLifecycleOwner(), plans -> {
            currentPlans.clear();
            if (plans != null)
                currentPlans.addAll(plans);
            refreshSportCard();
            updateOverallProgress();
        });
        checkInViewModel.getSelectedDateLogs().observe(getViewLifecycleOwner(), logs -> {
            currentLogs.clear();
            if (logs != null)
                currentLogs.addAll(logs);
            refreshSportCard();
            updateOverallProgress();
        });
        checkInViewModel.getConsecutiveDays().observe(getViewLifecycleOwner(),
                days -> binding.tvConsecutiveDays.setText("连续打卡 " + (days == null ? 0 : days) + " 天 🔥"));
        checkInViewModel.getThisWeekCheckedDates(checkedDays -> {
            int weekly = 0;
            for (boolean checked : checkedDays)
                if (checked)
                    weekly++;
            int finalWeekly = weekly;
            requireActivity().runOnUiThread(() -> binding.tvSportWeekly.setText("本周已达成 " + finalWeekly + " 天"));
        });

        homeDashboardViewModel.getSelectedDateLatestWeight().observe(getViewLifecycleOwner(), r -> {
            setTextIfExists(R.id.tv_weight_value,
                    r == null ? "--" : String.format(java.util.Locale.getDefault(), "%.1f", r.getWeight()));
            setTextIfExists(R.id.tv_weight_update,
                    r == null ? "暂无更新" : getSelectedDateUpdateText(r.getTimestamp()));
            setTextIfExists(R.id.tv_weight_summary, r == null ? "点击查看体重明细" : "已记录当日体重");
        });
        homeDashboardViewModel.getTodayWaterTotal().observe(getViewLifecycleOwner(), total -> {
            currentWaterTotal = total == null ? 0 : total;
            setTextIfExists(R.id.tv_water_value, String.valueOf(currentWaterTotal));
            updateOverallProgress();
        });
        homeDashboardViewModel.getSelectedDateLatestWater().observe(getViewLifecycleOwner(),
                r -> setTextIfExists(R.id.tv_water_update,
                        r == null ? "暂无更新" : getSelectedDateUpdateText(r.getTimestamp())));
        homeDashboardViewModel.getTodayMedicationTakenCount().observe(getViewLifecycleOwner(),
                c -> setTextIfExists(R.id.tv_medication_value, String.valueOf(c == null ? 0 : c)));
        homeDashboardViewModel.getSelectedDateLatestMedication().observe(getViewLifecycleOwner(),
                r -> setTextIfExists(R.id.tv_medication_update,
                        r == null ? "暂无更新" : getSelectedDateUpdateText(r.getTimestamp())));

        achievementCenterViewModel.getTodayMissions().observe(getViewLifecycleOwner(), this::renderDailyMissions);

        checkInViewModel.getSelectedDaySleepRecords().observe(getViewLifecycleOwner(), records -> {
            float totalHours = 0;
            float deepSleepHours = 0;
            if (records != null && !records.isEmpty()) {
                for (com.cz.fitnessdiary.database.entity.SleepRecord r : records) {
                    if (r.getEndTime() > r.getStartTime()) {
                        float duration = (r.getEndTime() - r.getStartTime()) / (1000f * 60 * 60);
                        totalHours += duration;
                        // 估算深度睡眠：基于总时长和睡眠质量 (15%-35%)
                        float ratio = 0.15f + (Math.max(1, Math.min(5, r.getQuality())) / 5.0f) * 0.20f;
                        deepSleepHours += (duration * ratio);
                    }
                }
                com.cz.fitnessdiary.database.entity.SleepRecord latest = records.get(records.size() - 1);
                setTextIfExists(R.id.tv_sleep_update, getSelectedDateUpdateText(latest.getEndTime()));
            } else {
                setTextIfExists(R.id.tv_sleep_update, "暂无更新");
            }

            setTextIfExists(R.id.tv_sleep_value, String.format(java.util.Locale.getDefault(), "%.1f", totalHours));
            setTextIfExists(R.id.tv_sleep_summary,
                    String.format(java.util.Locale.getDefault(), "深度睡眠 %.1fh", deepSleepHours));

            com.google.android.material.progressindicator.LinearProgressIndicator pSleep = binding.getRoot()
                    .findViewById(R.id.progress_sleep);
            if (pSleep != null) {
                int progress = (int) (Math.min(totalHours / 8.0f, 1.0f) * 100);
                pSleep.setProgress(progress);
            }
            updateOverallProgress();
        });

        homeDashboardViewModel.getEnabledTrackers().observe(getViewLifecycleOwner(), trackers -> {
            // [v1.3] 动态管理分类卡片
            if (trackers != null) {
                // 清理已禁用的分类视图
                java.util.Set<Long> enabledIds = new java.util.HashSet<>();
                for (com.cz.fitnessdiary.database.entity.CustomTracker t : trackers)
                    enabledIds.add(t.getId());
                customTrackerViews.entrySet().removeIf(entry -> !enabledIds.contains(entry.getKey()));

                // 更新或创建视图
                for (com.cz.fitnessdiary.database.entity.CustomTracker tracker : trackers) {
                    View card = customTrackerViews.get(tracker.getId());
                    if (card == null) {
                        card = createDynamicCustomCard(tracker);
                        customTrackerViews.put(tracker.getId(), card);
                        updateDynamicCardObserver(tracker, card); // 仅在创建时绑定观察者
                    } else {
                        // 如果卡片已存在，可能需要更新标题/颜色等视觉属性
                        updateCardVisuals(card, tracker);
                    }
                }
                applyCardConfig();
            }
        });
        homeDashboardViewModel.getEnabledHabits().observe(getViewLifecycleOwner(), items -> {
            habitItems.clear();
            if (items != null)
                habitItems.addAll(items);
            refreshHabitCard();
            updateOverallProgress();
        });
        homeDashboardViewModel.getSelectedDateHabitRecords().observe(getViewLifecycleOwner(), records -> {
            habitRecords.clear();
            if (records != null) {
                for (HabitRecord r : records) {
                    habitRecords.put(r.getHabitId(), r);
                }
            }
            refreshHabitCard();
            updateOverallProgress();
        });
    }

    private void renderDailyMissions(List<DailyMission> missions) {
        if (missions == null || binding == null) {
            return;
        }
        latestMissions.clear();
        latestMissions.addAll(missions);

        binding.layoutMissionList.removeAllViews();
        for (DailyMission mission : missions) {
            TextView tv = new TextView(requireContext());
            tv.setTextSize(14);
            tv.setPadding(0, dp(4), 0, dp(4));
            tv.setText((mission.isCompleted() ? "✅ " : "○ ") + mission.getTitle());
            tv.setTextColor(getResources().getColor(
                    mission.isCompleted() ? R.color.fitnessdiary_primary : R.color.text_secondary,
                    null));
            if (mission.isCustom()) {
                tv.setOnClickListener(v -> achievementCenterViewModel.toggleCustomMissionCompletion(mission.getId()));
            }
            binding.layoutMissionList.addView(tv);
        }

        if (missions.isEmpty()) {
            TextView emptyTv = new TextView(requireContext());
            emptyTv.setText("暂无任务，点击右侧编辑添加");
            emptyTv.setTextSize(13);
            emptyTv.setTextColor(getResources().getColor(R.color.text_hint, null));
            emptyTv.setPadding(0, dp(4), 0, dp(4));
            binding.layoutMissionList.addView(emptyTv);
        }
    }

    private void showEditMissionsDialog() {
        EditText et = new EditText(requireContext());
        et.setMinLines(6);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setHint("每行一个任务，可用前缀：\n[训练] [饮食] [习惯] [自定义]");

        List<String> lines = new ArrayList<>();
        for (DailyMission mission : latestMissions) {
            if ("mission_training".equals(mission.getId())) {
                lines.add("[训练] " + mission.getTitle());
            } else if ("mission_diet".equals(mission.getId())) {
                lines.add("[饮食] " + mission.getTitle());
            } else if ("mission_habit".equals(mission.getId())) {
                lines.add("[习惯] " + mission.getTitle());
            } else {
                lines.add("[自定义] " + mission.getTitle());
            }
        }
        et.setText(String.join("\n", lines));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑任务")
                .setView(et)
                .setMessage("可修改默认任务文案，删除对应行可移除该任务；新增行可添加自定义任务。")
                .setPositiveButton("保存", (dialog, which) -> {
                    String text = et.getText() == null ? "" : et.getText().toString();
                    String[] rawLines = text.split("\\r?\\n");
                    List<String> editorLines = new ArrayList<>();
                    for (String line : rawLines) {
                        String t = line == null ? "" : line.trim();
                        if (!t.isEmpty()) {
                            editorLines.add(t);
                        }
                    }
                    achievementCenterViewModel.replaceMissionDefinitionsFromLines(editorLines);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateOverallProgress() {
        float sportProgress = 1f;
        if (!currentPlans.isEmpty()) {
            int done = 0;
            HashSet<Integer> donePlanIds = new HashSet<>();
            for (DailyLog log : currentLogs)
                if (log.isCompleted())
                    donePlanIds.add(log.getPlanId());
            for (TrainingPlan plan : currentPlans)
                if (donePlanIds.contains(plan.getPlanId()))
                    done++;
            sportProgress = (float) done / currentPlans.size();
        }
        binding.progressSportToday.setProgress((int) (sportProgress * 100));

        float waterProgress = Math.min(1.0f, (float) currentWaterTotal / 1600f);
        LinearProgressIndicator pWater = binding.getRoot().findViewById(R.id.progress_water);
        if (pWater != null) {
            pWater.setProgress((int) (waterProgress * 100));
        }

        float habitProgress = 0f;
        if (!habitItems.isEmpty()) {
            int completed = 0;
            for (HabitItem item : habitItems) {
                HabitRecord r = habitRecords.get(item.getId());
                if (r != null && r.isCompleted())
                    completed++;
            }
            habitProgress = (float) completed / habitItems.size();
            LinearProgressIndicator pHabit = binding.getRoot().findViewById(R.id.progress_habit);
            if (pHabit != null)
                pHabit.setProgress((int) (habitProgress * 100));
        }

        float dietProgress = 0f;
        Integer consumed = dietViewModel.getTodayTotalCalories().getValue();
        User user = dietViewModel.getCurrentUser().getValue();
        int target = (user != null && user.getDailyCalorieTarget() > 0) ? user.getDailyCalorieTarget() : 2000;
        if (consumed != null) {
            dietProgress = Math.min(1.0f, (float) consumed / target);
        }

        int totalScore = Math.round(
                (sportProgress * 0.3f + waterProgress * 0.2f + habitProgress * 0.2f + dietProgress * 0.3f) * 100);
        binding.progressTotalCircle.setProgress(totalScore);
        binding.tvProgressPercent.setText(totalScore + "%");
    }

    private void showOverallProgressDetails() {
        float sportProgress = 1f;
        if (!currentPlans.isEmpty()) {
            int done = 0;
            HashSet<Integer> donePlanIds = new HashSet<>();
            for (DailyLog log : currentLogs) {
                if (log.isCompleted()) {
                    donePlanIds.add(log.getPlanId());
                }
            }
            for (TrainingPlan plan : currentPlans) {
                if (donePlanIds.contains(plan.getPlanId())) {
                    done++;
                }
            }
            sportProgress = (float) done / currentPlans.size();
        }

        float waterProgress = Math.min(1.0f, (float) currentWaterTotal / 1600f);

        float habitProgress = 0f;
        if (!habitItems.isEmpty()) {
            int completed = 0;
            for (HabitItem item : habitItems) {
                HabitRecord r = habitRecords.get(item.getId());
                if (r != null && r.isCompleted()) {
                    completed++;
                }
            }
            habitProgress = (float) completed / habitItems.size();
        }

        float dietProgress = 0f;
        Integer consumed = dietViewModel.getTodayTotalCalories().getValue();
        User user = dietViewModel.getCurrentUser().getValue();
        int target = (user != null && user.getDailyCalorieTarget() > 0) ? user.getDailyCalorieTarget() : 2000;
        if (consumed != null) {
            dietProgress = Math.min(1.0f, (float) consumed / target);
        }

        int scoreSport = Math.round(sportProgress * 100);
        int scoreWater = Math.round(waterProgress * 100);
        int scoreHabit = Math.round(habitProgress * 100);
        int scoreDiet = Math.round(dietProgress * 100);

        String msg = String.format(java.util.Locale.getDefault(),
                "• 运动进度 (权重30%%)：%d%%\n\n" +
                        "• 饮食热量 (权重30%%)：%d%%\n\n" +
                        "• 喝水目标 (权重20%%)：%d%%\n\n" +
                        "• 习惯达成 (权重20%%)：%d%%\n\n" +
                        "进度说明：基于以上四项得分及其权重比例综合计算，呈现今天的整体任务完成度。",
                scoreSport, scoreDiet, scoreWater, scoreHabit);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("今日综合进度分解")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void refreshSportCard() {
        HashSet<Integer> donePlanIds = new HashSet<>();
        for (DailyLog log : currentLogs)
            if (log.isCompleted())
                donePlanIds.add(log.getPlanId());
        if (currentPlans.isEmpty()) {
            binding.tvSportUpdate.setText("今日无训练计划，好好休息吧！");
            binding.progressSportToday.setProgress(100);
            return;
        }
        int done = 0;
        for (TrainingPlan plan : currentPlans)
            if (donePlanIds.contains(plan.getPlanId()))
                done++;
        // 已完成数已在 tv_sport_update 中体现
        binding.tvSportUpdate.setText("已完成今日 " + done + " 个运动计划");
    }

    private void refreshHabitCard() {
        int total = habitItems.size();
        int completed = 0;
        for (HabitItem item : habitItems) {
            HabitRecord r = habitRecords.get(item.getId());
            if (r != null && r.isCompleted())
                completed++;
        }
        setTextIfExists(R.id.tv_habit_value, completed + "/" + total);
        setTextIfExists(R.id.tv_habit_summary, completed == total ? "今日习惯已全部达成！✨" : "还有一些习惯没打卡呢");
        setTextIfExists(R.id.tv_habit_update, "养成好习惯");
    }

    private void openDetail(int destination) {
        openDetail(destination, null);
    }

    private void openDetail(int destination, Bundle extraArgs) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNavigateTs < 280) {
            return;
        }
        lastNavigateTs = now;

        Bundle args = new Bundle();
        Long ts = checkInViewModel.getSelectedDate().getValue();
        args.putLong("selectedDate", ts == null ? System.currentTimeMillis() : ts);
        if (extraArgs != null) {
            args.putAll(extraArgs);
        }

        NavOptions navOptions = new NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_right)
                .setExitAnim(R.anim.fade_out_fast)
                .setPopEnterAnim(R.anim.fade_in_fast)
                .setPopExitAnim(R.anim.slide_out_right)
                .build();
        NavHostFragment.findNavController(this).navigate(destination, args, navOptions);
    }

    private void showEditCardsDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_cards, null);
        RecyclerView rv = view.findViewById(R.id.rv_edit_cards);

        List<EditCardsAdapter.CardConfig> configs = new ArrayList<>();
        for (String id : smallCardOrder) {
            String name = "";
            boolean visible = true;
            if (CARD_WATER.equals(id)) {
                name = "喝水记录";
                visible = isCardEnabled(KEY_SHOW_WATER, true);
            } else if (CARD_SLEEP.equals(id)) {
                name = "睡眠分析";
                visible = isCardEnabled(KEY_SHOW_SLEEP, true);
            } else if (CARD_HABIT.equals(id)) {
                name = "习惯养成";
                visible = isCardEnabled(KEY_SHOW_HABIT, true);
            } else if (CARD_MEDICATION.equals(id)) {
                name = "用药提醒";
                visible = isCardEnabled(KEY_SHOW_MEDICATION, true);
            } else if (CARD_WEIGHT.equals(id)) {
                name = "体重变化";
                visible = isCardEnabled(KEY_SHOW_WEIGHT, true);
            } else if (CARD_CUSTOM.equals(id)) {
                name = "自定义分类";
                visible = isCardEnabled(KEY_SHOW_CUSTOM, true);
            }
            configs.add(new EditCardsAdapter.CardConfig(id, name, visible));
        }

        EditCardsAdapter adapter = new EditCardsAdapter(configs);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        androidx.recyclerview.widget.ItemTouchHelper.Callback callback = new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                adapter.moveItem(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        };
        new androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(rv);

        new MaterialAlertDialogBuilder(requireContext()).setTitle("编辑首页卡片").setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    SharedPreferences sp = requireContext().getSharedPreferences(PREF_HOME_CARDS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sp.edit();
                    List<String> newOrder = new ArrayList<>();
                    for (EditCardsAdapter.CardConfig cfg : adapter.getConfigs()) {
                        newOrder.add(cfg.id);
                        if (CARD_WATER.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_WATER, cfg.visible);
                        else if (CARD_SLEEP.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_SLEEP, cfg.visible);
                        else if (CARD_HABIT.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_HABIT, cfg.visible);
                        else if (CARD_MEDICATION.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_MEDICATION, cfg.visible);
                        else if (CARD_WEIGHT.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_WEIGHT, cfg.visible);
                        else if (CARD_CUSTOM.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_CUSTOM, cfg.visible);
                    }
                    editor.putString(KEY_SMALL_ORDER, String.join(",", newOrder)).apply();
                    loadCardConfig();
                    applyCardConfig();
                }).setNegativeButton("取消", null).show();
    }

    private void loadCardConfig() {
        SharedPreferences sp = requireContext().getSharedPreferences(PREF_HOME_CARDS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_SMALL_ORDER,
                CARD_WATER + "," + CARD_SLEEP + "," + CARD_HABIT + "," + CARD_MEDICATION + "," + CARD_WEIGHT + ","
                        + CARD_CUSTOM);
        smallCardOrder.clear();
        if (raw != null) {
            for (String s : raw.split(",")) {
                String id = s.trim();
                if (!smallCardOrder.contains(id))
                    smallCardOrder.add(id);
            }
        }
        if (!smallCardOrder.contains(CARD_WATER))
            smallCardOrder.add(CARD_WATER);
        if (!smallCardOrder.contains(CARD_SLEEP))
            smallCardOrder.add(CARD_SLEEP);
        if (!smallCardOrder.contains(CARD_HABIT))
            smallCardOrder.add(CARD_HABIT);
        if (!smallCardOrder.contains(CARD_MEDICATION))
            smallCardOrder.add(CARD_MEDICATION);
        if (!smallCardOrder.contains(CARD_WEIGHT))
            smallCardOrder.add(CARD_WEIGHT);
        if (!smallCardOrder.contains(CARD_CUSTOM))
            smallCardOrder.add(CARD_CUSTOM);
    }

    private void applyCardConfig() {
        androidx.gridlayout.widget.GridLayout layout = binding.gridCards;
        if (layout == null)
            return;

        List<String> enabled = new ArrayList<>();
        for (String id : smallCardOrder) {
            if (CARD_WATER.equals(id) && isCardEnabled(KEY_SHOW_WATER, true))
                enabled.add(id);
            if (CARD_SLEEP.equals(id) && isCardEnabled(KEY_SHOW_SLEEP, true))
                enabled.add(id);
            if (CARD_HABIT.equals(id) && isCardEnabled(KEY_SHOW_HABIT, true))
                enabled.add(id);
            if (CARD_MEDICATION.equals(id) && isCardEnabled(KEY_SHOW_MEDICATION, true))
                enabled.add(id);
            if (CARD_WEIGHT.equals(id) && isCardEnabled(KEY_SHOW_WEIGHT, true))
                enabled.add(id);

            // [v1.3] 处理所有动态自定义卡片
            if (CARD_CUSTOM.equals(id) && isCardEnabled(KEY_SHOW_CUSTOM, true)) {
                List<Long> ids = new ArrayList<>(customTrackerViews.keySet());
                Collections.sort(ids);
                for (Long tid : ids) {
                    enabled.add("custom_" + tid);
                }
            }
        }

        // [v2.3] 核心优化：对比配置，若无变化则跳过刷新
        if (enabled.equals(lastEnabledCardIds)) {
            return;
        }
        lastEnabledCardIds = new ArrayList<>(enabled);

        // ── 彻底重建策略 ──────────────────────────────────────────
        // 第1步：把所有卡片从旧的 FrameLayout parent 里解绑，不然 addView 会抛异常
        List<View> targetCards = new ArrayList<>();
        for (String id : enabled) {
            View card = getCardById(id);
            if (card == null)
                continue;
            ViewGroup oldParent = (ViewGroup) card.getParent();
            if (oldParent != null) {
                oldParent.removeView(card);
            }
            targetCards.add(card);
        }

        // 第2步：清空 GridLayout 所有子视图（旧 FrameLayout wrapper 一并丢弃）
        layout.removeAllViews();

        // 第3步：用全新 FrameLayout + 全新 LayoutParams 逐一重新添加
        int marginGap = dp(8); // 两列之间的总间隙拆半
        int marginBottom = dp(16);

        for (int i = 0; i < targetCards.size(); i++) {
            View card = targetCards.get(i);

            // 全新 wrapper，保证没有任何旧参数
            FrameLayout wrapper = new FrameLayout(requireContext());
            wrapper.addView(card);

            // 全新 GridLayout.LayoutParams
            androidx.gridlayout.widget.GridLayout.LayoutParams lp = new androidx.gridlayout.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = androidx.gridlayout.widget.GridLayout.spec(
                    androidx.gridlayout.widget.GridLayout.UNDEFINED, 1f);

            // 左列：右边留间距；右列：左边留间距
            if (i % 2 == 0) {
                lp.setMargins(0, 0, marginGap, marginBottom);
            } else {
                lp.setMargins(marginGap, 0, 0, marginBottom);
            }

            wrapper.setLayoutParams(lp);
            layout.addView(wrapper);
        }
        // ─────────────────────────────────────────────────────────

    }

    private View getCardById(String id) {
        if (id.startsWith("custom_")) {
            try {
                long tid = Long.parseLong(id.substring(7));
                return customTrackerViews.get(tid);
            } catch (Exception e) {
                return null;
            }
        }
        return cachedCards.get(id);
    }

    // [v1.3] 动态创建自定义卡片
    private View createDynamicCustomCard(com.cz.fitnessdiary.database.entity.CustomTracker tracker) {
        View card = getLayoutInflater().inflate(R.layout.view_home_card_custom, null);
        updateCardVisuals(card, tracker);

        card.setOnClickListener(v -> {
            Bundle b = new Bundle();
            b.putLong("targetId", tracker.getId());
            b.putString("title", tracker.getName());
            openDetail(R.id.customCategoryDetailFragment, b);
        });

        card.findViewById(R.id.btn_add_custom).setOnClickListener(v -> {
            Bundle b = new Bundle();
            b.putLong("targetId", tracker.getId());
            b.putString("title", tracker.getName());
            openDetail(R.id.customCategoryDetailFragment, b); // 或者打开快速录入对话框
        });

        return card;
    }

    private void updateCardVisuals(View card, com.cz.fitnessdiary.database.entity.CustomTracker tracker) {
        int color = android.graphics.Color
                .parseColor(tracker.getColorHex() != null ? tracker.getColorHex() : "#4CAF50");
        TextView title = card.findViewById(R.id.tv_custom_title);
        title.setText(tracker.getName());
        ((android.widget.ImageView) card.findViewById(R.id.iv_custom_icon)).setColorFilter(color);
        com.google.android.material.card.MaterialCardView cardView = card.findViewById(R.id.card_custom);
        if (cardView != null) {
            cardView.setCardBackgroundColor(android.graphics.Color.argb(26, android.graphics.Color.red(color),
                    android.graphics.Color.green(color), android.graphics.Color.blue(color)));
            cardView.setStrokeColor(android.graphics.Color.argb(40, android.graphics.Color.red(color),
                    android.graphics.Color.green(color), android.graphics.Color.blue(color)));
        }
        TextView unitTv = card.findViewById(R.id.tv_custom_unit);
        if (unitTv != null)
            unitTv.setText(tracker.getUnit());
    }

    // [v1.3] 为动态卡片设置独立观察者
    private void updateDynamicCardObserver(com.cz.fitnessdiary.database.entity.CustomTracker tracker, View card) {
        homeDashboardViewModel.getTodayCustomSum(tracker.getId()).observe(getViewLifecycleOwner(), sum -> {
            double s = sum == null ? 0.0 : sum;
            String valStr = s == (long) s ? String.valueOf((long) s) : String.format("%.1f", s);
            ((TextView) card.findViewById(R.id.tv_custom_value)).setText(valStr);
            TextView unitTv = card.findViewById(R.id.tv_custom_unit);
            if (unitTv != null)
                unitTv.setText(tracker.getUnit());
            updateOverallProgress();
        });

        homeDashboardViewModel.getSelectedDateLatestCustomRecord(tracker.getId()).observe(getViewLifecycleOwner(), record -> {
            ((TextView) card.findViewById(R.id.tv_custom_update))
                    .setText(record == null ? "暂无更新" : getSelectedDateUpdateText(record.getTimestamp()));
            ((TextView) card.findViewById(R.id.tv_custom_summary)).setText(record == null ? "记录新数据" : "已记录新数据");
        });
    }

    private boolean isCardEnabled(String key, boolean def) {
        return requireContext().getSharedPreferences(PREF_HOME_CARDS, Context.MODE_PRIVATE).getBoolean(key, def);
    }

    private CheckBox buildToggle(LinearLayout root, String text, boolean checked) {
        CheckBox cb = new CheckBox(requireContext());
        cb.setText(text);
        cb.setChecked(checked);
        root.addView(cb);
        return cb;
    }

    private String getUpdateText(long ts) {
        long d = (System.currentTimeMillis() - ts) / (24L * 60L * 60L * 1000L);
        return d <= 0 ? "今日更新" : d + " 天前更新";
    }

    private String getSelectedDateUpdateText(long recordTs) {
        Long selected = checkInViewModel.getSelectedDate().getValue();
        long selectedDay = selected == null ? DateUtils.getTodayStartTimestamp() : DateUtils.getDayStartTimestamp(selected);
        long recordDay = DateUtils.getDayStartTimestamp(recordTs);
        if (selectedDay == recordDay) {
            return DateUtils.isToday(selectedDay) ? "今日更新" : "当日更新";
        }
        return getUpdateText(recordTs);
    }

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T v(int id) {
        return (T) binding.getRoot().findViewById(id);
    }

    private void setTextIfExists(int id, CharSequence text) {
        TextView t = binding.getRoot().findViewById(id);
        if (t != null) {
            t.setText(text);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        lastEnabledCardIds.clear();
        cachedCards.clear();
        customTrackerViews.clear();
        binding = null;
    }
}
