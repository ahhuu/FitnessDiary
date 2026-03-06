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

public class WeightChartView extends View {

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

    private float minWeight = 0f;
    private float maxWeight = 100f;
    private int ySteps = 4;
    private int xLabelInterval = 0;

    public WeightChartView(Context context) {
        super(context);
        init();
    }

    public WeightChartView(Context context, @Nullable AttributeSet attrs) {
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
        linePaint.setColor(0xFFFF5722); // cat_weight_primary
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(6f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(0xFFFF5722);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<Float> weights, List<String> labels) {
        this.dataPoints.clear();
        this.xLabels.clear();

        List<Float> validWeights = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            Float w = weights.get(i);
            this.dataPoints.add(w);
            if (i < labels.size())
                this.xLabels.add(labels.get(i));
            else
                this.xLabels.add("");

            if (w != null && w > 0)
                validWeights.add(w);
        }

        if (validWeights.isEmpty()) {
            minWeight = 40f;
            maxWeight = 100f;
            ySteps = 4;
        } else {
            float min = validWeights.get(0);
            float max = validWeights.get(0);
            for (float w : validWeights) {
                if (w < min)
                    min = w;
                if (w > max)
                    max = w;
            }

            float range = Math.max(0.1f, max - min);
            float tickStep = pickNiceTickStep(range);
            float padding = Math.max(tickStep, range * 0.2f);

            minWeight = floorToStep(min - padding, tickStep);
            maxWeight = ceilToStep(max + padding, tickStep);

            if (minWeight < 0)
                minWeight = 0;
            if (maxWeight <= minWeight)
                maxWeight = minWeight + tickStep * 4f;

            ySteps = Math.max(4, (int) Math.ceil((maxWeight - minWeight) / tickStep));
            ySteps = Math.min(ySteps, 8);
        }

        invalidate();
    }

    public void setXAxisLabelInterval(int interval) {
        xLabelInterval = Math.max(0, interval);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        int chartWidth = width - paddingLeft - paddingRight;
        int chartHeight = height - paddingTop - paddingBottom;

        // Draw Y Axis
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= ySteps; i++) {
            float val = minWeight + (maxWeight - minWeight) * i / ySteps;
            float y = height - paddingBottom - (chartHeight * i / ySteps);
            canvas.drawText(String.format(Locale.getDefault(), "%.1fkg", val), paddingLeft - 10, y + 10, textPaint);
            if (i > 0) {
                canvas.drawLine(paddingLeft, y, width - paddingRight, y, axisPaint); // Grid
            }
        }
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height - paddingBottom, axisPaint); // Y Axis

        // Draw X Axis
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, axisPaint);

        if (dataPoints.isEmpty())
            return;

        textPaint.setTextAlign(Paint.Align.CENTER);
        float stepX = (float) chartWidth / (dataPoints.size() <= 1 ? 1 : (dataPoints.size() - 1));

        Path path = new Path();
        boolean first = true;
        List<Float> currentPx = new ArrayList<>();
        List<Float> currentPy = new ArrayList<>();

        for (int i = 0; i < dataPoints.size(); i++) {
            float x = paddingLeft + i * stepX;
            Float val = dataPoints.get(i);

            // X Label
            boolean shouldDrawXLabel;
            if (xLabelInterval > 1) {
                shouldDrawXLabel = i % xLabelInterval == 0 || i == dataPoints.size() - 1;
            } else {
                int autoInterval = Math.max(1, dataPoints.size() / 5);
                shouldDrawXLabel = dataPoints.size() <= 7 || i % autoInterval == 0 || i == dataPoints.size() - 1;
            }

            if (shouldDrawXLabel) {
                String label = i < xLabels.size() ? xLabels.get(i) : "";
                canvas.drawText(label, x, height - paddingBottom + 40, textPaint);
            }

            if (val != null && val > 0) {
                float y = height - paddingBottom - chartHeight * ((val - minWeight) / (maxWeight - minWeight));
                currentPx.add(x);
                currentPy.add(y);

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
            for (int i = 0; i < currentPx.size(); i++) {
                canvas.drawCircle(currentPx.get(i), currentPy.get(i), 8f, dotPaint);
            }
        }
    }

    private float pickNiceTickStep(float range) {
        float rough = range / 4f;
        if (rough <= 0.5f) {
            return 0.5f;
        }
        if (rough <= 1f) {
            return 1f;
        }
        if (rough <= 2f) {
            return 2f;
        }
        return 5f;
    }

    private float floorToStep(float value, float step) {
        return (float) (Math.floor(value / step) * step);
    }

    private float ceilToStep(float value, float step) {
        return (float) (Math.ceil(value / step) * step);
    }
}
