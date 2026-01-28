package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.cz.fitnessdiary.databinding.FragmentAchievementBottomSheetBinding;
import com.cz.fitnessdiary.ui.adapter.AchievementAdapter;
import com.cz.fitnessdiary.viewmodel.ProfileViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * 成就系统展示底栏 - v1.2
 */
public class AchievementBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentAchievementBottomSheetBinding binding;
    private ProfileViewModel viewModel;
    private AchievementAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAchievementBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireParentFragment()).get(ProfileViewModel.class);

        setupRecyclerView();
        observeViewModel();
    }

    private void setupRecyclerView() {
        adapter = new AchievementAdapter();
        // 3列网格布局
        binding.rvAchievements.setLayoutManager(new GridLayoutManager(getContext(), 3));
        binding.rvAchievements.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getAchievements().observe(getViewLifecycleOwner(), achievements -> {
            if (achievements != null) {
                adapter.setAchievements(achievements);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
