package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 3D拟物微拟态 3D 渐变圆环进度条
 * 支持平滑扫描渐变、圆角笔触
 */
public class GradientCircularProgressView extends View {

    private Paint mTrackPaint;
    private Paint mProgressPaint;
    
    private int mTrackColor = 0xFFE5E0D5;
    private int mStartColor = 0xFFD2D7D9; // 渐变起点（较浅）
    private int mEndColor = 0xFF656D70;   // 渐变终点（较深）
    
    private float mStrokeWidth = 14f; // 默认像素值，会在初始化时根据dp缩放
    private int mProgress = 65;
    private int mMax = 100;
    
    private RectF mOvalRect;
    private SweepGradient mSweepGradient;
    private Matrix mGradientMatrix;

    public GradientCircularProgressView(Context context) {
        super(context);
        init(context, null);
    }

    public GradientCircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public GradientCircularProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        float density = context.getResources().getDisplayMetrics().density;
        mStrokeWidth = 7f * density; // 7dp 厚度，与布局相符

        if (attrs != null) {
            // 这里可以解析XML自定义属性，如果没有定义也使用上述默认值
        }

        // 1. 初始化轨道画笔
        mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setColor(mTrackColor);
        mTrackPaint.setStrokeWidth(mStrokeWidth);
        mTrackPaint.setStrokeCap(Paint.Cap.ROUND);

        // 2. 初始化进度条画笔
        mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mProgressPaint.setStyle(Paint.Style.STROKE);
        mProgressPaint.setStrokeWidth(mStrokeWidth);
        mProgressPaint.setStrokeCap(Paint.Cap.ROUND);

        mOvalRect = new RectF();
        mGradientMatrix = new Matrix();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // 留出画笔宽度的一半，防止被边缘切掉
        float padding = mStrokeWidth / 2f;
        mOvalRect.set(padding, padding, w - padding, h - padding);
        
        updateGradientShader();
    }

    private void updateGradientShader() {
        if (mOvalRect.width() <= 0) return;
        
        float centerX = mOvalRect.centerX();
        float centerY = mOvalRect.centerY();
        
        // 动态计算进度比例
        float ratio = (float) mProgress / mMax;
        if (ratio <= 0) ratio = 0.01f; // 避免 0 时节点重复
        
        // 核心优化：将渐变终点锁在当前进度 ratio 位置。
        // 同时把 1.0f 设为 mStartColor，保证起点往逆时针溢出的圆头采样干净，呈现纯净浅色。
        int[] colors = new int[]{mStartColor, mEndColor, mEndColor, mStartColor};
        
        float midPos = Math.min(ratio + 0.01f, 0.99f);
        float[] positions = new float[]{0.0f, ratio, midPos, 1.0f};
        
        mSweepGradient = new SweepGradient(centerX, centerY, colors, positions);
        
        // 逆时针旋转 90 度，对齐 12 点钟方向
        mGradientMatrix.reset();
        mGradientMatrix.postRotate(-90, centerX, centerY);
        mSweepGradient.setLocalMatrix(mGradientMatrix);
        
        mProgressPaint.setShader(mSweepGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. 绘制底色轨道
        canvas.drawArc(mOvalRect, 0, 360, false, mTrackPaint);

        // 2. 绘制进度
        if (mProgress > 0) {
            float sweepAngle = 360f * mProgress / mMax;
            canvas.drawArc(mOvalRect, -90, sweepAngle, false, mProgressPaint);
        }
    }

    public synchronized int getProgress() {
        return mProgress;
    }

    public synchronized void setProgress(int progress) {
        if (progress < 0) progress = 0;
        if (progress > mMax) progress = mMax;
        this.mProgress = progress;
        // 进度改变时同步更新 Shader，保证渐变区间精准
        updateGradientShader();
        invalidate();
    }

    /**
     * 兼容 CircularProgressIndicator 的带动画的 setProgress 接口
     */
    public synchronized void setProgress(int progress, boolean animate) {
        setProgress(progress);
    }

    /**
     * 动态设置渐变色的起止值
     */
    public void setColors(int startColor, int endColor) {
        this.mStartColor = startColor;
        this.mEndColor = endColor;
        updateGradientShader();
        invalidate();
    }

    /**
     * 动态设置底盘轨道色
     */
    public void setTrackColor(int trackColor) {
        this.mTrackColor = trackColor;
        if (mTrackPaint != null) {
            mTrackPaint.setColor(trackColor);
        }
        invalidate();
    }

    /**
     * 动态设置笔触粗细（单位 dp）
     */
    public void setStrokeWidthDp(float dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        this.mStrokeWidth = dp * density;
        if (mTrackPaint != null) {
            mTrackPaint.setStrokeWidth(mStrokeWidth);
        }
        if (mProgressPaint != null) {
            mProgressPaint.setStrokeWidth(mStrokeWidth);
        }
        invalidate();
    }

    public synchronized int getMax() {
        return mMax;
    }

    public synchronized void setMax(int max) {
        if (max > 0) {
            this.mMax = max;
            invalidate();
        }
    }
}
