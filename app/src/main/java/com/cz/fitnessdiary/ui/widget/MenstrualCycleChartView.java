package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MenstrualCycleChartView extends View {

    public static class CycleBarData {
        public long startDate;
        public int durationDays;
        public String flowIntensity; // LIGHT, MEDIUM, HEAVY

        public CycleBarData(long startDate, int durationDays, String flowIntensity) {
            this.startDate = startDate;
            this.durationDays = durationDays;
            this.flowIntensity = flowIntensity;
        }
    }

    private List<CycleBarData> cycles = new ArrayList<>();
    private float avgCycleLength = 0;

    private Paint barPaint;
    private Paint textPaint;
    private Paint axisPaint;
    private Paint avgLinePaint;

    private int paddingBottom = 80;
    private int paddingLeft = 60;
    private int paddingTop = 40;
    private int paddingRight = 40;

    private int minDuration = 0;
    private int maxDuration = 40;

    private SimpleDateFormat dateFmt = new SimpleDateFormat("MM/dd", Locale.getDefault());

    public MenstrualCycleChartView(Context context) {
        super(context);
        init();
    }

    public MenstrualCycleChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF757575);
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(0xFFE0E0E0);
        axisPaint.setStrokeWidth(2f);

        avgLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avgLinePaint.setColor(0xFFE040FB);
        avgLinePaint.setStyle(Paint.Style.STROKE);
        avgLinePaint.setStrokeWidth(3f);
        avgLinePaint.setPathEffect(new DashPathEffect(new float[] { 12, 8 }, 0));
    }

    public void setData(List<CycleBarData> data, float avgCycleLen) {
        this.cycles.clear();
        if (data != null) this.cycles.addAll(data);
        this.avgCycleLength = avgCycleLen;

        if (cycles.isEmpty()) {
            minDuration = 0;
            maxDuration = 40;
        } else {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (CycleBarData c : cycles) {
                if (c.durationDays < min) min = c.durationDays;
                if (c.durationDays > max) max = c.durationDays;
            }
            minDuration = Math.max(0, min - 3);
            maxDuration = max + 5;
            if (avgCycleLength > maxDuration) maxDuration = (int) avgCycleLength + 5;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        int chartWidth = width - paddingLeft - paddingRight;
        int chartHeight = height - paddingTop - paddingBottom;

        // Y axis labels
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            int days = minDuration + (maxDuration - minDuration) * i / 4;
            float y = height - paddingBottom - (chartHeight * i / 4f);
            canvas.drawText(days + "天", paddingLeft - 10, y + 10, textPaint);
            if (i > 0) {
                canvas.drawLine(paddingLeft, y, width - paddingRight, y, axisPaint);
            }
        }
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height - paddingBottom, axisPaint);

        // X axis
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, axisPaint);

        if (cycles.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无数据", width / 2f, height / 2f, textPaint);
            return;
        }

        // Average line
        if (avgCycleLength > 0 && avgCycleLength >= minDuration && avgCycleLength <= maxDuration) {
            float avgY = height - paddingBottom - chartHeight * ((avgCycleLength - minDuration) / (float) (maxDuration - minDuration));
            canvas.drawLine(paddingLeft, avgY, width - paddingRight, avgY, avgLinePaint);
            textPaint.setColor(0xFFE040FB);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("平均 " + String.format(Locale.getDefault(), "%.1f天", avgCycleLength),
                    width - paddingRight + 8, avgY + 8, textPaint);
            textPaint.setColor(0xFF757575);
        }

        // Bars
        textPaint.setTextAlign(Paint.Align.CENTER);
        float barWidth = Math.min(80f, (float) chartWidth / cycles.size() * 0.7f);
        float gap = (float) chartWidth / cycles.size();
        int maxBars = Math.min(cycles.size(), 12); // show last 12 cycles
        int startIdx = cycles.size() - maxBars;

        for (int i = 0; i < maxBars; i++) {
            CycleBarData c = cycles.get(startIdx + i);
            float x = paddingLeft + i * gap + gap / 2f;

            // Bar color by flow intensity
            int color;
            if ("HEAVY".equals(c.flowIntensity)) {
                color = 0xFFC62828;
            } else if ("MEDIUM".equals(c.flowIntensity)) {
                color = 0xFFE040FB;
            } else {
                color = 0xFFCE93D8;
            }
            barPaint.setColor(color);

            float barH = chartHeight * ((c.durationDays - minDuration) / (float) (maxDuration - minDuration));
            float barTop = height - paddingBottom - barH;
            canvas.drawRect(x - barWidth / 2f, barTop, x + barWidth / 2f, height - paddingBottom, barPaint);

            // Duration label
            canvas.drawText(c.durationDays + "天", x, barTop - 8, textPaint);

            // Date label
            textPaint.setTextSize(22f);
            canvas.drawText(dateFmt.format(new Date(c.startDate)), x, height - paddingBottom + 36, textPaint);
            textPaint.setTextSize(28f);
        }
    }
}
