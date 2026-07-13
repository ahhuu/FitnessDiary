package com.cz.fitnessdiary.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentFriendsHomeBinding;
import com.cz.fitnessdiary.model.AccountUser;
import com.cz.fitnessdiary.repository.AccountRepository;
import com.cz.fitnessdiary.utils.TextUtilsCompat;

public final class FriendsHomeFragment extends Fragment {
    private FragmentFriendsHomeBinding binding;
    private String friendCode = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFriendsHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AccountUser account = new AccountRepository(requireContext()).getCurrentAccount();
        if (account == null) {
            NavHostFragment.findNavController(this).navigate(R.id.accountFragment);
            return;
        }
        if (!account.isEmailVerified()) {
            Bundle args = new Bundle();
            args.putString("email", account.getEmail());
            NavHostFragment.findNavController(this).navigate(R.id.emailVerificationFragment, args);
            return;
        }
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        binding.cardFeed.setOnClickListener(v -> navigate(R.id.socialFeedFragment));
        binding.cardFriends.setOnClickListener(v -> navigate(R.id.friendListFragment));
        binding.cardRequests.setOnClickListener(v -> navigate(R.id.friendRequestsFragment));
        binding.btnAddFriend.setOnClickListener(v -> navigate(R.id.addFriendFragment));
        binding.btnCopyCode.setOnClickListener(v -> copyFriendCode());
        binding.btnShareCode.setOnClickListener(v -> shareFriendCode());
        loadMyProfile();
    }

    private void loadMyProfile() {
        new AccountRepository(requireContext()).loadMyProfile(new AccountRepository.Callback<com.cz.fitnessdiary.model.FriendUiModel>() {
            @Override
            public void onSuccess(com.cz.fitnessdiary.model.FriendUiModel profile) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (binding == null) return;
                    friendCode = profile.getFriendCode();
                    binding.tvFriendCode.setText(friendCode);
                    binding.btnCopyCode.setEnabled(true);
                    binding.btnShareCode.setEnabled(true);
                    
                    binding.tvMyNickname.setText(profile.getNickname());
                    binding.tvMyBio.setText(TextUtilsCompat.isBlank(profile.getBio())
                            ? "未设置简介" : profile.getBio());
                    
                    if (!TextUtilsCompat.isBlank(profile.getAvatarUrl())) {
                        binding.tvMyAvatarText.setVisibility(View.GONE);
                        binding.ivMyAvatar.setVisibility(View.VISIBLE);
                        com.bumptech.glide.Glide.with(requireContext())
                             .load(profile.getAvatarUrl())
                             .placeholder(R.drawable.bg_circle_light_primary)
                             .into(binding.ivMyAvatar);
                    } else {
                        binding.ivMyAvatar.setVisibility(View.GONE);
                        binding.tvMyAvatarText.setVisibility(View.VISIBLE);
                        binding.tvMyAvatarText.setText(profile.getNickname().isEmpty() ? "我" : profile.getNickname().substring(0, 1));
                    }
                    
                    binding.btnEditProfile.setOnClickListener(v -> {
                        EditProfileBottomSheetFragment sheet = new EditProfileBottomSheetFragment();
                        sheet.setInitialData(profile.getNickname(), profile.getBio());
                        sheet.setListener((nickname, bio) -> {
                            loadMyProfile(); // Reload after edit
                        });
                        sheet.show(getChildFragmentManager(), "EditProfile");
                    });
                });
            }

            @Override
            public void onError(Throwable error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (binding == null) return;
                    binding.tvFriendCode.setText("加载失败");
                    Toast.makeText(requireContext(), "资料加载失败，请稍后重试", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void copyFriendCode() {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("FitnessDiary 好友码", friendCode));
        Toast.makeText(requireContext(), "好友码已复制", Toast.LENGTH_SHORT).show();
    }

    private void shareFriendCode() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "在 FitnessDiary 添加我为好友吧，我的好友码是：" + friendCode);
        startActivity(Intent.createChooser(intent, "分享好友码"));
    }

    private void navigate(int destination) {
        NavHostFragment.findNavController(this).navigate(destination);
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); binding = null; }
}
