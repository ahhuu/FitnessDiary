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
import com.cz.fitnessdiary.model.ImageFoodItemDraft;
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
import com.cz.fitnessdiary.viewmodel.RecipeViewModel;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.utils.UnitUtils;
import com.cz.fitnessdiary.utils.ErrorHandler;
import com.cz.fitnessdiary.utils.FoodCategoryUtils;
import com.cz.fitnessdiary.utils.PinyinUtils;
import com.cz.fitnessdiary.model.DailyHealthSnapshot;
import com.cz.fitnessdiary.repository.HealthAggregationRepository;
import com.cz.fitnessdiary.service.OpenFoodFactsService;
import com.cz.fitnessdiary.service.AiDietTextAnalyzer;
import com.cz.fitnessdiary.service.DietLibraryTextMatcher;
import com.cz.fitnessdiary.service.FoodImageQuotaStore;
import com.cz.fitnessdiary.viewmodel.AiRecordDraftViewModel;
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
import android.content.res.ColorStateList;
import java.util.Set;
import java.util.HashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.cz.fitnessdiary.ui.guide.GuideStateManager;
import com.cz.fitnessdiary.ui.guide.GuideStep;
import com.cz.fitnessdiary.ui.guide.PageGuide;
import com.cz.fitnessdiary.ui.guide.TargetedGuideOverlay;

/**
 * 饮食记录页面 - 2.0 智能化完整版
 * 核心功能：食物库联想、自动热量计算、智能反馈
 * Refactored for Plan 12: Grid Layout & Context-Aware Add
 */
public class DietFragment extends Fragment {

    private FragmentDietBinding binding;
    private DietViewModel viewModel;
    private AiRecordDraftViewModel aiDraftViewModel;
    private RecipeViewModel recipeViewModel;
    private ExecutorService executorService;
    private ExecutorService imageExecutorService;
    private java.util.Set<Long> cachedDietRecordedDates = new java.util.HashSet<>();


