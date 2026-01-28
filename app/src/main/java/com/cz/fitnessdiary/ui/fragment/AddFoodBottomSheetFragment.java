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
import java.util.concurrent.Executors;

/**
 * 升级版添加食物底栏弹窗
 */
public class AddFoodBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentAddFoodBottomSheetBinding binding;
    private DietViewModel viewModel;
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

        if (getArguments() != null) {
            mealType = getArguments().getInt("mealType", 3);
            if (getArguments().containsKey("selectedFood")) {
                selectedFood = (FoodLibrary) getArguments().getSerializable("selectedFood");
            }
        }

        setupAutoComplete();
        setupListeners();

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
        if (selectedFood == null)
            return;

        String servingsStr = binding.etServings.getText().toString().trim();
        if (servingsStr.isEmpty()) {
            binding.tvAutoCalories.setText("预估摄入: 0 千卡");
            return;
        }

        try {
            float servings = Float.parseFloat(servingsStr);
            double totalWeight = servings * selectedFood.getWeightPerUnit();
            int calories = (int) (selectedFood.getCaloriesPer100g() * (totalWeight / 100.0));
            binding.tvAutoCalories.setText("预估摄入: " + calories + " 千卡");
        } catch (NumberFormatException e) {
            binding.tvAutoCalories.setText("预估摄入: 0 千卡");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
