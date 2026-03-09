package com.cz.fitnessdiary.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.LottieAnimationView;
import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.model.FoodScanFlowState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class FoodScanFlowView extends FrameLayout {

    public interface ActionListener {
        void onRetry();
        void onCancel();
    }

    private View contentRoot;
    private ImageView ivPreview;
    private LottieAnimationView lottieScan;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private LinearProgressIndicator progress;
    private MaterialButton btnRetry;
    private MaterialButton btnCancel;
    private ActionListener listener;

    public FoodScanFlowView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public FoodScanFlowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_food_scan_flow, this, true);
        contentRoot = getChildAt(0);
        ivPreview = findViewById(R.id.iv_scan_preview);
        lottieScan = findViewById(R.id.lottie_scan);
        tvTitle = findViewById(R.id.tv_scan_title);
        tvSubtitle = findViewById(R.id.tv_scan_subtitle);
        progress = findViewById(R.id.progress_scan);
        btnRetry = findViewById(R.id.btn_scan_retry);
        btnCancel = findViewById(R.id.btn_scan_cancel);

        setVisibility(GONE);
        btnRetry.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRetry();
            }
        });
        btnCancel.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCancel();
            }
        });
        lottieScan.setFailureListener(result -> {
            lottieScan.cancelAnimation();
            lottieScan.setImageResource(android.R.drawable.ic_popup_sync);
        });
    }

    public void setActionListener(ActionListener listener) {
        this.listener = listener;
    }

    public void setPreviewBitmap(@Nullable Bitmap bitmap) {
        if (ivPreview == null) {
            return;
        }
        if (bitmap == null) {
            ivPreview.setImageResource(R.drawable.ic_hero_diet);
            ivPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            return;
        }
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivPreview.setImageBitmap(bitmap);
    }

    public void show() {
        if (contentRoot != null) {
            contentRoot.setVisibility(VISIBLE);
        }
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(220).start();
    }

    public void hide() {
        animate().alpha(0f).setDuration(180).withEndAction(() -> {
            setVisibility(GONE);
            if (contentRoot != null) {
                contentRoot.setVisibility(GONE);
            }
        }).start();
    }

    public void render(FoodScanFlowState state) {
        if (state == null) {
            return;
        }
        tvTitle.setText(state.getTitle());
        tvSubtitle.setText(state.getSubtitle());

        boolean indeterminate = state.getStage() == FoodScanFlowState.Stage.RECOGNIZE;
        progress.setIndeterminate(indeterminate);
        if (!indeterminate) {
            progress.setProgressCompat(Math.max(0, Math.min(100, state.getProgress())), true);
        }

        lottieScan.setAnimation(mapAnimation(state.getStage()));
        lottieScan.playAnimation();

        boolean error = state.getStage() == FoodScanFlowState.Stage.ERROR;
        btnRetry.setVisibility(error && state.isRetryable() ? VISIBLE : GONE);
        btnCancel.setText(error ? "关闭" : "取消");
    }

    private int mapAnimation(FoodScanFlowState.Stage stage) {
        if (stage == null) {
            return R.raw.food_scan_upload;
        }
        switch (stage) {
            case RECOGNIZE:
                return R.raw.food_scan_recognize;
            case NUTRITION:
                return R.raw.food_scan_nutrition;
            case SUGGESTION:
            case SUCCESS:
                return R.raw.food_scan_done;
            case ERROR:
            case UPLOAD:
            case IDLE:
            default:
                return R.raw.food_scan_upload;
        }
    }
}