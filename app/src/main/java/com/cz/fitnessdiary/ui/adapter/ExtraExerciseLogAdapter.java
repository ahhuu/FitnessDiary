package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ExtraExerciseLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExtraExerciseLogAdapter extends RecyclerView.Adapter<ExtraExerciseLogAdapter.ViewHolder> {

    public interface Listener {
        void onEdit(ExtraExerciseLog log);

        void onDelete(ExtraExerciseLog log);
    }

    private final Listener listener;
    private List<ExtraExerciseLog> logs = new ArrayList<>();

    public ExtraExerciseLogAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<ExtraExerciseLog> logs) {
        this.logs = logs == null ? new ArrayList<>() : new ArrayList<>(logs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_extra_exercise_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExtraExerciseLog log = logs.get(position);
        holder.name.setText(log.getName());
        holder.details.setText(formatMetrics(log.getSets(), log.getReps(), log.getWeight(), log.getDuration()));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEdit(log);
            }
        });
        holder.edit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEdit(log);
            }
        });
        holder.delete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(log);
            }
        });
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    private String formatMetrics(int sets, int reps, float weight, int duration) {
        StringBuilder builder = new StringBuilder();
        if (sets > 0 || reps > 0) {
            builder.append(sets).append(" 组 × ").append(reps).append(" 次");
        }
        if (weight > 0) {
            appendSeparator(builder);
            builder.append(String.format(Locale.getDefault(), "%.1f kg", weight));
        }
        if (duration > 0) {
            appendSeparator(builder);
            builder.append(duration).append(" 秒");
        }
        return builder.length() == 0 ? "已完成 · 未填写明细" : "实际 " + builder;
    }

    private void appendSeparator(StringBuilder builder) {
        if (builder.length() > 0) {
            builder.append(" · ");
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final android.widget.TextView name;
        final android.widget.TextView details;
        final android.widget.ImageButton edit;
        final android.widget.ImageButton delete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_extra_exercise_name);
            details = itemView.findViewById(R.id.tv_extra_exercise_details);
            edit = itemView.findViewById(R.id.btn_edit_extra_exercise);
            delete = itemView.findViewById(R.id.btn_delete_extra_exercise);
        }
    }
}
