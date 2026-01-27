package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.databinding.ItemFoodGroupHeaderBinding;
import com.cz.fitnessdiary.databinding.ItemFoodLibraryBinding;
import com.cz.fitnessdiary.model.FoodGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分组食物库适配器 (Plan 30)
 */
public class GroupedFoodLibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<Object> displayList = new ArrayList<>(); // 混合列表
    private List<FoodGroup> allGroups = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(FoodLibrary food);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置原始食物数据并自动分组 (Plan 30: 修复排序问题)
     */
    public void setFoodList(List<FoodLibrary> allFoods) {
        if (allFoods == null)
            return;

        // 按分类分组 (使用 LinkedHashMap 保持插入顺序)
        Map<String, List<FoodLibrary>> map = new java.util.LinkedHashMap<>();

        // 预定义分类顺序 (按照数据库定义的顺序)
        String[] orderedCategories = {
                "主食 (Staples)",
                "家常菜 (Dishes)",
                "优质蛋白质 (Protein)",
                "蔬菜 & 水果 (Veg & Fruits)",
                "零食饮品 (Snacks & Drinks)"
        };

        // 先初始化所有分类的空列表
        for (String category : orderedCategories) {
            map.put(category, new ArrayList<>());
        }

        // 将食物分配到对应分类
        for (FoodLibrary food : allFoods) {
            String cat = food.getCategory();
            if (cat == null || cat.isEmpty())
                cat = "其他";

            if (!map.containsKey(cat)) {
                map.put(cat, new ArrayList<>());
            }
            map.get(cat).add(food);
        }

        // 转换为 FoodGroup 列表 (只保留非空分类)
        allGroups.clear();
        for (Map.Entry<String, List<FoodLibrary>> entry : map.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                allGroups.add(new FoodGroup(entry.getKey(), entry.getValue()));
            }
        }

        rebuildDisplayList();
    }

    private void rebuildDisplayList() {
        displayList.clear();
        for (FoodGroup group : allGroups) {
            displayList.add(group);
            if (group.isExpanded()) {
                displayList.addAll(group.getFoods());
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return (displayList.get(position) instanceof FoodGroup) ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(ItemFoodGroupHeaderBinding.inflate(inflater, parent, false));
        } else {
            return new ItemViewHolder(ItemFoodLibraryBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = displayList.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((FoodGroup) item);
        } else {
            ((ItemViewHolder) holder).bind((FoodLibrary) item);
        }
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    // Header ViewHolder
    class HeaderViewHolder extends RecyclerView.ViewHolder {
        ItemFoodGroupHeaderBinding binding;

        HeaderViewHolder(ItemFoodGroupHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FoodGroup group) {
            // Plan 30: 添加分类 emoji 图标
            String emoji = getCategoryEmoji(group.getCategory());
            binding.tvCategoryName.setText(emoji + " " + group.getCategory());
            binding.tvFoodCount.setText(group.getFoodCount() + " 种");
            binding.ivExpandIcon.setRotation(group.isExpanded() ? 90 : 0);

            binding.getRoot().setOnClickListener(v -> {
                group.toggleExpanded();
                rebuildDisplayList();
            });
        }

        /**
         * 根据分类名称获取对应的 emoji 图标
         */
        private String getCategoryEmoji(String category) {
            if (category == null)
                return "🍽️";
            if (category.contains("主食"))
                return "🍜";
            if (category.contains("家常菜"))
                return "🥗";
            if (category.contains("蛋白质"))
                return "🥩";
            if (category.contains("蔬菜") || category.contains("水果"))
                return "🍎";
            if (category.contains("零食") || category.contains("饮品"))
                return "🍫";
            return "🍽️"; // 默认图标
        }
    }

    // Item ViewHolder
    class ItemViewHolder extends RecyclerView.ViewHolder {
        ItemFoodLibraryBinding binding;

        ItemViewHolder(ItemFoodLibraryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FoodLibrary food) {
            binding.tvName.setText(food.getName());
            binding.tvDetails.setText(food.getCaloriesPer100g() + " 千卡/100g · " +
                    food.getWeightPerUnit() + "g/" + food.getServingUnit());

            binding.tvMacros.setText(String.format("蛋白质: %.1fg · 碳水: %.1fg",
                    food.getProteinPer100g(), food.getCarbsPer100g()));

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(food);
                }
            });
        }
    }
}
