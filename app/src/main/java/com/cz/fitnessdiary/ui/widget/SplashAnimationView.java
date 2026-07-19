package com.cz.fitnessdiary.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cz.fitnessdiary.R;

public class SplashAnimationView extends View {

    private Paint ringPaint;
    private Paint dotPaint;
    private Paint pathPaint;

    private float ringRadius = 0f;
    private float maxRingRadius;
    private float dotRadius = 0f;
    private float maxDotRadius;
    private float ringAlpha = 255f;

    private ValueAnimator breathingAnimator;
    private ValueAnimator introAnimator;

    private float centerX;
    private float centerY;

    // Heartbeat path animation
    private Path heartPath;
    private float pathProgress = 0f;

    public SplashAnimationView(Context context) {
        super(context);
        init();
    }

    public SplashAnimationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SplashAnimationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int primaryColor = ContextCompat.getColor(getContext(), R.color.primary);
        int secondaryColor = ContextCompat.getColor(getContext(), R.color.secondary);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(12f);
        ringPaint.setColor(primaryColor);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(secondaryColor);

        pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeWidth(16f);
        pathPaint.setColor(primaryColor);
        pathPaint.setStrokeCap(Paint.Cap.ROUND);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);

        heartPath = new Path();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        maxRingRadius = Math.min(w, h) * 0.25f;
        maxDotRadius = maxRingRadius * 0.15f;

        setupHeartPath();
    }

    private void setupHeartPath() {
        heartPath.reset();
        float width = maxRingRadius * 1.8f;
        float startX = centerX - width / 2f;

        heartPath.moveTo(startX, centerY);
        heartPath.lineTo(startX + width * 0.2f, centerY);
        heartPath.lineTo(startX + width * 0.35f, centerY - maxRingRadius * 0.7f); // Up
        heartPath.lineTo(startX + width * 0.65f, centerY + maxRingRadius * 0.9f); // Down
        heartPath.lineTo(startX + width * 0.8f, centerY); // Up to center
        heartPath.lineTo(startX + width, centerY);
    }

    public void startAnimation() {
        introAnimator = ValueAnimator.ofFloat(0f, 1f);
        introAnimator.setDuration(1500);
        introAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        introAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();

            // Intro dot expanding
            if (progress < 0.2f) {
                dotRadius = maxDotRadius * (progress / 0.2f);
            } else {
                dotRadius = maxDotRadius;
            }

            // Ring expanding
            if (progress > 0.1f && progress <= 0.5f) {
                float ringProg = (progress - 0.1f) / 0.4f;
                ringRadius = maxRingRadius * ringProg;
                ringPaint.setAlpha((int) (255 * (1f - ringProg)));
            }

            // Path drawing
            if (progress > 0.3f) {
                pathProgress = (progress - 0.3f) / 0.7f;
            }

            invalidate();
        });

        introAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startBreathing();
            }
        });

        introAnimator.start();
    }

    private void startBreathing() {
        breathingAnimator = ValueAnimator.ofFloat(0f, 1f, 0f);
        breathingAnimator.setDuration(2500);
        breathingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathingAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        breathingAnimator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            // Pulse the heart line slightly
            pathPaint.setStrokeWidth(16f + (4f * val));
            invalidate();
        });
        breathingAnimator.start();
    }

    public void stopAnimation() {
        if (introAnimator != null) introAnimator.cancel();
        if (breathingAnimator != null) breathingAnimator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw expanding ring
        if (ringRadius > 0) {
            canvas.drawCircle(centerX, centerY, ringRadius, ringPaint);
        }

        // Draw heartbeat path if intro is sufficiently far along
        if (pathProgress > 0) {
            Path measurePath = new Path();
            android.graphics.PathMeasure measure = new android.graphics.PathMeasure(heartPath, false);
            measure.getSegment(0, measure.getLength() * pathProgress, measurePath, true);
            canvas.drawPath(measurePath, pathPaint);
        } else {
            // Draw initial dot
            if (dotRadius > 0) {
                canvas.drawCircle(centerX, centerY, dotRadius, dotPaint);
            }
        }
    }
}
