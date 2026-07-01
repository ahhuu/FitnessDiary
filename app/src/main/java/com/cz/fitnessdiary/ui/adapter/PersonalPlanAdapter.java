package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class PersonalPlanAdapter extends RecyclerView.Adapter<PersonalPlanAdapter.ViewHolder> {

    private List<String> planNames = new ArrayList<>();
    private String activePlanName = "";
    private OnPlanActionListener listener;

    public interface OnPlanActionListener {
        void onSelect(String planName);
        void onDelete(String planName);
        void onRename(String planName);
        int getActionCount(String planName);
    }

    public void setData(List<String> planNames, String activePlanName, OnPlanActionListener listener) {
        this.planNames = planNames != null ? planNames : new ArrayList<>();
        this.activePlanName = activePlanName != null ? activePlanName : "";
        this.listener = listener;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_personal_plan_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String planName = planNames.get(position);
        holder.tvPlanName.setText(planName);

        int actionCount = listener != null ? listener.getActionCount(planName) : 0;
        holder.tvActionCount.setText(actionCount + " 个动作");

        boolean isActive = planName.equals(activePlanName);
        if (isActive) {
            holder.tvStatusBadge.setVisibility(View.VISIBLE);
            holder.btnAction.setVisibility(View.GONE);
        } else {
            holder.tvStatusBadge.setVisibility(View.GONE);
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnAction.setOnClickListener(v -> {
                if (listener != null) listener.onSelect(planName);
            });
        }

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(planName);
        });

        holder.btnRename.setOnClickListener(v -> {
            if (listener != null) listener.onRename(planName);
        });
    }

    @Override
    public int getItemCount() {
        return planNames.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPlanName;
        TextView tvActionCount;
        TextView tvStatusBadge;
        MaterialButton btnAction;
        View btnDelete;
        View btnRename;

        ViewHolder(View view) {
            super(view);
            tvPlanName = view.findViewById(R.id.tv_plan_name);
            tvActionCount = view.findViewById(R.id.tv_action_count);
            tvStatusBadge = view.findViewById(R.id.tv_status_badge);
            btnAction = view.findViewById(R.id.btn_action);
            btnDelete = view.findViewById(R.id.btn_delete);
            btnRename = view.findViewById(R.id.btn_rename);
        }
    }
}
