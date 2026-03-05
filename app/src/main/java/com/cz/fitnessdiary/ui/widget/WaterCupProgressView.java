package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class WaterCupProgressView extends View {

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int current = 0;
    private int target = 1600;

    public WaterCupProgressView(Context context) {
        super(context);
        init();
    }

    public WaterCupProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaterCupProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokePaint.setColor(0x553BAAF5);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(6));

        fillPaint.setColor(0x663BAAF5);
        fillPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(0xFF3BAAF5);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(18));
    }

    public void setProgress(int currentMl, int targetMl) {
        this.current = Math.max(0, currentMl);
        this.target = Math.max(1, targetMl);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float p = dp(12);
        RectF cupRect = new RectF(p * 2, p, w - p * 2, h - p);

        Path cupPath = new Path();
        cupPath.moveTo(cupRect.left + dp(12), cupRect.top);
        cupPath.lineTo(cupRect.right - dp(12), cupRect.top);
        cupPath.lineTo(cupRect.right - dp(2), cupRect.bottom);
        cupPath.lineTo(cupRect.left + dp(2), cupRect.bottom);
        cupPath.close();

        float ratio = Math.min(1f, current * 1f / target);
        float fillTop = cupRect.bottom - ratio * (cupRect.height() - dp(4));
        RectF fill = new RectF(cupRect.left + dp(4), fillTop, cupRect.right - dp(4), cupRect.bottom - dp(2));

        canvas.save();
        canvas.clipPath(cupPath);
        canvas.drawRect(fill, fillPaint);
        canvas.restore();

        canvas.drawPath(cupPath, strokePaint);
        canvas.drawText(current + " ml", w / 2f, h / 2f, textPaint);
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
