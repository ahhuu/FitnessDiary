package com.cz.fitnessdiary.ui.fragment;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
 * v3.1: 全屏全局引导弹窗 — 4页对应四大功能Tab + FAB快捷入口
 */
public class OnboardingOverlayFragment extends DialogFragment {

    private FragmentOnboardingOverlayBinding binding;
    private int currentPage = 0;
    private final List<ImageView> indicators = new ArrayList<>();

    private static class PageData {
        final String emoji;
        final String title;
        final String subtitle;
        final String description;

        PageData(String emoji, String title, String subtitle, String description) {
            this.emoji = emoji;
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
        }
    }

    private static final int[] PAGE_COLORS = {
            0xFF4CAF50, // 首页 - 绿色
            0xFF2196F3, // 日历 - 蓝色
            0xFF9C27B0, // AI - 紫色
            0xFFFF9800, // 我的 - 橙色
    };

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
                params.dimAmount = 0.7f;
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
        pages.add(new PageData("🏠", "首页记录",
                "健康仪表盘 · 每日打卡",
                "顶部健康评分环一目了然，运动+饮食两大核心卡片，\n下方10个小卡片覆盖饮水/睡眠/步数/心情等全维度。"));
        pages.add(new PageData("📅", "日历历史",
                "训练日志 · 数据回顾",
                "月视图日历呈现每日训练标记，点击任意日期\n查看当天全维度摘要与训练详情。"));
        pages.add(new PageData("🤖", "AI 私教",
                "智能问答 · 个性化建议",
                "随时咨询训练动作、饮食搭配、健身知识，\n获取基于你个人数据的定制化指导。"));
        pages.add(new PageData("👤", "个人中心",
                "目标设置 · 成就回顾",
                "管理身体数据、健身目标、通知提醒，\n查看成就勋章、数据周报与健康日报回顾。\n\n💡 点击右下角 + 按钮，一键进入\n饮食记录、训练打卡、身体指标等快捷入口。"));

        setupViewPager(pages);
        setupIndicators(pages.size());
        updateIndicators(0);
        updateBackground(0);

