package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;

import java.util.List;

public class QuickAddAdapter extends RecyclerView.Adapter<QuickAddAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public static class Item {
        public String title;
        public int iconRes;
        public int color;

        public Item(String title, int iconRes, int color) {
            this.title = title;
            this.iconRes = iconRes;
            this.color = color;
        }
    }

    private final List<Item> items;
    private final OnItemClickListener listener;

    public QuickAddAdapter(List<Item> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quick_add, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = items.get(position);
        holder.tvTitle.setText(item.title);
        holder.ivIcon.setImageResource(item.iconRes);
        holder.ivIcon.setImageTintList(null);
        holder.ivBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(item.color));
        holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        View ivBg;
        TextView tvTitle;

        ViewHolder(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_icon);
            ivBg = v.findViewById(R.id.iv_bg);
            tvTitle = v.findViewById(R.id.tv_title);
        }
    }
}
