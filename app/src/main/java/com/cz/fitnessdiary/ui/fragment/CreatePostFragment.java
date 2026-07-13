package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.databinding.FragmentCreatePostBinding;
import com.cz.fitnessdiary.viewmodel.CheckInViewModel;
import com.cz.fitnessdiary.viewmodel.HomeDashboardViewModel;
import com.cz.fitnessdiary.viewmodel.SocialViewModel;

import java.util.HashMap;
import java.util.Map;

public final class CreatePostFragment extends Fragment {
    private FragmentCreatePostBinding binding;
    private SocialViewModel viewModel;
    private int workoutMinutes;
    private int checkInDays;
    private int steps;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        binding = FragmentCreatePostBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        viewModel = new ViewModelProvider(this).get(SocialViewModel.class);
        CheckInViewModel checkInViewModel = new ViewModelProvider(this).get(CheckInViewModel.class);
        HomeDashboardViewModel dashboardViewModel =
                new ViewModelProvider(this).get(HomeDashboardViewModel.class);

        checkInViewModel.getSelectedDateLogs().observe(getViewLifecycleOwner(), logs -> {
            int durationSeconds = 0;
            if (logs != null) {
                for (DailyLog log : logs) {
                    if (log.isCompleted()) durationSeconds += Math.max(0, log.getDuration());
                }
            }
            workoutMinutes = durationSeconds == 0 ? 0 : Math.max(1, Math.round(durationSeconds / 60f));
            binding.chipTraining.setText(workoutMinutes > 0
                    ? "训练 " + workoutMinutes + " 分钟" : "今日暂无训练摘要");
            binding.chipTraining.setEnabled(workoutMinutes > 0);
        });
        checkInViewModel.getConsecutiveDays().observe(getViewLifecycleOwner(), value -> {
            checkInDays = value == null ? 0 : Math.max(0, value);
            binding.chipStreak.setText(checkInDays > 0
                    ? "连续打卡 " + checkInDays + " 天" : "暂无连续打卡摘要");
            binding.chipStreak.setEnabled(checkInDays > 0);
        });
        dashboardViewModel.getTodayStep().observe(getViewLifecycleOwner(), record -> {
            steps = record == null ? 0 : Math.max(0, record.getSteps());
            binding.chipSteps.setText(steps > 0 ? "今日步数 " + steps : "今日暂无步数摘要");
            binding.chipSteps.setEnabled(steps > 0);
        });

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        binding.btnPublish.setOnClickListener(v -> viewModel.createPost(text(), selectedSummary()));
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean active = Boolean.TRUE.equals(loading);
            binding.progress.setVisibility(active ? View.VISIBLE : View.GONE);
            binding.btnPublish.setEnabled(!active);
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null) return;
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            if ("动态发布成功".equals(message)) {
                NavHostFragment.findNavController(this).navigateUp();
            }
        });
    }

    private String text() {
        return binding.etContent.getText() == null ? "" : binding.etContent.getText().toString();
    }

    private Map<String, Object> selectedSummary() {
        Map<String, Object> result = new HashMap<>();
        if (binding.chipTraining.isChecked() && workoutMinutes > 0) {
            result.put("workoutMinutes", workoutMinutes);
        }
        if (binding.chipStreak.isChecked() && checkInDays > 0) {
            result.put("checkInDays", checkInDays);
        }
        if (binding.chipSteps.isChecked() && steps > 0) {
            result.put("steps", steps);
        }
        return result;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
