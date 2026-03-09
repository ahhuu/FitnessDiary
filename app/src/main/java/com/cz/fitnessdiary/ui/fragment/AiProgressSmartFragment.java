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
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.model.SmartSuggestionItem;
import com.cz.fitnessdiary.repository.ReminderScheduleRepository;
import com.cz.fitnessdiary.repository.TrainingPlanRepository;
import com.cz.fitnessdiary.service.AICallback;
import com.cz.fitnessdiary.service.DeepSeekService;
import com.cz.fitnessdiary.ui.adapter.SmartSuggestionAdapter;
import com.cz.fitnessdiary.utils.ReminderManager;
import com.cz.fitnessdiary.viewmodel.CheckInViewModel;
import com.cz.fitnessdiary.viewmodel.DietViewModel;
import com.cz.fitnessdiary.viewmodel.HomeDashboardViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

public class AiProgressSmartFragment extends Fragment {

    private static final String PREF_NAME = "ai_smart_history";
    private static final String KEY_HISTORY = "progress_history";

    private CheckInViewModel checkInViewModel;
    private DietViewModel dietViewModel;
    private HomeDashboardViewModel homeDashboardViewModel;
    private TrainingPlanRepository trainingPlanRepository;
    private ReminderScheduleRepository reminderScheduleRepository;

    private final List<TrainingPlan> plans = new ArrayList<>();
    private final List<DailyLog> logs = new ArrayList<>();
    private final List<SmartSuggestionItem> history = new ArrayList<>();
    private Integer calories;
    private Integer water;

    private TextView tvSummary;
    private TextView tvDetail;
    private MaterialButton btnGenerate;
    private MaterialButton btnActionPrimary;
    private MaterialButton btnActionSecondary;
    private MaterialButton btnActionTertiary;
    private RecyclerView rvHistory;
    private SmartSuggestionAdapter suggestionAdapter;

    private ProgressActionPayload latestActionPayload;

    public AiProgressSmartFragment() {
        super(R.layout.fragment_ai_progress_smart);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkInViewModel = new ViewModelProvider(this).get(CheckInViewModel.class);
        dietViewModel = new ViewModelProvider(this).get(DietViewModel.class);
        homeDashboardViewModel = new ViewModelProvider(this).get(HomeDashboardViewModel.class);
        Application application = requireActivity().getApplication();
        trainingPlanRepository = new TrainingPlanRepository(application);
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
        btnActionPrimary.setOnClickListener(v -> handleCreateTomorrowPlan());
        btnActionSecondary.setOnClickListener(v -> handleAddTodayTask());
        btnActionTertiary.setOnClickListener(v -> handleSetReviewReminder());

        checkInViewModel.getSelectedDatePlans().observe(getViewLifecycleOwner(), list -> {
            plans.clear();
            if (list != null) {
                plans.addAll(list);
            }
            render();
        });
        checkInViewModel.getSelectedDateLogs().observe(getViewLifecycleOwner(), list -> {
            logs.clear();
            if (list != null) {
                logs.addAll(list);
            }
            render();
        });
        dietViewModel.getTodayTotalCalories().observe(getViewLifecycleOwner(), value -> {
            calories = value;
            render();
        });
        homeDashboardViewModel.getTodayWaterTotal().observe(getViewLifecycleOwner(), value -> {
            water = value;
            render();
        });
    }

    private void render() {
        HashSet<Integer> doneIds = new HashSet<>();
        for (DailyLog log : logs) {
            if (log.isCompleted()) {
                doneIds.add(log.getPlanId());
            }
        }
        int completed = 0;
        for (TrainingPlan plan : plans) {
            if (doneIds.contains(plan.getPlanId())) {
                completed++;
            }
        }
        int total = plans.size();
        int sportProgress = total == 0 ? 0 : (completed * 100 / total);
        int consumed = calories == null ? 0 : calories;
        int waterMl = water == null ? 0 : water;

        tvSummary.setText("今日进度：训练 " + completed + "/" + total + "（" + sportProgress + "%）");
        tvDetail.setText("饮食摄入：" + consumed + " 千卡\n饮水：" + waterMl
                + " ml\n生成后可直接创建明日微计划/加入今日任务/设置复盘提醒。\n历史会记录执行状态。");
    }

    private String buildPrompt() {
        HashSet<Integer> doneIds = new HashSet<>();
        for (DailyLog log : logs) {
            if (log.isCompleted()) {
                doneIds.add(log.getPlanId());
            }
        }
        int completed = 0;
        for (TrainingPlan plan : plans) {
            if (doneIds.contains(plan.getPlanId())) {
                completed++;
            }
        }
        int total = plans.size();
        int consumed = calories == null ? 0 : calories;
        int waterMl = water == null ? 0 : water;

        return "请基于我的真实打卡数据评估今日进度：\n"
                + "训练完成：" + completed + "/" + total + "\n"
                + "饮食热量：" + consumed + " 千卡\n"
                + "饮水：" + waterMl + " ml\n"
                + "请给出：1) 当前状态评估 2) 今天余下时间最优先做的3件事 3) 明天如何衔接。";
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
                latestActionPayload = parseProgressActionPayload(response, safe);
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
                + "评估建议要明确优先级，给可直接执行的下一步。"
                + "回答结尾追加 <action>{\"type\":\"TASK\",\"tomorrow_plan\":\"名称\",\"today_task\":\"名称\",\"review_hour\":21,\"review_minute\":30}</action>。";
    }

