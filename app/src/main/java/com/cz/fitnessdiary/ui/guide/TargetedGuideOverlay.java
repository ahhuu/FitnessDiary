package com.cz.fitnessdiary.ui.guide;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.cz.fitnessdiary.R;

/**
 * 页面引导遮罩层，在 Activity 的 DecorView 上叠加半透明遮罩，
 * 对目标 View 进行"挖洞"高亮，并显示 Tooltip 引导气泡。
 */
public class TargetedGuideOverlay {

    private final ViewGroup rootView;
    private final PageGuide guide;
    private int currentStep = 0;
    private View overlayView;
    private View tooltipView;
    private final GuideStateManager stateManager;
    private final Runnable onComplete;
    private static final int TOOLTIP_MARGIN = 16;

    public TargetedGuideOverlay(Activity activity, PageGuide guide,
                                GuideStateManager stateManager, Runnable onComplete) {
        this.rootView = (ViewGroup) activity.getWindow().getDecorView();
        this.guide = guide;
        this.stateManager = stateManager;
        this.onComplete = onComplete;
    }

    /**
     * 开始引导。如果该页面引导已完成则直接回调 onComplete。
     */
    public void start() {
        if (stateManager.isPageGuideDone(guide.pageKey)) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        showStep(0);
    }

    private void showStep(int index) {
        // 清除之前的遮罩
        removeOverlay();

        if (index < 0 || index >= guide.steps.size()) {
            complete();
            return;
        }

        GuideStep step = guide.steps.get(index);
        currentStep = index;

        // 查找目标 View
        final View targetView = rootView.findViewById(step.targetViewId);
        if (targetView == null) {
            // 目标 View 不存在，跳过该步骤
            advance(index);
            return;
        }

        // 等目标 View 布局完成后再获取坐标
        targetView.post(() -> {
            if (!targetView.isAttachedToWindow()) {
                advance(currentStep);
                return;
            }

            // 获取目标 View 在屏幕上的坐标
            int[] location = new int[2];
            targetView.getLocationOnScreen(location);
            int targetLeft = location[0];
            int targetTop = location[1];
            int targetWidth = targetView.getWidth();
            int targetHeight = targetView.getHeight();

            // 创建全屏半透明遮罩（带挖洞效果）
            overlayView = new HolePunchOverlayView(rootView.getContext(),
                    targetLeft, targetTop, targetWidth, targetHeight);
            overlayView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // 创建 Tooltip
            tooltipView = LayoutInflater.from(rootView.getContext())
                    .inflate(R.layout.view_guide_tooltip, rootView, false);

            // 设置标题和描述
            TextView titleView = tooltipView.findViewById(R.id.tv_guide_title);
            TextView descView = tooltipView.findViewById(R.id.tv_guide_description);
            titleView.setText(step.title);
            descView.setText(step.description);

            // 设置按钮
            View btnSkip = tooltipView.findViewById(R.id.btn_guide_skip);
            View btnNext = tooltipView.findViewById(R.id.btn_guide_next);

            // 更新"下一步"按钮文本（最后一步显示"完成"）
            boolean isLastStep = (index == guide.steps.size() - 1);
            if (isLastStep) {
                ((com.google.android.material.button.MaterialButton) btnNext).setText("完成");
            }

            btnSkip.setOnClickListener(v -> complete());
            btnNext.setOnClickListener(v -> advance(index));

            // 遮罩点击进入下一步
            overlayView.setOnClickListener(v -> advance(index));

            // 计算 Tooltip 位置
            FrameLayout.LayoutParams tooltipParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            positionTooltip(tooltipParams, step.anchor, targetLeft, targetTop,
                    targetWidth, targetHeight, tooltipView);

            // 添加到 DecorView
            rootView.addView(overlayView);
            rootView.addView(tooltipView, tooltipParams);
        });
    }

