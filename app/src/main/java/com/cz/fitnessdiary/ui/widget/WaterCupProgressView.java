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

        float density = getResources().getDisplayMetrics().density;
        
        // 核心布局参数
        float padX = dp(36); // 左右缩进，使杯子高耸有型
        float padY = dp(24); // 上下缩进
        
        float top = padY;
        float bottom = h - padY;
        float left = padX;
        float right = w - padX;
        
        float rimHeight = dp(10); // 杯口椭圆的半高度
        float bottomCurvature = dp(8); // 杯底弯曲度
        float glassBottomThickness = dp(12); // 玻璃底厚度

        // 1. 杯口椭圆的包裹矩形
        RectF topRimRect = new RectF(left, top, right, top + rimHeight * 2);
        
        // 2. 玻璃杯体外壁轮廓（用于杯身轮廓线，左右带有顺滑的收紧微弧）
        Path glassOutline = new Path();
        glassOutline.moveTo(left, top + rimHeight);
        // 左杯壁：使用贝塞尔曲线，由上往下微微向内收缩
        glassOutline.cubicTo(left + dp(2), top + (bottom - top) * 0.4f,
                             left + dp(8), bottom - bottomCurvature,
                             left + dp(12), bottom - bottomCurvature);
        // 杯底外弧线，向右划到右下转折点
        glassOutline.lineTo(right - dp(12), bottom - bottomCurvature);
        // 右杯壁：同理向上滑回杯口右端
        glassOutline.cubicTo(right - dp(8), bottom - bottomCurvature,
                             right - dp(2), top + (bottom - top) * 0.4f,
                             right, top + rimHeight);

        // 3. 玻璃杯内腔轮廓 Path (内部水只在这个区域内填充)
        Path cupInnerPath = new Path();
        float innerLeft = left + dp(5);
        float innerRight = right - dp(5);
        float innerTop = top + rimHeight * 1.5f;
        float innerBottom = bottom - bottomCurvature - glassBottomThickness;
        
        cupInnerPath.moveTo(innerLeft, innerTop);
        cupInnerPath.cubicTo(innerLeft + dp(2), innerTop + (innerBottom - innerTop) * 0.4f,
                             innerLeft + dp(6), innerBottom,
                             innerLeft + dp(10), innerBottom);
        cupInnerPath.lineTo(innerRight - dp(10), innerBottom);
        cupInnerPath.cubicTo(innerRight - dp(6), innerBottom,
                             innerRight - dp(2), innerTop + (innerBottom - innerTop) * 0.4f,
                             innerRight, innerTop);
        cupInnerPath.close();

        // 4. 绘制填充水 (带正弦微波浪起伏)
        float ratio = Math.min(1f, current * 1f / target);
        if (ratio > 0) {
            canvas.save();
            // 只在杯内空腔区域内填充
            canvas.clipPath(cupInnerPath);
            
            float waterTopY = innerBottom - ratio * (innerBottom - innerTop);
            
            Path waterPath = new Path();
            waterPath.moveTo(innerLeft - dp(10), waterTopY);
            
            // 用二次贝塞尔曲线画一条起伏的正弦波浪水面
            float waveHeight = dp(3.5f);
            float waveWidth = (innerRight - innerLeft) / 2f;
            waterPath.quadTo(innerLeft + waveWidth / 2f, waterTopY - waveHeight, innerLeft + waveWidth, waterTopY);
            waterPath.quadTo(innerLeft + waveWidth * 1.5f, waterTopY + waveHeight, innerRight + dp(10), waterTopY);
            
            // 围合出水面底部的填充区
            waterPath.lineTo(innerRight + dp(10), innerBottom + dp(20));
            waterPath.lineTo(innerLeft - dp(10), innerBottom + dp(20));
            waterPath.close();
            
            canvas.drawPath(waterPath, fillPaint);
            canvas.restore();
        }

        // 5. 绘制杯身杯壁的晶莹线条
        canvas.drawPath(glassOutline, strokePaint);
        
        // 6. 绘制杯口的椭圆沿口 (增加 3D 纵深感)
        canvas.drawOval(topRimRect, strokePaint);

        // 7. 绘制杯底的厚度分界内弧 (突出 3D 厚玻璃杯底座质感)
        Path bottomInnerLine = new Path();
        bottomInnerLine.moveTo(left + dp(13), innerBottom + dp(1));
        bottomInnerLine.lineTo(right - dp(13), innerBottom + dp(1));
        
        strokePaint.setStrokeWidth(dp(3)); // 厚底线稍微细柔些
        canvas.drawPath(bottomInnerLine, strokePaint);
        
        // 还原杯身正常粗细
        strokePaint.setStrokeWidth(dp(5));

        // 8. 绘制中心水量数字 (文字增加阴影以增强玻璃光折射感)
        textPaint.setShadowLayer(4f, 0f, 2f, 0x40FFFFFF);
        canvas.drawText(current + " ml", w / 2f, h / 2f + dp(6), textPaint);
        textPaint.clearShadowLayer();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
