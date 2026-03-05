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

public class LineSparkView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<Float> values = new ArrayList<>();

    public LineSparkView(Context context) {
        super(context);
        init();
    }

    public LineSparkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineSparkView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linePaint.setColor(0xFF2ECC71);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));

        gridPaint.setColor(0x1A000000);
        gridPaint.setStyle(Paint.Style.STROKE);
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

        if (values.size() < 2) return;

        float min = values.get(0);
        float max = values.get(0);
        for (Float v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (Math.abs(max - min) < 0.001f) {
            max = min + 1f;
        }

        path.reset();
        for (int i = 0; i < values.size(); i++) {
            float x = left + (right - left) * i / (values.size() - 1f);
            float normalized = (values.get(i) - min) / (max - min);
            float y = bottom - normalized * (bottom - top);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, linePaint);
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
