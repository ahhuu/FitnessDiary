package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FavoriteFood;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.Recipe;
import com.cz.fitnessdiary.databinding.FragmentRecipeListBinding;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.ui.adapter.FavoriteFoodAdapter;
import com.cz.fitnessdiary.ui.adapter.RecipeAdapter;
import com.cz.fitnessdiary.viewmodel.RecipeViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.concurrent.Executors;

public class RecipeListFragment extends Fragment {

    private FragmentRecipeListBinding binding;
    private RecipeViewModel viewModel;
    private RecipeAdapter recipeAdapter;
    private FavoriteFoodAdapter favAdapter;
    private boolean isRecipeTab = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRecipeListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RecipeViewModel.class);

        setupTabs();
        setupAdapters();
        setupFab();

        loadData();
    }

    private void setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("我的食谱"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("收藏食物"));
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isRecipeTab = (tab.getPosition() == 0);
                updateContentVisibility();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        updateContentVisibility(); // Ensure initial tab shows content
    }

    private void setupAdapters() {
        recipeAdapter = new RecipeAdapter(new RecipeAdapter.OnRecipeActionListener() {
            @Override
            public void onRecord(Recipe recipe) {
                showMealPickerForRecipe(recipe);
            }
            @Override
            public void onDelete(Recipe recipe) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除食谱")
                        .setMessage("确定要删除「" + recipe.getName() + "」吗？")
                        .setPositiveButton("删除", (d, w) -> {
                            new Thread(() -> {
                                viewModel.deleteRecipe(recipe);
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> loadData());
                                }
                            }).start();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
            @Override
            public void onEdit(Recipe recipe) {
                showEditRecipeDialog(recipe);
            }
        });

        favAdapter = new FavoriteFoodAdapter(new FavoriteFoodAdapter.OnFavoriteFoodActionListener() {
            @Override
            public void onRecord(FavoriteFood food) {
                showMealPickerForFavFood(food);
            }
            @Override
            public void onRemove(FavoriteFood food) {
                viewModel.deleteFavoriteFood(food);
                loadData();
                Toast.makeText(getContext(), "已取消收藏", Toast.LENGTH_SHORT).show();
            }
        });

        binding.rvContent.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvContent.setAdapter(recipeAdapter); // Set initial adapter
    }

    private void setupFab() {
        binding.fabAdd.setOnClickListener(v -> {
            if (isRecipeTab) {
                showCreateRecipeDialog();
            } else {
                showFavoriteFoodPickerDialog();
            }
        });
    }

    /**
     * 创建新食谱对话框
     */
    private void showCreateRecipeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_recipe_name_input, null);
        TextInputEditText etName = dialogView.findViewById(R.id.et_recipe_name);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("创建新食谱")
                .setMessage("输入食谱名称，稍后可从饮食页面添加食物")
                .setView(dialogView)
                .setPositiveButton("创建", (d, w) -> {
                    String recipeName = etName.getText().toString().trim();
                    if (recipeName.isEmpty()) {
                        Toast.makeText(getContext(), "请输入食谱名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long now = System.currentTimeMillis();
                    Recipe recipe = new Recipe(recipeName, "[]", 0, -1, false, now, now);
                    viewModel.saveRecipe(recipe);
                    Toast.makeText(getContext(), "📋 已创建食谱「" + recipeName + "」", Toast.LENGTH_SHORT).show();
                    loadData();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 收藏食物选择器 - 从食物库搜索并收藏
     */
    private void showFavoriteFoodPickerDialog() {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(),
                android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        View view = getLayoutInflater().inflate(R.layout.dialog_food_wiki, null);
        dialog.setContentView(view);

        android.widget.EditText etSearch = view.findViewById(R.id.et_search_query);
        View btnBack = view.findViewById(R.id.btn_back);
        androidx.recyclerview.widget.RecyclerView rvResults = view.findViewById(R.id.rv_food_results);
        View fabAddFood = view.findViewById(R.id.fab_add_food);
        fabAddFood.setVisibility(View.GONE); // 简化UI，隐藏添加食物按钮

        btnBack.setOnClickListener(v -> dialog.dismiss());

        com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter adapter =
                new com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter();
        rvResults.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        rvResults.setAdapter(adapter);

        adapter.setOnItemClickListener(food -> {
            // 收藏选中的食物
            FavoriteFood fav = new FavoriteFood(
                    food.getName(),
                    food.getCaloriesPer100g(),
                    (float) food.getProteinPer100g(),
                    (float) food.getCarbsPer100g(),
                    (float) food.getFatPer100g(),
                    food.getId(),
                    System.currentTimeMillis()
            );
            viewModel.addFavoriteFood(fav);
            dialog.dismiss();
            Toast.makeText(getContext(), "⭐ 已收藏 " + food.getName(), Toast.LENGTH_SHORT).show();
            loadData();
        });

        Executors.newSingleThreadExecutor().execute(() -> {
            FoodLibraryRepository repo = new FoodLibraryRepository(requireActivity().getApplication());
            java.util.List<FoodLibrary> allFoods = repo.getAllFoodsSync();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> adapter.setFoodList(allFoods));
            }
        });

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();
                Executors.newSingleThreadExecutor().execute(() -> {
                    FoodLibraryRepository repo = new FoodLibraryRepository(requireActivity().getApplication());
                    java.util.List<FoodLibrary> results;
                    if (query.isEmpty()) {
                        results = repo.getAllFoodsSync();
                    } else {
                        results = repo.searchFoods(query);
                    }
                    if (getActivity() != null) {
                        java.util.List<FoodLibrary> finalResults = results;
                        getActivity().runOnUiThread(() -> adapter.setFoodList(finalResults));
                    }
                });
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        dialog.show();
    }

    private void loadData() {
        if (isRecipeTab) {
            viewModel.loadRecipes(() -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> recipeAdapter.setRecipes(viewModel.getRecipeList()));
                }
            });
        } else {
            viewModel.loadFavoriteFoods(() -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> favAdapter.setFoods(viewModel.getFavoriteFoodList()));
                }
            });
        }
    }

    private void updateContentVisibility() {
        if (isRecipeTab) {
            binding.rvContent.setAdapter(recipeAdapter);
        } else {
            binding.rvContent.setAdapter(favAdapter);
        }
        loadData();
    }

    private void showMealPickerForRecipe(Recipe recipe) {
        String[] meals = {"☀️ 早餐", "🌞 午餐", "🌙 晚餐", "🍪 加餐"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("记录「" + recipe.getName() + "」到...")
                .setItems(meals, (dialog, which) -> {
                    recordRecipeToMeal(recipe, which);
                    recipe.setUpdatedAt(System.currentTimeMillis());
                    viewModel.saveRecipe(recipe);
                    Toast.makeText(getContext(), "✅ 已记录 " + recipe.getName(), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showMealPickerForFavFood(FavoriteFood food) {
        String[] meals = {"☀️ 早餐", "🌞 午餐", "🌙 晚餐", "🍪 加餐"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("记录「" + food.getFoodName() + "」到...")
                .setItems(meals, (dialog, which) -> {
                    recordFoodToMeal(food, which);
                    Toast.makeText(getContext(), "✅ 已添加 1 份 " + food.getFoodName(), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void recordRecipeToMeal(Recipe recipe, int mealType) {
        Gson gson = new Gson();
        JsonArray foods = gson.fromJson(recipe.getFoodsJson(), JsonArray.class);
        FoodRecordRepository foodRepo = new FoodRecordRepository(requireActivity().getApplication());

        for (int i = 0; i < foods.size(); i++) {
            JsonObject item = foods.get(i).getAsJsonObject();
            FoodRecord record = new FoodRecord();
            record.setFoodName(item.get("foodName").getAsString());
            record.setCalories((int) item.get("calories").getAsFloat());
            record.setProtein(item.get("protein").getAsFloat());
            record.setCarbs(item.get("carbs").getAsFloat());
            record.setFat(item.has("fat") ? item.get("fat").getAsFloat() : 0f);
            record.setRecordDate(System.currentTimeMillis());
            record.setMealType(mealType);
            record.setServings(item.has("servings") ? item.get("servings").getAsFloat() : 1.0f);
            record.setServingUnit(item.has("servingUnit") ? item.get("servingUnit").getAsString() : "份");
            foodRepo.insert(record);
        }
    }

    private void recordFoodToMeal(FavoriteFood food, int mealType) {
        FoodRecordRepository foodRepo = new FoodRecordRepository(requireActivity().getApplication());
        FoodRecord record = new FoodRecord();
        record.setFoodName(food.getFoodName());
        record.setCalories((int) food.getCalories());
        record.setProtein(food.getProtein());
        record.setCarbs(food.getCarbs());
        record.setFat(food.getFat());
        record.setRecordDate(System.currentTimeMillis());
        record.setMealType(mealType);
        record.setServings(1.0f);
        record.setServingUnit("份");
        foodRepo.insert(record);
    }

    // ==================== 编辑食谱（添加食物） ====================

    /**
     * 编辑食谱对话框 — 显示当前食物列表，并支持添加新食物
     */
    private void showEditRecipeDialog(Recipe recipe) {
        Gson gson = new Gson();
        JsonArray foodsArray = gson.fromJson(recipe.getFoodsJson(), JsonArray.class);
        if (foodsArray == null) foodsArray = new JsonArray();
        JsonArray finalFoodsArray = foodsArray;

        float density = getResources().getDisplayMetrics().density;
        int p16 = (int) (16 * density);
        int p8 = (int) (8 * density);
        int p4 = (int) (4 * density);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(p16, p8, p16, p8);

        // 摘要信息
        TextView tvSummary = new TextView(requireContext());
        tvSummary.setText("总热量: " + (int) recipe.getTotalCalories() + " kcal  |  共 "
                + finalFoodsArray.size() + " 种食物");
        tvSummary.setTextSize(14);
        tvSummary.setTextColor(0xFFE85D3A);
        tvSummary.setPadding(0, 0, 0, p8);
        container.addView(tvSummary);

        // 使用数组持有dialog引用，使内部类可以访问
        final androidx.appcompat.app.AlertDialog[] dialogRef = new androidx.appcompat.app.AlertDialog[1];

        // 食物列表
        if (finalFoodsArray.size() == 0) {
            TextView tvEmpty = new TextView(requireContext());
            tvEmpty.setText("(食谱为空，点击下方按钮添加食物)");
            tvEmpty.setTextSize(14);
            tvEmpty.setTextColor(0xFF999999);
            tvEmpty.setPadding(p8, p8, p8, p8);
            container.addView(tvEmpty);
        } else {
            for (int i = 0; i < finalFoodsArray.size(); i++) {
                final int index = i;
                JsonObject item = finalFoodsArray.get(i).getAsJsonObject();
                String foodName = item.get("foodName").getAsString();
                float cal = item.get("calories").getAsFloat();
                float servings = item.has("servings") ? item.get("servings").getAsFloat() : 1f;
                String unit = item.has("servingUnit") ? item.get("servingUnit").getAsString() : "份";

                // 水平行：食物信息 + 删除按钮
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, p8, 0, p8);

                TextView tvFood = new TextView(requireContext());
                tvFood.setText("• " + foodName + "  (" + (int) cal + " kcal, "
                        + String.format("%.1f", servings) + " " + unit + ")");
                tvFood.setTextSize(14);
                tvFood.setTextColor(0xFF333333);
                LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tvFood.setLayoutParams(tvParams);
                row.addView(tvFood);

                TextView btnDel = new TextView(requireContext());
                btnDel.setText("✕");
                btnDel.setTextSize(16);
                btnDel.setTextColor(0xFFCC4444);
                btnDel.setPadding(p16, p4, p4, p4);
                btnDel.setOnClickListener(v -> {
                    finalFoodsArray.remove(index);
                    // 重新计算总热量
                    float newTotal = 0;
                    for (int j = 0; j < finalFoodsArray.size(); j++) {
                        JsonObject fi = finalFoodsArray.get(j).getAsJsonObject();
                        newTotal += fi.get("calories").getAsFloat();
                    }
                    recipe.setFoodsJson(gson.toJson(finalFoodsArray));
                    recipe.setTotalCalories(newTotal);
                    viewModel.updateRecipe(recipe);
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    // 重新打开编辑框
                    showEditRecipeDialog(recipe);
                });
                row.addView(btnDel);

                TextView btnEdit = new TextView(requireContext());
                btnEdit.setText("✎");
                btnEdit.setTextSize(16);
                btnEdit.setTextColor(0xFF4A90D9);
                btnEdit.setPadding(p8, p4, p4, p4);
                btnEdit.setOnClickListener(v -> {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    float currentGrams = 100f;
                    if (item.has("grams")) currentGrams = item.get("grams").getAsFloat();
                    showEditFoodItemDialog(recipe, index, foodName, currentGrams);
                });
                row.addView(btnEdit);

                container.addView(row);
            }
        }

        // 分割线
        View divider = new View(requireContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFE0E0E0);
        divider.setPadding(0, p8, 0, p8);
        container.addView(divider);

        // 添加食物按钮
        com.google.android.material.button.MaterialButton btnAdd =
                new com.google.android.material.button.MaterialButton(requireContext());
        btnAdd.setText("➕ 添加食物");
        btnAdd.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(btnAdd);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑食谱 - " + recipe.getName())
                .setView(container)
                .setPositiveButton("完成", null)
                .show();
        dialogRef[0] = dialog;

        btnAdd.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            showFoodPickerForRecipe(recipe);
        });
    }

    /**
     * 全屏食物库选择器 — 从食物库搜索并选择食物添加到食谱
     */
    private void showFoodPickerForRecipe(Recipe recipe) {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(),
                android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        View view = getLayoutInflater().inflate(R.layout.dialog_food_wiki, null);
        dialog.setContentView(view);

        android.widget.EditText etSearch = view.findViewById(R.id.et_search_query);
        View btnBack = view.findViewById(R.id.btn_back);
        androidx.recyclerview.widget.RecyclerView rvResults = view.findViewById(R.id.rv_food_results);
        View fabAddFood = view.findViewById(R.id.fab_add_food);
        fabAddFood.setVisibility(View.GONE);

        btnBack.setOnClickListener(v -> dialog.dismiss());

        com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter adapter =
                new com.cz.fitnessdiary.ui.adapter.GroupedFoodLibraryAdapter();
        rvResults.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        rvResults.setAdapter(adapter);

        adapter.setOnItemClickListener(food -> {
            dialog.dismiss();
            showGramsInputForRecipe(recipe, food);
        });

        Executors.newSingleThreadExecutor().execute(() -> {
            FoodLibraryRepository repo = new FoodLibraryRepository(requireActivity().getApplication());
            java.util.List<FoodLibrary> allFoods = repo.getAllFoodsSync();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> adapter.setFoodList(allFoods));
            }
        });

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();
                Executors.newSingleThreadExecutor().execute(() -> {
                    FoodLibraryRepository repo = new FoodLibraryRepository(requireActivity().getApplication());
                    java.util.List<FoodLibrary> results;
                    if (query.isEmpty()) {
                        results = repo.getAllFoodsSync();
                    } else {
                        results = repo.searchFoods(query);
                    }
                    if (getActivity() != null) {
                        java.util.List<FoodLibrary> finalResults = results;
                        getActivity().runOnUiThread(() -> adapter.setFoodList(finalResults));
                    }
                });
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        dialog.show();
    }

    /**
     * 份量输入对话框 — 显示份数和克数双输入，自动同步
     */
    private void showGramsInputForRecipe(Recipe recipe, FoodLibrary food) {
        String servingUnit = food.getServingUnit();
        if (servingUnit == null || servingUnit.isEmpty()) servingUnit = "份";

        final int rawWeightPerUnit = food.getWeightPerUnit();
        final int weightPerUnit = rawWeightPerUnit <= 0 ? 100 : rawWeightPerUnit;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_food_to_recipe, null);

        TextView tvFoodInfo = dialogView.findViewById(R.id.tv_food_info);
        EditText etServings = dialogView.findViewById(R.id.et_servings);
        EditText etGrams = dialogView.findViewById(R.id.et_grams);
        TextView tvServingUnit = dialogView.findViewById(R.id.tv_serving_unit);
        TextView tvServingHint = dialogView.findViewById(R.id.tv_serving_hint);

        tvFoodInfo.setText(food.getName() + " — " + food.getCaloriesPer100g() + "千卡/100g");
        tvServingUnit.setText(servingUnit);
        tvServingHint.setText("（约" + weightPerUnit + "g/份）");
        etGrams.setText(String.valueOf(weightPerUnit));
        etServings.setText("1");

        // 双向同步标志，避免递归
        final boolean[] isSyncing = {false};

        etServings.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isSyncing[0]) return;
                isSyncing[0] = true;
                try {
                    float servings = Float.parseFloat(s.toString());
                    if (servings < 0) servings = 0;
                    etGrams.setText(String.valueOf((int) (servings * weightPerUnit)));
                } catch (NumberFormatException ignored) {
                }
                isSyncing[0] = false;
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        etGrams.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isSyncing[0]) return;
                isSyncing[0] = true;
                try {
                    float grams = Float.parseFloat(s.toString());
                    if (grams < 0) grams = 0;
                    etServings.setText(String.valueOf(grams / weightPerUnit));
                } catch (NumberFormatException ignored) {
                }
                isSyncing[0] = false;
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加「" + food.getName() + "」到食谱")
                .setView(dialogView)
                .setPositiveButton("添加", (d, w) -> {
                    String gramsStr = etGrams.getText().toString().trim();
                    float grams;
                    try {
                        grams = Float.parseFloat(gramsStr);
                        if (grams <= 0) grams = weightPerUnit;
                    } catch (NumberFormatException e) {
                        grams = weightPerUnit;
                    }
                    addFoodToRecipe(recipe, food, grams);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 编辑食物份量对话框 — 预填当前克数，保存时按比例重新计算营养
     */
    private void showEditFoodItemDialog(Recipe recipe, int foodIndex, String foodName, float currentGrams) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_food_to_recipe, null, false);
        TextView tvFoodInfo = dialogView.findViewById(R.id.tv_food_info);
        EditText etServings = dialogView.findViewById(R.id.et_servings);
        EditText etGrams = dialogView.findViewById(R.id.et_grams);
        TextView tvServingUnit = dialogView.findViewById(R.id.tv_serving_unit);

        tvFoodInfo.setText("编辑「" + foodName + "」的份量");
        tvServingUnit.setText("份");
        etGrams.setText(String.valueOf((int) currentGrams));
        etServings.setText(String.format("%.1f", currentGrams / 100f));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑 - " + foodName)
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    String gramsStr = etGrams.getText().toString();
                    float grams = currentGrams;
                    try { grams = Float.parseFloat(gramsStr); } catch (NumberFormatException ignored) {}
                    updateFoodInRecipe(recipe, foodIndex, grams);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 更新食谱中指定食物的份量，按比例重新计算营养值并保存
     */
    private void updateFoodInRecipe(Recipe recipe, int foodIndex, float newGrams) {
        Gson gson = new Gson();
        JsonArray foodsArray = gson.fromJson(recipe.getFoodsJson(), JsonArray.class);
        if (foodsArray == null || foodIndex >= foodsArray.size()) return;

        JsonObject item = foodsArray.get(foodIndex).getAsJsonObject();

        // Determine old grams
        float oldGrams = 100f;
        if (item.has("grams")) oldGrams = item.get("grams").getAsFloat();

        // Scale nutrition proportionally
        float scale = newGrams / oldGrams;
        item.addProperty("grams", newGrams);
        item.addProperty("calories", (float) Math.round(item.get("calories").getAsFloat() * scale * 10) / 10);
        if (item.has("protein"))
            item.addProperty("protein", (float) Math.round(item.get("protein").getAsFloat() * scale * 10) / 10);
        if (item.has("carbs"))
            item.addProperty("carbs", (float) Math.round(item.get("carbs").getAsFloat() * scale * 10) / 10);
        if (item.has("fat"))
            item.addProperty("fat", (float) Math.round(item.get("fat").getAsFloat() * scale * 10) / 10);
        item.addProperty("servings", (float) Math.round(newGrams / 100f * 10) / 10);

        // Recalculate total calories
        float newTotal = 0;
        for (int j = 0; j < foodsArray.size(); j++) {
            newTotal += foodsArray.get(j).getAsJsonObject().get("calories").getAsFloat();
        }

        recipe.setFoodsJson(gson.toJson(foodsArray));
        recipe.setTotalCalories(newTotal);
        recipe.setUpdatedAt(System.currentTimeMillis());
        viewModel.updateRecipe(recipe);
        loadData();
        // Reopen the edit dialog to reflect changes
        showEditRecipeDialog(recipe);
    }

    /**
     * 后台线程中将选中的食物添加到食谱并保存
     */
    private void addFoodToRecipe(Recipe recipe, FoodLibrary food, float grams) {
        new Thread(() -> {
            try {
                Gson gson = new Gson();
                JsonArray foodsArray = gson.fromJson(recipe.getFoodsJson(), JsonArray.class);
                if (foodsArray == null) foodsArray = new JsonArray();

                final String servingUnit;
                if (food.getServingUnit() == null || food.getServingUnit().isEmpty()) {
                    servingUnit = "份";
                } else {
                    servingUnit = food.getServingUnit();
                }
                final int rawWpu = food.getWeightPerUnit();
                final int weightPerUnit = rawWpu <= 0 ? 100 : rawWpu;
                float servings = grams / weightPerUnit;
                JsonObject newFood = new JsonObject();
                newFood.addProperty("foodName", food.getName());
                newFood.addProperty("calories", food.getCaloriesPer100g() * (grams / 100f));
                newFood.addProperty("protein", food.getProteinPer100g() * (grams / 100f));
                newFood.addProperty("carbs", food.getCarbsPer100g() * (grams / 100f));
                newFood.addProperty("fat", food.getFatPer100g() * (grams / 100f));
                newFood.addProperty("servings", servings);
                newFood.addProperty("servingUnit", servingUnit);
                foodsArray.add(newFood);

                recipe.setFoodsJson(gson.toJson(foodsArray));

                // 重新计算总热量
                float totalCal = 0;
                for (int i = 0; i < foodsArray.size(); i++) {
                    JsonObject item = foodsArray.get(i).getAsJsonObject();
                    totalCal += item.get("calories").getAsFloat();
                }
                recipe.setTotalCalories(totalCal);
                recipe.setUpdatedAt(System.currentTimeMillis());

                viewModel.updateRecipe(recipe);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        loadData();
                        Toast.makeText(getContext(), "✅ 已添加 " + food.getName()
                                + "（" + (int) grams + "g）", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
