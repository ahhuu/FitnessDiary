package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.databinding.ItemFoodBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 食物记录列表适配器
 */
public class FoodRecordAdapter extends RecyclerView.Adapter<FoodRecordAdapter.ViewHolder> {

    private List<FoodRecord> foodList = new ArrayList<>();
    private OnFoodClickListener listener;

    public interface OnFoodClickListener {
        void onFoodClick(FoodRecord food);
    }

    public FoodRecordAdapter(OnFoodClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<FoodRecord> foods) {
        this.foodList = foods;
        notifyDataSetChanged();
    }

    /**
     * 设置食物记录列表（别名方法）
     */
    public void setFoodRecords(List<FoodRecord> foods) {
        setData(foods);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFoodBinding binding = ItemFoodBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(foodList.get(position));
    }

    @Override
    public int getItemCount() {
        return foodList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ItemFoodBinding binding;

        ViewHolder(ItemFoodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FoodRecord food) {
            // Plan 9: 显示餐点类型标签
            String mealLabel = "";
            switch (food.getMealType()) {
                case 0:
                    mealLabel = "[早餐] ";
                    break;
                case 1:
                    mealLabel = "[午餐] ";
                    break;
                case 2:
                    mealLabel = "[晚餐] ";
                    break;
                case 3:
                    mealLabel = "[加餐] ";
                    break;
            }
            binding.tvFoodName.setText(mealLabel + food.getFoodName());

            // 显示热量和份数
            String info = food.getCalories() + " 千卡";
            if (food.getServings() > 0) {
                // 如果有份数记录，也显示出来
                // info += " (" + food.getServings() + "份)";
            }
            binding.tvCalories.setText(info);

            // Plan 1.2: 显示份数和单位
            if (food.getServings() > 0) {
                String unit = food.getServingUnit() != null ? food.getServingUnit() : "份";
                String portionInfo = food.getServings() + unit;
                binding.tvPortions.setText(portionInfo);
                binding.tvPortions.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.tvPortions.setVisibility(android.view.View.GONE);
            }

            // 删除按钮点击
            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFoodClick(food);
                }
            });
        }
    }
}
