package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.cz.fitnessdiary.utils.DateUtils;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class StreakCalendarView extends View {

    private static final int[] LEVEL_COLORS = {
            0xFFEEEEEE, 0xFFC8E6C9, 0xFF81C784, 0xFF4CAF50, 0xFF2E7D32,
    };

    private static final String[] WEEK_LABELS = {"一", "二", "三", "四", "五", "六", "日"};
    private static final int TOTAL_COLS = 7;
    private static final int TOTAL_ROWS = 6;

    private Map<Long, Integer> dayLevels = new HashMap<>();
    private Paint cellPaint;
    private Paint textPaint;

    public StreakCalendarView(Context context) {
        super(context);
        init();
    }

    public StreakCalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF757575);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setDayLevels(Map<Long, Integer> levels) {
        this.dayLevels.clear();
        if (levels != null) this.dayLevels.putAll(levels);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        long today = DateUtils.getTodayStartTimestamp();
        Calendar cal = Calendar.getInstance();

        // Dynamic sizing: fit 7 cols + labels into available width
        float labelArea = 28f * density;
        float availWidth = width - labelArea - 16f * density;
        int cellAndGap = Math.round(availWidth / TOTAL_COLS);
        int gap = Math.round(3f * density);
        int cs = cellAndGap - gap;
        if (cs < 8) cs = 8;
        int cellTotal = cs + gap;

        float leftMargin = labelArea;
        float topMargin = 20f * density;

        // Start from Monday of 5 weeks ago
        cal.setTimeInMillis(today);
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.add(Calendar.WEEK_OF_YEAR, -(TOTAL_ROWS - 1));
        long startDate = cal.getTimeInMillis();

        // Month labels
        int lastMonth = -1;
        for (int col = 0; col < TOTAL_COLS; col++) {
            long day = startDate + col * TOTAL_ROWS * 86400000L;
            cal.setTimeInMillis(day);
            int month = cal.get(Calendar.MONTH);
            if (month != lastMonth) {
                lastMonth = month;
                float x = leftMargin + col * cellTotal;
                canvas.drawText((month + 1) + "月", x + cs / 2f, topMargin - 4f * density, textPaint);
            }
        }

        float gridTop = topMargin + 10f * density;

        // Weekday labels
        textPaint.setTextSize(20f * density);
        for (int row = 0; row < TOTAL_ROWS; row++) {
            float y = gridTop + row * cellTotal;
            canvas.drawText(WEEK_LABELS[row], leftMargin / 2f, y + cs / 2f + 6f, textPaint);
        }

        // Draw cells
        for (int col = 0; col < TOTAL_COLS; col++) {
            for (int row = 0; row < TOTAL_ROWS; row++) {
                int idx = col * TOTAL_ROWS + row;
                long day = startDate + idx * 86400000L;
                if (day > today) break;

                int level = dayLevels.containsKey(day) ? dayLevels.get(day) : 0;
                level = Math.max(0, Math.min(level, 4));

                float x = leftMargin + col * cellTotal;
                float y = gridTop + row * cellTotal;
                RectF rect = new RectF(x, y, x + cs, y + cs);

                cellPaint.setColor(LEVEL_COLORS[level]);
                canvas.drawRoundRect(rect, 2f * density, 2f * density, cellPaint);
            }
        }
    }
}
