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

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.FragmentCheckinBinding;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.CheckInViewModel;
import com.cz.fitnessdiary.viewmodel.HomeDashboardViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
    private final List<TrainingPlan> currentPlans = new ArrayList<>();
    private final List<DailyLog> currentLogs = new ArrayList<>();
    private final List<String> smallCardOrder = new ArrayList<>();
    private final List<HabitItem> habitItems = new ArrayList<>();
    private final Map<Long, HabitRecord> habitRecords = new HashMap<>();
    private long lastNavigateTs = 0L;

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
        setupActions();
        observeData();
        loadCardConfig();
        applyCardConfig();
    }

    private void setupActions() {
        v(R.id.card_sport).setOnClickListener(v -> openDetail(R.id.sportRecordDetailFragment));
        v(R.id.card_water).setOnClickListener(v -> openDetail(R.id.waterRecordDetailFragment));
        v(R.id.card_sleep).setOnClickListener(v -> openDetail(R.id.sleepRecordDetailFragment));
        v(R.id.card_habit).setOnClickListener(v -> openDetail(R.id.habitRecordDetailFragment));
        v(R.id.card_medication).setOnClickListener(v -> openDetail(R.id.medicationRecordDetailFragment));
        v(R.id.card_weight_small).setOnClickListener(v -> openDetail(R.id.weightRecordDetailFragment));
        v(R.id.card_custom).setOnClickListener(v -> openDetail(R.id.customCategoryFragment));

        binding.btnAddSport.setOnClickListener(v -> openDetail(R.id.sportRecordDetailFragment));
        v(R.id.btn_add_water).setOnClickListener(v -> quickNumberInput("记录喝水(ml)", value -> homeDashboardViewModel.addWater(value.intValue(), null)));
        v(R.id.btn_add_weight).setOnClickListener(v -> quickNumberInput("添加体重(kg)", value -> homeDashboardViewModel.addWeight(value.floatValue(), null)));
        v(R.id.btn_add_medication).setOnClickListener(v -> quickTextInput("添加用药", text -> homeDashboardViewModel.addMedication(text, "", true, null)));
        v(R.id.btn_add_sleep).setOnClickListener(v -> openDetail(R.id.sleepRecordDetailFragment));
        v(R.id.btn_add_habit).setOnClickListener(v -> openDetail(R.id.habitRecordDetailFragment));
        v(R.id.btn_add_custom).setOnClickListener(v -> openDetail(R.id.customCategoryFragment));

        binding.fabQuickAdd.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext()).setTitle("快捷新增")
                .setItems(new String[] { "运动", "体重", "喝水", "用药", "习惯", "睡眠", "自定义分类" }, (d, i) -> {
                    if (i == 0) openDetail(R.id.sportRecordDetailFragment);
                    if (i == 1) v(R.id.btn_add_weight).performClick();
                    if (i == 2) v(R.id.btn_add_water).performClick();
                    if (i == 3) v(R.id.btn_add_medication).performClick();
                    if (i == 4) openDetail(R.id.habitRecordDetailFragment);
                    if (i == 5) openDetail(R.id.sleepRecordDetailFragment);
                    if (i == 6) openDetail(R.id.customCategoryFragment);
                }).show());

        binding.btnEditHomeCards.setOnClickListener(v -> showEditCardsDialog());
    }

    private interface NumberConsumer { void accept(Double v); }
    private interface TextConsumer { void accept(String v); }

    private void quickNumberInput(String title, NumberConsumer c) {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try { c.accept(Double.parseDouble(et.getText().toString().trim())); }
                    catch (Exception e) { Toast.makeText(getContext(), "请输入正确数字", Toast.LENGTH_SHORT).show(); }
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
        checkInViewModel.getSelectedDate().observe(getViewLifecycleOwner(), homeDashboardViewModel::setSelectedDate);
        checkInViewModel.getSelectedDatePlans().observe(getViewLifecycleOwner(), plans -> {
            currentPlans.clear();
            if (plans != null) currentPlans.addAll(plans);
            refreshSportCard();
            refreshHabitCard();
        });
        checkInViewModel.getSelectedDateLogs().observe(getViewLifecycleOwner(), logs -> {
            currentLogs.clear();
            if (logs != null) currentLogs.addAll(logs);
            refreshSportCard();
            refreshHabitCard();
        });
        checkInViewModel.getConsecutiveDays().observe(getViewLifecycleOwner(), days -> binding.tvConsecutiveDays.setText("连续 " + (days == null ? 0 : days) + " 天"));
        checkInViewModel.getThisWeekCheckedDates(checkedDays -> {
            int weekly = 0;
            for (boolean checked : checkedDays) if (checked) weekly++;
            int finalWeekly = weekly;
            requireActivity().runOnUiThread(() -> binding.tvSportWeekly.setText("本周达成 " + finalWeekly + " 天"));
        });

        homeDashboardViewModel.getLatestWeight().observe(getViewLifecycleOwner(), r -> {
            setTextIfExists(R.id.tv_weight_value, r == null ? "-- kg" : String.format(java.util.Locale.getDefault(), "%.1f kg", r.getWeight()));
            setTextIfExists(R.id.tv_weight_update, r == null ? "暂无更新" : getUpdateText(r.getTimestamp()));
            setTextIfExists(R.id.tv_weight_summary, "点击查看体重明细");
        });
        homeDashboardViewModel.getTodayWaterTotal().observe(getViewLifecycleOwner(), total -> {
            int v = total == null ? 0 : total;
            setTextIfExists(R.id.tv_water_value, v + " ml");
            setTextIfExists(R.id.tv_water_summary, "目标1600ml");
        });
        homeDashboardViewModel.getLatestWater().observe(getViewLifecycleOwner(), r -> setTextIfExists(R.id.tv_water_update, r == null ? "暂无更新" : getUpdateText(r.getTimestamp())));
        homeDashboardViewModel.getTodayMedicationTakenCount().observe(getViewLifecycleOwner(), c -> setTextIfExists(R.id.tv_medication_value, (c == null ? 0 : c) + " 次"));
        homeDashboardViewModel.getLatestMedication().observe(getViewLifecycleOwner(), r -> setTextIfExists(R.id.tv_medication_update, r == null ? "暂无更新" : getUpdateText(r.getTimestamp())));
        homeDashboardViewModel.getEnabledTrackerCount().observe(getViewLifecycleOwner(), c -> setTextIfExists(R.id.tv_custom_value, (c == null ? 0 : c) + " 类"));
        homeDashboardViewModel.getTodayCustomRecordCount().observe(getViewLifecycleOwner(), c -> setTextIfExists(R.id.tv_custom_summary, "今日 " + (c == null ? 0 : c) + " 条"));
        homeDashboardViewModel.getEnabledHabits().observe(getViewLifecycleOwner(), items -> {
            habitItems.clear();
            if (items != null) habitItems.addAll(items);
            refreshHabitCard();
        });
        homeDashboardViewModel.getSelectedDateHabitRecords().observe(getViewLifecycleOwner(), records -> {
            habitRecords.clear();
            if (records != null) {
                for (HabitRecord r : records) {
                    habitRecords.put(r.getHabitId(), r);
                }
            }
            refreshHabitCard();
        });
    }

    private void refreshSportCard() {
        HashSet<Integer> donePlanIds = new HashSet<>();
        for (DailyLog log : currentLogs) if (log.isCompleted()) donePlanIds.add(log.getPlanId());
        int done = 0;
        for (TrainingPlan plan : currentPlans) if (donePlanIds.contains(plan.getPlanId())) done++;
        binding.tvSportTodayCount.setText("今日完成 " + done + " 项");
        Long selected = checkInViewModel.getSelectedDate().getValue();
        binding.tvSportUpdate.setText(DateUtils.isToday(selected == null ? 0 : selected) ? "今日更新" : "历史记录");
    }

    private void refreshHabitCard() {
        int total = habitItems.size();
        int completed = 0;
        for (HabitItem item : habitItems) {
            HabitRecord r = habitRecords.get(item.getId());
            if (r != null && r.isCompleted()) completed++;
        }
        setTextIfExists(R.id.tv_habit_value, completed + " / " + total);
        setTextIfExists(R.id.tv_habit_summary, "默认：每日打卡/早餐/早睡");
        setTextIfExists(R.id.tv_habit_update, "今日状态");
    }

    private void openDetail(int destination) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNavigateTs < 280) {
            return;
        }
        lastNavigateTs = now;

        Bundle args = new Bundle();
        Long ts = checkInViewModel.getSelectedDate().getValue();
        args.putLong("selectedDate", ts == null ? System.currentTimeMillis() : ts);

        NavOptions navOptions = new NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_right)
                .setExitAnim(R.anim.fade_out_fast)
                .setPopEnterAnim(R.anim.fade_in_fast)
                .setPopExitAnim(R.anim.slide_out_right)
                .build();
        NavHostFragment.findNavController(this).navigate(destination, args, navOptions);
    }

    private void showEditCardsDialog() {
        ScrollView sv = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);

        CheckBox cWater = buildToggle(root, "喝水", isCardEnabled(KEY_SHOW_WATER, true));
        CheckBox cSleep = buildToggle(root, "睡眠", isCardEnabled(KEY_SHOW_SLEEP, true));
        CheckBox cHabit = buildToggle(root, "习惯", isCardEnabled(KEY_SHOW_HABIT, true));
        CheckBox cMed = buildToggle(root, "用药", isCardEnabled(KEY_SHOW_MEDICATION, true));
        CheckBox cWeight = buildToggle(root, "体重", isCardEnabled(KEY_SHOW_WEIGHT, true));
        CheckBox cCustom = buildToggle(root, "自定义分类", isCardEnabled(KEY_SHOW_CUSTOM, true));

        List<String> temp = new ArrayList<>(smallCardOrder);
        LinearLayout oc = new LinearLayout(requireContext());
        oc.setOrientation(LinearLayout.VERTICAL);
        root.addView(oc);
        renderOrderEditor(oc, temp);

        new MaterialAlertDialogBuilder(requireContext()).setTitle("编辑首页卡片").setView(sv).setPositiveButton("保存", (d, w) -> {
            SharedPreferences sp = requireContext().getSharedPreferences(PREF_HOME_CARDS, Context.MODE_PRIVATE);
            sp.edit().putBoolean(KEY_SHOW_WATER, cWater.isChecked()).putBoolean(KEY_SHOW_SLEEP, cSleep.isChecked())
                    .putBoolean(KEY_SHOW_HABIT, cHabit.isChecked()).putBoolean(KEY_SHOW_MEDICATION, cMed.isChecked())
                    .putBoolean(KEY_SHOW_WEIGHT, cWeight.isChecked()).putBoolean(KEY_SHOW_CUSTOM, cCustom.isChecked())
                    .putString(KEY_SMALL_ORDER, String.join(",", temp)).apply();
            loadCardConfig();
            applyCardConfig();
        }).setNegativeButton("取消", null).show();
    }

    private void loadCardConfig() {
        SharedPreferences sp = requireContext().getSharedPreferences(PREF_HOME_CARDS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_SMALL_ORDER,
                CARD_WATER + "," + CARD_SLEEP + "," + CARD_HABIT + "," + CARD_MEDICATION + "," + CARD_WEIGHT + "," + CARD_CUSTOM);
        smallCardOrder.clear();
        if (raw != null) {
            for (String s : raw.split(",")) {
                String id = s.trim();
                if (!smallCardOrder.contains(id)) smallCardOrder.add(id);
            }
        }
        if (!smallCardOrder.contains(CARD_WATER)) smallCardOrder.add(CARD_WATER);
        if (!smallCardOrder.contains(CARD_SLEEP)) smallCardOrder.add(CARD_SLEEP);
        if (!smallCardOrder.contains(CARD_HABIT)) smallCardOrder.add(CARD_HABIT);
        if (!smallCardOrder.contains(CARD_MEDICATION)) smallCardOrder.add(CARD_MEDICATION);
        if (!smallCardOrder.contains(CARD_WEIGHT)) smallCardOrder.add(CARD_WEIGHT);
        if (!smallCardOrder.contains(CARD_CUSTOM)) smallCardOrder.add(CARD_CUSTOM);
    }

    private void applyCardConfig() {
        Map<String, View> map = new HashMap<>();
        map.put(CARD_WATER, v(R.id.card_water));
        map.put(CARD_SLEEP, v(R.id.card_sleep));
        map.put(CARD_HABIT, v(R.id.card_habit));
        map.put(CARD_MEDICATION, v(R.id.card_medication));
        map.put(CARD_WEIGHT, v(R.id.card_weight_small));
        map.put(CARD_CUSTOM, v(R.id.card_custom));

        for (View card : map.values()) {
            ViewGroup parent = (ViewGroup) card.getParent();
            if (parent != null) parent.removeView(card);
        }

        binding.layoutSmallCardsTop.removeAllViews();
        binding.layoutSmallCardsMiddle.removeAllViews();
        binding.layoutSmallCardsBottom.removeAllViews();

        List<String> enabled = new ArrayList<>();
        for (String id : smallCardOrder) {
            if (CARD_WATER.equals(id) && isCardEnabled(KEY_SHOW_WATER, true)) enabled.add(id);
            if (CARD_SLEEP.equals(id) && isCardEnabled(KEY_SHOW_SLEEP, true)) enabled.add(id);
            if (CARD_HABIT.equals(id) && isCardEnabled(KEY_SHOW_HABIT, true)) enabled.add(id);
            if (CARD_MEDICATION.equals(id) && isCardEnabled(KEY_SHOW_MEDICATION, true)) enabled.add(id);
            if (CARD_WEIGHT.equals(id) && isCardEnabled(KEY_SHOW_WEIGHT, true)) enabled.add(id);
            if (CARD_CUSTOM.equals(id) && isCardEnabled(KEY_SHOW_CUSTOM, true)) enabled.add(id);
        }

        LinearLayout[] rows = new LinearLayout[] {
                binding.layoutSmallCardsTop,
                binding.layoutSmallCardsMiddle,
                binding.layoutSmallCardsBottom
        };

        for (int i = 0; i < enabled.size(); i++) {
            View card = map.get(enabled.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(190), 1f);
            if (i % 2 == 0) lp.setMarginEnd(dp(6)); else lp.setMarginStart(dp(6));
            card.setLayoutParams(lp);
            rows[i / 2].addView(card);
        }

        binding.layoutSmallCardsTop.setVisibility(enabled.size() > 0 ? View.VISIBLE : View.GONE);
        binding.layoutSmallCardsMiddle.setVisibility(enabled.size() > 2 ? View.VISIBLE : View.GONE);
        binding.layoutSmallCardsBottom.setVisibility(enabled.size() > 4 ? View.VISIBLE : View.GONE);
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

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    private void renderOrderEditor(LinearLayout c, List<String> o) {
        c.removeAllViews();
        for (int i = 0; i < o.size(); i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(requireContext());
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);

            TextView t = new TextView(requireContext());
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            t.setText(o.get(i));
            r.addView(t);

            ImageButton up = new ImageButton(requireContext());
            up.setImageResource(android.R.drawable.arrow_up_float);
            up.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            up.setEnabled(i > 0);
            up.setOnClickListener(v -> {
                if (idx > 0) {
                    String cur = o.get(idx);
                    o.set(idx, o.get(idx - 1));
                    o.set(idx - 1, cur);
                    renderOrderEditor(c, o);
                }
            });
            r.addView(up);

            ImageButton down = new ImageButton(requireContext());
            down.setImageResource(android.R.drawable.arrow_down_float);
            down.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            down.setEnabled(i < o.size() - 1);
            down.setOnClickListener(v -> {
                if (idx < o.size() - 1) {
                    String cur = o.get(idx);
                    o.set(idx, o.get(idx + 1));
                    o.set(idx + 1, cur);
                    renderOrderEditor(c, o);
                }
            });
            r.addView(down);
            c.addView(r);
        }
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
        binding = null;
    }
}