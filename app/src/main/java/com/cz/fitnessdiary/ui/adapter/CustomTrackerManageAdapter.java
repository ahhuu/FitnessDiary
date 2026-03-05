package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.CustomTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CustomTrackerManageAdapter extends RecyclerView.Adapter<CustomTrackerManageAdapter.VH> {

    public interface Listener {
        void onOpen(CustomTracker tracker);
        void onToggle(CustomTracker tracker, boolean enabled);
        void onMoveUp(int position);
        void onMoveDown(int position);
    }

    public static class TrackerMeta {
        public int todayCount;
        public String latestText;

        public TrackerMeta(int todayCount, String latestText) {
            this.todayCount = todayCount;
            this.latestText = latestText;
        }
    }

    private final Listener listener;
    private final List<CustomTracker> items = new ArrayList<>();
    private Map<Long, TrackerMeta> metaMap;

    public CustomTrackerManageAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<CustomTracker> list, Map<Long, TrackerMeta> metaMap) {
        items.clear();
        if (list != null) items.addAll(list);
        this.metaMap = metaMap;
        notifyDataSetChanged();
    }

    public List<CustomTracker> getCurrentItems() {
        return items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_custom_tracker_manage, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CustomTracker tracker = items.get(position);
        holder.tvName.setText(tracker.getName());
        TrackerMeta meta = metaMap == null ? null : metaMap.get(tracker.getId());
        String latest = meta == null || meta.latestText == null ? "暂无更新" : meta.latestText;
        int todayCount = meta == null ? 0 : meta.todayCount;
        holder.tvMeta.setText("今日 " + todayCount + " 条 · " + latest + " · " + (tracker.getUnit() == null ? "次" : tracker.getUnit()));
        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(tracker.isEnabled());
        holder.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onToggle(tracker, isChecked));
        holder.btnUp.setEnabled(position > 0);
        holder.btnDown.setEnabled(position < getItemCount() - 1);
        holder.btnUp.setOnClickListener(v -> listener.onMoveUp(position));
        holder.btnDown.setOnClickListener(v -> listener.onMoveDown(position));
        holder.itemView.setOnClickListener(v -> listener.onOpen(tracker));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvMeta;
        Switch switchEnabled;
        ImageButton btnUp;
        ImageButton btnDown;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvMeta = itemView.findViewById(R.id.tv_meta);
            switchEnabled = itemView.findViewById(R.id.switch_enabled);
            btnUp = itemView.findViewById(R.id.btn_up);
            btnDown = itemView.findViewById(R.id.btn_down);
        }
    }
}
