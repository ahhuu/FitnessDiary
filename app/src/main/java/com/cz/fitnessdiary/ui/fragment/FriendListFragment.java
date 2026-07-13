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

import com.cz.fitnessdiary.databinding.FragmentFriendListBinding;
import com.cz.fitnessdiary.model.FriendUiModel;
import com.cz.fitnessdiary.ui.adapter.FriendAdapter;
import com.cz.fitnessdiary.viewmodel.SocialViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class FriendListFragment extends Fragment {
    private FragmentFriendListBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentFriendListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        SocialViewModel viewModel = new ViewModelProvider(this).get(SocialViewModel.class);
        FriendAdapter adapter = new FriendAdapter(false, new FriendAdapter.Listener() {
            @Override public void onPrimaryAction(FriendUiModel friend) { showProfile(friend); }
            @Override public void onSecondaryAction(FriendUiModel friend) { showActions(viewModel, friend); }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        viewModel.getFriends().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            binding.emptyState.setVisibility(items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
        viewModel.loadFriends();
    }

    private void showProfile(FriendUiModel friend) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(friend.getNickname())
                .setMessage(friend.getBio() + "\n\n好友码：" + friend.getFriendCode())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showActions(SocialViewModel viewModel, FriendUiModel friend) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(friend.getNickname())
                .setItems(new String[]{"删除好友", "拉黑并删除"}, (dialog, which) -> {
                    if (which == 0) viewModel.removeFriend(friend.getUserId());
                    else viewModel.blockUser(friend.getUserId());
                })
                .show();
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
