package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.FragmentCheckinBinding;
import com.cz.fitnessdiary.ui.adapter.DailyLogAdapter;
import com.cz.fitnessdiary.viewmodel.CheckInViewModel;
import com.cz.fitnessdiary.viewmodel.PlanViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 每日打卡页面 - 2.0 升级版
 * 添加本周日历视图和连续打卡天数
 */
public class CheckInFragment extends Fragment {

    private FragmentCheckinBinding binding;
    private CheckInViewModel checkInViewModel;
    private PlanViewModel planViewModel;
    private DailyLogAdapter adapter;

    // 暂存数据
    private List<TrainingPlan> mPlans = new ArrayList<>();
    private List<DailyLog> mLogs = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentCheckinBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        checkInViewModel = new ViewModelProvider(this).get(CheckInViewModel.class);
        planViewModel = new ViewModelProvider(this).get(PlanViewModel.class);

        setupRecyclerView();
        setupWeekCalendar();
        observeViewModel();
    }

    /**
     * 设置 RecyclerView
     */
    private void setupRecyclerView() {
        adapter = new DailyLogAdapter((planId, isCompleted) -> {
            // CheckBox 点击切换完成状态
            // 查找对应的 log
            DailyLog existingLog = null;
            for (DailyLog log : mLogs) {
                if (log.getPlanId() == planId) {
                    existingLog = log;
                    break;
                }
            }

            if (existingLog != null) {
                // 已有打卡记录，更新状态
                checkInViewModel.updateCompletionStatus(existingLog.getLogId(), isCompleted);
            } else {
                // 无打卡记录，创建新记录
                checkInViewModel.checkInToday(planId);
            }
        });

        binding.rvTodayLogs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvTodayLogs.setAdapter(adapter);
    }

    /**
     * 设置本周日历视图
     */
    private void setupWeekCalendar() {
        // 显示周一到周日
        String[] weekDays = { "一", "二", "三", "四", "五", "六", "日" };

        checkInViewModel.getThisWeekCheckedDates(checkedDays -> {
            requireActivity().runOnUiThread(() -> {
                binding.weekCalendar.removeAllViews();

                for (int i = 0; i < 7; i++) {
                    LinearLayout dayContainer = new LinearLayout(getContext());
                    dayContainer.setOrientation(LinearLayout.VERTICAL);
                    dayContainer.setGravity(Gravity.CENTER);
                    dayContainer.setLayoutParams(new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f));
                    dayContainer.setPadding(8, 8, 8, 8);

                    // 星期文字
                    TextView tvDay = new TextView(getContext());
                    tvDay.setText(weekDays[i]);
                    tvDay.setTextSize(12);
                    tvDay.setTextColor(getResources().getColor(R.color.text_secondary, null));
                    tvDay.setGravity(Gravity.CENTER);

                    // 打卡圆点
                    View dot = new View(getContext());
                    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                            dpToPx(24),
                            dpToPx(24));
                    dotParams.setMargins(0, 8, 0, 0);
                    dot.setLayoutParams(dotParams);

                    if (checkedDays[i]) {
                        // 已打卡：绿色实心圆
                        dot.setBackgroundResource(R.drawable.circle_checked);
                    } else {
                        // 未打卡：灰色空心圆
                        dot.setBackgroundResource(R.drawable.circle_unchecked);
                    }

                    dayContainer.addView(tvDay);
                    dayContainer.addView(dot);
                    binding.weekCalendar.addView(dayContainer);
                }
            });
        });
    }

    /**
     * 观察 ViewModel 数据（双重监听）
     */
    private void observeViewModel() {
        // 监听今日训练计划 (Plan 26: 只显示今日排期)
        checkInViewModel.getTodayPlans().observe(getViewLifecycleOwner(), plans -> {
            if (plans != null) {
                mPlans = plans;
                updateAdapter();
            }
        });

        // 监听今日打卡记录
        checkInViewModel.getTodayLogs().observe(getViewLifecycleOwner(), logs -> {
            if (logs != null) {
                mLogs = logs;
                updateAdapter();
            }
        });

        // 观察连续打卡天数
        checkInViewModel.getConsecutiveDays().observe(getViewLifecycleOwner(), days -> {
            if (days != null && days > 0) {
                binding.tvConsecutiveDays.setText("已连续坚持 " + days + " 天");
                binding.tvConsecutiveDays.setVisibility(View.VISIBLE);
            } else {
                binding.tvConsecutiveDays.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 合并更新适配器
     */
    private void updateAdapter() {
        if (adapter != null) {
            adapter.setData(mPlans, mLogs);

            // 更新UI显示
            if (mPlans.isEmpty()) {
                binding.tvNoLogs.setVisibility(View.VISIBLE);
                binding.rvTodayLogs.setVisibility(View.GONE);
            } else {
                binding.tvNoLogs.setVisibility(View.GONE);
                binding.rvTodayLogs.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * dp转px
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
