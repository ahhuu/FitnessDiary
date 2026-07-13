package com.cz.fitnessdiary.ui.fragment;

import com.cz.fitnessdiary.BuildConfig;

import android.app.AlarmManager;
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
import android.widget.TextView;
import android.widget.LinearLayout;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.content.Context;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.ReminderScheduleDao;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.database.entity.MoodRecord;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.databinding.FragmentProfileBinding;
import com.cz.fitnessdiary.model.AccountUser;
import com.cz.fitnessdiary.repository.AccountRepository;
import com.cz.fitnessdiary.config.CloudApiConfig;
import com.cz.fitnessdiary.ui.MainActivity;
import com.cz.fitnessdiary.ui.guide.GuideStateManager;
import com.cz.fitnessdiary.service.DailyBriefingService;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.utils.ExerciseMetTable;
import com.cz.fitnessdiary.utils.ReminderManager;
import com.cz.fitnessdiary.utils.ShareUtils;
import com.cz.fitnessdiary.utils.UnitUtils;
import com.cz.fitnessdiary.viewmodel.AchievementCenterViewModel;
import com.cz.fitnessdiary.viewmodel.ProfileViewModel;
import com.cz.fitnessdiary.database.ReminderPresetDataLoader;
import com.cz.fitnessdiary.ui.adapter.ReminderScheduleAdapter;

