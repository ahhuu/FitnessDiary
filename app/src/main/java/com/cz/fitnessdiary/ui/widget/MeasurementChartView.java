package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MeasurementChartView extends View {

    private Paint axisPaint;
    private Paint textPaint;
    private Paint linePaint;
    private Paint dotPaint;

    private List<Float> dataPoints = new ArrayList<>();
    private List<String> xLabels = new ArrayList<>();

    private int paddingBottom = 60;
    private int paddingLeft = 100;
    private int paddingTop = 40;
    private int paddingRight = 40;

    private float minVal = 0f;
    private float maxVal = 100f;
    private int ySteps = 4;
    private String unit = "cm";
    private int lineColor = 0xFF00BCD4;

    public MeasurementChartView(Context context) {
        super(context);
        init();
    }

    public MeasurementChartView(Context context, @Nullable AttributeSet attrs) {
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

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(lineColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(6f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(lineColor);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setLineColor(int color) {
        this.lineColor = color;
        linePaint.setColor(color);
        dotPaint.setColor(color);
        invalidate();
    }

    public void setUnit(String unit) {
        this.unit = unit;
        invalidate();
    }

    public void setData(List<Float> values, List<String> labels) {
        this.dataPoints.clear();
        this.xLabels.clear();

        List<Float> validValues = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Float v = values.get(i);
            this.dataPoints.add(v);
            if (i < labels.size())
                this.xLabels.add(labels.get(i));
            else
                this.xLabels.add("");

            if (v != null && v > 0)
                validValues.add(v);
        }

        if (validValues.isEmpty()) {
            minVal = 0f;
            maxVal = 100f;
            ySteps = 4;
        } else {
            float min = validValues.get(0);
            float max = validValues.get(0);
            for (float v : validValues) {
                if (v < min) min = v;
                if (v > max) max = v;
            }

            float range = Math.max(0.1f, max - min);
            float tickStep = pickNiceTickStep(range);
            float padding = Math.max(tickStep, range * 0.2f);

            minVal = floorToStep(min - padding, tickStep);
            maxVal = ceilToStep(max + padding, tickStep);

            if (minVal < 0) minVal = 0;
            if (maxVal <= minVal) maxVal = minVal + tickStep * 4f;

            ySteps = Math.max(4, (int) Math.ceil((maxVal - minVal) / tickStep));
            ySteps = Math.min(ySteps, 8);
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

        // Y axis labels and grid
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= ySteps; i++) {
            float val = minVal + (maxVal - minVal) * i / ySteps;
            float y = height - paddingBottom - (chartHeight * i / ySteps);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f%s", val, unit), paddingLeft - 10, y + 10, textPaint);
            if (i > 0) {
                canvas.drawLine(paddingLeft, y, width - paddingRight, y, axisPaint);
            }
        }
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height - paddingBottom, axisPaint);

        // X axis
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, axisPaint);

        if (dataPoints.isEmpty()) return;

        textPaint.setTextAlign(Paint.Align.CENTER);
        float stepX = (float) chartWidth / (dataPoints.size() <= 1 ? 1 : (dataPoints.size() - 1));

        Path path = new Path();
        boolean first = true;
        List<Float> pxList = new ArrayList<>();
        List<Float> pyList = new ArrayList<>();

        for (int i = 0; i < dataPoints.size(); i++) {
            float x = paddingLeft + i * stepX;
            Float val = dataPoints.get(i);

            int autoInterval = Math.max(1, dataPoints.size() / 5);
            boolean drawLabel = dataPoints.size() <= 7 || i % autoInterval == 0 || i == dataPoints.size() - 1;
            if (drawLabel) {
                String label = i < xLabels.size() ? xLabels.get(i) : "";
                canvas.drawText(label, x, height - paddingBottom + 40, textPaint);
            }

            if (val != null && val > 0) {
                float y = height - paddingBottom - chartHeight * ((val - minVal) / (maxVal - minVal));
                pxList.add(x);
                pyList.add(y);

                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
        }

        if (!first) {
            canvas.drawPath(path, linePaint);
            for (int i = 0; i < pxList.size(); i++) {
                canvas.drawCircle(pxList.get(i), pyList.get(i), 8f, dotPaint);
            }
        }

        // No-data message
        if (dataPoints.isEmpty() || first) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无数据", width / 2f, height / 2f, textPaint);
        }
    }

    private float pickNiceTickStep(float range) {
        float rough = range / 4f;
        if (rough <= 0.5f) return 0.5f;
        if (rough <= 1f) return 1f;
        if (rough <= 2f) return 2f;
        if (rough <= 5f) return 5f;
        if (rough <= 10f) return 10f;
        return 20f;
    }

    private float floorToStep(float value, float step) {
        return (float) (Math.floor(value / step) * step);
    }

    private float ceilToStep(float value, float step) {
        return (float) (Math.ceil(value / step) * step);
    }
}
