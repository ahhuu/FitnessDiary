package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BristolChartView extends View {

    private static final int[] TYPE_COLORS = {
            0x00000000, // unused 0
            0xFFE53935, // type 1 - red (severe constipation)
            0xFFFF7043, // type 2 - deep orange (constipation)
            0xFF66BB6A, // type 3 - green (normal)
            0xFF43A047, // type 4 - dark green (normal ideal)
            0xFFFFCA28, // type 5 - yellow (soft)
            0xFFFF9800, // type 6 - orange (diarrhea)
            0xFFEF5350, // type 7 - red (severe diarrhea)
    };

    private static final String[] TYPE_NAMES = {
            "", "坚果状 (严重便秘)", "干裂香肠状 (便秘)", "玉米状 (正常)",
            "香蕉状 (理想)", "软团状 (偏稀)", "糊状 (腹泻)", "水状 (严重腹泻)"
    };

    private Map<Integer, Integer> counts = new HashMap<>();
    private int maxCount = 1;

    private Paint barPaint;
    private Paint textPaint;
    private Paint labelPaint;
    private Paint bgPaint;

    private int paddingLeft = 200;
    private int paddingRight = 40;
    private int paddingTop = 20;
    private int paddingBottom = 20;

    public BristolChartView(Context context) {
        super(context);
        init();
    }

    public BristolChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF424242);
        textPaint.setTextSize(28f);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF757575);
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.RIGHT);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFFF5F5F5);
    }

    public void setData(Map<Integer, Integer> bristolCounts) {
        this.counts.clear();
        if (bristolCounts != null) this.counts.putAll(bristolCounts);

        maxCount = 1;
        for (int c : counts.values()) {
            if (c > maxCount) maxCount = c;
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        int chartWidth = width - paddingLeft - paddingRight;
        int barHeight = (height - paddingTop - paddingBottom) / 7;
        if (barHeight < 40) barHeight = 40;

        for (int type = 1; type <= 7; type++) {
            int count = counts.containsKey(type) ? counts.get(type) : 0;
            float barWidth = maxCount > 0 ? (float) count / maxCount * chartWidth : 0;

            float top = paddingTop + (type - 1) * barHeight;
            float midY = top + barHeight / 2f;

            // Label - show Chinese name only (no "Type N" prefix)
            labelPaint.setColor(0xFF757575);
            canvas.drawText(TYPE_NAMES[type], paddingLeft - 8, midY + 8, labelPaint);

            // Bar background
            barPaint.setColor(0xFFEEEEEE);
            canvas.drawRect(paddingLeft, top + 4, width - paddingRight, top + barHeight - 4, barPaint);

            // Bar
            if (count > 0) {
                barPaint.setColor(TYPE_COLORS[type]);
                canvas.drawRect(paddingLeft, top + 4, paddingLeft + barWidth, top + barHeight - 4, barPaint);

                // Count label
                textPaint.setColor(0xFF212121);
                canvas.drawText(String.valueOf(count), paddingLeft + barWidth + 12, midY + 10, textPaint);
            } else {
                textPaint.setColor(0xFFBDBDBD);
                canvas.drawText("0", paddingLeft + 8, midY + 10, textPaint);
            }
        }
    }
}
