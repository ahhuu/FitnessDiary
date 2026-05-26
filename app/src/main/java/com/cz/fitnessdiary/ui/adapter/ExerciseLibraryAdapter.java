package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.cz.fitnessdiary.model.ExerciseGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExerciseLibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<Object> displayList = new ArrayList<>();
    private List<ExerciseGroup> groupList = new ArrayList<>();
    private OnExerciseClickListener listener;

    public interface OnExerciseClickListener {
        void onExerciseClick(ExerciseLibrary exercise);
    }

    public void setOnExerciseClickListener(OnExerciseClickListener listener) {
        this.listener = listener;
    }

    public void setGroupList(List<ExerciseGroup> groups) {
        this.groupList = groups != null ? groups : new ArrayList<>();
        rebuildDisplayList();
    }

    public void setExercises(List<ExerciseLibrary> exercises) {
        Map<String, List<ExerciseLibrary>> grouped = new LinkedHashMap<>();
        if (exercises != null) {
            for (ExerciseLibrary ex : exercises) {
                String key = ex.getBodyPart() != null ? ex.getBodyPart() : "其他";
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(ex);
            }
        }

        List<ExerciseGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<ExerciseLibrary>> entry : grouped.entrySet()) {
            groups.add(new ExerciseGroup(entry.getKey(), entry.getValue()));
        }
        setGroupList(groups);
    }

    private void rebuildDisplayList() {
        List<Object> newList = new ArrayList<>();
        for (ExerciseGroup group : groupList) {
            newList.add(group);
            if (group.isExpanded()) {
                newList.addAll(group.getExercises());
            }
        }
        displayList = newList;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return displayList.get(position) instanceof ExerciseGroup ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_exercise_group_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_exercise_library_item, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((ExerciseGroup) displayList.get(position));
        } else if (holder instanceof ItemViewHolder) {
            ((ItemViewHolder) holder).bind((ExerciseLibrary) displayList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        android.widget.TextView tvGroupName, tvCount;
        android.widget.ImageView ivExpandIcon;

        HeaderViewHolder(View itemView) {
            super(itemView);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            tvCount = itemView.findViewById(R.id.tv_count);
            ivExpandIcon = itemView.findViewById(R.id.iv_expand_icon);
        }

        void bind(ExerciseGroup group) {
            tvGroupName.setText(group.getBodyPart());
            int count = group.getExercises().size();
            tvCount.setText(count + " 个动作");

            ivExpandIcon.setRotation(group.isExpanded() ? 180f : 0f);

            itemView.setOnClickListener(v -> {
                group.toggleExpanded();
                rebuildDisplayList();
            });
        }
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {
        android.widget.TextView tvExerciseName, tvDifficulty, tvDescription, tvBodyPart, tvEquipment;

        ItemViewHolder(View itemView) {
            super(itemView);
            tvExerciseName = itemView.findViewById(R.id.tv_exercise_name);
            tvDifficulty = itemView.findViewById(R.id.tv_difficulty);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvBodyPart = itemView.findViewById(R.id.tv_body_part);
            tvEquipment = itemView.findViewById(R.id.tv_equipment);
        }

        void bind(ExerciseLibrary exercise) {
            tvExerciseName.setText(exercise.getName());

            String diffText;
            switch (exercise.getDifficulty()) {
                case 1: diffText = "初级"; break;
                case 2: diffText = "中级"; break;
                case 3: diffText = "高级"; break;
                default: diffText = "";
            }
            tvDifficulty.setText(diffText);

            if (exercise.getDescription() != null && !exercise.getDescription().isEmpty()) {
                tvDescription.setText(exercise.getDescription());
                tvDescription.setVisibility(View.VISIBLE);
            } else {
                tvDescription.setVisibility(View.GONE);
            }

            tvBodyPart.setText(exercise.getBodyPart());
            tvEquipment.setText(exercise.getEquipment());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExerciseClick(exercise);
                }
            });
        }
    }
}
