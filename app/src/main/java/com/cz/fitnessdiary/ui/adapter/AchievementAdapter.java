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
            binding.tvDescription.setText(achievement.getDescription());
            binding.ivIcon.setImageResource(achievement.getIconRes());

            if (achievement.isUnlocked()) {
                // 已解锁：彩色图标 + 深色文字
                binding.ivIcon.setAlpha(1.0f);
                binding.tvTitle.setTextColor(Color.parseColor("#212121"));
                binding.tvDescription.setTextColor(Color.parseColor("#757575"));
                binding.tvStatus.setText("✓ 已点亮");
                binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                // 未解锁：灰色
                binding.ivIcon.setAlpha(0.3f);
                binding.tvTitle.setTextColor(Color.parseColor("#BDBDBD"));
                binding.tvDescription.setTextColor(Color.parseColor("#E0E0E0"));
                binding.tvStatus.setText("未解锁");
                binding.tvStatus.setTextColor(Color.parseColor("#BDBDBD"));
            }
        }
    }
}
