package com.cz.fitnessdiary.ui.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DateNavigatorAdapter extends RecyclerView.Adapter<DateNavigatorAdapter.ViewHolder> {

    public interface OnDateSelectedListener {
        void onDateSelected(long timestamp);
    }

    private final List<Long> dates;
    private long selectedDate;
    private final OnDateSelectedListener listener;
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());
    private final SimpleDateFormat weekFormat = new SimpleDateFormat("E", Locale.getDefault());

    public DateNavigatorAdapter(List<Long> dates, long selectedDate, OnDateSelectedListener listener) {
        this.dates = dates;
        this.selectedDate = selectedDate;
        this.listener = listener;
    }

    public void setSelectedDate(long timestamp) {
        this.selectedDate = DateUtils.getDayStartTimestamp(timestamp);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_date_nav, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        long dateTs = dates.get(position);
        Date date = new Date(dateTs);

        holder.tvDay.setText(dayFormat.format(date));
        holder.tvWeek.setText(weekFormat.format(date));

        boolean isSelected = DateUtils.isSameDay(dateTs, selectedDate);
        boolean isToday = DateUtils.isToday(dateTs);

        if (isSelected) {
            holder.itemView.setBackgroundResource(R.drawable.bg_date_selected);
            holder.tvDay.setTextColor(Color.WHITE);
            holder.tvWeek.setTextColor(Color.WHITE);
        } else {
            holder.itemView.setBackground(null);
            holder.tvDay.setTextColor(holder.itemView.getContext().getColor(R.color.text_primary));
            holder.tvWeek.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
        }

        if (isToday && !isSelected) {
            holder.tvDay.setTextColor(holder.itemView.getContext().getColor(R.color.fitnessdiary_primary));
        }

        holder.itemView.setOnClickListener(v -> {
            listener.onDateSelected(dateTs);
        });
    }

    @Override
    public int getItemCount() {
        return dates.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvWeek;

        ViewHolder(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tv_nav_day);
            tvWeek = v.findViewById(R.id.tv_nav_week);
        }
    }
}
