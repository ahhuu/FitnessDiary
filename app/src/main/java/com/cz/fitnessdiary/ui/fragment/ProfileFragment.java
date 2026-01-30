package com.cz.fitnessdiary.ui.fragment;

import android.app.TimePickerDialog;
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
import com.cz.fitnessdiary.ui.MainActivity;
import com.cz.fitnessdiary.ui.adapter.AchievementAdapter;
import com.cz.fitnessdiary.utils.ReminderManager;
import com.cz.fitnessdiary.viewmodel.ProfileViewModel;
import com.cz.fitnessdiary.ui.fragment.AchievementBottomSheetFragment;

/**
 * Profile Fragment - 用户个人信息页面
 * 核心功能：显示用户数据、修改体重身高、设置目标、清除数据、更换头像
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    // SAF Launchers for Backup/Restore
    private ActivityResultLauncher<String> createBackupLauncher;
    private ActivityResultLauncher<String[]> restoreBackupLauncher;

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
                            // [v1.1] 物理存储：将外部图片拷贝到内部私有目录
                            java.io.File localFile = com.cz.fitnessdiary.utils.MediaManager
                                    .saveToInternal(requireContext(), imageUri);
                            if (localFile != null) {
                                // 保存本地路径到数据库
                                String localPath = localFile.getAbsolutePath();
                                viewModel.updateAvatarUri(localPath);

                                // 立即更新头像显示
                                binding.ivAvatar.setImageURI(Uri.fromFile(localFile));
                                Toast.makeText(getContext(), "头像已本地化存储", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "图片本地化失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });

        // 注册备份创建器 (SAF)
        createBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                uri -> {
                    if (uri != null) {
                        try {
                            boolean success = com.cz.fitnessdiary.utils.BackupManager.backupDatabase(requireContext(),
                                    uri);
                            if (success) {
                                Toast.makeText(getContext(), "✅ 备份完成", Toast.LENGTH_SHORT).show();
                            } else {
                                // 尝试获取源文件路径用于诊断
                                String path = requireContext().getDatabasePath("fitness_diary_db").getAbsolutePath();
                                Toast.makeText(getContext(), "❌ 备份失败: 找不到文件或数据为空\n路径: " + path, Toast.LENGTH_LONG)
                                        .show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "❌ 备份过程中发生异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });

        // 注册恢复选择器 (SAF)
        restoreBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        showConfirmRestoreDialog(uri);
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

        setupAchievementEntry(); // v1.2
        setupReportEntry(); // Plan 11
        setupReminderEntry(); // v1.2 New
        observeViewModel();
        setupClickListeners();
    }

    /**
     * [v1.2] 初始化训练提醒入口
     */
    private void setupReminderEntry() {
        // 加载当前状态
        boolean enabled = ReminderManager.isReminderEnabled(requireContext());
        String time = ReminderManager.getFormattedTime(requireContext());

        binding.switchReminder.setChecked(enabled);
        binding.tvReminderTime.setText(time);

        // 开关监听
        binding.switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // [v1.2] 触发精确闹钟权限检查 (Android 12+)
                if (getActivity() instanceof MainActivity) {
                    if (((MainActivity) getActivity()).checkExactAlarmPermission()) {
                        // 如果缺失权限且已弹窗，则将开关重置为关闭，并不再继续
                        binding.switchReminder.setChecked(false);
                        return;
                    }
                }

                // 开启提醒
                ReminderManager.setReminder(requireContext(),
                        ReminderManager.getReminderHour(requireContext()),
                        ReminderManager.getReminderMinute(requireContext()));

                // [v1.2] 展示自启动引导弹窗
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showAutoStartGuidance();
                } else {
                    Toast.makeText(getContext(), "训练提醒已开启", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 取消提醒
                ReminderManager.cancelReminder(requireContext());
                Toast.makeText(getContext(), "训练提醒已关闭", Toast.LENGTH_SHORT).show();
            }
        });

        // 点击卡片选择时间
        binding.cardReminder.setOnClickListener(v -> {
            // [v1.2] 触发精确闹钟权限检查 (Android 12+)
            if (getActivity() instanceof MainActivity) {
                if (((MainActivity) getActivity()).checkExactAlarmPermission()) {
                    // 如果缺失权限，直接拦截，不弹出时间选择器
                    return;
                }
            }

            int currentHour = ReminderManager.getReminderHour(requireContext());
            int currentMinute = ReminderManager.getReminderMinute(requireContext());

            new TimePickerDialog(requireContext(), (view1, hourOfDay, minute) -> {
                // 更新显示
                String formattedTime = String.format("%02d:%02d", hourOfDay, minute);
                binding.tvReminderTime.setText(formattedTime);

                // 如果开关是开启的，则立即重新设定闹钟
                if (binding.switchReminder.isChecked()) {
                    ReminderManager.setReminder(requireContext(), hourOfDay, minute);
                } else {
                    // 仅保存设置，不立即开启
                    // 我们在 setReminder 内部处理了保存，所以这里如果是关闭状态，
                    // 其实也应该保存一下时间设置，以便下次开启时使用正确的时间。
                    // 我们可以直接调用 setReminder 的保存逻辑，或者直接开启开关。
                    // 习惯上点击修改时间后，通常用户希望它是开启的。
                    binding.switchReminder.setChecked(true);
                    ReminderManager.setReminder(requireContext(), hourOfDay, minute);
                }
                Toast.makeText(getContext(), "提醒时间已设为 " + formattedTime, Toast.LENGTH_SHORT).show();

            }, currentHour, currentMinute, true).show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    // Plan 1.2: 成就系统已移至 AchievementBottomSheetFragment
    private void setupAchievementEntry() {
        binding.cardAchievements.setOnClickListener(v -> {
            new AchievementBottomSheetFragment().show(getChildFragmentManager(), "AchievementBottomSheet");
        });
    }

    /**
     * 观察 ViewModel 数据
     */
    private void observeViewModel() {
        // 观察用户信息
        viewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                // 显示用户名（如果为空则显示默认值）
                String nickname = (user.getNickname() == null || user.getNickname().isEmpty()
                        || "新用户".equals(user.getNickname()))
                                ? "新用户" // 保持与跳过逻辑一致
                                : user.getNickname();
                binding.tvUsername.setText(nickname);

                // 优化 0 值显示
                binding.tvWeight.setText(user.getWeight() > 0 ? String.valueOf(user.getWeight()) : "--");
                binding.tvHeight.setText(user.getHeight() > 0 ? String.valueOf((int) user.getHeight()) : "--");
                binding.tvAge.setText(user.getAge() > 0 ? user.getAge() + " 岁" : "--");

                binding.tvGoal.setText(user.getGoal() != null ? user.getGoal() : "点此设置");

                // 加载头像
                if (user.getAvatarUri() != null && !user.getAvatarUri().isEmpty()) {
                    try {
                        String uriString = user.getAvatarUri();
                        Uri avatarUri = uriString.startsWith("/") ? Uri.fromFile(new java.io.File(uriString))
                                : Uri.parse(uriString);
                        binding.ivAvatar.setImageURI(avatarUri);
                    } catch (Exception e) {
                        // 如果 URI 无效，使用默认头像
                        binding.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
                    }
                }

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

        // Plan 1.2: 观察成就数据并更新概览文字
        viewModel.getAchievements().observe(getViewLifecycleOwner(), achievements -> {
            if (achievements != null) {
                int unlockedCount = 0;
                for (com.cz.fitnessdiary.model.Achievement a : achievements) {
                    if (a.isUnlocked())
                        unlockedCount++;
                }
                binding.tvAchievementSummary.setText("已获得 " + unlockedCount + " 枚");
            }
        });

    }

    /**
     * [Plan Data Visualization] 初始化并配置数据看板
     */
    /**
     * [Plan Data Visualization] 初始化数据周报入口
     */
    private void setupReportEntry() {
        binding.cardDataReport.setOnClickListener(v -> {
            new ReportBottomSheetFragment().show(getChildFragmentManager(), "ReportBottomSheet");
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

        // 点击右上角设置按钮 (Plan: Data Management)
        binding.btnSettingsTop.setOnClickListener(v -> showSettingsBottomSheet());

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
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_input_simple, null);
        com.google.android.material.textfield.TextInputEditText input = dialogView.findViewById(R.id.edit_text);
        com.google.android.material.textfield.TextInputLayout layout = dialogView.findViewById(R.id.text_input_layout);

        layout.setHint("用户名");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        // 预填充当前用户名
        viewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null && user.getNickname() != null && input.getText().toString().isEmpty()) {
                input.setText(user.getNickname());
                input.setSelection(input.getText().length());
            }
        });

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("修改用户名")
                .setView(dialogView)
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
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_input_simple, null);
        com.google.android.material.textfield.TextInputEditText input = dialogView.findViewById(R.id.edit_text);
        com.google.android.material.textfield.TextInputLayout layout = dialogView.findViewById(R.id.text_input_layout);

        layout.setHint("体重 (kg)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("修改体重")
                .setView(dialogView)
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
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_input_simple, null);
        com.google.android.material.textfield.TextInputEditText input = dialogView.findViewById(R.id.edit_text);
        com.google.android.material.textfield.TextInputLayout layout = dialogView.findViewById(R.id.text_input_layout);

        layout.setHint("身高 (cm)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("修改身高")
                .setView(dialogView)
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

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("清除所有数据")
                .setMessage("确定要清除所有数据吗？此操作不可恢复！")
                .setPositiveButton("确定清除", (dialog, which) -> {
                    viewModel.clearAllData();
                    Toast.makeText(getContext(), "数据已清除，正在注销...", Toast.LENGTH_SHORT).show();

                    // [物理重启逻辑] 清除数据后强制物理重启，返回欢迎页
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        android.content.Context context = requireContext();
                        android.content.Intent intent = context.getPackageManager()
                                .getLaunchIntentForPackage(context.getPackageName());
                        if (intent != null) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        }
                        Runtime.getRuntime().exit(0);
                    }, 1000);
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
     * 显示设置中心底部弹窗
     */
    private void showSettingsBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(
                requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_settings, null);
        bottomSheetDialog.setContentView(view);

        // 1. 备份数据
        view.findViewById(R.id.btn_backup).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            String fileName = "FitnessDiary_Backup_"
                    + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
                            .format(new java.util.Date())
                    + ".db";
            createBackupLauncher.launch(fileName);
        });

        // 2. 恢复数据
        view.findViewById(R.id.btn_restore).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            restoreBackupLauncher.launch(new String[] { "application/octet-stream", "application/x-sqlite3", "*/*" });
        });

        // 3. 清除数据
        view.findViewById(R.id.btn_clear_all).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showClearDataDialog();
        });

        bottomSheetDialog.show();
    }

    /**
     * 恢复前进行二次确认
     */
    private void showConfirmRestoreDialog(Uri uri) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ 确认恢复数据？")
                .setMessage("恢复操作将覆盖当前所有的本地数据，且无法撤销！建议在恢复前先执行一次备份。")
                .setPositiveButton("确认恢复", (dialog, which) -> {
                    boolean success = com.cz.fitnessdiary.utils.BackupManager.restoreDatabase(requireContext(), uri);
                    if (success) {
                        Toast.makeText(getContext(), "🎉 恢复成功！应用即将重启", Toast.LENGTH_LONG).show();
                        // [物理重启逻辑] 强制物理重启应用以确保数据库单例刷新
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            android.content.Context context = requireContext();
                            android.content.Intent intent = context.getPackageManager()
                                    .getLaunchIntentForPackage(context.getPackageName());
                            if (intent != null) {
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            }
                            Runtime.getRuntime().exit(0);
                        }, 1000);
                    } else {
                        Toast.makeText(getContext(), "❌ 恢复失败，请检查文件是否有效", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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
        int gender = user.getGender();
        float activityLevel = user.getActivityLevel();
        if (activityLevel <= 0)
            activityLevel = 1.2f;

        // [核心修复] 使用统一工具类计算 BMR 和 TDEE
        int bmrValue = com.cz.fitnessdiary.utils.CalorieCalculatorUtils.calculateBMR(gender, (float) weight,
                (float) height, age);
        int tdeeValue = com.cz.fitnessdiary.utils.CalorieCalculatorUtils.calculateTDEE(bmrValue, activityLevel);

        // 设置BMR值
        android.widget.TextView tvBmrValue = view.findViewById(R.id.tv_bmr_value);
        tvBmrValue.setText(String.valueOf(bmrValue));

        // [核心修复] 使用统一工具类计算建议值，确保与饮食页目标一致
        int deficitCalories = com.cz.fitnessdiary.utils.CalorieCalculatorUtils.calculateTargetCalories(tdeeValue,
                com.cz.fitnessdiary.utils.CalorieCalculatorUtils.GOAL_LOSE_FAT);
        int maintainCalories = com.cz.fitnessdiary.utils.CalorieCalculatorUtils.calculateTargetCalories(tdeeValue,
                com.cz.fitnessdiary.utils.CalorieCalculatorUtils.GOAL_MAINTAIN);
        int surplusCalories = com.cz.fitnessdiary.utils.CalorieCalculatorUtils.calculateTargetCalories(tdeeValue,
                com.cz.fitnessdiary.utils.CalorieCalculatorUtils.GOAL_GAIN_MUSCLE);

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

        tvGender.setText(gender == com.cz.fitnessdiary.utils.CalorieCalculatorUtils.GENDER_MALE ? "男" : "女");
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
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_input_simple, null);
        com.google.android.material.textfield.TextInputEditText input = dialogView.findViewById(R.id.edit_text);
        com.google.android.material.textfield.TextInputLayout layout = dialogView.findViewById(R.id.text_input_layout);

        layout.setHint("年龄");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        viewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null && input.getText().toString().isEmpty()) {
                input.setText(String.valueOf(user.getAge()));
            }
        });

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("🎂 修改年龄")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String ageStr = input.getText().toString().trim();
                    if (!ageStr.isEmpty()) {
                        try {
                            int age = Integer.parseInt(ageStr);
                            if (age > 0 && age < 150) {
                                viewModel.updateAge(age);
                            } else {
                                Toast.makeText(requireContext(), "请输入有效的年龄", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(requireContext(), "输入格式错误", Toast.LENGTH_SHORT).show();
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

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择性别")
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

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("🏋️ 选择活动水平")
                .setSingleChoiceItems(activityOptions, selectedIndex, (dialog, which) -> {
                    viewModel.updateActivityLevel(activityValues[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
