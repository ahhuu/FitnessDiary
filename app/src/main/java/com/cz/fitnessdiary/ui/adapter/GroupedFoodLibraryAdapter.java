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
import com.cz.fitnessdiary.databinding.ItemFoodMainGroupBinding;
import com.cz.fitnessdiary.model.FoodGroup;
import com.cz.fitnessdiary.model.FoodMainGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分组食物库适配器 (Plan 30: 升级二级目录)
 */
public class GroupedFoodLibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MAIN_HEADER = 0;
    private static final int TYPE_SUB_HEADER = 1;
    private static final int TYPE_ITEM = 2;

    private List<Object> displayList = new ArrayList<>(); // 混合列表
    private List<FoodMainGroup> allMainGroups = new ArrayList<>();
    private OnItemClickListener listener;
    private OnEditClickListener editListener;

    public interface OnItemClickListener {
        void onItemClick(FoodLibrary food);
    }

    public interface OnEditClickListener {
        void onEditClick(FoodLibrary food);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnEditClickListener(OnEditClickListener listener) {
        this.editListener = listener;
    }

    /**
     * 设置原始食物数据并自动二级分组与智能迁移
     */
    public void setFoodList(List<FoodLibrary> allFoods) {
        if (allFoods == null)
            return;

        // 1. 初始化大类逻辑映射 (保持交互顺序)
        Map<String, FoodMainGroup> mainMap = new LinkedHashMap<>();
        mainMap.put("主食", new FoodMainGroup("主食", "🍱"));
        mainMap.put("家常菜", new FoodMainGroup("家常菜", "🍲"));
        mainMap.put("蛋白质", new FoodMainGroup("蛋白质", "🥩"));
        mainMap.put("蔬菜水果", new FoodMainGroup("蔬菜水果", "🥗"));
        mainMap.put("零食饮料", new FoodMainGroup("零食饮料", "🍫"));
        mainMap.put("调料油脂", new FoodMainGroup("调料/油脂", "🧂"));
        mainMap.put("酒精", new FoodMainGroup("酒精", "🍷"));
        mainMap.put("其他", new FoodMainGroup("其他", "❓"));

        // 2. 临时存储二级分组: Map<MainKey, Map<SubName, List<Food>>>
        Map<String, Map<String, List<FoodLibrary>>> structure = new LinkedHashMap<>();
        for (String key : mainMap.keySet()) {
            structure.put(key, new LinkedHashMap<>());
        }

        // 3. 智能迁移逻辑 (正则关键字匹配)
        for (FoodLibrary food : allFoods) {
            String rawCat = food.getCategory();
            if (rawCat == null)
                rawCat = "其他";

            String mainKey = "其他";
            String subName = "其它项目";

            // 规则 A: 分项匹配 (如 "主食: 基础米面")
            if (rawCat.contains(":")) {
                String[] parts = rawCat.split(":");
                String part0 = parts[0].trim();
                subName = parts[1].trim();

                if (part0.contains("主食"))
                    mainKey = "主食";
                else if (part0.contains("菜肴") || part0.contains("家常菜"))
                    mainKey = "家常菜";
                else if (part0.contains("蛋白"))
                    mainKey = "蛋白质";
                else if (part0.contains("蔬菜") || part0.contains("水果"))
                    mainKey = "蔬菜水果";
                else if (part0.contains("零食") || part0.contains("饮料"))
                    mainKey = "零食饮料";
            }
            // 规则 B: 历史数据智能兼容 (处理 "主食 (Staples)" 等情况)
            else if (rawCat.contains("主食")) {
                mainKey = "主食";
                subName = "常用主食";
            } else if (rawCat.contains("Protein") || rawCat.contains("蛋白质")) {
                mainKey = "蛋白质";
                subName = "肉蛋奶";
            } else if (rawCat.contains("Dishes") || rawCat.contains("家常菜")) {
                mainKey = "家常菜";
                subName = "经典菜肴";
            } else if (rawCat.contains("Veg") || rawCat.contains("Fruits")) {
                mainKey = "蔬菜水果";
                subName = "新鲜蔬果";
            } else if (rawCat.contains("Condiment") || rawCat.contains("调料")) {
                mainKey = "调料油脂";
                subName = "调味油脂";
            } else if (rawCat.contains("Alcohol") || rawCat.contains("酒精")) {
                mainKey = "酒精";
                subName = "酒水合集";
            } else if (rawCat.contains("Snacks") || rawCat.contains("饮料")) {
                mainKey = "零食饮料";
                subName = "精选零食";
            }

            if (!structure.get(mainKey).containsKey(subName)) {
                structure.get(mainKey).put(subName, new ArrayList<>());
            }
            structure.get(mainKey).get(subName).add(food);
        }

        // 4. 构建数据模型列表
        allMainGroups.clear();
        for (Map.Entry<String, FoodMainGroup> mainEntry : mainMap.entrySet()) {
            FoodMainGroup mainGroup = mainEntry.getValue();
            Map<String, List<FoodLibrary>> subMap = structure.get(mainEntry.getKey());

            if (!subMap.isEmpty()) {
                for (Map.Entry<String, List<FoodLibrary>> subEntry : subMap.entrySet()) {
                    // 二级目录默认开启折叠
                    FoodGroup subGroup = new FoodGroup(subEntry.getKey(), subEntry.getValue());
                    subGroup.setExpanded(false);
                    mainGroup.addSubGroup(subGroup);
                }
                allMainGroups.add(mainGroup);
            }
        }

        rebuildDisplayList();
    }

    private void rebuildDisplayList() {
        List<Object> newList = new ArrayList<>();
        for (FoodMainGroup main : allMainGroups) {
            if (main == null)
                continue;
            newList.add(main);
            if (main.isExpanded()) {
                for (FoodGroup sub : main.getSubGroups()) {
                    if (sub == null)
                        continue;
                    newList.add(sub);
                    if (sub.isExpanded() && sub.getFoods() != null) {
                        newList.addAll(sub.getFoods());
                    }
                }
            }
        }
        this.displayList = newList;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = displayList.get(position);
        if (item instanceof FoodMainGroup)
            return TYPE_MAIN_HEADER;
        if (item instanceof FoodGroup)
            return TYPE_SUB_HEADER;
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MAIN_HEADER) {
            return new MainHeaderViewHolder(ItemFoodMainGroupBinding.inflate(inflater, parent, false));
        } else if (viewType == TYPE_SUB_HEADER) {
            return new SubHeaderViewHolder(ItemFoodGroupHeaderBinding.inflate(inflater, parent, false));
        } else {
            return new ItemViewHolder(ItemFoodLibraryBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = displayList.get(position);
        if (holder instanceof MainHeaderViewHolder) {
            ((MainHeaderViewHolder) holder).bind((FoodMainGroup) item);
        } else if (holder instanceof SubHeaderViewHolder) {
            ((SubHeaderViewHolder) holder).bind((FoodGroup) item);
        } else {
            ((ItemViewHolder) holder).bind((FoodLibrary) item);
        }
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    // --- ViewHolders ---

    class MainHeaderViewHolder extends RecyclerView.ViewHolder {
        ItemFoodMainGroupBinding binding;

        MainHeaderViewHolder(ItemFoodMainGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FoodMainGroup group) {
            binding.tvMainEmoji.setText(group.getEmoji());
            binding.tvMainName.setText(group.getName());
            binding.tvTotalCount.setText(group.getTotalFoodCount() + " 种");
            binding.ivMainExpandIcon.setRotation(group.isExpanded() ? 90 : 0);
            binding.getRoot().setOnClickListener(v -> {
                group.toggleExpanded();
                v.post(GroupedFoodLibraryAdapter.this::rebuildDisplayList);
            });
        }
    }

    class SubHeaderViewHolder extends RecyclerView.ViewHolder {
        ItemFoodGroupHeaderBinding binding;

        SubHeaderViewHolder(ItemFoodGroupHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FoodGroup group) {
            binding.tvCategoryName.setText(getCategoryEmoji(group.getCategory()) + " " + group.getCategory());
            binding.tvFoodCount.setText(group.getFoodCount() + " 种");
            binding.ivExpandIcon.setRotation(group.isExpanded() ? 90 : 0);
            binding.getRoot().setOnClickListener(v -> {
                group.toggleExpanded();
                v.post(GroupedFoodLibraryAdapter.this::rebuildDisplayList);
            });
        }

        private String getCategoryEmoji(String category) {
            if (category == null)
                return "🔹";
            if (category.contains("米面"))
                return "🍚";
            if (category.contains("粥类"))
                return "🥣";
            if (category.contains("薯类"))
                return "🍠";
            if (category.contains("包子") || category.contains("面点"))
                return "🥯";
            if (category.contains("饺子"))
                return "🥟";
            if (category.contains("汤粉") || category.contains("面条"))
                return "🍜";
            if (category.contains("快餐"))
                return "🍔";
            if (category.contains("荤菜"))
                return "🍱";
            if (category.contains("素菜"))
                return "🥗";
            if (category.contains("蛋奶") || category.contains("豆制品"))
                return "🥛";
            if (category.contains("肉类") || category.contains("海鲜"))
                return "🥩";
            if (category.contains("补剂"))
                return "💪";
            if (category.contains("时蔬"))
                return "🥦";
            if (category.contains("水果"))
                return "🍎";
            if (category.contains("包装"))
                return "🍿";
            if (category.contains("咖啡") || category.contains("奶茶"))
                return "☕";
            return "🔹";
        }
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {
        ItemFoodLibraryBinding binding;

        ItemViewHolder(ItemFoodLibraryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FoodLibrary food) {
            binding.tvName.setText(food.getName());
            binding.tvDetails.setText(food.getCaloriesPer100g() + " kcal/100g · " + food.getWeightPerUnit() + "g/"
                    + food.getServingUnit());
            binding.tvMacros
                    .setText(String.format("蛋白质: %.1fg · 碳水: %.1fg", food.getProteinPer100g(), food.getCarbsPer100g()));

            // 如果设置了编辑监听器，显示编辑按钮
            if (editListener != null) {
                binding.btnEdit.setVisibility(View.VISIBLE);
                binding.btnEdit.setOnClickListener(v -> editListener.onEditClick(food));
            } else {
                binding.btnEdit.setVisibility(View.GONE);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null)
                    listener.onItemClick(food);
            });
        }
    }
}
