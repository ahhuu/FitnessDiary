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
import com.cz.fitnessdiary.database.AppDatabase;
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
import com.cz.fitnessdiary.utils.ExerciseMetTable;
import com.cz.fitnessdiary.utils.RestTimerManager;
import com.cz.fitnessdiary.viewmodel.AchievementCenterViewModel;
import com.cz.fitnessdiary.viewmodel.CheckInViewModel;
import com.cz.fitnessdiary.viewmodel.DietViewModel;
import com.cz.fitnessdiary.viewmodel.HomeDashboardViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import android.widget.FrameLayout;
import android.view.LayoutInflater;
import android.widget.ImageView;

import com.cz.fitnessdiary.model.DailyHealthSnapshot;
import com.cz.fitnessdiary.model.HealthScoreBreakdown;
import com.cz.fitnessdiary.repository.HealthAggregationRepository;
import com.cz.fitnessdiary.ui.guide.GuideStateManager;
import com.cz.fitnessdiary.ui.guide.GuideStep;
import com.cz.fitnessdiary.ui.guide.PageGuide;
import com.cz.fitnessdiary.ui.guide.TargetedGuideOverlay;
import com.cz.fitnessdiary.utils.HealthScoreCalculator;
import com.cz.fitnessdiary.utils.UnitUtils;

