package com.cz.fitnessdiary.ui.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.databinding.ItemAchievementBinding;
import com.cz.fitnessdiary.model.Achievement;

import java.util.ArrayList;
import java.util.List;

/**
 * 成就适配器 - Plan 10
 * 展示解锁/未解锁的成就
 */
public class AchievementAdapter extends RecyclerView.Adapter<AchievementAdapter.AchievementViewHolder> {

    private List<Achievement> achievements = new ArrayList<>();

    public void setAchievements(List<Achievement> achievements) {
        this.achievements = achievements;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AchievementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAchievementBinding binding = ItemAchievementBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AchievementViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AchievementViewHolder holder, int position) {
        holder.bind(achievements.get(position));
    }

    @Override
    public int getItemCount() {
        return achievements.size();
    }

    static class AchievementViewHolder extends RecyclerView.ViewHolder {
        ItemAchievementBinding binding;

        AchievementViewHolder(ItemAchievementBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Achievement achievement) {
            binding.tvTitle.setText(achievement.getTitle());
            binding.ivIcon.setImageResource(achievement.getIconRes());

            // 点击查看成就说明
            binding.getRoot().setOnClickListener(v -> {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(binding.getRoot().getContext())
                        .setTitle(achievement.getTitle())
                        .setMessage(achievement.getDescription())
                        .setPositiveButton("知道了", null)
                        .show();
            });

            if (achievement.isUnlocked()) {
                // 已解锁：彩色图标 + 深色文字
                binding.ivIcon.setAlpha(1.0f);
                binding.ivIcon.clearColorFilter();
                binding.tvTitle.setTextColor(androidx.core.content.ContextCompat
                        .getColor(binding.getRoot().getContext(), com.cz.fitnessdiary.R.color.text_primary));
            } else {
                // 未解锁：置灰图标 + 灰色文字
                binding.ivIcon.setAlpha(0.7f);
                android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
                matrix.setSaturation(0); // 置灰
                binding.ivIcon.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
                binding.tvTitle.setTextColor(androidx.core.content.ContextCompat
                        .getColor(binding.getRoot().getContext(), com.cz.fitnessdiary.R.color.text_hint));
            }
        }
    }
}