        binding.btnSkip.setOnClickListener(v -> finishOnboarding());
        binding.btnGetStarted.setOnClickListener(v -> finishOnboarding());
    }

    private void setupViewPager(List<PageData> pages) {
        binding.onboardingPager.setAdapter(new RecyclerView.Adapter<PageHolder>() {
            @NonNull
            @Override
            public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout root = new LinearLayout(requireContext());
                root.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER);
                root.setPadding(dp(32), dp(0), dp(32), dp(0));

                // 大图标区：圆形彩色背景 + emoji
                FrameLayout iconFrame = new FrameLayout(requireContext());
                iconFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(100), dp(100)));
                LinearLayout.LayoutParams frameLp = (LinearLayout.LayoutParams) iconFrame.getLayoutParams();
                frameLp.bottomMargin = dp(28);
                frameLp.gravity = Gravity.CENTER;

                // 圆形背景
                View circle = new View(requireContext());
                circle.setId(View.generateViewId());
                FrameLayout.LayoutParams circleLp = new FrameLayout.LayoutParams(dp(100), dp(100));
                circleLp.gravity = Gravity.CENTER;
                iconFrame.addView(circle, circleLp);

                // Emoji 文字
                TextView emojiView = new TextView(requireContext());
                emojiView.setTextSize(44);
                emojiView.setGravity(Gravity.CENTER);
                emojiView.setId(View.generateViewId());
                FrameLayout.LayoutParams emojiLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                emojiLp.gravity = Gravity.CENTER;
                iconFrame.addView(emojiView, emojiLp);

                root.addView(iconFrame);

                // 标题
                TextView titleView = new TextView(requireContext());
                titleView.setTextSize(24);
                titleView.setTextColor(Color.WHITE);
                titleView.setGravity(Gravity.CENTER);
                titleView.setTypeface(null, android.graphics.Typeface.BOLD);
                titleView.setId(View.generateViewId());
                LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                titleLp.bottomMargin = dp(8);
                root.addView(titleView, titleLp);

                // 副标题
                TextView subtitleView = new TextView(requireContext());
                subtitleView.setTextSize(13);
                subtitleView.setTextColor(0xCCFFFFFF);
                subtitleView.setGravity(Gravity.CENTER);
                subtitleView.setId(View.generateViewId());
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                subLp.bottomMargin = dp(20);
                root.addView(subtitleView, subLp);

                // 描述
                TextView descView = new TextView(requireContext());
                descView.setTextSize(14);
                descView.setTextColor(0x99FFFFFF);
                descView.setGravity(Gravity.CENTER);
                descView.setLineSpacing(dp(6), 1.0f);
                descView.setId(View.generateViewId());
                root.addView(descView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                return new PageHolder(root, circle, emojiView, titleView, subtitleView, descView);
            }

            @Override
            public void onBindViewHolder(@NonNull PageHolder holder, int position) {
                PageData data = pages.get(position);
                holder.emojiView.setText(data.emoji);
                holder.titleView.setText(data.title);
                holder.subtitleView.setText(data.subtitle);
                holder.descView.setText(data.description);

                // 设置圆形背景颜色
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(PAGE_COLORS[position]);
                bg.setAlpha(50);
                holder.circleView.setBackground(bg);
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
                updateBackground(position);
                if (position == pages.size() - 1) {
                    binding.btnGetStarted.setVisibility(View.VISIBLE);
                    binding.btnSkip.setVisibility(View.GONE);
                } else {
                    binding.btnGetStarted.setVisibility(View.GONE);
                    binding.btnSkip.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void updateBackground(int position) {
        if (binding == null) return;
        int color = PAGE_COLORS[position];
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        blendColor(color, 0xFF1A1A2E, 0.7f),
                        0xFF0D0D1A
                });
        binding.getRoot().setBackground(bg);
        binding.btnGetStarted.setBackgroundColor(color);
    }

    /** 混合两个颜色，ratio 越大越接近 c1 */
    private int blendColor(int c1, int c2, float ratio) {
        int r = (int) (Color.red(c1) * ratio + Color.red(c2) * (1 - ratio));
        int g = (int) (Color.green(c1) * ratio + Color.green(c2) * (1 - ratio));
        int b = (int) (Color.blue(c1) * ratio + Color.blue(c2) * (1 - ratio));
        return Color.rgb(r, g, b);
    }

    private void setupIndicators(int count) {
        indicators.clear();
        binding.layoutIndicator.removeAllViews();
        int size = dp(8);
        int margin = dp(5);
        int activeSize = dp(20);

        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            indicators.add(dot);
            binding.layoutIndicator.addView(dot);
        }
    }

    private void updateIndicators(int position) {
        int size = dp(8);
        int activeSize = dp(20);
        for (int i = 0; i < indicators.size(); i++) {
            ImageView dot = indicators.get(i);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            if (i == position) {
                shape.setColor(PAGE_COLORS[i]);
                dot.getLayoutParams().width = activeSize;
                dot.getLayoutParams().height = size;
            } else {
                shape.setColor(0x33FFFFFF);
                dot.getLayoutParams().width = size;
                dot.getLayoutParams().height = size;
            }
            dot.setImageDrawable(shape);
            dot.requestLayout();
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
        final View circleView;
        final TextView emojiView;
        final TextView titleView;
        final TextView subtitleView;
        final TextView descView;

        PageHolder(View itemView, View circleView, TextView emojiView,
                TextView titleView, TextView subtitleView, TextView descView) {
            super(itemView);
            this.circleView = circleView;
            this.emojiView = emojiView;
            this.titleView = titleView;
            this.subtitleView = subtitleView;
            this.descView = descView;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
