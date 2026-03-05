package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BarSparkView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> values = new ArrayList<>();

    public BarSparkView(Context context) {
        super(context);
        init();
    }

    public BarSparkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BarSparkView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(0xFF7E57C2);
        barPaint.setStyle(Paint.Style.FILL);
        gridPaint.setColor(0x1A000000);
        gridPaint.setStrokeWidth(dp(1));
    }

    public void setValues(List<Float> points) {
        values.clear();
        if (points != null) {
            values.addAll(points);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float p = dp(8);
        float left = p;
        float top = p;
        float right = w - p;
        float bottom = h - p;

        for (int i = 1; i <= 3; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        if (values.isEmpty()) return;

        float max = 0f;
        for (Float v : values) max = Math.max(max, v);
        if (max <= 0f) max = 1f;

        float space = dp(6);
        float totalSpace = space * (values.size() - 1);
        float barW = (right - left - totalSpace) / values.size();
        if (barW < dp(4)) barW = dp(4);

        float x = left;
        for (Float value : values) {
            float ratio = Math.max(0f, value / max);
            float bh = ratio * (bottom - top);
            RectF rect = new RectF(x, bottom - bh, x + barW, bottom);
            canvas.drawRoundRect(rect, dp(4), dp(4), barPaint);
            x += barW + space;
        }
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
