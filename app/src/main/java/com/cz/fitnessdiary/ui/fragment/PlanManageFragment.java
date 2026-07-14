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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.navigation.fragment.NavHostFragment;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.FragmentPlanManageBinding;
import com.cz.fitnessdiary.ui.adapter.GroupedPlanAdapter;
import com.cz.fitnessdiary.utils.ExerciseMetTable;
import com.cz.fitnessdiary.utils.PermissionHelper;
import com.cz.fitnessdiary.viewmodel.PlanViewModel;
import com.cz.fitnessdiary.ui.guide.GuideStateManager;
import com.cz.fitnessdiary.ui.guide.GuideStep;
import com.cz.fitnessdiary.ui.guide.PageGuide;
import com.cz.fitnessdiary.ui.guide.TargetedGuideOverlay;

/**
 * 训练计划管理页面 - 迁移自原主页训练计划模块
 */
public class PlanManageFragment extends Fragment {
    private static final int MAX_VISIBLE_CATEGORY_CHIPS = 6;

    private FragmentPlanManageBinding binding;
    private PlanViewModel viewModel;
    private GroupedPlanAdapter adapter;
    
    // 探索计划库所需的适配器与模板列表
    private com.cz.fitnessdiary.ui.adapter.TemplateListAdapter basicAdapter;
    private com.cz.fitnessdiary.ui.adapter.TemplateListAdapter advancedAdapter;
    private List<com.cz.fitnessdiary.model.TrainingTemplate> allTemplates = new ArrayList<>();
    private com.cz.fitnessdiary.ui.adapter.PersonalPlanAdapter personalPlanAdapter;

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
                                try {
                                    requireContext().getContentResolver().takePersistableUriPermission(
                                            mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                } catch (SecurityException e) {
                                    e.printStackTrace();
                                }

                                selectedMediaUri = mediaUri.toString();
                                if (currentMediaPreview != null) {
                                    currentMediaPreview.setVisibility(View.VISIBLE);
                                    currentMediaPreview.setImageURI(mediaUri);
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
        binding = FragmentPlanManageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(PlanViewModel.class);

        // 顶栏返回按钮
        binding.btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        setupRecyclerView();
        setupTabLayout();
        setupExploreLibrary();
        setupPersonalPlans();
        observeViewModel();

        // 监听来自模板预览一键导入或AI生成计划完毕的通知，切回“当前计划”Tab
        getChildFragmentManager().setFragmentResultListener("plan_imported_request", getViewLifecycleOwner(), (requestKey, bundle) -> {
            if (binding.tabLayout.getTabAt(0) != null) {
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0));
            }
        });



        // FAB 点击分流：Tab 2 新建模板，Tab 0 添加动作
        binding.fabAddPlan.setOnClickListener(v -> {
            int tabPosition = binding.tabLayout.getSelectedTabPosition();
            if (tabPosition == 2) {
                showCreatePersonalPlanDialog();
            } else if (tabPosition == 0) {
                showAddPlanDialog();
            }
        });

