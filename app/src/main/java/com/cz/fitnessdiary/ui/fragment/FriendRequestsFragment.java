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

public final class FriendRequestsFragment extends Fragment {
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
        FriendAdapter adapter = new FriendAdapter(true, new FriendAdapter.Listener() {
            @Override public void onPrimaryAction(FriendUiModel friend) { viewModel.respond(friend.getRequestId(), true); }
            @Override public void onSecondaryAction(FriendUiModel friend) { viewModel.respond(friend.getRequestId(), false); }
        });
        binding.toolbar.setTitle("好友申请");
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        viewModel.getRequests().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            binding.emptyState.setVisibility(items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
        viewModel.loadRequests();
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
