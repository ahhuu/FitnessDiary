package com.cz.fitnessdiary.ui.fragment;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentMainHomeBinding;
import com.cz.fitnessdiary.model.AchievementUnlockEvent;
import com.cz.fitnessdiary.viewmodel.AchievementCenterViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 主页大容器 - 支持左右滑动切换四大核心功能
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

        // 关键优化：预加载所有页面 (0点到4点)
        binding.viewPager.setOffscreenPageLimit(4);

        // 滑动监听：同步底部导航
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                int itemId;
                switch (position) {
                    case 0:
                        itemId = R.id.checkInFragment;
                        break;
                    case 1:
                        itemId = R.id.planFragment;
                        break;
                    case 2:
                        itemId = R.id.dietFragment;
                        break;
                    case 3:
                        itemId = R.id.aiChatFragment;
                        break;
                    case 4:
                        itemId = R.id.profileFragment;
                        break;
                    default:
                        return;
                }
                // 仅在 ID 不同时更新，防止触发冗余监听
                if (binding.bottomNav.getSelectedItemId() != itemId) {
                    binding.bottomNav.setSelectedItemId(itemId);
                }
                updateBottomNavTheme(position);
            }
        });
    }

    private void setupBottomNavigation() {
        updateBottomNavTheme(TAB_CHECKIN);
        // 点击监听：同步 ViewPager2
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            int targetPos = -1;
            if (itemId == R.id.checkInFragment)
                targetPos = TAB_CHECKIN;
            else if (itemId == R.id.planFragment)
                targetPos = TAB_PLAN;
            else if (itemId == R.id.dietFragment)
                targetPos = TAB_DIET;
            else if (itemId == R.id.aiChatFragment)
                targetPos = TAB_AI;
            else if (itemId == R.id.profileFragment)
                targetPos = TAB_PROFILE;

            // 仅在位置不同时滚动，防止重复触发
            if (targetPos != -1 && binding.viewPager.getCurrentItem() != targetPos) {
                binding.viewPager.setCurrentItem(targetPos, true);
            }
            if (targetPos != -1) {
                updateBottomNavTheme(targetPos);
            }
            return targetPos != -1;
        });
    }

    private void updateBottomNavTheme(int tabIndex) {
        if (binding == null || getContext() == null) {
            return;
        }
        int checkedColor = getTabThemeColor(tabIndex);
        int uncheckedColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] {}
        };
        int[] colors = new int[] { checkedColor, uncheckedColor };
        ColorStateList navStateList = new ColorStateList(states, colors);
        binding.bottomNav.setItemIconTintList(navStateList);
        binding.bottomNav.setItemTextColor(navStateList);

        int indicatorColor = ColorUtils.setAlphaComponent(checkedColor, 34);
        binding.bottomNav.setItemActiveIndicatorColor(ColorStateList.valueOf(indicatorColor));
    }

    private int getTabThemeColor(int tabIndex) {
        if (tabIndex == TAB_CHECKIN) {
            return ContextCompat.getColor(requireContext(), R.color.fitnessdiary_primary);
        }
        if (tabIndex == TAB_PLAN) {
            return ContextCompat.getColor(requireContext(), R.color.plan_blue_primary);
        }
        if (tabIndex == TAB_DIET) {
            return ContextCompat.getColor(requireContext(), R.color.diet_primary);
        }
        if (tabIndex == TAB_AI) {
            return ContextCompat.getColor(requireContext(), R.color.ai_primary);
        }
        return ContextCompat.getColor(requireContext(), R.color.text_primary);
    }

    /**
     * 切换到指定的 Tab
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
