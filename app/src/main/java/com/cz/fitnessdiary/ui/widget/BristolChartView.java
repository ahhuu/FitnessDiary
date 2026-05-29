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
import com.cz.fitnessdiary.R;

public class BristolChartView extends View {

    private static final int[] TYPE_COLORS = {
            0x00000000, // unused 0
            0xFFD48280, // type 1 - 莫兰迪暗红 (严重便秘)
            0xFFECA586, // type 2 - 莫兰迪粉橙 (便秘)
            0xFFA1CCA5, // type 3 - 莫兰迪淡草绿 (正常)
            0xFF6E9C73, // type 4 - 莫兰迪雅致绿 (香蕉状理想)
            0xFFE1CD98, // type 5 - 莫兰迪燕麦黄 (偏软)
            0xFFD5A77B, // type 6 - 莫兰迪暖茶色 (腹泻)
            0xFFD4857B, // type 7 - 莫兰迪绛红色 (严重腹泻)
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

    private android.graphics.drawable.Drawable[] mBristolIcons = new android.graphics.drawable.Drawable[8];

    private int paddingLeft = 270;
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
        textPaint.setColor(0xFF3C3730); // 统一对齐项目主色深褐灰
        textPaint.setTextSize(28f);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF8A8276); // 统一对齐项目副色柔灰褐
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.LEFT);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFFFCFAF6); // 使用卡面背景对齐

        for (int type = 1; type <= 7; type++) {
            android.graphics.drawable.Drawable d = androidx.appcompat.content.res.AppCompatResources.getDrawable(getContext(), getBristolIconRes(type));
            if (d != null) {
                d = d.mutate();
                d.setTint(0xFF8A8276);
            }
            mBristolIcons[type] = d;
        }
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

            // Draw Bristol Icon
            int iconSize = 36;
            float iconLeft = 20f;
            float iconTop = midY - iconSize / 2f;
            float iconRight = iconLeft + iconSize;
            float iconBottom = midY + iconSize / 2f;
            android.graphics.drawable.Drawable drawable = mBristolIcons[type];
            if (drawable != null) {
                drawable.setBounds((int) iconLeft, (int) iconTop, (int) iconRight, (int) iconBottom);
                drawable.draw(canvas);
                drawable.setBounds((int) iconLeft, (int) iconTop, (int) iconRight, (int) iconBottom);
                drawable.draw(canvas);
            }

            // Label - 柔灰褐字色
            labelPaint.setColor(0xFF8A8276);
            canvas.drawText(TYPE_NAMES[type], iconRight + 12, midY + 8, labelPaint);

            // Bar background - 升级为平滑内敛的淡暖米灰槽
            barPaint.setColor(0xFFF4F0E6);
            canvas.drawRect(paddingLeft, top + 4, width - paddingRight, top + barHeight - 4, barPaint);

            // Bar
            if (count > 0) {
                barPaint.setColor(TYPE_COLORS[type]);
                canvas.drawRect(paddingLeft, top + 4, paddingLeft + barWidth, top + barHeight - 4, barPaint);

                // Count label - 醒目主色
                textPaint.setColor(0xFF3C3730);
                canvas.drawText(String.valueOf(count), paddingLeft + barWidth + 12, midY + 10, textPaint);
            } else {
                // 无记录时字色淡化
                textPaint.setColor(0xFFC5BEB5);
                canvas.drawText("0", paddingLeft + 8, midY + 10, textPaint);
            }
        }
    }
    private int getBristolIconRes(int type) {
        return com.cz.fitnessdiary.utils.AnalysisUtils.getBristolIconRes(type);
    }
}
