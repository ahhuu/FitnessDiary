package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class ReminderScheduleAdapter extends RecyclerView.Adapter<ReminderScheduleAdapter.ViewHolder> {

    private List<ReminderSchedule> schedules = new ArrayList<>();
    private OnReminderActionListener listener;

    public interface OnReminderActionListener {
        void onToggle(ReminderSchedule schedule, boolean enabled);
        void onTimeClick(ReminderSchedule schedule);
        void onEdit(ReminderSchedule schedule);
        void onDelete(ReminderSchedule schedule);
    }

    public ReminderScheduleAdapter(OnReminderActionListener listener) {
        this.listener = listener;
    }

    public void setSchedules(List<ReminderSchedule> schedules) {
        this.schedules = schedules != null ? schedules : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reminder_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReminderSchedule schedule = schedules.get(position);
        holder.tvTitle.setText(schedule.getTitle());
        if ("weekly_report".equals(schedule.getModuleType())) {
            String[] days = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
            int dayIndex = 1;
            try {
                String repeatDaysStr = schedule.getRepeatDays();
                if (repeatDaysStr != null && !repeatDaysStr.isEmpty()) {
                    dayIndex = Integer.parseInt(repeatDaysStr.split(",")[0].trim());
                }
            } catch (Exception ignored) {}
            if (dayIndex < 0 || dayIndex > 6) dayIndex = 1;
            holder.tvTime.setText(String.format("⏰ %s %02d:%02d", days[dayIndex], schedule.getHour(), schedule.getMinute()));
        } else {
            holder.tvTime.setText(String.format("⏰ %02d:%02d", schedule.getHour(), schedule.getMinute()));
        }

        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(schedule.isEnabled());
        holder.switchEnabled.setOnCheckedChangeListener((btn, checked) -> {
            schedule.setEnabled(checked);
            if (listener != null) listener.onToggle(schedule, checked);
        });

        holder.tvTime.setOnClickListener(v -> {
            if (listener != null) listener.onTimeClick(schedule);
        });

        holder.btnDelete.setVisibility(schedule.isPreset() ? View.GONE : View.VISIBLE);
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(schedule);
        });
    }

    @Override
    public int getItemCount() { return schedules.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime;
        MaterialSwitch switchEnabled;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_reminder_title);
            tvTime = itemView.findViewById(R.id.tv_reminder_time);
            switchEnabled = itemView.findViewById(R.id.switch_reminder);
            btnDelete = itemView.findViewById(R.id.btn_delete_reminder);
        }
    }
}
