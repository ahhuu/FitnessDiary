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
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.model.SmartSuggestionItem;
import com.cz.fitnessdiary.repository.TrainingPlanRepository;
import com.cz.fitnessdiary.service.AICallback;
import com.cz.fitnessdiary.service.DeepSeekService;
import com.cz.fitnessdiary.ui.adapter.SmartSuggestionAdapter;
import com.cz.fitnessdiary.viewmodel.CheckInViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

public class AiPlanSmartFragment extends Fragment {

    private static final String PREF_NAME = "ai_smart_history";
    private static final String KEY_HISTORY = "plan_history";

    private CheckInViewModel checkInViewModel;
    private TrainingPlanRepository trainingPlanRepository;

    private final List<TrainingPlan> plans = new ArrayList<>();
    private final List<DailyLog> logs = new ArrayList<>();
    private final List<SmartSuggestionItem> history = new ArrayList<>();
    private User user;

    private TextView tvSummary;
    private TextView tvDetail;
    private MaterialButton btnGenerate;
    private MaterialButton btnActionPrimary;
    private MaterialButton btnActionSecondary;
    private MaterialButton btnActionTertiary;
    private RecyclerView rvHistory;
    private SmartSuggestionAdapter suggestionAdapter;

    private PlanActionPayload latestActionPayload;

    public AiPlanSmartFragment() {
        super(R.layout.fragment_ai_plan_smart);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkInViewModel = new ViewModelProvider(this).get(CheckInViewModel.class);
        Application application = requireActivity().getApplication();
        trainingPlanRepository = new TrainingPlanRepository(application);

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
        btnActionPrimary.setOnClickListener(v -> handleAddPlans());
        btnActionSecondary.setOnClickListener(v -> handleReplaceTodayPlans());
        btnActionTertiary.setOnClickListener(v -> handleSaveOnly());

        checkInViewModel.getUser().observe(getViewLifecycleOwner(), u -> {
            user = u;
            render();
        });
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
    }

    private void render() {
        int total = plans.size();
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

        int progress = total == 0 ? 0 : (completed * 100 / total);
        tvSummary.setText("今日计划完成度：" + completed + " / " + total + "（" + progress + "%）");

        String nickname = user != null && user.getNickname() != null ? user.getNickname() : "用户";
        String goal = user != null && user.getGoal() != null ? user.getGoal() : "保持健康";
        tvDetail.setText("用户：" + nickname + "\n目标：" + goal + "\n生成后可直接一键入计划/替换今日计划，形成闭环。\n历史会记录执行状态与结果。");
    }

    private String buildPrompt() {
        int total = plans.size();
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

        String nickname = user != null && user.getNickname() != null ? user.getNickname() : "用户";
        String goal = user != null && user.getGoal() != null ? user.getGoal() : "保持健康";
        return "请基于我的真实数据制定今日训练建议：\n"
                + "昵称：" + nickname + "\n"
                + "目标：" + goal + "\n"
                + "今日计划总数：" + total + "\n"
                + "今日已完成：" + completed + "\n"
                + "请给出：1) 今天建议练什么 2) 每个动作建议组数与次数 3) 如需调整给精简版新计划。";
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
                latestActionPayload = parsePlanActionPayload(response, safe);
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
                + "训练建议优先给动作+组次，不要空话。正文不超过8行。"
                + "回答结尾追加 <action>{\"type\":\"PLAN\",\"add\":[{\"name\":\"动作名\",\"description\":\"说明\",\"sets\":3,\"reps\":12,\"duration\":600}],\"replace\":[同add]}</action>。"
                + "如果信息不足，add 至少给 1 个动作。";
    }

    private void handleAddPlans() {
        if (history.isEmpty()) {
            Toast.makeText(getContext(), "请先生成建议", Toast.LENGTH_SHORT).show();
            return;
        }
        PlanActionPayload payload = getPayloadForLatest();
        if (payload.addPlans.isEmpty()) {
            Toast.makeText(getContext(), "暂无可加入计划", Toast.LENGTH_SHORT).show();
            return;
        }
        insertPlans(payload.addPlans, false);
        String label = "已执行：一键加入计划(" + payload.addPlans.size() + "项)";
        markLatestExecuted(label, "已将建议加入训练计划");
        Toast.makeText(getContext(), "已加入计划", Toast.LENGTH_SHORT).show();
    }

