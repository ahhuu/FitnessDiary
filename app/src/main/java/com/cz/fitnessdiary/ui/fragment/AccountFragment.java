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

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentAccountBinding;
import com.cz.fitnessdiary.model.AccountUser;
import com.cz.fitnessdiary.viewmodel.AccountViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Email-code entry point; basic local health features never require login. */
public final class AccountFragment extends Fragment {
    private FragmentAccountBinding binding;
    private AccountViewModel viewModel;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        binding.btnSubmit.setOnClickListener(v -> viewModel.requestEmailCode(email()));
        binding.btnOpenFriends.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.friendsHomeFragment));
        binding.btnLogout.setOnClickListener(v -> confirmLogout());
        binding.btnDeleteAccount.setOnClickListener(v -> confirmDelete());
        viewModel.getLoading().observe(getViewLifecycleOwner(), active -> {
            boolean busy = Boolean.TRUE.equals(active);
            binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnSubmit.setEnabled(!busy);
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });
        viewModel.getChallenge().observe(getViewLifecycleOwner(), challenge -> {
            if (challenge == null) return;
            Bundle args = new Bundle();
            args.putString("email", challenge.getEmail());
            args.putString("verificationId", challenge.getVerificationId());
            args.putBoolean("existingUser", challenge.isExistingUser());
            NavHostFragment.findNavController(this).navigate(R.id.emailVerificationFragment, args);
        });
        viewModel.getAccount().observe(getViewLifecycleOwner(), this::renderAccount);
    }

    private void renderAccount(AccountUser account) {
        boolean loggedIn = account != null;
        binding.emailLayout.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        binding.btnSubmit.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        binding.loggedInPanel.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        binding.tvHeading.setText(loggedIn ? "云端账号已连接" : "登录后，与朋友一起坚持");
        binding.tvSubtitle.setText(loggedIn ? "退出账号不会删除本机健康记录。" : "基础记录无需登录；朋友与动态需要邮箱验证码登录。");
        if (loggedIn) binding.tvAccountEmail.setText(account.getEmail());
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle("注销云端账号（暂未开放）")
                .setMessage("云端资料删除功能尚未接入服务器。本机健康记录无论如何都不会被删除。")
                .setPositiveButton("我知道了", null)
                .setNegativeButton("取消", null).show();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle("退出云端账号？")
                .setMessage("退出后，本机健康记录不会删除；需要使用邮箱验证码才能再次登录。")
                .setPositiveButton("确认退出", (dialog, which) -> viewModel.logout())
                .setNegativeButton("取消", null).show();
    }

    private String email() { return binding.etEmail.getText() == null ? "" : binding.etEmail.getText().toString().trim(); }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
