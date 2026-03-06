package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SleepChartView extends View {

    private Paint axisPaint;
    private Paint textPaint;
    private Paint barPaint;
    private Paint avgLinePaint;

    private List<Float> dataPoints = new ArrayList<>();
    private List<String> xLabels = new ArrayList<>();
    private float averageHours = 0f;

    private int paddingBottom = 60;
    private int paddingLeft = 80;
    private int paddingTop = 40;
    private int paddingRight = 40;
    private float maxHours = 10f;

    public SleepChartView(Context context) {
        super(context);
        init();
    }

    public SleepChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(0xFFE0E0E0);
        axisPaint.setStrokeWidth(2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF757575);
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(0xFF673AB7); // cat_sleep_primary
        barPaint.setStyle(Paint.Style.FILL);

        avgLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avgLinePaint.setColor(0xFF673AB7);
        avgLinePaint.setStrokeWidth(3f);
        avgLinePaint.setStyle(Paint.Style.STROKE);
        avgLinePaint.setPathEffect(new DashPathEffect(new float[] { 10f, 10f }, 0f));
    }

    public void setData(List<Float> hours, List<String> labels, float avg) {
        this.dataPoints.clear();
        this.dataPoints.addAll(hours);
        this.xLabels.clear();
        this.xLabels.addAll(labels);
        this.averageHours = avg;

        maxHours = 0f;
        for (Float h : dataPoints) {
            if (h != null && h > maxHours)
                maxHours = h;
        }
        if (maxHours < 8f)
            maxHours = 8f;
        maxHours = (float) Math.ceil(maxHours) + 1f;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        int chartWidth = width - paddingLeft - paddingRight;
        int chartHeight = height - paddingTop - paddingBottom;

        // Draw Y Axis labels and grid lines
        textPaint.setTextAlign(Paint.Align.RIGHT);
        int ySteps = 5;
        for (int i = 0; i <= ySteps; i++) {
            float val = maxHours * i / ySteps;
            float y = height - paddingBottom - (chartHeight * i / ySteps);
            canvas.drawText(String.format(Locale.getDefault(), "%.1fh", val), paddingLeft - 10, y + 10, textPaint);
            if (i > 0) {
                canvas.drawLine(paddingLeft, y, width - paddingRight, y, axisPaint); // Grid lines
            }
        }
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height - paddingBottom, axisPaint); // Y Axis

        // Draw X Axis
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, axisPaint);

        if (dataPoints.isEmpty() || xLabels.isEmpty())
            return;

        // Draw Bars and X Labels
        textPaint.setTextAlign(Paint.Align.CENTER);
        float stepX = (float) chartWidth / dataPoints.size();
        float barWidth = stepX * 0.5f;
        if (barWidth > 60f)
            barWidth = 60f;

        for (int i = 0; i < dataPoints.size(); i++) {
            float centerX = paddingLeft + (i + 0.5f) * stepX;
            Float val = dataPoints.get(i);

            // X Label (skip some if too many)
            if (dataPoints.size() <= 7 || i % (dataPoints.size() / 5) == 0 || i == dataPoints.size() - 1) {
                String label = i < xLabels.size() ? xLabels.get(i) : "";
                canvas.drawText(label, centerX, height - paddingBottom + 40, textPaint);
            }

            if (val != null && val > 0) {
                float barH = chartHeight * (val / maxHours);
                float top = height - paddingBottom - barH;
                float left = centerX - barWidth / 2f;
                float right = centerX + barWidth / 2f;
                float bottom = height - paddingBottom;

                // Draw rounded rect bar
                canvas.drawRoundRect(left, top, right, bottom, 10f, 10f, barPaint);
                // Fix rounded bottom
                canvas.drawRect(left, bottom - 10f, right, bottom, barPaint);
            }
        }

        // Draw Average Line
        if (averageHours > 0) {
            float avgY = height - paddingBottom - (chartHeight * (averageHours / maxHours));
            Path path = new Path();
            path.moveTo(paddingLeft, avgY);
            path.lineTo(width - paddingRight, avgY);
            canvas.drawPath(path, avgLinePaint);

            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(String.format(Locale.getDefault(), "avg: %.1fh", averageHours),
                    width - paddingRight - 120, avgY - 10, textPaint);
        }
    }
}