    private void handleCreateTomorrowPlan() {
        if (history.isEmpty()) {
            Toast.makeText(getContext(), "请先生成建议", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressActionPayload payload = getPayloadForLatest();
        insertTaskPlan(payload.tomorrowPlan, payload.tomorrowDescription, true);
        markLatestExecuted("已执行：生成明日微计划", "已创建明日微计划：" + payload.tomorrowPlan);
        Toast.makeText(getContext(), "已生成明日微计划", Toast.LENGTH_SHORT).show();
    }

    private void handleAddTodayTask() {
        if (history.isEmpty()) {
            Toast.makeText(getContext(), "请先生成建议", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressActionPayload payload = getPayloadForLatest();
        insertTaskPlan(payload.todayTask, payload.todayDescription, false);
        markLatestExecuted("已执行：加入今日任务", "已加入今日任务：" + payload.todayTask);
        Toast.makeText(getContext(), "已加入今日任务", Toast.LENGTH_SHORT).show();
    }

    private void handleSetReviewReminder() {
        ProgressActionPayload payload = getPayloadForLatest();
        long reminderId = System.currentTimeMillis();
        ReminderSchedule schedule = new ReminderSchedule(
                "CUSTOM_TRACKER",
                reminderId,
                payload.reviewHour,
                payload.reviewMinute,
                "1,2,3,4,5,6,7",
                true,
                "每日复盘提醒",
                "花 3 分钟复盘今天，准备明天的训练与饮食");
        schedule.setId(reminderId);
        reminderScheduleRepository.insert(schedule);
        ReminderManager.schedule(requireContext(), schedule);

        if (!history.isEmpty()) {
            markLatestExecuted("已执行：设置复盘提醒",
                    "已设置每日 " + String.format(java.util.Locale.getDefault(), "%02d:%02d", payload.reviewHour,
                            payload.reviewMinute) + " 复盘提醒");
        }
        Toast.makeText(getContext(), "已设置复盘提醒", Toast.LENGTH_SHORT).show();
    }

    private void insertTaskPlan(String name, String description, boolean tomorrow) {
        String mode = getCurrentPlanMode();
        Calendar calendar = Calendar.getInstance();
        if (tomorrow) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        int dayIndex = toDayIndex(calendar);

        String finalName = TextUtils.isEmpty(name) ? (tomorrow ? "明日微计划" : "今日任务") : name;
        String finalDescription = TextUtils.isEmpty(description) ? "由 AI私教进度评估生成" : description;

        TrainingPlan plan = new TrainingPlan(finalName, finalDescription, System.currentTimeMillis());
        plan.setCategory(mode + "-AI私教");
        plan.setScheduledDays(String.valueOf(dayIndex));
        plan.setSets(1);
        plan.setReps(1);
        plan.setDuration(600);
        trainingPlanRepository.insert(plan);
    }

    private int toDayIndex(Calendar calendar) {
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.SUNDAY ? 7 : dayOfWeek - 1;
    }

    private String getCurrentPlanMode() {
        SharedPreferences sp = requireContext().getSharedPreferences("fitness_diary_prefs", Context.MODE_PRIVATE);
        return sp.getString("current_plan_mode", "基础");
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

    private ProgressActionPayload parseProgressActionPayload(String rawResponse, String cleanResponse) {
        ProgressActionPayload payload = new ProgressActionPayload();
        String actionJson = extractActionJson(rawResponse);
        if (!TextUtils.isEmpty(actionJson)) {
            try {
                JSONObject obj = new JSONObject(actionJson);
                String type = obj.optString("type", "");
                if ("TASK".equalsIgnoreCase(type) || "REMINDER".equalsIgnoreCase(type)
                        || "PROGRESS".equalsIgnoreCase(type)) {
                    payload.tomorrowPlan = obj.optString("tomorrow_plan", payload.tomorrowPlan);
                    payload.todayTask = obj.optString("today_task", payload.todayTask);
                    payload.reviewHour = obj.optInt("review_hour", payload.reviewHour);
                    payload.reviewMinute = obj.optInt("review_minute", payload.reviewMinute);
                    payload.tomorrowDescription = obj.optString("tomorrow_desc", payload.tomorrowDescription);
                    payload.todayDescription = obj.optString("today_desc", payload.todayDescription);
                }
            } catch (Exception ignored) {
            }
        }

        if (TextUtils.isEmpty(payload.tomorrowPlan)) {
            payload.tomorrowPlan = "明日微计划-" + pickFirstLineTitle(cleanResponse, "循序推进");
        }
        if (TextUtils.isEmpty(payload.todayTask)) {
            payload.todayTask = "今日任务-" + pickFirstLineTitle(cleanResponse, "关键1步");
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
                if (t.length() > 12) {
                    return t.substring(0, 12);
                }
                return t;
            }
        }
        return fallback;
    }

    private ProgressActionPayload getPayloadForLatest() {
        if (latestActionPayload != null) {
            return latestActionPayload;
        }
        SmartSuggestionItem latest = getLatestHistoryItem();
        String response = latest != null ? latest.getResponse() : "";
        latestActionPayload = parseProgressActionPayload(response, response);
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

    private static class ProgressActionPayload {
        String tomorrowPlan = "明日微计划";
        String todayTask = "今日关键任务";
        String tomorrowDescription = "由 AI私教进度评估生成";
        String todayDescription = "由 AI私教进度评估生成";
        int reviewHour = 21;
        int reviewMinute = 30;
    }
}






