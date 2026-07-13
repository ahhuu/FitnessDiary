package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.ItemDailyLogBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Displays scheduled exercises with their plan target and daily actual values. */
public class DailyLogAdapter extends RecyclerView.Adapter<DailyLogAdapter.ViewHolder> {

    private List<TrainingPlan> planList = new ArrayList<>();
    private final Map<Integer, Boolean> completionMap = new HashMap<>();
    private final Map<Integer, DailyLog> logMap = new HashMap<>();
    private final OnCheckChangeListener listener;

    public interface OnCheckChangeListener {
        void onCheckChanged(int planId, boolean isCompleted);

        void onPlanClicked(TrainingPlan plan);
    }

    public DailyLogAdapter(OnCheckChangeListener listener) {
        this.listener = listener;
    }

    public void setData(List<TrainingPlan> plans, List<DailyLog> logs) {
        planList = plans == null ? new ArrayList<>() : plans;
        completionMap.clear();
        logMap.clear();
        if (logs != null) {
            for (DailyLog log : logs) {
                completionMap.put(log.getPlanId(), log.isCompleted());
                logMap.put(log.getPlanId(), log);
            }
        }
        notifyDataSetChanged();
    }

    public TrainingPlan getPlanAt(int position) {
        if (position >= 0 && position < planList.size()) {
            return planList.get(position);
        }
        return null;
    }

    public boolean isPlanCompleted(int planId) {
        return completionMap.getOrDefault(planId, false);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDailyLogBinding binding = ItemDailyLogBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(planList.get(position));
    }

    @Override
    public int getItemCount() {
        return planList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemDailyLogBinding binding;

        ViewHolder(ItemDailyLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TrainingPlan plan) {
            binding.tvPlanName.setText(plan.getName());
            DailyLog dailyLog = logMap.get(plan.getPlanId());
            boolean hasActual = dailyLog != null && (dailyLog.getActualSets() > 0
                    || dailyLog.getActualReps() > 0
                    || dailyLog.getActualWeight() > 0
                    || dailyLog.getDuration() > 0);
            String targetText = formatMetrics(plan.getSets(), plan.getReps(), plan.getWeight(), plan.getDuration());
            if (hasActual) {
                String actualText = formatMetrics(dailyLog.getActualSets(), dailyLog.getActualReps(),
                        dailyLog.getActualWeight(), dailyLog.getDuration());
                binding.tvPlanDescription.setText("目标 " + targetText + "\n实际 " + actualText);
                binding.tvPlanDescription.setVisibility(View.VISIBLE);
            } else if (!targetText.isEmpty()) {
                binding.tvPlanDescription.setText("目标 " + targetText);
                binding.tvPlanDescription.setVisibility(View.VISIBLE);
            } else {
                binding.tvPlanDescription.setVisibility(View.GONE);
            }

            boolean isCompleted = completionMap.getOrDefault(plan.getPlanId(), false);
            binding.checkbox.setChecked(isCompleted);
            binding.checkbox.setEnabled(true);
            binding.checkbox.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCheckChanged(plan.getPlanId(), binding.checkbox.isChecked());
                }
            });
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlanClicked(plan);
                }
            });

            binding.getRoot().setAlpha(isCompleted ? 0.6f : 1.0f);
            int primaryColor = androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(),
                    com.cz.fitnessdiary.R.color.fitnessdiary_primary);
            binding.tvPlanName.setTextColor(isCompleted ? primaryColor
                    : androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(),
                            com.cz.fitnessdiary.R.color.text_primary));
        }

        private String formatMetrics(int sets, int reps, float weight, int duration) {
            StringBuilder builder = new StringBuilder();
            if (sets > 0 || reps > 0) {
                builder.append(sets).append(" 组 × ").append(reps).append(" 次");
            }
            if (weight > 0) {
                appendSeparator(builder);
                builder.append(String.format(Locale.getDefault(), "%.1f kg", weight));
            }
            if (duration > 0) {
                appendSeparator(builder);
                builder.append(duration).append(" 秒");
            }
            return builder.toString();
        }

        private void appendSeparator(StringBuilder builder) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
        }
    }
}
