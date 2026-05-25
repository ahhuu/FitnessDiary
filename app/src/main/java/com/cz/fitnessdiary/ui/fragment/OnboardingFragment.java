package com.cz.fitnessdiary.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentOnboardingBinding;
import com.cz.fitnessdiary.databinding.ItemOnboardingPageBinding;
import com.cz.fitnessdiary.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class OnboardingFragment extends Fragment {

    private static final String PREF_ONBOARDING = "onboarding_prefs";
    private static final String KEY_DONE = "onboarding_done";

    private FragmentOnboardingBinding binding;
    private int currentPage = 0;
    private List<ImageView> indicators = new ArrayList<>();

    public static boolean isDone(Context ctx) {
        return ctx.getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false);
    }

    private static void markDone(Context ctx) {
        ctx.getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        // Block back during onboarding
                    }
                });

        setupViewPager();
        setupIndicators(3);
        updateIndicators(0);

        binding.btnPrev.setOnClickListener(v -> {
            if (currentPage > 0) {
                binding.vpOnboarding.setCurrentItem(currentPage - 1, true);
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            if (currentPage < 2) {
                binding.vpOnboarding.setCurrentItem(currentPage + 1, true);
            } else {
                finishOnboarding();
            }
        });
    }

    private void setupViewPager() {
        List<PageData> pages = new ArrayList<>();
        pages.add(new PageData(R.drawable.ic_hero_dumbbell, "全方位健康追踪",
                "记录运动、饮食、饮水、睡眠、用药和习惯\n一站式管理你的健康数据"));
        pages.add(new PageData(R.drawable.img_welcome_hero, "AI 智能教练",
                "拍照识别食物热量，AI 为你定制训练计划\n智能分析饮食和进度，让健身更科学"));
        pages.add(new PageData(R.drawable.ic_hero_diet, "坚持，成为更好的自己",
                "每日任务、成就解锁、连续打卡激励\n从今天开始，和健康日记一起蜕变"));

        binding.vpOnboarding.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<PageHolder>() {
            @NonNull
            @Override
            public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ItemOnboardingPageBinding b = ItemOnboardingPageBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false);
                return new PageHolder(b);
            }

            @Override
            public void onBindViewHolder(@NonNull PageHolder holder, int position) {
                PageData data = pages.get(position);
                holder.binding.ivOnboarding.setImageResource(data.iconRes);
                holder.binding.tvOnboardingTitle.setText(data.title);
                holder.binding.tvOnboardingDesc.setText(data.desc);
            }

            @Override
            public int getItemCount() {
                return pages.size();
            }
        });

        binding.vpOnboarding.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateIndicators(position);
                binding.btnPrev.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
                if (position == 2) {
                    binding.btnNext.setText("开始使用");
                } else {
                    binding.btnNext.setText("下一步");
                }
            }
        });
    }

    private void setupIndicators(int count) {
        indicators.clear();
        binding.layoutIndicators.removeAllViews();
        int size = dp(10);
        int margin = dp(6);

        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
            dot.setImageDrawable(shape);

            indicators.add(dot);
            binding.layoutIndicators.addView(dot);
        }
    }

    private void updateIndicators(int position) {
        for (int i = 0; i < indicators.size(); i++) {
            GradientDrawable shape = (GradientDrawable) indicators.get(i).getDrawable();
            if (i == position) {
                shape.setColor(ContextCompat.getColor(requireContext(), R.color.fitnessdiary_primary));
                indicators.get(i).getLayoutParams().width = dp(24);
                indicators.get(i).getLayoutParams().height = dp(10);
                GradientDrawable active = new GradientDrawable();
                active.setShape(GradientDrawable.RECTANGLE);
                active.setCornerRadius(dp(5));
                active.setColor(ContextCompat.getColor(requireContext(), R.color.fitnessdiary_primary));
                indicators.get(i).setImageDrawable(active);
            } else {
                shape.setColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
                indicators.get(i).getLayoutParams().width = dp(10);
                indicators.get(i).getLayoutParams().height = dp(10);
                indicators.get(i).setImageDrawable(shape);
            }
            indicators.get(i).requestLayout();
        }
    }

    private void finishOnboarding() {
        markDone(requireContext());
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onRegistrationComplete();
        }
    }

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    private static class PageData {
        final int iconRes;
        final String title;
        final String desc;

        PageData(int iconRes, String title, String desc) {
            this.iconRes = iconRes;
            this.title = title;
            this.desc = desc;
        }
    }

    private static class PageHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        final ItemOnboardingPageBinding binding;

        PageHolder(ItemOnboardingPageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
