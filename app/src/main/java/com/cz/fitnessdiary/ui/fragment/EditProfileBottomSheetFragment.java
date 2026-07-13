package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.databinding.FragmentEditProfileBinding;
import com.cz.fitnessdiary.repository.SocialRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public final class EditProfileBottomSheetFragment extends BottomSheetDialogFragment {
    private FragmentEditProfileBinding binding;

    public interface OnProfileUpdatedListener {
        void onProfileUpdated(String nickname, String bio);
    }

    private OnProfileUpdatedListener listener;
    private String initialNickname;
    private String initialBio;

    public void setListener(OnProfileUpdatedListener listener) {
        this.listener = listener;
    }

    public void setInitialData(String nickname, String bio) {
        this.initialNickname = nickname;
        this.initialBio = bio;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEditProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (initialNickname != null) binding.etNickname.setText(initialNickname);
        if (initialBio != null) binding.etBio.setText(initialBio);
        binding.btnSave.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String nickname = binding.etNickname.getText() == null
                ? "" : binding.etNickname.getText().toString().trim();
        String bio = binding.etBio.getText() == null
                ? "" : binding.etBio.getText().toString().trim();
        if (nickname.isEmpty()) {
            binding.etNickname.setError("昵称不能为空");
            return;
        }

        binding.btnSave.setEnabled(false);
        binding.btnSave.setText("保存中…");
        new SocialRepository(requireActivity().getApplication()).updateProfile(nickname, bio,
                new SocialRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "个人资料已更新", Toast.LENGTH_SHORT).show();
                            if (listener != null) listener.onProfileUpdated(nickname, bio);
                            dismiss();
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            binding.btnSave.setEnabled(true);
                            binding.btnSave.setText("保存");
                            String detail = error == null ? "" : error.getMessage();
                            Toast.makeText(requireContext(), "保存失败：" + detail,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
