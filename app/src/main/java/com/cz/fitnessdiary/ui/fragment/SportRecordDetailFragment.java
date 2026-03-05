package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.ui.adapter.DailyLogAdapter;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.viewmodel.CheckInViewModel;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class SportRecordDetailFragment extends Fragment {

    private CheckInViewModel checkInViewModel;
    private DailyLogAdapter adapter;
    private final List<TrainingPlan> plans = new ArrayList<>();
    private final List<DailyLog> logs = new ArrayList<>();

    private TextView tvSummary;
    private TextView tvSelectedDate;
    private TextView tvConsecutiveDays;
    private TextView tvNoLogs;
    private TextView tvWeeklyStat;
    private TextView tvDailyAvg;
    private LinearLayout weekCalendar;
    private RecyclerView rvTodayLogs;

    public SportRecordDetailFragment() {
        super(R.layout.fragment_sport_record_detail);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkInViewModel = new ViewModelProvider(this).get(CheckInViewModel.class);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        ImageButton btnAdd = view.findViewById(R.id.btn_add);
        ImageButton btnPrevDay = view.findViewById(R.id.btn_prev_day);
        ImageButton btnNextDay = view.findViewById(R.id.btn_next_day);
        tvSelectedDate = view.findViewById(R.id.tv_selected_date);
        tvConsecutiveDays = view.findViewById(R.id.tv_consecutive_days);
        tvSummary = view.findViewById(R.id.tv_summary);
        tvNoLogs = view.findViewById(R.id.tv_no_logs);
        tvWeeklyStat = view.findViewById(R.id.tv_weekly_stat);
        tvDailyAvg = view.findViewById(R.id.tv_daily_avg);
        weekCalendar = view.findViewById(R.id.week_calendar);
        rvTodayLogs = view.findViewById(R.id.rv_today_logs);

        adapter = new DailyLogAdapter((planId, isCompleted) -> {
            DailyLog existing = null;
            for (DailyLog log : logs) {
                if (log.getPlanId() == planId) {
                    existing = log;
                    break;
                }
            }
            if (existing != null) {
                checkInViewModel.updateCompletionStatus(existing.getLogId(), isCompleted);
            } else {
                checkInViewModel.checkInSelectedDate(planId);
                if (!isCompleted) {
                    Toast.makeText(getContext(), "已新增记录，可继续勾选完成", Toast.LENGTH_SHORT).show();
                }
            }
        });
        rvTodayLogs.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTodayLogs.setAdapter(adapter);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnAdd.setOnClickListener(v -> {
            if (!plans.isEmpty()) {
                rvTodayLogs.smoothScrollToPosition(0);
            } else {
                Toast.makeText(getContext(), "请先创建训练计划", Toast.LENGTH_SHORT).show();
            }
        });
        btnPrevDay.setOnClickListener(v -> checkInViewModel.toPreviousDay());
        btnNextDay.setOnClickListener(v -> checkInViewModel.toNextDay());
        tvSelectedDate.setOnClickListener(v -> showDatePicker());

        long selectedDate = requireArguments().getLong("selectedDate", System.currentTimeMillis());
        checkInViewModel.setSelectedDate(selectedDate);

        observeViewModel();
        refreshWeekCalendar();
    }

    private void showDatePicker() {
        Long currentSelection = checkInViewModel.getSelectedDate().getValue();
        if (currentSelection == null) {
            currentSelection = System.currentTimeMillis();
        }
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择日期")
                .setSelection(DateUtils.localToUtcDayStart(currentSelection))
                .setCalendarConstraints(new CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now()).build())
                .build();
        datePicker.addOnPositiveButtonClickListener(selection -> checkInViewModel.setSelectedDate(selection));
        datePicker.show(getParentFragmentManager(), "SPORT_DETAIL_DATE_PICKER");
    }

    private void observeViewModel() {
        checkInViewModel.getSelectedDate().observe(getViewLifecycleOwner(), date -> {
            if (DateUtils.isToday(date)) {
                tvSelectedDate.setText("今日");
            } else {
                String d = new SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(new Date(date));
                tvSelectedDate.setText(d);
            }
            refreshWeekCalendar();
        });

        checkInViewModel.getSelectedDatePlans().observe(getViewLifecycleOwner(), p -> {
            plans.clear();
            if (p != null) {
                plans.addAll(p);
            }
            updateAdapter();
        });

        checkInViewModel.getSelectedDateLogs().observe(getViewLifecycleOwner(), l -> {
            logs.clear();
            if (l != null) {
                logs.addAll(l);
            }
            updateAdapter();
        });

        checkInViewModel.getConsecutiveDays().observe(getViewLifecycleOwner(), days -> {
            int value = days == null ? 0 : days;
            tvConsecutiveDays.setText("已连续坚持 " + value + " 天");
        });
    }

    private void updateAdapter() {
        adapter.setData(plans, logs);
        int completed = 0;
        for (DailyLog log : logs) {
            if (log.isCompleted()) {
                completed++;
            }
        }
        tvSummary.setText("今日完成 " + completed + " / " + plans.size());
        tvDailyAvg.setText("每日平均 " + completed);

        boolean hasPlans = !plans.isEmpty();
        rvTodayLogs.setVisibility(hasPlans ? View.VISIBLE : View.GONE);
        tvNoLogs.setVisibility(hasPlans ? View.GONE : View.VISIBLE);
    }

    private void refreshWeekCalendar() {
        String[] weekDays = { "一", "二", "三", "四", "五", "六", "日" };
        checkInViewModel.getThisWeekCheckedDates(checkedDays -> requireActivity().runOnUiThread(() -> {
            weekCalendar.removeAllViews();
            int weeklyDone = 0;
            for (int i = 0; i < 7; i++) {
                LinearLayout dayContainer = new LinearLayout(getContext());
                dayContainer.setOrientation(LinearLayout.VERTICAL);
                dayContainer.setGravity(Gravity.CENTER);
                dayContainer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView tvDay = new TextView(getContext());
                tvDay.setText(weekDays[i]);
                tvDay.setTextSize(12);
                tvDay.setTextColor(getResources().getColor(R.color.text_secondary, null));

                View dot = new View(getContext());
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20));
                dotParams.topMargin = dpToPx(6);
                dot.setLayoutParams(dotParams);
                boolean checked = checkedDays[i];
                if (checked) {
                    weeklyDone++;
                }
                dot.setBackgroundResource(checked ? R.drawable.circle_checked : R.drawable.circle_unchecked);

                dayContainer.addView(tvDay);
                dayContainer.addView(dot);
                weekCalendar.addView(dayContainer);
            }
            tvWeeklyStat.setText("本周达成 " + weeklyDone + " 天");
        }));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}