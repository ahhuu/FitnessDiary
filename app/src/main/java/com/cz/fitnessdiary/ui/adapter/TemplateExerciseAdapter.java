package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.model.TemplateExercise;

import java.util.ArrayList;
import java.util.List;

public class TemplateExerciseAdapter extends RecyclerView.Adapter<TemplateExerciseAdapter.ViewHolder> {

    private List<TemplateExercise> exercises = new ArrayList<>();

    public void setExercises(List<TemplateExercise> exercises) {
        this.exercises = exercises != null ? exercises : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        android.view.View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_template_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(exercises.get(position));
    }

    @Override
    public int getItemCount() {
        return exercises.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        android.widget.TextView tvExerciseName, tvSetsReps, tvSchedule, tvCategory;

        ViewHolder(android.view.View itemView) {
            super(itemView);
            tvExerciseName = itemView.findViewById(R.id.tv_exercise_name);
            tvSetsReps = itemView.findViewById(R.id.tv_sets_reps);
            tvSchedule = itemView.findViewById(R.id.tv_schedule);
            tvCategory = itemView.findViewById(R.id.tv_category);
        }

        void bind(TemplateExercise exercise) {
            tvExerciseName.setText(exercise.getName());

            if (exercise.getDuration() > 0) {
                tvSetsReps.setText(exercise.getSets() + " 组 × " + exercise.getDuration() + "秒");
            } else {
                tvSetsReps.setText(exercise.getSets() + " 组 × " + exercise.getReps() + " 次");
            }

            tvSchedule.setText("执行日: " + daysToChinese(exercise.getScheduledDays()));
            tvCategory.setText(exercise.getCategory() != null ? exercise.getCategory() : "");
        }

        private String daysToChinese(String days) {
            if (days == null || days.isEmpty() || days.equals("0")) return "每天";
            String[] dayNames = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            StringBuilder sb = new StringBuilder();
            String[] parts = days.split(",");
            for (String part : parts) {
                try {
                    int d = Integer.parseInt(part.trim());
                    if (d >= 1 && d <= 7) {
                        if (sb.length() > 0) sb.append("、");
                        sb.append(dayNames[d]);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return sb.length() > 0 ? sb.toString() : "每天";
        }
    }
}
