package com.cz.fitnessdiary.ui.fragment;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentOnboardingOverlayBinding;
import com.cz.fitnessdiary.ui.guide.GuideStateManager;

import java.util.ArrayList;
import java.util.List;

/**
 * v3.0: 全屏全局引导弹窗 — 首次启动时展示4页新功能介绍
 */
public class OnboardingOverlayFragment extends DialogFragment {

    private FragmentOnboardingOverlayBinding binding;
    private int currentPage = 0;
    private List<ImageView> indicators = new ArrayList<>();

    private static class PageData {
        final String emoji;
        final String title;
        final String description;

        PageData(String emoji, String title, String description) {
            this.emoji = emoji;
            this.title = title;
            this.description = description;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, android.R.style.Theme_Translucent_NoTitleBar);
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            android.app.Dialog dialog = getDialog();
            if (dialog == null) return;
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams params = window.getAttributes();
                params.dimAmount = 0.6f;
                window.setAttributes(params);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingOverlayBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        List<PageData> pages = new ArrayList<>();
        pages.add(new PageData("🏠", "健康仪表盘",
                "查看每日健康评分、能量天平和AI智能建议"));
        pages.add(new PageData("➕", "快捷记录",
                "点击+按钮，一键记录训练、饮食和习惯"));
        pages.add(new PageData("📊", "数据看板",
                "在历史页面查看所有健康数据的趋势和交叉分析"));
        pages.add(new PageData("🤖", "AI 私教",
                "随时咨询训练和饮食问题，获取个性化建议"));

        setupViewPager(pages);
        setupIndicators(pages.size());
        updateIndicators(0);

        binding.btnSkip.setOnClickListener(v -> finishOnboarding());

        binding.btnGetStarted.setOnClickListener(v -> finishOnboarding());
    }

    private void setupViewPager(List<PageData> pages) {
        binding.onboardingPager.setAdapter(new RecyclerView.Adapter<PageHolder>() {
            @NonNull
            @Override
            public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                // ViewPager2 requires children to use MATCH_PARENT
                LinearLayout root = new LinearLayout(requireContext());
                root.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(android.view.Gravity.CENTER);
                root.setPadding(
                        dp(40), dp(0), dp(40), dp(0)
                );

                TextView emojiView = new TextView(requireContext());
                emojiView.setTextSize(64);
                emojiView.setGravity(android.view.Gravity.CENTER);
                emojiView.setId(View.generateViewId());
                LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                emojiLp.bottomMargin = dp(24);
                root.addView(emojiView, emojiLp);

                TextView titleView = new TextView(requireContext());
                titleView.setTextSize(22);
                titleView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
                titleView.setGravity(android.view.Gravity.CENTER);
                titleView.setId(View.generateViewId());
                LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                titleLp.bottomMargin = dp(12);
                root.addView(titleView, titleLp);

                TextView descView = new TextView(requireContext());
                descView.setTextSize(15);
                descView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
                descView.setGravity(android.view.Gravity.CENTER);
                descView.setLineSpacing(dp(4), 1.0f);
                descView.setId(View.generateViewId());
                root.addView(descView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                return new PageHolder(root, emojiView, titleView, descView);
            }

            @Override
            public void onBindViewHolder(@NonNull PageHolder holder, int position) {
                PageData data = pages.get(position);
                holder.emojiView.setText(data.emoji);
                holder.titleView.setText(data.title);
                holder.descView.setText(data.description);
            }

            @Override
            public int getItemCount() {
                return pages.size();
            }
        });

        binding.onboardingPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateIndicators(position);
                if (position == pages.size() - 1) {
                    binding.btnGetStarted.setVisibility(View.VISIBLE);
                } else {
                    binding.btnGetStarted.setVisibility(View.GONE);
                }
            }
        });
    }

    private void setupIndicators(int count) {
        indicators.clear();
        binding.layoutIndicator.removeAllViews();
        int size = dp(10);
        int margin = dp(6);

        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(0x66FFFFFF);
            dot.setImageDrawable(shape);

            indicators.add(dot);
            binding.layoutIndicator.addView(dot);
        }
    }

    private void updateIndicators(int position) {
        for (int i = 0; i < indicators.size(); i++) {
            GradientDrawable shape = (GradientDrawable) indicators.get(i).getDrawable();
            if (i == position) {
                shape.setColor(0xFFFFFFFF);
                indicators.get(i).getLayoutParams().width = dp(24);
                indicators.get(i).getLayoutParams().height = dp(10);
                GradientDrawable active = new GradientDrawable();
                active.setShape(GradientDrawable.RECTANGLE);
                active.setCornerRadius(dp(5));
                active.setColor(0xFFFFFFFF);
                indicators.get(i).setImageDrawable(active);
            } else {
                shape.setColor(0x66FFFFFF);
                indicators.get(i).getLayoutParams().width = dp(10);
                indicators.get(i).getLayoutParams().height = dp(10);
                indicators.get(i).setImageDrawable(shape);
            }
            indicators.get(i).requestLayout();
        }
    }

    private void finishOnboarding() {
        GuideStateManager guideManager = new GuideStateManager(requireContext());
        guideManager.markGlobalOnboardingDone();
        dismiss();
    }

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    private static class PageHolder extends RecyclerView.ViewHolder {
        final TextView emojiView;
        final TextView titleView;
        final TextView descView;

        PageHolder(View itemView, TextView emojiView, TextView titleView, TextView descView) {
            super(itemView);
            this.emojiView = emojiView;
            this.titleView = titleView;
            this.descView = descView;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
