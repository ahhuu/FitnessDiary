package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.cz.fitnessdiary.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 肌肉疲劳度热力图 - 发光轮廓版 (Glowing Silhouette)
 * 绘制一张精美极简人体底图，并在上方叠加带高斯模糊的发光色块。
 */
public class MuscleHeatmapView extends View {

    private Map<String, Float> fatigueMap = new HashMap<>();

    private Paint glowPaint;
    private Bitmap silhouetteBitmap;

    // 颜色配置
    private final int COLOR_NORMAL = Color.TRANSPARENT; // 恢复：透明无光
    private final int COLOR_MILD = Color.parseColor("#FFF59D"); // 浅黄
    private final int COLOR_MODERATE = Color.parseColor("#FFB74D"); // 橙黄
    private final int COLOR_SEVERE = Color.parseColor("#E53935"); // 红色

    public MuscleHeatmapView(Context context) {
        super(context);
        init();
    }

    public MuscleHeatmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // BlurMaskFilter 在硬件加速下有时渲染异常，必须开启软件加速图层
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
        // 使用高斯模糊制造科幻光晕效果
        glowPaint.setMaskFilter(new BlurMaskFilter(35f, BlurMaskFilter.Blur.NORMAL));

        try {
            silhouetteBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg_human_silhouette);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setFatigueMap(Map<String, Float> map) {
        if (map != null) {
            this.fatigueMap.clear();
            this.fatigueMap.putAll(map);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // 1. 绘制底图 (保持比例居中)
        if (silhouetteBitmap != null) {
            int bw = silhouetteBitmap.getWidth();
            int bh = silhouetteBitmap.getHeight();

            // 计算缩放 (以 View 为边界，Fit Center)
            float scale = Math.min((float) width / bw, (float) height / bh);
            int drawW = (int) (bw * scale);
            int drawH = (int) (bh * scale);

            int left = (width - drawW) / 2;
            int top = (height - drawH) / 2;

            Rect src = new Rect(0, 0, bw, bh);
            Rect dst = new Rect(left, top, left + drawW, top + drawH);

            canvas.drawBitmap(silhouetteBitmap, src, dst, null);

            // 2. 叠加发光光晕
            drawGlows(canvas, left, top, drawW, drawH);
        }
    }

    private void drawGlows(Canvas canvas, int left, int top, int drawW, int drawH) {
        float cx = left + drawW / 2f; // 人体中心线

        float chest = fatigueMap.containsKey("胸部") ? fatigueMap.get("胸部") : 0f;
        float back = fatigueMap.containsKey("背部") ? fatigueMap.get("背部") : 0f;
        float shoulders = fatigueMap.containsKey("肩部") ? fatigueMap.get("肩部") : 0f;
        float core = fatigueMap.containsKey("核心") ? fatigueMap.get("核心") : 0f;
        float arms = fatigueMap.containsKey("手臂") ? fatigueMap.get("手臂") : 0f;
        float legs = fatigueMap.containsKey("腿部") ? fatigueMap.get("腿部") : 0f;
        float glutes = fatigueMap.containsKey("臀部") ? fatigueMap.get("臀部") : 0f;

        // 根据标准人体轮廓比例定位
        // 胸部/背部 (y: 28%)
        drawGlowOval(canvas, cx, top + drawH * 0.28f, drawW * 0.18f, drawH * 0.06f, Math.max(chest, back));

        // 肩部 (y: 25%, 左右两侧)
        drawGlowOval(canvas, cx - drawW * 0.20f, top + drawH * 0.25f, drawW * 0.08f, drawH * 0.05f, shoulders);
        drawGlowOval(canvas, cx + drawW * 0.20f, top + drawH * 0.25f, drawW * 0.08f, drawH * 0.05f, shoulders);

        // 核心 (y: 40%)
        drawGlowOval(canvas, cx, top + drawH * 0.40f, drawW * 0.12f, drawH * 0.08f, core);

        // 臀部 (y: 52%)
        drawGlowOval(canvas, cx, top + drawH * 0.52f, drawW * 0.15f, drawH * 0.06f, glutes);

        // 手臂 (y: 42%, 左右两侧)
        drawGlowOval(canvas, cx - drawW * 0.28f, top + drawH * 0.42f, drawW * 0.06f, drawH * 0.12f, arms);
        drawGlowOval(canvas, cx + drawW * 0.28f, top + drawH * 0.42f, drawW * 0.06f, drawH * 0.12f, arms);

        // 腿部 (y: 72%, 左右两侧)
        drawGlowOval(canvas, cx - drawW * 0.12f, top + drawH * 0.72f, drawW * 0.08f, drawH * 0.18f, legs);
        drawGlowOval(canvas, cx + drawW * 0.12f, top + drawH * 0.72f, drawW * 0.08f, drawH * 0.18f, legs);
    }

    private void drawGlowOval(Canvas canvas, float cx, float cy, float rx, float ry, float fatigue) {
        if (fatigue <= 0) return;

        glowPaint.setColor(getFatigueColor(fatigue));
        RectF rect = new RectF(cx - rx, cy - ry, cx + rx, cy + ry);
        canvas.drawOval(rect, glowPaint);
    }

    private int getFatigueColor(float fatigue) {
        if (fatigue <= 0f) return COLOR_NORMAL;
        if (fatigue > 100f) fatigue = 100f;

        int color;
        if (fatigue <= 50f) {
            float ratio = fatigue / 50f;
            color = ColorUtils.blendARGB(COLOR_MILD, COLOR_MODERATE, ratio);
        } else {
            float ratio = (fatigue - 50f) / 50f;
            color = ColorUtils.blendARGB(COLOR_MODERATE, COLOR_SEVERE, ratio);
        }

        // 加上透明度，使其光晕质感更好 (约 60% 透明度)
        return ColorUtils.setAlphaComponent(color, 150);
    }
}
