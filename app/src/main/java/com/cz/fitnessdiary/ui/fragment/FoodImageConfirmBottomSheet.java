package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.model.ImageFoodItemDraft;
import com.cz.fitnessdiary.model.ImageMealDraft;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.ui.adapter.FoodAutoCompleteAdapter;
import com.cz.fitnessdiary.utils.FoodUnitUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Confirmation sheet for an image, library, or text meal draft. */
public class FoodImageConfirmBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "FoodImageConfirmBottomSheet";
    public static final String REQUEST_KEY = "food_image_confirm_request";
    public static final String RESULT_ACTION = "result_action";
    public static final String RESULT_DRAFT = "result_draft";
    public static final String RESULT_SYNC_MODE = "result_sync_mode";
    public static final String RESULT_UNMATCHED_TEXT = "result_unmatched_text";
    public static final String RESULT_AI_MODE = "result_ai_mode";

    public static final String ACTION_CONFIRM = "confirm";
    public static final String ACTION_RETRY = "retry";
    public static final String ACTION_CANCEL = "cancel";
    public static final String ACTION_SUPPLEMENT_AI = "supplement_ai";
    public static final String ACTION_AI_RECOGNIZE = "ai_recognize";
    public static final String AI_MODE_RECOGNIZE_FULL = "recognize_full";
    public static final String AI_MODE_SUPPLEMENT_UNMATCHED = "supplement_unmatched";

    private ImageMealDraft draft;

    private TextInputEditText etMealName;
    private RadioGroup rgMealType;
    private LinearLayout layoutItems;
    private TextView tvSummary;
    private TextView tvSummaryProtein;
    private TextView tvSummaryCarbs;
    private TextView tvSummaryFat;
    private RadioGroup rgSyncOptions;
    private TextView tvSuggestion;
    private ExecutorService foodLibraryExecutor;

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
        foodLibraryExecutor = Executors.newSingleThreadExecutor();
        Object raw = getArguments() == null ? null : getArguments().getSerializable(RESULT_DRAFT);
        if (raw instanceof ImageMealDraft) {
            draft = (ImageMealDraft) raw;
        }
        if (draft == null) {
            draft = new ImageMealDraft();
        }

        etMealName = view.findViewById(R.id.et_meal_name);
        rgMealType = view.findViewById(R.id.rg_meal_type);
        layoutItems = view.findViewById(R.id.layout_items);
        tvSummary = view.findViewById(R.id.tv_summary);
        tvSummaryProtein = view.findViewById(R.id.tv_summary_protein);
        tvSummaryCarbs = view.findViewById(R.id.tv_summary_carbs);
        tvSummaryFat = view.findViewById(R.id.tv_summary_fat);
        rgSyncOptions = view.findViewById(R.id.rg_sync_options);
        tvSuggestion = view.findViewById(R.id.tv_suggestion);

        MaterialButton btnAddItem = view.findViewById(R.id.btn_add_item);
        MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm);
        MaterialButton btnRetry = view.findViewById(R.id.btn_retry);
        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel);
        MaterialButton btnAiRecognize = view.findViewById(R.id.btn_ai_recognize);

        btnRetry.setVisibility(draft.isImageSource() ? View.VISIBLE : View.GONE);
        boolean isLocalLibraryDraft = ImageMealDraft.SOURCE_LIBRARY.equals(draft.getSourceType());
        boolean hasUnmatchedText = draft.getUnmatchedText() != null
                && !draft.getUnmatchedText().trim().isEmpty();
        btnAiRecognize.setVisibility(isLocalLibraryDraft ? View.VISIBLE : View.GONE);
        btnAiRecognize.setText(hasUnmatchedText ? "用 AI 补充" : "用 AI 识别");

        etMealName.setText(draft.getMealName());
        tvSuggestion.setText(draft.getSuggestion() == null ? "" : draft.getSuggestion());
        bindMealType(draft.getMealType());

        if (draft.getItems() == null) {
            draft.setItems(new ArrayList<>());
        }
        normalizeDraftItemsToGrams();
        renderItems();

        btnAddItem.setOnClickListener(v -> {
            showAddFoodDialog();
        });

        btnConfirm.setOnClickListener(v -> attemptConfirm(btnConfirm, btnRetry, btnCancel));

        btnRetry.setOnClickListener(v -> {
            if (!draft.isImageSource()) {
                return;
            }
            collectDraftFromUI();
            Bundle result = new Bundle();
            result.putString(RESULT_ACTION, ACTION_RETRY);
            result.putSerializable(RESULT_DRAFT, draft);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismissAllowingStateLoss();
        });

        btnAiRecognize.setOnClickListener(v -> {
            collectDraftFromUI();
            boolean supplement = hasUnmatchedText;
            Bundle result = new Bundle();
            result.putString(RESULT_ACTION, supplement ? ACTION_SUPPLEMENT_AI : ACTION_AI_RECOGNIZE);
            result.putString(RESULT_AI_MODE,
                    supplement ? AI_MODE_SUPPLEMENT_UNMATCHED : AI_MODE_RECOGNIZE_FULL);
            result.putSerializable(RESULT_DRAFT, draft);
            String aiText = supplement ? draft.getUnmatchedText() : draft.getOriginalText();
            if (aiText == null || aiText.trim().isEmpty()) {
                aiText = draft.getMealName();
            }
            result.putString(RESULT_UNMATCHED_TEXT, aiText);
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

    @Override
    public void onDestroyView() {
        if (foodLibraryExecutor != null) {
            foodLibraryExecutor.shutdownNow();
            foodLibraryExecutor = null;
        }
        super.onDestroyView();
    }

    private void showAddFoodDialog() {
        if (foodLibraryExecutor == null) return;
        Toast.makeText(requireContext(), "正在加载食物库…", Toast.LENGTH_SHORT).show();
        foodLibraryExecutor.execute(() -> {
            List<FoodLibrary> foods;
            try {
                foods = new FoodLibraryRepository(requireContext()).getAllFoodsSync();
            } catch (Exception ignored) {
                foods = new ArrayList<>();
            }
            List<FoodLibrary> finalFoods = foods;
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (getView() != null) showFoodPickerDialog(finalFoods);
            });
        });
    }

    private void showFoodPickerDialog(List<FoodLibrary> foods) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, 0, padding, 0);

        TextInputLayout nameLayout = new TextInputLayout(requireContext());
        nameLayout.setHint("输入食物名称");
        nameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AutoCompleteTextView nameInput = new AutoCompleteTextView(requireContext());
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setThreshold(1);
        FoodAutoCompleteAdapter adapter = new FoodAutoCompleteAdapter(requireContext(), foods);
        nameInput.setAdapter(adapter);
        nameLayout.addView(nameInput);
        container.addView(nameLayout);

        TextView hint = new TextView(requireContext());
        hint.setText("优先选择本地食物库；没有匹配时可用 AI 估算或手动填写营养。\n营养数据统一按每 100 克记录。\n");
        hint.setTextColor(android.graphics.Color.GRAY);
        hint.setTextSize(12);
        container.addView(hint);

        final FoodLibrary[] selectedFood = new FoodLibrary[1];
        nameInput.setOnItemClickListener((parent, view, position, id) ->
                selectedFood[0] = adapter.getItem(position));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("新增食物")
                .setView(container)
                .setNegativeButton("取消", null)
                .setNeutralButton("手动填写", null)
                .setPositiveButton("添加", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String name = safeText(nameInput);
                if (name.isEmpty()) {
                    nameLayout.setError("请输入食物名称");
                    return;
                }
                dialog.dismiss();
                showManualNutritionDialog(name);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = safeText(nameInput);
                if (name.isEmpty()) {
                    nameLayout.setError("请输入食物名称");
                    return;
                }
                FoodLibrary food = findFoodByName(foods, name, selectedFood[0]);
                if (food != null) {
                    addLibraryFood(food);
                    dialog.dismiss();
                } else {
                    dialog.dismiss();
                    showUnmatchedFoodOptions(name);
                }
            });
        });
        dialog.show();
    }

    private FoodLibrary findFoodByName(List<FoodLibrary> foods, String name,
            FoodLibrary selectedFood) {
        for (FoodLibrary food : foods) {
            if (food != null && food.getName() != null
                    && food.getName().trim().equalsIgnoreCase(name.trim())) {
                return food;
            }
        }
        if (selectedFood != null && selectedFood.getName() != null
                && selectedFood.getName().trim().equalsIgnoreCase(name.trim())) {
            return selectedFood;
        }
        return null;
    }

    private void showUnmatchedFoodOptions(String name) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("未找到本地食物")
                .setMessage("“" + name + "”没有匹配到本地食物库，请选择营养数据来源。")
                .setNegativeButton("取消", null)
                .setNeutralButton("手动填写", (dialog, which) -> showManualNutritionDialog(name))
                .setPositiveButton("用 AI 估算", (dialog, which) -> requestAiForNewFood(name))
                .show();
    }

    private void showManualNutritionDialog(String name) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, 0, padding, 0);

        TextView hint = new TextView(requireContext());
        hint.setText("请填写每 100 克的营养数据，保存后可同步到食物库。\n");
        hint.setTextColor(android.graphics.Color.GRAY);
        hint.setTextSize(12);
        container.addView(hint);

        TextInputEditText calories = addNumberField(container, "热量（千卡/100g）", "0");
        TextInputEditText protein = addNumberField(container, "蛋白质（g/100g）", "0");
        TextInputEditText carbs = addNumberField(container, "碳水（g/100g）", "0");
        TextInputEditText fat = addNumberField(container, "脂肪（g/100g）", "0");

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("填写营养数据")
                .setMessage("食物：" + name)
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int caloriesValue = (int) Math.round(parseDouble(safeText(calories)));
                    double proteinValue = parseDouble(safeText(protein));
                    double carbsValue = parseDouble(safeText(carbs));
                    double fatValue = parseDouble(safeText(fat));
                    if (caloriesValue <= 0 && proteinValue <= 0 && carbsValue <= 0 && fatValue <= 0) {
                        calories.setError("至少填写一项营养数据");
                        return;
                    }
                    addCustomFood(name, caloriesValue, proteinValue, carbsValue, fatValue);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private TextInputEditText addNumberField(LinearLayout container, String hint, String value) {
        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setHint(hint);
        inputLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(value);
        inputLayout.addView(input);
        container.addView(inputLayout);
        return input;
    }

    private void addLibraryFood(FoodLibrary food) {
        ImageFoodItemDraft item = createFoodItem(food.getName(), food.getCaloriesPer100g(),
                food.getProteinPer100g(), food.getCarbsPer100g(), food.getFatPer100g(),
                food.getCategory(), ImageFoodItemDraft.SOURCE_LIBRARY);
        addFoodItem(item);
    }

    private void addCustomFood(String name, int calories, double protein, double carbs, double fat) {
        ImageFoodItemDraft item = createFoodItem(name, calories, protein, carbs, fat,
                "其他", ImageFoodItemDraft.SOURCE_MANUAL);
        addFoodItem(item);
    }

    private ImageFoodItemDraft createFoodItem(String name, int calories, double protein,
            double carbs, double fat, String category, String sourceType) {
        ImageFoodItemDraft item = new ImageFoodItemDraft(name, calories, protein, carbs, fat,
                100d, "g", category == null ? "其他" : category);
        item.setNutritionBasis(ImageFoodItemDraft.BASIS_PER_100G);
        item.setBaseNutrition(calories, protein, carbs, fat);
        item.setEstimatedWeightGrams(100d);
        item.setSourceType(sourceType);
        item.setNeedsReview(false);
        item.setUnitConversionConfirmed(true);
        item.recalculateNutrition();
        return item;
    }

    private void addFoodItem(ImageFoodItemDraft item) {
        draft.getItems().add(item);
        renderItems();
        updateSummary();
    }

    private void requestAiForNewFood(String name) {
        collectDraftFromUI();
        Bundle result = new Bundle();
        result.putString(RESULT_ACTION, ACTION_SUPPLEMENT_AI);
        result.putString(RESULT_AI_MODE, AI_MODE_SUPPLEMENT_UNMATCHED);
        result.putSerializable(RESULT_DRAFT, draft);
        result.putString(RESULT_UNMATCHED_TEXT, name);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismissAllowingStateLoss();
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
        if (checkedId == R.id.rb_breakfast) return 0;
        if (checkedId == R.id.rb_dinner) return 2;
        if (checkedId == R.id.rb_snack) return 3;
        return 1;
    }

    private void collectDraftFromUI() {
        String mealName = etMealName.getText() == null ? "" : etMealName.getText().toString().trim();
        if (mealName.isEmpty()) mealName = "识别餐食";
        draft.setMealName(mealName);
        draft.setMealType(collectMealType());
        draft.setServings(1f);

        syncItemViewsFromUI();
        List<ImageFoodItemDraft> filtered = new ArrayList<>();
        for (ImageFoodItemDraft item : draft.getItems()) {
            if (item == null || item.getName() == null || item.getName().trim().isEmpty()) continue;
            filtered.add(item);
        }
        draft.setItems(filtered);
        draft.recomputeTotals();
    }

    /** Reads the actual editor rows so the final name cannot lag behind a watcher callback. */
    private void syncItemViewsFromUI() {
        List<ImageFoodItemDraft> items = draft.getItems();
        int rowCount = Math.min(items.size(), layoutItems.getChildCount());
        for (int i = 0; i < rowCount; i++) {
            ImageFoodItemDraft item = items.get(i);
            if (item == null) continue;
            View itemView = layoutItems.getChildAt(i);
            String name = safeText((EditText) itemView.findViewById(R.id.et_item_name));
            double amount = parseDouble(safeText((EditText) itemView.findViewById(R.id.et_item_amount)));
            boolean amountChanged = Math.abs(item.getAmount() - Math.max(1d, amount)) > 0.0001d;
            item.setName(name);
            item.setAmount(Math.max(1d, amount));
            if (amountChanged) item.recalculateNutrition();
        }
    }

    private void renderItems() {
        layoutItems.removeAllViews();
        for (int i = 0; i < draft.getItems().size(); i++) {
            int index = i;
            ImageFoodItemDraft item = draft.getItems().get(i);
            if (item == null) continue;
            View itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_food_draft_edit, layoutItems, false);
            EditText etName = itemView.findViewById(R.id.et_item_name);
            EditText etAmount = itemView.findViewById(R.id.et_item_amount);
            TextView tvAiFlag = itemView.findViewById(R.id.tv_item_ai_flag);
            TextView tvCalories = itemView.findViewById(R.id.tv_item_calories);
            TextView tvProtein = itemView.findViewById(R.id.tv_item_protein);
            TextView tvCarbs = itemView.findViewById(R.id.tv_item_carbs);
            TextView tvFat = itemView.findViewById(R.id.tv_item_fat);
            TextView tvUnit = itemView.findViewById(R.id.tv_item_unit);
            ImageButton btnDelete = itemView.findViewById(R.id.btn_delete_item);

            etName.setText(item.getName());
            etAmount.setText(formatDouble(item.getAmount()));
            if (tvUnit != null) tvUnit.setText(item.getDisplayUnit());
            String sourceLabel = draft.getSuggestion() != null && draft.getSuggestion().contains("条码")
                    ? "条码数据"
                    : (draft.getSuggestion() != null && (draft.getSuggestion().contains("本地饮食库")
                    || draft.getSuggestion().contains("本地食物库"))
                    ? "来自本地食物库" : "AI 估算");
            tvAiFlag.setText(item.isNeedsReview() ? "需要确认 · " + sourceLabel : sourceLabel);
            tvAiFlag.setTextColor(item.isNeedsReview()
                    ? android.graphics.Color.rgb(198, 40, 40)
                    : android.graphics.Color.rgb(117, 117, 117));
            if (ImageFoodItemDraft.SOURCE_LIBRARY.equals(item.getSourceType())) {
                tvAiFlag.setText("来自本地食物库");
                tvAiFlag.setTextColor(android.graphics.Color.rgb(117, 117, 117));
            } else if (ImageFoodItemDraft.SOURCE_MANUAL.equals(item.getSourceType())) {
                tvAiFlag.setText("手动填写");
                tvAiFlag.setTextColor(android.graphics.Color.rgb(117, 117, 117));
            } else if (ImageFoodItemDraft.SOURCE_AI.equals(item.getSourceType())) {
                tvAiFlag.setText(item.isNeedsReview() ? "AI 估算 · 请确认" : "AI 估算");
                tvAiFlag.setTextColor(item.isNeedsReview()
                        ? android.graphics.Color.rgb(198, 40, 40)
                        : android.graphics.Color.rgb(117, 117, 117));
            }
            updateItemNutritionTexts(tvCalories, tvProtein, tvCarbs, tvFat, item);

            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (index >= draft.getItems().size()) return;
                    ImageFoodItemDraft current = draft.getItems().get(index);
                    double previousAmount = current.getAmount();
                    current.setName(safeText(etName));
                    current.setAmount(parseDouble(safeText(etAmount)));
                    if (Math.abs(previousAmount - current.getAmount()) > 0.0001d) {
                        current.recalculateNutrition();
                    }
                    updateItemNutritionTexts(tvCalories, tvProtein, tvCarbs, tvFat, current);
                    updateSummary();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };

            etName.addTextChangedListener(watcher);
            etAmount.addTextChangedListener(watcher);
            btnDelete.setOnClickListener(v -> {
                draft.getItems().remove(index);
                renderItems();
                updateSummary();
            });
            layoutItems.addView(itemView);
        }
    }

    private void updateItemNutritionTexts(TextView tvCalories, TextView tvProtein,
            TextView tvCarbs, TextView tvFat, ImageFoodItemDraft item) {
        tvCalories.setText(Math.max(0, item.getCalories()) + " 千卡");
        tvProtein.setText(formatDouble(item.getProtein()) + "g");
        tvCarbs.setText(formatDouble(item.getCarbs()) + "g");
        tvFat.setText(formatDouble(item.getFat()) + "g");
    }

    private void updateSummary() {
        draft.recomputeTotals();
        tvSummary.setText("总热量 " + draft.getTotalCalories() + " 千卡");
        tvSummaryProtein.setText("蛋白质 " + formatDouble(draft.getTotalProtein()) + "g");
        tvSummaryCarbs.setText("碳水 " + formatDouble(draft.getTotalCarbs()) + "g");
        tvSummaryFat.setText("脂肪 " + formatDouble(draft.getTotalFat()) + "g");
    }

    private boolean hasInvalidItemValues() {
        if (!hasValidFoodItem()) return true;
        for (ImageFoodItemDraft item : draft.getItems()) {
            if (item == null || item.getAmount() <= 0 || item.getRawUnit() == null
                    || item.getRawUnit().trim().isEmpty() || !FoodUnitUtils.isSupported(item.getRawUnit())
                    || !"g".equalsIgnoreCase(item.getRawUnit().trim())
                    || !item.hasNutritionData()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAiEstimateToConfirm() {
        for (ImageFoodItemDraft item : draft.getItems()) {
            if (item != null && item.isNeedsReview()) return true;
        }
        return false;
    }

    private void attemptConfirm(MaterialButton btnConfirm, MaterialButton btnRetry,
            MaterialButton btnCancel) {
        collectDraftFromUI();
        if (!hasValidFoodItem()) {
            showValidationDialog("请至少保留一项有效食物名称");
            return;
        }
        if (hasInvalidItemValues()) {
            showValidationDialog("请先为每项食物匹配食物库、用 AI 估算，或填写营养数据");
            return;
        }
        if (hasAiEstimateToConfirm()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("确认保存这餐")
                    .setMessage("部分营养数据是 AI 估算值，确认食物名称和克数后可以保存。是否继续？")
                    .setNegativeButton("返回修改", null)
                    .setPositiveButton("确认保存", (dialog, which) ->
                            completeConfirm(btnConfirm, btnRetry, btnCancel))
                    .show();
            return;
        }
        completeConfirm(btnConfirm, btnRetry, btnCancel);
    }

    private void completeConfirm(MaterialButton btnConfirm, MaterialButton btnRetry,
            MaterialButton btnCancel) {
        markUnitConversionsConfirmed();
        btnConfirm.setEnabled(false);
        btnRetry.setEnabled(false);
        btnCancel.setEnabled(false);
        Bundle result = new Bundle();
        result.putString(RESULT_ACTION, ACTION_CONFIRM);
        result.putSerializable(RESULT_DRAFT, draft);

        int syncMode = 0;
        int checkedId = rgSyncOptions.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_sync_recipe) {
            syncMode = 1;
        } else if (checkedId == R.id.rb_sync_items) {
            syncMode = 2;
        }
        result.putInt(RESULT_SYNC_MODE, syncMode);

        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismissAllowingStateLoss();
    }

    private void showValidationDialog(String message) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("暂时不能保存")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private boolean hasValidFoodItem() {
        if (draft.getItems() == null || draft.getItems().isEmpty()) return false;
        for (ImageFoodItemDraft item : draft.getItems()) {
            if (item != null && item.getName() != null && !item.getName().trim().isEmpty()
                    && !"请手动补充食物".equals(item.getName().trim())) {
                return true;
            }
        }
        return false;
    }

    private void normalizeDraftItemsToGrams() {
        if (draft == null || draft.getItems() == null) return;
        for (ImageFoodItemDraft item : draft.getItems()) {
            if (item != null) {
                item.normalizeToGramsForEditing();
            }
        }
    }

    private void markUnitConversionsConfirmed() {
        for (ImageFoodItemDraft item : draft.getItems()) {
            if (item != null) {
                item.setNeedsReview(false);
                item.setUnitConversionConfirmed(true);
            }
        }
    }

    private String safeText(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
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
