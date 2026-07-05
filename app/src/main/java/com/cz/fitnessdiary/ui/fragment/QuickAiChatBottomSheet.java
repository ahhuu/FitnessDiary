package com.cz.fitnessdiary.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.FragmentQuickAiChatBottomSheetBinding;
import com.cz.fitnessdiary.model.ChatMessage;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.repository.TrainingPlanRepository;
import com.cz.fitnessdiary.ui.adapter.AIChatAdapter;
import com.cz.fitnessdiary.utils.ErrorHandler;
import com.cz.fitnessdiary.utils.FoodCategoryUtils;
import com.cz.fitnessdiary.viewmodel.AIChatViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D 拟物快捷对话半屏小抽屉
 */
public class QuickAiChatBottomSheet extends BottomSheetDialogFragment {

    private FragmentQuickAiChatBottomSheetBinding binding;
    private AIChatViewModel viewModel;
    private AIChatAdapter adapter;

    private FoodLibraryRepository foodRepository;
    private FoodRecordRepository foodRecordRepository;
    private TrainingPlanRepository trainingRepository;

    private long selectedRecordDate = System.currentTimeMillis();
    private boolean isFirstLoad = true;

    public static QuickAiChatBottomSheet newInstance() {
        return new QuickAiChatBottomSheet();
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            // 启用 SOFT_INPUT_ADJUST_RESIZE，确保软键盘弹出时能够调整布局大小以顶起输入栏
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQuickAiChatBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 绑定 Activity 级别的 ViewModel，使得两个聊天界面完全共享相同的会话和消息流
        viewModel = new ViewModelProvider(requireActivity()).get(AIChatViewModel.class);

        foodRepository = new FoodLibraryRepository(requireContext());
        foodRecordRepository = new FoodRecordRepository(requireActivity().getApplication());
        trainingRepository = new TrainingPlanRepository(requireActivity().getApplication());

        setupRecyclerView();
        setupListeners();
        observeViewModel();
    }

