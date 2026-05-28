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

    private static final int[] GRADIENT_START_COLORS = {
            0xFFF6F3ED, // level 0 (极浅米白灰)
            0xFFCFE3CC, // level 1
            0xFFA8CCA4, // level 2
            0xFF82B57B, // level 3
            0xFF5D9E52  // level 4
    };

    private static final int[] GRADIENT_END_COLORS = {
            0xFFF6F3ED, // level 0 (极浅米白灰)
            0xFFBAD3B6, // level 1
            0xFF90BD8A, // level 2
            0xFF69A361, // level 3
            0xFF44883A  // level 4
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
        textPaint.setColor(0xFF8A8276); // 换成项目统一的温润灰褐色
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
        float labelArea = 24f * density;
        float availWidth = width - labelArea - 8f * density;
        int cellAndGap = Math.round(availWidth / TOTAL_COLS);
        int gap = Math.round(4f * density); // 稍微增大间隙，带来呼吸感
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
        textPaint.setTextSize(11f * density); // 调优月份标签大小，更精致
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
        textPaint.setTextSize(10f * density); // 调优星期标签大小，消除拥挤
        for (int row = 0; row < TOTAL_ROWS; row++) {
            float y = gridTop + row * cellTotal;
            canvas.drawText(WEEK_LABELS[row], leftMargin / 2f, y + cs / 2f + 4f * density, textPaint);
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
                float radius = 4f * density; // 圆角设为 4dp

                if (level == 0) {
                    // 1. 绘制下凹格子槽 (Sunken Box)
                    cellPaint.setStyle(Paint.Style.FILL);
                    cellPaint.setColor(0xFFF6F3ED);
                    canvas.drawRoundRect(rect, radius, radius, cellPaint);

                    // 绘制左上深影
                    cellPaint.setStyle(Paint.Style.STROKE);
                    cellPaint.setStrokeWidth(1f * density);
                    cellPaint.setColor(0x0F000000);
                    canvas.drawLine(rect.left, rect.bottom, rect.left, rect.top, cellPaint);
                    canvas.drawLine(rect.left, rect.top, rect.right, rect.top, cellPaint);

                    // 绘制右下反射白边
                    cellPaint.setColor(0x80FFFFFF);
                    canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, cellPaint);
                    canvas.drawLine(rect.right, rect.top, rect.right, rect.bottom, cellPaint);
                } else {
                    // 2. 绘制打卡凸起立体格子 (Embossed 3D Button)
                    // a. 绘制右下柔和阴影
                    RectF shadowRect = new RectF(rect.left + 1.2f * density, rect.top + 1.2f * density, rect.right + 1.2f * density, rect.bottom + 1.2f * density);
                    cellPaint.setStyle(Paint.Style.FILL);
                    cellPaint.setColor(0x1B000000);
                    canvas.drawRoundRect(shadowRect, radius, radius, cellPaint);

                    // b. 绘制左上亮边白光
                    RectF highlightRect = new RectF(rect.left - 0.6f * density, rect.top - 0.6f * density, rect.right - 0.6f * density, rect.bottom - 0.6f * density);
                    cellPaint.setColor(0x60FFFFFF);
                    canvas.drawRoundRect(highlightRect, radius, radius, cellPaint);

                    // c. 绘制主体渐变
                    android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
                            rect.left, rect.top, rect.right, rect.bottom,
                            GRADIENT_START_COLORS[level], GRADIENT_END_COLORS[level],
                            android.graphics.Shader.TileMode.CLAMP);
                    cellPaint.setShader(gradient);
                    canvas.drawRoundRect(rect, radius, radius, cellPaint);
                    cellPaint.setShader(null);

                    // d. 顶端边缘极细亮线提升高反光质感
                    cellPaint.setColor(0x40FFFFFF);
                    cellPaint.setStyle(Paint.Style.STROKE);
                    cellPaint.setStrokeWidth(0.8f * density);
                    canvas.drawLine(rect.left + radius, rect.top + 0.5f * density, rect.right - radius, rect.top + 0.5f * density, cellPaint);
                }
            }
        }
    }
}
