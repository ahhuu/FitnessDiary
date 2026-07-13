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
import com.cz.fitnessdiary.databinding.FragmentEmailVerificationBinding;
import com.cz.fitnessdiary.model.EmailCodeChallenge;
import com.cz.fitnessdiary.viewmodel.AccountViewModel;

public final class EmailVerificationFragment extends Fragment {
    private FragmentEmailVerificationBinding binding;
    private AccountViewModel viewModel;
    private EmailCodeChallenge challenge;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentEmailVerificationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        Bundle args = getArguments();
        String email = args == null ? "" : args.getString("email", "");
        challenge = new EmailCodeChallenge(email, args == null ? "" : args.getString("verificationId", ""),
                args != null && args.getBoolean("existingUser", false));
        binding.tvEmail.setText("验证码已发送至 " + email + "，10 分钟内有效。");
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        binding.btnVerifyCode.setOnClickListener(v -> viewModel.verifyEmailCode(challenge, code()));
        binding.btnResend.setOnClickListener(v -> viewModel.requestEmailCode(challenge.getEmail()));
        viewModel.getChallenge().observe(getViewLifecycleOwner(), value -> { if (value != null) challenge = value; });
        viewModel.getAccount().observe(getViewLifecycleOwner(), account -> { if (account != null) NavHostFragment.findNavController(this).navigate(R.id.friendsHomeFragment); });
        viewModel.getLoading().observe(getViewLifecycleOwner(), active -> binding.progress.setVisibility(Boolean.TRUE.equals(active) ? View.VISIBLE : View.GONE));
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> { if (message != null) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show(); });
    }

    private String code() { return binding.etCode.getText() == null ? "" : binding.etCode.getText().toString().trim(); }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
