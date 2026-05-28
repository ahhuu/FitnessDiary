package com.cz.fitnessdiary.ui.fragment;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentMainHomeBinding;
import com.cz.fitnessdiary.model.AchievementUnlockEvent;
import com.cz.fitnessdiary.viewmodel.AchievementCenterViewModel;

/**
 * 主页大容器 - 支持左右滑动切换五大核心功能（自定义精美底部导航）
 */
public class MainHomeFragment extends Fragment {

    private FragmentMainHomeBinding binding;
    private static final int TAB_CHECKIN = 0;
    private static final int TAB_PLAN = 1;
    private static final int TAB_DIET = 2;
    private static final int TAB_AI = 3;
    private static final int TAB_PROFILE = 4;
    private final Handler noticeHandler = new Handler(Looper.getMainLooper());
    private AchievementCenterViewModel achievementCenterViewModel;
    private final Runnable autoDismissRunnable = this::dismissGlobalNotice;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentMainHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        achievementCenterViewModel = new ViewModelProvider(requireActivity()).get(AchievementCenterViewModel.class);
        setupViewPager();
        setupBottomNavigation();
        setupGlobalNotice();
        observeAchievementEvents();
        // 初始高亮第一页（记录）
        updateBottomNavTheme(TAB_CHECKIN);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (achievementCenterViewModel != null) {
            achievementCenterViewModel.refreshAll();
        }
    }

    private void setupViewPager() {
        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return new CheckInFragment();
                    case 1:
                        return new PlanFragment();
                    case 2:
                        return new DietFragment();
                    case 3:
                        return new AIChatFragment();
                    case 4:
                        return new ProfileFragment();
                    default:
                        return new CheckInFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 5;
            }
        };

        binding.viewPager.setAdapter(adapter);

        // 预加载所有主页面，避免 Tab 频繁切换卡顿
        binding.viewPager.setOffscreenPageLimit(4);

        // 滑动监听：同步底部导航的高亮
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateBottomNavTheme(position);
            }
        });
    }

    /**
     * 初始化自定义底部导航栏的卡座点击事件
     */
    private void setupBottomNavigation() {
        binding.tabCheckin.setOnClickListener(v -> binding.viewPager.setCurrentItem(TAB_CHECKIN, true));
        binding.tabPlan.setOnClickListener(v -> binding.viewPager.setCurrentItem(TAB_PLAN, true));
        binding.tabDiet.setOnClickListener(v -> binding.viewPager.setCurrentItem(TAB_DIET, true));
        binding.tabAi.setOnClickListener(v -> binding.viewPager.setCurrentItem(TAB_AI, true));
        binding.tabProfile.setOnClickListener(v -> binding.viewPager.setCurrentItem(TAB_PROFILE, true));
    }

    /**
     * 动态渲染自定义 Tab 的选中和未选中视觉效果
     */
    private void updateBottomNavTheme(int activeIndex) {
        if (binding == null || getContext() == null) {
            return;
        }

        // 选中和未选中的色彩
        int colorSelected = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int colorUnselected = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        // 遍历更新所有 Tab Item 的视图
        updateSingleTabState(TAB_CHECKIN, activeIndex == TAB_CHECKIN, colorSelected, colorUnselected);
        updateSingleTabState(TAB_PLAN, activeIndex == TAB_PLAN, colorSelected, colorUnselected);
        updateSingleTabState(TAB_DIET, activeIndex == TAB_DIET, colorSelected, colorUnselected);
        updateSingleTabState(TAB_AI, activeIndex == TAB_AI, colorSelected, colorUnselected);
        updateSingleTabState(TAB_PROFILE, activeIndex == TAB_PROFILE, colorSelected, colorUnselected);
    }

    /**
     * 更新单个 Tab 状态
     */
    private void updateSingleTabState(int index, boolean isSelected, int selectedColor, int unselectedColor) {
        View pillView;
        ImageView iconView;
        TextView textView;

        switch (index) {
            case TAB_CHECKIN:
                pillView = binding.tabCheckinPill;
                iconView = binding.tabCheckinIcon;
                textView = binding.tabCheckinText;
                break;
            case TAB_PLAN:
                pillView = binding.tabPlanPill;
                iconView = binding.tabPlanIcon;
                textView = binding.tabPlanText;
                break;
            case TAB_DIET:
                pillView = binding.tabDietPill;
                iconView = binding.tabDietIcon;
                textView = binding.tabDietText;
                break;
            case TAB_AI:
                pillView = binding.tabAiPill;
                iconView = binding.tabAiIcon;
                textView = binding.tabAiText;
                break;
            case TAB_PROFILE:
                pillView = binding.tabProfilePill;
                iconView = binding.tabProfileIcon;
                textView = binding.tabProfileText;
                break;
            default:
                return;
        }

        if (isSelected) {
            // 选中状态：显示胶囊高亮背景，文字和图标变成主色调
            pillView.setBackgroundResource(R.drawable.bg_nav_selected_pill);
            iconView.setImageTintList(ColorStateList.valueOf(selectedColor));
            textView.setTextColor(selectedColor);
            iconView.setSelected(true);
        } else {
            // 未选中状态：透明背景，文字和图标为次要灰褐色
            pillView.setBackground(null);
            iconView.setImageTintList(ColorStateList.valueOf(unselectedColor));
            textView.setTextColor(unselectedColor);
            iconView.setSelected(false);
        }
    }

    /**
     * 切换到指定的 Tab（提供给子页面/其它组件调用）
     *
     * @param position 0=记录, 1=计划, 2=饮食, 3=AI, 4=我的
     */
    public void switchToTab(int position) {
        if (binding != null && binding.viewPager != null) {
            binding.viewPager.setCurrentItem(position, true);
        }
    }

    private void setupGlobalNotice() {
        binding.btnGlobalNoticeClose.setOnClickListener(v -> dismissGlobalNotice());
        binding.btnGlobalNoticeAction.setOnClickListener(v -> {
            new AchievementBottomSheetFragment().show(getChildFragmentManager(), "AchievementBottomSheet");
            dismissGlobalNotice();
        });
    }

    private void observeAchievementEvents() {
        achievementCenterViewModel.getUnlockEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            AchievementUnlockEvent unlock = event.getContentIfNotHandled();
            if (unlock == null) {
                return;
            }
            showGlobalNotice(unlock);
        });
    }

    private void showGlobalNotice(AchievementUnlockEvent unlock) {
        binding.tvGlobalNoticeEmoji.setText(unlock.getEmoji());
        binding.tvGlobalNoticeTitle.setText(unlock.getTitle());
        binding.tvGlobalNoticeDesc.setText(unlock.getDescription());
        binding.btnGlobalNoticeAction.setVisibility(
                unlock.getType() == AchievementUnlockEvent.TYPE_ACHIEVEMENT ? View.VISIBLE : View.GONE);
        binding.cardGlobalNotice.setVisibility(View.VISIBLE);
        binding.cardGlobalNotice.setAlpha(0f);
        binding.cardGlobalNotice.setTranslationY(-12f);
        binding.cardGlobalNotice.animate().alpha(1f).translationY(0f).setDuration(180).start();
        noticeHandler.removeCallbacks(autoDismissRunnable);
        noticeHandler.postDelayed(autoDismissRunnable, 2600);
    }

    private void dismissGlobalNotice() {
        if (binding == null || binding.cardGlobalNotice.getVisibility() != View.VISIBLE) {
            if (achievementCenterViewModel != null) {
                achievementCenterViewModel.consumeUnlockEvent();
            }
            return;
        }
        noticeHandler.removeCallbacks(autoDismissRunnable);
        binding.cardGlobalNotice.animate().alpha(0f).translationY(-8f).setDuration(150).withEndAction(() -> {
            if (binding != null) {
                binding.cardGlobalNotice.setVisibility(View.GONE);
                binding.cardGlobalNotice.setAlpha(1f);
                binding.cardGlobalNotice.setTranslationY(0f);
            }
            if (achievementCenterViewModel != null) {
                achievementCenterViewModel.consumeUnlockEvent();
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        noticeHandler.removeCallbacksAndMessages(null);
        binding = null;
    }
}
