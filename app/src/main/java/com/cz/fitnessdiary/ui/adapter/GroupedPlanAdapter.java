package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.databinding.ItemPlanGroupHeaderBinding;
import com.cz.fitnessdiary.databinding.ItemTrainingPlanBinding;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.model.PlanGroup;

import java.util.ArrayList;
import java.util.List;
import android.net.Uri;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.cz.fitnessdiary.R;

/**
 * 分组训练计划适配器 - Plan 10
 * 实现可展开/折叠的分组列表
 */
public class GroupedPlanAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<Object> displayList = new ArrayList<>(); // 混合了 Header 和 Plan
    private List<PlanGroup> groupList = new ArrayList<>();
    private OnPlanClickListener listener;

    public interface OnPlanClickListener {
        void onPlanClick(TrainingPlan plan);

        void onPlanDelete(TrainingPlan plan);
    }

    public void setOnPlanClickListener(OnPlanClickListener listener) {
        this.listener = listener;
    }

    // Plan 28: 长按分类标题回调
    private OnCategoryLongClickListener categoryLongClickListener;

    public interface OnCategoryLongClickListener {
        void onCategoryLongClick(String categoryName);
    }

    public void setOnCategoryLongClickListener(OnCategoryLongClickListener listener) {
        this.categoryLongClickListener = listener;
    }

    public void setGroupList(List<PlanGroup> groups) {
        this.groupList = groups;
        rebuildDisplayList();
    }

    private void rebuildDisplayList() {
        displayList.clear();
        for (PlanGroup group : groupList) {
            displayList.add(group); // 添加 Header
            if (group.isExpanded()) {
                displayList.addAll(group.getPlans()); // 添加展开的子项
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = displayList.get(position);
        return (item instanceof PlanGroup) ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            ItemPlanGroupHeaderBinding binding = ItemPlanGroupHeaderBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new HeaderViewHolder(binding);
        } else {
            ItemTrainingPlanBinding binding = ItemTrainingPlanBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new PlanViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = displayList.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((PlanGroup) item);
        } else if (holder instanceof PlanViewHolder) {
            ((PlanViewHolder) holder).bind((TrainingPlan) item);
        }
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    // Header ViewHolder
    class HeaderViewHolder extends RecyclerView.ViewHolder {
        ItemPlanGroupHeaderBinding binding;

        HeaderViewHolder(ItemPlanGroupHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PlanGroup group) {
            binding.tvCategoryName.setText(group.getCategory());
            binding.tvPlanCount.setText(group.getPlanCount() + " 个动作");
            binding.ivExpandIcon.setRotation(group.isExpanded() ? 90 : 0);

            // 设置对应图标
            binding.ivHeaderIcon.setImageResource(getCategoryIcon(group.getCategory()));

            binding.getRoot().setOnClickListener(v -> {
                group.toggleExpanded();
                rebuildDisplayList();
            });

            // Plan 28: 长按修改分类
            binding.getRoot().setOnLongClickListener(v -> {
                if (categoryLongClickListener != null) {
                    categoryLongClickListener.onCategoryLongClick(group.getCategory());
                    return true; // 消费事件，不触发点击折叠
                }
                return false;
            });
        }

        private int getCategoryIcon(String category) {
            if (category == null)
                return com.cz.fitnessdiary.R.drawable.ic_hero_dumbbell;

            String cat = category.trim();
            if (cat.contains("胸"))
                return com.cz.fitnessdiary.R.drawable.ic_muscle_chest;
            if (cat.contains("背"))
                return com.cz.fitnessdiary.R.drawable.ic_muscle_back;
            if (cat.contains("肩"))
                return com.cz.fitnessdiary.R.drawable.ic_muscle_shoulder;
            if (cat.contains("臂") || cat.contains("手"))
                return com.cz.fitnessdiary.R.drawable.ic_muscle_arm;
            if (cat.contains("腿") || cat.contains("臀"))
                return com.cz.fitnessdiary.R.drawable.ic_muscle_leg;
            if (cat.contains("腹") || cat.contains("核心"))
                return com.cz.fitnessdiary.R.drawable.ic_muscle_abs;

            return com.cz.fitnessdiary.R.drawable.ic_hero_dumbbell;
        }
    }

    // Plan Item ViewHolder
    class PlanViewHolder extends RecyclerView.ViewHolder {
        ItemTrainingPlanBinding binding;

        PlanViewHolder(ItemTrainingPlanBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TrainingPlan plan) {
            binding.tvPlanName.setText(plan.getName());

            // 显示组数和次数/时长 (v1.2: 针对 1次 + 有时长的计划优化显示)
            String setsReps;
            if (plan.getReps() == 1 && plan.getDuration() > 0) {
                setsReps = plan.getSets() + " 组 × " + plan.getDuration() + "s";
            } else {
                setsReps = plan.getSets() + " 组 × " + plan.getReps() + " 次";
            }
            binding.tvSetsReps.setText(setsReps);
            binding.tvSetsReps.setVisibility(View.VISIBLE);

            // 显示描述（如果有）
            if (plan.getDescription() != null && !plan.getDescription().isEmpty()) {
                binding.tvPlanDescription.setText(plan.getDescription());
                binding.tvPlanDescription.setVisibility(View.VISIBLE);
            } else {
                binding.tvPlanDescription.setVisibility(View.GONE);
            }

            // Plan 21 & 22: [v1.1 完美适配] 兼容本地路径与着色器清理
            if (plan.getMediaUri() != null && !plan.getMediaUri().isEmpty()) {
                // 有图：彻底清除 Tint、滤镜和背景，防止遮挡
                binding.ivMediaThumbnail.setImageTintList(null);
                binding.ivMediaThumbnail.setColorFilter(null);
                binding.ivMediaThumbnail.clearColorFilter();
                binding.ivMediaThumbnail.setBackground(null);
                binding.ivMediaThumbnail.setPadding(0, 0, 0, 0);
                binding.ivMediaThumbnail.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                binding.ivMediaThumbnail.setVisibility(View.VISIBLE);

                try {
                    // [v1.1 兼容性修复] 识别是 Content URI 还是本地绝对路径
                    Object loadTarget;
                    if (plan.getMediaUri().startsWith("/")) {
                        loadTarget = Uri.fromFile(new java.io.File(plan.getMediaUri()));
                    } else {
                        loadTarget = Uri.parse(plan.getMediaUri());
                    }

                    Glide.with(itemView.getContext())
                            .load(loadTarget)
                            .placeholder(R.drawable.ic_placeholder_plan)
                            .error(android.R.drawable.ic_menu_close_clear_cancel)
                            .centerCrop()
                            .into(binding.ivMediaThumbnail);
                } catch (Exception e) {
                    // 加载失败：恢复默认并着色
                    int primaryColor = ContextCompat.getColor(itemView.getContext(), R.color.fitnessdiary_primary);
                    binding.ivMediaThumbnail.setColorFilter(primaryColor);
                    binding.ivMediaThumbnail.setImageResource(R.drawable.ic_placeholder_plan);
                }
            } else {
                // 无图：恢复默认并着色
                int primaryColor = ContextCompat.getColor(itemView.getContext(), R.color.fitnessdiary_primary);
                binding.ivMediaThumbnail.setColorFilter(primaryColor);
                binding.ivMediaThumbnail.setImageTintList(android.content.res.ColorStateList.valueOf(primaryColor));
                binding.ivMediaThumbnail.setImageResource(R.drawable.ic_placeholder_plan);

                // 恢复默认 Padding 和 ScaleType
                int padding = (int) (12 * itemView.getContext().getResources().getDisplayMetrics().density);
                binding.ivMediaThumbnail.setPadding(padding, padding, padding, padding);
                binding.ivMediaThumbnail.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            }
            binding.ivMediaThumbnail.setVisibility(View.VISIBLE);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlanClick(plan);
                }
            });

            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlanDelete(plan);
                }
            });
        }
    }
}
