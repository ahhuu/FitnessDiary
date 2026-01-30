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

        // [v1.2] 顶部活跃计划区域点击弹出模式选择
        binding.layoutHeroStats.setOnClickListener(v -> showModeSelectionDialog());

        // 初始化时尝试注入进阶计划 (如果不存在)
        viewModel.seedAdvancedPlans();
    }

    /**
     * [v1.2] 显示训练模式选择弹窗
     */
    private void showModeSelectionDialog() {
        String[] modes = { "基础训练计划", "进阶训练计划", "自定义训练计划" };
        String currentMode = viewModel.getFilterMode().getValue();
        int checkedItem = 0;
        if ("进阶".equals(currentMode))
            checkedItem = 1;
        if ("自定义".equals(currentMode))
            checkedItem = 2;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择训练种类")
                .setSingleChoiceItems(modes, checkedItem, (dialog, which) -> {
                    String selectedModeStr = "基础";
                    if (which == 1)
                        selectedModeStr = "进阶";
                    if (which == 2)
                        selectedModeStr = "自定义";

                    viewModel.setFilterMode(selectedModeStr);
                    Toast.makeText(requireContext(), "已切换至 " + modes[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
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
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.rvPlans.setVisibility(View.VISIBLE);
            } else {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
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
                updateCategoryChips(categories);
            } else {
                binding.tvCoveredCategories.setText("暂无提示");
                binding.cgCategories.removeAllViews();
            }
        });
    }

    /**
     * Plan 35: 动态生成部位标签 (Chips)
     */
    private void updateCategoryChips(String categoriesStr) {
        binding.cgCategories.removeAllViews();
        String[] parts = categoriesStr.split("、");
        for (String part : parts) {
            if (part.trim().isEmpty())
                continue;

            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
            chip.setText(part.trim());
            chip.setChipMinHeight(0);
            chip.setChipStartPadding(12f);
            chip.setChipEndPadding(12f);
            chip.setTextAppearance(R.style.TextAppearance_App_LabelSmall);
            // 绿底白字适配经典布局
            int primaryColor = androidx.core.content.ContextCompat.getColor(requireContext(),
                    R.color.fitnessdiary_primary);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(primaryColor));
            chip.setTextColor(android.graphics.Color.WHITE);
            chip.setChipStrokeWidth(0);
            binding.cgCategories.addView(chip);
        }
    }

    /**
     * 显示添加训练计划对话框（底栏版）
     */
    private void showAddPlanDialog() {
        AddPlanBottomSheetFragment.newInstance(null)
                .show(getChildFragmentManager(), "AddPlanBottomSheet");
    }

    /**
     * 显示编辑计划对话框（底栏版）
     */
    private void showEditPlanDialog(TrainingPlan plan) {
        AddPlanBottomSheetFragment.newInstance(plan)
                .show(getChildFragmentManager(), "EditPlanBottomSheet");
    }

    /**
     * 显示重命名分类弹窗 (Material 3 版)
     */
    private void showRenameCategoryDialog(String oldCategory) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_input_simple, null);
        com.google.android.material.textfield.TextInputEditText input = dialogView.findViewById(R.id.edit_text);
        com.google.android.material.textfield.TextInputLayout layout = dialogView.findViewById(R.id.text_input_layout);

        layout.setHint("分类名称");
        input.setText(oldCategory);
        input.setSelection(oldCategory.length());

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("修改分类名称")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newCategory = input.getText().toString().trim();
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
