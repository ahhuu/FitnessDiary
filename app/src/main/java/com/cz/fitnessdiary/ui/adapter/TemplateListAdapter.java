package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.model.TrainingTemplate;

import java.util.ArrayList;
import java.util.List;

public class TemplateListAdapter extends RecyclerView.Adapter<TemplateListAdapter.ViewHolder> {

    private List<TrainingTemplate> templates = new ArrayList<>();
    private List<TrainingTemplate> filteredTemplates = new ArrayList<>();
    private String currentFilter = "全部";
    private OnTemplateClickListener listener;

    public interface OnTemplateClickListener {
        void onTemplateClick(TrainingTemplate template);
    }

    public void setOnTemplateClickListener(OnTemplateClickListener listener) {
        this.listener = listener;
    }

    public void setTemplates(List<TrainingTemplate> templates) {
        this.templates = templates != null ? templates : new ArrayList<>();
        applyFilter(currentFilter);
    }

    public void setFilter(String goal) {
        currentFilter = goal;
        applyFilter(goal);
    }

    private void applyFilter(String goal) {
        filteredTemplates.clear();
        if ("全部".equals(goal)) {
            filteredTemplates.addAll(templates);
        } else {
            for (TrainingTemplate t : templates) {
                if (t.getGoal() != null && t.getGoal().equals(goal)) {
                    filteredTemplates.add(t);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_template_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(filteredTemplates.get(position));
    }

    @Override
    public int getItemCount() {
        return filteredTemplates.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        android.widget.TextView tvName, tvDifficulty, tvGoal, tvDays, tvDescription;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvDifficulty = itemView.findViewById(R.id.tv_difficulty);
            tvGoal = itemView.findViewById(R.id.tv_goal);
            tvDays = itemView.findViewById(R.id.tv_days);
            tvDescription = itemView.findViewById(R.id.tv_description);
        }

        void bind(TrainingTemplate template) {
            tvName.setText(template.getName());

            String diffText;
            switch (template.getDifficulty()) {
                case 1: diffText = "初级"; break;
                case 2: diffText = "中级"; break;
                case 3: diffText = "高级"; break;
                default: diffText = "";
            }
            tvDifficulty.setText(diffText);
            tvGoal.setText(template.getGoal());
            tvDays.setText(template.getDaysPerWeek() + "天/周");
            tvDescription.setText(template.getShortDescription());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTemplateClick(template);
                }
            });
        }
    }
}
