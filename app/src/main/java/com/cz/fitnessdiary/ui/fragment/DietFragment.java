package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.databinding.FragmentDietBinding;
import com.cz.fitnessdiary.ui.adapter.FoodAutoCompleteAdapter;
import com.cz.fitnessdiary.viewmodel.DietViewModel;
import com.cz.fitnessdiary.database.entity.User;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 饮食记录页面 - 2.0 智能化完整版
 * 核心功能：食物库联想、自动热量计算、智能反馈
 * Refactored for Plan 12: Grid Layout & Context-Aware Add
 */
public class DietFragment extends Fragment {

    private FragmentDietBinding binding;
    private DietViewModel viewModel;
    private ExecutorService executorService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentDietBinding.inflate(inflater, container, false);
        executorService = Executors.newSingleThreadExecutor();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(DietViewModel.class);

        setupViews();
        observeViewModel();
    }

    private void setupViews() {
        // 设置搜索卡片点击
        binding.cardFoodWiki.setOnClickListener(v -> showFoodWikiDialog());

        // 绑定卡片添加按钮监听
        setupCardListeners();
    }

    /**
     * 显示食物百科全屏搜索页
     */
    private void showFoodWikiDialog() {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(),
                android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        View view = getLayoutInflater().inflate(R.layout.dialog_food_wiki, null);
        dialog.setContentView(view);

        android.widget.EditText etSearch = view.findViewById(R.id.et_search_query);
        View btnBack = view.findViewById(R.id.btn_back);
        androidx.recyclerview.widget.RecyclerView rvResults = view.findViewById(R.id.rv_food_results);

        btnBack.setOnClickListener(v -> dialog.dismiss());

        // Setup Adapter (Plan 30: 使用分组适配器)
        com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter adapter = new com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter();
        rvResults.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        rvResults.setAdapter(adapter);

        adapter.setOnItemClickListener(food -> {
            // 点击食物 -> 弹出“添加到”选择框
            String[] mealOptions = { "早餐", "午餐", "晚餐", "加餐" };

            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("将 " + food.getName() + " 添加到...")
                    .setItems(mealOptions, (dialogInterface, which) -> {
                        // which match the mealType int (0=Breakfast, 1=Lunch, 2=Dinner, 3=Snack)
                        dialog.dismiss(); // 关闭百科页面

                        // 打开添加弹窗，并选中对应的餐点类型，同时自动填入食物信息
                        showSmartAddFoodDialog(which, food);
                    })
                    .show();
        });

        // Load initial data (Plan 30: 使用 getAllFoodsSync 无限制加载所有食物)
        executorService.execute(() -> {
            List<FoodLibrary> allFoods = viewModel.getAllFoodsSync();
            requireActivity().runOnUiThread(() -> adapter.setFoodList(allFoods));
        });

        // Search listener
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                executorService.execute(() -> {
                    List<FoodLibrary> results = viewModel.searchFoods(query);
                    requireActivity().runOnUiThread(() -> adapter.setFoodList(results));
                });
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        // Plan 32: FAB 添加自定义食物
        com.google.android.material.floatingactionbutton.FloatingActionButton fabAddFood = view
                .findViewById(R.id.fab_add_food);
        fabAddFood.setOnClickListener(v -> {
            showAddCustomFoodDialog(dialog, adapter);
        });

        dialog.show();
    }

    /**
     * Plan 32: 显示添加自定义食物对话框
     */
    private void showAddCustomFoodDialog(android.app.Dialog parentDialog,
            com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter adapter) {

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_custom_food, null);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(
                requireContext());
        builder.setView(dialogView);

        androidx.appcompat.app.AlertDialog addDialog = builder.create();

        // 获取输入控件
        com.google.android.material.textfield.TextInputEditText etFoodName = dialogView.findViewById(R.id.et_food_name);
        com.google.android.material.textfield.TextInputEditText etCalories = dialogView.findViewById(R.id.et_calories);
        com.google.android.material.textfield.TextInputEditText etProtein = dialogView.findViewById(R.id.et_protein);
        com.google.android.material.textfield.TextInputEditText etCarbs = dialogView.findViewById(R.id.et_carbs);
        com.google.android.material.textfield.TextInputEditText etServingUnit = dialogView
                .findViewById(R.id.et_serving_unit);
        com.google.android.material.textfield.TextInputEditText etWeightPerUnit = dialogView
                .findViewById(R.id.et_weight_per_unit);
        AutoCompleteTextView spinnerCategory = dialogView.findViewById(R.id.spinner_category);

        // 设置分类下拉
        String[] categories = {
                "主食 (Staples)",
                "家常菜 (Dishes)",
                "优质蛋白质 (Protein)",
                "蔬菜 & 水果 (Veg & Fruits)",
                "零食饮品 (Snacks & Drinks)",
                "其他"
        };
        android.widget.ArrayAdapter<String> categoryAdapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, categories);
        spinnerCategory.setAdapter(categoryAdapter);
        spinnerCategory.setText(categories[5], false); // 默认选择"其他"

        // 取消按钮
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> addDialog.dismiss());

        // 保存按钮
        dialogView.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String name = etFoodName.getText() != null ? etFoodName.getText().toString().trim() : "";
            String caloriesStr = etCalories.getText() != null ? etCalories.getText().toString().trim() : "";
            String proteinStr = etProtein.getText() != null ? etProtein.getText().toString().trim() : "";
            String carbsStr = etCarbs.getText() != null ? etCarbs.getText().toString().trim() : "";
            String servingUnit = etServingUnit.getText() != null ? etServingUnit.getText().toString().trim() : "";
            String weightStr = etWeightPerUnit.getText() != null ? etWeightPerUnit.getText().toString().trim() : "";
            String category = spinnerCategory.getText().toString().trim();

            // 验证必填字段
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "请输入食物名称", Toast.LENGTH_SHORT).show();
                return;
            }
            if (caloriesStr.isEmpty()) {
                Toast.makeText(requireContext(), "请输入热量", Toast.LENGTH_SHORT).show();
                return;
            }

            // 解析数值
            int calories;
            try {
                calories = (int) Double.parseDouble(caloriesStr);
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "热量格式错误", Toast.LENGTH_SHORT).show();
                return;
            }

            double protein = 0;
            if (!proteinStr.isEmpty()) {
                try {
                    protein = Double.parseDouble(proteinStr);
                } catch (NumberFormatException ignored) {
                }
            }

            double carbs = 0;
            if (!carbsStr.isEmpty()) {
                try {
                    carbs = Double.parseDouble(carbsStr);
                } catch (NumberFormatException ignored) {
                }
            }

            int weightPerUnit = 100; // 默认100g
            if (!weightStr.isEmpty()) {
                try {
                    weightPerUnit = Integer.parseInt(weightStr);
                } catch (NumberFormatException ignored) {
                }
            }

            if (servingUnit.isEmpty()) {
                servingUnit = "份";
            }

            if (category.isEmpty()) {
                category = "其他";
            }

            // 创建食物对象并保存
            FoodLibrary newFood = new FoodLibrary(name, calories, protein, carbs,
                    servingUnit, weightPerUnit, category);
            viewModel.insertFood(newFood);

            Toast.makeText(requireContext(), "✅ 已添加: " + name, Toast.LENGTH_SHORT).show();
            addDialog.dismiss();

            // 刷新列表
            executorService.execute(() -> {
                List<FoodLibrary> allFoods = viewModel.getAllFoodsSync();
                requireActivity().runOnUiThread(() -> adapter.setFoodList(allFoods));
            });
        });

        addDialog.show();
    }

    private void setupCardListeners() {
        // 早餐 (Type 0)
        binding.cardBreakfast.btnAddFood.setOnClickListener(v -> showSmartAddFoodDialog(0));
        // 午餐 (Type 1)
        binding.cardLunch.btnAddFood.setOnClickListener(v -> showSmartAddFoodDialog(1));
        // 晚餐 (Type 2)
        binding.cardDinner.btnAddFood.setOnClickListener(v -> showSmartAddFoodDialog(2));
        // 加餐 (Type 3)
        binding.cardSnack.btnAddFood.setOnClickListener(v -> showSmartAddFoodDialog(3));
    }

    /**
     * 观察 ViewModel 数据
     */
    private void observeViewModel() {
        // 用于缓存目标热量（避免重复读取）
        final int[] cachedTargetCalories = { 2000 }; // 默认值

        // 观察用户数据（获取目标热量）
        viewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null && user.getTargetCalories() > 0) {
                cachedTargetCalories[0] = user.getTargetCalories();
            }
        });

        // Plan 12: 观察餐段数据并更新卡片 (不再使用 RecyclerView)
        viewModel.getMealSections().observe(getViewLifecycleOwner(), sections -> {
            if (sections != null) {
                for (com.cz.fitnessdiary.model.MealSection section : sections) {
                    switch (section.getMealType()) {
                        case 0:
                            updateMealCard(binding.cardBreakfast.getRoot(), "☀️ 早餐", section);
                            break;
                        case 1:
                            updateMealCard(binding.cardLunch.getRoot(), "🌞 午餐", section);
                            break;
                        case 2:
                            updateMealCard(binding.cardDinner.getRoot(), "🌙 晚餐", section);
                            break;
                        case 3:
                            updateMealCard(binding.cardSnack.getRoot(), "🍪 加餐", section);
                            break;
                    }
                }
            }
        });

        // 观察今日总热量
        viewModel.getTodayTotalCalories().observe(getViewLifecycleOwner(), totalCalories -> {
            if (totalCalories != null) {
                binding.tvTotalCalories.setText(String.valueOf(totalCalories));

                // 使用动态获取的目标热量
                int targetCalories = cachedTargetCalories[0];
                int progress = (int) ((totalCalories * 100.0) / targetCalories);
                binding.progressCalories.setProgress(Math.min(progress, 100));

                // 更新副标题（显示目标）
                binding.tvCaloriesSubtitle.setText("千卡 · 目标 " + targetCalories);

                // 如果超过 100%，进度条变红
                if (progress > 100) {
                    binding.progressCalories.setIndicatorColor(
                            getResources().getColor(com.cz.fitnessdiary.R.color.error, null));
                } else {
                    binding.progressCalories.setIndicatorColor(
                            getResources().getColor(com.cz.fitnessdiary.R.color.color_success, null));
                }
            } else {
                binding.tvTotalCalories.setText("0");
                binding.progressCalories.setProgress(0);
                binding.tvCaloriesSubtitle.setText("千卡 · 目标 " + cachedTargetCalories[0]);
            }
        });

        // 观察蛋白质数据
        viewModel.getTodayTotalProtein().observe(getViewLifecycleOwner(), totalProtein -> {
            if (totalProtein != null) {
                int currentProtein = totalProtein.intValue();
                int targetProtein = 0;
                User user = viewModel.getCurrentUser().getValue();
                if (user != null) {
                    targetProtein = user.getTargetProtein();
                    if (targetProtein <= 0)
                        targetProtein = (int) (user.getWeight() * 1.5); // 默认估算
                }
                if (targetProtein <= 0)
                    targetProtein = 60; // 兜底默认值

                int progress = (int) ((currentProtein * 100.0) / targetProtein);
                binding.progressProtein.setProgress(Math.min(progress, 100));
                binding.tvProteinStatus.setText("蛋白质: " + currentProtein + "/" + targetProtein + "g");
            }
        });

        // 观察碳水数据
        viewModel.getTodayTotalCarbs().observe(getViewLifecycleOwner(), totalCarbs -> {
            if (totalCarbs != null) {
                int currentCarbs = totalCarbs.intValue();
                int targetCarbs = 0;
                User user = viewModel.getCurrentUser().getValue();
                if (user != null) {
                    targetCarbs = user.getTargetCarbs();
                    if (targetCarbs <= 0)
                        targetCarbs = 250; // 默认估算
                }
                if (targetCarbs <= 0)
                    targetCarbs = 250; // 兜底默认值

                int progress = (int) ((currentCarbs * 100.0) / targetCarbs);
                binding.progressCarbs.setProgress(Math.min(progress, 100));
                binding.tvCarbsStatus.setText("碳水: " + currentCarbs + "/" + targetCarbs + "g");
            }
        });
    }

    /**
     * Plan 12: 更新餐点卡片 UI
     */
    private void updateMealCard(View cardRoot, String title, com.cz.fitnessdiary.model.MealSection section) {
        TextView tvName = cardRoot.findViewById(R.id.tv_meal_name);
        TextView tvCalories = cardRoot.findViewById(R.id.tv_meal_calories);
        TextView tvSummary = cardRoot.findViewById(R.id.tv_food_summary);
        // ImageButton btnAdd = cardRoot.findViewById(R.id.btn_add_food); // 已在
        // setupViews 绑定

        tvName.setText(title);
        tvCalories.setText(section.getTotalCalories() + " 千卡");

        List<com.cz.fitnessdiary.database.entity.FoodRecord> records = section.getFoodRecords();
        if (records == null || records.isEmpty()) {
            tvSummary.setText("暂无记录");
        } else {
            StringBuilder sb = new StringBuilder();
            for (com.cz.fitnessdiary.database.entity.FoodRecord r : records) {
                sb.append(r.getFoodName()).append(", ");
            }
            if (sb.length() > 2)
                sb.setLength(sb.length() - 2);
            tvSummary.setText(sb.toString());
        }

        // 点击卡片查看详情（支持删除）
        cardRoot.setOnClickListener(v -> showMealDetailsDialog(title, records));
    }

    /**
     * 显示餐点详情对话框 (支持删除)
     */
    private void showMealDetailsDialog(String title, List<com.cz.fitnessdiary.database.entity.FoodRecord> records) {
        if (records == null || records.isEmpty()) {
            // 打开添加弹窗
            int mealType = 3;
            if (title.equals("早餐"))
                mealType = 0;
            else if (title.equals("午餐"))
                mealType = 1;
            else if (title.equals("晚餐"))
                mealType = 2;
            showSmartAddFoodDialog(mealType);
            return;
        }

        String[] items = new String[records.size()];
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());

        for (int i = 0; i < records.size(); i++) {
            com.cz.fitnessdiary.database.entity.FoodRecord r = records.get(i);
            String timeStr = timeFormat.format(new java.util.Date(r.getRecordDate()));
            items[i] = "• " + r.getFoodName() + " (" + r.getCalories() + "千卡)  " + timeStr;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(title + "详情")
                .setItems(items, (dialog, which) -> {
                    // 点击条目删除
                    com.cz.fitnessdiary.database.entity.FoodRecord recordToDelete = records.get(which);
                    new AlertDialog.Builder(requireContext())
                            .setTitle("删除记录")
                            .setMessage("确定要删除 " + recordToDelete.getFoodName() + " 吗？")
                            .setPositiveButton("删除", (d, w) -> viewModel.deleteFoodRecord(recordToDelete))
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setPositiveButton("添加更多", (dialog, which) -> {
                    int mealType = 3;
                    if (title.equals("早餐"))
                        mealType = 0;
                    else if (title.equals("午餐"))
                        mealType = 1;
                    else if (title.equals("晚餐"))
                        mealType = 2;
                    showSmartAddFoodDialog(mealType);
                })
                .setNeutralButton("关闭", null)
                .show();
    }

    /**
     * 显示智能添加食物对话框（支持预选餐类型）
     */
    private void showSmartAddFoodDialog(int preSelectedMealType) {
        showSmartAddFoodDialogInternal(preSelectedMealType, null);
    }

    /**
     * 显示智能添加食物对话框（支持预选餐类型和特定食物）
     */
    private void showSmartAddFoodDialog(int preSelectedMealType, FoodLibrary preSelectedFood) {
        showSmartAddFoodDialogInternal(preSelectedMealType, preSelectedFood);
    }

    /**
     * 显示智能添加食物对话框（支持食物库联想）
     */
    private void showSmartAddFoodDialog() {
        showSmartAddFoodDialogInternal(-1, null); // -1 表示无预选
    }

    /**
     * 智能添加食物对话框实现
     */
    private void showSmartAddFoodDialogInternal(int preSelectedMealType, FoodLibrary preSelectedFood) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_food, null);

        // 1. 初始化控件 (使用 MaterialAutoCompleteTextView)
        com.google.android.material.textfield.MaterialAutoCompleteTextView etFoodName = dialogView
                .findViewById(R.id.et_food_name);
        TextInputEditText etServings = dialogView.findViewById(R.id.et_servings);
        TextView tvUnit = dialogView.findViewById(R.id.tv_unit);
        TextView tvAutoCalories = dialogView.findViewById(R.id.tv_auto_calories);
        RadioGroup rgMealType = dialogView.findViewById(R.id.rg_meal_type);

        // 处理预选类型
        if (preSelectedMealType != -1) {
            switch (preSelectedMealType) {
                case 0:
                    rgMealType.check(R.id.rb_breakfast);
                    break;
                case 1:
                    rgMealType.check(R.id.rb_lunch);
                    break;
                case 2:
                    rgMealType.check(R.id.rb_dinner);
                    break;
                case 3:
                    rgMealType.check(R.id.rb_snack);
                    break;
            }
        } else {
            rgMealType.check(R.id.rb_snack);
        }

        // 2. 异步加载食物库并配置适配器
        final List<FoodLibrary> allFoodsCache = new ArrayList<>();

        executorService.execute(() -> {
            List<FoodLibrary> foods = viewModel.getAllFoodsSync();
            final List<FoodLibrary> safeFoods = foods != null ? foods : new ArrayList<>();
            if (foods != null) {
                allFoodsCache.addAll(foods);
            }

            requireActivity().runOnUiThread(() -> {
                // 使用自定义的 FoodAutoCompleteAdapter (支持"包含"搜索)
                com.cz.fitnessdiary.ui.adapter.FoodAutoCompleteAdapter adapter = new com.cz.fitnessdiary.ui.adapter.FoodAutoCompleteAdapter(
                        getContext(), safeFoods);
                etFoodName.setAdapter(adapter);

                // [核心修复] 如果有预选食物，直接填充并初始化
                if (preSelectedFood != null) {
                    etFoodName.setText(preSelectedFood.getName());
                    etFoodName.dismissDropDown(); // 填充后不显示下拉列表

                    // 手动设置数据
                    tvUnit.setText(preSelectedFood.getServingUnit());
                    updateAutoCaloriesSmart(preSelectedFood, etServings, tvAutoCalories);
                    etFoodName.setTag(preSelectedFood);
                }
            });
        });

        // 3. 选中监听 (自动填充热量信息, 兼容 Adapter 返回对象或 String)
        etFoodName.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            FoodLibrary selectedFood = null;

            if (item instanceof FoodLibrary) {
                selectedFood = (FoodLibrary) item;
            } else if (item instanceof String) {
                // Fallback catch
                String name = (String) item;
                for (FoodLibrary f : allFoodsCache) {
                    if (f.getName().equals(name)) {
                        selectedFood = f;
                        break;
                    }
                }
            }

            if (selectedFood != null) {
                // 更新单位
                if (selectedFood.getServingUnit() != null) {
                    tvUnit.setText(selectedFood.getServingUnit());
                }
                updateAutoCaloriesSmart(selectedFood, etServings, tvAutoCalories);
                etFoodName.setTag(selectedFood);
            }
        });

        // 4. 份数变化监听
        etServings.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 取出刚才保存的 tag
                Object tag = etFoodName.getTag();
                if (tag instanceof FoodLibrary) {
                    updateAutoCaloriesSmart((FoodLibrary) tag, etServings, tvAutoCalories);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("添加食物")
                .setView(dialogView)
                .setPositiveButton("添加", (dialog, which) -> {
                    String foodName = etFoodName.getText().toString().trim();
                    String servingsStr = etServings.getText().toString().trim();

                    if (foodName.isEmpty() || servingsStr.isEmpty()) {
                        Toast.makeText(getContext(), "请填写完整信息", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        float servings = Float.parseFloat(servingsStr);

                        // 获取餐点类型
                        int checkedId = rgMealType.getCheckedRadioButtonId();
                        int mealType = 3;
                        if (checkedId == R.id.rb_breakfast)
                            mealType = 0;
                        else if (checkedId == R.id.rb_lunch)
                            mealType = 1;
                        else if (checkedId == R.id.rb_dinner)
                            mealType = 2;

                        viewModel.addFoodRecordSmart(foodName, servings, mealType);
                        Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "请输入有效的份数", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 更新自动计算的热量显示 (Smart Version)
     */
    private void updateAutoCaloriesSmart(FoodLibrary food, TextInputEditText etServings, TextView tvCalories) {
        if (food == null) {
            tvCalories.setVisibility(View.GONE);
            return;
        }

        String servingsStr = etServings.getText().toString().trim();
        if (servingsStr.isEmpty()) {
            tvCalories.setVisibility(View.GONE);
            return;
        }

        try {
            float servings = Float.parseFloat(servingsStr);
            int weightPerUnit = food.getWeightPerUnit();

            // 计算热量: 份数 * 单份重量 * (每100g热量 / 100)
            int calories = (int) (servings * weightPerUnit * (food.getCaloriesPer100g() / 100.0));

            tvCalories.setText("热量: " + calories + " 千卡 (" + (int) (servings * weightPerUnit) + "g)");
            tvCalories.setVisibility(View.VISIBLE);
        } catch (NumberFormatException e) {
            tvCalories.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null) {
            executorService.shutdown();
        }
        binding = null;
    }
}