    private void handleReplaceTodayPlans() {
        if (history.isEmpty()) {
            Toast.makeText(getContext(), "请先生成建议", Toast.LENGTH_SHORT).show();
            return;
        }
        PlanActionPayload payload = getPayloadForLatest();
        List<PlanDraft> replaceList = payload.replacePlans.isEmpty() ? payload.addPlans : payload.replacePlans;
        if (replaceList.isEmpty()) {
            Toast.makeText(getContext(), "暂无可替换计划", Toast.LENGTH_SHORT).show();
            return;
        }
        replaceTodayPlans(replaceList);
        String label = "已执行：替换今日计划(" + replaceList.size() + "项)";
        markLatestExecuted(label, "已替换今日训练计划");
        Toast.makeText(getContext(), "已替换今日计划", Toast.LENGTH_SHORT).show();
    }

    private void handleSaveOnly() {
        if (history.isEmpty()) {
            Toast.makeText(getContext(), "请先生成建议", Toast.LENGTH_SHORT).show();
            return;
        }
        markLatestExecuted("已执行：仅保存建议", "本次建议仅保存，不落地数据");
        Toast.makeText(getContext(), "已保存建议", Toast.LENGTH_SHORT).show();
    }

    private void insertPlans(List<PlanDraft> drafts, boolean forTomorrow) {
        if (drafts == null || drafts.isEmpty()) {
            return;
        }
        String mode = getCurrentPlanMode();
        long baseDate = getSelectedDateOrNow();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(baseDate);
        if (forTomorrow) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        int dayIndex = toDayIndex(calendar);
        long createTime = System.currentTimeMillis();
        for (PlanDraft draft : drafts) {
            String name = TextUtils.isEmpty(draft.name) ? "AI私教建议训练" : draft.name;
            String description = TextUtils.isEmpty(draft.description) ? "由 AI私教生成" : draft.description;
            TrainingPlan plan = new TrainingPlan(name, description, createTime);
            plan.setSets(Math.max(0, draft.sets));
            plan.setReps(Math.max(0, draft.reps));
            plan.setDuration(Math.max(0, draft.duration));
            plan.setCategory(mode + "-AI私教");
            plan.setScheduledDays(String.valueOf(dayIndex));
            trainingPlanRepository.insert(plan);
        }
    }

    private void replaceTodayPlans(List<PlanDraft> drafts) {
        for (TrainingPlan plan : plans) {
            trainingPlanRepository.delete(plan);
        }
        insertPlans(drafts, false);
    }

    private long getSelectedDateOrNow() {
        Long selectedDate = checkInViewModel.getSelectedDate().getValue();
        return selectedDate == null ? System.currentTimeMillis() : selectedDate;
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

    private PlanActionPayload parsePlanActionPayload(String rawResponse, String cleanResponse) {
        PlanActionPayload payload = new PlanActionPayload();
        String actionJson = extractActionJson(rawResponse);
        if (!TextUtils.isEmpty(actionJson)) {
            try {
                JSONObject obj = new JSONObject(actionJson);
                String type = obj.optString("type", "");
                if ("PLAN".equalsIgnoreCase(type)) {
                    parsePlanArray(obj.optJSONArray("add"), payload.addPlans);
                    parsePlanArray(obj.optJSONArray("replace"), payload.replacePlans);
                    if (payload.addPlans.isEmpty()) {
                        parsePlanArray(obj.optJSONArray("plans"), payload.addPlans);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (payload.addPlans.isEmpty()) {
            PlanDraft fallback = new PlanDraft();
            fallback.name = pickFirstLineTitle(cleanResponse, "AI私教建议训练");
            fallback.description = "根据建议自动生成";
            fallback.sets = 3;
            fallback.reps = 12;
            fallback.duration = 600;
            payload.addPlans.add(fallback);
        }
        if (payload.replacePlans.isEmpty()) {
            payload.replacePlans.addAll(payload.addPlans);
        }
        return payload;
    }

    private void parsePlanArray(JSONArray arr, List<PlanDraft> out) {
        if (arr == null || out == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) {
                continue;
            }
            PlanDraft draft = new PlanDraft();
            draft.name = item.optString("name", "AI私教建议训练");
            draft.description = item.optString("description", "根据 AI 建议生成");
            draft.sets = item.optInt("sets", 3);
            draft.reps = item.optInt("reps", 12);
            draft.duration = item.optInt("duration", 600);
            out.add(draft);
        }
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

    private PlanActionPayload getPayloadForLatest() {
        if (latestActionPayload != null) {
            return latestActionPayload;
        }
        SmartSuggestionItem latest = getLatestHistoryItem();
        String response = latest != null ? latest.getResponse() : "";
        latestActionPayload = parsePlanActionPayload(response, response);
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

    private static class PlanActionPayload {
        List<PlanDraft> addPlans = new ArrayList<>();
        List<PlanDraft> replacePlans = new ArrayList<>();
    }

    private static class PlanDraft {
        String name;
        String description;
        int sets;
        int reps;
        int duration;
    }
}






