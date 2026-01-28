package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentWelcomeBinding;
import com.cz.fitnessdiary.ui.MainActivity;
import com.cz.fitnessdiary.utils.CalorieCalculatorUtils;
import com.cz.fitnessdiary.viewmodel.WelcomeViewModel;
import com.google.android.material.chip.Chip;

/**
 * 欢迎注册页面 - 2.0 智能化版本
 * 添加性别、目标、活动水平选择和智能卡路里计算预览
 */
public class WelcomeFragment extends Fragment {

    private FragmentWelcomeBinding binding;
    private WelcomeViewModel viewModel;

    // 用户选择的数据
    private int selectedGender = 0; // 默认女性
    private int selectedGoal = 0; // 默认减脂
    private float selectedActivityLevel = 1.2f; // 默认久坐

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(WelcomeViewModel.class);

        setupGenderChips();
        setupGoalChips();
        setupActivityChips();
        setupNavigation();
        setupTextWatchers();

        // 设置开始按钮点击监听
        binding.btnStart.setOnClickListener(v -> registerUser());
        binding.btnSkip.setOnClickListener(v -> registerUserSkip());

        // 拦截返回键逻辑：在设置页按返回键应回到封面页
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (binding.viewFlipper.getDisplayedChild() == 1) {
                            // 如果在设置页，回到封面页
                            binding.viewFlipper.setDisplayedChild(0);
                        } else {
                            // 如果在封面页，禁用拦截并再次触发返回（即退出应用）
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }

    /**
     * 设置页面切换导航
     */
    private void setupNavigation() {
        binding.btnToSetup.setOnClickListener(v -> binding.viewFlipper.setDisplayedChild(1));
        binding.btnBackToLanding.setOnClickListener(v -> binding.viewFlipper.setDisplayedChild(0));
    }

    /**
     * 设置输入监听以更新预览
     */
    private void setupTextWatchers() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateCaloriePreview();
            }
        };
        binding.etHeight.addTextChangedListener(watcher);
        binding.etWeight.addTextChangedListener(watcher);
        binding.etAge.addTextChangedListener(watcher);
    }

    /**
     * 设置性别选择
     */
    private void setupGenderChips() {
        binding.chipGenderGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_female)) {
                selectedGender = CalorieCalculatorUtils.GENDER_FEMALE;
            } else if (checkedIds.contains(R.id.chip_male)) {
                selectedGender = CalorieCalculatorUtils.GENDER_MALE;
            }
            updateCaloriePreview();
        });
    }

    /**
     * 设置目标选择
     */
    private void setupGoalChips() {
        binding.chipGoalGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_lose_fat)) {
                selectedGoal = CalorieCalculatorUtils.GOAL_LOSE_FAT;
            } else if (checkedIds.contains(R.id.chip_gain_muscle)) {
                selectedGoal = CalorieCalculatorUtils.GOAL_GAIN_MUSCLE;
            } else if (checkedIds.contains(R.id.chip_maintain)) {
                selectedGoal = CalorieCalculatorUtils.GOAL_MAINTAIN;
            }
            updateCaloriePreview();
        });
    }

    /**
     * 设置活动水平选择
     */
    private void setupActivityChips() {
        binding.chipActivityGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_sedentary)) {
                selectedActivityLevel = 1.2f;
            } else if (checkedIds.contains(R.id.chip_light)) {
                selectedActivityLevel = 1.375f;
            } else if (checkedIds.contains(R.id.chip_moderate)) {
                selectedActivityLevel = 1.55f;
            }
            updateCaloriePreview();
        });
    }

    /**
     * 更新卡路里预览
     */
    private void updateCaloriePreview() {
        String heightStr = binding.etHeight.getText().toString().trim();
        String weightStr = binding.etWeight.getText().toString().trim();
        String ageStr = binding.etAge.getText().toString().trim();

        if (!TextUtils.isEmpty(heightStr) && !TextUtils.isEmpty(weightStr) && !TextUtils.isEmpty(ageStr)) {
            try {
                float height = Float.parseFloat(heightStr);
                float weight = Float.parseFloat(weightStr);
                int age = Integer.parseInt(ageStr);

                int targetCalories = viewModel.previewCalorieTarget(
                        height, weight, age, selectedGender, selectedGoal, selectedActivityLevel);

                binding.tvCaloriePreview.setText("预计每日目标：" + targetCalories + " 千卡");
                binding.tvCaloriePreview.setVisibility(View.VISIBLE);
            } catch (NumberFormatException e) {
                binding.tvCaloriePreview.setVisibility(View.GONE);
            }
        } else {
            binding.tvCaloriePreview.setVisibility(View.GONE);
        }
    }

    /**
     * 直接跳过注册 (使用默认零值)
     */
    private void registerUserSkip() {
        // 默认参数：新用户，各项指标为 0
        viewModel.registerUserWithGoal("新用户", 0f, 0f, 0,
                selectedGender, CalorieCalculatorUtils.GOAL_MAINTAIN, 1.2f);

        Toast.makeText(getContext(), "已跳过设置，您可以稍后在个人页面完善", Toast.LENGTH_SHORT).show();
        navigateToMain();
    }

    /**
     * 注册用户 (正常表单提交)
     */
    private void registerUser() {
        String name = binding.etNickname.getText().toString().trim();
        String heightStr = binding.etHeight.getText().toString().trim();
        String weightStr = binding.etWeight.getText().toString().trim();
        String ageStr = binding.etAge.getText().toString().trim();

        // 验证输入
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(getContext(), "请输入昵称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(heightStr)) {
            Toast.makeText(getContext(), "请输入身高", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(weightStr)) {
            Toast.makeText(getContext(), "请输入体重", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(ageStr)) {
            Toast.makeText(getContext(), "请输入年龄", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float height = Float.parseFloat(heightStr);
            float weight = Float.parseFloat(weightStr);
            int age = Integer.parseInt(ageStr);

            // 验证数值范围
            if (height <= 0 || height > 300) {
                Toast.makeText(getContext(), "请输入有效的身高", Toast.LENGTH_SHORT).show();
                return;
            }

            if (weight <= 0 || weight > 500) {
                Toast.makeText(getContext(), "请输入有效的体重", Toast.LENGTH_SHORT).show();
                return;
            }

            if (age <= 0 || age > 150) {
                Toast.makeText(getContext(), "请输入有效的年龄", Toast.LENGTH_SHORT).show();
                return;
            }

            // 使用设置的资料注册
            viewModel.registerUserWithGoal(name, height, weight, age,
                    selectedGender, selectedGoal, selectedActivityLevel);

            Toast.makeText(getContext(), "注册成功！每日卡路里目标已自动计算", Toast.LENGTH_LONG).show();
            navigateToMain();

        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 导航到主页
     */
    private void navigateToMain() {
        binding.getRoot().postDelayed(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onRegistrationComplete();
            }
        }, 500);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
