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
import com.cz.fitnessdiary.utils.DateUtils;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DayViewDecorator;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.color.MaterialColors;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import androidx.core.content.ContextCompat;
import java.util.Set;
import java.util.HashSet;

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

        viewModel = new ViewModelProvider(requireActivity()).get(DietViewModel.class);

        setupViews();
        observeViewModel();
    }

    private void setupViews() {
        // 设置日期导航监听 (Plan 13)
        binding.btnPrevDay.setOnClickListener(v -> viewModel.toPreviousDay());
        binding.btnNextDay.setOnClickListener(v -> viewModel.toNextDay());
        binding.tvSelectedDate.setOnClickListener(v -> showDatePickerDialog());

        // 设置搜索卡片点击
        binding.cardFoodWiki.setOnClickListener(v -> showFoodWikiDialog());

        // 绑定卡片添加按钮监听
        setupCardListeners();
    }

    /**
     * 打开日历选择器 (Plan 13: 增强版 - Material 3 + 高亮)
     */
    private void showDatePickerDialog() {
        Long currentSelection = viewModel.getSelectedDate().getValue();
        if (currentSelection == null)
            currentSelection = System.currentTimeMillis();

        // 获取有记录的日期集合
        Set<Long> recordedDates = viewModel.getRecordedDates().getValue();
        if (recordedDates == null)
            recordedDates = new HashSet<>();

        // 创建装饰器：为有记录的日期添加绿色下划点
        final Set<Long> finalRecordedDates = recordedDates;
        DayViewDecorator decorator = new DayViewDecorator() {
            @Nullable
            @Override
            public Drawable getCompoundDrawableBottom(android.content.Context context, int year, int month, int day,
                    boolean valid, boolean selected) {
                // MaterialDatePicker 的装饰器回调是基于 UTC 的年月日
                // 我们构造一个 UTC 0点的时间戳进行匹配
                java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                cal.set(year, month, day, 0, 0, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long utcStart = cal.getTimeInMillis();

                // 同时考虑到本地存储的时间戳可能是本地 0点，这里做一个兼容或转换逻辑
                // 暂时假设 finalRecordedDates 包含的是 UTC 0点的时间戳 (我们在 ViewModel 中会做对齐)
                if (finalRecordedDates.contains(utcStart)) {
                    GradientDrawable dot = new GradientDrawable();
                    dot.setShape(GradientDrawable.OVAL);
                    dot.setSize(12, 12);
                    dot.setColor(ContextCompat.getColor(requireContext(), R.color.color_success));
                    return new InsetDrawable(dot, 0, 0, 0, 4);
                }
                return null;
            }

            @Override
            public void writeToParcel(android.os.Parcel dest, int flags) {
            }

            @Override
            public int describeContents() {
                return 0;
            }
        };

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择日期")
                .setSelection(DateUtils.localToUtcDayStart(currentSelection))
                .setDayViewDecorator(decorator)
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointBackward.now()) // 不允许选择未来日期
                        .build())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            // MaterialDatePicker 返回的是 UTC 时间戳
            // 我们需要将其调整为本地日期的 0 点
            viewModel.setSelectedDate(selection);
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
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

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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

        // Plan 32: FAB 添加自定义食物 (同步 ImageButton 风格)
        android.view.View fabAddFood = view.findViewById(R.id.fab_add_food);
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

        // 设置分类下拉 (Plan 32: 增加图标和 M3 布局)
        String[] categories = {
                "🍱 主食: 其它主食",
                "🍲 家常菜: 精选家常",
                "🥩 蛋白质: 肉蛋奶",
                "🥗 蔬菜水果: 新鲜蔬果",
                "🍫 零食饮料: 休闲小食",
                "🧂 调料油脂: 常用调味",
                "🍷 酒精: 酒水明细",
                "❓ 其他"
        };
        android.widget.ArrayAdapter<String> categoryAdapter = new android.widget.ArrayAdapter<>(
                requireContext(), R.layout.item_dropdown_category, categories);
        spinnerCategory.setAdapter(categoryAdapter);
        spinnerCategory.setText(categories[5], false); // 默认选择"其他"

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加自定义食物")
                .setView(dialogView)
                .setNeutralButton("取消", null)
                .setPositiveButton("保存", (dialogInterface, i) -> {
                    String name = etFoodName.getText() != null ? etFoodName.getText().toString().trim() : "";
                    String caloriesStr = etCalories.getText() != null ? etCalories.getText().toString().trim() : "";
                    String proteinStr = etProtein.getText() != null ? etProtein.getText().toString().trim() : "";
                    String carbsStr = etCarbs.getText() != null ? etCarbs.getText().toString().trim() : "";
                    String servingUnit = etServingUnit.getText() != null ? etServingUnit.getText().toString().trim()
                            : "";
                    String weightStr = etWeightPerUnit.getText() != null ? etWeightPerUnit.getText().toString().trim()
                            : "";
                    String categoryRaw = spinnerCategory.getText().toString().trim();

                    // 验证必填字段
                    if (name.isEmpty() || caloriesStr.isEmpty()) {
                        Toast.makeText(requireContext(), "请填写名称和热量", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 解析数值 (保持原有解析逻辑)
                    try {
                        int calories = (int) Double.parseDouble(caloriesStr);
                        double protein = proteinStr.isEmpty() ? 0 : Double.parseDouble(proteinStr);
                        double carbs = carbsStr.isEmpty() ? 0 : Double.parseDouble(carbsStr);
                        int weightPerUnit = weightStr.isEmpty() ? 100 : Integer.parseInt(weightStr);
                        String unit = servingUnit.isEmpty() ? "份" : servingUnit;

                        // 清理分类名称中的 Emoji (保持数据库存储一致性)
                        String cat = categoryRaw.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+", "").trim();
                        if (cat.isEmpty())
                            cat = "其他";

                        // 创建食物对象并保存
                        FoodLibrary newFood = new FoodLibrary(name, calories, protein, carbs, unit, weightPerUnit, cat);
                        viewModel.insertFood(newFood);

                        Toast.makeText(requireContext(), "✅ 已添加: " + name, Toast.LENGTH_SHORT).show();

                        // 刷新列表
                        executorService.execute(() -> {
                            List<FoodLibrary> allFoods = viewModel.getAllFoodsSync();
                            requireActivity().runOnUiThread(() -> adapter.setFoodList(allFoods));
                        });
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "输入格式不正确", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
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
        // 0. 观察选中日期并显示 (Plan 13)
        viewModel.getSelectedDate().observe(getViewLifecycleOwner(), date -> {
            boolean isToday = com.cz.fitnessdiary.utils.DateUtils.isToday(date);
            if (isToday) {
                binding.tvSelectedDate.setText("今日");
            } else {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年M月d日",
                        java.util.Locale.getDefault());
                binding.tvSelectedDate.setText(sdf.format(new java.util.Date(date)));
            }
        });

        // 1. 观察用户信息 (核心：作为所有计算的目标基准)
        viewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                // 用户信息加载后，立即刷新所有相关 UI
                refreshAllSummaryUI(user);
            }
        });

        // 2. 观察餐段数据并更新卡片
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

        // 3. 观察热量/营养素数据 (变化时触发局部刷新)
        viewModel.getTodayTotalCalories().observe(getViewLifecycleOwner(),
                total -> refreshAllSummaryUI(viewModel.getCurrentUser().getValue()));
        viewModel.getTodayTotalProtein().observe(getViewLifecycleOwner(),
                total -> refreshAllSummaryUI(viewModel.getCurrentUser().getValue()));
        viewModel.getTodayTotalCarbs().observe(getViewLifecycleOwner(),
                total -> refreshAllSummaryUI(viewModel.getCurrentUser().getValue()));
    }

    /**
     * 统一刷新顶部概览 UI
     */
    private void refreshAllSummaryUI(User user) {
        if (user == null || binding == null)
            return;

        // --- 1. 卡路里刷新 ---
        int targetCalories = user.getTargetCalories();
        if (targetCalories <= 0)
            targetCalories = 2000; // 极简兜底

        Integer consumed = viewModel.getTodayTotalCalories().getValue();
        int currentCalories = consumed != null ? consumed : 0;

        binding.tvTotalCalories.setText(String.valueOf(currentCalories));
        binding.tvCaloriesSubtitle.setText("千卡 · 目标 " + targetCalories);

        int calProgress = (int) ((currentCalories * 100.0) / targetCalories);
        binding.progressCalories.setProgress(Math.min(calProgress, 100));
        binding.progressCalories
                .setIndicatorColor(currentCalories > targetCalories ? getResources().getColor(R.color.error, null)
                        : getResources().getColor(R.color.color_success, null));

        // --- 2. 蛋白质刷新 ---
        int targetProtein = user.getTargetProtein();
        if (targetProtein <= 0)
            targetProtein = (int) (user.getWeight() * 1.5);

        Double pConsumed = viewModel.getTodayTotalProtein().getValue();
        int currentProtein = pConsumed != null ? pConsumed.intValue() : 0;

        int pProgress = (int) ((currentProtein * 100.0) / targetProtein);
        binding.progressProtein.setProgress(Math.min(pProgress, 100));
        binding.tvProteinStatus.setText("蛋白质: " + currentProtein + "/" + targetProtein + "g");

        // --- 3. 碳水刷新 ---
        int targetCarbs = user.getTargetCarbs();
        if (targetCarbs <= 0)
            targetCarbs = 250;

        Double cConsumed = viewModel.getTodayTotalCarbs().getValue();
        int currentCarbs = cConsumed != null ? cConsumed.intValue() : 0;

        int cProgress = (int) ((currentCarbs * 100.0) / targetCarbs);
        binding.progressCarbs.setProgress(Math.min(cProgress, 100));
        binding.tvCarbsStatus.setText("碳水: " + currentCarbs + "/" + targetCarbs + "g");
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
            String portions = "";
            if (r.getServings() > 0) {
                portions = r.getServings() + (r.getServingUnit() != null ? r.getServingUnit() : "份") + " - ";
            }
            items[i] = "• " + r.getFoodName() + " (" + portions + r.getCalories() + "千卡)  " + timeStr;
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
        AddFoodBottomSheetFragment.newInstance(preSelectedMealType)
                .show(getChildFragmentManager(), "AddFoodBottomSheet");
    }

    /**
     * 显示智能添加食物对话框（支持预选餐类型和特定食物）
     */
    private void showSmartAddFoodDialog(int preSelectedMealType, FoodLibrary preSelectedFood) {
        AddFoodBottomSheetFragment.newInstance(preSelectedMealType, preSelectedFood)
                .show(getChildFragmentManager(), "AddFoodBottomSheet");
    }

    /**
     * 显示智能添加食物对话框（支持食物库联想）
     */
    private void showSmartAddFoodDialog() {
        AddFoodBottomSheetFragment.newInstance(-1)
                .show(getChildFragmentManager(), "AddFoodBottomSheet");
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
