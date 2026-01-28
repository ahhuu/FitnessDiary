package com.cz.fitnessdiary.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentProfileBinding;
import com.cz.fitnessdiary.ui.adapter.AchievementAdapter;
import com.cz.fitnessdiary.viewmodel.ProfileViewModel;
import androidx.recyclerview.widget.LinearLayoutManager;

/**
 * Profile Fragment - 用户个人信息页面
 * 核心功能：显示用户数据、修改体重身高、设置目标、清除数据、更换头像
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private AchievementAdapter achievementAdapter; // Plan 10
    private boolean isAchievementsExpanded = true; // Plan 11: 折叠状态

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 注册图库选择器
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            // 申请持久化权限
                            try {
                                requireContext().getContentResolver().takePersistableUriPermission(
                                        imageUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION);

                                // 保存 URI 到数据库
                                viewModel.updateAvatarUri(imageUri.toString());

                                // 立即更新头像显示
                                binding.ivAvatar.setImageURI(imageUri);

                                Toast.makeText(getContext(), "头像已更新", Toast.LENGTH_SHORT).show();
                            } catch (SecurityException e) {
                                Toast.makeText(getContext(), "无法获取图片权限", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        setupAchievementRecyclerView(); // Plan 10
        observeViewModel();
        setupClickListeners();
    }

    // Plan 10: 设置成就墙 RecyclerView
    private void setupAchievementRecyclerView() {
        achievementAdapter = new AchievementAdapter();
        binding.rvAchievements.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvAchievements.setAdapter(achievementAdapter);

        // Plan 11: 设置折叠交互
        binding.layoutAchievementHeader.setOnClickListener(v -> toggleAchievementsExpansion());
    }

    // Plan 11: 切换成就板块展开/折叠
    private void toggleAchievementsExpansion() {
        isAchievementsExpanded = !isAchievementsExpanded;

        // 切换可见性
        binding.rvAchievements.setVisibility(isAchievementsExpanded ? View.VISIBLE : View.GONE);

        // 旋转箭头动画
        binding.ivAchievementArrow.animate()
                .rotation(isAchievementsExpanded ? 0 : 180)
                .setDuration(300)
                .start();
    }

    /**
     * 观察 ViewModel 数据
     */
    private void observeViewModel() {
        // 观察用户信息
        viewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                // 显示用户名（如果为空则显示默认值）
                String nickname = (user.getNickname() == null || user.getNickname().isEmpty())
                        ? "健身达人"
                        : user.getNickname();
                binding.tvUsername.setText(nickname);

                binding.tvWeight.setText(String.valueOf(user.getWeight()));
                binding.tvHeight.setText(String.valueOf((int) user.getHeight()));
                binding.tvGoal.setText(user.getGoal() != null ? user.getGoal() : "减脂");

                // 加载头像
                if (user.getAvatarUri() != null && !user.getAvatarUri().isEmpty()) {
                    try {
                        Uri avatarUri = Uri.parse(user.getAvatarUri());
                        binding.ivAvatar.setImageURI(avatarUri);
                    } catch (Exception e) {
                        // 如果 URI 无效，使用默认头像
                        binding.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
                    }
                }

                // Plan 34: 显示年龄
                binding.tvAge.setText(user.getAge() + " 岁");

                // Plan 34: 显示性别 (带动态emoji)
                boolean isMale = user.getGender() == 1; // 1=男, 0=女
                binding.tvGender.setText(isMale ? "男" : "女");
                binding.tvGenderIcon.setText(isMale ? "👦 性别: " : "👧 性别: ");

                // Plan 34: 显示活动水平
                float activityLevel = user.getActivityLevel();
                String activityText = getActivityLevelText(activityLevel);
                binding.tvActivityLevel.setText(activityText + " (" + activityLevel + ")");
            }
        });

        // 观察 BMI
        viewModel.getBmi().observe(getViewLifecycleOwner(), bmiValue -> {
            if (bmiValue != null) {
                binding.tvBmi.setText(String.valueOf(bmiValue));
            }
        });

        // 观察 BMR
        viewModel.getBmr().observe(getViewLifecycleOwner(), bmrValue -> {
            if (bmrValue != null) {
                binding.tvBmr.setText(String.valueOf(bmrValue));
            }
        });

        // Plan 10: 观察用户等级
        viewModel.getUserLevel().observe(getViewLifecycleOwner(), level -> {
            if (level != null && !level.isEmpty()) {
                binding.tvUserLevel.setText(level);
            }
        });

        // Plan 10: 观察成就数据
        viewModel.getAchievements().observe(getViewLifecycleOwner(), achievements -> {
            if (achievements != null) {
                achievementAdapter.setAchievements(achievements);
            }
        });
    }

    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        // 点击头像 - 选择图片
        binding.ivAvatar.setOnClickListener(v -> openImagePicker());

        // 点击用户名 - 修改用户名
        binding.tvUsername.setOnClickListener(v -> showEditNicknameDialog());

        // 点击体重 - 修改体重
        View.OnClickListener weightClickListener = v -> showEditWeightDialog();
        binding.tvWeight.setOnClickListener(weightClickListener);

        // 点击身高 - 修改身高
        View.OnClickListener heightClickListener = v -> showEditHeightDialog();
        binding.tvHeight.setOnClickListener(heightClickListener);

        // 点击目标卡片 - 切换目标
        binding.cardGoal.setOnClickListener(v -> showGoalSelectionDialog());

        // 点击清除数据
        binding.cardClearData.setOnClickListener(v -> showClearDataDialog());

        // Plan 33: 点击BMI - 显示详情
        binding.layoutBmi.setOnClickListener(v -> showBMIDetailDialog());

        // Plan 33: 点击BMR - 显示详情
        binding.layoutBmr.setOnClickListener(v -> showBMRDetailDialog());

        // Plan 34: 点击年龄 - 修改年龄
        binding.layoutAge.setOnClickListener(v -> showEditAgeDialog());

        // Plan 34: 点击性别 - 修改性别
        binding.layoutGender.setOnClickListener(v -> showEditGenderDialog());

        // Plan 34: 点击活动水平 - 修改活动水平
        binding.cardActivityLevel.setOnClickListener(v -> showEditActivityLevelDialog());
    }

    /**
     * 打开图库选择器
     */
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        pickImageLauncher.launch(intent);
    }

    /**
     * 显示修改用户名对话框
     */
    private void showEditNicknameDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("请输入用户名");

        // 预填充当前用户名
        viewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null && user.getNickname() != null) {
                input.setText(user.getNickname());
                input.setSelection(input.getText().length()); // 光标移到末尾
            }
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("修改用户名")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String nickname = input.getText().toString().trim();
                    if (!nickname.isEmpty()) {
                        viewModel.updateNickname(nickname);
                        Toast.makeText(getContext(), "用户名已更新", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "用户名不能为空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示修改体重对话框
     */
    private void showEditWeightDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("请输入体重 (kg)");

        new AlertDialog.Builder(requireContext())
                .setTitle("修改体重")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String weightStr = input.getText().toString().trim();
                    if (!weightStr.isEmpty()) {
                        try {
                            double weight = Double.parseDouble(weightStr);
                            if (weight > 0 && weight < 300) {
                                viewModel.updateWeight(weight);
                                Toast.makeText(getContext(), "体重已更新", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "请输入有效的体重", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(getContext(), "输入格式错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示修改身高对话框
     */
    private void showEditHeightDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("请输入身高 (cm)");

        new AlertDialog.Builder(requireContext())
                .setTitle("修改身高")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String heightStr = input.getText().toString().trim();
                    if (!heightStr.isEmpty()) {
                        try {
                            int height = Integer.parseInt(heightStr);
                            if (height > 0 && height < 250) {
                                viewModel.updateHeight(height);
                                Toast.makeText(getContext(), "身高已更新", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "请输入有效的身高", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(getContext(), "输入格式错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示目标选择对话框
     */
    private void showGoalSelectionDialog() {
        String[] goals = { "减脂", "增肌", "保持" };

        new AlertDialog.Builder(requireContext())
                .setTitle("选择目标")
                .setItems(goals, (dialog, which) -> {
                    String selectedGoal = goals[which];
                    viewModel.updateGoal(selectedGoal);
                    Toast.makeText(getContext(), "目标已切换为：" + selectedGoal, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示清除数据确认对话框
     */
    private void showClearDataDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("清除所有数据")
                .setMessage("确定要清除所有数据吗？此操作不可恢复！")
                .setPositiveButton("确定清除", (dialog, which) -> {
                    viewModel.clearAllData();
                    Toast.makeText(getContext(), "数据已清除", Toast.LENGTH_SHORT).show();

                    // 重新加载数据
                    requireActivity().recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Plan 33: 显示BMI详情对话框
     */
    private void showBMIDetailDialog() {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(),
                android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        View view = getLayoutInflater().inflate(R.layout.dialog_bmi_detail, null);
        dialog.setContentView(view);

        // 返回按钮
        view.findViewById(R.id.btn_back).setOnClickListener(v -> dialog.dismiss());

        // 获取用户数据
        com.cz.fitnessdiary.database.entity.User user = viewModel.getCurrentUser().getValue();
        if (user == null) {
            dialog.show();
            return;
        }

        double weight = user.getWeight();
        int height = (int) user.getHeight();
        double heightM = height / 100.0;
        double bmi = weight / (heightM * heightM);

        // 设置BMI值
        android.widget.TextView tvBmiValue = view.findViewById(R.id.tv_bmi_value);
        tvBmiValue.setText(String.format(java.util.Locale.getDefault(), "%.1f", bmi));

        // 设置分类标签和颜色
        android.widget.TextView tvCategory = view.findViewById(R.id.tv_bmi_category);
        String category;
        int categoryColor;
        if (bmi < 18.5) {
            category = "偏瘦";
            categoryColor = android.graphics.Color.parseColor("#4FC3F7");
        } else if (bmi < 24.0) {
            category = "正常";
            categoryColor = android.graphics.Color.parseColor("#4CAF50");
        } else if (bmi < 28.0) {
            category = "偏重";
            categoryColor = android.graphics.Color.parseColor("#FF9800");
        } else {
            category = "肥胖";
            categoryColor = android.graphics.Color.parseColor("#F44336");
        }
        tvCategory.setText(category);
        tvCategory.getBackground().setColorFilter(categoryColor, android.graphics.PorterDuff.Mode.SRC_IN);
        tvBmiValue.setTextColor(categoryColor);

        // 设置指针位置 (BMI范围: 10-35, 映射到进度条宽度)
        View pointer = view.findViewById(R.id.view_bmi_pointer);
        pointer.post(() -> {
            View bar = view.findViewById(R.id.view_bmi_bar);
            int barWidth = bar.getWidth();
            double clampedBmi = Math.max(10, Math.min(35, bmi));
            double progress = (clampedBmi - 10) / 25.0; // 0.0 到 1.0
            int pointerMargin = (int) (progress * barWidth) - 6; // 减去指针宽度的一半
            android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) pointer
                    .getLayoutParams();
            params.setMarginStart(Math.max(0, pointerMargin));
            pointer.setLayoutParams(params);
        });

        // 数据分析
        android.widget.TextView tvHeightValue = view.findViewById(R.id.tv_height_value);
        android.widget.TextView tvWeightValue = view.findViewById(R.id.tv_weight_value);
        android.widget.TextView tvSuggestedWeight = view.findViewById(R.id.tv_suggested_weight);

        tvHeightValue.setText(String.valueOf(height) + ".0");
        tvWeightValue.setText(String.format(java.util.Locale.getDefault(), "%.1f", weight));

        // 计算建议体重范围 (BMI 18.5 ~ 24.0)
        double minWeight = 18.5 * heightM * heightM;
        double maxWeight = 24.0 * heightM * heightM;
        tvSuggestedWeight.setText(String.format(java.util.Locale.getDefault(), "%.1f ~ %.1f", minWeight, maxWeight));

        dialog.show();
    }

    /**
     * Plan 33: 显示BMR详情对话框
     */
    private void showBMRDetailDialog() {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(),
                android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        View view = getLayoutInflater().inflate(R.layout.dialog_bmr_detail, null);
        dialog.setContentView(view);

        // 返回按钮
        view.findViewById(R.id.btn_back).setOnClickListener(v -> dialog.dismiss());

        // 获取用户数据
        com.cz.fitnessdiary.database.entity.User user = viewModel.getCurrentUser().getValue();
        if (user == null) {
            dialog.show();
            return;
        }

        double weight = user.getWeight();
        int height = (int) user.getHeight();
        int age = user.getAge();
        boolean isMale = user.getGender() == 1; // 1=男, 0=女

        // 计算BMR (Mifflin-St Jeor公式)
        double bmr;
        if (isMale) {
            bmr = 10 * weight + 6.25 * height - 5 * age + 5;
        } else {
            bmr = 10 * weight + 6.25 * height - 5 * age - 161;
        }

        // 设置BMR值
        android.widget.TextView tvBmrValue = view.findViewById(R.id.tv_bmr_value);
        tvBmrValue.setText(String.valueOf((int) bmr));

        // 计算每日热量建议 (TDEE = BMR * 活动系数)
        // 假设轻度活动系数 1.375
        double tdee = bmr * 1.375;
        int deficitCalories = (int) (tdee * 0.8); // 减脂: 赤字20%
        int maintainCalories = (int) tdee; // 维持
        int surplusCalories = (int) (tdee * 1.15); // 增肌: 盈余15%

        android.widget.TextView tvDeficit = view.findViewById(R.id.tv_deficit_calories);
        android.widget.TextView tvMaintain = view.findViewById(R.id.tv_maintain_calories);
        android.widget.TextView tvSurplus = view.findViewById(R.id.tv_surplus_calories);

        tvDeficit.setText(deficitCalories + " 千卡");
        tvMaintain.setText(maintainCalories + " 千卡");
        tvSurplus.setText(surplusCalories + " 千卡");

        // 计算依据
        android.widget.TextView tvGender = view.findViewById(R.id.tv_gender);
        android.widget.TextView tvAge = view.findViewById(R.id.tv_age);
        android.widget.TextView tvHeight = view.findViewById(R.id.tv_height);
        android.widget.TextView tvWeight = view.findViewById(R.id.tv_weight);

        tvGender.setText(isMale ? "男" : "女");
        tvAge.setText(age + " 岁");
        tvHeight.setText(height + " cm");
        tvWeight.setText(String.format(java.util.Locale.getDefault(), "%.1f kg", weight));

        dialog.show();
    }

    /**
     * Plan 34: 获取活动水平文字描述
     */
    private String getActivityLevelText(float level) {
        if (level <= 1.2f) {
            return "久坐";
        } else if (level <= 1.375f) {
            return "轻度活动";
        } else if (level <= 1.55f) {
            return "中度活动";
        } else if (level <= 1.725f) {
            return "高度活动";
        } else {
            return "专业运动员";
        }
    }

    /**
     * Plan 34: 显示修改年龄对话框
     */
    private void showEditAgeDialog() {
        EditText editText = new EditText(requireContext());
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint("请输入年龄");

        com.cz.fitnessdiary.database.entity.User currentUser = viewModel.getCurrentUser().getValue();
        if (currentUser != null) {
            editText.setText(String.valueOf(currentUser.getAge()));
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("🎂 修改年龄")
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    String ageStr = editText.getText().toString().trim();
                    if (!ageStr.isEmpty()) {
                        int age = Integer.parseInt(ageStr);
                        if (age > 0 && age < 150) {
                            viewModel.updateAge(age);
                        } else {
                            Toast.makeText(requireContext(), "请输入有效的年龄", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * Plan 34: 显示修改性别对话框
     */
    private void showEditGenderDialog() {
        String[] genderOptions = { "👧 女", "👦 男" };

        com.cz.fitnessdiary.database.entity.User currentUser = viewModel.getCurrentUser().getValue();
        int currentGender = (currentUser != null) ? currentUser.getGender() : 0; // 0=女, 1=男

        new AlertDialog.Builder(requireContext())
                .setTitle("修改性别")
                .setSingleChoiceItems(genderOptions, currentGender, (dialog, which) -> {
                    viewModel.updateGender(which);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * Plan 34: 显示修改活动水平对话框
     */
    private void showEditActivityLevelDialog() {
        String[] activityOptions = {
                "🛋️ 久坐 (1.2) - 几乎不运动",
                "🚶 轻度活动 (1.375) - 每周运动1-3次",
                "🏃 中度活动 (1.55) - 每周运动3-5次",
                "💪 高度活动 (1.725) - 每周运动6-7次",
                "🏆 专业运动员 (1.9) - 每天高强度训练"
        };
        float[] activityValues = { 1.2f, 1.375f, 1.55f, 1.725f, 1.9f };

        com.cz.fitnessdiary.database.entity.User currentUser = viewModel.getCurrentUser().getValue();
        float currentLevel = (currentUser != null) ? currentUser.getActivityLevel() : 1.375f;

        int selectedIndex = 1; // 默认轻度活动
        for (int i = 0; i < activityValues.length; i++) {
            if (Math.abs(currentLevel - activityValues[i]) < 0.01f) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("🏋️ 选择活动水平")
                .setSingleChoiceItems(activityOptions, selectedIndex, (dialog, which) -> {
                    viewModel.updateActivityLevel(activityValues[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
