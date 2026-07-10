package com.cz.fitnessdiary.ui.fragment;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.cz.fitnessdiary.R;

public class AchievementUnlockDialogFragment extends DialogFragment {

    private String emoji;
    private String title;
    private String desc;
    private Runnable onDismissCallback;

    public static AchievementUnlockDialogFragment newInstance(String emoji, String title, String desc, Runnable onDismissCallback) {
        AchievementUnlockDialogFragment fragment = new AchievementUnlockDialogFragment();
        fragment.emoji = emoji;
        fragment.title = title;
        fragment.desc = desc;
        fragment.onDismissCallback = onDismissCallback;
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_achievement_unlock, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        TextView tvEmoji = view.findViewById(R.id.tv_emoji);
        TextView tvTitle = view.findViewById(R.id.tv_title);
        TextView tvDesc = view.findViewById(R.id.tv_desc);
        View rootCard = view.findViewById(R.id.card_achievement_container);
        View rootLayout = view.findViewById(R.id.layout_root);
        View emojiCircle = view.findViewById(R.id.card_emoji_circle);
        View btnConfirm = view.findViewById(R.id.btn_confirm);

        tvEmoji.setText(emoji != null ? emoji : "🏆");
        tvTitle.setText(title != null ? title : "成就达成");
        tvDesc.setText(desc != null ? desc : "");

        rootLayout.setAlpha(0f);
        rootCard.setAlpha(0f);
        rootCard.setScaleX(0.7f);
        rootCard.setScaleY(0.7f);
        emojiCircle.setScaleX(0.3f);
        emojiCircle.setScaleY(0.3f);

        rootLayout.animate().alpha(1f).setDuration(300).start();
        rootCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .withEndAction(() -> {
                    emojiCircle.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(350)
                            .setInterpolator(new OvershootInterpolator(1.6f))
                            .start();
                })
                .start();

        View.OnClickListener closeListener = v -> {
            rootLayout.animate().alpha(0f).setDuration(250).start();
            rootCard.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(250)
                    .withEndAction(() -> {
                        dismissAllowingStateLoss();
                        if (onDismissCallback != null) {
                            onDismissCallback.run();
                        }
                    })
                    .start();
        };

        btnConfirm.setOnClickListener(closeListener);
        rootLayout.setOnClickListener(closeListener);
    }
}
