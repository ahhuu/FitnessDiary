package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentSocialFeedBinding;
import com.cz.fitnessdiary.model.SocialPostUiModel;
import com.cz.fitnessdiary.ui.adapter.SocialPostAdapter;
import com.cz.fitnessdiary.viewmodel.SocialViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class SocialFeedFragment extends Fragment {
    private FragmentSocialFeedBinding binding;
    private SocialViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentSocialFeedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(SocialViewModel.class);
        SocialPostAdapter adapter = new SocialPostAdapter(new SocialPostAdapter.Listener() {
            @Override public void onLike(SocialPostUiModel post) { viewModel.setLiked(post); }
            @Override public void onMore(SocialPostUiModel post) { showPostActions(post); }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        binding.fabCreate.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.createPostFragment));
        viewModel.getPosts().observe(getViewLifecycleOwner(), posts -> {
            adapter.submitList(posts);
            binding.emptyState.setVisibility(posts == null || posts.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> binding.progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onResume() { super.onResume(); if (viewModel != null) viewModel.loadFeed(); }

    private void showPostActions(SocialPostUiModel post) {
        String[] actions = post.isOwnedByCurrentUser() ? new String[]{"删除动态"} : new String[]{"举报动态"};
        new MaterialAlertDialogBuilder(requireContext()).setItems(actions, (dialog, which) -> {
            if (post.isOwnedByCurrentUser()) viewModel.deletePost(post.getPostId());
            else viewModel.reportPost(post.getPostId());
        }).show();
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
