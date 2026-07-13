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

import com.cz.fitnessdiary.databinding.FragmentAddFriendBinding;
import com.cz.fitnessdiary.model.FriendUiModel;
import com.cz.fitnessdiary.utils.TextUtilsCompat;
import com.cz.fitnessdiary.viewmodel.SocialViewModel;

public final class AddFriendFragment extends Fragment {
    private FragmentAddFriendBinding binding;
    private SocialViewModel viewModel;
    private FriendUiModel result;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddFriendBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(SocialViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        binding.btnSearch.setOnClickListener(v -> viewModel.searchFriend(text(binding.etFriendCode)));
        binding.btnSendRequest.setOnClickListener(v -> {
            if (result != null) viewModel.sendRequest(result.getFriendCode());
        });
        viewModel.getSearchResult().observe(getViewLifecycleOwner(), this::showResult);
        viewModel.getLoading().observe(getViewLifecycleOwner(), value -> binding.progress.setVisibility(Boolean.TRUE.equals(value) ? View.VISIBLE : View.GONE));
        viewModel.getMessage().observe(getViewLifecycleOwner(), value -> Toast.makeText(requireContext(), value, Toast.LENGTH_SHORT).show());
    }

    private void showResult(FriendUiModel value) {
        result = value;
        boolean visible = value != null && !TextUtilsCompat.isBlank(value.getUserId());
        binding.resultCard.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        binding.tvName.setText(value.getNickname());
        binding.tvBio.setText(value.getBio());
        binding.tvAvatar.setText(value.getNickname().isEmpty() ? "友" : value.getNickname().substring(0, 1));
    }

    private String text(android.widget.EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); binding = null; }
}
