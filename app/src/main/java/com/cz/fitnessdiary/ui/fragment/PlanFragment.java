package com.cz.fitnessdiary.ui.fragment;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.FragmentPlanBinding;
import com.cz.fitnessdiary.ui.adapter.GroupedPlanAdapter;
import com.cz.fitnessdiary.utils.PermissionHelper;
import com.cz.fitnessdiary.viewmodel.PlanViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 训练计划页面 - 2.0 升级版
 * 添加组数、次数和媒体支持
 */
public class PlanFragment extends Fragment {

    private FragmentPlanBinding binding;
    private PlanViewModel viewModel;
    private GroupedPlanAdapter adapter; // Plan 10: 使用分组适配器

    // 相册选择
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private String selectedMediaUri = null;
    private ImageView currentMediaPreview = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 注册相册选择器
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri mediaUri = result.getData().getData();
                            if (mediaUri != null) {
                                // 获取持久化读权限
                                try {
                                    requireContext().getContentResolver().takePersistableUriPermission(
                                            mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                } catch (SecurityException e) {
                                    e.printStackTrace();
                                }

                                selectedMediaUri = mediaUri.toString();
                                // 显示缩略图
                                if (currentMediaPreview != null) {
                                    currentMediaPreview.setVisibility(View.VISIBLE);
                                    // 显示缩略图 (使用原生 setImageURI 以避免 HeifDecoder 兼容性问题)
                                    if (currentMediaPreview != null) {
                                        currentMediaPreview.setVisibility(View.VISIBLE);
                                        currentMediaPreview.setImageURI(mediaUri);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "处理图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPlanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(PlanViewModel.class);

        setupRecyclerView();
        observeViewModel();

        // FAB 点击添加计划
        binding.fabAddPlan.setOnClickListener(v -> showAddPlanDialog());
    }

    /**
     * 设置 RecyclerView
     */
    private void setupRecyclerView() {
        // Plan 10: 使用分组适配器
        adapter = new GroupedPlanAdapter();
        adapter.setOnPlanClickListener(new GroupedPlanAdapter.OnPlanClickListener() {
            @Override
            public void onPlanClick(TrainingPlan plan) {
                // Plan 10: 显示编辑对话框
                showEditPlanDialog(plan);
            }

            @Override
            public void onPlanDelete(TrainingPlan plan) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("删除计划")
                        .setMessage("确定要删除这个训练计划吗？")
                        .setPositiveButton("删除", (dialog, which) -> viewModel.deletePlan(plan))
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        // Plan 28: 设置长按分类回调
        adapter.setOnCategoryLongClickListener(category -> {
            showRenameCategoryDialog(category);
        });

        binding.rvPlans.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvPlans.setAdapter(adapter);
    }

    /**
     * 观察 ViewModel 数据
     */
    private void observeViewModel() {
        // Plan 10: 观察分组后的计划
        viewModel.getGroupedPlans().observe(getViewLifecycleOwner(), groupedPlans -> {
            if (groupedPlans != null && !groupedPlans.isEmpty()) {
                adapter.setGroupList(groupedPlans);
                binding.tvNoPlans.setVisibility(View.GONE);
                binding.rvPlans.setVisibility(View.VISIBLE);
            } else {
                binding.tvNoPlans.setVisibility(View.VISIBLE);
                binding.rvPlans.setVisibility(View.GONE);
            }
        });

        // Plan 10: 观察总计划数
        viewModel.getTotalPlanCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null) {
                binding.tvPlanCount.setText(String.valueOf(count));
            } else {
                binding.tvPlanCount.setText("0");
            }
        });

        // Plan 10: 观察覆盖部位
        viewModel.getCoveredCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null && !categories.isEmpty()) {
                binding.tvCoveredCategories.setText(categories);
            } else {
                binding.tvCoveredCategories.setText("暂无");
            }
        });
    }

    /**
     * 显示添加训练计划对话框（升级版）
     */
    private void showAddPlanDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_plan, null);
        TextInputEditText etName = dialogView.findViewById(R.id.et_plan_name);
        TextInputEditText etDescription = dialogView.findViewById(R.id.et_plan_description);
        TextInputEditText etSets = dialogView.findViewById(R.id.et_sets);
        TextInputEditText etReps = dialogView.findViewById(R.id.et_reps);
        TextInputEditText etCategory = dialogView.findViewById(R.id.et_plan_category);
        com.google.android.material.chip.ChipGroup chipDaysGroup = dialogView.findViewById(R.id.chip_days_group);
        MaterialButton btnAddMedia = dialogView.findViewById(R.id.btn_add_media);
        ImageView ivMediaPreview = dialogView.findViewById(R.id.iv_media_preview);

        // 重置选中的媒体
        selectedMediaUri = null;
        currentMediaPreview = ivMediaPreview;

        // 添加演示按钮点击
        btnAddMedia.setOnClickListener(v -> {
            // 检查权限
            if (!PermissionHelper.hasMediaPermission(requireContext())) {
                PermissionHelper.requestMediaPermission(requireActivity());
                return;
            }

            // 打开文档选择器 (SAF) - 使用 ACTION_OPEN_DOCUMENT 以获取持久化权限
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); // 先设为 */*
            String[] mimeTypes = { "image/jpeg", "image/png", "image/jpg" }; // 明确指定只支持 JPG/PNG
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

            // 关键：添加权限标志
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try {
                imagePickerLauncher.launch(intent);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), "无法打开相册: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("添加训练计划")
                .setView(dialogView)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String description = etDescription.getText().toString().trim();
                    String setsStr = etSets.getText().toString().trim();
                    String repsStr = etReps.getText().toString().trim();
                    String category = etCategory.getText().toString().trim();

                    // 获取选择的日期
                    StringBuilder scheduledDaysBuilder = new StringBuilder();
                    for (int i = 0; i < chipDaysGroup.getChildCount(); i++) {
                        com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) chipDaysGroup
                                .getChildAt(i);
                        if (chip.isChecked()) {
                            if (scheduledDaysBuilder.length() > 0) {
                                scheduledDaysBuilder.append(",");
                            }
                            scheduledDaysBuilder.append(chip.getTag());
                        }
                    }
                    String scheduledDays = scheduledDaysBuilder.toString();
                    if (scheduledDays.isEmpty()) {
                        scheduledDays = "0"; // 默认每天
                    }

                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "请输入计划名称", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        int sets = setsStr.isEmpty() ? 0 : Integer.parseInt(setsStr);
                        int reps = repsStr.isEmpty() ? 0 : Integer.parseInt(repsStr);

                        // 创建完整训练计划
                        TrainingPlan plan = new TrainingPlan(
                                name,
                                description,
                                System.currentTimeMillis(),
                                sets,
                                reps,
                                selectedMediaUri);

                        // 设置 2.0 新字段
                        if (!category.isEmpty()) {
                            plan.setCategory(category);
                        }
                        plan.setScheduledDays(scheduledDays);

                        viewModel.addPlan(plan);
                        Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();

                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * Plan 10: 显示编辑计划对话框
     */
    private void showEditPlanDialog(TrainingPlan plan) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_plan, null);

        TextInputEditText etPlanName = dialogView.findViewById(R.id.et_plan_name);
        TextInputEditText etCategory = dialogView.findViewById(R.id.et_category);
        TextInputEditText etSets = dialogView.findViewById(R.id.et_sets);
        TextInputEditText etReps = dialogView.findViewById(R.id.et_reps);

        // 填充现有数据
        etPlanName.setText(plan.getName());
        etCategory.setText(plan.getCategory());
        etSets.setText(String.valueOf(plan.getSets()));
        etReps.setText(String.valueOf(plan.getReps()));

        // Plan 16: 初始化当前图片状态
        selectedMediaUri = plan.getMediaUri();
        ImageView ivEditMediaPreview = dialogView.findViewById(R.id.iv_edit_media_preview);
        currentMediaPreview = ivEditMediaPreview; // 绑定预览视图

        if (!android.text.TextUtils.isEmpty(selectedMediaUri)) {
            ivEditMediaPreview.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(Uri.parse(selectedMediaUri))
                    .centerCrop()
                    .into(ivEditMediaPreview);
        } else {
            ivEditMediaPreview.setVisibility(View.GONE);
        }

        // Plan 26: 初始化排期 ChipGroup
        com.google.android.material.chip.ChipGroup chipGroup = dialogView.findViewById(R.id.chip_days_group);
        String scheduledDays = plan.getScheduledDays(); // "1,3,5"
        if (scheduledDays != null && !scheduledDays.isEmpty() && !scheduledDays.equals("0")) {
            String[] days = scheduledDays.split(",");
            for (String day : days) {
                try {
                    int dayIndex = Integer.parseInt(day.trim());
                    // Tag 是 1~7
                    for (int i = 0; i < chipGroup.getChildCount(); i++) {
                        com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) chipGroup
                                .getChildAt(i);
                        if (chip.getTag() != null && chip.getTag().toString().equals(String.valueOf(dayIndex))) {
                            chip.setChecked(true);
                            break;
                        }
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }

        // Plan 18: 重新绑定点击事件，并添加详细日志
        View btnEditMedia = dialogView.findViewById(R.id.btn_edit_media);
        if (btnEditMedia != null) {
            btnEditMedia.setOnClickListener(v -> {
                android.util.Log.d("PlanFragment", "EDIT_DIALOG: Edit Media Button Clicked!"); // 调试日志

                // 复用选择器
                currentMediaPreview = ivEditMediaPreview;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] mimeTypes = { "image/jpeg", "image/png", "image/jpg" };
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                try {
                    imagePickerLauncher.launch(intent);
                } catch (Exception e) {
                    android.util.Log.e("PlanFragment", "Launcher failed", e);
                    Toast.makeText(requireContext(), "启动相册失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("编辑训练计划")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String planName = etPlanName.getText().toString().trim();
                    String category = etCategory.getText().toString().trim();
                    String setsStr = etSets.getText().toString().trim();
                    String repsStr = etReps.getText().toString().trim();

                    if (planName.isEmpty()) {
                        Toast.makeText(getContext(), "请输入训练名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (setsStr.isEmpty() || repsStr.isEmpty()) {
                        Toast.makeText(getContext(), "请输入组数和次数", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        int sets = Integer.parseInt(setsStr);
                        int reps = Integer.parseInt(repsStr);

                        // 更新计划对象
                        plan.setName(planName);
                        plan.setCategory(category);
                        plan.setSets(sets);
                        plan.setReps(reps);

                        // Plan 26: 保存排期选择
                        StringBuilder daysBuilder = new StringBuilder();
                        List<Integer> selectedDays = new ArrayList<>();
                        for (int i = 0; i < chipGroup.getChildCount(); i++) {
                            com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) chipGroup
                                    .getChildAt(i);
                            if (chip.isChecked()) {
                                if (chip.getTag() != null) {
                                    selectedDays.add(Integer.parseInt(chip.getTag().toString()));
                                }
                            }
                        }
                        if (!selectedDays.isEmpty()) {
                            for (int i = 0; i < selectedDays.size(); i++) {
                                daysBuilder.append(selectedDays.get(i));
                                if (i < selectedDays.size() - 1) {
                                    daysBuilder.append(",");
                                }
                            }
                            plan.setScheduledDays(daysBuilder.toString());
                        } else {
                            plan.setScheduledDays("0"); // 0 代表每天/无限制
                        }

                        // Plan 19: 修复漏保存图片的问题
                        // 只有当 selectedMediaUri 不为空（有修改）或者需要清除时才设置
                        // 这里逻辑假定 selectedMediaUri 始终持有最新状态（初始为原图，修改后为新图）
                        plan.setMediaUri(selectedMediaUri);

                        viewModel.updatePlan(plan);
                        Toast.makeText(getContext(), "修改成功", Toast.LENGTH_SHORT).show();
                        plan.setName(planName);
                        plan.setCategory(category.isEmpty() ? null : category);
                        plan.setSets(sets);
                        plan.setReps(reps);

                        // 保存到数据库
                        viewModel.updatePlan(plan);
                        Toast.makeText(getContext(), "计划已更新", Toast.LENGTH_SHORT).show();

                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示重命名分类弹窗 (Plan 28)
     */
    private void showRenameCategoryDialog(String oldCategory) {
        // 构建简单的输入弹窗
        android.widget.EditText editText = new android.widget.EditText(requireContext());
        editText.setHint("输入新的分类名称");
        editText.setText(oldCategory);

        // 增加一点 Padding 看起来更好看
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(requireContext())
                .setTitle("修改分类名称")
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newCategory = editText.getText().toString().trim();
                    if (!newCategory.isEmpty() && !newCategory.equals(oldCategory)) {
                        viewModel.updateCategory(oldCategory, newCategory);
                        Toast.makeText(requireContext(), "分类更新成功", Toast.LENGTH_SHORT).show();
                    } else if (newCategory.isEmpty()) {
                        Toast.makeText(requireContext(), "分类名称不能为空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (PermissionHelper.onRequestPermissionsResult(requestCode, grantResults)) {
            Toast.makeText(getContext(), "权限已授予", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "需要相册权限才能选择图片", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
