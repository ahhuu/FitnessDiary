package com.cz.fitnessdiary.ui.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import com.cz.fitnessdiary.model.FoodScanFlowState;
import com.cz.fitnessdiary.model.ImageMealDraft;
import java.io.File;
import java.io.InputStream;
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
import com.cz.fitnessdiary.utils.FoodCategoryUtils;
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
    private ExecutorService imageExecutorService;


    private Uri photoUri;
    private Uri lastImageUri;
    private Bitmap lastScanBitmap;

    private final androidx.activity.result.ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
            success -> {
                if (success && photoUri != null) {
                    lastImageUri = photoUri;
                    startAnalyzeFromUri(photoUri);
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String> mediaPickerLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    lastImageUri = uri;
                    startAnalyzeFromUri(uri);
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String> requestCameraPermissionLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    launchCamera();
                } else if (isAdded()) {
                    Toast.makeText(requireContext(), "需要相机权限才能拍照识别", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentDietBinding.inflate(inflater, container, false);
        executorService = Executors.newSingleThreadExecutor();
        imageExecutorService = Executors.newSingleThreadExecutor();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(DietViewModel.class);

        setupFoodImageResultListener();
        setupViews();
        setupFoodScanFlowView();
        observeViewModel();
    }

    private void setupViews() {
        // 设置日期导航监听 (Plan 13)
        binding.btnPrevDay.setOnClickListener(v -> viewModel.toPreviousDay());
        binding.btnNextDay.setOnClickListener(v -> viewModel.toNextDay());
        binding.tvSelectedDate.setOnClickListener(v -> showDatePickerDialog());

        // 设置搜索卡片点击
        binding.cardFoodWiki.setOnClickListener(v -> showFoodWikiDialog());
        binding.btnFoodScan.setOnClickListener(v -> showImageSourcePicker());

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

        com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter adapter = new com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter();
        rvResults.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        rvResults.setAdapter(adapter);

        // 设置点击添加监听
        adapter.setOnItemClickListener(food -> {
            String[] mealOptions = { "早餐", "午餐", "晚餐", "加餐" };
            AlertDialog chooseMealDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("将 " + food.getName() + " 添加到...")
                    .setItems(mealOptions, (dialogInterface, which) -> {
                        dialog.dismiss();
                        showSmartAddFoodDialog(which, food);
                    })
                    .show();
            tintDialogButtons(chooseMealDialog);
        });

        // 设置点击编辑监听
        adapter.setOnEditClickListener(food -> {
            showAddOrEditFoodDialog(dialog, adapter, food);
        });

        imageExecutorService.execute(() -> {
            List<FoodLibrary> allFoods = viewModel.getAllFoodsSync();
            requireActivity().runOnUiThread(() -> adapter.setFoodList(allFoods));
        });

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                imageExecutorService.execute(() -> {
                    List<FoodLibrary> results = viewModel.searchFoods(query);
                    requireActivity().runOnUiThread(() -> adapter.setFoodList(results));
                });
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        android.view.View fabAddFood = view.findViewById(R.id.fab_add_food);
        fabAddFood.setOnClickListener(v -> showAddOrEditFoodDialog(dialog, adapter, null));

        dialog.show();
    }

    /**
     * 显示添加或编辑食物对话框
     */
    private void showAddOrEditFoodDialog(android.app.Dialog parentDialog,
            com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter adapter,
            @Nullable FoodLibrary existingFood) {

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_custom_food, null);
        boolean isEditMode = existingFood != null;

        com.google.android.material.textfield.TextInputEditText etFoodName = dialogView.findViewById(R.id.et_food_name);
        com.google.android.material.textfield.TextInputEditText etCalories = dialogView.findViewById(R.id.et_calories);
        com.google.android.material.textfield.TextInputEditText etProtein = dialogView.findViewById(R.id.et_protein);
        com.google.android.material.textfield.TextInputEditText etCarbs = dialogView.findViewById(R.id.et_carbs);
        com.google.android.material.textfield.TextInputEditText etServingUnit = dialogView
                .findViewById(R.id.et_serving_unit);
        com.google.android.material.textfield.TextInputEditText etWeightPerUnit = dialogView
                .findViewById(R.id.et_weight_per_unit);
        AutoCompleteTextView spinnerCategory = dialogView.findViewById(R.id.spinner_category);

        // === 千卡/千焦 单位切换 ===
        com.google.android.material.button.MaterialButtonToggleGroup toggleUnit = dialogView
                .findViewById(R.id.toggle_calorie_unit);
        com.google.android.material.textfield.TextInputLayout layoutCalories = dialogView
                .findViewById(R.id.layout_calories);
        // isKJ[0] 标记当前输入框是否处于千焦模式
        final boolean[] isKJ = { false };

        toggleUnit.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            String currentText = etCalories.getText().toString().trim();
            if (checkedId == R.id.btn_unit_kj) {
                // 切换到千焦模式
                layoutCalories.setHint("热量 (千焦/100g) *");
                layoutCalories.setSuffixText("KJ");
                isKJ[0] = true;
                // 自动换算已有数值: kcal → KJ
                if (!currentText.isEmpty()) {
                    try {
                        double kcalVal = Double.parseDouble(currentText);
                        int kjVal = (int) Math.round(kcalVal * 4.184);
                        etCalories.setText(String.valueOf(kjVal));
                    } catch (NumberFormatException ignored) { }
                }
            } else {
                // 切换到千卡模式
                layoutCalories.setHint("热量 (千卡/100g) *");
                layoutCalories.setSuffixText("kcal");
                isKJ[0] = false;
                // 自动换算已有数值: KJ → kcal
                if (!currentText.isEmpty()) {
                    try {
                        double kjVal = Double.parseDouble(currentText);
                        int kcalVal = (int) Math.round(kjVal / 4.184);
                        etCalories.setText(String.valueOf(kcalVal));
                    } catch (NumberFormatException ignored) { }
                }
            }
        });

        // 设置分类适配器
        android.widget.ArrayAdapter<String> categoryAdapter = new android.widget.ArrayAdapter<>(
                requireContext(), R.layout.item_dropdown_category, FoodCategoryUtils.getDisplayCategories());
        spinnerCategory.setAdapter(categoryAdapter);

        // 如果是编辑模式，预填数据
        if (isEditMode) {
            etFoodName.setText(existingFood.getName());
            etCalories.setText(String.valueOf(existingFood.getCaloriesPer100g()));
            etProtein.setText(String.valueOf(existingFood.getProteinPer100g()));
            etCarbs.setText(String.valueOf(existingFood.getCarbsPer100g()));
            etServingUnit.setText(existingFood.getServingUnit());
            etWeightPerUnit.setText(String.valueOf(existingFood.getWeightPerUnit()));

            spinnerCategory.setText(FoodCategoryUtils.toDisplayCategory(existingFood.getCategory()), false);
        } else {
            spinnerCategory.setText(FoodCategoryUtils.toDisplayCategory(FoodCategoryUtils.CAT_OTHER), false);
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
            requireContext())
                .setTitle(isEditMode ? "编辑食物信息" : "添加自定义食物")
                .setView(dialogView)
                .setNeutralButton("取消", null)
                .setPositiveButton("保存", (dialogInterface, i) -> {
                    String name = etFoodName.getText().toString().trim();
                    String caloriesStr = etCalories.getText().toString().trim();
                    String proteinStr = etProtein.getText().toString().trim();
                    String carbsStr = etCarbs.getText().toString().trim();
                    String servingUnit = etServingUnit.getText().toString().trim();
                    String weightStr = etWeightPerUnit.getText().toString().trim();
                    String categoryRaw = spinnerCategory.getText().toString().trim();

                    if (name.isEmpty() || caloriesStr.isEmpty()) {
                        Toast.makeText(requireContext(), "请填写名称和热量", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        double caloriesInput = Double.parseDouble(caloriesStr);
                        // 如果用户以千焦输入，转换为千卡存储
                        int calories;
                        if (isKJ[0]) {
                            calories = (int) Math.round(caloriesInput / 4.184);
                        } else {
                            calories = (int) caloriesInput;
                        }
                        double protein = proteinStr.isEmpty() ? 0 : Double.parseDouble(proteinStr);
                        double carbs = carbsStr.isEmpty() ? 0 : Double.parseDouble(carbsStr);
                        int weightPerUnit = weightStr.isEmpty() ? 100 : Integer.parseInt(weightStr);
                        String unit = servingUnit.isEmpty() ? "份" : servingUnit;
                        String cat = FoodCategoryUtils.normalizeCategory(categoryRaw);

                        if (isEditMode) {
                            existingFood.setName(name);
                            existingFood.setCaloriesPer100g(calories);
                            existingFood.setProteinPer100g(protein);
                            existingFood.setCarbsPer100g(carbs);
                            existingFood.setServingUnit(unit);
                            existingFood.setWeightPerUnit(weightPerUnit);
                            existingFood.setCategory(cat);
                            viewModel.updateFood(existingFood);
                            Toast.makeText(requireContext(), "✅ 已更新: " + name, Toast.LENGTH_SHORT).show();
                        } else {
                            FoodLibrary newFood = new FoodLibrary(name, calories, protein, carbs, unit, weightPerUnit,
                                    cat);
                            viewModel.insertFood(newFood);
                            Toast.makeText(requireContext(), "✅ 已添加: " + name, Toast.LENGTH_SHORT).show();
                        }

                        // 刷新列表
                        imageExecutorService.execute(() -> {
                            List<FoodLibrary> allFoods = viewModel.getAllFoodsSync();
                            requireActivity().runOnUiThread(() -> adapter.setFoodList(allFoods));
                        });
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "输入格式不正确", Toast.LENGTH_SHORT).show();
                    }
                });

        if (isEditMode) {
            builder.setNegativeButton("删除", (dialogInterface, i) -> {
                AlertDialog deleteDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("确认删除")
                        .setMessage("确定要从食物库中删除“" + existingFood.getName() + "”吗？此操作不可撤销。")
                        .setPositiveButton("删除", (d, w) -> {
                            viewModel.deleteFoodFromLibrary(existingFood);
                            Toast.makeText(requireContext(), "🗑️ 已从库中移除: " + existingFood.getName(),
                                    Toast.LENGTH_SHORT).show();
                            // 刷新列表
                            imageExecutorService.execute(() -> {
                                List<FoodLibrary> allFoods = viewModel.getAllFoodsSync();
                                requireActivity().runOnUiThread(() -> adapter.setFoodList(allFoods));
                            });
                        })
                        .setNegativeButton("取消", null)
                        .show();
                tintDialogButtons(deleteDialog);
            });
        }

        AlertDialog addOrEditDialog = builder.show();
        tintDialogButtons(addOrEditDialog);
    }

    private void tintDialogButtons(@Nullable AlertDialog dialog) {
        if (dialog == null || !isAdded()) {
            return;
        }
        int color = ContextCompat.getColor(requireContext(), R.color.diet_primary);
        android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        android.widget.Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        android.widget.Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (positive != null) {
            positive.setTextColor(color);
        }
        if (negative != null) {
            negative.setTextColor(color);
        }
        if (neutral != null) {
            neutral.setTextColor(color);
        }
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
                            updateMealCard(binding.cardBreakfast.getRoot(), "☀️ 早餐", 0, section);
                            break;
                        case 1:
                            updateMealCard(binding.cardLunch.getRoot(), "🌞 午餐", 1, section);
                            break;
                        case 2:
                            updateMealCard(binding.cardDinner.getRoot(), "🌙 晚餐", 2, section);
                            break;
                        case 3:
                            updateMealCard(binding.cardSnack.getRoot(), "🍪 加餐", 3, section);
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

        viewModel.getFoodScanState().observe(getViewLifecycleOwner(), this::renderFoodScanState);
        viewModel.getFoodScanDraft().observe(getViewLifecycleOwner(), draft -> {
            if (draft == null || !isAdded()) {
                return;
            }
            viewModel.clearFoodScanDraft();
            viewModel.resetFoodScanState();
            showFoodConfirmSheet(draft);
        });
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
        binding.progressCalories.setIndicatorColor(getResources().getColor(R.color.diet_primary, null));

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
    private void updateMealCard(View cardRoot, String title, int mealType, com.cz.fitnessdiary.model.MealSection section) {
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
        cardRoot.setOnClickListener(v -> showMealDetailsDialog(title, mealType, records));
    }

    /**
     * 显示餐点详情对话框 (支持删除)
     */
    private void showMealDetailsDialog(String title, int mealType, List<com.cz.fitnessdiary.database.entity.FoodRecord> records) {
        if (records == null || records.isEmpty()) {
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

    private void setupFoodImageResultListener() {
        getChildFragmentManager().setFragmentResultListener(
                FoodImageConfirmBottomSheet.REQUEST_KEY,
                getViewLifecycleOwner(),
                (requestKey, bundle) -> {
                    String action = bundle.getString(FoodImageConfirmBottomSheet.RESULT_ACTION, "");
                    if (FoodImageConfirmBottomSheet.ACTION_CONFIRM.equals(action)) {
                        Object raw = bundle.getSerializable(FoodImageConfirmBottomSheet.RESULT_DRAFT);
                        if (raw instanceof ImageMealDraft) {
                            boolean syncLibrary = bundle.getBoolean(FoodImageConfirmBottomSheet.RESULT_SYNC_LIBRARY, false);
                            viewModel.saveImageMealDraft((ImageMealDraft) raw, syncLibrary);
                            Toast.makeText(requireContext(), "已记录整餐", Toast.LENGTH_SHORT).show();
                        }
                    } else if (FoodImageConfirmBottomSheet.ACTION_RETRY.equals(action)) {
                        retryAnalyzeLastImage();
                    }
                });
    }

    private void setupFoodScanFlowView() {
        binding.viewFoodScanFlow.bringToFront();
        binding.viewFoodScanFlow.setTranslationZ(100f);
        binding.viewFoodScanFlow.setActionListener(new com.cz.fitnessdiary.ui.widget.FoodScanFlowView.ActionListener() {
            @Override
            public void onRetry() {
                retryAnalyzeLastImage();
            }

            @Override
            public void onCancel() {
                viewModel.resetFoodScanState();
            }
        });
    }

    private void showImageSourcePicker() {
        String[] options = { "拍照识别", "从相册选择" };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("识别食物")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED) {
                            launchCamera();
                        } else {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                        }
                    } else {
                        mediaPickerLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private void launchCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "food_scan");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(requireContext(), "无法创建图片缓存目录", Toast.LENGTH_SHORT).show();
                return;
            }
            File photoFile = File.createTempFile("food_", ".jpg", dir);
            photoUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", photoFile);
            cameraLauncher.launch(photoUri);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "拍照启动失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startAnalyzeFromUri(Uri uri) {
        if (uri == null) {
            return;
        }
        imageExecutorService.execute(() -> {
            try {
                Bitmap bitmap = decodeScaledBitmap(uri, 1280);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> startAnalyze(bitmap));
            } catch (Exception e) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.viewFoodScanFlow.show();
                        binding.viewFoodScanFlow.render(FoodScanFlowState.error("图片解析失败，请重试", true));
                    }
                    Toast.makeText(requireContext(), "图片解析失败，请重试", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startAnalyze(Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(requireContext(), "图片为空，请重试", Toast.LENGTH_SHORT).show();
            return;
        }
        lastScanBitmap = bitmap;
        binding.viewFoodScanFlow.setPreviewBitmap(bitmap);
        binding.viewFoodScanFlow.show();
        viewModel.analyzeMealImage(bitmap);
    }

    private void retryAnalyzeLastImage() {
        if (lastScanBitmap != null) {
            binding.viewFoodScanFlow.show();
            viewModel.analyzeMealImage(lastScanBitmap);
            return;
        }
        if (lastImageUri != null) {
            startAnalyzeFromUri(lastImageUri);
            return;
        }
        Toast.makeText(requireContext(), "没有可重试的图片", Toast.LENGTH_SHORT).show();
    }

    private void renderFoodScanState(FoodScanFlowState state) {
        if (state == null || binding == null) {
            return;
        }
        if (state.getStage() == FoodScanFlowState.Stage.IDLE) {
            binding.viewFoodScanFlow.hide();
            return;
        }
        binding.viewFoodScanFlow.show();
        binding.viewFoodScanFlow.render(state);
        if (state.getStage() == FoodScanFlowState.Stage.SUCCESS) {
            binding.viewFoodScanFlow.postDelayed(() -> {
                if (binding != null) {
                    binding.viewFoodScanFlow.hide();
                    binding.viewFoodScanFlow.setPreviewBitmap(null);
                }
            }, 1100);
        }
    }

    private void showFoodConfirmSheet(ImageMealDraft draft) {
        if (getChildFragmentManager().findFragmentByTag(FoodImageConfirmBottomSheet.TAG) != null) {
            return;
        }
        FoodImageConfirmBottomSheet.newInstance(draft)
                .show(getChildFragmentManager(), FoodImageConfirmBottomSheet.TAG);
    }

    private Bitmap decodeScaledBitmap(Uri uri, int maxSide) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(requireContext().getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                int maxDim = Math.max(width, height);
                if (maxDim > maxSide) {
                    float scale = (float) maxSide / (float) maxDim;
                    decoder.setTargetSize(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
                }
            });
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        int inSampleSize = 1;
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxDim / inSampleSize > maxSide) {
            inSampleSize *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, inSampleSize);
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
            if (bitmap == null) {
                throw new IllegalStateException("无法解析图片");
            }
            return bitmap;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null) {
            executorService.shutdown();
        }
        if (imageExecutorService != null) {
            imageExecutorService.shutdown();
        }
        binding = null;
    }
}







