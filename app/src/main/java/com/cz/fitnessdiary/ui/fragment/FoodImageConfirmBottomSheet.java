package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 图片识别结果确认底部面板
 */
public class FoodImageConfirmBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "FoodImageConfirmBottomSheet";
    public static final String REQUEST_KEY = "food_image_confirm_request";
    public static final String RESULT_ACTION = "result_action";
    public static final String RESULT_DRAFT = "result_draft";
    public static final String RESULT_SYNC_LIBRARY = "result_sync_library";

    public static final String ACTION_CONFIRM = "confirm";
    public static final String ACTION_RETRY = "retry";
    public static final String ACTION_CANCEL = "cancel";

    private ImageMealDraft draft;

    private TextInputEditText etMealName;
    private TextInputEditText etServings;
    private RadioGroup rgMealType;
    private LinearLayout layoutItems;
    private TextView tvSummary;
    private TextView tvSuggestion;
    private MaterialSwitch swSyncLibrary;

    public static FoodImageConfirmBottomSheet newInstance(ImageMealDraft draft) {
        FoodImageConfirmBottomSheet fragment = new FoodImageConfirmBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(RESULT_DRAFT, draft);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_food_image_confirm, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Object raw = getArguments() == null ? null : getArguments().getSerializable(RESULT_DRAFT);
        if (raw instanceof ImageMealDraft) {
            draft = (ImageMealDraft) raw;
        }
        if (draft == null) {
            draft = new ImageMealDraft();
        }

        etMealName = view.findViewById(R.id.et_meal_name);
        etServings = view.findViewById(R.id.et_servings);
        rgMealType = view.findViewById(R.id.rg_meal_type);
        layoutItems = view.findViewById(R.id.layout_items);
        tvSummary = view.findViewById(R.id.tv_summary);
        tvSuggestion = view.findViewById(R.id.tv_suggestion);
        swSyncLibrary = view.findViewById(R.id.sw_sync_library);
        MaterialButton btnAddItem = view.findViewById(R.id.btn_add_item);
        MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm);
        MaterialButton btnRetry = view.findViewById(R.id.btn_retry);
        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel);

        etMealName.setText(draft.getMealName());
        etServings.setText(String.valueOf(draft.getServings() <= 0 ? 1f : draft.getServings()));
        tvSuggestion.setText(draft.getSuggestion() == null ? "" : draft.getSuggestion());
        bindMealType(draft.getMealType());

        if (draft.getItems() == null) {
            draft.setItems(new ArrayList<>());
        }
        if (draft.getItems().isEmpty()) {
            draft.getItems().add(new ImageFoodItemDraft("请手动补充食物", 0, 0, 0, "份", "其他"));
        }
        renderItems();

        btnAddItem.setOnClickListener(v -> {
            draft.getItems().add(new ImageFoodItemDraft("", 0, 0, 0, "份", "其他"));
            renderItems();
            updateSummary();
        });

        btnConfirm.setOnClickListener(v -> {
            collectDraftFromUI();
            Bundle result = new Bundle();
            result.putString(RESULT_ACTION, ACTION_CONFIRM);
            result.putSerializable(RESULT_DRAFT, draft);
            result.putBoolean(RESULT_SYNC_LIBRARY, swSyncLibrary.isChecked());
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismissAllowingStateLoss();
        });

        btnRetry.setOnClickListener(v -> {
            collectDraftFromUI();
            Bundle result = new Bundle();
            result.putString(RESULT_ACTION, ACTION_RETRY);
            result.putSerializable(RESULT_DRAFT, draft);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismissAllowingStateLoss();
        });

        btnCancel.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString(RESULT_ACTION, ACTION_CANCEL);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismissAllowingStateLoss();
        });

        updateSummary();
    }

    private void bindMealType(int mealType) {
        switch (mealType) {
            case 0:
                rgMealType.check(R.id.rb_breakfast);
                break;
            case 2:
                rgMealType.check(R.id.rb_dinner);
                break;
            case 3:
                rgMealType.check(R.id.rb_snack);
                break;
            case 1:
            default:
                rgMealType.check(R.id.rb_lunch);
                break;
        }
    }

    private int collectMealType() {
        int checkedId = rgMealType.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_breakfast) {
            return 0;
        }
        if (checkedId == R.id.rb_dinner) {
            return 2;
        }
        if (checkedId == R.id.rb_snack) {
            return 3;
        }
        return 1;
    }

    private void collectDraftFromUI() {
        String mealName = etMealName.getText() == null ? "" : etMealName.getText().toString().trim();
        if (mealName.isEmpty()) {
            mealName = "识别餐";
        }
        draft.setMealName(mealName);
        draft.setMealType(collectMealType());

        String servingsRaw = etServings.getText() == null ? "" : etServings.getText().toString().trim();
        float servings = 1f;
        try {
            servings = Float.parseFloat(servingsRaw);
        } catch (Exception ignored) {
        }
        draft.setServings(servings <= 0 ? 1f : servings);

        List<ImageFoodItemDraft> filtered = new ArrayList<>();
        for (ImageFoodItemDraft item : draft.getItems()) {
            if (item == null || item.getName() == null || item.getName().trim().isEmpty()) {
                continue;
            }
            filtered.add(item);
        }
        if (filtered.isEmpty()) {
            filtered.add(new ImageFoodItemDraft("请手动补充食物", 0, 0, 0, "份", "其他"));
        }
        draft.setItems(filtered);
        draft.recomputeTotals();
    }

    private void renderItems() {
        layoutItems.removeAllViews();
        for (int i = 0; i < draft.getItems().size(); i++) {
            int index = i;
            ImageFoodItemDraft item = draft.getItems().get(i);
            View itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_food_draft_edit, layoutItems, false);
            EditText etName = itemView.findViewById(R.id.et_item_name);
            EditText etCalories = itemView.findViewById(R.id.et_item_calories);
            EditText etProtein = itemView.findViewById(R.id.et_item_protein);
            EditText etCarbs = itemView.findViewById(R.id.et_item_carbs);
            ImageButton btnDelete = itemView.findViewById(R.id.btn_delete_item);

            etName.setText(item.getName());
            etCalories.setText(String.valueOf(Math.max(0, item.getCalories())));
            etProtein.setText(formatDouble(item.getProtein()));
            etCarbs.setText(formatDouble(item.getCarbs()));

            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    ImageFoodItemDraft current = draft.getItems().get(index);
                    current.setName(safeText(etName));
                    current.setCalories(parseInt(safeText(etCalories)));
                    current.setProtein(parseDouble(safeText(etProtein)));
                    current.setCarbs(parseDouble(safeText(etCarbs)));
                    updateSummary();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };

            etName.addTextChangedListener(watcher);
            etCalories.addTextChangedListener(watcher);
            etProtein.addTextChangedListener(watcher);
            etCarbs.addTextChangedListener(watcher);

            btnDelete.setOnClickListener(v -> {
                if (draft.getItems().size() <= 1) {
                    draft.getItems().set(0, new ImageFoodItemDraft("", 0, 0, 0, "份", "其他"));
                } else {
                    draft.getItems().remove(index);
                }
                renderItems();
                updateSummary();
            });

            layoutItems.addView(itemView);
        }
    }

    private void updateSummary() {
        draft.recomputeTotals();
        String summary = "总热量 " + draft.getTotalCalories()
                + " 千卡 | 蛋白 " + formatDouble(draft.getTotalProtein())
                + "g | 碳水 " + formatDouble(draft.getTotalCarbs()) + "g";
        tvSummary.setText(summary);
    }

    private String safeText(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private int parseInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double parseDouble(String value) {
        try {
            return Math.max(0d, Double.parseDouble(value));
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private String formatDouble(double value) {
        return String.format(Locale.getDefault(), "%.1f", Math.max(0d, value));
    }
}
