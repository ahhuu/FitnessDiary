package com.cz.fitnessdiary.ui.fragment;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.model.SmartSuggestionItem;
import com.cz.fitnessdiary.repository.FoodLibraryRepository;
import com.cz.fitnessdiary.repository.FoodRecordRepository;
import com.cz.fitnessdiary.repository.ReminderScheduleRepository;
import com.cz.fitnessdiary.service.AICallback;
import com.cz.fitnessdiary.service.DeepSeekService;
import com.cz.fitnessdiary.ui.adapter.SmartSuggestionAdapter;
import com.cz.fitnessdiary.utils.ReminderManager;
import com.cz.fitnessdiary.viewmodel.DietViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiDietSmartFragment extends Fragment {

    private static final String PREF_NAME = "ai_smart_history";
    private static final String KEY_HISTORY = "diet_history";

    private DietViewModel dietViewModel;
    private FoodRecordRepository foodRecordRepository;
    private FoodLibraryRepository foodLibraryRepository;
    private ReminderScheduleRepository reminderScheduleRepository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private User user;
    private Integer calories;
    private Double protein;
    private Double carbs;
    private final List<FoodRecord> records = new ArrayList<>();
    private final List<SmartSuggestionItem> history = new ArrayList<>();

    private TextView tvSummary;
    private TextView tvDetail;
    private MaterialButton btnGenerate;
    private MaterialButton btnActionPrimary;
    private MaterialButton btnActionSecondary;
    private MaterialButton btnActionTertiary;
    private RecyclerView rvHistory;
    private SmartSuggestionAdapter suggestionAdapter;

    private FoodActionPayload latestActionPayload;

    public AiDietSmartFragment() {
        super(R.layout.fragment_ai_diet_smart);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dietViewModel = new ViewModelProvider(this).get(DietViewModel.class);
        Application application = requireActivity().getApplication();
        foodRecordRepository = new FoodRecordRepository(application);
        foodLibraryRepository = new FoodLibraryRepository(application);
        reminderScheduleRepository = new ReminderScheduleRepository(application);

        tvSummary = view.findViewById(R.id.tv_data_summary);
        tvDetail = view.findViewById(R.id.tv_data_detail);
        MaterialButton btnBack = view.findViewById(R.id.btn_back);
        btnGenerate = view.findViewById(R.id.btn_generate);
        MaterialButton btnClearHistory = view.findViewById(R.id.btn_clear_history);
        btnActionPrimary = view.findViewById(R.id.btn_action_primary);
        btnActionSecondary = view.findViewById(R.id.btn_action_secondary);
        btnActionTertiary = view.findViewById(R.id.btn_action_tertiary);
        rvHistory = view.findViewById(R.id.rv_history);

        suggestionAdapter = new SmartSuggestionAdapter();
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        rvHistory.setAdapter(suggestionAdapter);

        loadHistory();
        renderHistory();
        updateActionButtonsEnabled();

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        btnClearHistory.setOnClickListener(v -> showClearHistoryDialog());
        btnGenerate.setOnClickListener(v -> generateSuggestion());
        btnActionPrimary.setOnClickListener(v -> handleRecordMeal());
        btnActionSecondary.setOnClickListener(v -> handleSaveMealTemplate());
        btnActionTertiary.setOnClickListener(v -> handleSetDinnerReminder());

        dietViewModel.getCurrentUser().observe(getViewLifecycleOwner(), u -> {
            user = u;
            render();
        });
        dietViewModel.getTodayTotalCalories().observe(getViewLifecycleOwner(), value -> {
            calories = value;
            render();
        });
        dietViewModel.getTodayTotalProtein().observe(getViewLifecycleOwner(), value -> {
            protein = value;
            render();
        });
        dietViewModel.getTodayTotalCarbs().observe(getViewLifecycleOwner(), value -> {
            carbs = value;
            render();
        });
        dietViewModel.getTodayFoodRecords().observe(getViewLifecycleOwner(), list -> {
            records.clear();
            if (list != null) {
                records.addAll(list);
            }
            render();
        });
    }

    private void render() {
        int consumed = calories == null ? 0 : calories;
        int target = user != null && user.getDailyCalorieTarget() > 0 ? user.getDailyCalorieTarget() : 2000;
        int gap = target - consumed;
        tvSummary.setText("今日热量：" + consumed + " / " + target + " 千卡（差值 " + gap + "）");

        double p = protein == null ? 0d : protein;
        double c = carbs == null ? 0d : carbs;
        tvDetail.setText("记录条数：" + records.size() + "\n蛋白质："
                + String.format(java.util.Locale.getDefault(), "%.1f", p)
                + " g\n碳水："
                + String.format(java.util.Locale.getDefault(), "%.1f", c)
                + " g\n生成后可直接记录本餐、存入食物库、设晚餐提醒。\n历史会记录执行结果。");
    }

    private String buildPrompt() {
        int consumed = calories == null ? 0 : calories;
        int target = user != null && user.getDailyCalorieTarget() > 0 ? user.getDailyCalorieTarget() : 2000;
        String goal = user != null && user.getGoal() != null ? user.getGoal() : "保持健康";
        double p = protein == null ? 0d : protein;
        double c = carbs == null ? 0d : carbs;

        return "请基于我的真实饮食数据做今天的饮食分析：\n"
                + "目标：" + goal + "\n"
                + "今日摄入热量：" + consumed + " 千卡\n"
                + "目标热量：" + target + " 千卡\n"
                + "蛋白质：" + String.format(java.util.Locale.getDefault(), "%.1f", p) + " g\n"
                + "碳水：" + String.format(java.util.Locale.getDefault(), "%.1f", c) + " g\n"
                + "记录条数：" + records.size() + "\n"
                + "请给出：1) 结论 2) 下一餐建议 3) 今日剩余热量分配建议。";
    }

    private void generateSuggestion() {
        String prompt = buildPrompt();
        setGenerating(true);
        DeepSeekService.sendMessage(prompt, buildSystemInstruction(), false, null, new AICallback() {
            @Override
            public void onSuccess(String response, String reasoning) {
                setGenerating(false);
                String safe = sanitizeResponse(response);
                if (safe == null || safe.trim().isEmpty()) {
                    safe = sanitizeResponse(reasoning);
                }
                if (safe == null || safe.trim().isEmpty()) {
                    String fallback = response == null ? "" : response.trim();
                    if (fallback.isEmpty()) {
                        fallback = reasoning == null ? "" : reasoning.trim();
                    }
                    safe = fallback.isEmpty() ? "AI 暂未返回可展示内容，请重试" : fallback;
                }
                latestActionPayload = parseFoodActionPayload(response, safe);
                boolean shouldAutoScroll = isUserAtHistoryBottom();
                addHistory(prompt, safe);
                if (shouldAutoScroll) {
                    scrollToHistoryBottomSmooth();
                }
                updateActionButtonsEnabled();
            }

            @Override
            public void onPartialUpdate(String content, String reasoning) {
            }

            @Override
            public void onError(String error) {
                setGenerating(false);
                String msg = "生成失败：" + (error == null ? "未知错误" : error);
                addHistory(prompt, msg);
                updateActionButtonsEnabled();
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String buildSystemInstruction() {
        return "你是 FitnessDiary 应用的 AI私教。输出简洁、可执行、中文回答。"
                + "饮食建议固定结构：结论+热量判断+一条可执行建议。"
                + "回答结尾追加 <action>{\"type\":\"FOOD\",\"meal_name\":\"餐名\",\"calories\":550,\"protein\":28,\"carbs\":65,\"meal_type\":2,\"servings\":1,\"serving_unit\":\"份\"}</action>。";
    }

    private void handleRecordMeal() {
        if (history.isEmpty()) {
            Toast.makeText(getContext(), "请先生成建议", Toast.LENGTH_SHORT).show();
            return;
        }
        FoodActionPayload payload = getPayloadForLatest();
        long recordTime = System.currentTimeMillis();
        FoodRecord record = new FoodRecord(payload.mealName, payload.calories, recordTime);
        record.setProtein(payload.protein);
        record.setCarbs(payload.carbs);
        record.setMealType(payload.mealType);
        record.setServings(payload.servings);
        record.setServingUnit(payload.servingUnit);
        foodRecordRepository.insert(record);

        markLatestExecuted("已执行：一键记录本餐", "已新增饮食记录：" + payload.mealName);
        Toast.makeText(getContext(), "已记录本餐", Toast.LENGTH_SHORT).show();
    }

    private void handleSaveMealTemplate() {
        if (history.isEmpty()) {
            Toast.makeText(getContext(), "请先生成建议", Toast.LENGTH_SHORT).show();
            return;
        }
        FoodActionPayload payload = getPayloadForLatest();
        ioExecutor.execute(() -> {
            FoodLibrary existing = foodLibraryRepository.getFoodByName(payload.mealName);
            if (existing == null) {
                FoodLibrary template = new FoodLibrary(
                        payload.mealName,
                        Math.max(1, payload.calories),
                        Math.max(0d, payload.protein),
                        Math.max(0d, payload.carbs),
                        payload.servingUnit,
                        100,
                        "AI私教推荐");
                foodLibraryRepository.insert(template);
            }
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                markLatestExecuted("已执行：加入常用模板", "已存入食物库模板：" + payload.mealName);
                Toast.makeText(getContext(), "已存入食物库（饮食记录搜索可复用）", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void handleSetDinnerReminder() {
        long reminderId = System.currentTimeMillis();
        ReminderSchedule schedule = new ReminderSchedule(
                "CUSTOM_TRACKER",
                reminderId,
                19,
                0,
                "1,2,3,4,5,6,7",
                true,
                "晚餐记录提醒",
                "记得记录今晚饮食，保持热量可控");
        schedule.setId(reminderId);
        reminderScheduleRepository.insert(schedule);
        ReminderManager.schedule(requireContext(), schedule);

        if (!history.isEmpty()) {
            markLatestExecuted("已执行：设置晚餐提醒", "已设置每日 19:00 晚餐提醒");
        }
        Toast.makeText(getContext(), "已设置晚餐提醒", Toast.LENGTH_SHORT).show();
    }

    private void setGenerating(boolean generating) {
        btnGenerate.setEnabled(!generating);
        btnGenerate.setText(generating ? "生成中..." : "生成建议");
    }

    private String sanitizeResponse(String response) {
        if (response == null) {
            return "";
        }
        return response.replaceAll("<action>(?s:.*?)</action>", "").trim();
    }

    private String extractActionJson(String response) {
        if (response == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<action>(?s:(.*?))</action>")
                .matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private FoodActionPayload parseFoodActionPayload(String rawResponse, String cleanResponse) {
        FoodActionPayload payload = new FoodActionPayload();
        String actionJson = extractActionJson(rawResponse);
        if (!TextUtils.isEmpty(actionJson)) {
            try {
                JSONObject obj = new JSONObject(actionJson);
                if ("FOOD".equalsIgnoreCase(obj.optString("type", ""))) {
                    payload.mealName = obj.optString("meal_name", payload.mealName);
                    payload.calories = obj.optInt("calories", payload.calories);
                    payload.protein = obj.optDouble("protein", payload.protein);
                    payload.carbs = obj.optDouble("carbs", payload.carbs);
                    payload.mealType = obj.optInt("meal_type", payload.mealType);
                    payload.servings = (float) obj.optDouble("servings", payload.servings);
                    payload.servingUnit = obj.optString("serving_unit", payload.servingUnit);
                }
            } catch (Exception ignored) {
            }
        }

        if (TextUtils.isEmpty(payload.mealName)) {
            payload.mealName = pickFirstLineTitle(cleanResponse, "AI私教推荐餐");
        }
        if (payload.calories <= 0) {
            payload.calories = 450;
        }
        if (payload.servings <= 0f) {
            payload.servings = 1f;
        }
        if (TextUtils.isEmpty(payload.servingUnit)) {
            payload.servingUnit = "份";
        }
        return payload;
    }

    private String pickFirstLineTitle(String text, String fallback) {
        if (TextUtils.isEmpty(text)) {
            return fallback;
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (!t.isEmpty()) {
                if (t.length() > 20) {
                    return t.substring(0, 20);
                }
                return t;
            }
        }
        return fallback;
    }

    private FoodActionPayload getPayloadForLatest() {
        if (latestActionPayload != null) {
            return latestActionPayload;
        }
        SmartSuggestionItem latest = getLatestHistoryItem();
        String response = latest != null ? latest.getResponse() : "";
        latestActionPayload = parseFoodActionPayload(response, response);
        return latestActionPayload;
    }

    private void addHistory(String prompt, String response) {
        history.add(new SmartSuggestionItem(prompt, response, System.currentTimeMillis(), false, "未执行"));
        while (history.size() > 30) {
            history.remove(0);
        }
        persistHistory();
        renderHistory();
    }

    private SmartSuggestionItem getLatestHistoryItem() {
        if (history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }

    private void markLatestExecuted(String actionLabel, String resultText) {
        SmartSuggestionItem latest = getLatestHistoryItem();
        if (latest == null) {
            return;
        }
        latest.setExecuted(true);
        latest.setActionLabel(actionLabel);
        if (!TextUtils.isEmpty(resultText)) {
            String response = latest.getResponse() == null ? "" : latest.getResponse();
            latest.setResponse(response + "\n\n执行结果：" + resultText);
        }
        boolean shouldAutoScroll = isUserAtHistoryBottom();
        persistHistory();
        renderHistory();
        if (shouldAutoScroll) {
            scrollToHistoryBottomSmooth();
        }
    }

    private void renderHistory() {
        suggestionAdapter.submitList(new ArrayList<>(history));
    }

    private void updateActionButtonsEnabled() {
        boolean enabled = !history.isEmpty();
        btnActionPrimary.setEnabled(enabled);
        btnActionSecondary.setEnabled(enabled);
        btnActionTertiary.setEnabled(enabled);
    }

    private boolean isUserAtHistoryBottom() {
        RecyclerView.LayoutManager manager = rvHistory.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) {
            return true;
        }
        LinearLayoutManager lm = (LinearLayoutManager) manager;
        int total = suggestionAdapter.getItemCount();
        if (total <= 1) {
            return true;
        }
        int lastVisible = lm.findLastVisibleItemPosition();
        return lastVisible >= total - 2;
    }

    private void scrollToHistoryBottomSmooth() {
        rvHistory.post(() -> {
            int target = suggestionAdapter.getItemCount() - 1;
            if (target >= 0) {
                rvHistory.smoothScrollToPosition(target);
            }
        });
    }

    private void showClearHistoryDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("清空历史")
                .setMessage("确定清空本页历史建议吗？")
                .setPositiveButton("清空", (d, w) -> {
                    history.clear();
                    latestActionPayload = null;
                    persistHistory();
                    renderHistory();
                    updateActionButtonsEnabled();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadHistory() {
        history.clear();
        SharedPreferences sp = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String prompt = obj.optString("prompt", "");
                String response = obj.optString("response", "");
                long ts = obj.optLong("timestamp", System.currentTimeMillis());
                boolean executed = obj.optBoolean("executed", false);
                String actionLabel = obj.optString("actionLabel", executed ? "已执行" : "未执行");
                history.add(new SmartSuggestionItem(prompt, response, ts, executed, actionLabel));
            }
        } catch (Exception ignored) {
        }
    }

    private void persistHistory() {
        JSONArray arr = new JSONArray();
        for (SmartSuggestionItem item : history) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("prompt", item.getPrompt());
                obj.put("response", item.getResponse());
                obj.put("timestamp", item.getTimestamp());
                obj.put("executed", item.isExecuted());
                obj.put("actionLabel", item.getActionLabel());
                arr.put(obj);
            } catch (Exception ignored) {
            }
        }
        SharedPreferences sp = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private static class FoodActionPayload {
        String mealName = "AI私教推荐餐";
        int calories = 450;
        double protein = 20d;
        double carbs = 50d;
        int mealType = 2;
        float servings = 1f;
        String servingUnit = "份";
    }
}







