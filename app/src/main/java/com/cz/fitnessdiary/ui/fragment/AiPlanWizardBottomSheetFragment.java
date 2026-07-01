package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.FragmentAiPlanWizardBottomSheetBinding;
import com.cz.fitnessdiary.repository.ExerciseLibraryRepository;
import com.cz.fitnessdiary.service.DeepSeekService;
import com.cz.fitnessdiary.service.AICallback;
import com.cz.fitnessdiary.viewmodel.PlanViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AiPlanWizardBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentAiPlanWizardBottomSheetBinding binding;
    private PlanViewModel viewModel;
    private WizardExerciseAdapter adapter;
    private List<WizardExercise> matchedExercises = new ArrayList<>();

    // 收集的决策数据
    private int currentStep = 1;
    private String goal = "增肌";
    private int daysPerWeek = 3;
    private int difficulty = 1; // 1=基础, 2=进阶, 3=高级
    private final List<String> focusedParts = new ArrayList<>();
    private String scene = "gym"; // gym, home, bodyweight

    public static class WizardExercise implements Serializable {
        public String name;
        public String category; // 部位
        public String description = "";
        public int sets = 3;
        public int reps = 12;
        public int duration = 0;
        public boolean checked = true;
        public boolean isAiRecommended = false;
    }

    public static AiPlanWizardBottomSheetFragment newInstance() {
        return new AiPlanWizardBottomSheetFragment();
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAiPlanWizardBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(PlanViewModel.class);

        adapter = new WizardExerciseAdapter();
        binding.rvMatchedExercises.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMatchedExercises.setAdapter(adapter);

        setupStep1();
        setupStep2();
        setupStep3();

        // 按钮事件
        binding.btnWizardNext.setOnClickListener(v -> handleNext());
        binding.btnWizardBack.setOnClickListener(v -> handleBack());
    }

    private void setupStep1() {
        // 目标默认选中
        binding.cgWizardGoal.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_goal_bulking)) {
                goal = "增肌";
            } else if (checkedIds.contains(R.id.chip_goal_cutting)) {
                goal = "减脂";
            } else if (checkedIds.contains(R.id.chip_goal_shaping)) {
                goal = "塑形";
            }
        });

        // 训练天数默认选中
        binding.cgWizardDays.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_days_2)) daysPerWeek = 2;
            else if (checkedIds.contains(R.id.chip_days_3)) daysPerWeek = 3;
            else if (checkedIds.contains(R.id.chip_days_4)) daysPerWeek = 4;
            else if (checkedIds.contains(R.id.chip_days_5)) daysPerWeek = 5;
        });

        // 难度默认选中 (新添加)
        binding.cgWizardDifficulty.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_diff_base)) {
                difficulty = 1;
            } else if (checkedIds.contains(R.id.chip_diff_advanced)) {
                difficulty = 2;
            } else if (checkedIds.contains(R.id.chip_diff_senior)) {
                difficulty = 3;
            }
        });
    }

    private void setupStep2() {
        View.OnClickListener listener = v -> {
            binding.cardSceneGym.setStrokeWidth(0);
            binding.cardSceneGym.setStrokeColor(android.graphics.Color.TRANSPARENT);
            binding.cardSceneHome.setStrokeWidth(0);
            binding.cardSceneHome.setStrokeColor(android.graphics.Color.TRANSPARENT);
            binding.cardSceneBodyweight.setStrokeWidth(0);
            binding.cardSceneBodyweight.setStrokeColor(android.graphics.Color.TRANSPARENT);

            int blueColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.plan_blue_primary);

            if (v == binding.cardSceneGym) {
                scene = "gym";
                binding.cardSceneGym.setStrokeWidth(4);
                binding.cardSceneGym.setStrokeColor(blueColor);
            } else if (v == binding.cardSceneHome) {
                scene = "home";
                binding.cardSceneHome.setStrokeWidth(4);
                binding.cardSceneHome.setStrokeColor(blueColor);
            } else if (v == binding.cardSceneBodyweight) {
                scene = "bodyweight";
                binding.cardSceneBodyweight.setStrokeWidth(4);
                binding.cardSceneBodyweight.setStrokeColor(blueColor);
            }
        };

        binding.cardSceneGym.setOnClickListener(listener);
        binding.cardSceneHome.setOnClickListener(listener);
        binding.cardSceneBodyweight.setOnClickListener(listener);
    }

    private void setupStep3() {
        binding.btnAiExpand.setOnClickListener(v -> triggerAiExpand());
    }

    private void handleNext() {
        if (currentStep == 1) {
            // 收集重点部位
            focusedParts.clear();
            for (int i = 0; i < binding.cgWizardParts.getChildCount(); i++) {
                Chip chip = (Chip) binding.cgWizardParts.getChildAt(i);
                if (chip.isChecked()) {
                    focusedParts.add(chip.getText().toString());
                }
            }

            if (focusedParts.isEmpty()) {
                Toast.makeText(requireContext(), "请至少选择一个重点训练部位", Toast.LENGTH_SHORT).show();
                return;
            }

            currentStep = 2;
            binding.tvStepIndicator.setText("第 2 / 3 步");
            binding.layoutStep1.setVisibility(View.GONE);
            binding.layoutStep2.setVisibility(View.VISIBLE);
            binding.btnWizardBack.setVisibility(View.VISIBLE);

        } else if (currentStep == 2) {
            currentStep = 3;
            binding.tvStepIndicator.setText("第 3 / 3 步");
            binding.layoutStep2.setVisibility(View.GONE);
            binding.layoutStep3.setVisibility(View.VISIBLE);
            binding.btnWizardNext.setText("生成我的专属计划");

            // 执行本地动作匹配推荐
            loadLocalMatchedExercises();

        } else if (currentStep == 3) {
            generatePlan();
        }
    }

    private void handleBack() {
        if (currentStep == 2) {
            currentStep = 1;
            binding.tvStepIndicator.setText("第 1 / 3 步");
            binding.layoutStep2.setVisibility(View.GONE);
            binding.layoutStep1.setVisibility(View.VISIBLE);
            binding.btnWizardBack.setVisibility(View.GONE);
        } else if (currentStep == 3) {
            currentStep = 2;
            binding.tvStepIndicator.setText("第 2 / 3 步");
            binding.layoutStep3.setVisibility(View.GONE);
            binding.layoutStep2.setVisibility(View.VISIBLE);
            binding.btnWizardNext.setText("下一步");
        }
    }

    private void loadLocalMatchedExercises() {
        matchedExercises.clear();
        adapter.setExercises(matchedExercises);

        new Thread(() -> {
            try {
                ExerciseLibraryRepository repo = new ExerciseLibraryRepository(requireContext());
                List<ExerciseLibrary> all = repo.getAllExercisesSync();
                if (all == null) return;

                List<WizardExercise> list = new ArrayList<>();
                // 根据重点部位和设备场景进行筛选
                for (String part : focusedParts) {
                    int countPerPart = 0;
                    for (ExerciseLibrary ex : all) {
                        // 过滤部位
                        if (ex.getBodyPart() == null || !ex.getBodyPart().contains(part.substring(0, 2))) {
                            continue;
                        }
                        // 过滤设备
                        boolean matchesScene = false;
                        if ("gym".equals(scene)) {
                            matchesScene = true; // 健身房全部可用
                        } else if ("home".equals(scene)) {
                            // 居家可自重、哑铃、弹力带
                            String eq = ex.getEquipment();
                            matchesScene = eq == null || eq.equals("无") || eq.contains("哑铃") || eq.contains("弹力带");
                        } else if ("bodyweight".equals(scene)) {
                            // 自重只能是无器械的徒手动作
                            String eq = ex.getEquipment();
                            matchesScene = eq == null || eq.equals("无");
                        }

                        if (matchesScene) {
                            // 过滤难度 (1=基础, 2=进阶, 3=高级)
                            boolean matchesDiff = false;
                            int exDiff = ex.getDifficulty();
                            if (difficulty == 1) {
                                matchesDiff = exDiff == 1;
                            } else if (difficulty == 2) {
                                matchesDiff = exDiff == 1 || exDiff == 2;
                            } else {
                                matchesDiff = exDiff == 2 || exDiff == 3;
                            }
                            if (!matchesDiff) {
                                continue;
                            }

                            WizardExercise we = new WizardExercise();
                            we.name = ex.getName();
                            we.category = ex.getBodyPart();
                            we.description = ex.getDescription() != null ? ex.getDescription() : "";
                            // 如果是时间类动作如平板支撑
                            if (ex.getName().contains("撑") || ex.getName().contains("跑") || ex.getName().contains("跳")) {
                                we.sets = 3;
                                we.reps = 1;
                                we.duration = 45; // 默认 45 秒
                            } else {
                                we.sets = 3;
                                we.reps = 12;
                                we.duration = 0;
                            }
                            list.add(we);
                            countPerPart++;
                            if (countPerPart >= 2) {
                                break; // 每个部位最多推荐 2 个本地基础动作，防止堆积
                            }
                        }
                    }

                    // 降级兜底：如果在严苛过滤条件下该部位匹配数量为 0，则忽略难度条件再次匹配，确保部位动作不落空
                    if (countPerPart == 0) {
                        for (ExerciseLibrary ex : all) {
                            if (ex.getBodyPart() == null || !ex.getBodyPart().contains(part.substring(0, 2))) {
                                continue;
                            }
                            boolean matchesScene = false;
                            if ("gym".equals(scene)) {
                                matchesScene = true;
                            } else if ("home".equals(scene)) {
                                String eq = ex.getEquipment();
                                matchesScene = eq == null || eq.equals("无") || eq.contains("哑铃") || eq.contains("弹力带");
                            } else if ("bodyweight".equals(scene)) {
                                String eq = ex.getEquipment();
                                matchesScene = eq == null || eq.equals("无");
                            }

                            if (matchesScene) {
                                WizardExercise we = new WizardExercise();
                                we.name = ex.getName();
                                we.category = ex.getBodyPart();
                                we.description = ex.getDescription() != null ? ex.getDescription() : "";
                                if (ex.getName().contains("撑") || ex.getName().contains("跑") || ex.getName().contains("跳")) {
                                    we.sets = 3;
                                    we.reps = 1;
                                    we.duration = 45;
                                } else {
                                    we.sets = 3;
                                    we.reps = 12;
                                    we.duration = 0;
                                }
                                list.add(we);
                                countPerPart++;
                                if (countPerPart >= 2) {
                                    break;
                                }
                            }
                        }
                    }
                }

                requireActivity().runOnUiThread(() -> {
                    matchedExercises = list;
                    adapter.setExercises(matchedExercises);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void triggerAiExpand() {
        binding.layoutAiLoading.setVisibility(View.VISIBLE);
        binding.btnAiExpand.setEnabled(false);

        List<String> existingNames = new ArrayList<>();
        for (WizardExercise ex : matchedExercises) {
            existingNames.add(ex.name);
        }

        String sceneText = "健身房(所有器械)";
        if ("home".equals(scene)) sceneText = "居家(有哑铃)";
        if ("bodyweight".equals(scene)) sceneText = "自重(无器械)";

        String diffText = "基础级 (适合新手，偏重自重与基础动作，高重复次数，安全首位)";
        if (difficulty == 2) {
            diffText = "进阶级 (有一定基础，可以推荐自由重量负重训练动作，中等负荷)";
        } else if (difficulty == 3) {
            diffText = "高级级 (高强度训练者，偏重力量突破与大负重，动作难度较高)";
        }

        String systemInstruction = "你是一个极其专业的健身 AI 私教。请基于用户的训练目标、设备场景、体能阶段和重点部位，为他额外推荐 2 到 3 个动作。必须以严谨的 JSON 数组格式输出，格式为：[{\"name\":\"动作名\",\"sets\":3,\"reps\":12,\"duration\":0,\"category\":\"部位\",\"description\":\"动作要领\"}]，除 JSON 外不要有任何多余的解释，不要包含 markdown 代码块标记，只输出纯 JSON。";
        String userPrompt = String.format("目标: %s, 场景: %s, 体能阶段: %s, 重点部位: %s, 每周训练: %d 天。当前已有动作: %s。请额外推荐动作。",
                goal, sceneText, diffText, String.join("/", focusedParts), daysPerWeek, String.join(",", existingNames));

        DeepSeekService.sendMessage(userPrompt, systemInstruction, false, null, new AICallback() {
            @Override
            public void onSuccess(@NonNull String content, @Nullable String reasoning) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    binding.layoutAiLoading.setVisibility(View.GONE);
                    binding.btnAiExpand.setEnabled(true);
                    try {
                        // 过滤 AI 回复中可能夹带 of ```json ``` 标记
                        String json = content.trim();
                        if (json.startsWith("```")) {
                            int firstLineEnd = json.indexOf('\n');
                            int lastMarkdown = json.lastIndexOf("```");
                            if (firstLineEnd != -1 && lastMarkdown != -1) {
                                json = json.substring(firstLineEnd, lastMarkdown).trim();
                            }
                        }

                        java.lang.reflect.Type type = new TypeToken<List<WizardExercise>>() {}.getType();
                        List<WizardExercise> aiExes = new Gson().fromJson(json, type);
                        if (aiExes != null && !aiExes.isEmpty()) {
                            for (WizardExercise ex : aiExes) {
                                ex.isAiRecommended = true;
                                ex.checked = true;
                                matchedExercises.add(ex);
                            }
                            adapter.notifyDataSetChanged();
                            Toast.makeText(requireContext(), "AI 成功扩充 " + aiExes.size() + " 个定制动作", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "AI 推荐结果为空，已加载本地推荐", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "AI 解析失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onPartialUpdate(@NonNull String content, @Nullable String reasoning) {
                // 不需要处理流式更新，本功能仅在完成时读取全量 JSON 动作数据
            }

            @Override
            public void onError(@NonNull String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    binding.layoutAiLoading.setVisibility(View.GONE);
                    binding.btnAiExpand.setEnabled(true);
                    Toast.makeText(requireContext(), "AI 推荐失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void generatePlan() {
        List<WizardExercise> selected = new ArrayList<>();
        for (WizardExercise ex : matchedExercises) {
            if (ex.checked) {
                selected.add(ex);
            }
        }

        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), "请至少勾选一个动作生成计划", Toast.LENGTH_SHORT).show();
            return;
        }

        // 弹出 Dialog 询问计划名称
        android.widget.EditText input = new android.widget.EditText(requireContext());
        String defaultName = "AI" + goal + "计划-" + (new java.text.SimpleDateFormat("MMdd", java.util.Locale.getDefault()).format(new java.util.Date()));
        input.setText(defaultName);
        input.setSelection(defaultName.length());
        input.setSingleLine(true);

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("保存制定好的计划")
                .setView(container)
                .setPositiveButton("生成计划", (dialog, which) -> {
                    String customName = input.getText().toString().trim();
                    if (customName.isEmpty()) {
                        Toast.makeText(requireContext(), "计划名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveGeneratedPlan(selected, customName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveGeneratedPlan(List<WizardExercise> selected, String customPlanName) {
        // 智能排班排程
        String[] scheduledDaysPool;
        if (daysPerWeek == 2) {
            scheduledDaysPool = new String[]{"2", "4"};
        } else if (daysPerWeek == 3) {
            scheduledDaysPool = new String[]{"1", "3", "5"};
        } else if (daysPerWeek == 4) {
            scheduledDaysPool = new String[]{"1", "3", "5", "6"};
        } else {
            scheduledDaysPool = new String[]{"1", "2", "3", "4", "5"};
        }

        long now = System.currentTimeMillis();
        List<TrainingPlan> plans = new ArrayList<>();
        String prefix = "自定义-" + customPlanName + "-";

        for (int i = 0; i < selected.size(); i++) {
            WizardExercise we = selected.get(i);
            int dayIndex = i % scheduledDaysPool.length;
            String day = scheduledDaysPool[dayIndex];

            String cat = we.category != null ? we.category : "全身";
            if (cat.contains("部")) {
                cat = cat.substring(0, cat.indexOf("部")); // 去掉“部”字，保持“胸”、“背”、“腿”简炼分类
            }

            TrainingPlan plan = new TrainingPlan(
                    we.name,
                    we.description != null ? we.description : "",
                    now,
                    we.sets,
                    we.reps,
                    null
            );
            plan.setCategory(prefix + cat);
            plan.setDuration(we.duration);
            plan.setScheduledDays(day);
            plans.add(plan);
        }

        // 写入数据库
        new Thread(() -> {
            try {
                // 如果已存在相同模板名称，则覆盖（先删除前缀相同的旧动作）
                viewModel.deletePersonalPlan(customPlanName);
                try { Thread.sleep(100); } catch (Exception ignored) {} // 稍微等待删除执行完毕

                for (TrainingPlan p : plans) {
                    viewModel.addPlan(p);
                }

                requireActivity().runOnUiThread(() -> {
                    // 设置为当前活跃并切回自定义模式
                    viewModel.setActivePersonalPlanName(customPlanName);
                    viewModel.setFilterMode("自定义");
                    Toast.makeText(requireContext(), "AI 计划“" + customPlanName + "”制定成功，已加入个人计划", Toast.LENGTH_SHORT).show();
                    // 发送结果给 PlanManageFragment 自动切 Tab
                    getParentFragmentManager().setFragmentResult("plan_imported_request", new Bundle());
                    dismiss();
                });
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

    // === 适配器适配实现 ===
    private static class WizardExerciseAdapter extends RecyclerView.Adapter<WizardExerciseAdapter.ViewHolder> {

        private List<WizardExercise> list = new ArrayList<>();

        public void setExercises(List<WizardExercise> list) {
            this.list = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_wizard_exercise, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(list.get(position));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbSelect;
            TextView tvName, tvCategory, tvUnit, tvAiBadge;
            EditText etSets, etReps;

            ViewHolder(View itemView) {
                super(itemView);
                cbSelect = itemView.findViewById(R.id.cb_select);
                tvName = itemView.findViewById(R.id.tv_exercise_name);
                tvCategory = itemView.findViewById(R.id.tv_category);
                tvUnit = itemView.findViewById(R.id.tv_unit);
                tvAiBadge = itemView.findViewById(R.id.tv_ai_badge);
                etSets = itemView.findViewById(R.id.et_sets);
                etReps = itemView.findViewById(R.id.et_reps);
            }

            void bind(WizardExercise ex) {
                tvName.setText(ex.name);
                tvCategory.setText(ex.category != null ? ex.category : "全身");
                tvAiBadge.setVisibility(ex.isAiRecommended ? View.VISIBLE : View.GONE);

                // 数据解绑与重绑监听
                cbSelect.setOnCheckedChangeListener(null);
                cbSelect.setChecked(ex.checked);
                cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> ex.checked = isChecked);

                etSets.setText(String.valueOf(ex.sets));
                etReps.setText(String.valueOf(ex.reps > 0 ? ex.reps : ex.duration));
                tvUnit.setText(ex.duration > 0 ? "秒" : "次");

                // 输入框改变时动态回写数据
                etSets.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        try {
                            ex.sets = Integer.parseInt(s.toString().trim());
                        } catch (NumberFormatException e) {
                            ex.sets = 3;
                        }
                    }
                });

                etReps.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        try {
                            int val = Integer.parseInt(s.toString().trim());
                            if (ex.duration > 0) {
                                ex.duration = val;
                            } else {
                                ex.reps = val;
                            }
                        } catch (NumberFormatException e) {
                            if (ex.duration > 0) ex.duration = 45;
                            else ex.reps = 12;
                        }
                    }
                });
            }
        }
    }
}
