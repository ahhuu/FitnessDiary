package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;

public class DetailRecordAdapter extends ListAdapter<DetailRecordAdapter.Item, DetailRecordAdapter.VH> {

    public interface OnItemActionListener {
        void onClick(Item item);
        void onLongClick(Item item);
    }

    private final OnItemActionListener listener;

    public DetailRecordAdapter(OnItemActionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_detail_record, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Item item = getItem(position);
        holder.tvTitle.setText(item.title);
        holder.tvSubtitle.setText(item.subtitle);
        holder.tvValue.setText(item.value);
        holder.tvStatus.setText(item.status);

        if (item.iconRes != 0) {
            holder.ivIcon.setImageResource(item.iconRes);
            holder.ivIcon.setVisibility(View.VISIBLE);
        } else {
            holder.ivIcon.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onClick(item));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(item);
            return true;
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvSubtitle;
        TextView tvValue;
        TextView tvStatus;

        VH(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            tvValue = itemView.findViewById(R.id.tv_value);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }

    public static class Item {
        public long id;
        public String title;
        public String subtitle;
        public String value;
        public String status;
        public int iconRes;
        public Object payload;

        public Item(long id, String title, String subtitle, String value, String status, int iconRes, Object payload) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.value = value;
            this.status = status;
            this.iconRes = iconRes;
            this.payload = payload;
        }
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<Item>() {
        @Override
        public boolean areItemsTheSame(@NonNull Item oldItem, @NonNull Item newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Item oldItem, @NonNull Item newItem) {
            return oldItem.id == newItem.id
                    && safeEquals(oldItem.title, newItem.title)
                    && safeEquals(oldItem.subtitle, newItem.subtitle)
                    && safeEquals(oldItem.value, newItem.value)
                    && safeEquals(oldItem.status, newItem.status)
                    && oldItem.iconRes == newItem.iconRes;
        }

        private boolean safeEquals(String a, String b) {
            if (a == null) return b == null;
            return a.equals(b);
        }
    };
}
