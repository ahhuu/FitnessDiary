package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.databinding.ItemMealSectionBinding;
import com.cz.fitnessdiary.model.MealSection;
import com.cz.fitnessdiary.ui.adapter.FoodRecordAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 餐段适配器 - Plan 10
 * 展示固定4个餐段卡片，支持折叠
 */
public class MealSectionAdapter extends RecyclerView.Adapter<MealSectionAdapter.MealViewHolder> {

    private List<MealSection> mealSections = new ArrayList<>();
    private OnMealClickListener listener;

    public interface OnMealClickListener {
        void onAddFood(int mealType);

        void onFoodDelete(int recordId);
    }

    public void setOnMealClickListener(OnMealClickListener listener) {
        this.listener = listener;
    }

    public void setMealSections(List<MealSection> sections) {
        this.mealSections = sections;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MealViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMealSectionBinding binding = ItemMealSectionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MealViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MealViewHolder holder, int position) {
        holder.bind(mealSections.get(position));
    }

    @Override
    public int getItemCount() {
        return mealSections.size();
    }

    class MealViewHolder extends RecyclerView.ViewHolder {
        ItemMealSectionBinding binding;
        FoodRecordAdapter foodAdapter;

        MealViewHolder(ItemMealSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            // 初始化子 RecyclerView，传递删除回调
            foodAdapter = new FoodRecordAdapter(food -> {
                if (listener != null) {
                    listener.onFoodDelete(food.getFoodId());
                }
            });
            binding.rvFoodList.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext()));
            binding.rvFoodList.setAdapter(foodAdapter);
        }

        void bind(MealSection section) {
            // 设置标题
            String title = section.getMealName() + " · " + section.getTotalCalories() + " 千卡";
            binding.tvMealHeader.setText(title);

            // 设置展开/折叠图标
            binding.ivExpandIcon.setRotation(section.isExpanded() ? 90 : 0);

            // 处理空状态
            if (section.isEmpty()) {
                binding.tvEmptyHint.setVisibility(View.VISIBLE);
                binding.rvFoodList.setVisibility(View.GONE);
            } else {
                binding.tvEmptyHint.setVisibility(View.GONE);
                binding.rvFoodList.setVisibility(section.isExpanded() ? View.VISIBLE : View.GONE);
                foodAdapter.setFoodRecords(section.getFoodRecords());
            }

            // 点击头部切换折叠状态
            binding.cardHeader.setOnClickListener(v -> {
                section.toggleExpanded();
                notifyItemChanged(getAdapterPosition());
            });

            // 点击空状态添加食物
            binding.tvEmptyHint.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddFood(section.getMealType());
                }
            });
        }
    }
}
