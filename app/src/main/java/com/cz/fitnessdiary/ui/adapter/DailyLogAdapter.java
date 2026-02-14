package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.ItemDailyLogBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日打卡记录适配器
 * 结合训练计划和打卡记录显示
 */
public class DailyLogAdapter extends RecyclerView.Adapter<DailyLogAdapter.ViewHolder> {

    private List<TrainingPlan> planList = new ArrayList<>();
    private Map<Integer, Boolean> completionMap = new HashMap<>();
    private OnCheckChangeListener listener;

    public interface OnCheckChangeListener {
        void onCheckChanged(int planId, boolean isCompleted);
    }

    public DailyLogAdapter(OnCheckChangeListener listener) {
        this.listener = listener;
    }

    public void setData(List<TrainingPlan> plans, List<DailyLog> logs) {
        this.planList = plans;

        // 创建完成状态映射
        completionMap.clear();
        if (logs != null) {
            for (DailyLog log : logs) {
                completionMap.put(log.getPlanId(), log.isCompleted());
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
        private ItemDailyLogBinding binding;
        private android.os.CountDownTimer activeTimer;

        ViewHolder(ItemDailyLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TrainingPlan plan) {
            binding.tvPlanName.setText(plan.getName());

            // v1.2: 如果有预设时长，显示时长
            if (plan.getDuration() > 0) {
                int mins = plan.getDuration() / 60;
                int secs = plan.getDuration() % 60;
                String durationStr = String.format(java.util.Locale.getDefault(), "⏳ %02d:%02d", mins, secs);
                binding.tvPlanDescription.setText(durationStr);
                binding.tvPlanDescription.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.tvPlanDescription.setVisibility(android.view.View.GONE);
            }

            // 设置完成状态视觉效果
            boolean isCompleted = completionMap.getOrDefault(plan.getPlanId(), false);
            binding.checkbox.setChecked(isCompleted);
            binding.checkbox.setEnabled(true); // 恢复手动勾选

            // 监听勾选
            binding.checkbox.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCheckChanged(plan.getPlanId(), binding.checkbox.isChecked());
                }
            });

            // v1.2 反馈: 点击带计时的计划启动计时逻辑
            binding.getRoot().setOnClickListener(v -> {
                if (plan.getDuration() > 0 && !isCompleted) {
                    android.content.Context context = binding.getRoot().getContext();
                    // 加载自定义布局
                    android.view.View dialogView = android.view.LayoutInflater.from(context)
                            .inflate(com.cz.fitnessdiary.R.layout.dialog_timer, null);

                    android.widget.ImageView ivHourglass = dialogView
                            .findViewById(com.cz.fitnessdiary.R.id.iv_hourglass);
                    android.widget.TextView tvCountdown = dialogView
                            .findViewById(com.cz.fitnessdiary.R.id.tv_countdown);
                    android.widget.TextView tvTaskName = dialogView.findViewById(com.cz.fitnessdiary.R.id.tv_task_name);

                    tvTaskName.setText(plan.getName());

                    // 格式化初始时间
                    int totalSeconds = plan.getDuration();
                    String timePrompt;
                    if (totalSeconds < 60) {
                        timePrompt = "计划时长 " + totalSeconds + " 秒。是否现在开始计时？";
                        tvCountdown.setText(String.format(java.util.Locale.getDefault(), "00:%02d", totalSeconds));
                    } else {
                        timePrompt = "计划时长 " + (totalSeconds / 60) + " 分钟。是否现在开始计时？";
                        tvCountdown.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", totalSeconds / 60,
                                totalSeconds % 60));
                    }

                    // 创建对话框
                    androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                            context)
                            .setTitle("开始训练")
                            .setMessage(timePrompt)
                            .setView(dialogView)
                            .setCancelable(false)
                            .setNegativeButton("取消退出", (d, which) -> d.dismiss())
                            .setPositiveButton("直接打卡", (d, which) -> {
                                binding.checkbox.setChecked(true);
                                if (listener != null)
                                    listener.onCheckChanged(plan.getPlanId(), true);
                            })
                            .setNeutralButton("开始计时", null) // 稍后获取并设置点击监听，防止自动关闭
                            .create();

                    // v2.0: 显式处理计时器清理，防止内存泄漏
                    dialog.setOnDismissListener(d -> {
                        if (activeTimer != null) {
                            activeTimer.cancel();
                            activeTimer = null;
                        }
                    });

                    dialog.setOnShowListener(d -> {
                        android.widget.Button startBtn = dialog
                                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL);
                        startBtn.setOnClickListener(btn -> {
                            startBtn.setEnabled(false);
                            // 设置沙漏旋转动画
                            android.view.animation.RotateAnimation rotate = new android.view.animation.RotateAnimation(
                                    0, 360,
                                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
                            rotate.setDuration(2000);
                            rotate.setRepeatCount(android.view.animation.Animation.INFINITE);
                            ivHourglass.startAnimation(rotate);

                            // 启动倒计时
                            activeTimer = new android.os.CountDownTimer(totalSeconds * 1000L, 1000L) {
                                @Override
                                public void onTick(long millisUntilFinished) {
                                    int remaining = (int) (millisUntilFinished / 1000);
                                    tvCountdown.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d",
                                            remaining / 60, remaining % 60));
                                }

                                @Override
                                public void onFinish() {
                                    ivHourglass.clearAnimation();
                                    tvCountdown.setText("已完成!");
                                    android.widget.Toast
                                            .makeText(context, "计时结束！请手动勾选完成", android.widget.Toast.LENGTH_LONG).show();
                                    // 延时关闭对话框，给用户反馈
                                    new android.os.Handler(android.os.Looper.getMainLooper())
                                            .postDelayed(dialog::dismiss, 1500);
                                }
                            };
                            activeTimer.start();
                        });
                    });
                    dialog.show();
                }
            });

            // 完成后的半透明效果
            binding.getRoot().setAlpha(isCompleted ? 0.6f : 1.0f);
            int primaryColor = androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(),
                    com.cz.fitnessdiary.R.color.fitnessdiary_primary);
            binding.tvPlanName.setTextColor(isCompleted ? primaryColor
                    : androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(),
                            com.cz.fitnessdiary.R.color.text_primary));
        }
    }
}
