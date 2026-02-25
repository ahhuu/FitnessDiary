package com.cz.fitnessdiary.ui.adapter;

import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.ItemTrainingPlanBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 训练计划列表适配器 - 2.0 升级版
 * 显示组数、次数和媒体缩略图
 */
public class TrainingPlanAdapter extends RecyclerView.Adapter<TrainingPlanAdapter.ViewHolder> {

    private List<TrainingPlan> planList = new ArrayList<>();
    private OnPlanClickListener listener;

    public interface OnPlanClickListener {
        void onPlanClick(TrainingPlan plan);
        void onPlanDelete(TrainingPlan plan);
    }

    public TrainingPlanAdapter(OnPlanClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<TrainingPlan> plans) {
        this.planList = plans;
        notifyDataSetChanged();
    }

    public void setPlans(List<TrainingPlan> plans) {
        setData(plans);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTrainingPlanBinding binding = ItemTrainingPlanBinding.inflate(
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
        private ItemTrainingPlanBinding binding;

        ViewHolder(ItemTrainingPlanBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TrainingPlan plan) {
            binding.tvPlanName.setText(plan.getName());
            binding.tvPlanDescription.setText(plan.getDescription());

            // 格式化创建时间 - Compact Layout 移除显示
            // SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm",
            // Locale.getDefault());
            // binding.tvCreateTime.setText(sdf.format(new Date(plan.getCreateTime())));

            // 显示组数和次数/时长 (v1.2: 针对 1次 + 有时长的计划优化显示)
            if (plan.getSets() > 0) {
                String setsReps;
                if (plan.getReps() == 1 && plan.getDuration() > 0) {
                    setsReps = plan.getSets() + " 组 × " + plan.getDuration() + "s";
                } else {
                    setsReps = plan.getSets() + " 组 × " + plan.getReps() + " 次";
                }
                binding.tvSetsReps.setText(setsReps);
                binding.tvSetsReps.setVisibility(View.VISIBLE);
            } else {
                binding.tvSetsReps.setVisibility(View.GONE);
            }

            // 显示媒体缩略图（2.0 新功能 - 优化版）
            if (!TextUtils.isEmpty(plan.getMediaUri())) {
                // 有图片：彻底清除 Tint 和 背景，防止遮挡
                binding.ivMediaThumbnail.setImageTintList(null);
                binding.ivMediaThumbnail.clearColorFilter(); // 关键：清除滤镜
                binding.ivMediaThumbnail.setBackground(null); // 关键：清除背景
                binding.ivMediaThumbnail.setPadding(0, 0, 0, 0);
                binding.ivMediaThumbnail.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);

                // 关键修复：在 Glide 加载前必须设置为可见，否则 Glide 会因为 View 尺寸未知而放弃加载 (size: -2147483648)
                binding.ivMediaThumbnail.setVisibility(View.VISIBLE);

                try {
                    android.util.Log.d("TrainingPlanAdapter",
                            "Loading Image for " + plan.getName() + ": " + plan.getMediaUri());

                    // [v1.1 兼容性修复] 识别是 Content URI 还是本地绝对路径
                    Object loadTarget;
                    if (plan.getMediaUri().startsWith("/")) {
                        loadTarget = new java.io.File(plan.getMediaUri());
                    } else {
                        loadTarget = Uri.parse(plan.getMediaUri());
                    }

                    Glide.with(binding.getRoot().getContext())
                            .load(loadTarget)
                            // 使用原图尺寸或控件测量尺寸
                            .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                            .centerCrop()
                            .placeholder(R.drawable.ic_placeholder_plan)
                            .error(android.R.drawable.ic_menu_close_clear_cancel) // 使用系统自带图标
                            .listener(
                                    new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                        @Override
                                        public boolean onLoadFailed(
                                                @androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                                                Object model,
                                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                boolean isFirstResource) {
                                            android.util.Log.e("TrainingPlanAdapter",
                                                    "Load Failed for: " + plan.getMediaUri(), e);
                                            // 加载失败时显示显眼的错误图标
                                            binding.ivMediaThumbnail.post(() -> {
                                                binding.ivMediaThumbnail.setImageResource(
                                                        android.R.drawable.ic_menu_close_clear_cancel);
                                                binding.ivMediaThumbnail
                                                        .setImageTintList(android.content.res.ColorStateList
                                                                .valueOf(android.graphics.Color.RED));
                                                binding.ivMediaThumbnail
                                                        .setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                                            });
                                            return false; // let Glide handle error placeholder
                                        }

                                        @Override
                                        public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                                Object model,
                                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                com.bumptech.glide.load.DataSource dataSource,
                                                boolean isFirstResource) {
                                            android.util.Log.d("TrainingPlanAdapter",
                                                    "Load Success: " + plan.getMediaUri());
                                            return false;
                                        }
                                    })
                            .into(binding.ivMediaThumbnail);
                } catch (Exception e) {
                    // Fallback just in case
                    binding.ivMediaThumbnail.setImageResource(R.drawable.ic_placeholder_plan);
                }
            } else {
                // 无图片：恢复默认占位样式
                // 设置Tint为主要颜色，增加Padding，CenterInside
                binding.ivMediaThumbnail.setImageResource(R.drawable.ic_placeholder_plan);
                binding.ivMediaThumbnail.setImageTintList(android.content.res.ColorStateList.valueOf(
                        binding.getRoot().getContext().getColor(R.color.fitnessdiary_primary)));

                // 12dp padding
                int padding = (int) (12 * binding.getRoot().getContext().getResources().getDisplayMetrics().density);
                binding.ivMediaThumbnail.setPadding(padding, padding, padding, padding);
                binding.ivMediaThumbnail.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            }
            binding.ivMediaThumbnail.setVisibility(View.VISIBLE); // 始终显示，不再隐藏

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlanClick(plan);
                }
            });

            // 删除按钮点击
            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlanDelete(plan);
                }
            });
        }
    }
}