    private Uri photoUri;
    private Uri lastImageUri;
    private Bitmap lastScanBitmap;
    private ImageMealDraft pendingLibraryDraftForAi;
    private String pendingAiRequestText = "";
    private boolean pendingAiRequestForce;

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
                    ErrorHandler.showError(DietFragment.this, "需要相机权限才能拍照识别", null);
                }
            });

    private OpenFoodFactsService openFoodFactsService;
    private FoodImageQuotaStore foodImageQuotaStore;

    private final androidx.activity.result.ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions> barcodeLauncher = registerForActivityResult(
            new com.journeyapps.barcodescanner.ScanContract(),
            result -> {
                if (result.getContents() == null) {
                    return;
                }
                String barcode = result.getContents();
                if (openFoodFactsService != null) {
                    openFoodFactsService.lookupByBarcode(barcode, new OpenFoodFactsService.LookupCallback() {
                        @Override
                        public void onSuccess(OpenFoodFactsService.FoodResult food) {
                            if (!isAdded()) {
                                return;
                            }
                            requireActivity().runOnUiThread(() -> showBarcodeDraft(food));
                        }

                        @Override
                        public void onNotFound() {
                            requireActivity().runOnUiThread(() -> ErrorHandler.showInfo(
                                    DietFragment.this, "未在食物数据库中查询到此条码 (" + barcode + ")"));
                        }

                        @Override
                        public void onError(String message) {
                            requireActivity().runOnUiThread(() -> ErrorHandler.showError(
                                    DietFragment.this, message, () -> {
                                        if (openFoodFactsService != null) {
                                            openFoodFactsService.lookupByBarcode(barcode, this);
                                        }
                                    }));
                        }
                    });
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
        aiDraftViewModel = new ViewModelProvider(requireActivity()).get(AiRecordDraftViewModel.class);
        recipeViewModel = new ViewModelProvider(this).get(RecipeViewModel.class);
        openFoodFactsService = new OpenFoodFactsService();
        foodImageQuotaStore = new FoodImageQuotaStore(requireContext());

        setupFoodImageResultListener();
        setupAiDietEntryResultListener();
        setupViews();
        setupFoodScanFlowView();
        observeViewModel();
        loadEnergyStatusBar();
    }

    private void setupViews() {
        // 设置日期导航监听 (Plan 13)
        binding.btnPrevDay.setOnClickListener(v -> viewModel.toPreviousDay());
        binding.btnNextDay.setOnClickListener(v -> viewModel.toNextDay());
        binding.tvSelectedDate.setOnClickListener(v -> showDatePickerDialog());

        // 设置搜索卡片点击
        binding.cardFoodWiki.setOnClickListener(v -> showFoodWikiDialog());
        binding.btnAiFoodRecord.setOnClickListener(v -> showAiDietEntry());



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

        final Set<Long> finalRecordedDates = cachedDietRecordedDates;
        DayViewDecorator decorator = new DayViewDecorator() {
            @Nullable
            @Override
            public Drawable getCompoundDrawableBottom(android.content.Context context, int year, int month, int day,
                    boolean valid, boolean selected) {
                java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                cal.set(year, month, day, 12, 0, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long dayStart = DateUtils.getDayStartTimestamp(cal.getTimeInMillis());

                if (finalRecordedDates.contains(dayStart)) {
                    GradientDrawable dot = new GradientDrawable();
                    dot.setShape(GradientDrawable.OVAL);
                    dot.setColor(ContextCompat.getColor(requireContext(), R.color.color_success));
                    dot.setBounds(0, 0, 24, 24);
                    return dot;
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
                    List<FoodLibrary> allFoods = viewModel.getAllFoodsSync();
                    List<FoodLibrary> results = new ArrayList<>();
                    String q = query.trim().toLowerCase();
                    if (q.isEmpty()) {
                        results.addAll(allFoods);
                    } else {
                        for (FoodLibrary food : allFoods) {
                            if (PinyinUtils.matches(food.getName(), q)) {
                                results.add(food);
                            }
                        }
                    }
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
        com.google.android.material.textfield.TextInputEditText etFat = dialogView.findViewById(R.id.et_fat);
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
            etFat.setText(String.valueOf(existingFood.getFatPer100g()));
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
                    String fatStr = etFat.getText().toString().trim();
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
                        double fat = fatStr.isEmpty() ? 0 : Double.parseDouble(fatStr);
                        int weightPerUnit = weightStr.isEmpty() ? 100 : Integer.parseInt(weightStr);
                        String unit = servingUnit.isEmpty() ? "份" : servingUnit;
                        String cat = FoodCategoryUtils.normalizeCategory(categoryRaw);

                        if (isEditMode) {
                            existingFood.setName(name);
                            existingFood.setCaloriesPer100g(calories);
                            existingFood.setProteinPer100g(protein);
                            existingFood.setCarbsPer100g(carbs);
                                existingFood.setFatPer100g(fat);
                            existingFood.setServingUnit(unit);
                            existingFood.setWeightPerUnit(weightPerUnit);
                            existingFood.setCategory(cat);
                            viewModel.updateFood(existingFood);
                            Toast.makeText(requireContext(), "✅ 已更新: " + name, Toast.LENGTH_SHORT).show();
                        } else {
                                FoodLibrary newFood = new FoodLibrary(name, calories, protein, carbs, fat, unit,
                                    weightPerUnit, cat);
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
            binding.btnNextDay.setEnabled(!isToday);
            binding.btnNextDay.setAlpha(isToday ? 0.35f : 1f);
            if (isToday) {
                binding.tvSelectedDate.setText("今日");
            } else {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年M月d日",
                        java.util.Locale.getDefault());
                binding.tvSelectedDate.setText(sdf.format(new java.util.Date(date)));
            }
        });

        // 预加载有饮食记录的日期
        viewModel.getRecordedDates().observe(getViewLifecycleOwner(), d -> {
            if (d != null) cachedDietRecordedDates = d;
        });

        // 1. 观察用户信息 (核心：作为所有计算的目标基准)
        viewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
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
        viewModel.getTodayTotalFat().observe(getViewLifecycleOwner(),
                total -> refreshAllSummaryUI(viewModel.getCurrentUser().getValue()));

        viewModel.getFrequentFoods().observe(getViewLifecycleOwner(), foodNames -> {
            binding.cgFrequentFoods.removeAllViews();
            if (foodNames != null && !foodNames.isEmpty()) {
                for (String name : foodNames) {
                    com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                    chip.setText(name);
                    chip.setChipBackgroundColor(ColorStateList.valueOf(0xFFF0FDF4)); // 莫兰迪浅绿，与饮食色系一致
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.diet_primary));
                    chip.setCloseIconVisible(false);
                    chip.setOnClickListener(v -> {
                        String[] meals = {"☀️ 早餐", "🌞 午餐", "🌙 晚餐", "🍪 加餐"};
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("记录「" + name + "」到...")
                                .setItems(meals, (dialog, which) -> {
                                    viewModel.addFoodRecordSmart(name, 1.0f, which);
                                    Toast.makeText(requireContext(), "✅ 已添加 1 份 " + name, Toast.LENGTH_SHORT).show();
                                })
                                .show();
                    });
                    chip.setOnLongClickListener(v -> {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("收藏「" + name + "」")
                                .setMessage("将 " + name + " 添加到收藏食物？")
                                .setPositiveButton("收藏", (d, w) -> {
                                    addFrequentFoodToFavorites(name);
                                })
                                .setNegativeButton("取消", null)
                                .show();
                        return true;
                    });
                    binding.cgFrequentFoods.addView(chip);
                }
            } else {
                com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                chip.setText("暂无高频食物，记录几次即可在此快捷添加");
                chip.setEnabled(false);
                binding.cgFrequentFoods.addView(chip);
            }
        });

        // Favorite foods chips
        recipeViewModel.loadFavoriteFoods(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    binding.cgFavoriteFoods.removeAllViews();
                    java.util.List<com.cz.fitnessdiary.database.entity.FavoriteFood> favs = recipeViewModel.getFavoriteFoodList();
                    if (favs != null && !favs.isEmpty()) {
                        for (com.cz.fitnessdiary.database.entity.FavoriteFood food : favs) {
                            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                            chip.setText("⭐ " + food.getFoodName());
                            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFFFF3E0));
                            chip.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.diet_primary));
                            chip.setCloseIconVisible(false);
                            chip.setOnClickListener(v -> {
                                String[] meals = {"☀️ 早餐", "🌞 午餐", "🌙 晚餐", "🍪 加餐"};
                                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                        .setTitle("记录「" + food.getFoodName() + "」到...")
                                        .setItems(meals, (dialog, which) -> {
                                            viewModel.addFoodRecordSmart(food.getFoodName(), 1.0f, which);
                                            Toast.makeText(requireContext(), "✅ 已添加 1 份 " + food.getFoodName(), Toast.LENGTH_SHORT).show();
                                        })
                                        .show();
                            });
                            binding.cgFavoriteFoods.addView(chip);
                        }
                    }
                });
            }
        });

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

        binding.tvTotalCalories.setText(UnitUtils.formatEnergy(currentCalories, requireContext()));
        binding.tvCaloriesSubtitle.setText(UnitUtils.getEnergyUnitSymbol(requireContext()) + " · 目标 " + UnitUtils.formatEnergy(targetCalories, requireContext()));

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

        // --- 4. 脂肪刷新 ---
        int targetFat = user.getTargetFat();
        if (targetFat <= 0)
            targetFat = 60; // 默认60g

        Double fConsumed = viewModel.getTodayTotalFat().getValue();
        int currentFat = fConsumed != null ? fConsumed.intValue() : 0;

        int fProgress = (int) ((currentFat * 100.0) / targetFat);
        binding.progressFat.setProgress(Math.min(fProgress, 100));
        binding.tvFatStatus.setText("脂肪: " + currentFat + "/" + targetFat + "g");
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
        tvCalories.setText(UnitUtils.formatEnergy(section.getTotalCalories(), requireContext()) + " " + UnitUtils.getEnergyUnitSymbol(requireContext()));

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
            items[i] = "• " + r.getFoodName() + " (" + portions + UnitUtils.formatEnergy(r.getCalories(), requireContext()) + UnitUtils.getEnergyUnitSymbol(requireContext()) + ")  " + timeStr;
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
                            aiDraftViewModel.clearDietDraft();
                            Toast.makeText(requireContext(), "饮食已保存", Toast.LENGTH_SHORT).show();
                        }
                    } else if (FoodImageConfirmBottomSheet.ACTION_RETRY.equals(action)) {
                        retryAnalyzeLastImage();
                    } else if (FoodImageConfirmBottomSheet.ACTION_SUPPLEMENT_AI.equals(action)) {
                        Object rawDraft = bundle.getSerializable(FoodImageConfirmBottomSheet.RESULT_DRAFT);
                        if (rawDraft instanceof ImageMealDraft) {
                            pendingLibraryDraftForAi = (ImageMealDraft) rawDraft;
                        }
                        analyzeDietWithAi(bundle.getString(
                                FoodImageConfirmBottomSheet.RESULT_UNMATCHED_TEXT, ""), false);
                    } else if (FoodImageConfirmBottomSheet.ACTION_AI_RECOGNIZE.equals(action)) {
                        pendingLibraryDraftForAi = null;
                        analyzeDietWithAi(bundle.getString(
                                FoodImageConfirmBottomSheet.RESULT_UNMATCHED_TEXT, ""), true);
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

    private void setupAiDietEntryResultListener() {
        getChildFragmentManager().setFragmentResultListener(
                AiDietEntryBottomSheet.REQUEST_KEY,
                getViewLifecycleOwner(),
                (requestKey, bundle) -> {
                    String type = bundle.getString(AiDietEntryBottomSheet.RESULT_TYPE, "");
                    if (AiDietEntryBottomSheet.TYPE_IMAGE.equals(type)) {
                        ensureFoodImageAccess();
                    } else if (AiDietEntryBottomSheet.TYPE_BARCODE.equals(type)) {
                        launchBarcodeScanner();
                    } else if (AiDietEntryBottomSheet.TYPE_TEXT.equals(type)) {
                        analyzeDietText(bundle.getString(AiDietEntryBottomSheet.RESULT_TEXT, ""));
                    }
                });
    }

    private void showAiDietEntry() {
        if (getChildFragmentManager().findFragmentByTag(AiDietEntryBottomSheet.TAG) != null) {
            return;
        }
        new AiDietEntryBottomSheet().show(getChildFragmentManager(), AiDietEntryBottomSheet.TAG);
    }

    private void analyzeDietText(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return;
        }
        ImageMealDraft cached = aiDraftViewModel.getCachedDietDraft(normalized);
        if (cached != null && pendingLibraryDraftForAi == null) {
            cached.setSuggestion("已复用上次饮食整理结果，请确认后保存");
            showFoodConfirmSheet(cached);
            return;
        }

        binding.btnAiFoodRecord.setEnabled(false);
        Toast.makeText(requireContext(), "正在查询本地饮食库…", Toast.LENGTH_SHORT).show();
        imageExecutorService.execute(() -> {
            ImageMealDraft libraryDraft = null;
            try {
                libraryDraft = DietLibraryTextMatcher.match(normalized, viewModel.getAllFoodsSync());
            } catch (Exception ignored) {
                // A local database failure should not prevent the explicit AI fallback.
            }
            final ImageMealDraft matchedDraft = libraryDraft;
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (matchedDraft != null && matchedDraft.getItems() != null
                        && !matchedDraft.getItems().isEmpty()) {
                    binding.btnAiFoodRecord.setEnabled(true);
                    aiDraftViewModel.cacheDietDraft(normalized, matchedDraft);
                    showFoodConfirmSheet(matchedDraft);
                } else {
                    Toast.makeText(requireContext(), "饮食库未找到匹配项，正在估算…", Toast.LENGTH_SHORT).show();
                    analyzeDietWithAi(normalized);
                }
            });
        });
    }

    /** Runs the explicit AI action from the confirmation sheet with visible retry feedback. */
    private void analyzeDietWithAi(String text, boolean forceAi) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            showAiRecognitionError("没有可识别的文字，请返回修改餐名或食物名称。", false);
            return;
        }
        pendingAiRequestText = normalized;
        pendingAiRequestForce = forceAi;

        ImageMealDraft cached = forceAi ? null : aiDraftViewModel.getCachedDietDraft(normalized);
        if (cached != null && pendingLibraryDraftForAi == null) {
            cached.setSuggestion("已复用上次 AI 整理结果，请确认后保存");
            showFoodConfirmSheet(cached);
            return;
        }

        binding.btnAiFoodRecord.setEnabled(false);
        Toast.makeText(requireContext(), "正在用 AI 整理饮食信息…", Toast.LENGTH_SHORT).show();
        AiDietTextAnalyzer.analyze(normalized, new AiDietTextAnalyzer.Callback() {
            @Override
            public void onSuccess(ImageMealDraft draft) {
                if (!isAdded() || binding == null) {
                    return;
                }
                draft.setSourceType(ImageMealDraft.SOURCE_TEXT);
                draft.setOriginalText(normalized);
                markAiItems(draft);
                if (!forceAi) {
                    draft = mergePendingLibraryDraft(draft);
                } else {
                    pendingLibraryDraftForAi = null;
                }
                draft.setSuggestion(forceAi
                        ? "来源：AI 识别。请确认食物名称、克数和营养数据后保存。"
                        : "已补充本地食物库未匹配内容，请确认食物名称、克数和营养数据后保存。");
                aiDraftViewModel.cacheDietDraft(normalized, draft);
                binding.btnAiFoodRecord.setEnabled(true);
                showFoodConfirmSheet(draft);
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || binding == null) {
                    return;
                }
                binding.btnAiFoodRecord.setEnabled(true);
                showAiRecognitionError(message, true);
            }
        });
    }

    private void markAiItems(ImageMealDraft draft) {
        if (draft == null || draft.getItems() == null) return;
        for (ImageFoodItemDraft item : draft.getItems()) {
            if (item != null) item.setSourceType(ImageFoodItemDraft.SOURCE_AI);
        }
    }

    private void showAiRecognitionError(String message, boolean canRetry) {
        if (!isAdded()) return;
        String displayMessage = message == null || message.trim().isEmpty()
                ? "AI 暂时没有返回识别结果，请稍后重试。" : message;
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("AI 识别失败")
                        .setMessage(displayMessage)
                        .setNegativeButton("返回修改", null);
        if (canRetry) {
            builder.setPositiveButton("重试", (dialog, which) ->
                    analyzeDietWithAi(pendingAiRequestText, pendingAiRequestForce));
        } else {
            builder.setPositiveButton("知道了", null);
        }
        builder.show();
    }

    private void analyzeDietWithAi(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return;
        }
        ImageMealDraft cached = aiDraftViewModel.getCachedDietDraft(normalized);
        if (cached != null) {
            cached = mergePendingLibraryDraft(cached);
            cached.setSuggestion("已复用上次文字解析结果，请确认后保存");
            showFoodConfirmSheet(cached);
            return;
        }
        binding.btnAiFoodRecord.setEnabled(false);
        Toast.makeText(requireContext(), "正在整理饮食信息…", Toast.LENGTH_SHORT).show();
        AiDietTextAnalyzer.analyze(normalized, new AiDietTextAnalyzer.Callback() {
            @Override
            public void onSuccess(ImageMealDraft draft) {
                if (!isAdded() || binding == null) {
                    return;
                }
                draft.setSourceType(ImageMealDraft.SOURCE_TEXT);
                draft.setOriginalText(normalized);
                binding.btnAiFoodRecord.setEnabled(true);
                draft = mergePendingLibraryDraft(draft);
                draft.setSuggestion("来源：文字描述。请确认数量、单位和营养数据后保存");
                aiDraftViewModel.cacheDietDraft(normalized, draft);
                showFoodConfirmSheet(draft);
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || binding == null) {
                    return;
                }
                pendingLibraryDraftForAi = null;
                binding.btnAiFoodRecord.setEnabled(true);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private ImageMealDraft mergePendingLibraryDraft(ImageMealDraft aiDraft) {
        if (pendingLibraryDraftForAi == null) return aiDraft;
        pendingLibraryDraftForAi.getItems().addAll(aiDraft.getItems());
        pendingLibraryDraftForAi.setSourceType(ImageMealDraft.SOURCE_TEXT);
        pendingLibraryDraftForAi.setUnmatchedText("");
        pendingLibraryDraftForAi.setSuggestion("已补全本地饮食库未匹配文本，请确认后保存");
        pendingLibraryDraftForAi.recomputeTotals();
        ImageMealDraft merged = pendingLibraryDraftForAi;
        pendingLibraryDraftForAi = null;
        return merged;
    }

    private void showBarcodeDraft(OpenFoodFactsService.FoodResult food) {
        if (!isAdded() || food == null) {
            return;
        }
        double weight = Math.max(1, food.weightPerUnit);
        double ratio = weight / 100d;
        ImageMealDraft draft = new ImageMealDraft();
        draft.setSourceType(ImageMealDraft.SOURCE_BARCODE);
        draft.setMealName(food.name == null || food.name.trim().isEmpty() ? "包装食品" : food.name.trim());
        draft.setMealType(1);
        draft.setSuggestion("来源：条码营养数据。请确认包装份量后保存");
        ImageFoodItemDraft item = new ImageFoodItemDraft(draft.getMealName(),
                (int) Math.round(Math.max(0, food.caloriesPer100g) * ratio),
                Math.max(0, food.proteinPer100g) * ratio,
                Math.max(0, food.carbsPer100g) * ratio,
                Math.max(0, food.fatPer100g) * ratio,
                1d,
                food.servingUnit == null || food.servingUnit.trim().isEmpty() ? "份" : food.servingUnit,
                FoodCategoryUtils.CAT_OTHER);
        item.setNutritionBasis(ImageFoodItemDraft.BASIS_TOTAL_PORTION);
        item.setEstimatedWeightGrams(weight);
        item.setNeedsReview(food.weightPerUnit <= 0 || food.name == null || food.name.trim().isEmpty());
        draft.getItems().add(item);
        draft.recomputeTotals();
        showFoodConfirmSheet(draft);
    }

    private void showImageSourcePicker() {
        String[] options = { "拍照识别", "从相册选择", "查看服务端额度" };
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
                    } else if (which == 1) {
                        mediaPickerLauncher.launch("image/*");
                    } else {
                        showFoodImageQuotaDialog();
                    }
                })
                .show();
    }

    private void ensureFoodImageAccess() {
        User user = viewModel.getCurrentUser().getValue();
        if (user == null) {
            Toast.makeText(requireContext(), "用户信息尚未加载，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        showImageSourcePicker();
    }

    private void showFoodImageQuotaDialog() {
        User user = viewModel.getCurrentUser().getValue();
        if (user == null) {
            Toast.makeText(requireContext(), "用户信息尚未加载，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        int localUsed = foodImageQuotaStore.getUsedCount(user);
        String message = "当前为本地私人模式，图片识别不会保存到 FitnessDiary 云端。\n"
                + "本机已发起约 " + localUsed + " 次识别请求；清除应用数据后计数会重置。\n"
                + "实际费用和服务限制以 MiMo 平台账号为准。";
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("图片识别额度")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void launchBarcodeScanner() {
        com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
        options.setPrompt("将条形码置于扫描框内");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        barcodeLauncher.launch(options);
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
            viewModel.forceAnalyzeMealImage(lastScanBitmap);
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

    /**
     * 在后台线程加载今日能量状态数据并更新 UI
     */
    private void loadEnergyStatusBar() {
        executorService.execute(() -> {
            try {
                HealthAggregationRepository repo = new HealthAggregationRepository(
                        requireActivity().getApplication());
                DailyHealthSnapshot snapshot = repo.getTodaySnapshot();
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> bindEnergyStatus(snapshot));
                }
            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            ErrorHandler.showError(DietFragment.this, "加载能量数据失败", null));
                }
            }
        });
    }

    /**
     * 将今日快照数据绑定到能量状态横条
     */
    private void bindEnergyStatus(DailyHealthSnapshot snapshot) {
        // 布局已移除，无需执行绑定
    }

    /**
     * 将高频食物添加到收藏
     */
    private void addFrequentFoodToFavorites(String foodName) {
        imageExecutorService.execute(() -> {
            com.cz.fitnessdiary.database.entity.FoodLibrary found = null;
            java.util.List<com.cz.fitnessdiary.database.entity.FoodLibrary> allFoods = viewModel.getAllFoodsSync();
            if (allFoods != null) {
                for (com.cz.fitnessdiary.database.entity.FoodLibrary f : allFoods) {
                    if (f.getName().equals(foodName)) {
                        found = f;
                        break;
                    }
                }
            }
            if (getActivity() == null) return;
            final com.cz.fitnessdiary.database.entity.FoodLibrary food = found;
            getActivity().runOnUiThread(() -> {
                if (food != null) {
                    com.cz.fitnessdiary.database.entity.FavoriteFood fav = new com.cz.fitnessdiary.database.entity.FavoriteFood(
                            food.getName(),
                            food.getCaloriesPer100g(),
                            (float) food.getProteinPer100g(),
                            (float) food.getCarbsPer100g(),
                            (float) food.getFatPer100g(),
                            food.getId(),
                            System.currentTimeMillis()
                    );
                    recipeViewModel.addFavoriteFood(fav);
                    Toast.makeText(getContext(), "⭐ 已收藏 " + foodName, Toast.LENGTH_SHORT).show();
                } else {
                    // 不在食物库中，用默认值收藏
                    com.cz.fitnessdiary.database.entity.FavoriteFood fav = new com.cz.fitnessdiary.database.entity.FavoriteFood(
                            foodName, 100, 0, 0, 0, null, System.currentTimeMillis()
                    );
                    recipeViewModel.addFavoriteFood(fav);
                    Toast.makeText(getContext(), "⭐ 已收藏 " + foodName, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    public void showPageGuide(GuideStateManager guideManager) {
        // 饮食页无需提示引导
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