    /**
     * 根据锚点方向计算 Tooltip 在屏幕中的位置。
     */
    private void positionTooltip(FrameLayout.LayoutParams params,
                                  GuideStep.Anchor anchor,
                                  int targetLeft, int targetTop,
                                  int targetWidth, int targetHeight,
                                  View tooltipView) {
        // 获取屏幕尺寸
        WindowManager wm = (WindowManager) rootView.getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point screenSize = new Point();
        display.getSize(screenSize);

        // 先测量 tooltip 获取宽高
        tooltipView.measure(
                View.MeasureSpec.makeMeasureSpec(screenSize.x - TOOLTIP_MARGIN * 2,
                        View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int tooltipWidth = tooltipView.getMeasuredWidth();
        int tooltipHeight = tooltipView.getMeasuredHeight();

        // 箭头控制
        ImageView arrowTop = tooltipView.findViewById(R.id.iv_arrow_top);
        ImageView arrowBottom = tooltipView.findViewById(R.id.iv_arrow_bottom);

        switch (anchor) {
            case BOTTOM:
                // Tooltip 在目标上方，箭头朝下（在底部）
                params.leftMargin = Math.max(TOOLTIP_MARGIN,
                        targetLeft + targetWidth / 2 - tooltipWidth / 2);
                params.leftMargin = Math.min(params.leftMargin,
                        screenSize.x - tooltipWidth - TOOLTIP_MARGIN);
                params.topMargin = targetTop - tooltipHeight - TOOLTIP_MARGIN;
                if (params.topMargin < 0) {
                    // 空间不足，放在目标下方
                    params.topMargin = targetTop + targetHeight + TOOLTIP_MARGIN;
                    arrowTop.setVisibility(View.VISIBLE);
                    arrowBottom.setVisibility(View.GONE);
                } else {
                    arrowTop.setVisibility(View.GONE);
                    arrowBottom.setVisibility(View.VISIBLE);
                }
                break;

            case TOP:
                // Tooltip 在目标下方，箭头朝上（在顶部）
                params.leftMargin = Math.max(TOOLTIP_MARGIN,
                        targetLeft + targetWidth / 2 - tooltipWidth / 2);
                params.leftMargin = Math.min(params.leftMargin,
                        screenSize.x - tooltipWidth - TOOLTIP_MARGIN);
                params.topMargin = targetTop + targetHeight + TOOLTIP_MARGIN;
                if (params.topMargin + tooltipHeight > screenSize.y) {
                    // 空间不足，放在目标上方
                    params.topMargin = targetTop - tooltipHeight - TOOLTIP_MARGIN;
                    arrowTop.setVisibility(View.GONE);
                    arrowBottom.setVisibility(View.VISIBLE);
                } else {
                    arrowTop.setVisibility(View.VISIBLE);
                    arrowBottom.setVisibility(View.GONE);
                }
                break;

            case LEFT:
                // Tooltip 在目标右侧
                params.leftMargin = targetLeft + targetWidth + TOOLTIP_MARGIN;
                params.topMargin = Math.max(TOOLTIP_MARGIN,
                        targetTop + targetHeight / 2 - tooltipHeight / 2);
                params.topMargin = Math.min(params.topMargin,
                        screenSize.y - tooltipHeight - TOOLTIP_MARGIN);
                if (params.leftMargin + tooltipWidth > screenSize.x) {
                    // 空间不足，放在左侧
                    params.leftMargin = targetLeft - tooltipWidth - TOOLTIP_MARGIN;
                }
                arrowTop.setVisibility(View.GONE);
                arrowBottom.setVisibility(View.GONE);
                break;

            case RIGHT:
                // Tooltip 在目标左侧
                params.leftMargin = targetLeft - tooltipWidth - TOOLTIP_MARGIN;
                params.topMargin = Math.max(TOOLTIP_MARGIN,
                        targetTop + targetHeight / 2 - tooltipHeight / 2);
                params.topMargin = Math.min(params.topMargin,
                        screenSize.y - tooltipHeight - TOOLTIP_MARGIN);
                if (params.leftMargin < 0) {
                    // 空间不足，放在右侧
                    params.leftMargin = targetLeft + targetWidth + TOOLTIP_MARGIN;
                }
                arrowTop.setVisibility(View.GONE);
                arrowBottom.setVisibility(View.GONE);
                break;
        }
    }

    private void advance(int currentIndex) {
        if (currentIndex + 1 < guide.steps.size()) {
            showStep(currentIndex + 1);
        } else {
            complete();
        }
    }

    private void complete() {
        removeOverlay();
        stateManager.markPageGuideDone(guide.pageKey);
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void removeOverlay() {
        if (overlayView != null && overlayView.getParent() != null) {
            rootView.removeView(overlayView);
        }
        if (tooltipView != null && tooltipView.getParent() != null) {
            rootView.removeView(tooltipView);
        }
    }

    /**
     * 自定义 View，在 Canvas 上绘制半透明遮盖并在目标区域"挖洞"。
     */
    private static class HolePunchOverlayView extends View {

        private final int highlightLeft;
        private final int highlightTop;
        private final int highlightWidth;
        private final int highlightHeight;
        private final Paint holePaint;
        private final Paint borderPaint;
        private static final float CORNER_RADIUS = 12f;

        HolePunchOverlayView(Context context,
                             int left, int top, int width, int height) {
            super(context);
            this.highlightLeft = left;
            this.highlightTop = top;
            this.highlightWidth = width;
            this.highlightHeight = height;

            // 遮罩用 Hardware Layer 以支持 PorterDuff 透明混合
            this.setLayerType(LAYER_TYPE_HARDWARE, null);

            this.holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            holePaint.setColor(Color.TRANSPARENT);
            holePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            this.borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(Color.WHITE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            // 绘制半透明遮罩底色
            canvas.drawColor(0x99000000);

            // 使用 PorterDuff.CLEAR 在目标区域"挖洞"
            // 增加 8dp padding 让高亮区域比目标 View 略大
            float padding = 8f * getResources().getDisplayMetrics().density;
            RectF holeRect = new RectF(
                    highlightLeft - padding,
                    highlightTop - padding,
                    highlightLeft + highlightWidth + padding,
                    highlightTop + highlightHeight + padding);

            canvas.drawRoundRect(holeRect, CORNER_RADIUS, CORNER_RADIUS, holePaint);

            // 绘制白色描边边框，强化高亮效果
            canvas.drawRoundRect(holeRect, CORNER_RADIUS, CORNER_RADIUS, borderPaint);
        }
    }
}
