package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.databinding.FragmentAddFoodBottomSheetBinding;
import com.cz.fitnessdiary.viewmodel.DietViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import com.cz.fitnessdiary.database.entity.FavoriteFood;
import com.cz.fitnessdiary.database.entity.Recipe;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.viewmodel.RecipeViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 升级版添加食物底栏弹窗
 */
public class AddFoodBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentAddFoodBottomSheetBinding binding;
    private DietViewModel viewModel;
    private RecipeViewModel recipeViewModel;
    private int mealType = 3; // 默认加餐
    private FoodLibrary selectedFood = null;
    private final List<FoodLibrary> allFoodsCache = new ArrayList<>();

    public static AddFoodBottomSheetFragment newInstance(int mealType, @Nullable FoodLibrary preSelectedFood) {
        AddFoodBottomSheetFragment fragment = new AddFoodBottomSheetFragment();
        Bundle args = new Bundle();
        args.putInt("mealType", mealType);
        if (preSelectedFood != null) {
            args.putSerializable("selectedFood", preSelectedFood);
        }
        fragment.setArguments(args);
        return fragment;
    }

    public static AddFoodBottomSheetFragment newInstance(int mealType) {
        return newInstance(mealType, null);
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAddFoodBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DietViewModel.class);
        recipeViewModel = new ViewModelProvider(requireActivity()).get(RecipeViewModel.class);

        if (getArguments() != null) {
            mealType = getArguments().getInt("mealType", 3);
            if (getArguments().containsKey("selectedFood")) {
                selectedFood = (FoodLibrary) getArguments().getSerializable("selectedFood");
            }
        }

        setupAutoComplete();
        setupListeners();
        setupActionButtons();

        // 核心修复：如果已预选食物，则重显 UI
        if (selectedFood != null) {
            binding.etFoodName.setText(selectedFood.getName());
            updateUIWithSelectedFood();
        }
    }

    private void setupAutoComplete() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<FoodLibrary> foods = viewModel.getAllFoodsSync();
            if (foods != null) {
                allFoodsCache.clear();
                allFoodsCache.addAll(foods);

                requireActivity().runOnUiThread(() -> {
                    com.cz.fitnessdiary.ui.adapter.FoodAutoCompleteAdapter adapter = new com.cz.fitnessdiary.ui.adapter.FoodAutoCompleteAdapter(
                            getContext(), allFoodsCache);
                    binding.etFoodName.setAdapter(adapter);
                });
            }
        });

        binding.etFoodName.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (item instanceof FoodLibrary) {
                selectedFood = (FoodLibrary) item;
                updateUIWithSelectedFood();
            }
        });
    }

    private void setupListeners() {
        binding.etServings.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCaloriePreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.btnSave.setOnClickListener(v -> {
            String foodName = binding.etFoodName.getText().toString().trim();
            String servingsStr = binding.etServings.getText().toString().trim();

            if (foodName.isEmpty() || servingsStr.isEmpty()) {
                Toast.makeText(getContext(), "请输入食物名称和份数", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                float servings = Float.parseFloat(servingsStr);
                viewModel.addFoodRecordSmart(foodName, servings, mealType);
                Toast.makeText(getContext(), "记录成功", Toast.LENGTH_SHORT).show();
                dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "请输入有效的份数", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUIWithSelectedFood() {
        if (selectedFood != null) {
            binding.tvUnit.setText(selectedFood.getServingUnit());
            updateCaloriePreview();
        }
    }

    private void updateCaloriePreview() {
        if (selectedFood == null) {
            binding.tvAutoCalories.setText("预估摄入: 0 千卡");
            binding.tvMacroPreview.setText("蛋白质: 0.0g · 碳水: 0.0g · 脂肪: 0.0g · 1份约0g");
            return;
        }

        String servingsStr = binding.etServings.getText().toString().trim();
        if (servingsStr.isEmpty()) {
            binding.tvAutoCalories.setText("预估摄入: 0 千卡");
            String unit = selectedFood.getServingUnit() == null || selectedFood.getServingUnit().trim().isEmpty()
                    ? "份"
                    : selectedFood.getServingUnit().trim();
            binding.tvMacroPreview
                    .setText(String.format(Locale.getDefault(), "蛋白质: 0.0g · 碳水: 0.0g · 脂肪: 0.0g · 1%s约%dg", unit,
                            selectedFood.getWeightPerUnit()));
            return;
        }

        try {
            float servings = Float.parseFloat(servingsStr);
            double totalWeight = servings * selectedFood.getWeightPerUnit();
            int calories = (int) Math.round(selectedFood.getCaloriesPer100g() * (totalWeight / 100.0));
            double protein = selectedFood.getProteinPer100g() * (totalWeight / 100.0);
            double carbs = selectedFood.getCarbsPer100g() * (totalWeight / 100.0);
                double fat = selectedFood.getFatPer100g() * (totalWeight / 100.0);
            String unit = selectedFood.getServingUnit() == null || selectedFood.getServingUnit().trim().isEmpty()
                    ? "份"
                    : selectedFood.getServingUnit().trim();

            binding.tvAutoCalories.setText("预估摄入: " + calories + " 千卡");
            binding.tvMacroPreview.setText(String.format(Locale.getDefault(),
                    "蛋白质: %.1fg · 碳水: %.1fg · 脂肪: %.1fg · 1%s约%dg", protein, carbs, fat, unit,
                    selectedFood.getWeightPerUnit()));
        } catch (NumberFormatException e) {
            binding.tvAutoCalories.setText("预估摄入: 0 千卡");
            String unit = selectedFood.getServingUnit() == null || selectedFood.getServingUnit().trim().isEmpty()
                    ? "份"
                    : selectedFood.getServingUnit().trim();
            binding.tvMacroPreview
                    .setText(String.format(Locale.getDefault(), "蛋白质: 0.0g · 碳水: 0.0g · 脂肪: 0.0g · 1%s约%dg", unit,
                            selectedFood.getWeightPerUnit()));
        }
    }

    private void setupActionButtons() {
        binding.btnFavorite.setOnClickListener(v -> onFavoriteFood());
        binding.btnAddToRecipe.setOnClickListener(v -> onAddFoodToRecipe());
    }

    /**
     * 获取当前食物数据（从 selectedFood 或通过文本查找）
     */
    private void resolveCurrentFoodData(OnFoodDataResolved callback) {
        if (selectedFood != null) {
            callback.onResolved(
                    selectedFood.getName(),
                    selectedFood.getCaloriesPer100g(),
                    selectedFood.getProteinPer100g(),
                    selectedFood.getCarbsPer100g(),
                    selectedFood.getFatPer100g(),
                    selectedFood.getId()
            );
            return;
        }
        String foodName = binding.etFoodName.getText().toString().trim();
        if (foodName.isEmpty()) {
            Toast.makeText(getContext(), "请先输入食物名称", Toast.LENGTH_SHORT).show();
            return;
        }
        // 在后台查找食物库
        Executors.newSingleThreadExecutor().execute(() -> {
            FoodLibraryRepository repo = new FoodLibraryRepository(requireActivity().getApplication());
            FoodLibrary found = repo.getFoodByName(foodName);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (found != null) {
                        callback.onResolved(
                                found.getName(),
                                found.getCaloriesPer100g(),
                                found.getProteinPer100g(),
                                found.getCarbsPer100g(),
                                found.getFatPer100g(),
                                found.getId()
                        );
                    } else {
                        // 不在食物库中，用默认值
                        callback.onResolved(foodName, 100, 0, 0, 0, null);
                    }
                });
            }
        });
    }

    private interface OnFoodDataResolved {
        void onResolved(String name, int caloriesPer100g, double proteinPer100g,
                        double carbsPer100g, double fatPer100g, Long foodLibraryId);
    }

    private void onFavoriteFood() {
        resolveCurrentFoodData((name, caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g, foodLibraryId) -> {
            String servingsStr = binding.etServings.getText().toString().trim();
            float servings = 1.0f;
            try {
                servings = Float.parseFloat(servingsStr);
            } catch (NumberFormatException ignored) {}

            // 计算总营养（根据当前份数）
            float totalCalories = (float) (caloriesPer100g * servings * 100.0 / 100.0);
            float totalProtein = (float) (proteinPer100g * servings * 100.0 / 100.0);
            float totalCarbs = (float) (carbsPer100g * servings * 100.0 / 100.0);
            float totalFat = (float) (fatPer100g * servings * 100.0 / 100.0);

            // 如果是选中的食物，用 weightPerUnit 计算实际重量
            if (selectedFood != null) {
                double totalWeight = servings * selectedFood.getWeightPerUnit();
                double ratio = totalWeight / 100.0;
                totalCalories = (float) (selectedFood.getCaloriesPer100g() * ratio);
                totalProtein = (float) (selectedFood.getProteinPer100g() * ratio);
                totalCarbs = (float) (selectedFood.getCarbsPer100g() * ratio);
                totalFat = (float) (selectedFood.getFatPer100g() * ratio);
            }

            FavoriteFood fav = new FavoriteFood(
                    name, totalCalories, totalProtein, totalCarbs, totalFat,
                    foodLibraryId, System.currentTimeMillis()
            );
            recipeViewModel.addFavoriteFood(fav);
            Toast.makeText(getContext(), "⭐ 已收藏 " + name, Toast.LENGTH_SHORT).show();
        });
    }

    private void onAddFoodToRecipe() {
        resolveCurrentFoodData((name, caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g, foodLibraryId) -> {
            String servingsStr = binding.etServings.getText().toString().trim();
            float servings = 1.0f;
            try {
                servings = Float.parseFloat(servingsStr);
            } catch (NumberFormatException ignored) {}

            // 计算当前份的营养
            float totalCalories = (float) (caloriesPer100g * servings * 100.0 / 100.0);
            float totalProtein = (float) (proteinPer100g * servings * 100.0 / 100.0);
            float totalCarbs = (float) (carbsPer100g * servings * 100.0 / 100.0);
            float totalFat = (float) (fatPer100g * servings * 100.0 / 100.0);
            String servingUnit = selectedFood != null ? selectedFood.getServingUnit() : "份";

            if (selectedFood != null) {
                double totalWeight = servings * selectedFood.getWeightPerUnit();
                double ratio = totalWeight / 100.0;
                totalCalories = (float) (selectedFood.getCaloriesPer100g() * ratio);
                totalProtein = (float) (selectedFood.getProteinPer100g() * ratio);
                totalCarbs = (float) (selectedFood.getCarbsPer100g() * ratio);
                totalFat = (float) (selectedFood.getFatPer100g() * ratio);
            }

            final float finalCalories = totalCalories;
            final float finalProtein = totalProtein;
            final float finalCarbs = totalCarbs;
            final float finalFat = totalFat;
            final String finalUnit = servingUnit;
            final float finalServings = servings;

            // 加载现有食谱
            recipeViewModel.loadRecipes(() -> {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    List<Recipe> recipes = recipeViewModel.getRecipeList();
                    String[] recipeNames = new String[(recipes != null ? recipes.size() : 0) + 1];
                    if (recipes != null) {
                        for (int i = 0; i < recipes.size(); i++) {
                            recipeNames[i] = recipes.get(i).getName();
                        }
                    }
                    recipeNames[recipeNames.length - 1] = "➕ 新建食谱";

                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("将 " + name + " 加入食谱")
                            .setItems(recipeNames, (dialog, which) -> {
                                if (which == recipeNames.length - 1) {
                                    // 新建食谱
                                    showCreateRecipeDialog(name, finalCalories, finalProtein,
                                            finalCarbs, finalFat, finalUnit, finalServings);
                                } else {
                                    // 添加到现有食谱
                                    Recipe recipe = recipes.get(which);
                                    addFoodToExistingRecipe(recipe, name, finalCalories,
                                            finalProtein, finalCarbs, finalFat, finalUnit, finalServings);
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                });
            });
        });
    }

    private void showCreateRecipeDialog(String foodName, float calories, float protein,
                                        float carbs, float fat, String unit, float servings) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_recipe_name_input, null);
        com.google.android.material.textfield.TextInputEditText etName = dialogView.findViewById(R.id.et_recipe_name);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("新建食谱")
                .setView(dialogView)
                .setPositiveButton("创建", (d, w) -> {
                    String recipeName = etName.getText().toString().trim();
                    if (recipeName.isEmpty()) {
                        Toast.makeText(getContext(), "请输入食谱名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 构建 foods_json
                    JsonObject foodObj = new JsonObject();
                    foodObj.addProperty("foodName", foodName);
                    foodObj.addProperty("calories", calories);
                    foodObj.addProperty("protein", protein);
                    foodObj.addProperty("carbs", carbs);
                    foodObj.addProperty("fat", fat);
                    foodObj.addProperty("servingUnit", unit);
                    foodObj.addProperty("servings", servings);
                    JsonArray foodsArr = new JsonArray();
                    foodsArr.add(foodObj);

                    long now = System.currentTimeMillis();
                    Recipe recipe = new Recipe(recipeName, new Gson().toJson(foodsArr),
                            calories, -1, false, now, now);
                    recipeViewModel.saveRecipe(recipe);
                    Toast.makeText(getContext(), "📋 已创建食谱「" + recipeName + "」", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addFoodToExistingRecipe(Recipe recipe, String foodName, float calories,
                                         float protein, float carbs, float fat, String unit, float servings) {
        try {
            Gson gson = new Gson();
            JsonArray foodsArr = gson.fromJson(recipe.getFoodsJson(), JsonArray.class);
            if (foodsArr == null) {
                foodsArr = new JsonArray();
            }
            JsonObject foodObj = new JsonObject();
            foodObj.addProperty("foodName", foodName);
            foodObj.addProperty("calories", calories);
            foodObj.addProperty("protein", protein);
            foodObj.addProperty("carbs", carbs);
            foodObj.addProperty("fat", fat);
            foodObj.addProperty("servingUnit", unit);
            foodObj.addProperty("servings", servings);
            foodsArr.add(foodObj);

            recipe.setFoodsJson(gson.toJson(foodsArr));
            recipe.setTotalCalories(recipe.getTotalCalories() + calories);
            recipe.setUpdatedAt(System.currentTimeMillis());
            recipeViewModel.saveRecipe(recipe);
            Toast.makeText(getContext(), "📋 已加入食谱「" + recipe.getName() + "」", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "加入食谱失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
