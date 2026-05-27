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
import com.cz.fitnessdiary.utils.ErrorHandler;
import com.cz.fitnessdiary.utils.RestTimerManager;
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
    private static final String KEY_SHOW_MEASUREMENT = "show_measurement";
    private static final String KEY_SHOW_BOWEL = "show_bowel";
    private static final String KEY_SHOW_MENSTRUAL = "show_menstrual";
    private static final String KEY_SMALL_ORDER = "small_order";

    private static final String CARD_WATER = "water";
    private static final String CARD_SLEEP = "sleep";
    private static final String CARD_HABIT = "habit";
    private static final String CARD_MEDICATION = "medication";
    private static final String CARD_WEIGHT = "weight";
    private static final String CARD_MEASUREMENT = "measurement";
    private static final String CARD_BOWEL = "bowel";
    private static final String CARD_MENSTRUAL = "menstrual";

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
    private long lastNavigateTs = 0L;
    private List<String> lastEnabledCardIds = new ArrayList<>(); // [v2.3] 缓存记录，防止频繁刷新
    private com.cz.fitnessdiary.ui.adapter.DateNavigatorAdapter dateNavAdapter;
    private boolean isHeaderExpanded = false;
    private int currentWaterTotal = 0;
    private RestTimerManager restTimerManager;
    private int timerSelectedSeconds = 60;

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
        restTimerManager = new RestTimerManager();
        setupActions();
        setupRestTimer();
        cacheCards();
        setupDateNavigation();
        observeData();
        loadCardConfig();
        applyCardConfig();
        updateDateHeader();
        updateSummaryCard();
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
        cachedCards.put(CARD_MEASUREMENT, pool.findViewById(R.id.card_measure_small));
        cachedCards.put(CARD_BOWEL, pool.findViewById(R.id.card_bowel_small));
        cachedCards.put(CARD_MENSTRUAL, pool.findViewById(R.id.card_menstrual_small));
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
        v(R.id.card_measure_small).setOnClickListener(v -> openDetail(R.id.bodyMeasurementDetailFragment));
        v(R.id.card_bowel_small).setOnClickListener(v -> openDetail(R.id.bowelMovementDetailFragment));
        v(R.id.card_menstrual_small).setOnClickListener(v -> openDetail(R.id.menstrualRecordDetailFragment));

        binding.btnAddSport.setOnClickListener(v -> openDetail(R.id.sportRecordDetailFragment));

        // 组间休息计时器切换
        View timerToggle = v(R.id.btn_rest_timer_toggle);
        if (timerToggle != null) {
            timerToggle.setOnClickListener(v -> {
                boolean visible = binding.cardRestTimer.getVisibility() == View.VISIBLE;
                binding.cardRestTimer.setVisibility(visible ? View.GONE : View.VISIBLE);
                if (!visible) {
                    binding.nestedScroll.smoothScrollTo(0, binding.cardRestTimer.getTop());
                }
            });
        }

        v(R.id.btn_add_water).setOnClickListener(
                v -> quickNumberInput("记录喝水(ml)", value -> homeDashboardViewModel.addWater(value.intValue(), null)));
        v(R.id.btn_add_weight).setOnClickListener(
                v -> quickNumberInput("添加体重(kg)", value -> homeDashboardViewModel.addWeight(value.floatValue(), null)));
        v(R.id.btn_add_medication).setOnClickListener(
                v -> quickTextInput("添加用药", text -> homeDashboardViewModel.addMedication(text, "", true, null)));
        v(R.id.btn_add_sleep).setOnClickListener(v -> openDetail(R.id.sleepRecordDetailFragment));
        v(R.id.btn_add_habit).setOnClickListener(v -> openDetail(R.id.habitRecordDetailFragment));
        v(R.id.btn_add_measure).setOnClickListener(v -> openDetail(R.id.bodyMeasurementDetailFragment));
        v(R.id.btn_add_bowel).setOnClickListener(v -> openDetail(R.id.bowelMovementDetailFragment));
        v(R.id.btn_add_menstrual).setOnClickListener(v -> openDetail(R.id.menstrualRecordDetailFragment));
        binding.btnEditMissions.setOnClickListener(v -> showEditMissionsDialog());

        binding.btnEditHomeCards.setOnClickListener(v -> showEditCardsDialog());
        binding.btnHeatmap.setOnClickListener(v -> showHeatmapDialog());
        binding.btnChallenge.setOnClickListener(v -> showChallengeDialog());
        refreshChallengeCard();
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
            if (r != null) {
                setTextIfExists(R.id.tv_weight_value,
                        String.format(java.util.Locale.getDefault(), "%.1f", r.getWeight()));
                setTextIfExists(R.id.tv_weight_update, getSelectedDateUpdateText(r.getTimestamp()));
                setTextIfExists(R.id.tv_weight_summary, "已记录当日体重");
                // Compute detailed weight analysis (delta + BMI + 7-day trend)
                new Thread(() -> {
                    com.cz.fitnessdiary.database.AppDatabase db =
                            com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
                    StringBuilder analysis = new StringBuilder();
                    // Weight delta vs previous record
                    com.cz.fitnessdiary.database.entity.WeightRecord prevRecord =
                            db.weightRecordDao().getLatestRecordBeforeSync(r.getTimestamp());
                    if (prevRecord != null) {
                        float delta = r.getWeight() - prevRecord.getWeight();
                        if (Math.abs(delta) < 0.05f) {
                            analysis.append("稳定");
                        } else {
                            String arrow = delta < 0 ? "↓" : "↑";
                            analysis.append(arrow).append(String.format(java.util.Locale.getDefault(), "%.1fkg", Math.abs(delta)));
                        }
                    }
                    // BMI
                    com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();
                    if (user != null && user.getHeight() > 0) {
                        float h = user.getHeight() / 100f;
                        float bmi = r.getWeight() / (h * h);
                        if (analysis.length() > 0) analysis.append(" · ");
                        analysis.append("BMI").append(String.format(java.util.Locale.getDefault(), "%.1f", bmi));
                    }
                    // 7-day trend
                    long today = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
                    long weekAgo = today - 7 * 86400000L;
                    java.util.List<com.cz.fitnessdiary.database.entity.WeightRecord> weekRecords =
                            db.weightRecordDao().getRecordsByDateRangeSync(weekAgo, today + 86400000L);
                    if (weekRecords != null && weekRecords.size() >= 2) {
                        float firstOfWeek = weekRecords.get(weekRecords.size() - 1).getWeight();
                        float trend = r.getWeight() - firstOfWeek;
                        if (analysis.length() > 0) analysis.append(" · ");
                        if (Math.abs(trend) < 0.1f) {
                            analysis.append("周平稳");
                        } else {
                            analysis.append("周").append(trend < 0 ? "↓" : "↑")
                                    .append(String.format(java.util.Locale.getDefault(), "%.1fkg", Math.abs(trend)));
                        }
                    }
                    final String text = analysis.length() > 0 ? analysis.toString() : "已记录当日体重";
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            setTextIfExists(R.id.tv_weight_summary, text));
                }).start();
            } else {
                // Fallback to latest overall weight
                com.cz.fitnessdiary.database.entity.WeightRecord latest =
                        homeDashboardViewModel.getLatestWeight().getValue();
                if (latest != null) {
                    setTextIfExists(R.id.tv_weight_value,
                            String.format(java.util.Locale.getDefault(), "%.1f", latest.getWeight()));
                    setTextIfExists(R.id.tv_weight_update, getUpdateText(latest.getTimestamp()));
                    setTextIfExists(R.id.tv_weight_summary, "最近一次记录");
                } else {
                    setTextIfExists(R.id.tv_weight_value, "--");
                    setTextIfExists(R.id.tv_weight_update, "暂无更新");
                    setTextIfExists(R.id.tv_weight_summary, "点击查看体重明细");
                }
            }
        });
        homeDashboardViewModel.getTodayWaterTotal().observe(getViewLifecycleOwner(), total -> {
            currentWaterTotal = total == null ? 0 : total;
            setTextIfExists(R.id.tv_water_value, String.valueOf(currentWaterTotal));
            // Show water progress toward user's target
            new Thread(() -> {
                com.cz.fitnessdiary.database.AppDatabase db =
                        com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
                com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();
                int target = (user != null && user.getDailyWaterTarget() > 0) ? user.getDailyWaterTarget() : 2000;
                String summary = currentWaterTotal > 0
                        ? "已喝 " + currentWaterTotal + " / " + target + "ml"
                        : "点击添加喝水记录";
                int pct = target > 0 ? Math.min(currentWaterTotal * 100 / target, 100) : 0;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    setTextIfExists(R.id.tv_water_summary, summary);
                    View card = cachedCards.get(CARD_WATER);
                    if (card != null) {
                        com.google.android.material.progressindicator.LinearProgressIndicator p =
                                card.findViewById(R.id.progress_water);
                        if (p != null) p.setProgress(pct);
                    }
                });
            }).start();
            updateOverallProgress();
        });
        homeDashboardViewModel.getSelectedDateLatestWater().observe(getViewLifecycleOwner(),
                r -> setTextIfExists(R.id.tv_water_update,
                        r == null ? "暂无更新" : getSelectedDateUpdateText(r.getTimestamp())));
        homeDashboardViewModel.getTodayMedicationTakenCount().observe(getViewLifecycleOwner(), taken -> {
            int t = taken == null ? 0 : taken;
            Integer total = homeDashboardViewModel.getTodayMedicationTotal().getValue();
            int target = total != null ? total : 0;
            if (target > 0) {
                setTextIfExists(R.id.tv_medication_value, t + "/" + target);
                setTextIfExists(R.id.tv_medication_summary, t >= target ? "今日用药已全部完成 ✓" : "还有 " + (target - t) + " 次未服用");
                View card = cachedCards.get(CARD_MEDICATION);
                if (card != null) {
                    com.google.android.material.progressindicator.LinearProgressIndicator p =
                            card.findViewById(R.id.progress_medication);
                    if (p != null) p.setProgress(t * 100 / target);
                }
            } else {
                setTextIfExists(R.id.tv_medication_value, String.valueOf(t));
                setTextIfExists(R.id.tv_medication_summary, t > 0 ? "已记录 " + t + " 次用药" : "点击添加用药记录");
            }
        });
        homeDashboardViewModel.getTodayMedicationTotal().observe(getViewLifecycleOwner(), total -> {
            Integer taken = homeDashboardViewModel.getTodayMedicationTakenCount().getValue();
            int t = taken != null ? taken : 0;
            int target = total != null ? total : 0;
            if (target > 0) {
                setTextIfExists(R.id.tv_medication_value, t + "/" + target);
            }
        });
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
                java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                String window = tf.format(new java.util.Date(latest.getStartTime())) + "-"
                        + tf.format(new java.util.Date(latest.getEndTime()));
                setTextIfExists(R.id.tv_sleep_update, window);
            } else {
                setTextIfExists(R.id.tv_sleep_update, "暂无更新");
            }

            setTextIfExists(R.id.tv_sleep_value, String.format(java.util.Locale.getDefault(), "%.1f", totalHours));
            final float finalDeepSleep = deepSleepHours;
            if (finalDeepSleep > 0) {
                setTextIfExists(R.id.tv_sleep_summary,
                        String.format(java.util.Locale.getDefault(), "深度%.1fh", finalDeepSleep));
                // Compute 7-day average sleep
                new Thread(() -> {
                    long today = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
                    com.cz.fitnessdiary.database.AppDatabase db =
                            com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
                    float weekTotal = 0;
                    int weekDays = 0;
                    for (int i = 0; i < 7; i++) {
                        long dayStart = today - i * 86400000L;
                        long dayEnd = dayStart + 86400000L;
                        java.util.List<com.cz.fitnessdiary.database.entity.SleepRecord> dayRecords =
                                db.sleepRecordDao().getSleepRecordsByDateRangeSync(dayStart, dayEnd);
                        if (dayRecords != null && !dayRecords.isEmpty()) {
                            float dayTotal = 0;
                            for (com.cz.fitnessdiary.database.entity.SleepRecord sr : dayRecords) {
                                if (sr.getEndTime() > sr.getStartTime()) {
                                    dayTotal += (sr.getEndTime() - sr.getStartTime()) / (1000f * 60 * 60);
                                }
                            }
                            if (dayTotal > 0) {
                                weekTotal += dayTotal;
                                weekDays++;
                            }
                        }
                    }
                    if (weekDays > 0) {
                        float weekAvg = weekTotal / weekDays;
                        final String summary = String.format(java.util.Locale.getDefault(),
                                "深度%.1fh · 周均%.1fh", finalDeepSleep, weekAvg);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                setTextIfExists(R.id.tv_sleep_summary, summary));
                    }
                }).start();
            } else {
                setTextIfExists(R.id.tv_sleep_summary, "点击查看睡眠分析");
            }

            com.google.android.material.progressindicator.LinearProgressIndicator pSleep = binding.getRoot()
                    .findViewById(R.id.progress_sleep);
            if (pSleep != null) {
                int progress = (int) (Math.min(totalHours / 8.0f, 1.0f) * 100);
                pSleep.setProgress(progress);
            }
            updateOverallProgress();
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

        // 围度卡片观察
        homeDashboardViewModel.getTodayMeasurementCount().observe(getViewLifecycleOwner(), count -> {
            int c = count == null ? 0 : count;
            setTextIfExists(R.id.tv_measure_value, String.valueOf(c));
            View card = cachedCards.get(CARD_MEASUREMENT);
            if (card != null) {
                com.google.android.material.progressindicator.LinearProgressIndicator p =
                        card.findViewById(R.id.progress_measure);
                if (p != null) p.setProgress(Math.min(c * 16, 100));
            }
        });
        homeDashboardViewModel.getLatestMeasurementSummary().observe(getViewLifecycleOwner(), summary -> {
            setTextIfExists(R.id.tv_measure_summary, summary == null ? "点击查看围度明细" : summary);
        });
        homeDashboardViewModel.getSelectedDateLatestMeasurementTime().observe(getViewLifecycleOwner(), ts -> {
            setTextIfExists(R.id.tv_measure_update,
                    ts == null || ts == 0 ? "暂无更新" : getSelectedDateUpdateText(ts));
        });

        // 便便卡片观察
        homeDashboardViewModel.getTodayBowelCount().observe(getViewLifecycleOwner(), count -> {
            int c = count == null ? 0 : count;
            setTextIfExists(R.id.tv_bowel_value, String.valueOf(c));
            View card = cachedCards.get(CARD_BOWEL);
            if (card != null) {
                com.google.android.material.progressindicator.LinearProgressIndicator p =
                        card.findViewById(R.id.progress_bowel);
                if (p != null) p.setProgress(Math.min(c * 33, 100));
            }
        });
        homeDashboardViewModel.getLatestBowelSummary().observe(getViewLifecycleOwner(), summary -> {
            setTextIfExists(R.id.tv_bowel_summary, summary == null ? "点击查看便便明细" : summary);
        });
        homeDashboardViewModel.getSelectedDateLatestBowelTime().observe(getViewLifecycleOwner(), ts -> {
            setTextIfExists(R.id.tv_bowel_update,
                    ts == null || ts == 0 ? "暂无更新" : getSelectedDateUpdateText(ts));
        });

        // 经期卡片观察
        homeDashboardViewModel.getCurrentCycleDay().observe(getViewLifecycleOwner(), day -> {
            int d = day == null ? 0 : day;
            setTextIfExists(R.id.tv_menstrual_value, d > 0 ? String.valueOf(d) : "--");
            View card = cachedCards.get(CARD_MENSTRUAL);
            if (card != null) {
                com.google.android.material.progressindicator.LinearProgressIndicator p =
                        card.findViewById(R.id.progress_menstrual);
                if (p != null) p.setProgress(d > 0 ? Math.min(d * 100 / 28, 100) : 0);
            }
        });
        homeDashboardViewModel.getMenstrualSummary().observe(getViewLifecycleOwner(), summary -> {
            setTextIfExists(R.id.tv_menstrual_summary, summary == null ? "点击查看经期明细" : summary);
        });
        homeDashboardViewModel.getSelectedDateLatestMenstrualTime().observe(getViewLifecycleOwner(), ts -> {
            setTextIfExists(R.id.tv_menstrual_update,
                    ts == null || ts == 0 ? "暂无更新" : getSelectedDateUpdateText(ts));
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
        updateSummaryCard();

        // Compute health score on background thread (Room requires async)
        new Thread(() -> {
            try {
                int score = com.cz.fitnessdiary.utils.HealthScoreCalculator.calculateToday(getContext());
                com.cz.fitnessdiary.utils.HealthScoreCalculator.saveTodayScore(getContext(), score);
                android.os.Handler mainHandler = new android.os.Handler(
                        requireActivity().getMainLooper());
                mainHandler.post(() -> {
                    if (isAdded() && binding != null) {
                        binding.progressTotalCircle.setProgress(score);
                        binding.tvProgressPercent.setText(score + "分");
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void showOverallProgressDetails() {
        // Show quick summary immediately, load detailed health score from background
        new Thread(() -> {
            Context ctx = getContext();
            if (ctx == null) return;
            com.cz.fitnessdiary.database.AppDatabase db =
                    com.cz.fitnessdiary.database.AppDatabase.getInstance(ctx);
            long today = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
            long dayEnd = today + 86400000L;
            com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();

            int sportTotal = db.dailyLogDao().getTodayPlanCountSync(today);
            int sportDone = db.dailyLogDao().getTodayCompletedCountSync(today);
            int sportScore = sportTotal > 0 ? Math.round(25f * sportDone / sportTotal) : 25;

            int targetCal = user != null && user.getDailyCalorieTarget() > 0 ? user.getDailyCalorieTarget() : 2000;
            int consumedCal = 0;
            java.util.List<com.cz.fitnessdiary.database.entity.FoodRecord> foods =
                    db.foodRecordDao().getByDateRangeSync(today, dayEnd);
            if (foods != null) for (com.cz.fitnessdiary.database.entity.FoodRecord f : foods) consumedCal += f.getCalories();
            float calRatio = targetCal > 0 ? (float) consumedCal / targetCal : 0;
            int dietScore = consumedCal == 0 ? 0 : calRatio >= 0.9f && calRatio <= 1.1f ? 25 : calRatio >= 0.8f && calRatio <= 1.2f ? 15 : 5;

            int sleepScore = 0;
            java.util.List<com.cz.fitnessdiary.database.entity.SleepRecord> sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(today, dayEnd);
            if (sleeps == null || sleeps.isEmpty())
                sleeps = db.sleepRecordDao().getSleepRecordsByDateRangeSync(today - 86400000L, today);
            if (sleeps != null && !sleeps.isEmpty()) {
                float h = 0; for (com.cz.fitnessdiary.database.entity.SleepRecord s : sleeps) h += s.getDuration() / 3600f;
                sleepScore = h >= 7 && h <= 9 ? 20 : h >= 6 && h < 7 ? 15 : 5;
            }

            int waterMl = db.waterRecordDao().getTodayTotalSync(today, dayEnd);
            int waterTarget = user != null && user.getDailyWaterTarget() > 0 ? user.getDailyWaterTarget() : 2000;
            int waterScore = waterMl >= waterTarget ? 15 : waterMl >= waterTarget / 2 ? 10 : waterMl > 0 ? 5 : 0;

            java.util.List<com.cz.fitnessdiary.database.entity.HabitItem> habits = db.habitItemDao().getEnabledSync();
            int habitScore = 0;
            if (habits != null && !habits.isEmpty()) {
                int done = 0;
                for (com.cz.fitnessdiary.database.entity.HabitItem h : habits) {
                    com.cz.fitnessdiary.database.entity.HabitRecord r = db.habitRecordDao().getByHabitAndDateSync(h.getId(), today);
                    if (r != null && r.isCompleted()) done++;
                }
                habitScore = Math.round(10f * done / habits.size());
            }

            int weightScore = 3;
            if (user != null && user.getWeight() > 0) {
                java.util.List<com.cz.fitnessdiary.database.entity.WeightRecord> wRecords = db.weightRecordDao().getRecentRecordsSync(7);
                if (wRecords != null && wRecords.size() >= 2) {
                    float diff = wRecords.get(0).getWeight() - wRecords.get(wRecords.size() - 1).getWeight();
                    int goal = user.getGoalType();
                    if (goal == 0) weightScore = diff <= 0 ? 5 : 0;
                    else if (goal == 1) weightScore = diff >= 0 ? 5 : 0;
                    else weightScore = Math.abs(diff) < 1f ? 5 : 3;
                }
            }

            int total = sportScore + dietScore + sleepScore + waterScore + habitScore + weightScore;
            total = Math.min(total, 100);

            String msg = String.format(java.util.Locale.getDefault(),
                    "🏃 运动 (25%%):  %d/%d → %d分\n\n" +
                    "🍽 饮食 (25%%):  摄入%d/%d千卡 → %d分\n\n" +
                    "😴 睡眠 (20%%):  %s → %d分\n\n" +
                    "💧 饮水 (15%%):  %dml → %d分\n\n" +
                    "✅ 习惯 (10%%):  %s → %d分\n\n" +
                    "⚖ 体重 (5%%):   %s → %d分\n\n" +
                    "─────────────────\n今日健康总分：%d / 100",
                    sportDone, sportTotal, sportScore,
                    consumedCal, targetCal, dietScore,
                    sleepScore > 0 ? "已记录" : "无记录", sleepScore,
                    waterMl, waterScore,
                    habitScore > 0 ? "部分达成" : "无记录", habitScore,
                    weightScore >= 4 ? "趋势良好" : "待改善", weightScore,
                    total);

            requireActivity().runOnUiThread(() -> {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("今日健康评分分解")
                        .setMessage(msg)
                        .setPositiveButton("知道了", null)
                        .show();
            });
        }).start();
    }

    private void setupRestTimer() {
        binding.cardRestTimer.setVisibility(View.GONE);

        com.google.android.material.chip.ChipGroup chipGroup = binding.cardRestTimer.findViewById(R.id.chip_timer_presets);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_30s))
                timerSelectedSeconds = 30;
            else if (checkedIds.contains(R.id.chip_60s))
                timerSelectedSeconds = 60;
            else if (checkedIds.contains(R.id.chip_90s))
                timerSelectedSeconds = 90;
            else if (checkedIds.contains(R.id.chip_120s))
                timerSelectedSeconds = 120;
        });

        com.google.android.material.button.MaterialButton btnAction = binding.cardRestTimer.findViewById(R.id.btn_timer_action);
        com.google.android.material.button.MaterialButton btnReset = binding.cardRestTimer.findViewById(R.id.btn_timer_reset);
        android.widget.TextView tvCountdown = binding.cardRestTimer.findViewById(R.id.tv_timer_countdown);

        btnAction.setOnClickListener(v -> {
            if (restTimerManager.isRunning()) {
                restTimerManager.pause();
                btnAction.setIconResource(android.R.drawable.ic_media_play);
            } else if (restTimerManager.isPaused()) {
                restTimerManager.resume();
                btnAction.setIconResource(android.R.drawable.ic_media_pause);
            } else {
                restTimerManager.start(timerSelectedSeconds);
                btnAction.setIconResource(android.R.drawable.ic_media_pause);
            }
        });

        btnReset.setOnClickListener(v -> {
            restTimerManager.cancel();
            tvCountdown.setText(String.valueOf(timerSelectedSeconds));
            btnAction.setIconResource(android.R.drawable.ic_media_play);
        });

        restTimerManager.setCallback(new RestTimerManager.TimerCallback() {
            @Override
            public void onTick(long remainingSeconds) {
                android.widget.TextView tv = binding.cardRestTimer.findViewById(R.id.tv_timer_countdown);
                if (tv != null) {
                    tv.setText(String.valueOf(remainingSeconds));
                }
            }

            @Override
            public void onFinish() {
                android.widget.TextView tv = binding.cardRestTimer.findViewById(R.id.tv_timer_countdown);
                if (tv != null)
                    tv.setText("✓");
                com.google.android.material.button.MaterialButton btn = binding.cardRestTimer
                        .findViewById(R.id.btn_timer_action);
                if (btn != null)
                    btn.setIconResource(android.R.drawable.ic_media_play);
                if (getContext() != null)
                    restTimerManager.vibrate(getContext());
                ErrorHandler.showInfo(CheckInFragment.this, "休息时间到，准备下一组！");
            }
        });
    }

    private void updateSummaryCard() {
        Integer consumed = dietViewModel.getTodayTotalCalories().getValue();
        User user = dietViewModel.getCurrentUser().getValue();
        int target = (user != null && user.getDailyCalorieTarget() > 0) ? user.getDailyCalorieTarget() : 2000;
        int calValue = consumed != null ? consumed : 0;
        int balance = target - calValue;
        String calText = balance >= 0 ? "剩" + balance : "超" + (-balance);

        TextView tvCal = binding.getRoot().findViewById(R.id.tv_mission_cal_balance);
        if (tvCal != null) {
            tvCal.setText(calText + "千卡");
            tvCal.setTextColor(getResources().getColor(
                    balance >= 0 ? R.color.diet_primary : R.color.error, null));
        }

        TextView tvWater = binding.getRoot().findViewById(R.id.tv_mission_water);
        if (tvWater != null) {
            tvWater.setText("水" + currentWaterTotal + "ml");
        }

        int habitTotal = habitItems.size();
        int habitDone = 0;
        for (HabitItem item : habitItems) {
            HabitRecord r = habitRecords.get(item.getId());
            if (r != null && r.isCompleted())
                habitDone++;
        }
        TextView tvHabit = binding.getRoot().findViewById(R.id.tv_mission_habit);
        if (tvHabit != null) {
            tvHabit.setText("习惯" + habitDone + "/" + habitTotal);
        }
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
        setTextIfExists(R.id.tv_habit_value, total > 0 ? completed + "/" + total : "--");
        if (total > 0) {
            int pct = completed * 100 / total;
            if (completed == total) {
                setTextIfExists(R.id.tv_habit_summary, "全部达成，太棒了！");
            } else if (pct >= 50) {
                setTextIfExists(R.id.tv_habit_summary, "已完成过半，继续加油");
            } else if (completed > 0) {
                setTextIfExists(R.id.tv_habit_summary, "好的开始，还有 " + (total - completed) + " 项待完成");
            } else {
                setTextIfExists(R.id.tv_habit_summary, "今天还没开始，行动起来吧");
            }
        } else {
            setTextIfExists(R.id.tv_habit_summary, "点击添加习惯项目");
        }
        setTextIfExists(R.id.tv_habit_update, total > 0 ? "共 " + total + " 项习惯" : "养成好习惯");
        // Update progress
        View card = cachedCards.get(CARD_HABIT);
        if (card != null) {
            com.google.android.material.progressindicator.LinearProgressIndicator p =
                    card.findViewById(R.id.progress_habit);
            if (p != null) p.setProgress(total > 0 ? completed * 100 / total : 0);
        }
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

    private void showHeatmapDialog() {
        com.cz.fitnessdiary.ui.widget.StreakCalendarView calendar =
                new com.cz.fitnessdiary.ui.widget.StreakCalendarView(requireContext());
        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
        calendar.setLayoutParams(new android.view.ViewGroup.LayoutParams(w, dp(260)));
        calendar.setPadding(dp(8), dp(12), dp(8), dp(12));

        new Thread(() -> {
            java.util.Map<Long, Integer> levels = new java.util.HashMap<>();
            long today = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(today);
            cal.set(java.util.Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            cal.add(java.util.Calendar.WEEK_OF_YEAR, -5);
            long start = cal.getTimeInMillis();

            com.cz.fitnessdiary.database.AppDatabase db =
                    com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());

            for (int i = 0; i < 42; i++) {
                long day = start + i * 86400000L;
                if (day > today) break;
                int comp = db.dailyLogDao().getTodayCompletedCountSync(day);
                int tot = db.dailyLogDao().getTodayPlanCountSync(day);
                int dietCount = db.foodRecordDao().getRecordCountByDateRangeSync(day, day + 86400000L);
                int wml = db.waterRecordDao().getTodayTotalSync(day, day + 86400000L);
                int lvl = 0;
                if (tot > 0 && comp > 0) lvl = 1;
                if (comp >= tot && tot > 0) lvl = 2;
                if (lvl >= 2 && dietCount > 0) lvl = 3;
                if (lvl >= 3 && wml >= 1000) lvl = 4;
                levels.put(day, lvl);
            }

            requireActivity().runOnUiThread(() -> {
                calendar.setDayLevels(levels);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("打卡热力图")
                        .setView(calendar)
                        .setPositiveButton("关闭", null)
                        .show();
            });
        }).start();
    }

    private void refreshChallengeCard() {
        new Thread(() -> {
            com.cz.fitnessdiary.utils.ChallengeManager.checkToday(getContext());
            requireActivity().runOnUiThread(() -> {
        String status = com.cz.fitnessdiary.utils.ChallengeManager.getStatus(getContext());
        String type = com.cz.fitnessdiary.utils.ChallengeManager.getActiveType(getContext());
        View card = binding.getRoot().findViewById(R.id.card_challenge);
        if (card == null) return;

        if (type == null || !"ACTIVE".equals(status)) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        int days = com.cz.fitnessdiary.utils.ChallengeManager.getProgressDays(getContext());
        int fails = com.cz.fitnessdiary.utils.ChallengeManager.getFailDays(getContext());

        TextView tvEmoji = card.findViewById(R.id.tv_challenge_emoji);
        if (tvEmoji != null) tvEmoji.setText(com.cz.fitnessdiary.utils.ChallengeManager.getTypeEmoji(type));
        TextView tvTitle = card.findViewById(R.id.tv_challenge_title);
        if (tvTitle != null) tvTitle.setText(com.cz.fitnessdiary.utils.ChallengeManager.getTypeName(type));
        TextView tvProgress = card.findViewById(R.id.tv_challenge_progress);
        if (tvProgress != null) tvProgress.setText("第 " + Math.min(days, 21) + "/21 天");
        TextView tvFails = card.findViewById(R.id.tv_challenge_fails);
        if (tvFails != null) {
            int max = "MUSCLE_GAIN".equals(type) ? 2 : 3;
            tvFails.setText("失败" + fails + "/" + max);
        }
            });
        }).start();
    }

    private void showChallengeDialog() {
        String active = com.cz.fitnessdiary.utils.ChallengeManager.getActiveType(getContext());
        String status = com.cz.fitnessdiary.utils.ChallengeManager.getStatus(getContext());

        if (active != null && "ACTIVE".equals(status)) {
            // Show active challenge with option to abandon
            String msg = "当前挑战：" + com.cz.fitnessdiary.utils.ChallengeManager.getTypeName(active) +
                    "\n进度：" + com.cz.fitnessdiary.utils.ChallengeManager.getProgressDays(getContext()) + "/21 天" +
                    "\n失败：" + com.cz.fitnessdiary.utils.ChallengeManager.getFailDays(getContext()) + "次";
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("21天挑战进行中")
                    .setMessage(msg)
                    .setPositiveButton("继续挑战", null)
                    .setNegativeButton("放弃挑战", (d, w) -> {
                        com.cz.fitnessdiary.utils.ChallengeManager.reset(getContext());
                        refreshChallengeCard();
                        Toast.makeText(getContext(), "已放弃挑战", Toast.LENGTH_SHORT).show();
                    })
                    .show();
            return;
        }

        if (active != null && ("COMPLETED".equals(status) || "FAILED".equals(status))) {
            String result = "COMPLETED".equals(status) ? "挑战成功！太棒了！" : "挑战失败，下次加油！";
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(com.cz.fitnessdiary.utils.ChallengeManager.getTypeName(active))
                    .setMessage(result + "\n\n是否开启新的挑战？")
                    .setPositiveButton("新挑战", (d, w) -> { showChallengeTypePicker(); })
                    .setNegativeButton("关闭", (d, w) -> {
                        com.cz.fitnessdiary.utils.ChallengeManager.reset(getContext());
                        refreshChallengeCard();
                    })
                    .show();
            return;
        }

        showChallengeTypePicker();
    }

    private void showChallengeTypePicker() {
        String[] types = {
                com.cz.fitnessdiary.utils.ChallengeManager.TYPE_FAT_LOSS,
                com.cz.fitnessdiary.utils.ChallengeManager.TYPE_MUSCLE_GAIN,
                com.cz.fitnessdiary.utils.ChallengeManager.TYPE_EARLY_SLEEP,
                com.cz.fitnessdiary.utils.ChallengeManager.TYPE_WATER_MASTER
        };
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = com.cz.fitnessdiary.utils.ChallengeManager.getTypeEmoji(types[i]) + " " +
                    com.cz.fitnessdiary.utils.ChallengeManager.getTypeName(types[i]) + "\n" +
                    com.cz.fitnessdiary.utils.ChallengeManager.getTypeDesc(types[i]);
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择21天挑战")
                .setItems(names, (d, idx) -> {
                    com.cz.fitnessdiary.utils.ChallengeManager.start(getContext(), types[idx]);
                    refreshChallengeCard();
                    Toast.makeText(getContext(), "挑战已开启！", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
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
            } else if (CARD_MEASUREMENT.equals(id)) {
                name = "围度记录";
                visible = isCardEnabled(KEY_SHOW_MEASUREMENT, true);
            } else if (CARD_BOWEL.equals(id)) {
                name = "便便记录";
                visible = isCardEnabled(KEY_SHOW_BOWEL, true);
            } else if (CARD_MENSTRUAL.equals(id)) {
                name = "经期记录";
                visible = isCardEnabled(KEY_SHOW_MENSTRUAL, true);
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
                        else if (CARD_MEASUREMENT.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_MEASUREMENT, cfg.visible);
                        else if (CARD_BOWEL.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_BOWEL, cfg.visible);
                        else if (CARD_MENSTRUAL.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_MENSTRUAL, cfg.visible);
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
                        + CARD_MEASUREMENT + "," + CARD_BOWEL + "," + CARD_MENSTRUAL);
        smallCardOrder.clear();
        if (raw != null) {
            for (String s : raw.split(",")) {
                String id = s.trim();
                if (!smallCardOrder.contains(id) && !"custom".equals(id))
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
        if (!smallCardOrder.contains(CARD_MEASUREMENT))
            smallCardOrder.add(CARD_MEASUREMENT);
        if (!smallCardOrder.contains(CARD_BOWEL))
            smallCardOrder.add(CARD_BOWEL);
        if (!smallCardOrder.contains(CARD_MENSTRUAL))
            smallCardOrder.add(CARD_MENSTRUAL);
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
            if (CARD_MEASUREMENT.equals(id) && isCardEnabled(KEY_SHOW_MEASUREMENT, true))
                enabled.add(id);
            if (CARD_BOWEL.equals(id) && isCardEnabled(KEY_SHOW_BOWEL, true))
                enabled.add(id);
            if (CARD_MENSTRUAL.equals(id) && isCardEnabled(KEY_SHOW_MENSTRUAL, true))
                enabled.add(id);
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
        return cachedCards.get(id);
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
        if (restTimerManager != null) {
            restTimerManager.cancel();
        }
        lastEnabledCardIds.clear();
        cachedCards.clear();
        binding = null;
    }
}