import com.cz.fitnessdiary.ui.fragment.AchievementBottomSheetFragment;
import com.cz.fitnessdiary.utils.CalorieCalculatorUtils;
import com.cz.fitnessdiary.utils.HealthScoreCalculator;
import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Profile Fragment - 用户个人信息页面
 * 核心功能：显示用户数据、修改体重身高、设置目标、清除数据、更换头像
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private AchievementCenterViewModel achievementCenterViewModel;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> cropImageLauncher;

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
                            // 先将图片拷贝到 cache 目录，规避跨进程读取临时 content:// URI 时分区存储的权限失效问题
                            java.io.File cacheRawFile = new java.io.File(requireContext().getCacheDir(), "temp_raw_avatar.jpg");
                            boolean copySuccess = copyUriToFile(imageUri, cacheRawFile);
                            if (copySuccess) {
                                // 调起裁剪
                                startCrop(cacheRawFile);
                            } else {
                                // 拷贝失败则使用原图直接保存
                                useOriginalImage(cacheRawFile);
                            }
                        }
                    }
                });

        // 注册裁剪器
        cropImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                        java.io.File cropFile = new java.io.File(requireContext().getCacheDir(), "crop_avatar_temp.jpg");
                        if (cropFile.exists() && cropFile.length() > 0) {
                            java.io.File localFile = com.cz.fitnessdiary.utils.MediaManager
                                    .saveToInternal(requireContext(), Uri.fromFile(cropFile));
                            if (localFile != null) {
                                String localPath = localFile.getAbsolutePath();
                                viewModel.updateAvatarUri(localPath);
                                binding.ivAvatar.setImageURI(Uri.fromFile(localFile));
                                Toast.makeText(getContext(), "头像已成功剪裁并保存", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "裁剪数据无效，请重试", Toast.LENGTH_SHORT).show();
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
        achievementCenterViewModel = new ViewModelProvider(requireActivity()).get(AchievementCenterViewModel.class);

        setupAchievementEntry(); // v1.2
        setupReportEntry(); // Plan 11

        binding.tvVersion.setText("v" + BuildConfig.VERSION_NAME);

        observeViewModel();
        setupClickListeners();

        binding.tvUnitSummary.setText(UnitUtils.getWeightUnitSymbol(requireContext()) + " / " + UnitUtils.getEnergyUnitSymbol(requireContext()));
    }

    /**
     * 初始化智能推送可展开面板（替代已废弃的每日训练提醒）
     */
    

    private boolean mSmartExpanded = false;

    

    /** 切换智能推送子项面板展开/收起 */
    

    /** 刷新所有子项的时间摘要文字 */
    

    /** TimePicker：早晨概要 */
    

    /** TimePicker：晚间提醒 */
    

    /** TimePicker：不活跃挽留 */
    

    /** 两步弹窗：选择星期 → 选择时间（健康周报） */
    


    /** Calendar.DAY_OF_WEEK 常量 → 中文星期名 */
    


    @Override
    public void onResume() {
        super.onResume();
        updateCloudAccountSummary();
        if (achievementCenterViewModel != null) {
            achievementCenterViewModel.refreshAll();
        }
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
                // 显示用户名
                String nickname = (user.getNickname() == null || user.getNickname().isEmpty()
                        || "新用户".equals(user.getNickname()))
                                ? "新用户"
                                : user.getNickname();
                binding.tvUsername.setText(nickname);

                // 显示性别和年龄 (根据生日自动刷新)
                String genderText = user.getGender() == 1 ? "男" : "女";
                int age = user.getAge();
                android.content.SharedPreferences spBirth = requireContext().getSharedPreferences("user_profile_birth", Context.MODE_PRIVATE);
                String birth = spBirth.getString("birth_date", "");
                if (!birth.isEmpty()) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                        java.util.Date birthDate = sdf.parse(birth);
                        java.util.Calendar birthCal = java.util.Calendar.getInstance();
                        birthCal.setTime(birthDate);
                        java.util.Calendar todayCal = java.util.Calendar.getInstance();
                        int calculatedAge = todayCal.get(java.util.Calendar.YEAR) - birthCal.get(java.util.Calendar.YEAR);
                        if (todayCal.get(java.util.Calendar.DAY_OF_YEAR) < birthCal.get(java.util.Calendar.DAY_OF_YEAR)) {
                            calculatedAge--;
                        }
                        if (calculatedAge > 0 && calculatedAge != user.getAge()) {
                            age = calculatedAge;
                            user.setAge(calculatedAge);
                            new Thread(() -> AppDatabase.getInstance(requireContext()).userDao().update(user)).start();
                        }
                    } catch (Exception ignored) {}
                }
                binding.tvUserGenderAge.setText("🏷️ " + genderText + " · " + age + "岁");

                // 刷新合并身体数据看板流式小字
                refreshBodyDataSummaryText(user);

                binding.tvGoal.setText(user.getGoal() != null ? "目标：" + user.getGoal() : "点此设置");

                // 加载头像
                if (user.getAvatarUri() != null && !user.getAvatarUri().isEmpty()) {
                    try {
                        String uriString = user.getAvatarUri();
                        Uri avatarUri = uriString.startsWith("/") ? Uri.fromFile(new java.io.File(uriString))
                                : Uri.parse(uriString);
                        binding.ivAvatar.setImageURI(avatarUri);
                    } catch (Exception e) {
                        binding.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
                    }
                }

                // 显示活动水平
                float activityLevel = user.getActivityLevel();
                String activityText = getActivityLevelText(activityLevel);
                binding.tvActivityLevel.setText(activityText + " (" + activityLevel + ")");
            }
        });

        achievementCenterViewModel.getAchievements().observe(getViewLifecycleOwner(), achievements -> {
            int unlockedCount = 0;
            if (achievements != null) {
                for (com.cz.fitnessdiary.model.Achievement a : achievements) {
                    if (a.isUnlocked()) {
                        unlockedCount++;
                    }
                }
            }
            Integer unreadCount = achievementCenterViewModel.getUnreadUnlockCount().getValue();
            if (unreadCount != null && unreadCount > 0) {
                binding.tvAchievementSummary.setText("新解锁 " + unreadCount + " 枚");
                binding.tvAchievementSummary.setTextColor(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.ai_primary));
            } else {
                binding.tvAchievementSummary.setText("已获得 " + unlockedCount + " 枚");
                binding.tvAchievementSummary.setTextColor(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }
        });

        achievementCenterViewModel.getUnreadUnlockCount().observe(getViewLifecycleOwner(), unreadCount -> {
            if (unreadCount != null && unreadCount > 0) {
                binding.tvAchievementSummary.setText("新解锁 " + unreadCount + " 枚");
                binding.tvAchievementSummary.setTextColor(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.ai_primary));
            }
        });

        // 动态更新用户等级标签
        viewModel.getUserLevel().observe(getViewLifecycleOwner(), level -> {
            if (level != null && !level.isEmpty()) {
                binding.tvUserLevel.setText(level);
            }
        });
    }

    private void refreshBodyDataSummaryText(User user) {
        if (user == null || binding == null) return;
        float w = user.getWeight();
        float h = user.getHeight();
        float hMeter = h / 100.0f;
        float bmi = (hMeter > 0) ? w / (hMeter * hMeter) : 0;

        // 使用 Mifflin-St Jeor 公式（与 CalorieCalculatorUtils 统一）
        double bmr = com.cz.fitnessdiary.utils.CalorieCalculatorUtils.calculateBMR(
                user.getGender(), w, h, user.getAge());

        String summary = "身高 " + String.format(Locale.getDefault(), "%.0f", h) + "cm · 体重 "
                + UnitUtils.formatWeight(w, requireContext()) + UnitUtils.getWeightUnitSymbol(requireContext())
                + " · BMI " + String.format(Locale.getDefault(), "%.1f", bmi)
                + " · 基础代谢 " + UnitUtils.formatEnergy((float) bmr, requireContext()) + UnitUtils.getEnergyUnitSymbol(requireContext());
        binding.tvBodyDataSummary.setText(summary);
    }

    private void showUnitAndDisplaySettingsDialog() {
        String currentThemeText = "跟随系统";
        int currentTheme = UnitUtils.getThemeMode(requireContext());
        if (currentTheme == UnitUtils.THEME_LIGHT) {
            currentThemeText = "浅色模式";
        } else if (currentTheme == UnitUtils.THEME_DARK) {
            currentThemeText = "深色模式";
        }

        String[] settings = {"重量单位 (当前: " + UnitUtils.getWeightUnitDisplay(UnitUtils.getWeightUnit(requireContext())) + ")",
                "热量单位 (当前: " + UnitUtils.getEnergyUnitDisplay(UnitUtils.getEnergyUnit(requireContext())) + ")",
                "外观显示设置 (当前: " + currentThemeText + ")"};

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("🌓 显示与单位设置")
                .setItems(settings, (dialog, which) -> {
                    if (which == 0) {
                        String[] weights = {"千克 (kg)", "斤"};
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("选择重量单位")
                                .setItems(weights, (d, w) -> {
                                    String unit = (w == 0) ? "kg" : "jin";
                                    UnitUtils.setWeightUnit(requireContext(), unit);
                                    binding.tvUnitSummary.setText(UnitUtils.getWeightUnitSymbol(requireContext()) + " / " + UnitUtils.getEnergyUnitSymbol(requireContext()));
                                    Toast.makeText(getContext(), "重量单位已切换为 " + UnitUtils.getWeightUnitDisplay(unit), Toast.LENGTH_SHORT).show();
                                })
                                .show();
                    } else if (which == 1) {
                        String[] calories = {"kcal (千卡)", "kj (千焦)"};
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("选择热量单位")
                                .setItems(calories, (d, c) -> {
                                    String unit = (c == 0) ? "kcal" : "kj";
                                    UnitUtils.setEnergyUnit(requireContext(), unit);
                                    binding.tvUnitSummary.setText(UnitUtils.getWeightUnitSymbol(requireContext()) + " / " + UnitUtils.getEnergyUnitSymbol(requireContext()));
                                    Toast.makeText(getContext(), "热量单位已切换为 " + UnitUtils.getEnergyUnitDisplay(unit), Toast.LENGTH_SHORT).show();
                                })
                                .show();
                    } else {
                        String[] themes = {"浅色模式", "深色模式", "跟随系统"};
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("外观显示设置")
                                .setSingleChoiceItems(themes, currentTheme, (d, t) -> {
                                    UnitUtils.setThemeMode(requireContext(), t);
                                    int mode;
                                    if (t == 0) {
                                        mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
                                    } else if (t == 1) {
                                        mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
                                    } else {
                                        mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                                    }
                                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode);
                                    Toast.makeText(getContext(), "外观主题已设为：" + themes[t], Toast.LENGTH_SHORT).show();
                                    d.dismiss();
                                    // 立即重建Activity使主题生效
                                    requireActivity().recreate();
                                })
                                .show();
                    }
                })
                .show();
    }

    private boolean checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("🔔 需要通知权限")
                        .setMessage("提醒功能需要发送通知，请在系统设置中开启通知权限")
                        .setPositiveButton("去设置", (d, w) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return false;
            }
        }
        return true;
    }

    private boolean checkExactAlarmPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("⏰ 需要闹钟权限")
                        .setMessage("提醒功能需要精确闹钟权限才能准时提醒，请在系统设置中开启")
                        .setPositiveButton("去设置", (d, w) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return false;
            }
        }
        return true;
    }

    private void showNotificationSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_notification_settings, null);

        // Force-load presets before showing
        ReminderPresetDataLoader.loadIfNeeded(requireContext());

        androidx.recyclerview.widget.RecyclerView rvPreset = dialogView.findViewById(R.id.rv_preset_reminders);
        androidx.recyclerview.widget.RecyclerView rvCustom = dialogView.findViewById(R.id.rv_custom_reminders);

        rvPreset.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        rvCustom.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));

        ReminderScheduleDao dao = AppDatabase.getInstance(requireContext().getApplicationContext()).reminderScheduleDao();

        ReminderScheduleAdapter.OnReminderActionListener listener = new ReminderScheduleAdapter.OnReminderActionListener() {
            @Override
            public void onToggle(ReminderSchedule schedule, boolean enabled) {
                new Thread(() -> {
                    schedule.setEnabled(enabled);
                    dao.update(schedule);
                    if (enabled) {
                        ReminderManager.scheduleReminder(requireContext(), schedule, true);
                    } else {
                        ReminderManager.cancelReminder(requireContext(), schedule);
                    }
                }).start();
            }

            @Override
            public void onTimeClick(ReminderSchedule schedule) {
                ReminderScheduleAdapter.OnReminderActionListener self = this;
                if ("weekly_report".equals(schedule.getModuleType())) {
                    String[] days = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
                    int dayIndex = 1;
                    try {
                        String repeatDaysStr = schedule.getRepeatDays();
                        if (repeatDaysStr != null && !repeatDaysStr.isEmpty()) {
                            dayIndex = Integer.parseInt(repeatDaysStr.split(",")[0].trim());
                        }
                    } catch (Exception ignored) {}
                    if (dayIndex < 0 || dayIndex > 6) dayIndex = 1;

                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("选择健康周报发送日")
                            .setSingleChoiceItems(days, dayIndex, (d, which) -> {
                                d.dismiss();
                                TimePickerDialog timePicker = new TimePickerDialog(getContext(), (view, hour, minute) -> {
                                    schedule.setHour(hour);
                                    schedule.setMinute(minute);
                                    schedule.setRepeatDays(String.valueOf(which));
                                    new Thread(() -> {
                                        dao.update(schedule);
                                        ReminderManager.cancelReminder(requireContext(), schedule);
                                        if (schedule.isEnabled()) {
                                            ReminderManager.scheduleReminder(requireContext(), schedule, true);
                                        }
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> refreshReminderLists(dao, rvPreset, rvCustom, self));
                                        }
                                    }).start();
                                }, schedule.getHour(), schedule.getMinute(), true);
                                timePicker.show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                } else {
                    TimePickerDialog timePicker = new TimePickerDialog(getContext(), (view, hour, minute) -> {
                        schedule.setHour(hour);
                        schedule.setMinute(minute);
                        new Thread(() -> {
                            dao.update(schedule);
                            ReminderManager.cancelReminder(requireContext(), schedule);
                            if (schedule.isEnabled()) {
                                ReminderManager.scheduleReminder(requireContext(), schedule, true);
                            }
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> refreshReminderLists(dao, rvPreset, rvCustom, self));
                            }
                        }).start();
                    }, schedule.getHour(), schedule.getMinute(), true);
                    timePicker.show();
                }
            }

            @Override
            public void onEdit(ReminderSchedule schedule) {
                showEditReminderDialog(schedule, dao, () -> refreshReminderLists(dao, rvPreset, rvCustom, this));
            }

            @Override
            public void onDelete(ReminderSchedule schedule) {
                ReminderScheduleAdapter.OnReminderActionListener self = this;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除提醒")
                        .setMessage("确定要删除「" + schedule.getTitle() + "」吗？")
                        .setPositiveButton("删除", (d, w) -> {
                            new Thread(() -> {
                                ReminderManager.cancelReminder(requireContext(), schedule);
                                dao.deleteById(schedule.getId());
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> refreshReminderLists(dao, rvPreset, rvCustom, self));
                                }
                            }).start();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        };

        showNotificationSettingsDialogSmart(dialogView, dao, rvPreset, rvCustom, listener);
    }

    private void showNotificationSettingsDialogSmart(View dialogView, ReminderScheduleDao dao,
                                                     androidx.recyclerview.widget.RecyclerView rvPreset,
                                                     androidx.recyclerview.widget.RecyclerView rvCustom,
                                                     ReminderScheduleAdapter.OnReminderActionListener listener) {
        ReminderScheduleAdapter presetAdapter = new ReminderScheduleAdapter(listener);
        ReminderScheduleAdapter customAdapter = new ReminderScheduleAdapter(listener);
        rvPreset.setAdapter(presetAdapter);
        rvCustom.setAdapter(customAdapter);

        refreshReminderLists(dao, rvPreset, rvCustom, listener);

        com.google.android.material.button.MaterialButton btnAddCustom = dialogView.findViewById(R.id.btn_add_custom_reminder);
        if (btnAddCustom != null) {
            btnAddCustom.setOnClickListener(v -> {
                ReminderSchedule newSchedule = new ReminderSchedule(
                        "custom", 0, 8, 0, "0,1,2,3,4,5,6",
                        true, "新提醒", "", false, 99);
                showEditReminderDialog(newSchedule, dao, () -> refreshReminderLists(dao, rvPreset, rvCustom, listener));
            });
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("关闭", (d, w) -> {
                    ReminderManager.restoreAllReminders(requireContext());
                })
                .show();
    }

    private void refreshReminderLists(ReminderScheduleDao dao,
                                       androidx.recyclerview.widget.RecyclerView rvPreset,
                                       androidx.recyclerview.widget.RecyclerView rvCustom,
                                       ReminderScheduleAdapter.OnReminderActionListener listener) {
        new Thread(() -> {
            List<ReminderSchedule> presets = dao.getByPreset(true);
            List<ReminderSchedule> customs = dao.getByPreset(false);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    ((ReminderScheduleAdapter) rvPreset.getAdapter()).setSchedules(presets);
                    ((ReminderScheduleAdapter) rvCustom.getAdapter()).setSchedules(customs);
                });
            }
        }).start();
    }

    private void showEditReminderDialog(ReminderSchedule schedule, ReminderScheduleDao dao, Runnable onDone) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_edit_reminder, null, false);
        com.google.android.material.textfield.TextInputEditText etTitle = dialogView.findViewById(R.id.et_reminder_title);
        com.google.android.material.textfield.TextInputEditText etContent = dialogView.findViewById(R.id.et_reminder_content);
        TextView tvTime = dialogView.findViewById(R.id.tv_selected_time);
        android.widget.LinearLayout btnTimePicker = dialogView.findViewById(R.id.btn_time_picker);
        com.google.android.material.button.MaterialButton btnSave = dialogView.findViewById(R.id.btn_save_reminder);

        etTitle.setText(schedule.getTitle() != null ? schedule.getTitle() : "");
        etContent.setText(schedule.getContent() != null ? schedule.getContent() : "");
        tvTime.setText(String.format("%02d:%02d", schedule.getHour(), schedule.getMinute()));

        final int[] selectedHour = {schedule.getHour()};
        final int[] selectedMinute = {schedule.getMinute()};

        btnTimePicker.setOnClickListener(v -> {
            TimePickerDialog tpd = new TimePickerDialog(getContext(), (view, h, m) -> {
                selectedHour[0] = h;
                selectedMinute[0] = m;
                tvTime.setText(String.format("%02d:%02d", h, m));
            }, selectedHour[0], selectedMinute[0], true);
            tpd.show();
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    schedule.setTitle(etTitle.getText() != null ? etTitle.getText().toString() : "新提醒");
                    schedule.setContent(etContent.getText() != null ? etContent.getText().toString() : "");
                    schedule.setHour(selectedHour[0]);
                    schedule.setMinute(selectedMinute[0]);
                    schedule.setRepeatDays("0,1,2,3,4,5,6");

                    new Thread(() -> {
                        if (schedule.getId() == 0) {
                            long id = dao.insert(schedule);
                            schedule.setId(id);
                        } else {
                            dao.update(schedule);
                        }
                        ReminderManager.cancelReminder(requireContext(), schedule);
                        if (schedule.isEnabled()) {
                            ReminderManager.scheduleReminder(requireContext(), schedule, true);
                        }
                        if (onDone != null && getActivity() != null) {
                            getActivity().runOnUiThread(() -> onDone.run());
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditProfileHeaderDialog() {
        User user = viewModel.getCurrentUser().getValue();
        if (user == null) return;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 36, 48, 24);

        EditText etName = new EditText(requireContext());
        etName.setHint("昵称");
        etName.setText(user.getNickname());
        TextView tvN = new TextView(requireContext());
        tvN.setText("昵称");
        tvN.setTextSize(12);
        tvN.setTextColor(getResources().getColor(R.color.text_secondary, null));
        layout.addView(tvN);
        layout.addView(etName);

        TextView tvG = new TextView(requireContext());
        tvG.setText("性别");
        tvG.setTextSize(12);
        tvG.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvG.setPadding(0, 24, 0, 0);
        AutoCompleteTextView spinGender = new AutoCompleteTextView(requireContext());
        spinGender.setHint("选择性别");
        spinGender.setText(user.getGender() == 1 ? "男" : "女", false);
        spinGender.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, new String[]{"女", "男"}));
        layout.addView(tvG);
        layout.addView(spinGender);

        TextView tvB = new TextView(requireContext());
        tvB.setText("出生日期 (用于自动计算年龄)");
        tvB.setTextSize(12);
        tvB.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvB.setPadding(0, 24, 0, 0);
        EditText etBirth = new EditText(requireContext());
        etBirth.setFocusable(false);
        etBirth.setClickable(true);
        SharedPreferences sp = requireContext().getSharedPreferences("user_profile_birth", Context.MODE_PRIVATE);
        String currentBirth = sp.getString("birth_date", "2000-01-01");
        etBirth.setText(currentBirth);
        
        etBirth.setOnClickListener(v -> {
            String[] ymd = etBirth.getText().toString().split("-");
            int year = 2000, month = 0, day = 1;
            if (ymd.length == 3) {
                try {
                    year = Integer.parseInt(ymd[0]);
                    month = Integer.parseInt(ymd[1]) - 1;
                    day = Integer.parseInt(ymd[2]);
                } catch (Exception ignored) {}
            }
            new android.app.DatePickerDialog(requireContext(), (view, y, m, d) -> {
                String birthStr = String.format(Locale.getDefault(), "%d-%02d-%02d", y, m + 1, d);
                etBirth.setText(birthStr);
            }, year, month, day).show();
        });
        layout.addView(tvB);
        layout.addView(etBirth);

        com.google.android.material.button.MaterialButton btnAvatar = new com.google.android.material.button.MaterialButton(requireContext());
        btnAvatar.setText("📷 点击更换头像");
        btnAvatar.setPadding(0, 24, 0, 0);
        btnAvatar.setOnClickListener(v -> {
            openImagePicker();
        });
        layout.addView(btnAvatar);

        // ── 等级积分状态卡片 ──
        View spacer = new View(requireContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 20));
        layout.addView(spacer);

        com.google.android.material.card.MaterialCardView cardStats =
                new com.google.android.material.card.MaterialCardView(requireContext());
        cardStats.setCardBackgroundColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.fitnessdiary_surface));
        cardStats.setCardElevation(0);
        float density = getResources().getDisplayMetrics().density;
        cardStats.setRadius((int) (12 * density));
        cardStats.setContentPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardStats.setLayoutParams(cardParams);

        LinearLayout statsLayout = new LinearLayout(requireContext());
        statsLayout.setOrientation(LinearLayout.VERTICAL);

        TextView tvStatsTitle = new TextView(requireContext());
        tvStatsTitle.setText("📊 当前状态");
        tvStatsTitle.setTextSize(13);
        tvStatsTitle.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvStatsTitle.setPadding(0, 0, 0, 8);
        statsLayout.addView(tvStatsTitle);

        TextView tvStatsDetail = new TextView(requireContext());
        tvStatsDetail.setText("加载中...");
        tvStatsDetail.setTextSize(14);
        tvStatsDetail.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvStatsDetail.setLineSpacing(4, 1);
        statsLayout.addView(tvStatsDetail);

        cardStats.addView(statsLayout);
        layout.addView(cardStats);

        // 后台加载等级数据
        new Thread(() -> {
            com.cz.fitnessdiary.database.AppDatabase db =
                    com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
            int trainingDays = db.dailyLogDao().getTotalTrainingDays();
            int dietDays = db.foodRecordDao().getTotalDietDaysSync();
            String level = viewModel.getUserLevel().getValue();
            if (level == null || level.isEmpty()) level = "Lv.0 初来乍到 🌱";
            final String fLevel = level;
            final int fTrain = trainingDays;
            final int fDiet = dietDays;

            // 反推当前积分
            double trainingScore = trainingDays * 1.5;
            double dietScore = dietDays * 1.0;
            // 成就数从 LiveData 取
            java.util.List<com.cz.fitnessdiary.model.Achievement> achs = viewModel.getAchievements().getValue();
            int unlocked = 0;
            if (achs != null) {
                for (com.cz.fitnessdiary.model.Achievement a : achs) {
                    if (a.isUnlocked()) unlocked++;
                }
            }
            double baseScore = trainingScore + dietScore + unlocked * 2.0;
            double activityRatio = trainingDays > 0
                    ? Math.min((double) dietDays / trainingDays, 1.0)
                    : (dietDays > 0 ? 1.0 : 0.0);
            int finalScore = (int) Math.round(baseScore * (1.0 + activityRatio * 0.3));
            final int fScore = finalScore;
            final int fUnlocked = unlocked;

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    tvStatsDetail.setText(String.format(java.util.Locale.getDefault(),
                            "训练 %d 天 · 饮食 %d 天 · %d 成就\n当前积分 %d · %s",
                            fTrain, fDiet, fUnlocked, fScore, fLevel));
                });
            }
        }).start();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("✏️ 编辑个人资料")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String genderStr = spinGender.getText().toString().trim();
                    String birthStr = etBirth.getText().toString().trim();

                    if (!name.isEmpty()) {
                        user.setNickname(name);
                    }
                    user.setGender("男".equals(genderStr) ? 1 : 0);

                    int age = user.getAge();
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        java.util.Date birthDate = sdf.parse(birthStr);
                        java.util.Calendar birthCal = java.util.Calendar.getInstance();
                        birthCal.setTime(birthDate);
                        java.util.Calendar todayCal = java.util.Calendar.getInstance();
                        int calculatedAge = todayCal.get(java.util.Calendar.YEAR) - birthCal.get(java.util.Calendar.YEAR);
                        if (todayCal.get(java.util.Calendar.DAY_OF_YEAR) < birthCal.get(java.util.Calendar.DAY_OF_YEAR)) {
                            calculatedAge--;
                        }
                        if (calculatedAge > 0) {
                            age = calculatedAge;
                        }
                    } catch (Exception ignored) {}

                    user.setAge(age);
                    sp.edit().putString("birth_date", birthStr).apply();

                    new Thread(() -> {
                        AppDatabase.getInstance(requireContext()).userDao().update(user);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                viewModel.refreshUser();
                                Toast.makeText(getContext(), "个人资料保存成功", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * [Plan Data Visualization] 初始化并配置数据看板
     */
    /**
     * [Plan Data Visualization] 初始化数据周报入口
     */
    private void setupReportEntry() {
        binding.cardDataReport.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("数据周报")
                    .setItems(new String[] { "查看分析", "分享周报图片" }, (dialog, which) -> {
                        if (which == 0) {
                            new ReportBottomSheetFragment().show(getChildFragmentManager(), "ReportBottomSheet");
                        } else {
                            generateAndShareWeekReport();
                        }
                    })
                    .show();
        });
    }



    private void generateAndShareWeekReport() {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                long[] weekDates = DateUtils.getThisWeekDates();
                long weekStart = weekDates[0];
                long weekEnd = weekDates[6] + 24 * 3600 * 1000L;

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault());
                String weekRange = sdf.format(new java.util.Date(weekDates[0]))
                        + " - " + sdf.format(new java.util.Date(weekDates[6]));

                // Exercise days
                java.util.List<DailyLog> allLogs = db.dailyLogDao().getAllLogsSync();
                java.util.List<com.cz.fitnessdiary.database.entity.TrainingPlan> allPlans = db.trainingPlanDao().getAllPlansList();
                java.util.Map<Integer, com.cz.fitnessdiary.database.entity.TrainingPlan> planMap = new java.util.HashMap<>();
                if (allPlans != null) {
                    for (com.cz.fitnessdiary.database.entity.TrainingPlan p : allPlans) {
                        planMap.put(p.getPlanId(), p);
                    }
                }
                int exerciseDays = 0;
                int totalDurationSec = 0;
                if (allLogs != null) {
                    java.util.Set<String> dates = new java.util.HashSet<>();
                    for (DailyLog log : allLogs) {
                        if (log.getDate() >= weekStart && log.getDate() < weekEnd && log.isCompleted()) {
                            dates.add(DateUtils.formatDate(log.getDate()));
                            com.cz.fitnessdiary.database.entity.TrainingPlan plan = planMap.get(log.getPlanId());
                            totalDurationSec += com.cz.fitnessdiary.utils.ExerciseMetTable.resolveDuration(
                                    log.getDuration(),
                                    plan != null ? plan.getDuration() : 0,
                                    plan != null ? plan.getSets() : 0,
                                    plan != null ? plan.getReps() : 0,
                                    requireContext());
                        }
                    }
                    exerciseDays = dates.size();
                }

                // Diet
                java.util.List<FoodRecord> allFoods = db.foodRecordDao().getAllFoodRecordsSync();
                int totalCal = 0;
                int foodDays = 0;
                if (allFoods != null) {
                    java.util.Set<String> fDates = new java.util.HashSet<>();
                    for (FoodRecord f : allFoods) {
                        if (f.getRecordDate() >= weekStart && f.getRecordDate() < weekEnd) {
                            totalCal += f.getCalories();
                            fDates.add(DateUtils.formatDate(f.getRecordDate()));
                        }
                    }
                    foodDays = fDates.size();
                }
                int avgCal = foodDays > 0 ? totalCal / foodDays : 0;

                // User target
                User user = db.userDao().getUserSync();
                int target = (user != null && user.getDailyCalorieTarget() > 0) ? user.getDailyCalorieTarget() : 2000;

                // Weight
                java.util.List<WeightRecord> weights = db.weightRecordDao().getRecordsByDateRangeSync(weekStart, weekEnd);
                float wStart = 0, wEnd = 0;
                if (weights != null && !weights.isEmpty()) {
                    wStart = (float) weights.get(0).getWeight();
                    wEnd = (float) weights.get(weights.size() - 1).getWeight();
                }

                // Water avg
                java.util.List<WaterRecord> waters = db.waterRecordDao().getRecordsByDateRangeSync(weekStart, weekEnd);
                int waterTotal = 0;
                int wDays = 0;
                if (waters != null && !waters.isEmpty()) {
                    java.util.Set<String> wDateSet = new java.util.HashSet<>();
                    for (WaterRecord w : waters) {
                        waterTotal += w.getAmountMl();
                        wDateSet.add(DateUtils.formatDate(w.getTimestamp()));
                    }
                    wDays = wDateSet.size();
                }
                int waterAvg = wDays > 0 ? waterTotal / wDays : 0;

                // Habits
                java.util.List<HabitItem> allHabits = db.habitItemDao().getAllItemsSync();
                int habitTotal = 0;
                if (allHabits != null) {
                    for (HabitItem h : allHabits) {
                        if (h.isEnabled()) habitTotal++;
                    }
                }
                int habitDone = 0;
                int habitRate = 0;
                if (habitTotal > 0) {
                    for (long d = weekStart; d < weekEnd; d += 24 * 3600 * 1000L) {
                        java.util.List<HabitRecord> dayRecords = db.habitRecordDao().getRecordsByDateSync(d);
                        if (dayRecords != null) {
                            for (HabitRecord hr : dayRecords) {
                                if (hr.isCompleted()) habitDone++;
                            }
                        }
                    }
                    habitRate = habitDone * 100 / (habitTotal * 7);
                }

                // Steps avg & Target
                int totalSteps = 0;
                int stepDays = 0;
                for (long d = weekStart; d < weekEnd; d += 24 * 3600 * 1000L) {
                    StepRecord sr = db.stepRecordDao().getByDateSync(d);
                    if (sr != null && sr.getSteps() > 0) {
                        totalSteps += sr.getSteps();
                        stepDays++;
                    }
                }
                int stepsAvg = stepDays > 0 ? totalSteps / stepDays : 0;
                android.content.SharedPreferences stepSp = requireContext().getSharedPreferences("fitness_diary_prefs", android.content.Context.MODE_PRIVATE);
                int stepTarget = stepSp.getInt("step_target", 10000);

                // Sleep avg
                java.util.List<SleepRecord> sleepRecords = db.sleepRecordDao().getSleepRecordsByDateRangeSync(weekStart, weekEnd);
                float totalSleep = 0f;
                int sleepDays = 0;
                float totalQuality = 0f;
                int qualityDays = 0;
                for (long d = weekStart; d < weekEnd; d += 24 * 3600 * 1000L) {
                    float dayHrs = 0f;
                    float dayQualitySum = 0f;
                    int dayQualityCount = 0;
                    if (sleepRecords != null) {
                        for (SleepRecord sr : sleepRecords) {
                            if (sr.getStartTime() >= d && sr.getStartTime() < d + 24 * 3600 * 1000L) {
                                dayHrs += sr.getDuration() / 3600f;
                                if (sr.getQuality() > 0) {
                                    dayQualitySum += sr.getQuality();
                                    dayQualityCount++;
                                }
                            }
                        }
                    }
                    if (dayHrs > 0f) {
                        totalSleep += dayHrs;
                        sleepDays++;
                    }
                    if (dayQualityCount > 0) {
                        totalQuality += (dayQualitySum / dayQualityCount);
                        qualityDays++;
                    }
                }
                float sleepHrsAvg = sleepDays > 0 ? totalSleep / sleepDays : 0f;
                float sleepQualityAvg = qualityDays > 0 ? totalQuality / qualityDays : 0f;

                // Active Burned Calories
                int totalActiveCal = 0;
                if (allLogs != null) {
                    float weightVal = (user != null && user.getWeight() > 0) ? (float) user.getWeight() : 65f;
                    for (DailyLog log : allLogs) {
                        if (log.getDate() >= weekStart && log.getDate() < weekEnd && log.isCompleted()) {
                            com.cz.fitnessdiary.database.entity.TrainingPlan plan = planMap.get(log.getPlanId());
                            int duration = com.cz.fitnessdiary.utils.ExerciseMetTable.resolveDuration(
                                    log.getDuration(),
                                    plan != null ? plan.getDuration() : 0,
                                    plan != null ? plan.getSets() : 0,
                                    plan != null ? plan.getReps() : 0,
                                    requireContext());
                            String category = plan != null ? plan.getCategory() : "无";
                            double met = com.cz.fitnessdiary.utils.ExerciseMetTable.getMetForExercise(plan != null ? plan.getName() : "", category);
                            int burned = (int) (met * weightVal * (duration / 3600.0));
                            totalActiveCal += burned;
                        }
                    }
                }

                // Bowel Movements
                java.util.List<BowelMovement> bowels = db.bowelMovementDao().getByDateRangeSync(weekStart, weekEnd);
                int bowelCount = 0;
                int bowelAbnormalCount = 0;
                if (bowels != null) {
                    bowelCount = bowels.size();
                    for (BowelMovement bm : bowels) {
                        int type = bm.getBristolType();
                        boolean abnormalType = (type == 1 || type == 2 || type == 5 || type == 6 || type == 7);
                        boolean abnormalFeeling = "DIFFICULT".equals(bm.getProcessFeeling()) 
                                || "INCOMPLETE".equals(bm.getProcessFeeling());
                        if (abnormalType || abnormalFeeling) {
                            bowelAbnormalCount++;
                        }
                    }
                }

                // Mood calculation
                int mDays = 0;
                float totalMoodScore = 0f;
                java.util.Map<String, Integer> moodCounts = new java.util.HashMap<>();
                for (long d = weekStart; d < weekEnd; d += 24 * 3600 * 1000L) {
                    MoodRecord mr = db.moodRecordDao().getByDateSync(d);
                    if (mr != null && mr.getMoodCode() != null) {
                        String code = mr.getMoodCode();
                        moodCounts.put(code, moodCounts.getOrDefault(code, 0) + 1);
                        int score = 3;
                        if ("HAPPY".equals(code)) score = 5;
                        else if ("NEUTRAL".equals(code)) score = 4;
                        else if ("SAD".equals(code) || "IRRITABLE".equals(code) || "ANXIOUS".equals(code)) score = 2;
                        totalMoodScore += score;
                        mDays++;
                    }
                }
                float moodAvgScore = mDays > 0 ? totalMoodScore / mDays : 0f;
                String primaryMood = null;
                int maxCount = 0;
                for (java.util.Map.Entry<String, java.lang.Integer> entry : moodCounts.entrySet()) {
                    if (entry.getValue() > maxCount) {
                        maxCount = entry.getValue();
                        primaryMood = entry.getKey();
                    }
                }

                ShareUtils.WeekSummary summary = new ShareUtils.WeekSummary();
                summary.weekRange = weekRange;
                summary.exerciseDays = exerciseDays;
                summary.totalExerciseMinutes = totalDurationSec / 60;
                summary.avgCaloriesConsumed = avgCal;
                summary.calorieTarget = target;
                summary.weightStart = wStart;
                summary.weightEnd = wEnd;
                summary.waterAvgMl = waterAvg;
                summary.waterTarget = (user != null && user.getDailyWaterTarget() > 0) ? user.getDailyWaterTarget() : 2000;
                summary.habitCompletionRate = Math.min(habitRate, 100);

                summary.avgSteps = stepsAvg;
                summary.stepTarget = stepTarget;
                summary.avgSleepDuration = sleepHrsAvg;
                summary.avgSleepQuality = sleepQualityAvg;
                summary.totalActiveCalories = totalActiveCal;

                summary.avgMoodScore = moodAvgScore;
                summary.primaryMood = primaryMood;
                summary.moodDays = mDays;
                summary.bowelCount = bowelCount;
                summary.bowelAbnormalCount = bowelAbnormalCount;

                requireActivity().runOnUiThread(() -> ShareUtils.shareWeekReport(requireContext(), summary));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "生成报告失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        // 点击整个头像卡片 - 修改昵称、头像、性别、年龄及生日
        binding.layoutProfileHeaderClick.setOnClickListener(v -> showEditProfileHeaderDialog());

        binding.cardAccountFriends.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.accountFragment));

        // 点击头像本身也触发编辑
        binding.ivAvatar.setOnClickListener(v -> showEditProfileHeaderDialog());

        // 📊 身体数据中心：点击通栏卡片内部的 LinearLayout 响应
        binding.layoutBodyDataClick.setOnClickListener(v -> {
            BodyDataDetailBottomSheetFragment bottomSheet = new BodyDataDetailBottomSheetFragment();
            bottomSheet.setOnDataUpdatedListener(() -> {
                viewModel.refreshUser(); // 刷新数据
            });
            bottomSheet.show(getChildFragmentManager(), "BodyDataDetail");
        });

        // 🎯 阶段目标合并：点击卡片内部的 LinearLayout 响应
        binding.layoutPhaseGoalClick.setOnClickListener(v -> {
            String[] options = {"修改健身目标", "修改日常活动水平"};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("配置阶段目标")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            showGoalSelectionDialog();
                        } else {
                            showEditActivityLevelDialog();
                        }
                    })
                    .show();
        });

        // 📂 【我的内容资产】分类块
        binding.btnMyExercises.setOnClickListener(v -> {
            Navigation.findNavController(requireView())
                    .navigate(R.id.customExerciseFragment);
        });

        binding.btnMyRecipes.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.recipeListFragment);
        });

        // 🔄 【设备与连接】分类块
        binding.btnConnectHealth.setOnClickListener(v -> {
            Toast.makeText(getContext(), "❤️ Apple Health & Google Fit 同步功能正在开发中...", Toast.LENGTH_SHORT).show();
        });

        binding.btnConnectDevices.setOnClickListener(v -> {
            Toast.makeText(getContext(), "⌚ 智能手环与体脂秤同步功能正在开发中...", Toast.LENGTH_SHORT).show();
        });

        // ⚙️ 【系统通用设置】分类块
        binding.btnSettingsUnits.setOnClickListener(v -> showUnitAndDisplaySettingsDialog());

        binding.btnSettingsNotifications.setOnClickListener(v -> {
            // Check permissions first before showing notification settings
            if (!checkNotificationPermission()) return;
            if (!checkExactAlarmPermission()) return;
            showNotificationSettingsDialog();
        });

        binding.btnSettingsData.setOnClickListener(v -> showSettingsBottomSheet());

        // 🛠️ 【健身工具箱】点击事件
        binding.btnTool1rm.setOnClickListener(v -> show1RMCalculatorDialog());
        binding.btnToolTdee.setOnClickListener(v -> showTDEECalculatorDialog());

        // 关于 App 点击事件
        binding.btnAbout.setOnClickListener(v -> showAboutFitnessDiaryDialog());


        // 其他经典旧 system 卡片 (周报、成就)
        binding.cardAchievements.setOnClickListener(v -> {
            new AchievementBottomSheetFragment().show(getChildFragmentManager(), "AchievementBottomSheet");
        });

        setupReportEntry();

        // 每日目标综合设置
        binding.btnDailyTargets.setOnClickListener(v -> showDailyTargetsDialog());

        // 清除缓存
        binding.btnClearCache.setOnClickListener(v -> showClearCacheDialog());

        // 新手引导回顾
        binding.btnReplayGuide.setOnClickListener(v -> replayOnboarding());

        // 推荐给好友
        binding.btnShareApp.setOnClickListener(v -> shareApp());

        // 意见反馈
        binding.btnFeedback.setOnClickListener(v -> sendFeedback());
    }

    private void updateCloudAccountSummary() {
        if (binding == null) return;
        if (!CloudApiConfig.isConfigured()) {
            binding.tvCloudAccountStatus.setText("云服务未配置 · 本地功能不受影响");
            return;
        }
        AccountUser account = new AccountRepository(requireContext()).getCurrentAccount();
        if (account == null) {
            binding.tvCloudAccountStatus.setText("未登录 · 本地功能不受影响");
        } else if (!account.isEmailVerified()) {
            binding.tvCloudAccountStatus.setText("邮箱待验证 · 朋友功能暂不可用");
        } else {
            binding.tvCloudAccountStatus.setText("已登录 · 查看好友与动态");
        }
    }

    /**
     * 打开图库选择器 (直接启动系统图库而非文件管理器)
     */
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    /**
     * 将临时 URI 复制到指定缓存文件中
     */
    private boolean copyUriToFile(Uri uri, java.io.File destFile) {
        try {
            java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
            if (in == null) return false;
            if (destFile.exists()) {
                destFile.delete();
            }
            java.io.FileOutputStream out = new java.io.FileOutputStream(destFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 调起系统裁剪应用 (ACTION_CROP)
     */
    private void startCrop(java.io.File rawFile) {
        try {
            // 通过 FileProvider 获取安全的 content:// URI
            Uri sourceUri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "com.cz.fitnessdiary.fileprovider",
                    rawFile
            );

            java.io.File cropFile = new java.io.File(requireContext().getCacheDir(), "crop_avatar_temp.jpg");
            if (cropFile.exists()) {
                cropFile.delete();
            }
            cropFile.createNewFile();

            Uri destinationUri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "com.cz.fitnessdiary.fileprovider",
                    cropFile
            );

            Intent intent = new Intent("com.android.camera.action.CROP");
            intent.setDataAndType(sourceUri, "image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("aspectX", 1);
            intent.putExtra("aspectY", 1);
            intent.putExtra("outputX", 320);
            intent.putExtra("outputY", 320);
            intent.putExtra("scale", true);

            // 临时读写授权，解决 Android 11+ 分区存储导致的沙盒越权问题
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, destinationUri);
            intent.putExtra("return-data", false);
            intent.putExtra("outputFormat", android.graphics.Bitmap.CompressFormat.JPEG.toString());

            // 跨应用临时权限显式授予（对齐特定手机 ROM 要求）
            android.content.pm.PackageManager pm = requireContext().getPackageManager();
            java.util.List<android.content.pm.ResolveInfo> resInfoList = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            for (android.content.pm.ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                requireContext().grantUriPermission(packageName, sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                requireContext().grantUriPermission(packageName, destinationUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            cropImageLauncher.launch(intent);
        } catch (Exception e) {
            e.printStackTrace();
            // 如果裁剪程序调起崩溃/异常（如少数去裁剪器的魔改系统），直接原图保存
            useOriginalImage(rawFile);
        }
    }

    /**
     * 兜底：跳过裁剪，直接使用原图进行拷贝和保存
     */
    private void useOriginalImage(java.io.File rawFile) {
        java.io.File localFile = com.cz.fitnessdiary.utils.MediaManager
                .saveToInternal(requireContext(), Uri.fromFile(rawFile));
        if (localFile != null) {
            String localPath = localFile.getAbsolutePath();
            viewModel.updateAvatarUri(localPath);
            binding.ivAvatar.setImageURI(Uri.fromFile(localFile));
            Toast.makeText(getContext(), "已自动适配原图头像", Toast.LENGTH_SHORT).show();
        }
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

    public void showPageGuide(GuideStateManager guideManager) {
        String pageKey = "guide_profile";
        if (guideManager.isPageGuideDone(pageKey)) return;
        // Profile page guide not yet implemented
        guideManager.markPageGuideDone(pageKey);
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

        // 设置分类标签 and 颜色
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

        // 设置指针颜色同步
        View pointer = view.findViewById(R.id.view_bmi_pointer);
        pointer.getBackground().setColorFilter(categoryColor, android.graphics.PorterDuff.Mode.SRC_IN);

        // 设置指针位置 (BMI范围: 12-36, 映射到进度条宽度)
        pointer.post(() -> {
            View bar = view.findViewById(R.id.view_bmi_bar);
            int barWidth = bar.getWidth();
            double clampedBmi = Math.max(12, Math.min(36, bmi));
            double progress = (clampedBmi - 12) / 24.0; // 量程扩大至 12-36，匹配分段 UI
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
        tvWeightValue.setText(UnitUtils.formatWeight((float) weight, requireContext()));

        // 计算建议体重范围 (BMI 18.5 ~ 24.0)
        double minWeight = 18.5 * heightM * heightM;
        double maxWeight = 24.0 * heightM * heightM;
        tvSuggestedWeight.setText(UnitUtils.formatWeight((float) minWeight, requireContext()) + " ~ " + UnitUtils.formatWeight((float) maxWeight, requireContext()) + " " + UnitUtils.getWeightUnitSymbol(requireContext()));

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
        tvBmrValue.setText(UnitUtils.formatEnergy(bmrValue, requireContext()));

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

        tvDeficit.setText(UnitUtils.formatEnergy(deficitCalories, requireContext()) + " " + UnitUtils.getEnergyUnitSymbol(requireContext()));
        tvMaintain.setText(UnitUtils.formatEnergy(maintainCalories, requireContext()) + " " + UnitUtils.getEnergyUnitSymbol(requireContext()));
        tvSurplus.setText(UnitUtils.formatEnergy(surplusCalories, requireContext()) + " " + UnitUtils.getEnergyUnitSymbol(requireContext()));

        // 计算依据
        android.widget.TextView tvGender = view.findViewById(R.id.tv_gender);
        android.widget.TextView tvAge = view.findViewById(R.id.tv_age);
        android.widget.TextView tvHeight = view.findViewById(R.id.tv_height);
        android.widget.TextView tvWeight = view.findViewById(R.id.tv_weight);

        tvGender.setText(gender == com.cz.fitnessdiary.utils.CalorieCalculatorUtils.GENDER_MALE ? "男" : "女");
        tvAge.setText(age + " 岁");
        tvHeight.setText(height + " cm");
        tvWeight.setText(UnitUtils.formatWeight((float) weight, requireContext()) + " " + UnitUtils.getWeightUnitSymbol(requireContext()));

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
                "💪 高度活动 (1.725) - 每交运动6-7次",
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

    /**
     * 第一阶段追加：显示 1RM 计算器对话框
     */
    private void show1RMCalculatorDialog() {
        Context context = requireContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        // 显式指定 LayoutParams 为 WRAP_CONTENT，消除 MATCH_PARENT 导致的大白板！
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layout.setLayoutParams(layoutParams);

        final EditText etWeight = new EditText(context);
        etWeight.setHint("输入负重 (kg)  例: 60");
        etWeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etWeight.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(etWeight);

        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                24
        ));
        layout.addView(spacer);

        final EditText etReps = new EditText(context);
        etReps.setHint("输入重复次数 (1-10)  例: 5");
        etReps.setInputType(InputType.TYPE_CLASS_NUMBER);
        etReps.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(etReps);

        new MaterialAlertDialogBuilder(context)
                .setTitle("1RM 极限重量计算")
                .setMessage("1RM (One-Repetition Maximum) 指你在该动作下只能规范地完成一次的最大重量。")
                .setView(layout)
                .setPositiveButton("精算", (dialog, which) -> {
                    String wStr = etWeight.getText().toString().trim();
                    String rStr = etReps.getText().toString().trim();
                    if (wStr.isEmpty() || rStr.isEmpty()) {
                        Toast.makeText(context, "请填入完整的负重与次数", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        double w = Double.parseDouble(wStr);
                        int r = Integer.parseInt(rStr);
                        if (r <= 0 || r > 15) {
                            Toast.makeText(context, "为了准确性，推荐输入 1~15 次", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // Epley 公式计算
                        double oneRM = w * (1.0 + r / 30.0);
                        
                        new MaterialAlertDialogBuilder(context)
                                .setTitle("精算结果")
                                .setMessage(String.format(Locale.getDefault(), 
                                        "您的 1RM 极限负重约为：\n\n🔥 %.1f kg\n\n💡 训练强度指导建议：\n• 肌力训练 (85%%+ 1RM): %.1f kg (1-5次)\n• 增肌训练 (70%%-80%% 1RM): %.1f - %.1f kg (8-12次)\n• 耐力训练 (60%% 1RM): %.1f kg (15次+)",
                                        oneRM, oneRM * 0.85, oneRM * 0.70, oneRM * 0.80, oneRM * 0.60))
                                .setPositiveButton("确定", null)
                                .show();
                    } catch (Exception e) {
                        Toast.makeText(context, "输入格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 第一阶段追加：显示 TDEE 计算器对话框
     */
    private void showTDEECalculatorDialog() {
        Context context = requireContext();
        User user = viewModel.getCurrentUser().getValue();
        
        double defaultWeight = (user != null && user.getWeight() > 0) ? user.getWeight() : 65.0;
        double defaultHeight = (user != null && user.getHeight() > 0) ? user.getHeight() : 170.0;
        int defaultAge = (user != null && user.getAge() > 0) ? user.getAge() : 25;
        int defaultGender = (user != null) ? user.getGender() : 1; // 1=男, 2=女

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        // 显式指定 LayoutParams 为 WRAP_CONTENT，消除 MATCH_PARENT 导致的大白板！
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layout.setLayoutParams(layoutParams);

        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        final EditText etWeight = new EditText(context);
        etWeight.setHint("体重 (kg)");
        // 格式化保留 1 位小数，避免精度拖尾 (如 54.04999...)
        etWeight.setText(String.format(Locale.getDefault(), "%.1f", defaultWeight));
        etWeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etWeight.setLayoutParams(itemParams);
        layout.addView(etWeight);

        View spacer1 = new View(context);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 16));
        layout.addView(spacer1);

        final EditText etHeight = new EditText(context);
        etHeight.setHint("身高 (cm)");
        etHeight.setText(String.format(Locale.getDefault(), "%.1f", defaultHeight));
        etHeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etHeight.setLayoutParams(itemParams);
        layout.addView(etHeight);

        View spacer2 = new View(context);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 16));
        layout.addView(spacer2);

        final EditText etAge = new EditText(context);
        etAge.setHint("年龄");
        etAge.setText(String.valueOf(defaultAge));
        etAge.setInputType(InputType.TYPE_CLASS_NUMBER);
        etAge.setLayoutParams(itemParams);
        layout.addView(etAge);

        View spacer3 = new View(context);
        spacer3.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 24));
        layout.addView(spacer3);

        TextView tvActivityTitle = new TextView(context);
        tvActivityTitle.setText("选择日常活动水平：");
        tvActivityTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvActivityTitle.setTextSize(14);
        tvActivityTitle.setPadding(0, 0, 0, 8);
        tvActivityTitle.setLayoutParams(itemParams);
        layout.addView(tvActivityTitle);

        final String[] activityLevels = {
                "久坐 (基本不运动 - 系数 1.2)",
                "轻度活跃 (每周运动 1-3 天 - 系数 1.375)",
                "中度活跃 (每周运动 3-5 天 - 系数 1.55)",
                "重度活跃 (每周运动 6-7 天 - 系数 1.725)",
                "极重度活跃 (高强度竞技/体力劳动 - 系数 1.9)"
        };
        final double[] activityFactors = {1.2, 1.375, 1.55, 1.725, 1.9};
        final int[] selectedIdx = {1}; // 默认轻度活跃

        // 使用普通 TextView + 点击弹单选列表，避免 AutoCompleteTextView 在 AlertDialog 中无法交互
        final TextView tvActivitySelected = new TextView(context);
        tvActivitySelected.setText(activityLevels[1]);
        tvActivitySelected.setTextSize(14);
        tvActivitySelected.setTextColor(getResources().getColor(R.color.fitnessdiary_primary, null));
        tvActivitySelected.setPadding(12, 14, 12, 14);
        tvActivitySelected.setBackground(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_code_block));
        tvActivitySelected.setLayoutParams(itemParams);
        tvActivitySelected.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("日常活动水平")
                    .setSingleChoiceItems(activityLevels, selectedIdx[0], (d, i) -> {
                        selectedIdx[0] = i;
                        tvActivitySelected.setText(activityLevels[i]);
                        d.dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        layout.addView(tvActivitySelected);

        new MaterialAlertDialogBuilder(context)
                .setTitle("TDEE 每日总能耗精算")
                .setMessage("TDEE (Total Daily Energy Expenditure) 代表你每天的总能耗。在此基础上吃多增重，吃少减脂。")
                .setView(layout)
                .setPositiveButton("计算", (dialog, which) -> {
                    try {
                        double w = Double.parseDouble(etWeight.getText().toString().trim());
                        double h = Double.parseDouble(etHeight.getText().toString().trim());
                        int a = Integer.parseInt(etAge.getText().toString().trim());
                        
                        double factor = activityFactors[selectedIdx[0]];

                        // Mifflin-St Jeor 基础代谢公式
                        double bmr;
                        if (defaultGender == 1) {
                            bmr = 10.0 * w + 6.25 * h - 5.0 * a + 5.0;
                        } else {
                            bmr = 10.0 * w + 6.25 * h - 5.0 * a - 161.0;
                        }
                        double tdee = bmr * factor;

                        new MaterialAlertDialogBuilder(context)
                                .setTitle("精算结果")
                                .setMessage("您的代谢评估指标如下：\n\n基础代谢率 (BMR): " + UnitUtils.formatEnergy((float) bmr, context) + UnitUtils.getEnergyUnitSymbol(context)
                                        + "\nTDEE 每日总消耗: " + UnitUtils.formatEnergy((float) tdee, context) + UnitUtils.getEnergyUnitSymbol(context)
                                        + "\n\n💡 营养摄入热量预算指导：\n• 纯净减脂预算 (TDEE - 400): " + UnitUtils.formatEnergy((float) (tdee - 400), context) + UnitUtils.getEnergyUnitSymbol(context)
                                        + "\n• 科学增肌预算 (TDEE + 300): " + UnitUtils.formatEnergy((float) (tdee + 300), context) + UnitUtils.getEnergyUnitSymbol(context)
                                        + "\n• 保持体重预算 (维持 TDEE): " + UnitUtils.formatEnergy((float) tdee, context) + UnitUtils.getEnergyUnitSymbol(context))
                                .setPositiveButton("确定", null)
                                .show();
                    } catch (Exception e) {
                        Toast.makeText(context, "输入格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 第一阶段追加：关于对话框
     */
    private void showAboutFitnessDiaryDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("关于 FitnessDiary")
                .setMessage("FitnessDiary 健身日记 v" + BuildConfig.VERSION_NAME + "\n\n"
                        + "极简、纯净、无广告的个人健康管理应用。\n\n"
                        + "核心理念：\n"
                        + "“用代码雕琢习惯，用汗水记录蜕变。没有花哨的营销，只有最纯粹的数据见证。”\n\n"
                        + "技术构型：\n"
                        + "• MVVM + Android Room 数据库\n"
                        + "• Material Design 3 风格大一统\n"
                        + "• DeepSeek & Qwen 智能大模型助手内核\n\n"
                        + "致敬每一个今天依然在自律训练的你！")
                .setPositiveButton("致敬", null)
                .show();
    }

    /**
     * 每日目标综合设置弹窗 — 集中管理饮水/步数/运动时长/体重目标
     * 饮食宏量目标为只读展示（由健身目标+活动水平自动计算）
     */
    private void showDailyTargetsDialog() {
        Context ctx = requireContext();
        User user = viewModel.getCurrentUser().getValue();
        if (user == null) return;

        SharedPreferences stepSp = ctx.getSharedPreferences("fitness_diary_prefs", Context.MODE_PRIVATE);
        SharedPreferences healthSp = ctx.getSharedPreferences("health_score_prefs", Context.MODE_PRIVATE);
        SharedPreferences exerciseSp = ctx.getSharedPreferences("exercise_targets", Context.MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        // ── 可编辑目标 ──
        TextView tvSection1 = new TextView(ctx);
        tvSection1.setText("── 运动与活动目标 ──");
        tvSection1.setTextSize(13);
        tvSection1.setTextColor(0xFF607D8B);
        tvSection1.setPadding(0, 0, 0, 12);
        layout.addView(tvSection1);

        // 饮水目标
        addLabel(layout, "饮水目标 (ml/天)");
        final EditText etWater = new EditText(ctx);
        etWater.setInputType(InputType.TYPE_CLASS_NUMBER);
        etWater.setText(String.valueOf(user.getDailyWaterTarget()));
        etWater.setHint("2000");
        addEditText(layout, etWater);

        // 步数目标
        addLabel(layout, "步数目标 (步/天)");
        final EditText etSteps = new EditText(ctx);
        etSteps.setInputType(InputType.TYPE_CLASS_NUMBER);
        etSteps.setText(String.valueOf(stepSp.getInt("step_target", 8000)));
        etSteps.setHint("8000");
        addEditText(layout, etSteps);

        // 运动时长目标
        addLabel(layout, "运动时长目标 (分钟/天)");
        final EditText etExercise = new EditText(ctx);
        etExercise.setInputType(InputType.TYPE_CLASS_NUMBER);
        etExercise.setText(String.valueOf(exerciseSp.getInt("target_minutes_default", 0)));
        etExercise.setHint("0 (不设目标)");
        addEditText(layout, etExercise);

        // 体重目标
        addLabel(layout, "体重目标 (kg, 留空自动计算)");
        final EditText etWeightTarget = new EditText(ctx);
        etWeightTarget.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        float currentTarget = healthSp.getFloat("target_weight_kg", -1f);
        if (currentTarget > 0) {
            etWeightTarget.setText(String.format(Locale.getDefault(), "%.1f", currentTarget));
        }
        etWeightTarget.setHint("留空使用BMI健康范围自动计算");
        addEditText(layout, etWeightTarget);

        // 分隔线
        View divider = new View(ctx);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFE0E0E0);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divParams.setMargins(0, 16, 0, 16);
        divider.setLayoutParams(divParams);
        layout.addView(divider);

        // ── 只读营养目标 ──
        TextView tvSection2 = new TextView(ctx);
        tvSection2.setText("── 饮食营养目标（自动计算）──");
        tvSection2.setTextSize(13);
        tvSection2.setTextColor(0xFF607D8B);
        tvSection2.setPadding(0, 0, 0, 8);
        layout.addView(tvSection2);

        TextView tvExplanation = new TextView(ctx);
        String goalStr;
        int goalType = user.getGoalType();
        if (goalType == 0) goalStr = "减脂";
        else if (goalType == 1) goalStr = "增肌";
        else goalStr = "保持";
        String activityStr;
        float al = user.getActivityLevel();
        if (al <= 1.2f) activityStr = "久坐";
        else if (al <= 1.375f) activityStr = "轻度活动";
        else if (al <= 1.55f) activityStr = "中度活动";
        else if (al <= 1.725f) activityStr = "高度活动";
        else activityStr = "专业运动员";
        tvExplanation.setText("基于您的目标「" + goalStr + "」和活动水平「" + activityStr
                + "」自动计算，修改健身目标即可调整。");
        tvExplanation.setTextSize(12);
        tvExplanation.setTextColor(0xFF888888);
        tvExplanation.setPadding(0, 0, 0, 12);
        layout.addView(tvExplanation);

        int calTarget = user.getDailyCalorieTarget();
        int proteinTarget = user.getTargetProtein();
        int carbsTarget = user.getTargetCarbs();
        int fatTarget = user.getTargetFat();

        addReadOnlyRow(layout, "热量目标", UnitUtils.formatEnergy(calTarget, ctx) + " " + UnitUtils.getEnergyUnitSymbol(ctx));
        addReadOnlyRow(layout, "蛋白质目标", proteinTarget + " g");
        addReadOnlyRow(layout, "碳水目标", carbsTarget + " g");
        addReadOnlyRow(layout, "脂肪目标", fatTarget + " g");

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("🎯 每日目标综合设置")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String waterStr = etWater.getText().toString().trim();
                    String stepStr = etSteps.getText().toString().trim();
                    String exerciseStr = etExercise.getText().toString().trim();
                    String weightStr = etWeightTarget.getText().toString().trim();

                    new Thread(() -> {
                        if (!waterStr.isEmpty()) {
                            try {
                                int water = Integer.parseInt(waterStr);
                                if (water >= 0 && water <= 20000) {
                                    user.setDailyWaterTarget(water);
                                    AppDatabase.getInstance(ctx).userDao().update(user);
                                    // 同时写入当日独立值
                                    long todayTs = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
                                    stepSp.edit().putInt("water_target_" + todayTs, water).apply();
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        requireActivity().runOnUiThread(() -> {
                            if (!stepStr.isEmpty()) {
                                try {
                                    int steps = Integer.parseInt(stepStr);
                                    if (steps >= 0 && steps <= 100000) {
                                        // 保存全局默认 + 当日独立值，避免修改影响其他日期
                                        stepSp.edit().putInt("step_target", steps).apply();
                                        long todayTs = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
                                        stepSp.edit().putInt("step_target_" + todayTs, steps).apply();
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                            if (!exerciseStr.isEmpty()) {
                                try {
                                    int mins = Integer.parseInt(exerciseStr);
                                    if (mins >= 0 && mins <= 600) {
                                        exerciseSp.edit().putInt("target_minutes_default", mins).apply();
                                        // 同时写入当日独立值，避免被后续修改覆盖
                                        long todayTs = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
                                        requireContext().getSharedPreferences("fitness_diary_prefs", android.content.Context.MODE_PRIVATE)
                                                .edit().putInt("target_minutes_" + todayTs, mins).apply();
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                            if (weightStr.isEmpty()) {
                                healthSp.edit().remove("target_weight_kg").apply();
                            } else {
                                try {
                                    float w = Float.parseFloat(weightStr);
                                    if (w > 0 && w < 500) {
                                        healthSp.edit().putFloat("target_weight_kg", w).apply();
                                        // 同时写入当日独立值
                                        long todayTs = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
                                        healthSp.edit().putFloat("target_weight_kg_" + todayTs, w).apply();
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                            Toast.makeText(ctx, "✅ 目标已保存", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(parent.getContext());
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(0xFF888888);
        tv.setPadding(0, 8, 0, 4);
        parent.addView(tv);
    }

    private void addEditText(LinearLayout parent, EditText et) {
        et.setTextSize(14);
        et.setPadding(12, 10, 12, 10);
        et.setBackgroundColor(0xFFF5F5F5);
        parent.addView(et);
    }

    private void addReadOnlyRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 6, 0, 6);

        TextView tvLabel = new TextView(parent.getContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(14);
        tvLabel.setTextColor(0xFF333333);
        row.addView(tvLabel);

        View spacer = new View(parent.getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1));
        row.addView(spacer);

        TextView tvValue = new TextView(parent.getContext());
        tvValue.setText(value);
        tvValue.setTextSize(14);
        tvValue.setTextColor(0xFF2196F3);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(tvValue);

        parent.addView(row);
    }

    /**
     * 清除缓存 — Glide 图片缓存 + AI 对话历史
     */
    private void showClearCacheDialog() {
        Context ctx = requireContext();

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("🧹 清除缓存")
                .setMessage("将清除以下内容：\n\n" +
                        "• 图片缓存（Glide）\n" +
                        "• AI 私教对话历史\n\n" +
                        "不会影响您的训练数据、饮食记录和个人设置。")
                .setPositiveButton("确认清除", (dialog, which) -> {
                    new Thread(() -> {
                        // 清除 Glide 磁盘缓存
                        try {
                            Glide.get(ctx).clearDiskCache();
                        } catch (Exception ignored) {}

                        // 清除 AI 对话历史
                        try {
                            AppDatabase.getInstance(ctx).chatMessageDao().deleteAll();
                        } catch (Exception ignored) {}

                        // 清除 Glide 内存缓存 (须在主线程)
                        requireActivity().runOnUiThread(() -> {
                            try {
                                Glide.get(ctx).clearMemory();
                            } catch (Exception ignored) {}
                            Toast.makeText(ctx, "✅ 缓存已清除", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 新手引导回顾 — 重置引导状态并重新播放全局引导
     */
    private void replayOnboarding() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("📖 新手引导回顾")
                .setMessage("将重新展示首次使用时的新手引导，帮助您快速回顾各项功能。\n\n是否继续？")
                .setPositiveButton("开始回顾", (dialog, which) -> {
                    // 重置所有引导状态
                    new GuideStateManager(requireContext()).resetAll();

                    // 显示全局引导弹窗
                    try {
                        OnboardingOverlayFragment onboarding = new OnboardingOverlayFragment();
                        onboarding.show(getChildFragmentManager(), "OnboardingOverlay");
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "引导加载失败，请重启应用", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 推荐给好友 — 分享应用信息
     */
    private void shareApp() {
        String shareText = "💪 推荐你试试 FitnessDiary 健身日记！\n\n"
                + "极简、纯净、无广告的个人健康管理应用。\n"
                + "📊 训练计划 · 饮食记录 · 智能AI私教 · 健康评分\n\n"
                + "用代码雕琢习惯，用汗水记录蜕变。";

        try {
            String apkPath = requireContext().getApplicationInfo().sourceDir;
            File apkFile = new File(apkPath);
            if (!apkFile.exists()) {
                Toast.makeText(requireContext(), "APK 文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }

            File cacheDir = new File(requireContext().getCacheDir(), "share");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File destFile = new File(cacheDir, "FitnessDiary_v" + BuildConfig.VERSION_NAME + ".apk");

            new Thread(() -> {
                try {
                    java.io.FileInputStream fis = new java.io.FileInputStream(apkFile);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                    fos.close();
                    fis.close();

                    requireActivity().runOnUiThread(() -> {
                        Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                                requireContext(), "com.cz.fitnessdiary.fileprovider", destFile);
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("application/vnd.android.package-archive");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, apkUri);
                        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(shareIntent, "分享安装包"));
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "分享失败", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "分享失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 意见反馈 — 发送邮件到开发者邮箱
     */
    private void sendFeedback() {
        String[] recipients = {"2322106007@qq.com"};
        String subject = "FitnessDiary 反馈 - v" + BuildConfig.VERSION_NAME;

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.putExtra(Intent.EXTRA_EMAIL, recipients);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

        try {
            startActivity(emailIntent);
        } catch (Exception e) {
            // 如果没有邮件客户端，提示用户
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("📧 意见反馈")
                    .setMessage("请发送邮件至：\n2322106007@qq.com\n\n主题：" + subject)
                    .setPositiveButton("复制邮箱", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("email", "2322106007@qq.com");
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(requireContext(), "邮箱已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        }
    }
}
