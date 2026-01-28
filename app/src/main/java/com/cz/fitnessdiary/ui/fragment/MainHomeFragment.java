package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentMainHomeBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 主页大容器 - 支持左右滑动切换四大核心功能
 */
public class MainHomeFragment extends Fragment {

    private FragmentMainHomeBinding binding;

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
        setupViewPager();
        setupBottomNavigation();
    }

    private void setupViewPager() {
        List<Fragment> fragments = new ArrayList<>();
        fragments.add(new CheckInFragment());
        fragments.add(new PlanFragment());
        fragments.add(new DietFragment());
        fragments.add(new ProfileFragment());

        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return fragments.get(position);
            }

            @Override
            public int getItemCount() {
                return fragments.size();
            }
        };

        binding.viewPager.setAdapter(adapter);

        // 关键：禁用 ViewPager2 的离屏加载限制以保证页面平滑，但其实默认已经很好
        // binding.viewPager.setOffscreenPageLimit(3);

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
                        itemId = R.id.profileFragment;
                        break;
                    default:
                        return;
                }
                // 仅在 ID 不同时更新，防止触发冗余监听
                if (binding.bottomNav.getSelectedItemId() != itemId) {
                    binding.bottomNav.setSelectedItemId(itemId);
                }
            }
        });
    }

    private void setupBottomNavigation() {
        // 点击监听：同步 ViewPager2
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            int targetPos = -1;
            if (itemId == R.id.checkInFragment)
                targetPos = 0;
            else if (itemId == R.id.planFragment)
                targetPos = 1;
            else if (itemId == R.id.dietFragment)
                targetPos = 2;
            else if (itemId == R.id.profileFragment)
                targetPos = 3;

            // 仅在位置不同时滚动，防止重复触发
            if (targetPos != -1 && binding.viewPager.getCurrentItem() != targetPos) {
                binding.viewPager.setCurrentItem(targetPos, true);
            }
            return targetPos != -1;
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