        // 默认点击模板导入按钮切换到 Tab 1 (探索计划库)
        binding.btnTemplateImport.setOnClickListener(v -> {
            if (binding.tabLayout.getTabAt(1) != null) {
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(1));
            }
        });

        // 点击大卡片切换计划细分模式 (基础/进阶/自定义)
        binding.layoutHeroStats.setOnClickListener(v -> showModeSelectionDialog());

        // 初始化时尝试注入进阶计划
        viewModel.seedAdvancedPlans();
        setupPlanManageGuide();
    }

    /**
     * 初始化 TabLayout 切换
     */
    private void setupTabLayout() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("当前计划"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("探索计划库"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("个人计划"));

        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    binding.scrollCurrentPlan.setVisibility(View.VISIBLE);
                    binding.scrollExploreLibrary.setVisibility(View.GONE);
                    binding.scrollPersonalPlans.setVisibility(View.GONE);
                } else if (tab.getPosition() == 1) {
                    binding.scrollCurrentPlan.setVisibility(View.GONE);
                    binding.scrollExploreLibrary.setVisibility(View.VISIBLE);
                    binding.scrollPersonalPlans.setVisibility(View.GONE);
                } else {
                    binding.scrollCurrentPlan.setVisibility(View.GONE);
                    binding.scrollExploreLibrary.setVisibility(View.GONE);
                    binding.scrollPersonalPlans.setVisibility(View.VISIBLE);
                }
                updateFabVisibility();
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
    }

    /**
     * 初始化“探索计划库”的 RecyclerView 和过滤
     */
    private void setupExploreLibrary() {
        basicAdapter = new com.cz.fitnessdiary.ui.adapter.TemplateListAdapter();
        advancedAdapter = new com.cz.fitnessdiary.ui.adapter.TemplateListAdapter();

        binding.rvBasicTemplates.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        binding.rvBasicTemplates.setAdapter(basicAdapter);

        binding.rvAdvancedTemplates.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        binding.rvAdvancedTemplates.setAdapter(advancedAdapter);

        // 设置卡片点击预览监听
        com.cz.fitnessdiary.ui.adapter.TemplateListAdapter.OnTemplateClickListener clickListener = template -> {
            TemplatePreviewBottomSheetFragment preview =
                    TemplatePreviewBottomSheetFragment.newInstance(template);
            preview.show(getChildFragmentManager(), "TemplatePreviewBottomSheet");
        };
        basicAdapter.setOnTemplateClickListener(clickListener);
        advancedAdapter.setOnTemplateClickListener(clickListener);

        // AI 制定计划大横幅点击
        binding.cardAiWizard.setOnClickListener(v -> showAiPlanWizard());

        // 绑定 Chip 过滤逻辑
        binding.cgExploreFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            String goal = "全部";
            if (checkedIds.contains(R.id.chip_explore_bulking)) {
                goal = "增肌";
            } else if (checkedIds.contains(R.id.chip_explore_cutting)) {
                goal = "减脂";
            } else if (checkedIds.contains(R.id.chip_explore_shaping)) {
                goal = "塑形";
            }
            basicAdapter.setFilter(goal);
            advancedAdapter.setFilter(goal);
        });

        // 异步加载官方模板
        loadExploreTemplates();
    }

    private void loadExploreTemplates() {
        try {
            java.io.InputStreamReader reader = new java.io.InputStreamReader(
                    requireContext().getAssets().open("training_templates.json"), "UTF-8");
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<TemplateListWrapper>() {}.getType();
            TemplateListWrapper wrapper = new com.google.gson.Gson().fromJson(reader, type);
            reader.close();

            if (wrapper != null && wrapper.templates != null) {
                allTemplates = wrapper.templates;
                updateExploreAdapters();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateExploreAdapters() {
        List<com.cz.fitnessdiary.model.TrainingTemplate> basicList = new ArrayList<>();
        List<com.cz.fitnessdiary.model.TrainingTemplate> advancedList = new ArrayList<>();
        for (com.cz.fitnessdiary.model.TrainingTemplate t : allTemplates) {
            if (t.getDifficulty() == 1) {
                basicList.add(t);
            } else {
                advancedList.add(t);
            }
        }
        basicAdapter.setTemplates(basicList);
        advancedAdapter.setTemplates(advancedList);
    }

    private void showAiPlanWizard() {
        AiPlanWizardBottomSheetFragment.newInstance()
                .show(getChildFragmentManager(), "AiPlanWizard");
    }

    private static class TemplateListWrapper {
        List<com.cz.fitnessdiary.model.TrainingTemplate> templates;
    }

    /**
     * 显示训练模式选择弹窗
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
        adapter = new GroupedPlanAdapter();
        adapter.setOnPlanClickListener(new GroupedPlanAdapter.OnPlanClickListener() {
            @Override
            public void onPlanClick(TrainingPlan plan) {
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
        viewModel.getGroupedPlans().observe(getViewLifecycleOwner(), groupedPlans -> {
            if (groupedPlans != null && !groupedPlans.isEmpty()) {
                adapter.setGroupList(groupedPlans);
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.btnTemplateImport.setVisibility(View.GONE);
                binding.rvPlans.setVisibility(View.VISIBLE);
                updateHeroMetrics(groupedPlans);
            } else {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.btnTemplateImport.setVisibility(View.VISIBLE);
                binding.rvPlans.setVisibility(View.GONE);
                binding.tvWeeklyFrequency.setText("0 次/周");
                binding.tvBodyPartCount.setText("0 类");
                binding.tvActionCount.setText("0 个");
            }
        });

        viewModel.getTotalPlanCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null) {
                binding.tvPlanCount.setText(String.valueOf(count));
            } else {
                binding.tvPlanCount.setText("0");
            }
        });

        viewModel.getCoveredCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null && !categories.isEmpty()) {
                binding.tvCoveredCategories.setText(categories);
                updateCategoryChips(categories);
            } else {
                binding.tvCoveredCategories.setText("暂无提示");
                binding.cgCategories.removeAllViews();
            }
        });

        viewModel.getFilterMode().observe(getViewLifecycleOwner(), mode -> {
            String alias = mode;
            if ("基础".equals(mode)) alias = "初级官方";
            else if ("进阶".equals(mode)) alias = "中高级官方";
            else if ("自定义".equals(mode)) alias = "个人计划";

            binding.tvActivePlanLabel.setText("当前活跃计划 (" + alias + ")");
            updateEmptyStatePrompt(mode);
            updateFabVisibility();
        });

        // 观察个人计划模板名称列表
        viewModel.getPersonalPlanNames().observe(getViewLifecycleOwner(), planNames -> {
            String activeName = viewModel.getActivePersonalPlanName().getValue();
            updatePersonalPlansAdapter(planNames, activeName);
        });

        // 观察当前活跃的个人计划模板名称
        viewModel.getActivePersonalPlanName().observe(getViewLifecycleOwner(), activeName -> {
            List<String> planNames = viewModel.getPersonalPlanNames().getValue();
            updatePersonalPlansAdapter(planNames, activeName);
        });
    }

    private void updateHeroMetrics(List<com.cz.fitnessdiary.model.PlanGroup> groupedPlans) {
        if (groupedPlans == null || groupedPlans.isEmpty()) {
            binding.tvWeeklyFrequency.setText("0 次/周");
            binding.tvBodyPartCount.setText("0 类");
            binding.tvActionCount.setText("0 个");
            return;
        }

        int bodyPartCount = groupedPlans.size();
        int actionCount = 0;
        int weeklyFrequency = 0;

        for (com.cz.fitnessdiary.model.PlanGroup group : groupedPlans) {
            if (group == null || group.getPlans() == null) {
                continue;
            }
            actionCount += group.getPlans().size();

            for (TrainingPlan plan : group.getPlans()) {
                if (plan == null) {
                    continue;
                }
                String days = plan.getScheduledDays();
                if ("none".equals(days)) {
                    // 真正不排期，不加入频率统计
                    continue;
                }
                if (days == null || days.trim().isEmpty() || "0".equals(days.trim())) {
                    weeklyFrequency += 7;
                    continue;
                }
                java.util.Set<String> uniqueDays = new java.util.HashSet<>();
                for (String day : days.split(",")) {
                    String d = day == null ? "" : day.trim();
                    if (d.matches("[1-7]")) {
                        uniqueDays.add(d);
                    }
                }
                weeklyFrequency += uniqueDays.isEmpty() ? 7 : uniqueDays.size();
            }
        }

        binding.tvWeeklyFrequency.setText(weeklyFrequency + " 次/周");
        binding.tvBodyPartCount.setText(bodyPartCount + " 类");
        binding.tvActionCount.setText(actionCount + " 个");
    }

    private void updateCategoryChips(String categoriesStr) {
        binding.cgCategories.removeAllViews();
        if (categoriesStr == null || categoriesStr.trim().isEmpty()) {
            return;
        }

        String[] parts = categoriesStr.split("、");
        int shown = 0;
        for (String part : parts) {
            if (part.trim().isEmpty())
                continue;
            if (shown >= MAX_VISIBLE_CATEGORY_CHIPS) {
                break;
            }

            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
            chip.setText(part.trim());
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(28f);
            chip.setChipStartPadding(10f);
            chip.setChipEndPadding(10f);
            chip.setTextAppearance(R.style.TextAppearance_App_LabelSmall);
            int bg = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.plan_blue_container);
            int text = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary);
            int stroke = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.plan_blue_primary);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bg));
            chip.setTextColor(text);
            chip.setChipStrokeWidth(1f);
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(stroke));
            chip.setClickable(false);
            binding.cgCategories.addView(chip);
            shown++;
        }

        int hidden = parts.length - shown;
        if (hidden > 0) {
            com.google.android.material.chip.Chip moreChip = new com.google.android.material.chip.Chip(requireContext());
            moreChip.setText("+" + hidden);
            moreChip.setEnsureMinTouchTargetSize(false);
            moreChip.setChipMinHeight(28f);
            moreChip.setChipStartPadding(10f);
            moreChip.setChipEndPadding(10f);
            int bg = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.plan_blue_primary);
            moreChip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bg));
            moreChip.setTextColor(android.graphics.Color.WHITE);
            moreChip.setClickable(false);
            binding.cgCategories.addView(moreChip);
        }
    }

    private void showAddPlanChoiceDialog() {
        String[] options = { "新建空白计划", "探索官方模板", "AI 智能制定计划" };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加训练计划")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showAddPlanDialog();
                    } else if (which == 1) {
                        if (binding.tabLayout.getTabAt(1) != null) {
                            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(1));
                        }
                    } else {
                        showAiPlanWizard();
                    }
                })
                .show();
    }

    private void showTemplateList() {
        TemplateListBottomSheetFragment.newInstance()
                .show(getChildFragmentManager(), "TemplateListBottomSheet");
    }

    private void showAddPlanDialog() {
        AddPlanBottomSheetFragment.newInstance(null)
                .show(getChildFragmentManager(), "AddPlanBottomSheet");
    }

    private void showEditPlanDialog(TrainingPlan plan) {
        AddPlanBottomSheetFragment.newInstance(plan)
                .show(getChildFragmentManager(), "EditPlanBottomSheet");
    }

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

    private void setupPersonalPlans() {
        personalPlanAdapter = new com.cz.fitnessdiary.ui.adapter.PersonalPlanAdapter();
        binding.rvPersonalPlans.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPersonalPlans.setAdapter(personalPlanAdapter);

        binding.btnMergePlans.setOnClickListener(v -> showMergePlansDialog());
        binding.btnWeightManager.setOnClickListener(v -> showWeightManagerDialog());
    }

    private void updatePersonalPlansAdapter(List<String> planNames, String activeName) {
        if (planNames == null || planNames.isEmpty()) {
            binding.layoutPersonalEmpty.setVisibility(View.VISIBLE);
            binding.rvPersonalPlans.setVisibility(View.GONE);
        } else {
            binding.layoutPersonalEmpty.setVisibility(View.GONE);
            binding.rvPersonalPlans.setVisibility(View.VISIBLE);

            // 有2个以上计划时显示合并功能；有任意计划时显示负重管理
            final boolean canMerge = planNames.size() >= 2;
            binding.btnMergePlans.setVisibility(canMerge ? View.VISIBLE : View.GONE);
            binding.btnWeightManager.setVisibility(planNames.size() > 0 ? View.VISIBLE : View.GONE);

            personalPlanAdapter.setData(planNames, activeName, new com.cz.fitnessdiary.ui.adapter.PersonalPlanAdapter.OnPlanActionListener() {
                @Override
                public void onSelect(String planName) {
                    viewModel.setActivePersonalPlanName(planName);
                    viewModel.setFilterMode("自定义");
                    if (binding.tabLayout.getTabAt(0) != null) {
                        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0));
                    }
                    Toast.makeText(requireContext(), "已激活个人计划: " + planName, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onDelete(String planName) {
                    if (planName.equals(activeName)) {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("无法删除")
                                .setMessage("个人计划“" + planName + "”当前正在执行中。\n如需删除，请先激活其它计划模板后再进行删除。")
                                .setPositiveButton("我知道了", null)
                                .show();
                        return;
                    }

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("删除计划")
                            .setMessage("确定要删除个人计划“" + planName + "”及其中所有的动作吗？删除后不可恢复。")
                            .setPositiveButton("删除", (dialog, which) -> {
                                viewModel.deletePersonalPlan(planName);
                                Toast.makeText(requireContext(), "计划“" + planName + "”已删除", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }

                @Override
                public void onRename(String planName) {
                    android.widget.EditText input = new android.widget.EditText(requireContext());
                    input.setText(planName);
                    input.setSelection(planName.length());
                    input.setSingleLine(true);
                    int padding = (int) (16 * getResources().getDisplayMetrics().density);
                    android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
                    container.setPadding(padding, padding / 2, padding, 0);
                    container.addView(input);

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("修改计划名称")
                            .setView(container)
                            .setPositiveButton("保存", (dialog, which) -> {
                                String newName = input.getText().toString().trim();
                                if (newName.isEmpty()) {
                                    Toast.makeText(requireContext(), "计划名称不能为空", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                List<String> existing = viewModel.getPersonalPlanNames().getValue();
                                if (existing != null && existing.contains(newName) && !newName.equals(planName)) {
                                    Toast.makeText(requireContext(), "该计划名称已存在，请换一个名字", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                viewModel.renamePersonalPlan(planName, newName);
                                Toast.makeText(requireContext(), "计划重命名成功", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }

                @Override
                public int getActionCount(String planName) {
                    return viewModel.getActionCountForPersonalPlan(planName);
                }
            });
        }
    }

    /**
     * 合并计划对话框：多选源计划 → 选择一个目标计划 → 智能合并
     */
    private void showMergePlansDialog() {
        List<String> planNames = viewModel.getPersonalPlanNames().getValue();
        if (planNames == null || planNames.size() < 2) return;

        String[] names = planNames.toArray(new String[0]);
        boolean[] checked = new boolean[names.length];
        final int[] targetIdx = {0};

        // 构建勾选列表
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, 0, pad, pad);

        TextView tvHint = new TextView(requireContext());
        tvHint.setText("勾选要合并的源计划（可多选），再选择一个目标计划");
        tvHint.setTextSize(12);
        tvHint.setTextColor(0xFF888888);
        tvHint.setPadding(0, 0, 0, pad);
        container.addView(tvHint);

        // 每个计划一行：CheckBox + 目标单选
        List<android.widget.CheckBox> cbs = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, 4, 0, 4);

            android.widget.CheckBox cb = new android.widget.CheckBox(requireContext());
            cb.setText(names[i]);
            cb.setTextSize(14);
            cbs.add(cb);

            com.google.android.material.button.MaterialButton btnTarget =
                    new com.google.android.material.button.MaterialButton(requireContext());
            btnTarget.setText("→ 目标");
            btnTarget.setTextSize(10);
            btnTarget.setPadding(0, 0, 0, 0);
            btnTarget.setMinimumWidth(0);
            btnTarget.setMinimumHeight(0);
            btnTarget.setTextColor(idx == targetIdx[0] ? 0xFF4CAF50 : 0xFF888888);
            btnTarget.setOnClickListener(v -> {
                targetIdx[0] = idx;
                // 刷新所有目标按钮颜色
                for (int j = 0; j < cbs.size(); j++) {
                    ViewGroup parent = (ViewGroup) cbs.get(j).getParent();
                    for (int k = 0; k < parent.getChildCount(); k++) {
                        View child = parent.getChildAt(k);
                        if (child instanceof com.google.android.material.button.MaterialButton
                                && !(child instanceof android.widget.CheckBox)) {
                            ((com.google.android.material.button.MaterialButton) child)
                                    .setTextColor(j == idx ? 0xFF4CAF50 : 0xFF888888);
                        }
                    }
                }
            });
            row.addView(cb, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(btnTarget);
            container.addView(row);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("📋 合并计划")
                .setView(container)
                .setPositiveButton("执行合并", (d, w) -> {
                    List<String> sources = new ArrayList<>();
                    for (int i = 0; i < cbs.size(); i++) {
                        if (cbs.get(i).isChecked() && i != targetIdx[0]) {
                            sources.add(names[i]);
                        }
                    }
                    if (sources.isEmpty()) {
                        Toast.makeText(requireContext(), "请至少勾选一个源计划", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String target = names[targetIdx[0]];
                    // 二次确认
                    StringBuilder msg = new StringBuilder("将以下计划合并到「" + target + "」：\n");
                    for (String s : sources) msg.append("• ").append(s).append("\n");
                    msg.append("\n动作部位将通过名称智能识别，合并后源计划将被清空。");
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("确认合并")
                            .setMessage(msg.toString())
                            .setPositiveButton("确认合并", (d2, w2) -> {
                                viewModel.mergePersonalPlans(sources, target);
                                Toast.makeText(requireContext(),
                                        "已合并 " + sources.size() + " 个计划到「" + target + "」", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 统一负重管理对话框：列出所有器械动作，批量设置重量
     */
    private void showWeightManagerDialog() {
        new Thread(() -> {
            List<TrainingPlan> allPlans = AppDatabase.getInstance(requireContext().getApplicationContext())
                    .trainingPlanDao().getAllPlansList();
            if (allPlans == null || allPlans.isEmpty()) return;

            // 只筛选当前激活计划的动作（按分类前缀过滤）
            String activePlan = viewModel.getActivePersonalPlanName().getValue();
            String prefix = "自定义-" + (activePlan != null ? activePlan : "默认自定义计划") + "-";
            String currentMode = viewModel.getFilterMode().getValue();
            if (!"自定义".equals(currentMode)) {
                prefix = (currentMode != null ? currentMode : "基础") + "-";
            }

            // 筛选需要负重的动作（含器械关键词或 weight>0 且在激活计划中）
            List<TrainingPlan> equipmentPlans = new ArrayList<>();
            for (TrainingPlan p : allPlans) {
                if (p.getCategory() == null || !p.getCategory().startsWith(prefix)) continue;
                if (ExerciseMetTable.isEquipmentExercise(p.getName(), p.getCategory())
                        || p.getWeight() > 0) {
                    equipmentPlans.add(p);
                }
            }
            final List<TrainingPlan> finalPlans = equipmentPlans;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (finalPlans.isEmpty()) {
                        Toast.makeText(requireContext(), "暂无需要设置负重的动作", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    LinearLayout container = new LinearLayout(requireContext());
                    container.setOrientation(LinearLayout.VERTICAL);
                    int pad = (int) (12 * getResources().getDisplayMetrics().density);

                    TextView tvHint = new TextView(requireContext());
                    tvHint.setText("设置器械动作的负重（哑铃=单只重 / 杠铃=单边重 / 器械=配重片重量）");
                    tvHint.setTextSize(12);
                    tvHint.setTextColor(0xFF888888);
                    tvHint.setPadding(0, 0, 0, pad);
                    container.addView(tvHint);

                    List<EditText> weightInputs = new ArrayList<>();
                    for (TrainingPlan plan : finalPlans) {
                        LinearLayout row = new LinearLayout(requireContext());
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        row.setPadding(0, 4, 0, 4);

                        TextView tvName = new TextView(requireContext());
                        tvName.setText(plan.getName());
                        tvName.setTextSize(14);
                        tvName.setTextColor(0xFF333333);
                        tvName.setLayoutParams(new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                        row.addView(tvName);

                        EditText et = new EditText(requireContext());
                        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                        float displayWeight = com.cz.fitnessdiary.utils.UnitUtils.convertWeight(plan.getWeight(), com.cz.fitnessdiary.utils.UnitUtils.getWeightUnit(requireContext()));
                        et.setText(displayWeight > 0 ? String.format(java.util.Locale.getDefault(), "%.1f", displayWeight).replace(".0", "") : "");
                        et.setHint(com.cz.fitnessdiary.utils.UnitUtils.getWeightUnitSymbol(requireContext()));
                        et.setTextSize(14);
                        et.setWidth((int) (80 * getResources().getDisplayMetrics().density));
                        et.setGravity(android.view.Gravity.CENTER);
                        row.addView(et);
                        weightInputs.add(et);

                        container.addView(row);
                    }

                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("⚖️ 负重管理 (" + finalPlans.size() + " 个动作)")
                            .setView(container)
                            .setPositiveButton("保存全部", (d, w) -> {
                                new Thread(() -> {
                                    for (int i = 0; i < finalPlans.size(); i++) {
                                        String s = weightInputs.get(i).getText().toString().trim();
                                        float newWeight = 0f;
                                        try { newWeight = Float.parseFloat(s); } catch (NumberFormatException ignored) {}
                                        float kgWeight = com.cz.fitnessdiary.utils.UnitUtils.convertToKg(newWeight, requireContext());
                                        if (Math.abs(kgWeight - finalPlans.get(i).getWeight()) > 0.05f) {
                                            finalPlans.get(i).setWeight(kgWeight);
                                            viewModel.updatePlan(finalPlans.get(i));
                                        }
                                    }
                                }).start();
                                Toast.makeText(requireContext(), "负重设置已保存", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                });
            }
        }).start();
    }

    private void updateEmptyStatePrompt(String mode) {
        if (binding == null) return;
        if ("自定义".equals(mode)) {
            binding.tvEmptyTitle.setText("暂无当前执行计划");
            binding.tvEmptySubtitle.setText("请前往“个人计划”选择或创建一个模板执行吧！");
            binding.btnTemplateImport.setText("前往个人计划");
            binding.btnTemplateImport.setOnClickListener(v -> {
                if (binding.tabLayout.getTabAt(2) != null) {
                    binding.tabLayout.selectTab(binding.tabLayout.getTabAt(2));
                }
            });
        } else {
            binding.tvEmptyTitle.setText("还没有训练计划");
            binding.tvEmptySubtitle.setText("导入模板计划或使用 AI 制定属于你的计划吧！");
            binding.btnTemplateImport.setText("前往探索计划库");
            binding.btnTemplateImport.setOnClickListener(v -> {
                if (binding.tabLayout.getTabAt(1) != null) {
                    binding.tabLayout.selectTab(binding.tabLayout.getTabAt(1));
                }
            });
        }
    }

    private void showCreatePersonalPlanDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("输入计划名字，如：我的一周二分化");
        input.setSingleLine(true);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("新建训练计划")
                .setView(container)
                .setPositiveButton("确定", (dialog, which) -> {
                    String planName = input.getText().toString().trim();
                    if (planName.isEmpty()) {
                        Toast.makeText(requireContext(), "计划名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 检查是否重名
                    List<String> existing = viewModel.getPersonalPlanNames().getValue();
                    if (existing != null && existing.contains(planName)) {
                        Toast.makeText(requireContext(), "该计划名称已存在，请换一个名字", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 插入一个占位动作以实例化该模板
                    long now = System.currentTimeMillis();
                    TrainingPlan placeholder = new TrainingPlan("点击右上角添加动作", "新自定义计划的起点", now, 3, 12, null);
                    placeholder.setCategory("自定义-" + planName + "-未分类");
                    placeholder.setScheduledDays("1,3,5");
                    viewModel.addPlan(placeholder);

                    // 设为当前活跃个人计划
                    viewModel.setActivePersonalPlanName(planName);
                    viewModel.setFilterMode("自定义");

                    // 自动跳回 Tab 0
                    if (binding.tabLayout.getTabAt(0) != null) {
                        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0));
                    }
                    Toast.makeText(requireContext(), "个人计划“" + planName + "”创建成功", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateFabVisibility() {
        if (viewModel == null || binding == null) return;
        int tabPosition = binding.tabLayout.getSelectedTabPosition();
        String mode = viewModel.getFilterMode().getValue();
        if (tabPosition == 2 || (tabPosition == 0 && "自定义".equals(mode))) {
            binding.fabAddPlan.show();
        } else {
            binding.fabAddPlan.hide();
        }
    }

    private void setupPlanManageGuide() {
        // 计划管理页无需气泡引导
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