import java.util.Arrays;

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
    private static final String KEY_SHOW_STEP = "show_step";
    private static final String KEY_SHOW_MOOD = "show_mood";
    private static final String KEY_SMALL_ORDER = "small_order";

    private static final String CARD_WATER = "water";
    private static final String CARD_SLEEP = "sleep";
    private static final String CARD_HABIT = "habit";
    private static final String CARD_MEDICATION = "medication";
    private static final String CARD_WEIGHT = "weight";
    private static final String CARD_MEASUREMENT = "measurement";
    private static final String CARD_BOWEL = "bowel";
    private static final String CARD_MENSTRUAL = "menstrual";
    private static final String CARD_STEP = "step";
    private static final String CARD_MOOD = "mood";

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
    private com.cz.fitnessdiary.utils.StepSensorHelper stepSensorHelper;
    private SharedPreferences.OnSharedPreferenceChangeListener homeCardsChangeListener;

    // 顶部 3 高亮轻量化标签常量与配置
    private static final int TAG_TYPE_CAL_REMAIN = 0; // 剩余热量
    private static final int TAG_TYPE_SPORT_BURN = 1; // 运动消耗
    private static final int TAG_TYPE_WATER = 2; // 饮水量
    private static final int TAG_TYPE_STEPS = 3; // 今日步数
    private static final int TAG_TYPE_SLEEP = 4; // 睡眠时长
    private static final int TAG_TYPE_MOOD = 5; // 今日心情
    private static final int TAG_TYPE_HABIT = 6; // 习惯打卡
    private static final int TAG_TYPE_WEIGHT = 7; // 当前体重
    private static final int TAG_TYPE_BOWEL = 8; // 便便记录
    private static final int TAG_TYPE_MEDICATION = 9; // 药物记录
    private static final int TAG_TYPE_MENSTRUAL = 10; // 生理期天数

    private int tag1Type = TAG_TYPE_CAL_REMAIN;
    private int tag2Type = TAG_TYPE_SPORT_BURN;
    private int tag3Type = TAG_TYPE_WATER;
    private int exerciseCalories = 0;

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
        stepSensorHelper = new com.cz.fitnessdiary.utils.StepSensorHelper(requireContext());
        setupActions();
        setupRestTimer();
        cacheCards();
        setupDateNavigation();
        observeData();
        // 动态所有卡片配置应用
        applyBigCardsConfig();

        // 注册所有卡片配置监听
        homeCardsChangeListener = (sp, key) -> {
            if ("home_cards_order".equals(key) || key.startsWith("show_card_")) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(this::applyBigCardsConfig);
                }
            }
        };
        requireContext().getSharedPreferences("home_cards_prefs", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(homeCardsChangeListener);

        updateDateHeader();
        updateSummaryCard();
        achievementCenterViewModel.refreshAll();
    }

    public void showPageGuide(GuideStateManager guideManager) {
        String pageKey = "guide_checkin";
        if (guideManager.isPageGuideDone(pageKey)) return;
        binding.getRoot().postDelayed(() -> {
            if (!isAdded()) return;
            java.util.List<com.cz.fitnessdiary.ui.guide.GuideStep> steps = new java.util.ArrayList<>();
            steps.add(new com.cz.fitnessdiary.ui.guide.GuideStep(R.id.fl_total_circle,
                    "健康评分", "每日五维度健康评分环，点击可查看各维度详细得分",
                    com.cz.fitnessdiary.ui.guide.GuideStep.Anchor.BOTTOM));
            steps.add(new com.cz.fitnessdiary.ui.guide.GuideStep(R.id.card_sport,
                    "运动打卡", "查看今日训练计划完成度、消耗热量和热量来源分解",
                    com.cz.fitnessdiary.ui.guide.GuideStep.Anchor.TOP));
            steps.add(new com.cz.fitnessdiary.ui.guide.GuideStep(R.id.card_diet,
                    "饮食记录", "追踪每日热量与三大宏量营养素摄入情况",
                    com.cz.fitnessdiary.ui.guide.GuideStep.Anchor.TOP));
            steps.add(new com.cz.fitnessdiary.ui.guide.GuideStep(R.id.grid_cards,
                    "健康小卡片", "饮水/睡眠/习惯/体重/步数等10个小卡片，拖拽排序、自由开关",
                    com.cz.fitnessdiary.ui.guide.GuideStep.Anchor.TOP));
            com.cz.fitnessdiary.ui.guide.PageGuide guide = new com.cz.fitnessdiary.ui.guide.PageGuide(pageKey, steps);
            new com.cz.fitnessdiary.ui.guide.TargetedGuideOverlay(requireActivity(), guide, guideManager, null).start();
        }, 800);
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
        if (stepSensorHelper != null && stepSensorHelper.isSensorAvailable()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (requireContext().checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{android.Manifest.permission.ACTIVITY_RECOGNITION}, 1001);
                    return;
                }
            }
            stepSensorHelper.start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 1001 && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (stepSensorHelper != null && stepSensorHelper.isSensorAvailable()) {
                stepSensorHelper.start();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (stepSensorHelper != null) {
            stepSensorHelper.stop();
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
            updateTopTagsValues();
        });

        // 综合运动和饮食记录日期，更新横滑绿点
        androidx.lifecycle.MediatorLiveData<java.util.Set<Long>> combined = new androidx.lifecycle.MediatorLiveData<>();
        combined.addSource(checkInViewModel.getRecordedDates(), sportDates -> {
            java.util.Set<Long> diet = dietViewModel.getRecordedDates().getValue();
            java.util.Set<Long> all = new HashSet<>();
            if (sportDates != null) all.addAll(sportDates);
            if (diet != null) all.addAll(diet);
            combined.setValue(all);
        });
        combined.addSource(dietViewModel.getRecordedDates(), dietDates -> {
            java.util.Set<Long> sport = checkInViewModel.getRecordedDates().getValue();
            java.util.Set<Long> all = new HashSet<>();
            if (sport != null) all.addAll(sport);
            if (dietDates != null) all.addAll(dietDates);
            combined.setValue(all);
        });
        combined.observe(getViewLifecycleOwner(), rd -> {
            if (dateNavAdapter != null) {
                dateNavAdapter.setRecordedDates(rd);
            }
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
        cachedCards.put(CARD_STEP, pool.findViewById(R.id.card_step));
        cachedCards.put(CARD_MOOD, pool.findViewById(R.id.card_mood));
        // [v2.2] 饮食摘要观察
        // 这里的观察已移至底部逻辑
        dietViewModel.getTodayTotalCalories().observe(getViewLifecycleOwner(), calories -> {
            int consumed = calories != null ? calories : 0;
            binding.tvDietConsumed.setText(UnitUtils.formatEnergy(consumed, requireContext()) + " " + UnitUtils.getEnergyUnitSymbol(requireContext()));
            updateTopTagsValues();
        });

        dietViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            updateDietProgress();
            updateTopTagsValues();
        });

        dietViewModel.getSmartFeedback().observe(getViewLifecycleOwner(), feedback -> {
            if (feedback != null) {
                binding.tvDietFeedback.setText(feedback);
            }
        });

        dietViewModel.getProgressColor().observe(getViewLifecycleOwner(), colorRes -> {
            // 已由 gradient_progress_diet.xml 指定精美渐变色，此处无须动态改变
        });

        // 饮食数据变化时，同步更新大圆环
        dietViewModel.getTodayTotalCalories().observe(getViewLifecycleOwner(), calories -> {
            updateDietProgress();
            updateOverallProgress();
            updateTopTagsValues();
        });

        // 饮食宏量营养素观察
        dietViewModel.getTodayTotalCarbs().observe(getViewLifecycleOwner(), carbs -> {
            updateDietMacros();
            updateTopTagsValues();
        });
        dietViewModel.getTodayTotalProtein().observe(getViewLifecycleOwner(), protein -> {
            updateDietMacros();
            updateTopTagsValues();
        });
        dietViewModel.getTodayTotalFat().observe(getViewLifecycleOwner(), fat -> {
            updateDietMacros();
            updateTopTagsValues();
        });
        dietViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            updateDietMacros();
            updateTopTagsValues();
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
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(CheckInFragment.this)
                        .navigate(R.id.dietFragment);
            } catch (Exception e) {
                e.printStackTrace();
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
        v(R.id.card_step).setOnClickListener(v2 -> openDetail(R.id.stepRecordDetailFragment));
        v(R.id.card_step).setOnLongClickListener(v2 -> { showStepTargetDialog(); return true; });
        v(R.id.card_mood).setOnClickListener(v2 -> showMoodPicker());
        v(R.id.btn_add_step).setOnClickListener(v2 -> showStepInputDialog());

        // [Removed v2.4] 顶部 3 高亮轻量化标签已永久隐藏
        // binding.layoutTopTag1.setOnClickListener(v -> showSelectTagDialog(1));
        // binding.layoutTopTag2.setOnClickListener(v -> showSelectTagDialog(2));
        // binding.layoutTopTag3.setOnClickListener(v -> showSelectTagDialog(3));
        // loadTopTagsConfig();
        // updateTopTagsValues();

        View btnAiQuick = v(R.id.btn_ai_quick);
        if (btnAiQuick != null) {
            btnAiQuick.setOnClickListener(va -> {
                QuickAiChatBottomSheet.newInstance().show(getParentFragmentManager(), "QUICK_AI_CHAT");
            });
        }

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
                v -> quickNumberInput("添加体重(" + UnitUtils.getWeightUnitSymbol(requireContext()) + ")", value -> homeDashboardViewModel.addWeight(value.floatValue(), null)));
        v(R.id.btn_add_medication).setOnClickListener(
                v -> quickTextInput("添加用药", text -> homeDashboardViewModel.addMedication(text, "", true, null)));
        v(R.id.btn_add_sleep).setOnClickListener(v -> openDetail(R.id.sleepRecordDetailFragment));
        v(R.id.btn_add_habit).setOnClickListener(v -> openDetail(R.id.habitRecordDetailFragment));
        v(R.id.btn_add_measure).setOnClickListener(v -> openDetail(R.id.bodyMeasurementDetailFragment));
        v(R.id.btn_add_bowel).setOnClickListener(v -> openDetail(R.id.bowelMovementDetailFragment));
        v(R.id.btn_add_menstrual).setOnClickListener(v -> openDetail(R.id.menstrualRecordDetailFragment));

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
                days -> binding.tvConsecutiveDays.setText("连续打卡 " + (days == null ? 0 : days) + " 天"));
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
                        UnitUtils.formatWeight(r.getWeight(), requireContext()));
                setTextIfExists(R.id.tv_weight_unit,
                        UnitUtils.getWeightUnitSymbol(requireContext()));
                setTextIfExists(R.id.tv_weight_update, getSelectedDateUpdateText(r.getTimestamp()));
                setTextIfExists(R.id.tv_weight_summary, "已记录当日体重");
                // Compute detailed weight analysis (delta + BMI + 7-day trend)
                new Thread(() -> {
                    Context context = getContext();
                    if (context == null) return;
                    com.cz.fitnessdiary.database.AppDatabase db =
                            com.cz.fitnessdiary.database.AppDatabase.getInstance(context);
                    StringBuilder analysis = new StringBuilder();
                    String weightUnitSymbol = UnitUtils.getWeightUnitSymbol(context);
                    // Weight delta vs previous record
                    com.cz.fitnessdiary.database.entity.WeightRecord prevRecord =
                            db.weightRecordDao().getLatestRecordBeforeSync(r.getTimestamp());
                    if (prevRecord != null) {
                        float deltaAbs = Math.abs(r.getWeight() - prevRecord.getWeight());
                        if (deltaAbs < 0.05f) {
                            analysis.append("稳定");
                        } else {
                            String arrow = (r.getWeight() - prevRecord.getWeight()) < 0 ? "↓" : "↑";
                            analysis.append(arrow).append(UnitUtils.formatWeight(deltaAbs, context)).append(weightUnitSymbol);
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
                                    .append(UnitUtils.formatWeight(Math.abs(trend), context)).append(weightUnitSymbol);
                        }
                    }
                    final String text = analysis.length() > 0 ? analysis.toString() : "已记录当日体重";
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            setTextIfExists(R.id.tv_weight_summary, text));
                }).start();
            } else {
                // Fallback to latest overall weight using direct query to prevent LiveData latency/null issues
                new Thread(() -> {
                    try {
                        Context context = getContext();
                        if (context == null) return;
                        com.cz.fitnessdiary.database.AppDatabase db =
                                com.cz.fitnessdiary.database.AppDatabase.getInstance(context);
                        com.cz.fitnessdiary.database.entity.WeightRecord latest =
                                db.weightRecordDao().getLatestRecordSync();
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (isAdded() && binding != null) {
                                if (latest != null) {
                                    setTextIfExists(R.id.tv_weight_value,
                                            UnitUtils.formatWeight(latest.getWeight(), requireContext()));
                                    setTextIfExists(R.id.tv_weight_unit,
                                            UnitUtils.getWeightUnitSymbol(requireContext()));
                                    setTextIfExists(R.id.tv_weight_update, getUpdateText(latest.getTimestamp()));
                                    setTextIfExists(R.id.tv_weight_summary, "最近一次记录");
                                } else {
                                    setTextIfExists(R.id.tv_weight_value, "--");
                                    setTextIfExists(R.id.tv_weight_unit,
                                            UnitUtils.getWeightUnitSymbol(requireContext()));
                                    setTextIfExists(R.id.tv_weight_update, "暂无更新");
                                    setTextIfExists(R.id.tv_weight_summary, "点击查看体重明细");
                                }
                            }
                        });
                    } catch (Exception ignored) {}
                }).start();
            }
        });
        homeDashboardViewModel.getTodayWaterTotal().observe(getViewLifecycleOwner(), total -> {
            currentWaterTotal = total == null ? 0 : total;
            setTextIfExists(R.id.tv_water_value, String.valueOf(currentWaterTotal));
            // Show water progress toward user's target
            new Thread(() -> {
                Context context = getContext();
                if (context == null) return;
                com.cz.fitnessdiary.database.AppDatabase db =
                        com.cz.fitnessdiary.database.AppDatabase.getInstance(context);
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
                        android.widget.ProgressBar p =
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
                    android.widget.ProgressBar p =
                            card.findViewById(R.id.progress_medication);
                    if (p != null) p.setProgress(t * 100 / target);
                }
            } else {
                if (t > 0) {
                    setTextIfExists(R.id.tv_medication_value, String.valueOf(t));
                    setTextIfExists(R.id.tv_medication_summary, "已记录 " + t + " 次用药");
                }
            }
            updateTopTagsValues();
        });
        homeDashboardViewModel.getTodayMedicationTotal().observe(getViewLifecycleOwner(), total -> {
            Integer taken = homeDashboardViewModel.getTodayMedicationTakenCount().getValue();
            int t = taken != null ? taken : 0;
            int target = total != null ? total : 0;
            if (target > 0) {
                setTextIfExists(R.id.tv_medication_value, t + "/" + target);
            }
            updateTopTagsValues();
        });
        homeDashboardViewModel.getSelectedDateLatestMedication().observe(getViewLifecycleOwner(), r -> {
            if (r != null) {
                setTextIfExists(R.id.tv_medication_update, getSelectedDateUpdateText(r.getTimestamp()));
            } else {
                new Thread(() -> {
                    try {
                        Context context = getContext();
                        if (context == null) return;
                        com.cz.fitnessdiary.database.AppDatabase db =
                                com.cz.fitnessdiary.database.AppDatabase.getInstance(context);
                        java.util.List<com.cz.fitnessdiary.database.entity.MedicationRecord> recs =
                                db.medicationRecordDao().getRecentRecordsSync(1);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (isAdded() && binding != null) {
                                if (recs != null && !recs.isEmpty()) {
                                    com.cz.fitnessdiary.database.entity.MedicationRecord latest = recs.get(0);
                                    setTextIfExists(R.id.tv_medication_update, getUpdateText(latest.getTimestamp()));
                                    setTextIfExists(R.id.tv_medication_summary, "上次服用: " + latest.getName());
                                    setTextIfExists(R.id.tv_medication_value, "--");
                                    View card = cachedCards.get(CARD_MEDICATION);
                                    if (card != null) {
                                        android.widget.ProgressBar p = card.findViewById(R.id.progress_medication);
                                        if (p != null) p.setProgress(0);
                                    }
                                } else {
                                    setTextIfExists(R.id.tv_medication_update, "暂无更新");
                                    setTextIfExists(R.id.tv_medication_summary, "点击添加用药记录");
                                    setTextIfExists(R.id.tv_medication_value, "0");
                                    View card = cachedCards.get(CARD_MEDICATION);
                                    if (card != null) {
                                        android.widget.ProgressBar p = card.findViewById(R.id.progress_medication);
                                        if (p != null) p.setProgress(0);
                                    }
                                }
                            }
                        });
                    } catch (Exception ignored) {}
                }).start();
            }
        });

        checkInViewModel.getSelectedDaySleepRecords().observe(getViewLifecycleOwner(), records -> {
            if (records != null && !records.isEmpty()) {
                float totalHours = 0;
                float deepSleepHours = 0;
                for (com.cz.fitnessdiary.database.entity.SleepRecord r : records) {
                    if (r.getEndTime() > r.getStartTime()) {
                        float duration = (r.getEndTime() - r.getStartTime()) / (1000f * 60 * 60);
                        totalHours += duration;
                        // 估算深度睡眠：基于总时长 and 睡眠质量 (15%-35%)
                        float ratio = 0.15f + (Math.max(1, Math.min(5, r.getQuality())) / 5.0f) * 0.20f;
                        deepSleepHours += (duration * ratio);
                    }
                }
                com.cz.fitnessdiary.database.entity.SleepRecord latest = records.get(records.size() - 1);
                java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                String window = tf.format(new java.util.Date(latest.getStartTime())) + "-"
                        + tf.format(new java.util.Date(latest.getEndTime()));
                setTextIfExists(R.id.tv_sleep_update, window);
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

                android.widget.ProgressBar pSleep = binding.getRoot()
                        .findViewById(R.id.progress_sleep);
                if (pSleep != null) {
                    int progress = (int) (Math.min(totalHours / 8.0f, 1.0f) * 100);
                    pSleep.setProgress(progress);
                }
                updateOverallProgress();
            } else {
                // 当天没有更新睡眠记录时，回退去拉取最近一次历史记录展示，但今天的分数不加分
                new Thread(() -> {
                    try {
                        com.cz.fitnessdiary.database.AppDatabase db =
                                com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
                        java.util.List<com.cz.fitnessdiary.database.entity.SleepRecord> recs =
                                db.sleepRecordDao().getRecentRecordsSync(1);
                        if (recs != null && !recs.isEmpty()) {
                            com.cz.fitnessdiary.database.entity.SleepRecord latest = recs.get(0);
                            float duration = (latest.getEndTime() - latest.getStartTime()) / (1000f * 60 * 60);
                            float ratio = 0.15f + (Math.max(1, Math.min(5, latest.getQuality())) / 5.0f) * 0.20f;
                            float deep = duration * ratio;
                            
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (isAdded() && binding != null) {
                                    setTextIfExists(R.id.tv_sleep_value, String.format(java.util.Locale.getDefault(), "%.1f", duration));
                                    setTextIfExists(R.id.tv_sleep_update, getUpdateText(latest.getEndTime()));
                                    setTextIfExists(R.id.tv_sleep_summary, String.format(java.util.Locale.getDefault(), "上次：深度%.1fh", deep));
                                    android.widget.ProgressBar pSleep = binding.getRoot().findViewById(R.id.progress_sleep);
                                    if (pSleep != null) {
                                        pSleep.setProgress((int) (Math.min(duration / 8.0f, 1.0f) * 100));
                                    }
                                    updateOverallProgress();
                                }
                            });
                        } else {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (isAdded() && binding != null) {
                                    setTextIfExists(R.id.tv_sleep_update, "暂无更新");
                                    setTextIfExists(R.id.tv_sleep_value, "0.0");
                                    setTextIfExists(R.id.tv_sleep_summary, "点击查看睡眠分析");
                                    android.widget.ProgressBar pSleep = binding.getRoot().findViewById(R.id.progress_sleep);
                                    if (pSleep != null) pSleep.setProgress(0);
                                    updateOverallProgress();
                                }
                            });
                        }
                    } catch (Exception ignored) {}
                }).start();
            }
            updateTopTagsValues();
        });

        homeDashboardViewModel.getEnabledHabits().observe(getViewLifecycleOwner(), items -> {
            habitItems.clear();
            if (items != null)
                habitItems.addAll(items);
            refreshHabitCard();
            updateOverallProgress();
            updateTopTagsValues();
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
            updateTopTagsValues();
        });

        // 围度卡片观察
        homeDashboardViewModel.getTodayMeasurementCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null && count > 0) {
                int c = count;
                setTextIfExists(R.id.tv_measure_value, String.valueOf(c));
                View card = cachedCards.get(CARD_MEASUREMENT);
                if (card != null) {
                    android.widget.ProgressBar p =
                            card.findViewById(R.id.progress_measure);
                    if (p != null) p.setProgress(Math.min(c * 16, 100));
                }
            }
        });
        homeDashboardViewModel.getLatestMeasurementSummary().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null) {
                setTextIfExists(R.id.tv_measure_summary, summary);
            }
        });
        homeDashboardViewModel.getSelectedDateLatestMeasurementTime().observe(getViewLifecycleOwner(), ts -> {
            if (ts != null && ts > 0) {
                setTextIfExists(R.id.tv_measure_update, getSelectedDateUpdateText(ts));
            } else {
                new Thread(() -> {
                    try {
                        com.cz.fitnessdiary.database.AppDatabase db =
                                com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
                        com.cz.fitnessdiary.database.entity.BodyMeasurement latest =
                                db.bodyMeasurementDao().getLatestSync();
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (isAdded() && binding != null) {
                                if (latest != null) {
                                    String typeName = com.cz.fitnessdiary.utils.AnalysisUtils.getMeasurementTypeName(latest.getMeasurementType());
                                    String desc = typeName + ": " + latest.getValue() + latest.getUnit();
                                    setTextIfExists(R.id.tv_measure_update, getUpdateText(latest.getTimestamp()));
                                    setTextIfExists(R.id.tv_measure_summary, "上次: " + desc);
                                    setTextIfExists(R.id.tv_measure_value, String.format(java.util.Locale.getDefault(), "%.1f", latest.getValue()));
                                } else {
                                    setTextIfExists(R.id.tv_measure_update, "暂无更新");
                                    setTextIfExists(R.id.tv_measure_summary, "点击查看围度明细");
                                    setTextIfExists(R.id.tv_measure_value, "--");
                                }
                                View card = cachedCards.get(CARD_MEASUREMENT);
                                if (card != null) {
                                    android.widget.ProgressBar p = card.findViewById(R.id.progress_measure);
                                    if (p != null) p.setProgress(0);
                                }
                            }
                        });
                    } catch (Exception ignored) {}
                }).start();
            }
        });

        // 便便卡片观察
        homeDashboardViewModel.getTodayBowelCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null && count > 0) {
                int c = count;
                setTextIfExists(R.id.tv_bowel_value, String.valueOf(c));
                View card = cachedCards.get(CARD_BOWEL);
                if (card != null) {
                    android.widget.ProgressBar p =
                            card.findViewById(R.id.progress_bowel);
                    if (p != null) p.setProgress(Math.min(c * 33, 100));
                }
            }
            updateTopTagsValues();
        });
        homeDashboardViewModel.getLatestBowelSummary().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null) {
                setTextIfExists(R.id.tv_bowel_summary, summary);
            }
        });
        homeDashboardViewModel.getSelectedDateLatestBowelTime().observe(getViewLifecycleOwner(), ts -> {
            if (ts != null && ts > 0) {
                setTextIfExists(R.id.tv_bowel_update, getSelectedDateUpdateText(ts));
                final Context ctx = getContext();
                if (ctx == null) return;
                final Long selectedDate = checkInViewModel.getSelectedDate().getValue();
                final long date = selectedDate != null ? selectedDate : com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
                new Thread(() -> {
                    try {
                        com.cz.fitnessdiary.database.AppDatabase db =
                                com.cz.fitnessdiary.database.AppDatabase.getInstance(ctx);
                        com.cz.fitnessdiary.database.entity.BowelMovement latest =
                                db.bowelMovementDao().getLatestByDateSync(date, date + 86400000L);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (isAdded() && binding != null) {
                                View card = cachedCards.get(CARD_BOWEL);
                                if (card != null) {
                                    android.widget.ImageView ivIcon = card.findViewById(R.id.iv_bowel_icon);
                                    if (ivIcon != null) {
                                        if (latest != null) {
                                            ivIcon.setImageResource(getBristolIconRes(latest.getBristolType()));
                                        } else {
                                            ivIcon.setImageResource(R.drawable.ic_hero_bowel);
                                        }
                                    }
                                }
                            }
                        });
                    } catch (Exception ignored) {}
                }).start();
            } else {
                final Context ctx = getContext();
                if (ctx == null) return;
                new Thread(() -> {
                    try {
                        com.cz.fitnessdiary.database.AppDatabase db =
                                com.cz.fitnessdiary.database.AppDatabase.getInstance(ctx);
                        com.cz.fitnessdiary.database.entity.BowelMovement latest =
                                db.bowelMovementDao().getLatestSync();
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (isAdded() && binding != null) {
                                if (latest != null) {
                                    String typeStr = "类型" + latest.getBristolType();
                                    String colorStr = latest.getColor() != null ? latest.getColor() : "";
                                    String desc = typeStr + " " + colorStr;
                                    setTextIfExists(R.id.tv_bowel_update, getUpdateText(latest.getTimestamp()));
                                    setTextIfExists(R.id.tv_bowel_summary, "上次: " + desc.trim());
                                    View card = cachedCards.get(CARD_BOWEL);
                                    if (card != null) {
                                        android.widget.ImageView ivIcon = card.findViewById(R.id.iv_bowel_icon);
                                        if (ivIcon != null) {
                                            ivIcon.setImageResource(getBristolIconRes(latest.getBristolType()));
                                        }
                                    }
                                } else {
                                    setTextIfExists(R.id.tv_bowel_update, "暂无更新");
                                    setTextIfExists(R.id.tv_bowel_summary, "点击查看便便明细");
                                    View card = cachedCards.get(CARD_BOWEL);
                                    if (card != null) {
                                        android.widget.ImageView ivIcon = card.findViewById(R.id.iv_bowel_icon);
                                        if (ivIcon != null) {
                                            ivIcon.setImageResource(R.drawable.ic_hero_bowel);
                                        }
                                    }
                                }
                                setTextIfExists(R.id.tv_bowel_value, "--");
                                View card = cachedCards.get(CARD_BOWEL);
                                if (card != null) {
                                    android.widget.ProgressBar p = card.findViewById(R.id.progress_bowel);
                                    if (p != null) p.setProgress(0);
                                }
                            }
                        });
                    } catch (Exception ignored) {}
                }).start();
            }
        });

        // 经期卡片观察
        homeDashboardViewModel.getCurrentCycleDay().observe(getViewLifecycleOwner(), day -> {
            int d = day == null ? 0 : day;
            setTextIfExists(R.id.tv_menstrual_value, d > 0 ? String.valueOf(d) : "--");
            View card = cachedCards.get(CARD_MENSTRUAL);
            if (card != null) {
                android.widget.ProgressBar p =
                        card.findViewById(R.id.progress_menstrual);
                if (p != null) p.setProgress(d > 0 ? Math.min(d * 100 / 28, 100) : 0);
            }
            updateTopTagsValues();
        });
        homeDashboardViewModel.getMenstrualSummary().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null) {
                setTextIfExists(R.id.tv_menstrual_summary, summary);
            }
        });
        homeDashboardViewModel.getSelectedDateLatestMenstrualTime().observe(getViewLifecycleOwner(), ts -> {
            if (ts != null && ts > 0) {
                setTextIfExists(R.id.tv_menstrual_update, getSelectedDateUpdateText(ts));
            } else {
                new Thread(() -> {
                    try {
                        Context context = getContext();
                        if (context == null) return;
                        com.cz.fitnessdiary.database.AppDatabase db =
                                com.cz.fitnessdiary.database.AppDatabase.getInstance(context);
                        com.cz.fitnessdiary.database.entity.MenstrualCycle latest =
                                db.menstrualCycleDao().getLatestSync();
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (isAdded() && binding != null) {
                                if (latest != null) {
                                    setTextIfExists(R.id.tv_menstrual_update, getUpdateText(latest.getStartDate()));
                                } else {
                                    setTextIfExists(R.id.tv_menstrual_update, "暂无更新");
                                    setTextIfExists(R.id.tv_menstrual_summary, "点击查看经期明细");
                                }
                            }
                        });
                    } catch (Exception ignored) {}
                }).start();
            }
        });

        // 步数卡片观察
        homeDashboardViewModel.getTodayStep().observe(getViewLifecycleOwner(), step -> {
            if (step != null && step.getSteps() > 0) {
                int steps = step.getSteps();
                setTextIfExists(R.id.tv_step_value, String.valueOf(steps));
                setTextIfExists(R.id.tv_step_update, getSelectedDateUpdateText(step.getCreateTime()));
                int target = homeDashboardViewModel.getStepTarget();
                int pct = target > 0 ? Math.min(steps * 100 / target, 100) : 0;
                int cal = (int) (steps * 0.04);
                setTextIfExists(R.id.tv_step_summary,
                        "≈" + UnitUtils.formatEnergy(cal, requireContext()) + " " + UnitUtils.getEnergyUnitSymbol(requireContext()) + " | 目标" + target + "步");
                View card = cachedCards.get(CARD_STEP);
                if (card != null) {
                    android.widget.ProgressBar p = card.findViewById(R.id.progress_step);
                    if (p != null) p.setProgress(pct);
                }
            } else {
                setTextIfExists(R.id.tv_step_value, "0");
                setTextIfExists(R.id.tv_step_update, "暂无更新");
                int target = homeDashboardViewModel.getStepTarget();
                setTextIfExists(R.id.tv_step_summary, "目标 " + target + " 步");
                View card = cachedCards.get(CARD_STEP);
                if (card != null) {
                    android.widget.ProgressBar p = card.findViewById(R.id.progress_step);
                    if (p != null) p.setProgress(0);
                }
            }
            updateTopTagsValues();
        });

        // 情绪卡片观察
        homeDashboardViewModel.getTodayMood().observe(getViewLifecycleOwner(), mood -> {
            if (mood != null && mood.getMoodCode() != null) {
                String emoji = MoodPickerBottomSheet.getMoodEmoji(mood.getMoodCode());
                String summary = MoodPickerBottomSheet.getMoodSummary(mood.getMoodCode());
                setTextIfExists(R.id.tv_mood_emoji, emoji);
                setTextIfExists(R.id.tv_mood_summary, summary);
                setTextIfExists(R.id.tv_mood_update, getSelectedDateUpdateText(mood.getCreateTime()));
            } else {
                setTextIfExists(R.id.tv_mood_emoji, "—");
                setTextIfExists(R.id.tv_mood_summary, "记录每日心情");
                setTextIfExists(R.id.tv_mood_update, "暂无更新");
            }
            updateTopTagsValues();
        });
    }

    // ── 顶部 3 高亮轻量化标签管理 ──

    private void loadTopTagsConfig() {
        if (getContext() == null) return;
        android.content.SharedPreferences sp = requireContext().getSharedPreferences("checkin_config", android.content.Context.MODE_PRIVATE);
        tag1Type = sp.getInt("tag_1_type", TAG_TYPE_CAL_REMAIN);
        tag2Type = sp.getInt("tag_2_type", TAG_TYPE_SPORT_BURN);
        tag3Type = sp.getInt("tag_3_type", TAG_TYPE_WATER);
    }

    private void saveTopTagsConfig(int index, int type) {
        if (getContext() == null) return;
        android.content.SharedPreferences sp = requireContext().getSharedPreferences("checkin_config", android.content.Context.MODE_PRIVATE);
        sp.edit().putInt("tag_" + index + "_type", type).apply();
    }

    private void showSelectTagDialog(int index) {
        String[] items = {"剩余热量", "运动消耗", "饮水量", "今日步数", "睡眠时长", "今日心情", "习惯打卡", "当前体重", "便便记录", "药物记录", "生理期天数"};
        int currentType = (index == 1) ? tag1Type : ((index == 2) ? tag2Type : tag3Type);
        int currentSelected = 0;
        if (currentType >= 0 && currentType < items.length) {
            currentSelected = currentType;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择要展示的指标")
                .setSingleChoiceItems(items, currentSelected, (dialog, which) -> {
                    saveTopTagsConfig(index, which);
                    if (index == 1) tag1Type = which;
                    else if (index == 2) tag2Type = which;
                    else tag3Type = which;
                    dialog.dismiss();
                    updateTopTagsValues();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateTopTagsValues() {
        if (binding == null || getContext() == null) return;
        updateSingleTag(1, tag1Type);
        updateSingleTag(2, tag2Type);
        updateSingleTag(3, tag3Type);
    }

    private void updateSingleTag(int index, int type) {
        TextView tvValue = null;
        TextView tvLabel = null;
        if (index == 1) {
            tvValue = binding.tvTopTag1Value;
            tvLabel = binding.tvTopTag1Label;
        } else if (index == 2) {
            tvValue = binding.tvTopTag2Value;
            tvLabel = binding.tvTopTag2Label;
        } else if (index == 3) {
            tvValue = binding.tvTopTag3Value;
            tvLabel = binding.tvTopTag3Label;
        }
        if (tvValue == null || tvLabel == null) return;

        String valueText = "—";
        String labelText = "";

        switch (type) {
            case TAG_TYPE_CAL_REMAIN:
                labelText = "剩余热量";
                Integer consumed = dietViewModel.getTodayTotalCalories().getValue();
                User user = dietViewModel.getCurrentUser().getValue();
                int target = (user != null && user.getDailyCalorieTarget() > 0) ? user.getDailyCalorieTarget() : 2000;
                int cons = consumed != null ? consumed : 0;
                int remaining = target - cons + exerciseCalories;
                valueText = UnitUtils.formatEnergy(remaining, requireContext()) + " " + UnitUtils.getEnergyUnitSymbol(requireContext());
                break;
            case TAG_TYPE_SPORT_BURN:
                labelText = "运动消耗";
                valueText = UnitUtils.formatEnergy(exerciseCalories, requireContext()) + " " + UnitUtils.getEnergyUnitSymbol(requireContext());
                break;
            case TAG_TYPE_WATER:
                labelText = "饮水量";
                valueText = currentWaterTotal + " ml";
                break;
            case TAG_TYPE_STEPS:
                labelText = "今日步数";
                com.cz.fitnessdiary.database.entity.StepRecord step = homeDashboardViewModel.getTodayStep().getValue();
                int s = step != null ? step.getSteps() : 0;
                valueText = s + " 步";
                break;
            case TAG_TYPE_SLEEP:
                labelText = "睡眠时长";
                java.util.List<com.cz.fitnessdiary.database.entity.SleepRecord> sleepRecords = checkInViewModel.getSelectedDaySleepRecords().getValue();
                if (sleepRecords != null && !sleepRecords.isEmpty()) {
                    float totalHours = 0;
                    for (com.cz.fitnessdiary.database.entity.SleepRecord r : sleepRecords) {
                        if (r.getEndTime() > r.getStartTime()) {
                            totalHours += (r.getEndTime() - r.getStartTime()) / (1000f * 60 * 60);
                        }
                    }
                    valueText = String.format(java.util.Locale.getDefault(), "%.1f h", totalHours);
                } else {
                    valueText = "—";
                }
                break;
            case TAG_TYPE_MOOD:
                labelText = "今日心情";
                com.cz.fitnessdiary.database.entity.MoodRecord mood = homeDashboardViewModel.getTodayMood().getValue();
                if (mood != null && mood.getMoodCode() != null) {
                    valueText = MoodPickerBottomSheet.getMoodEmoji(mood.getMoodCode()) + " " + MoodPickerBottomSheet.getMoodSummary(mood.getMoodCode());
                } else {
                    valueText = "待记录";
                }
                break;
            case TAG_TYPE_HABIT:
                labelText = "习惯打卡";
                int totalHabit = habitItems.size();
                int doneHabit = 0;
                for (HabitItem item : habitItems) {
                    HabitRecord rec = habitRecords.get(item.getId());
                    if (rec != null && rec.isCompleted()) {
                        doneHabit++;
                    }
                }
                valueText = doneHabit + "/" + totalHabit;
                break;
            case TAG_TYPE_WEIGHT:
                labelText = "当前体重";
                com.cz.fitnessdiary.database.entity.WeightRecord weight = homeDashboardViewModel.getSelectedDateLatestWeight().getValue();
                if (weight != null && weight.getWeight() > 0) {
                    valueText = UnitUtils.formatWeight(weight.getWeight(), requireContext()) + " " + UnitUtils.getWeightUnitSymbol(requireContext());
                } else {
                    valueText = "—";
                }
                break;
            case TAG_TYPE_BOWEL:
                labelText = "便便记录";
                Integer bowelCount = homeDashboardViewModel.getTodayBowelCount().getValue();
                int bc = bowelCount != null ? bowelCount : 0;
                valueText = bc + " 次";
                break;
            case TAG_TYPE_MEDICATION:
                labelText = "药物记录";
                Integer taken = homeDashboardViewModel.getTodayMedicationTakenCount().getValue();
                Integer totalMed = homeDashboardViewModel.getTodayMedicationTotal().getValue();
                int tk = taken != null ? taken : 0;
                int tot = totalMed != null ? totalMed : 0;
                valueText = tk + "/" + tot;
                break;
            case TAG_TYPE_MENSTRUAL:
                labelText = "生理期天数";
                Integer cycleDay = homeDashboardViewModel.getCurrentCycleDay().getValue();
                if (cycleDay != null && cycleDay > 0) {
                    valueText = "第 " + cycleDay + " 天";
                } else {
                    valueText = "—";
                }
                break;
        }

        tvValue.setText(valueText);
        tvLabel.setText(labelText);
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
        ProgressBar pWater = binding.getRoot().findViewById(R.id.progress_water);
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
            ProgressBar pHabit = binding.getRoot().findViewById(R.id.progress_habit);
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

        updateSummaryCard();
        updateTopTagsValues();

        // Compute health score on background thread using the new 5-dimension breakdown
        // (same calculation as showOverallProgressDetails, ensuring ring and popup match)
        new Thread(() -> {
            try {
                Context context = getContext();
                if (context == null) return;
                Long selectedDate = checkInViewModel.getSelectedDate().getValue();
                long date = selectedDate != null ? selectedDate : DateUtils.getTodayStartTimestamp();

                // Use HealthAggregationRepository for the cross-module snapshot
                HealthAggregationRepository repo = new HealthAggregationRepository(
                        (android.app.Application) context.getApplicationContext());
                DailyHealthSnapshot snapshot = repo.getDateSnapshot(date);

                // Build user profile
                HealthScoreCalculator.UserProfile profile = new HealthScoreCalculator.UserProfile();
                try {
                    AppDatabase db = AppDatabase.getInstance(context);
                    User u = db.userDao().getUserSync();
                    if (u != null) {
                        if (u.getDailyCalorieTarget() > 0) profile.dailyCalorieTarget = u.getDailyCalorieTarget();
                        if (u.getDailyWaterTarget() > 0) profile.waterTargetMl = u.getDailyWaterTarget();
                        profile.weightKg = u.getWeight();
                        profile.heightCm = u.getHeight();
                        profile.age = u.getAge();
                        int goalType = u.getGoalType();
                        if (goalType == 0) profile.goalType = "lose";
                        else if (goalType == 1) profile.goalType = "gain";
                        else profile.goalType = "maintain";
                        if (u.getTargetProtein() > 0) profile.targetProteinGrams = u.getTargetProtein();
                        if (u.getTargetCarbs() > 0) profile.targetCarbsGrams = u.getTargetCarbs();
                    }
                    // 读取目标运动时长 (per-date)
                    SharedPreferences sp = context.getSharedPreferences("fitness_diary_prefs", Context.MODE_PRIVATE);
                    profile.targetExerciseMinutes = sp.getInt("target_minutes_" + date, 0);
                    // 步数目标：优先当日独立值，无则回落全局默认
                    profile.stepTarget = sp.getInt("step_target_" + date, sp.getInt("step_target", 8000));
                    // 饮水目标：优先当日独立值，无则回落全局
                    int dateWater = sp.getInt("water_target_" + date, -1);
                    if (dateWater > 0) profile.waterTargetMl = dateWater;
                    SharedPreferences scorePrefs = context.getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE);
                    // 体重目标：优先当日独立值，无则回落全局
                    profile.customTargetWeight = scorePrefs.getFloat("target_weight_kg_" + date,
                            scorePrefs.getFloat("target_weight_kg", -1f));
                } catch (Exception ignored) {}

                HealthScoreBreakdown breakdown = HealthScoreCalculator.calculateBreakdown(snapshot, profile);
                HealthScoreCalculator.saveTodayScore(context, breakdown.totalScore);

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (isAdded() && binding != null) {
                        binding.progressTotalCircle.setProgress(breakdown.totalScore);
                        binding.tvProgressPercent.setText(breakdown.totalScore + "分");

                        // 动态更新今日健康寄语
                        updateHeaderTip(snapshot, breakdown.totalScore);
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private static String bar(float ratio) {
        int filled = Math.round(ratio * 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < filled ? '█' : '░');
        return sb.toString();
    }

    private void showOverallProgressDetails() {
        new Thread(() -> {
            Context ctx = getContext();
            if (ctx == null) return;

            // Fix 1.1: Use selected date, not always today
            Long selectedTs = checkInViewModel.getSelectedDate().getValue();
            long date = selectedTs != null ? selectedTs : DateUtils.getTodayStartTimestamp();

            // Use HealthAggregationRepository for the cross-module snapshot
            HealthAggregationRepository repo = new HealthAggregationRepository(
                    (android.app.Application) ctx.getApplicationContext());
            DailyHealthSnapshot snapshot = repo.getDateSnapshot(date);

            // Build user profile
            HealthScoreCalculator.UserProfile profile = new HealthScoreCalculator.UserProfile();
            try {
                com.cz.fitnessdiary.database.AppDatabase db =
                        com.cz.fitnessdiary.database.AppDatabase.getInstance(ctx);
                com.cz.fitnessdiary.database.entity.User user = db.userDao().getUserSync();
                if (user != null) {
                    if (user.getDailyCalorieTarget() > 0) profile.dailyCalorieTarget = user.getDailyCalorieTarget();
                    // 饮水目标已在下方统一处理
                    profile.weightKg = user.getWeight();
                    profile.heightCm = user.getHeight();
                    profile.age = user.getAge();
                    profile.gender = user.getGender() == 1 ? "male" : "female";
                    int goalType = user.getGoalType();
                    if (goalType == 0) profile.goalType = "lose";
                    else if (goalType == 1) profile.goalType = "gain";
                    else profile.goalType = "maintain";
                    if (user.getTargetProtein() > 0) profile.targetProteinGrams = user.getTargetProtein();
                    if (user.getTargetCarbs() > 0) profile.targetCarbsGrams = user.getTargetCarbs();
                }
                // 读取目标运动时长 (per-date)
                SharedPreferences sp = ctx.getSharedPreferences("fitness_diary_prefs", Context.MODE_PRIVATE);
                profile.targetExerciseMinutes = sp.getInt("target_minutes_" + date, 0);
                profile.stepTarget = sp.getInt("step_target_" + date, sp.getInt("step_target", 8000));
                int dateWater = sp.getInt("water_target_" + date, -1);
                if (dateWater > 0) profile.waterTargetMl = dateWater;
                else if (user != null && user.getDailyWaterTarget() > 0) profile.waterTargetMl = user.getDailyWaterTarget();
                SharedPreferences scorePrefs = ctx.getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE);
                profile.customTargetWeight = scorePrefs.getFloat("target_weight_kg_" + date,
                        scorePrefs.getFloat("target_weight_kg", -1f));
            } catch (Exception ignored) {}

            HealthScoreBreakdown breakdown = HealthScoreCalculator.calculateBreakdown(snapshot, profile);

            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || binding == null) return;

                String energyUnitSym = UnitUtils.getEnergyUnitSymbol(requireContext());
                String weightUnitSym = UnitUtils.getWeightUnitSymbol(requireContext());

                StringBuilder detail = new StringBuilder();

                // ── 训练 ──
                float exRatio = breakdown.exerciseScore / 25f;
                int totalCal = snapshot.exerciseCalories + snapshot.stepCalories;
                int calThreshold = profile.targetExerciseMinutes > 0 && profile.weightKg > 0
                        ? (int) (5.0 * profile.weightKg * (profile.targetExerciseMinutes / 60.0)) : 300;
                String calFlag = totalCal > calThreshold ? "✅" : "⚠️";
                String exStatus = snapshot.completedPlans > 0
                        ? snapshot.completedPlans + "/" + snapshot.totalPlans + "项 · 消耗" + totalCal + "kcal " + calFlag
                        : "无训练";
                detail.append("🏋️ 训练  ").append(bar(exRatio)).append(" ").append(breakdown.exerciseScore).append("/25  ").append(exStatus).append("\n");

                // ── 饮食 ──
                float dietRatio = snapshot.dietCalories > 0 ? (float) breakdown.dietScore / 25f : 0;
                String dietStatus = snapshot.dietCalories > 0
                        ? UnitUtils.formatEnergy(snapshot.dietCalories, requireContext()) + energyUnitSym + " · 🥩" + snapshot.todayProtein + "g · 🍚" + snapshot.todayCarbs + "g" + (snapshot.todayFat > 0 ? " · 🧈" + snapshot.todayFat + "g" : "")
                        : "无记录";
                detail.append("🥗 饮食  ").append(bar(dietRatio)).append(" ").append(breakdown.dietScore).append("/25  ").append(dietStatus).append("\n");

                // ── 习惯 ──
                float habitRatio = (float) breakdown.habitsScore / 20f;
                StringBuilder habitStatus = new StringBuilder();
                if (snapshot.sleepHours >= 7) habitStatus.append("😴" + (int) snapshot.sleepHours + "h ✅ ");
                else if (snapshot.sleepHours > 0) habitStatus.append("😴" + String.format(java.util.Locale.getDefault(), "%.1f", snapshot.sleepHours) + "h ⚠️ ");
                else habitStatus.append("😴-- ");
                if (snapshot.waterMl >= profile.waterTargetMl) habitStatus.append("💧✅ ");
                else if (snapshot.waterMl > 0) habitStatus.append("💧⚠️ ");
                else habitStatus.append("💧-- ");
                int stepGoal = profile.stepTarget > 0 ? profile.stepTarget : 8000;
                if (snapshot.steps >= stepGoal) habitStatus.append("👣" + snapshot.steps + " ✅");
                else if (snapshot.steps > 0) habitStatus.append("👣" + snapshot.steps + " ⚠️");
                else habitStatus.append("👣--");
                detail.append("💧 习惯  ").append(bar(habitRatio)).append(" ").append(breakdown.habitsScore).append("/20  ").append(habitStatus).append("\n");

                // ── 身体 ──
                float bodyRatio = snapshot.weightKg > 0 ? (float) breakdown.bodyMetricsScore / 15f : 0;
                String bodyStatus;
                if (snapshot.weightKg > 0 && profile.goalType != null) {
                    float targetWeight = HealthScoreCalculator.computeTargetWeight(profile);
                    boolean movingTowardTarget = ("lose".equals(profile.goalType) && snapshot.weightTrend > 0) ||
                        ("gain".equals(profile.goalType) && snapshot.weightTrend < 0) ||
                        ("maintain".equals(profile.goalType) && Math.abs(snapshot.weightTrend) < 0.5f);
                    String trend = movingTowardTarget ? "✅" : "⚠️";
                    bodyStatus = UnitUtils.formatWeight(snapshot.weightKg, requireContext()) + weightUnitSym + " → " + UnitUtils.formatWeight(targetWeight, requireContext()) + weightUnitSym + " " + trend;
                } else {
                    bodyStatus = "暂无记录";
                }
                detail.append("📏 身体  ").append(bar(bodyRatio)).append(" ").append(breakdown.bodyMetricsScore).append("/15  ").append(bodyStatus).append("\n");

                // ── 坚持 ──
                float conRatio = (float) breakdown.consistencyScore / 15f;
                String conStatus = snapshot.activeDays7 >= 7 ? "全勤" + snapshot.activeDays7 + "/7天"
                        : snapshot.activeDays7 > 0 ? "活跃" + snapshot.activeDays7 + "/7天"
                        : "暂无";
                detail.append("🔥 坚持  ").append(bar(conRatio)).append(" ").append(breakdown.consistencyScore).append("/15  ").append(conStatus).append("\n\n");

                detail.append("总分：").append(breakdown.totalScore).append("/100");

                String message = detail.toString();

                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("健康评分明细")
                    .setMessage(message)
                    .setPositiveButton("确定", null)
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
        // 每日任务卡片已废弃，无需更新其子字段
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
        computeAndDisplayExerciseCalories();
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
            android.widget.ProgressBar p =
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

        // 通过 Activity 的 FragmentManager 直接找到顶层 NavHostFragment，
        // 避免 ViewPager2 内部 NavHostFragment.findNavController(this) 的缓存脱链问题
        try {
            androidx.navigation.fragment.NavHostFragment navHostFragment =
                    (androidx.navigation.fragment.NavHostFragment) requireActivity()
                            .getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            if (navHostFragment != null) {
                navHostFragment.getNavController().navigate(destination, args, navOptions);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showHeatmapDialog() {
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
                android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_heatmap, null);
                com.cz.fitnessdiary.ui.widget.StreakCalendarView calView = dialogView.findViewById(R.id.calendar_view);
                calView.setDayLevels(levels);

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("打卡热力图")
                        .setView(dialogView)
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

        // 首页中的挑战卡片已永久隐藏，所有状态都在FAB弹窗内展示
        card.setVisibility(View.GONE);
        if (type == null || !"ACTIVE".equals(status)) {
            return;
        }
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
            android.view.View activeVal = getLayoutInflater().inflate(R.layout.dialog_challenge_active, null);
            
            android.widget.TextView tvActiveEmoji = activeVal.findViewById(R.id.tv_active_emoji);
            android.widget.TextView tvActiveTitle = activeVal.findViewById(R.id.tv_active_title);
            android.widget.TextView tvActiveDesc = activeVal.findViewById(R.id.tv_active_desc);
            android.widget.TextView tvActiveProgressTxt = activeVal.findViewById(R.id.tv_active_progress_txt);
            android.widget.ProgressBar progressActiveChallenge = activeVal.findViewById(R.id.progress_active_challenge);
            com.google.android.material.card.MaterialCardView ivChallengeEmojiBg = activeVal.findViewById(R.id.iv_challenge_emoji_bg);
            android.widget.LinearLayout layoutFailIndicators = activeVal.findViewById(R.id.layout_fail_indicators);
            com.google.android.material.button.MaterialButton btnActiveContinue = activeVal.findViewById(R.id.btn_active_continue);
            com.google.android.material.button.MaterialButton btnActiveAbandon = activeVal.findViewById(R.id.btn_active_abandon);

            int days = com.cz.fitnessdiary.utils.ChallengeManager.getProgressDays(getContext());
            int fails = com.cz.fitnessdiary.utils.ChallengeManager.getFailDays(getContext());

            tvActiveEmoji.setText(com.cz.fitnessdiary.utils.ChallengeManager.getTypeEmoji(active));
            tvActiveTitle.setText(com.cz.fitnessdiary.utils.ChallengeManager.getTypeName(active));
            tvActiveDesc.setText(com.cz.fitnessdiary.utils.ChallengeManager.getTypeDesc(active));
            tvActiveProgressTxt.setText("第 " + Math.min(days, 21) + " / 21 天");
            progressActiveChallenge.setProgress(Math.min(days, 21));

            // Elegant background tint for active challenge emoji wrapper
            int bgColor = 0xFFFFF0EB;
            if (com.cz.fitnessdiary.utils.ChallengeManager.TYPE_FAT_LOSS.equals(active)) bgColor = 0xFFFFF0F0;
            else if (com.cz.fitnessdiary.utils.ChallengeManager.TYPE_MUSCLE_GAIN.equals(active)) bgColor = 0xFFEBF5FF;
            else if (com.cz.fitnessdiary.utils.ChallengeManager.TYPE_EARLY_SLEEP.equals(active)) bgColor = 0xFFF3EBFF;
            else if (com.cz.fitnessdiary.utils.ChallengeManager.TYPE_WATER_MASTER.equals(active)) bgColor = 0xFFE6F9FF;
            ivChallengeEmojiBg.setCardBackgroundColor(bgColor);

            // Failure indicators as visual circular dots
            int maxFails = com.cz.fitnessdiary.utils.ChallengeManager.TYPE_MUSCLE_GAIN.equals(active) ? 2 : 3;
            layoutFailIndicators.removeAllViews();
            int dotSize = dp(14);
            int margin = dp(4);
            for (int i = 0; i < maxFails; i++) {
                android.widget.ImageView dot = new android.widget.ImageView(requireContext());
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(dotSize, dotSize);
                lp.setMargins(margin, 0, margin, 0);
                dot.setLayoutParams(lp);
                if (i < fails) {
                    dot.setImageResource(R.drawable.circle_red);
                } else {
                    dot.setImageResource(R.drawable.circle_unchecked);
                }
                layoutFailIndicators.addView(dot);
            }

            androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setView(activeVal)
                    .create();

            btnActiveContinue.setOnClickListener(v -> dialog.dismiss());
            btnActiveAbandon.setOnClickListener(v -> {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("确认放弃挑战")
                        .setMessage("确定要放弃当前的21天挑战吗？一旦放弃，进度将全部清零。")
                        .setPositiveButton("确定放弃", (d, w) -> {
                            com.cz.fitnessdiary.utils.ChallengeManager.reset(getContext());
                            refreshChallengeCard();
                            Toast.makeText(getContext(), "已放弃挑战", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .setNegativeButton("继续坚持", null)
                        .show();
            });

            dialog.show();
            return;
        }

        if (active != null && ("COMPLETED".equals(status) || "FAILED".equals(status))) {
            String result = "COMPLETED".equals(status) ? "恭喜你！已圆满完成21天挑战！" : "挑战失败，别气馁，下次继续加油！";
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
        android.view.View pickerVal = getLayoutInflater().inflate(R.layout.dialog_challenge_picker, null);
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setView(pickerVal)
                .setTitle("选择21天挑战")
                .setNegativeButton("取消", null)
                .create();

        pickerVal.findViewById(R.id.card_challenge_fat_loss).setOnClickListener(v -> {
            com.cz.fitnessdiary.utils.ChallengeManager.start(getContext(), com.cz.fitnessdiary.utils.ChallengeManager.TYPE_FAT_LOSS);
            refreshChallengeCard();
            Toast.makeText(getContext(), "挑战已开启！", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        pickerVal.findViewById(R.id.card_challenge_muscle_gain).setOnClickListener(v -> {
            com.cz.fitnessdiary.utils.ChallengeManager.start(getContext(), com.cz.fitnessdiary.utils.ChallengeManager.TYPE_MUSCLE_GAIN);
            refreshChallengeCard();
            Toast.makeText(getContext(), "挑战已开启！", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        pickerVal.findViewById(R.id.card_challenge_early_sleep).setOnClickListener(v -> {
            com.cz.fitnessdiary.utils.ChallengeManager.start(getContext(), com.cz.fitnessdiary.utils.ChallengeManager.TYPE_EARLY_SLEEP);
            refreshChallengeCard();
            Toast.makeText(getContext(), "挑战已开启！", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        pickerVal.findViewById(R.id.card_challenge_water_master).setOnClickListener(v -> {
            com.cz.fitnessdiary.utils.ChallengeManager.start(getContext(), com.cz.fitnessdiary.utils.ChallengeManager.TYPE_WATER_MASTER);
            refreshChallengeCard();
            Toast.makeText(getContext(), "挑战已开启！", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
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
            } else if (CARD_STEP.equals(id)) {
                name = "步数记录";
                visible = isCardEnabled(KEY_SHOW_STEP, true);
            } else if (CARD_MOOD.equals(id)) {
                name = "情绪记录";
                visible = isCardEnabled(KEY_SHOW_MOOD, true);
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
                        else if (CARD_STEP.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_STEP, cfg.visible);
                        else if (CARD_MOOD.equals(cfg.id))
                            editor.putBoolean(KEY_SHOW_MOOD, cfg.visible);
                    }
                    editor.putString(KEY_SMALL_ORDER, String.join(",", newOrder)).apply();
                    loadCardConfig();
                    applyCardConfig();
                }).setNegativeButton("取消", null).show();
    }

    private void loadCardConfig() {
        // 合并至 applyBigCardsConfig
    }

    private void applyCardConfig() {
        // 合并至 applyBigCardsConfig
    }

    private View getCardById(String id) {
        return cachedCards.get(id);
    }

    private boolean isCardEnabled(String key, boolean def) {
        boolean defaultVal = def;
        if (KEY_SHOW_SLEEP.equals(key) || KEY_SHOW_WEIGHT.equals(key)) {
            defaultVal = true;
        } else if (KEY_SHOW_WATER.equals(key) || KEY_SHOW_HABIT.equals(key) || 
                   KEY_SHOW_MEDICATION.equals(key) || KEY_SHOW_MEASUREMENT.equals(key) || 
                   KEY_SHOW_BOWEL.equals(key) || KEY_SHOW_MENSTRUAL.equals(key) || 
                   KEY_SHOW_STEP.equals(key) || KEY_SHOW_MOOD.equals(key)) {
            defaultVal = false;
        }
        return requireContext().getSharedPreferences(PREF_HOME_CARDS, Context.MODE_PRIVATE).getBoolean(key, defaultVal);
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

    // ── Diet macros display ──

    private void updateDietMacros() {
        Double carbs = dietViewModel.getTodayTotalCarbs().getValue();
        Double protein = dietViewModel.getTodayTotalProtein().getValue();
        Double fat = dietViewModel.getTodayTotalFat().getValue();
        User user = dietViewModel.getCurrentUser().getValue();

        int carbsTarget = (user != null && user.getTargetCarbs() > 0) ? user.getTargetCarbs() : 250;
        int proteinTarget = (user != null && user.getTargetProtein() > 0) ? user.getTargetProtein() : 60;
        int fatTarget = (user != null && user.getTargetFat() > 0) ? user.getTargetFat() : 60;

        double carbsVal = carbs != null ? carbs : 0;
        double proteinVal = protein != null ? protein : 0;
        double fatVal = fat != null ? fat : 0;

        TextView tvCarbs = binding.getRoot().findViewById(R.id.tv_diet_carbs);
        if (tvCarbs != null) {
            tvCarbs.setText((int) carbsVal + "g / " + carbsTarget + "g");
        }
        ProgressBar pCarbs = binding.getRoot().findViewById(R.id.progress_diet_carbs);
        if (pCarbs != null) {
            pCarbs.setProgress(Math.min((int) (carbsVal * 100 / carbsTarget), 100));
        }

        TextView tvProtein = binding.getRoot().findViewById(R.id.tv_diet_protein);
        if (tvProtein != null) {
            tvProtein.setText((int) proteinVal + "g / " + proteinTarget + "g");
        }
        ProgressBar pProtein = binding.getRoot().findViewById(R.id.progress_diet_protein);
        if (pProtein != null) {
            pProtein.setProgress(Math.min((int) (proteinVal * 100 / proteinTarget), 100));
        }

        TextView tvFat = binding.getRoot().findViewById(R.id.tv_diet_fat);
        if (tvFat != null) {
            tvFat.setText((int) fatVal + "g / " + fatTarget + "g");
        }
        ProgressBar pFat = binding.getRoot().findViewById(R.id.progress_diet_fat);
        if (pFat != null) {
            pFat.setProgress(Math.min((int) (fatVal * 100 / fatTarget), 100));
        }
    }

    // ── Exercise calorie calculation ──

    private void computeAndDisplayExerciseCalories() {
        new Thread(() -> {
            int totalCal = 0;
            java.util.HashSet<Integer> donePlanIds = new java.util.HashSet<>();
            float weightKg = 70f;
            if (!currentPlans.isEmpty()) {
                for (DailyLog log : currentLogs) {
                    if (log.isCompleted()) donePlanIds.add(log.getPlanId());
                }
                try {
                    com.cz.fitnessdiary.database.AppDatabase db =
                            com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
                    com.cz.fitnessdiary.database.entity.WeightRecord latestW =
                            db.weightRecordDao().getLatestRecordSync();
                    if (latestW != null && latestW.getWeight() > 0) weightKg = latestW.getWeight();
                    else {
                        com.cz.fitnessdiary.database.entity.User u = db.userDao().getUserSync();
                        if (u != null && u.getWeight() > 0) weightKg = u.getWeight();
                    }
                } catch (Exception ignored) {}

                for (TrainingPlan plan : currentPlans) {
                    if (!donePlanIds.contains(plan.getPlanId())) continue;
                    int durationSec = 0;
                    for (DailyLog log : currentLogs) {
                        if (log.getPlanId() == plan.getPlanId() && log.isCompleted()) {
                            durationSec = log.getDuration() > 0 ? log.getDuration() : plan.getDuration();
                            break;
                        }
                    }
                    if (durationSec <= 0) durationSec = ExerciseMetTable.resolveDuration(0, plan.getDuration(), requireContext());
                    double met = ExerciseMetTable.getMetForExercise(plan.getName(), plan.getCategory());
                    double cal = met * weightKg * (durationSec / 3600.0);
                    totalCal += (int) cal;
                }
            }
            // Step calories — 使用选中日期而非总是今天
            Long selectedDate = checkInViewModel.getSelectedDate().getValue();
            long viewDate = selectedDate != null ? selectedDate
                    : com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
            com.cz.fitnessdiary.database.entity.StepRecord step = null;
            try {
                com.cz.fitnessdiary.database.AppDatabase db =
                        com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
                step = db.stepRecordDao().getByDateSync(viewDate);
            } catch (Exception ignored) {}
            int stepCal = 0;
            if (step != null && step.getSteps() > 0) {
                stepCal = (int) (step.getSteps() * 0.04);
                totalCal += stepCal;
            }

            int workoutCal = totalCal - stepCal;

            // 如果用户设了当天训练总时长，按加权平均MET重算运动消耗（与日历弹窗口径一致）
            int targetMin = requireContext().getSharedPreferences("fitness_diary_prefs", android.content.Context.MODE_PRIVATE)
                    .getInt("target_minutes_" + viewDate, 0);
            if (targetMin > 0 && workoutCal > 0) {
                // 重新计算累计时长，用于加权平均
                double totalDurHoursForWeighted = 0;
                double totalWeightedMet = 0;
                for (TrainingPlan plan : currentPlans) {
                    if (!donePlanIds.contains(plan.getPlanId())) continue;
                    int durSec = 0;
                    for (DailyLog log : currentLogs) {
                        if (log.getPlanId() == plan.getPlanId() && log.isCompleted()) {
                            durSec = log.getDuration() > 0 ? log.getDuration() : plan.getDuration();
                            break;
                        }
                    }
                    if (durSec <= 0)
                        durSec = ExerciseMetTable.resolveDuration(0, plan.getDuration(), requireContext());
                    double met = ExerciseMetTable.getMetForExercise(plan.getName(), plan.getCategory());
                    totalWeightedMet += met * durSec;
                    totalDurHoursForWeighted += durSec / 3600.0;
                }
                if (totalDurHoursForWeighted > 0) {
                    double avgMet = totalWeightedMet / (totalDurHoursForWeighted * 3600);
                    workoutCal = (int) (avgMet * weightKg * (targetMin / 60.0));
                    totalCal = workoutCal + stepCal;
                }
            }

            exerciseCalories = totalCal; // 赋值给成员变量以同步更新外露消耗标签
            
            final int finalTotal = totalCal;
            final int finalWorkout = workoutCal;
            final int finalStepCal = stepCal;

            requireActivity().runOnUiThread(() -> {
                String energyUnitSym = UnitUtils.getEnergyUnitSymbol(requireContext());
                setTextIfExists(R.id.tv_sport_calories, UnitUtils.formatEnergy(finalTotal, requireContext()) + " " + energyUnitSym);
                if (finalWorkout > 0 && finalStepCal > 0) {
                    setTextIfExists(R.id.tv_sport_cal_breakdown,
                            "运动" + UnitUtils.formatEnergy(finalWorkout, requireContext()) + " + 步数" + UnitUtils.formatEnergy(finalStepCal, requireContext()));
                } else if (finalWorkout > 0) {
                    setTextIfExists(R.id.tv_sport_cal_breakdown,
                            "运动消耗 " + UnitUtils.formatEnergy(finalWorkout, requireContext()) + " " + energyUnitSym);
                } else if (finalStepCal > 0) {
                    setTextIfExists(R.id.tv_sport_cal_breakdown,
                            "步数消耗 " + UnitUtils.formatEnergy(finalStepCal, requireContext()) + " " + energyUnitSym);
                } else {
                    setTextIfExists(R.id.tv_sport_cal_breakdown, "");
                }
                updateTopTagsValues(); // 同步重绘顶部指标
            });
        }).start();
    }

    // ── Step & mood dialogs ──

    private void showStepInputDialog() {
        android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setHint("输入今日步数");
        com.cz.fitnessdiary.database.entity.StepRecord existing =
                homeDashboardViewModel.getTodayStep().getValue();
        if (existing != null && existing.getSteps() > 0) {
            et.setText(String.valueOf(existing.getSteps()));
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("记录步数")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int steps = Integer.parseInt(et.getText().toString().trim());
                        int source = (existing != null && existing.getSource() == 0) ? 2 : 1;
                        homeDashboardViewModel.setTodaySteps(steps, source);
                        computeAndDisplayExerciseCalories();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(getContext(), "请输入正确数字",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showStepTargetDialog() {
        android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setHint("目标步数");
        et.setText(String.valueOf(homeDashboardViewModel.getStepTarget()));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("设置步数目标")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int target = Integer.parseInt(et.getText().toString().trim());
                        if (target > 0) {
                            homeDashboardViewModel.setStepTarget(target);
                            com.cz.fitnessdiary.database.entity.StepRecord existing =
                                    homeDashboardViewModel.getTodayStep().getValue();
                            if (existing != null) {
                                int pct = Math.min(existing.getSteps() * 100 / target, 100);
                                int stepCalEst = (int) (existing.getSteps() * 0.04);
                                setTextIfExists(R.id.tv_step_summary,
                                        "≈" + UnitUtils.formatEnergy(stepCalEst, requireContext()) + " " + UnitUtils.getEnergyUnitSymbol(requireContext()) + " | 目标" + target + "步");
                                View card = cachedCards.get(CARD_STEP);
                                if (card != null) {
                                    android.widget.ProgressBar p = card.findViewById(R.id.progress_step);
                                    if (p != null) p.setProgress(pct);
                                }
                            } else {
                                setTextIfExists(R.id.tv_step_summary, "目标 " + target + " 步");
                            }
                        }
                    } catch (Exception e) {
                        android.widget.Toast.makeText(getContext(), "请输入正确数字",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMoodPicker() {
        com.cz.fitnessdiary.database.entity.MoodRecord existing =
                homeDashboardViewModel.getTodayMood().getValue();
        String currentCode = existing != null ? existing.getMoodCode() : null;
        MoodPickerBottomSheet sheet = MoodPickerBottomSheet.newInstance(currentCode);
        sheet.setOnMoodSelectedListener(code -> homeDashboardViewModel.setTodayMood(code));
        sheet.show(getParentFragmentManager(), "MOOD_PICKER");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (homeCardsChangeListener != null && getContext() != null) {
            requireContext().getSharedPreferences("home_big_cards_prefs", Context.MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(homeCardsChangeListener);
        }
        if (restTimerManager != null) {
            restTimerManager.cancel();
        }
        lastEnabledCardIds.clear();
        cachedCards.clear();
        binding = null;
    }

    private int getBristolIconRes(int type) {
        return com.cz.fitnessdiary.utils.AnalysisUtils.getBristolIconRes(type);
    }

    private void applyBigCardsConfig() {
        if (binding == null || getContext() == null) return;

        SharedPreferences sp = requireContext().getSharedPreferences("home_cards_prefs", Context.MODE_PRIVATE);
        
        boolean showMissions = sp.getBoolean("show_card_missions", true);
        // [Removed v2.4] top tags permanently hidden
        // binding.layoutTopTags.setVisibility(showMissions ? View.VISIBLE : View.GONE);

        String orderStr = sp.getString("home_cards_order", "missions,sport,diet,water,sleep,habit,medication,weight,measurement,bowel,menstrual,step,mood");
        
        // 自动补齐缺失的卡片ID
        if (orderStr != null) {
            List<String> list = new ArrayList<>(Arrays.asList(orderStr.split(",")));
            boolean changed = false;
            for (String id : Arrays.asList("missions","sport","diet","water","sleep","habit","medication","weight","measurement","bowel","menstrual","step","mood")) {
                if (!list.contains(id)) {
                    list.add(id);
                    changed = true;
                }
            }
            if (changed) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    sb.append(list.get(i));
                    if (i < list.size() - 1) sb.append(",");
                }
                orderStr = sb.toString();
                sp.edit().putString("home_cards_order", orderStr).apply();
            }
        }

        String[] order = orderStr.split(",");

        // 1. 先安全地移除所有的 View
        binding.cardsContainer.removeView(binding.cardRestTimer);
        binding.cardsContainer.removeView(binding.cardSport);
        binding.cardsContainer.removeView(binding.cardDiet);
        binding.cardsContainer.removeView(binding.shellCardWater);
        binding.cardsContainer.removeView(binding.shellCardSleep);
        binding.cardsContainer.removeView(binding.shellCardHabit);
        binding.cardsContainer.removeView(binding.shellCardMedication);
        binding.cardsContainer.removeView(binding.shellCardWeight);
        binding.cardsContainer.removeView(binding.shellCardMeasurement);
        binding.cardsContainer.removeView(binding.shellCardBowel);
        binding.cardsContainer.removeView(binding.shellCardMenstrual);
        binding.cardsContainer.removeView(binding.shellCardStep);
        binding.cardsContainer.removeView(binding.shellCardMood);
        binding.cardsContainer.removeView(binding.gridCards);
        binding.cardsContainer.removeView(binding.cardChallenge);
        binding.cardsContainer.removeView(binding.layoutBottomActions);

        // 2. 清空 GridLayout 所有子视图
        binding.gridCards.removeAllViews();

        // 3. 计算已开启的打卡小卡片列表
        List<String> enabledSmallCardIds = new ArrayList<>();
        for (String id : order) {
            String trimmed = id.trim();
            if (isSmallCard(trimmed)) {
                boolean show = sp.getBoolean("show_card_" + trimmed, getDefaultCardVisibility(trimmed));
                if (show) {
                    enabledSmallCardIds.add(trimmed);
                }
            }
        }

        // 4. 用全新 FrameLayout + 全新 LayoutParams 逐一重新装填进 grid_cards
        int marginGap = dp(8); // 两列之间的总间隙拆半
        int marginBottom = dp(16);
        for (int i = 0; i < enabledSmallCardIds.size(); i++) {
            String id = enabledSmallCardIds.get(i);
            View cardShell = getCardShellViewById(id);
            if (cardShell != null) {
                ViewGroup oldParent = (ViewGroup) cardShell.getParent();
                if (oldParent != null) {
                    oldParent.removeView(cardShell);
                }

                // 将 shell 宽度改为 MATCH_PARENT 以拉伸占满单元格，高度改为 wrap_content
                ViewGroup.LayoutParams originalLp = cardShell.getLayoutParams();
                if (originalLp != null) {
                    originalLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    originalLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    cardShell.setLayoutParams(originalLp);
                }
                cardShell.setVisibility(View.VISIBLE);

                FrameLayout wrapper = new FrameLayout(requireContext());
                wrapper.addView(cardShell);

                androidx.gridlayout.widget.GridLayout.LayoutParams lp = new androidx.gridlayout.widget.GridLayout.LayoutParams();
                lp.width = 0;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.columnSpec = androidx.gridlayout.widget.GridLayout.spec(androidx.gridlayout.widget.GridLayout.UNDEFINED, 1f);

                if (i % 2 == 0) {
                    lp.setMargins(0, 0, marginGap, marginBottom);
                } else {
                    lp.setMargins(marginGap, 0, 0, marginBottom);
                }

                wrapper.setLayoutParams(lp);
                binding.gridCards.addView(wrapper);
            }
        }

        // 5. 依次按逻辑与配置添加 View 到首页主列表
        // A. 顶部计时器
        binding.cardsContainer.addView(binding.cardRestTimer);

        // B. 动态配置区域 (14个平级打卡记录卡片，其中小卡片用 grid_cards 网格统一承载)
        boolean gridCardsAdded = false;
        for (String id : order) {
            String trimmed = id.trim();
            if (isSmallCard(trimmed)) {
                if (!gridCardsAdded && !enabledSmallCardIds.isEmpty()) {
                    binding.gridCards.setVisibility(View.VISIBLE);
                    binding.cardsContainer.addView(binding.gridCards);
                    gridCardsAdded = true;
                }
            } else {
                View bigCardView = getBigCardViewById(trimmed);
                if (bigCardView != null) {
                    boolean show = sp.getBoolean("show_card_" + trimmed, getDefaultCardVisibility(trimmed));
                    bigCardView.setVisibility(show ? View.VISIBLE : View.GONE);
                    binding.cardsContainer.addView(bigCardView);
                }
            }
        }

        // C. 底部卡片与操作行
        binding.cardsContainer.addView(binding.cardChallenge);
        binding.cardsContainer.addView(binding.layoutBottomActions);
    }

    private boolean isSmallCard(String id) {
        return "water".equals(id) || "sleep".equals(id) || "habit".equals(id) || 
               "medication".equals(id) || "weight".equals(id) || "measurement".equals(id) || 
               "bowel".equals(id) || "menstrual".equals(id) || "step".equals(id) || "mood".equals(id);
    }

    private View getBigCardViewById(String id) {
        switch (id) {
            case "missions": return null; // 每日任务已废弃
            case "briefing": return null;
            case "sport": return binding.cardSport;
            case "diet": return binding.cardDiet;
            default: return null;
        }
    }

    private View getCardShellViewById(String id) {
        switch (id) {
            case "water": return binding.shellCardWater;
            case "sleep": return binding.shellCardSleep;
            case "habit": return binding.shellCardHabit;
            case "medication": return binding.shellCardMedication;
            case "weight": return binding.shellCardWeight;
            case "measurement": return binding.shellCardMeasurement;
            case "bowel": return binding.shellCardBowel;
            case "menstrual": return binding.shellCardMenstrual;
            case "step": return binding.shellCardStep;
            case "mood": return binding.shellCardMood;
            default: return null;
        }
    }

    private boolean getDefaultCardVisibility(String id) {
        return true; // 所有配置项默认全开启显示
    }

    // ================================================================
    // v3.0 AI Daily Briefing
    // ================================================================

    private void updateHeaderTip(DailyHealthSnapshot snapshot, int score) {
        if (binding == null) return;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        String greeting = hour < 10 ? "晨间好" : hour < 14 ? "午间好" : hour < 18 ? "下午好" : "晚间好";

        // Build tip from snapshot data, picking the most notable observation
        String tip;
        if (snapshot == null || (snapshot.completedPlans == 0 && snapshot.dietCalories == 0
                && snapshot.steps == 0 && snapshot.sleepHours == 0 && snapshot.waterMl == 0)) {
            tip = greeting + "！新的一天，从记录健康开始吧 🌱";
        } else {
            StringBuilder sb = new StringBuilder(greeting + "！");

            // Load user targets once
            int calTarget = 2000, waterTarget = 2000, stepTarget = 8000;
            try {
                com.cz.fitnessdiary.database.AppDatabase db =
                        com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
                com.cz.fitnessdiary.database.entity.User u = db.userDao().getUserSync();
                if (u != null) {
                    if (u.getDailyCalorieTarget() > 0) calTarget = u.getDailyCalorieTarget();
                    waterTarget = u.getDailyWaterTarget() > 0 ? u.getDailyWaterTarget() : 2000;
                }
                android.content.SharedPreferences sp = requireContext()
                        .getSharedPreferences("fitness_diary_prefs", android.content.Context.MODE_PRIVATE);
                long todayTs = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
                // 步数/饮水目标：优先当日独立值，无则回落全局默认
                int savedStep = sp.getInt("step_target_" + todayTs, sp.getInt("step_target", 0));
                int savedWater = sp.getInt("water_target_" + todayTs, -1);
                if (savedWater > 0) waterTarget = savedWater;
                if (savedStep > 0) stepTarget = savedStep;
            } catch (Exception ignored) {}

            // Pick notable highlights (up to 2)
            java.util.List<String> notes = new java.util.ArrayList<>();
            if (snapshot.completedPlans >= snapshot.totalPlans && snapshot.totalPlans > 0)
                notes.add("今天的训练全部完成，干得漂亮");
            else if (snapshot.completedPlans > 0)
                notes.add("已完成" + snapshot.completedPlans + "/" + snapshot.totalPlans + "项训练，继续加油");
            else if (score < 50)
                notes.add("还没开始训练，动起来吧");

            if (snapshot.dietCalories > 0) {
                float r = (float) snapshot.dietCalories / calTarget;
                if (r >= 0.9f && r <= 1.1f) notes.add("饮食热量刚好，控制在目标范围");
                else if (r > 1.2f) notes.add("今天吃得偏多，注意热量摄入");
                else if (r < 0.7f) notes.add("摄入偏少，记得吃够营养");
            } else if (score < 70) {
                notes.add("还没记录饮食，吃了吗？");
            }

            if (snapshot.sleepHours >= 7.5f) notes.add("睡眠充足，精力充沛");
            else if (snapshot.sleepHours > 0 && snapshot.sleepHours < 6f) notes.add("昨晚睡得不太够，注意休息");

            if (snapshot.waterMl >= waterTarget) notes.add("饮水达标");
            else if (snapshot.waterMl > 0 && snapshot.waterMl < waterTarget / 2) notes.add("喝水偏少，多喝几杯");

            if (snapshot.steps >= stepTarget) notes.add("步数达标，很活跃");
            else if (snapshot.steps > 0 && snapshot.steps < stepTarget / 3) notes.add("今天走得不多，起来动动");

            if (snapshot.consecutiveDays >= 7) notes.add("连续打卡" + snapshot.consecutiveDays + "天，超棒");

            if (snapshot.weightTrend > 0.5f) notes.add("体重在下降，趋势向好");
            else if (snapshot.weightTrend < -0.5f) notes.add("体重略涨，注意控制");

            // Pick 1-2 randomly or contextually
            if (notes.isEmpty()) {
                sb.append(score >= 70 ? "一切平稳，继续保持" : "还有提升空间，加油");
            } else if (notes.size() == 1) {
                sb.append(notes.get(0));
            } else {
                // Pick 2 different notes
                java.util.Collections.shuffle(notes);
                sb.append(notes.get(0)).append(" · ").append(notes.get(1));
            }
            tip = sb.toString();
        }

        binding.tvHeaderTip.setText(tip);
    }
}
