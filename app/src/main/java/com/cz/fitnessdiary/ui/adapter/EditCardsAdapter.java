package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Collections;
import java.util.List;

public class EditCardsAdapter extends RecyclerView.Adapter<EditCardsAdapter.ViewHolder> {

    public static class CardConfig {
        public String id;
        public String name;
        public boolean visible;

        public CardConfig(String id, String name, boolean visible) {
            this.id = id;
            this.name = name;
            this.visible = visible;
        }
    }

    private final List<CardConfig> configs;

    public EditCardsAdapter(List<CardConfig> configs) {
        this.configs = configs;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_edit_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CardConfig config = configs.get(position);
        holder.tvName.setText(config.name);
        holder.ivCardIcon.setImageResource(resolveIconRes(config.id));
        holder.ivCardIcon.setImageTintList(null);
        holder.switchVisibility.setChecked(config.visible);
        holder.switchVisibility.setOnCheckedChangeListener((b, checked) -> config.visible = checked);
    }

    @Override
    public int getItemCount() {
        return configs.size();
    }

    public void moveItem(int from, int to) {
        if (from < to) {
            for (int i = from; i < to; i++) {
                Collections.swap(configs, i, i + 1);
            }
        } else {
            for (int i = from; i > to; i--) {
                Collections.swap(configs, i, i - 1);
            }
        }
        notifyItemMoved(from, to);
    }

    public List<CardConfig> getConfigs() {
        return configs;
    }

    private int resolveIconRes(String id) {
        if ("water".equals(id)) {
            return R.drawable.ic_hero_water;
        }
        if ("sleep".equals(id)) {
            return R.drawable.ic_hero_moon;
        }
        if ("habit".equals(id)) {
            return R.drawable.ic_hero_calendar;
        }
        if ("medication".equals(id)) {
            return R.drawable.ic_hero_medication;
        }
        if ("weight".equals(id)) {
            return R.drawable.ic_hero_weight;
        }
        if ("custom".equals(id)) {
            return R.drawable.ic_edit;
        }
        return R.drawable.ic_hero_dumbbell;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        MaterialSwitch switchVisibility;
        ImageView ivHandle;
        ImageView ivCardIcon;

        ViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_card_name);
            switchVisibility = v.findViewById(R.id.switch_visibility);
            ivHandle = v.findViewById(R.id.iv_drag_handle);
            ivCardIcon = v.findViewById(R.id.iv_card_icon);
        }
    }
}
