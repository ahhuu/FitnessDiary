package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.model.SmartSuggestionItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmartSuggestionAdapter extends RecyclerView.Adapter<SmartSuggestionAdapter.ViewHolder> {

    private final List<SmartSuggestionItem> items = new ArrayList<>();

    public void submitList(List<SmartSuggestionItem> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_smart_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmartSuggestionItem item = items.get(position);
        holder.tvPrompt.setText(item.getPrompt());
        holder.tvResponse.setText(item.getResponse());
        String time = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(item.getTimestamp()));
        holder.tvTime.setText(time);

        String status = item.getActionLabel();
        if (status == null || status.trim().isEmpty()) {
            status = item.isExecuted() ? "已执行" : "未执行";
        }
        holder.tvStatus.setText(status);
        int color = item.isExecuted()
                ? holder.itemView.getContext().getColor(R.color.plan_blue_primary)
                : holder.itemView.getContext().getColor(R.color.text_secondary);
        holder.tvStatus.setTextColor(color);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPrompt;
        TextView tvResponse;
        TextView tvTime;
        TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPrompt = itemView.findViewById(R.id.tv_prompt);
            tvResponse = itemView.findViewById(R.id.tv_response);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}
