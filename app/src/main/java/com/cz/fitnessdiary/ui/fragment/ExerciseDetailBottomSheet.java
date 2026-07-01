package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.cz.fitnessdiary.databinding.FragmentExerciseDetailBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * 动作详情弹窗 - 纯本地中文命名离线动图秒播系统
 * 100% 离线运行，文件直接以动作的中文名（如 "标准俯卧撑.gif"）保存在 assets/gifs 下，
 * 支持同名动作自动识别播放与部位兜底离线播放，彻底根治国内超时与闪退问题。
 */
public class ExerciseDetailBottomSheet extends BottomSheetDialogFragment {

    private FragmentExerciseDetailBottomSheetBinding binding;
    private ExerciseLibrary exercise;

    public static ExerciseDetailBottomSheet newInstance(ExerciseLibrary exercise) {
        ExerciseDetailBottomSheet sheet = new ExerciseDetailBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable("exercise", exercise);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            exercise = (ExerciseLibrary) getArguments().getSerializable("exercise");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentExerciseDetailBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (exercise == null) {
            dismiss();
            return;
        }

        binding.tvDetailName.setText(exercise.getName());
        binding.tvDetailBodyPart.setText("部位: " + (exercise.getBodyPart() != null ? exercise.getBodyPart() : "全身"));
        binding.tvDetailEquipment.setText("器械: " + (exercise.getEquipment() != null ? exercise.getEquipment() : "自重"));

        int diff = exercise.getDifficulty();
        String stars;
        switch (diff) {
            case 1: stars = "★☆☆"; break;
            case 2: stars = "★★☆"; break;
            case 3: stars = "★★★"; break;
            default: stars = "☆☆☆";
        }
        binding.tvDetailDifficulty.setText("难度: " + stars);

        // 设置默认离线首字艺术徽标
        String name = exercise.getName();
        if (name != null && !name.isEmpty()) {
            binding.tvDetailAvatar.setText(name.substring(0, 1));
        } else {
            binding.tvDetailAvatar.setText("练");
        }
        binding.tvDetailAvatar.setVisibility(View.VISIBLE);

        String desc = com.cz.fitnessdiary.utils.ExerciseGuideHelper.getDetailedGuide(
                exercise.getName(), exercise.getBodyPart(), exercise.getDescription()
        );
        binding.tvDetailDescription.setText(desc);

        // 核心逻辑：动作演示渲染切换
        renderExerciseMedia();

        binding.btnCloseDetail.setOnClickListener(v -> dismiss());
    }

    /**
     * 渐进式加载本地 assets 目录中的中文同名动图，无动图则以所属部位兜底动图展示
     */
    private void renderExerciseMedia() {
        String name = exercise.getName();
        String bodyPart = exercise.getBodyPart();

        // 屏蔽不稳定的在线 Lottie 通道，统一走 100% 稳定的本地 3D GIF 离线图示
        binding.lavExerciseAnim.setVisibility(View.GONE);
        binding.ivExerciseGif.setVisibility(View.VISIBLE);

        // 1. 判断本地 assets/gifs 目录下是否存在中文同名动图文件 (如 "哑铃上斜卧推.gif")
        String targetGifName = name + ".gif";
        boolean hasLocalGif = false;
        try {
            String[] files = requireContext().getAssets().list("gifs");
            if (files != null) {
                for (String file : files) {
                    if (file.equalsIgnoreCase(targetGifName)) {
                        hasLocalGif = true;
                        break;
                    }
                }
            }
        } catch (java.io.IOException e) {
            android.util.Log.e("ExerciseDetail", "Failed to list assets/gifs", e);
        }

        // 2. 决定文件名：有则直接加载，无则按部位兜底加载 (如 "胸部.gif")
        String gifFileName;
        if (hasLocalGif) {
            gifFileName = targetGifName;
        } else {
            gifFileName = (bodyPart != null ? bodyPart : "其他") + ".gif";
        }

        String localAssetUrl = "file:///android_asset/gifs/" + gifFileName;

        Glide.with(this)
                .asGif()
                .load(localAssetUrl)
                .listener(new com.bumptech.glide.request.RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, boolean isFirstResource) {
                        android.util.Log.e("ExerciseDetail", "Local assets Glide load failed for: " + localAssetUrl, e);
                        binding.tvDetailAvatar.setVisibility(View.VISIBLE); // 失败时继续展示大字徽标
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(com.bumptech.glide.load.resource.gif.GifDrawable resource, Object model, com.bumptech.glide.request.target.Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        binding.tvDetailAvatar.setVisibility(View.GONE); // 本地秒开加载成功，隐藏文字
                        return false;
                    }
                })
                .into(binding.ivExerciseGif);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
