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
        
        ViewHolder(ItemDailyLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        void bind(TrainingPlan plan) {
            binding.tvPlanName.setText(plan.getName());
            binding.tvPlanDescription.setText(plan.getDescription());
            
            // 设置复选框状态
            boolean isCompleted = completionMap.getOrDefault(plan.getPlanId(), false);
            binding.checkbox.setChecked(isCompleted);
            
            // 复选框更改监听
            binding.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null && buttonView.isPressed()) {
                    listener.onCheckChanged(plan.getPlanId(), isChecked);
                    completionMap.put(plan.getPlanId(), isChecked);
                }
            });
        }
    }
}