    @Override
    public void onStart() {
        super.onStart();
        // 动态强制设置 BottomSheet 的实际总高度为屏幕的 65%，防折叠态被 translationY 截断
        View view = getView();
        if (view != null) {
            View parent = (View) view.getParent();
            if (parent != null) {
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int targetHeight = (int) (screenHeight * 0.65); // 限制容器最大高度为屏幕的 65%
                
                ViewGroup.LayoutParams layoutParams = parent.getLayoutParams();
                layoutParams.height = targetHeight;
                parent.setLayoutParams(layoutParams);

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
                behavior.setPeekHeight(targetHeight);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED); // 默认直接完全展开为 65% 状态
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new AIChatAdapter();
        adapter.setOnActionClickListener(this::handleSmartAction);
        adapter.setOnMessageLongClickListener(this::handleMessageLongClick);

        binding.recyclerViewMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewMessages.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> dismiss());

        binding.btnSend.setOnClickListener(v -> {
            String text = binding.etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                viewModel.sendMessage(text);
                binding.etInput.setText("");
            }
        });
    }

    private void observeViewModel() {
        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            if (messages == null) return;

            LinearLayoutManager layoutManager = (LinearLayoutManager) binding.recyclerViewMessages.getLayoutManager();
            boolean isAtBottom = false;
            if (layoutManager != null && adapter.getItemCount() > 0) {
                isAtBottom = layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 2;
            }

            adapter.setMessages(messages);

            // 滚动处理
            if (isFirstLoad) {
                binding.recyclerViewMessages.scrollToPosition(messages.size() - 1);
                isFirstLoad = false;
            } else if (isAtBottom || (messages.size() > 0 && messages.get(messages.size() - 1).isUser())) {
                binding.recyclerViewMessages.smoothScrollToPosition(messages.size() - 1);
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.btnSend.setEnabled(!loading);
            binding.etInput.setEnabled(!loading);
        });
    }

    private void handleMessageLongClick(ChatMessage message) {
        if (message == null || message.getId() <= 0) return;

        String[] options = {"复制", "删除"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(message.isUser() ? "用户消息" : "AI 消息")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        copyToClipboard(message.isUser() ? "用户消息" : "AI 消息",
                                message.isUser() ? message.getContent() : sanitizeAiContent(message.getContent()));
                    } else if (which == 1) {
                        viewModel.deleteMessage(message);
                    }
                })
                .show();
    }

    private String sanitizeAiContent(String content) {
        if (content == null) return "";
        return content.replaceAll("<action>(?s:.*?)</action>", "").trim();
    }

    private void copyToClipboard(String label, String text) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(getContext(), "没有可复制的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
            Toast.makeText(getContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSmartAction(JSONObject actionJson) {
        String type = actionJson.optString("type");
        if ("MULTI".equals(type)) {
            org.json.JSONArray actions = actionJson.optJSONArray("actions");
            if (actions == null || actions.length() == 0) {
                ErrorHandler.showInfo(this, "未识别到可执行操作");
                return;
            }
            JSONObject foodAction = null;
            JSONObject planAction = null;
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.optJSONObject(i);
                if (action == null) continue;
                String actionType = action.optString("type");
                if ("FOOD".equals(actionType)) {
                    foodAction = action;
                } else if ("PLAN".equals(actionType)) {
                    planAction = action;
                }
            }
            JSONObject finalFoodAction = foodAction;
            JSONObject finalPlanAction = planAction;
            String[] options = {"全部执行", "仅记录饮食", "仅添加计划"};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("检测到多个智能操作")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0 || which == 1) {
                            if (finalFoodAction != null) {
                                org.json.JSONArray items = finalFoodAction.optJSONArray("items");
                                if (items != null && items.length() > 0) {
                                    handleMultiFoodLogging(items, finalFoodAction.optString("meal_name", ""));
                                }
                            }
                        }
                        if (which == 0 || which == 2) {
                            if (finalPlanAction != null) {
                                handlePlanAction(finalPlanAction);
                            }
                        }
                    })
                    .show();
        } else if ("FOOD".equals(type)) {
            org.json.JSONArray items = actionJson.optJSONArray("items");
            if (items == null || items.length() == 0) return;
            handleMultiFoodLogging(items, actionJson.optString("meal_name", ""));
        } else if ("PLAN".equals(type)) {
            handlePlanAction(actionJson);
        }
    }

    private void handlePlanAction(JSONObject actionJson) {
        String name = actionJson.optString("name");
        int sets = Math.max(1, actionJson.optInt("sets", 3));
        int reps = Math.max(1, actionJson.optInt("reps", 10));
        String desc = actionJson.optString("desc");
        String category = actionJson.optString("category", "自定义-其他");
        if (!category.startsWith("自定义-")) {
            category = "自定义-" + category;
        }

        final String finalCategory = category;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加训练计划")
                .setMessage(String.format("是否将“%s”添加至您的计划？\n🔢 组数：%d\n🔁 次数：%d\n📂 分类：%s\n📝 描述：%s",
                        name, sets, reps, category, desc))
                .setPositiveButton("确定", (dialog, which) -> {
                    TrainingPlan plan = new TrainingPlan(name, desc, System.currentTimeMillis());
                    plan.setSets(sets);
                    plan.setReps(reps);
                    plan.setCategory(finalCategory);
                    trainingRepository.insert(plan);
                    Toast.makeText(getContext(), "已添加计划 " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void handleMultiFoodLogging(org.json.JSONArray items, String preferredMealName) {
        int count = items.length();
        String[] foodNames = new String[count];
        boolean[] checkedItems = new boolean[count];
        for (int i = 0; i < count; i++) {
            foodNames[i] = items.optJSONObject(i).optString("name");
            checkedItems[i] = true;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("智能识别：" + count + " 项食材")
                .setMultiChoiceItems(foodNames, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setNeutralButton("分别入库", (dialog, which) -> {
                    int success = 0;
                    int skipped = 0;
                    for (int i = 0; i < count; i++) {
                        if (checkedItems[i]) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) {
                                skipped++;
                                continue;
                            }
                            String name = item.optString("name", "").trim();
                            int calories = Math.max(0, item.optInt("calories", 0));
                            if (name.isEmpty() || calories <= 0) {
                                skipped++;
                                continue;
                            }
                            FoodLibrary food = new FoodLibrary(
                                    name, calories,
                                    Math.max(0d, item.optDouble("protein", 0d)),
                                    Math.max(0d, item.optDouble("carbs", 0d)),
                                    item.optString("unit", "克"), 100,
                                    FoodCategoryUtils.normalizeCategory(item.optString("category", "其他")));
                            foodRepository.insert(food);
                            success++;
                        }
                    }
                    Toast.makeText(getContext(),
                            "入库完成：成功 " + success + " 条，跳过 " + skipped + " 条",
                            Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("一键整餐记录", (dialog, which) -> {
                    selectedRecordDate = System.currentTimeMillis();
                    showMealTypeAndDatePickerDialog(items, checkedItems, preferredMealName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMealTypeAndDatePickerDialog(org.json.JSONArray items, boolean[] checkedItems, String preferredMealName) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_meal_date_picker, null);
        com.google.android.material.button.MaterialButton btnDate = dialogView.findViewById(R.id.btn_select_date);
        RadioGroup groupType = dialogView.findViewById(R.id.group_meal_type);

        btnDate.setText("日期: " + com.cz.fitnessdiary.utils.DateUtils.formatDate(selectedRecordDate));

        btnDate.setOnClickListener(v -> {
            com.google.android.material.datepicker.MaterialDatePicker<Long> datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder
                    .datePicker()
                    .setTitleText("选择记录日期")
                    .setSelection(com.cz.fitnessdiary.utils.DateUtils.localToUtcDayStart(selectedRecordDate))
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                selectedRecordDate = selection;
                btnDate.setText("日期: " + com.cz.fitnessdiary.utils.DateUtils.formatDate(selectedRecordDate));
            });
            datePicker.show(getChildFragmentManager(), "DATE_PICKER");
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认记录详情")
                .setView(dialogView)
                .setPositiveButton("确定记录", (dialog, which) -> {
                    int id = groupType.getCheckedRadioButtonId();
                    int mealType = 1;
                    if (id == R.id.radio_breakfast) mealType = 0;
                    else if (id == R.id.radio_lunch) mealType = 1;
                    else if (id == R.id.radio_dinner) mealType = 2;
                    else if (id == R.id.radio_snack) mealType = 3;

                    saveAggregatedFoodRecord(items, checkedItems, preferredMealName, mealType, selectedRecordDate);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveAggregatedFoodRecord(org.json.JSONArray items, boolean[] checkedItems, String preferredMealName,
                                          int mealType, long timestamp) {
        String[] types = {"早餐", "午餐", "晚餐", "加餐"};
        int totalCalories = 0;
        double totalProtein = 0d;
        double totalCarbs = 0d;
        double totalFat = 0d;
        int validCount = 0;

        for (int i = 0; i < items.length(); i++) {
            if (checkedItems != null && i < checkedItems.length && !checkedItems[i]) {
                continue;
            }
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            String name = item.optString("name", "").trim();
            int calories = Math.max(0, item.optInt("calories", 0));
            if (name.isEmpty() || calories <= 0) continue;

            totalCalories += calories;
            totalProtein += Math.max(0d, item.optDouble("protein", 0d));
            totalCarbs += Math.max(0d, item.optDouble("carbs", 0d));
            totalFat += Math.max(0d, item.optDouble("fat", 0d));
            validCount++;
        }

        if (validCount <= 0 || totalCalories <= 0) {
            Toast.makeText(getContext(), "未识别到可记录的有效食材", Toast.LENGTH_SHORT).show();
            return;
        }

        String mealName = buildAggregatedMealName(items, checkedItems, preferredMealName);

        FoodRecord record = new FoodRecord(mealName, totalCalories, timestamp);
        record.setProtein(totalProtein);
        record.setCarbs(totalCarbs);
        record.setFat(totalFat);
        record.setMealType(mealType);
        record.setServings(1.0f);
        record.setServingUnit("份");
        foodRecordRepository.insert(record);

        Toast.makeText(getContext(),
                "整餐记录完成：" + mealName + "（" + types[mealType] + "） 日期："
                        + com.cz.fitnessdiary.utils.DateUtils.formatDate(timestamp),
                Toast.LENGTH_SHORT).show();
    }

    private String buildAggregatedMealName(org.json.JSONArray items, boolean[] checkedItems, String preferredMealName) {
        if (preferredMealName != null && !preferredMealName.trim().isEmpty()) {
            return preferredMealName.trim();
        }

        List<String> names = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            if (checkedItems != null && i < checkedItems.length && !checkedItems[i]) {
                continue;
            }
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            String name = item.optString("name", "").trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }

        if (names.isEmpty()) return "混合餐";
        if (names.size() == 1) return names.get(0);
        return names.get(0) + "等";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
