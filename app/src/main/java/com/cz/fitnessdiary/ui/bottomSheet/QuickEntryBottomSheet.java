package com.cz.fitnessdiary.ui.bottomSheet;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.MenstrualCycle;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.BottomSheetQuickEntryBinding;
import com.cz.fitnessdiary.ui.fragment.QuickAiChatBottomSheet;
import com.cz.fitnessdiary.ui.fragment.MoodPickerBottomSheet;
import com.cz.fitnessdiary.utils.DateUtils;
import com.cz.fitnessdiary.utils.UnitUtils;
import com.cz.fitnessdiary.viewmodel.HomeDashboardViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * 极致重构后的快速录入与工作台 16 宫格 BottomSheet - v4.0
 * 1. 14 个卡片平铺在同一个列表中进行排序与显示/隐藏配置；
 * 2. 所有快捷弹窗重构，界面精致拟物，必填要素完善，提供高阶交互；
 * 3. 记便便增加时间选择，睡眠支持跨天小时自动算。
 */
public class QuickEntryBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetQuickEntryBinding binding;
    private FragmentActivity hostActivity;
    private HomeDashboardViewModel homeDashboardViewModel;

    @Override
    public int getTheme() {
        return com.google.android.material.R.style.Theme_Design_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetQuickEntryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        hostActivity = getActivity();
        if (hostActivity == null) {
            dismiss();
            return;
        }

        // 初始化 ViewModel
        homeDashboardViewModel = new ViewModelProvider(hostActivity).get(HomeDashboardViewModel.class);

        // 动态绘制 16 宫格 Neumorphic 渐变正圆背景
        setupQuickIconGradients();

        // 绑定事件
        setupActions();

        // 分区动效：同区域内按钮左右齐平 (targetY=0)，只保留交错时序差
        // 区块 1: 智能助理 (4列)
        animateViewIn(binding.itemQuickAi,          0, 0);
        animateViewIn(binding.itemQuickChallenge,   0, 40);
        animateViewIn(binding.itemQuickCalendar,    0, 80);
        animateViewIn(binding.itemQuickSettings,    0, 120);

        // 区块 2: 核心记录 (2列并排加大)
        animateViewIn(binding.itemQuickSport,       0, 160);
        animateViewIn(binding.itemQuickDiet,        0, 200);

        // 区块 3: 身体指标 (4列)
        animateViewIn(binding.itemQuickWeight,      0, 240);
        animateViewIn(binding.itemQuickMeasure,     0, 280);
        animateViewIn(binding.itemQuickStep,        0, 320);
        animateViewIn(binding.itemQuickSleep,       0, 360);

        // 区块 4: 生活日常 (分两行，每行 3 列)
        animateViewIn(binding.itemQuickWater,       0, 400);
        animateViewIn(binding.itemQuickHabit,       0, 430);
        animateViewIn(binding.itemQuickMedication,  0, 460);
        animateViewIn(binding.itemQuickBowel,       0, 490);
        animateViewIn(binding.itemQuickMenstrual,   0, 520);
        animateViewIn(binding.itemQuickMood,        0, 550);
    }

    private void animateViewIn(View view, float targetY, int delay) {
        if (view == null) return;
        view.setTranslationY(targetY + 150f);
        view.setAlpha(0f);
        view.setScaleX(0.4f);
        view.setScaleY(0.4f);
        view.animate()
                .alpha(1f)
                .translationY(targetY)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450)
                .setStartDelay(delay)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f))
                .start();
    }

    @Override
    public void onStart() {
        super.onStart();
        // 彻底消除 BottomSheet 圆角外部顶侧左右露出来的系统灰色阴影与白色背景色差
        if (getDialog() != null && getDialog().getWindow() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        }
    }

    private void setupQuickIconGradients() {
        // 第一行：4 个系统全局工具
        setIconGradient(binding.itemQuickAi, "#E6F4FF", "#BAE0FF"); // AI私教 - 淡蓝
        setIconGradient(binding.itemQuickChallenge, "#FFF0EB", "#FFE0D2"); // 21天挑战 - 淡橙
        setIconGradient(binding.itemQuickCalendar, "#E6F9F0", "#C2F0D8"); // 打卡日历 - 淡绿
        setIconGradient(binding.itemQuickSettings, "#F5F0FF", "#E2D6FF"); // 配置首页 - 淡紫

        // 后三行：12 个具体打卡项目
        setIconGradient(binding.itemQuickSport, "#FFF0F0", "#FFD6D6"); // 记运动 - 浅红
        setIconGradient(binding.itemQuickDiet, "#FFFCEB", "#FFF3C2"); // 记饮食 - 浅黄
        setIconGradient(binding.itemQuickWater, "#E6F4FF", "#BAE0FF"); // 记喝水 - 浅蓝
        setIconGradient(binding.itemQuickSleep, "#F0F0FF", "#D6D6FF"); // 记睡眠 - 浅蓝紫
        setIconGradient(binding.itemQuickHabit, "#F4FAF6", "#D2F0DE"); // 记习惯 - 浅绿
        setIconGradient(binding.itemQuickMedication, "#EBFDF8", "#C2FAF1"); // 记用药 - 薄荷绿
        setIconGradient(binding.itemQuickWeight, "#FFF5F0", "#FFE0D2"); // 记体重 - 暖橙
        setIconGradient(binding.itemQuickMeasure, "#FFF0FB", "#FFD6F5"); // 记围度 - 萌粉
        setIconGradient(binding.itemQuickBowel, "#FAF6F0", "#ECE2D2"); // 记便便 - 浅褐
        setIconGradient(binding.itemQuickMenstrual, "#FFF0F3", "#FFD6DF"); // 记经期 - 珊瑚粉
        setIconGradient(binding.itemQuickStep, "#F8FAF0", "#EBF0C2"); // 记步数 - 嫩黄绿
        setIconGradient(binding.itemQuickMood, "#F5F0FF", "#E2D6FF"); // 记心情 - 浅幻彩紫
    }

    private void setIconGradient(View itemView, String startHex, String endHex) {
        if (itemView instanceof ViewGroup) {
            View fl = ((ViewGroup) itemView).getChildAt(0);
            if (fl != null) {
                fl.setBackground(createCircularGradient(startHex, endHex));
            }
        }
    }

    private android.graphics.drawable.GradientDrawable createCircularGradient(String startHex, String endHex) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{android.graphics.Color.parseColor(startHex), android.graphics.Color.parseColor(endHex)}
        );
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        return gd;
    }

    private void setupActions() {
        // 1. AI私教
        binding.itemQuickAi.setOnClickListener(v -> {
            QuickAiChatBottomSheet.newInstance().show(getParentFragmentManager(), "QUICK_AI_CHAT");
            dismiss();
        });

        // 2. 21天挑战
        binding.itemQuickChallenge.setOnClickListener(v -> {
            showChallengeDialog();
            dismiss();
        });

        // 3. 打卡日历
        binding.itemQuickCalendar.setOnClickListener(v -> showHeatmapDialog());

        // 4. 配置工作台 (14个首页卡片统一管理，平级控制)
        binding.itemQuickSettings.setOnClickListener(v -> showWorkspaceSettingsDialog());

        // 5. 记运动 (完善：支持从动作库选择/自由新建，输入时长/组数/次数)
        binding.itemQuickSport.setOnClickListener(v -> showQuickSportDialog());

        // 6. 记饮食 (完善：支持食物名称/热量/份数/餐点类别单选)
        binding.itemQuickDiet.setOnClickListener(v -> showQuickDietDialog());

        // 7. 记喝水 (一键数字)
        binding.itemQuickWater.setOnClickListener(v -> {
            quickNumberInput("快捷记录喝水", "请输入喝水量 (ml)，如250", value -> {
                homeDashboardViewModel.addWater(value.intValue(), "快捷记录");
                Toast.makeText(getContext(), "已记录喝水 " + value.intValue() + "ml", Toast.LENGTH_SHORT).show();
                dismiss();
            });
        });

        // 8. 记睡眠 (完善：支持入睡和醒来时间跨天计算，以及质量单选)
        binding.itemQuickSleep.setOnClickListener(v -> showQuickSleepDialog());

        // 9. 记习惯 (从今天启用的习惯中列表单选打卡 + 备注)
        binding.itemQuickHabit.setOnClickListener(v -> showQuickHabitDialog());

        // 10. 记用药 (完善：用药名称/剂量/备注/时间)
        binding.itemQuickMedication.setOnClickListener(v -> showQuickMedicationDialog());

        // 11. 记体重 (简略：一键数字)
        String weightUnitSym = UnitUtils.getWeightUnitSymbol(requireContext());
        binding.itemQuickWeight.setOnClickListener(v -> {
            quickNumberInput("快捷记录体重", "请输入体重 (" + weightUnitSym + ")，如65.5", value -> {
                homeDashboardViewModel.addWeight(value.floatValue(), "快捷记录");
                Toast.makeText(getContext(), "已记录体重 " + UnitUtils.formatWeight(value.floatValue(), requireContext()) + " " + UnitUtils.getWeightUnitSymbol(requireContext()), Toast.LENGTH_SHORT).show();
                dismiss();
            });
        });

        // 12. 记围度 (完善：选择胸围/腰围/臀围等，并输入cm值)
        binding.itemQuickMeasure.setOnClickListener(v -> showQuickMeasureDialog());

        // 13. 记便便 (完善：布里斯托分类，包含入厕时间选择，颜色与干硬度)
        binding.itemQuickBowel.setOnClickListener(v -> showQuickBowelDialog());

        // 14. 记经期 (完善：选择生理期流量、症状、心情与备注)
        binding.itemQuickMenstrual.setOnClickListener(v -> showQuickMenstrualDialog());

        // 15. 记步数 (简略：输入步数)
        binding.itemQuickStep.setOnClickListener(v -> {
            quickNumberInput("快捷记录步数", "请输入今日手动记录步数，如5000", value -> {
                new Thread(() -> {
                    long todayStart = DateUtils.getTodayStartTimestamp();
                    int steps = value.intValue();
                    AppDatabase db = AppDatabase.getInstance(requireContext());
                    StepRecord existing = db.stepRecordDao().getByDateSync(todayStart);
                    if (existing != null) {
                        existing.setSteps(existing.getSteps() + steps);
                        existing.setSource(1);
                        existing.setCreateTime(System.currentTimeMillis());
                        db.stepRecordDao().insertOrUpdate(existing);
                    } else {
                        db.stepRecordDao().insertOrUpdate(new StepRecord(todayStart, steps, 1, System.currentTimeMillis()));
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "已成功记录今日步数 " + steps + " 步", Toast.LENGTH_SHORT).show();
                            dismiss();
                        });
                    }
                }).start();
            });
        });

        // 16. 记心情 (保持原表情底面板)
        binding.itemQuickMood.setOnClickListener(v -> {
            showMoodPicker();
            dismiss();
        });
    }

    private void showMoodPicker() {
        com.cz.fitnessdiary.database.entity.MoodRecord existing =
                homeDashboardViewModel.getTodayMood().getValue();
        String currentCode = existing != null ? existing.getMoodCode() : null;
        MoodPickerBottomSheet sheet = MoodPickerBottomSheet.newInstance(currentCode);
        sheet.setOnMoodSelectedListener(code -> homeDashboardViewModel.setTodayMood(code));
        sheet.show(getParentFragmentManager(), "MOOD_PICKER");
    }

    // ═══════════════════════ 精致 Dialog 构建辅助方法 ═══════════════════════

    private View createDialogTitleView(String title) {
        TextView tv = new TextView(requireContext());
        tv.setText(title);
        tv.setTextSize(18);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(android.graphics.Color.parseColor("#2B3E50"));
        tv.setPadding(dp(24), dp(18), dp(24), dp(8));
        return tv;
    }

    private View createLabeledInput(String labelText, EditText et) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        container.setLayoutParams(lp);

        TextView tv = new TextView(requireContext());
        tv.setText(labelText);
        tv.setTextSize(11);
        tv.setTextColor(android.graphics.Color.parseColor("#7A8B99"));
        tv.setPadding(dp(4), 0, 0, dp(4));
        container.addView(tv);

        et.setBackgroundResource(R.drawable.bg_btn_edit_sunken);
        et.setPadding(dp(14), dp(11), dp(14), dp(11));
        et.setTextSize(14);
        et.setTextColor(android.graphics.Color.parseColor("#2B3E50"));
        et.setSingleLine(true);
        container.addView(et);

        return container;
    }

    // ═══════════════════════ 精致快捷录入 Dialog 实装 ═══════════════════════

    private void showQuickSportDialog() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(10), dp(24), dp(10));

        final android.widget.AutoCompleteTextView etName = new android.widget.AutoCompleteTextView(requireContext());
        etName.setThreshold(1);
        final android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        etName.setAdapter(adapter);
        
        etName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String kw = s.toString().trim();
                if (!kw.isEmpty()) {
                    new Thread(() -> {
                        com.cz.fitnessdiary.repository.ExerciseLibraryRepository repo =
                                new com.cz.fitnessdiary.repository.ExerciseLibraryRepository(requireContext());
                        List<com.cz.fitnessdiary.database.entity.ExerciseLibrary> match = repo.searchExercises(kw);
                        List<String> names = new ArrayList<>();
                        if (match != null) {
                            for (com.cz.fitnessdiary.database.entity.ExerciseLibrary ex : match) {
                                names.add(ex.getName());
                            }
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                adapter.clear();
                                adapter.addAll(names);
                                adapter.notifyDataSetChanged();
                                if (etName.hasFocus() && !names.isEmpty()) {
                                    etName.showDropDown();
                                }
                            });
                        }
                    }).start();
                }
            }
        });

        container.addView(createLabeledInput("运动项目名称 (联想动作库，必填)", etName));

        EditText etDur = new EditText(requireContext());
        etDur.setInputType(InputType.TYPE_CLASS_NUMBER);
        etDur.setHint("30");
        container.addView(createLabeledInput("运动时长 (分钟)", etDur));

        EditText etSets = new EditText(requireContext());
        etSets.setInputType(InputType.TYPE_CLASS_NUMBER);
        etSets.setHint("4");
        container.addView(createLabeledInput("完成组数 (组)", etSets));

        EditText etReps = new EditText(requireContext());
        etReps.setInputType(InputType.TYPE_CLASS_NUMBER);
        etReps.setHint("12");
        container.addView(createLabeledInput("每组次数 (次)", etReps));

        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitleView("快捷记录运动"))
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) return;

                    int durMinutes = getIntOrDef(etDur, 30);
                    int sets = getIntOrDef(etSets, 4);
                    int reps = getIntOrDef(etReps, 12);

                    new Thread(() -> {
                        AppDatabase db = AppDatabase.getInstance(requireContext());
                        List<TrainingPlan> all = db.trainingPlanDao().getAllPlansList();
                        TrainingPlan found = null;
                        if (all != null) {
                            for (TrainingPlan p : all) {
                                if (name.equalsIgnoreCase(p.getName())) {
                                    found = p;
                                    break;
                                }
                            }
                        }
                        int planId;
                        if (found == null) {
                            TrainingPlan newPlan = new TrainingPlan(name, "工作台快捷新建", System.currentTimeMillis());
                            newPlan.setDuration(durMinutes * 60);
                            newPlan.setCategory("力量");
                            db.trainingPlanDao().insert(newPlan);
                            
                            // 重新加载并拿到最新生成的 planId
                            List<TrainingPlan> updated = db.trainingPlanDao().getAllPlansList();
                            planId = 0;
                            if (updated != null) {
                                for (TrainingPlan p : updated) {
                                    if (name.equalsIgnoreCase(p.getName())) {
                                        planId = p.getPlanId();
                                        break;
                                    }
                                }
                            }
                        } else {
                            planId = found.getPlanId();
                        }

                        // 插入打卡记录 DailyLog
                        if (planId > 0) {
                            DailyLog log = new DailyLog(planId, DateUtils.getTodayStartTimestamp(), true);
                            log.setDuration(durMinutes * 60);
                            db.dailyLogDao().insert(log);
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "已快捷记录运动: " + name, Toast.LENGTH_SHORT).show();
                                dismiss();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQuickDietDialog() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(10), dp(24), dp(10));

        final android.widget.AutoCompleteTextView etName = new android.widget.AutoCompleteTextView(requireContext());
        etName.setThreshold(1);
        final android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        etName.setAdapter(adapter);
        
        etName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String kw = s.toString().trim();
                if (!kw.isEmpty()) {
                    new Thread(() -> {
                        com.cz.fitnessdiary.repository.FoodLibraryRepository repo =
                                new com.cz.fitnessdiary.repository.FoodLibraryRepository(requireContext());
                        List<com.cz.fitnessdiary.database.entity.FoodLibrary> match = repo.searchFoods(kw);
                        List<String> names = new ArrayList<>();
                        if (match != null) {
                            for (com.cz.fitnessdiary.database.entity.FoodLibrary fd : match) {
                                names.add(fd.getName());
                            }
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                adapter.clear();
                                adapter.addAll(names);
                                adapter.notifyDataSetChanged();
                                if (etName.hasFocus() && !names.isEmpty()) {
                                    etName.showDropDown();
                                }
                            });
                        }
                    }).start();
                }
            }
        });

        container.addView(createLabeledInput("食物名称 (联想食物库，必填)", etName));

        final EditText etServings = new EditText(requireContext());
        etServings.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etServings.setText("1.0");
        container.addView(createLabeledInput("摄入份数", etServings));

        TextView tvMealLabel = new TextView(requireContext());
        tvMealLabel.setText("餐点类型");
        tvMealLabel.setTextSize(11);
        tvMealLabel.setTextColor(android.graphics.Color.parseColor("#7A8B99"));
        tvMealLabel.setPadding(dp(4), 0, 0, dp(4));
        container.addView(tvMealLabel);

        final RadioGroup rg = new RadioGroup(requireContext());
        rg.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rb0 = new RadioButton(requireContext()); rb0.setText("早餐"); rb0.setId(View.generateViewId()); rg.addView(rb0);
        final RadioButton rb1 = new RadioButton(requireContext()); rb1.setText("午餐"); rb1.setId(View.generateViewId()); rg.addView(rb1);
        final RadioButton rb2 = new RadioButton(requireContext()); rb2.setText("晚餐"); rb2.setId(View.generateViewId()); rg.addView(rb2);
        final RadioButton rb3 = new RadioButton(requireContext()); rb3.setText("加餐"); rb3.setId(View.generateViewId()); rg.addView(rb3);
        rb3.setChecked(true); // 默认加餐
        container.addView(rg);

        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitleView("快捷记录饮食"))
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    final String name = etName.getText().toString().trim();
                    if (name.isEmpty()) return;

                    try {
                        final float servings = Float.parseFloat(etServings.getText().toString().trim());
                        int mealType = 3;
                        if (rb0.isChecked()) mealType = 0;
                        else if (rb1.isChecked()) mealType = 1;
                        else if (rb2.isChecked()) mealType = 2;

                        final int finalMeal = mealType;
                        new Thread(() -> {
                            AppDatabase db = AppDatabase.getInstance(requireContext());
                            com.cz.fitnessdiary.database.entity.FoodLibrary food = db.foodLibraryDao().getFoodByName(name);
                            
                            int cal;
                            double protein = 0;
                            double carbs = 0;
                            double fat = 0;
                            String unit = "份";

                            if (food != null) {
                                int weightPerUnit = food.getWeightPerUnit() > 0 ? food.getWeightPerUnit() : 100;
                                double totalWeight = servings * weightPerUnit;
                                double ratio = totalWeight / 100.0;
                                cal = (int) (food.getCaloriesPer100g() * ratio);
                                protein = food.getProteinPer100g() * ratio;
                                carbs = food.getCarbsPer100g() * ratio;
                                fat = food.getFatPer100g() * ratio;
                                unit = food.getServingUnit() != null ? food.getServingUnit() : "份";
                            } else {
                                cal = (int) (150 * servings); // 自定义食物估算 1 份 150 千卡
                            }

                            FoodRecord record = new FoodRecord(name, cal, System.currentTimeMillis());
                            record.setMealType(finalMeal);
                            record.setServings(servings);
                            record.setServingUnit(unit);
                            record.setProtein(protein);
                            record.setCarbs(carbs);
                            db.foodRecordDao().insert(record);

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "已记录饮食: " + name, Toast.LENGTH_SHORT).show();
                                    dismiss();
                                });
                            }
                        }).start();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQuickSleepDialog() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(10), dp(24), dp(10));

        EditText etStart = new EditText(requireContext());
        etStart.setText("23:00");
        container.addView(createLabeledInput("昨晚入睡时间 (格式如 23:00)", etStart));

        EditText etEnd = new EditText(requireContext());
        etEnd.setText("07:00");
        container.addView(createLabeledInput("今早醒来时间 (格式如 07:00)", etEnd));

        TextView tvQualityLabel = new TextView(requireContext());
        tvQualityLabel.setText("睡眠质量");
        tvQualityLabel.setTextSize(11);
        tvQualityLabel.setTextColor(android.graphics.Color.parseColor("#7A8B99"));
        tvQualityLabel.setPadding(dp(4), 0, 0, dp(4));
        container.addView(tvQualityLabel);

        RadioGroup rg = new RadioGroup(requireContext());
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rb1 = new RadioButton(requireContext()); rb1.setText("差"); rb1.setId(View.generateViewId()); rg.addView(rb1);
        RadioButton rb2 = new RadioButton(requireContext()); rb2.setText("一般"); rb2.setId(View.generateViewId()); rg.addView(rb2);
        RadioButton rb3 = new RadioButton(requireContext()); rb3.setText("良好"); rb3.setId(View.generateViewId()); rg.addView(rb3);
        RadioButton rb4 = new RadioButton(requireContext()); rb4.setText("极佳"); rb4.setId(View.generateViewId()); rg.addView(rb4);
        rb3.setChecked(true); // 默认良好
        container.addView(rg);

        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitleView("快捷记录睡眠"))
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String startStr = etStart.getText().toString().trim();
                    String endStr = etEnd.getText().toString().trim();
                    if (startStr.isEmpty() || endStr.isEmpty()) return;

                    try {
                        String[] sParts = startStr.split(":");
                        String[] eParts = endStr.split(":");
                        int startH = Integer.parseInt(sParts[0].trim());
                        int startM = Integer.parseInt(sParts[1].trim());
                        int endH = Integer.parseInt(eParts[0].trim());
                        int endM = Integer.parseInt(eParts[1].trim());

                        int quality = 3;
                        if (rb1.isChecked()) quality = 1;
                        else if (rb2.isChecked()) quality = 2;
                        else if (rb4.isChecked()) quality = 5;

                        final int finalQ = quality;
                        new Thread(() -> {
                            Calendar calEnd = Calendar.getInstance();
                            calEnd.set(Calendar.HOUR_OF_DAY, endH);
                            calEnd.set(Calendar.MINUTE, endM);
                            calEnd.set(Calendar.SECOND, 0);

                            Calendar calStart = Calendar.getInstance();
                            if (startH > endH) {
                                calStart.add(Calendar.DAY_OF_YEAR, -1);
                            }
                            calStart.set(Calendar.HOUR_OF_DAY, startH);
                            calStart.set(Calendar.MINUTE, startM);
                            calStart.set(Calendar.SECOND, 0);

                            long startTime = calStart.getTimeInMillis();
                            long endTime = calEnd.getTimeInMillis();

                            AppDatabase.getInstance(requireContext()).sleepRecordDao().insert(
                                    new SleepRecord(startTime, endTime, finalQ, "快捷跨天算睡眠")
                            );

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    float hour = (endTime - startTime) / 3600000.0f;
                                    Toast.makeText(getContext(), String.format("已成功记录睡眠 %.1f 小时", hour), Toast.LENGTH_SHORT).show();
                                    dismiss();
                                });
                            }
                        }).start();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQuickMedicationDialog() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(10), dp(24), dp(10));

        EditText etName = new EditText(requireContext());
        container.addView(createLabeledInput("药品名称 (必填)", etName));

        EditText etDosage = new EditText(requireContext());
        etDosage.setText("1粒");
        container.addView(createLabeledInput("服用剂量", etDosage));

        EditText etTime = new EditText(requireContext());
        Calendar rightNow = Calendar.getInstance();
        etTime.setText(String.format("%02d:%02d", rightNow.get(Calendar.HOUR_OF_DAY), rightNow.get(Calendar.MINUTE)));
        container.addView(createLabeledInput("服用时间", etTime));

        EditText etNote = new EditText(requireContext());
        container.addView(createLabeledInput("服用备注", etNote));

        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitleView("快捷记录用药"))
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) return;

                    String dosage = etDosage.getText().toString().trim();
                    String timeStr = etTime.getText().toString().trim();
                    String note = etNote.getText().toString().trim();

                    try {
                        String[] tParts = timeStr.split(":");
                        int hour = Integer.parseInt(tParts[0].trim());
                        int minute = Integer.parseInt(tParts[1].trim());

                        new Thread(() -> {
                            Calendar cal = Calendar.getInstance();
                            cal.set(Calendar.HOUR_OF_DAY, hour);
                            cal.set(Calendar.MINUTE, minute);
                            cal.set(Calendar.SECOND, 0);

                            homeDashboardViewModel.addMedication(name, dosage, true, note);

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "已记录用药: " + name, Toast.LENGTH_SHORT).show();
                                    dismiss();
                                });
                            }
                        }).start();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQuickMeasureDialog() {
        String[] parts = {"胸围", "腰围", "臀围", "大腿围", "手臂围"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择测量部位")
                .setItems(parts, (dialog, which) -> {
                    String partName = parts[which];
                    showMeasureValueInputDialog(partName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMeasureValueInputDialog(String partName) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(10), dp(24), dp(10));

        EditText etVal = new EditText(requireContext());
        etVal.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        container.addView(createLabeledInput(partName + " 数值 (cm，必填)", etVal));

        EditText etNote = new EditText(requireContext());
        container.addView(createLabeledInput("数据备注", etNote));

        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitleView("记录 " + partName))
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String valStr = etVal.getText().toString().trim();
                    if (valStr.isEmpty()) return;

                    try {
                        float val = Float.parseFloat(valStr);
                        String note = etNote.getText().toString().trim();

                        new Thread(() -> {
                            String dbType = partName;
                            if ("胸围".equals(partName)) dbType = "CHEST";
                            else if ("腰围".equals(partName)) dbType = "WAIST";
                            else if ("臀围".equals(partName)) dbType = "HIP";
                            else if ("大腿围".equals(partName)) dbType = "THIGH";
                            else if ("手臂围".equals(partName) || "臂围".equals(partName)) dbType = "ARM";

                            AppDatabase.getInstance(requireContext()).bodyMeasurementDao().insert(
                                    new BodyMeasurement(dbType, val, "cm", System.currentTimeMillis(), note)
                            );
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "已成功记录" + partName + " " + val + "cm", Toast.LENGTH_SHORT).show();
                                    dismiss();
                                });
                            }
                        }).start();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQuickBowelDialog() {
        String[] types = {"1型: 颗状硬球 (便秘)", "2型: 肠状有凹凸 (偏干)", "3型: 肠状表面裂痕 (正常)",
                "4型: 肠状光滑柔软 (理想)", "5型: 软团边界切断 (偏软)", "6型: 糊状无边界 (腹泻)", "7型: 水状无固体 (严重腹泻)"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择大便性状")
                .setItems(types, (dialog, which) -> {
                    int selectedType = which + 1;
                    showBowelDetailInputDialog(selectedType);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBowelDetailInputDialog(int type) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(10), dp(24), dp(10));

        EditText etTime = new EditText(requireContext());
        Calendar now = Calendar.getInstance();
        etTime.setText(String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
        container.addView(createLabeledInput("入厕时间 (解决时间选择问题，格式如 09:30)", etTime));

        TextView tvColor = new TextView(requireContext());
        tvColor.setText("便便颜色");
        tvColor.setTextSize(11);
        tvColor.setTextColor(android.graphics.Color.parseColor("#7A8B99"));
        tvColor.setPadding(dp(4), 0, 0, dp(4));
        container.addView(tvColor);

        RadioGroup rgColor = new RadioGroup(requireContext());
        rgColor.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rc0 = new RadioButton(requireContext()); rc0.setText("褐色"); rc0.setId(View.generateViewId()); rgColor.addView(rc0);
        RadioButton rc1 = new RadioButton(requireContext()); rc1.setText("黄色"); rc1.setId(View.generateViewId()); rgColor.addView(rc1);
        RadioButton rc2 = new RadioButton(requireContext()); rc2.setText("黑色"); rc2.setId(View.generateViewId()); rgColor.addView(rc2);
        rc0.setChecked(true); // 默认褐色
        container.addView(rgColor);

        TextView tvShape = new TextView(requireContext());
        tvShape.setText("排便干硬度");
        tvShape.setTextSize(11);
        tvShape.setTextColor(android.graphics.Color.parseColor("#7A8B99"));
        tvShape.setPadding(dp(4), 0, 0, dp(4));
        container.addView(tvShape);

        RadioGroup rgShape = new RadioGroup(requireContext());
        rgShape.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rs0 = new RadioButton(requireContext()); rs0.setText("干硬"); rs0.setId(View.generateViewId()); rgShape.addView(rs0);
        RadioButton rs1 = new RadioButton(requireContext()); rs1.setText("适中"); rs1.setId(View.generateViewId()); rgShape.addView(rs1);
        RadioButton rs2 = new RadioButton(requireContext()); rs2.setText("稀软"); rs2.setId(View.generateViewId()); rgShape.addView(rs2);
        rs1.setChecked(true); // 默认适中
        container.addView(rgShape);

        EditText etNote = new EditText(requireContext());
        container.addView(createLabeledInput("其他备注", etNote));

        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitleView("记录便便"))
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String timeStr = etTime.getText().toString().trim();
                    if (timeStr.isEmpty()) return;

                    try {
                        String[] tParts = timeStr.split(":");
                        int hour = Integer.parseInt(tParts[0].trim());
                        int minute = Integer.parseInt(tParts[1].trim());

                        String colorTemp = "BROWN";
                        if (rc1.isChecked()) colorTemp = "YELLOW";
                        else if (rc2.isChecked()) colorTemp = "BLACK";
                        final String finalColor = colorTemp;

                        String shapeTemp = "NORMAL";
                        if (rs0.isChecked()) shapeTemp = "HARD";
                        else if (rs2.isChecked()) shapeTemp = "SOFT";
                        final String finalShape = shapeTemp;

                        final String finalNote = etNote.getText().toString().trim();

                        new Thread(() -> {
                            Calendar cal = Calendar.getInstance();
                            cal.set(Calendar.HOUR_OF_DAY, hour);
                            cal.set(Calendar.MINUTE, minute);
                            cal.set(Calendar.SECOND, 0);

                            AppDatabase.getInstance(requireContext()).bowelMovementDao().insert(
                                    new BowelMovement(type, finalColor, "MEDIUM", "NORMAL", finalShape, 0, cal.getTimeInMillis(), finalNote)
                            );

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "已快捷保存大便记录", Toast.LENGTH_SHORT).show();
                                    dismiss();
                                });
                            }
                        }).start();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQuickMenstrualDialog() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(10), dp(24), dp(10));

        TextView tvFlow = new TextView(requireContext());
        tvFlow.setText("经期流量");
        tvFlow.setTextSize(11);
        tvFlow.setTextColor(android.graphics.Color.parseColor("#7A8B99"));
        tvFlow.setPadding(dp(4), 0, 0, dp(4));
        container.addView(tvFlow);

        RadioGroup rgFlow = new RadioGroup(requireContext());
        rgFlow.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rf0 = new RadioButton(requireContext()); rf0.setText("轻度"); rf0.setId(View.generateViewId()); rgFlow.addView(rf0);
        RadioButton rf1 = new RadioButton(requireContext()); rf1.setText("中度"); rf1.setId(View.generateViewId()); rgFlow.addView(rf1);
        RadioButton rf2 = new RadioButton(requireContext()); rf2.setText("重度"); rf2.setId(View.generateViewId()); rgFlow.addView(rf2);
        rf1.setChecked(true); // 默认中度
        container.addView(rgFlow);

        TextView tvMood = new TextView(requireContext());
        tvMood.setText("生理期情绪");
        tvMood.setTextSize(11);
        tvMood.setTextColor(android.graphics.Color.parseColor("#7A8B99"));
        tvMood.setPadding(dp(4), 0, 0, dp(4));
        container.addView(tvMood);

        RadioGroup rgMood = new RadioGroup(requireContext());
        rgMood.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rm0 = new RadioButton(requireContext()); rm0.setText("平静"); rm0.setId(View.generateViewId()); rgMood.addView(rm0);
        RadioButton rm1 = new RadioButton(requireContext()); rm1.setText("烦躁"); rm1.setId(View.generateViewId()); rgMood.addView(rm1);
        RadioButton rm2 = new RadioButton(requireContext()); rm2.setText("抑郁"); rm2.setId(View.generateViewId()); rgMood.addView(rm2);
        rm0.setChecked(true);
        container.addView(rgMood);

        EditText etNote = new EditText(requireContext());
        container.addView(createLabeledInput("经期身体症状/备注", etNote));

        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitleView("快捷记录经期"))
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String flowTemp = "MEDIUM";
                    if (rf0.isChecked()) flowTemp = "LIGHT";
                    else if (rf2.isChecked()) flowTemp = "HEAVY";
                    final String finalFlow = flowTemp;

                    String moodTemp = "NORMAL";
                    if (rm1.isChecked()) moodTemp = "IRRITABLE";
                    else if (rm2.isChecked()) moodTemp = "DEPRESSED";
                    final String finalMood = moodTemp;

                    final String finalNote = etNote.getText().toString().trim();

                    new Thread(() -> {
                        long today = DateUtils.getTodayStartTimestamp();
                        AppDatabase.getInstance(requireContext()).menstrualCycleDao().insert(
                                new MenstrualCycle(today, null, finalFlow, "NONE", finalMood, finalNote, System.currentTimeMillis())
                        );

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "已记录生理期状态", Toast.LENGTH_SHORT).show();
                                dismiss();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQuickHabitDialog() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            List<HabitItem> habits = db.habitItemDao().getEnabledSync();
            if (habits == null || habits.isEmpty()) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "暂无启用的习惯，请先开启习惯卡片", Toast.LENGTH_SHORT).show();
                    });
                }
                return;
            }
            String[] names = new String[habits.size()];
            for (int i = 0; i < habits.size(); i++) {
                names[i] = habits.get(i).getName();
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("选择打卡习惯")
                            .setItems(names, (dialog, which) -> {
                                showHabitCheckInConfirmDialog(habits.get(which));
                            })
                            .setNegativeButton("取消", null)
                            .show();
                });
            }
        }).start();
    }

    private void showHabitCheckInConfirmDialog(HabitItem habit) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(10), dp(24), dp(10));

        TextView tvHabit = new TextView(requireContext());
        tvHabit.setText("打卡项目：" + habit.getName());
        tvHabit.setTextSize(14);
        tvHabit.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHabit.setTextColor(android.graphics.Color.parseColor("#4A5D6E"));
        tvHabit.setPadding(0, 0, 0, dp(15));
        container.addView(tvHabit);

        EditText etNote = new EditText(requireContext());
        container.addView(createLabeledInput("打卡备注 (选填)", etNote));

        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitleView("习惯确认打卡"))
                .setView(container)
                .setPositiveButton("打卡", (dialog, which) -> {
                    homeDashboardViewModel.upsertHabitRecord(habit.getId(), DateUtils.getTodayStartTimestamp(), true, "FAB");
                    Toast.makeText(getContext(), "习惯 " + habit.getName() + " 打卡成功", Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHeatmapDialog() {
        final Context context = getContext();
        if (context == null) return;
        dismiss();

        new Thread(() -> {
            java.util.Map<Long, Integer> levels = new java.util.HashMap<>();
            long today = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(today);
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            cal.add(Calendar.WEEK_OF_YEAR, -5);
            long start = cal.getTimeInMillis();

            com.cz.fitnessdiary.database.AppDatabase db =
                    com.cz.fitnessdiary.database.AppDatabase.getInstance(context);

            for (int i = 0; i < 42; i++) {
                long day = start + i * 86400000L;
                if (day > today) break;
                int comp = db.dailyLogDao().getTodayCompletedCountSync(day);
                int tot = db.dailyLogDao().getTodayPlanCountSync(day);
                int dietCount = db.foodRecordDao().getRecordCountByDateRangeSync(day, day + 86400000L);
                int wml = db.waterRecordDao().getTodayTotalSync(day, day + 86400000L);
                int lvl = 0;
                if (tot > 0 && comp > 0) lvl = 1;
                if (comp >= tot && tot > 0) lvl = 2;
                if (lvl >= 2 && dietCount > 0) lvl = 3;
                if (lvl >= 3 && wml >= 1000) lvl = 4;
                levels.put(day, lvl);
            }

            if (hostActivity != null) {
                hostActivity.runOnUiThread(() -> {
                    android.view.View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_heatmap, null);
                    com.cz.fitnessdiary.ui.widget.StreakCalendarView calView = dialogView.findViewById(R.id.calendar_view);
                    if (calView != null) calView.setDayLevels(levels);

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                            .setTitle("打卡热力图")
                            .setView(dialogView)
                            .setPositiveButton("关闭", null)
                            .show();
                });
            }
        }).start();
    }

    private void showChallengeDialog() {
        try {
            String status = com.cz.fitnessdiary.utils.ChallengeManager.getStatus(getContext());
            String active = com.cz.fitnessdiary.utils.ChallengeManager.getActiveType(getContext());

            if (active != null && ("COMPLETED".equals(status) || "FAILED".equals(status))) {
                String result = "COMPLETED".equals(status) ? "恭喜你！已圆满完成21天挑战！" : "挑战失败，别气馁，下次继续加油！";
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(com.cz.fitnessdiary.utils.ChallengeManager.getTypeName(active))
                        .setMessage(result + "\n\n是否开启新的挑战？")
                        .setPositiveButton("新挑战", (d, w) -> { showChallengeTypePicker(); })
                        .setNegativeButton("关闭", (d, w) -> {
                            com.cz.fitnessdiary.utils.ChallengeManager.reset(getContext());
                        })
                        .show();
                return;
            }
            showChallengeTypePicker();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showChallengeTypePicker() {
        android.view.View pickerVal = getLayoutInflater().inflate(R.layout.dialog_challenge_picker, null);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(pickerVal)
                .setTitle("选择21天挑战")
                .setNegativeButton("取消", null)
                .create();

        pickerVal.findViewById(R.id.card_challenge_fat_loss).setOnClickListener(v -> {
            com.cz.fitnessdiary.utils.ChallengeManager.start(getContext(), com.cz.fitnessdiary.utils.ChallengeManager.TYPE_FAT_LOSS);
            Toast.makeText(getContext(), "挑战已开启！", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        pickerVal.findViewById(R.id.card_challenge_muscle_gain).setOnClickListener(v -> {
            com.cz.fitnessdiary.utils.ChallengeManager.start(getContext(), com.cz.fitnessdiary.utils.ChallengeManager.TYPE_MUSCLE_GAIN);
            Toast.makeText(getContext(), "挑战已开启！", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        pickerVal.findViewById(R.id.card_challenge_early_sleep).setOnClickListener(v -> {
            com.cz.fitnessdiary.utils.ChallengeManager.start(getContext(), com.cz.fitnessdiary.utils.ChallengeManager.TYPE_EARLY_SLEEP);
            Toast.makeText(getContext(), "挑战已开启！", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        pickerVal.findViewById(R.id.card_challenge_water_master).setOnClickListener(v -> {
            com.cz.fitnessdiary.utils.ChallengeManager.start(getContext(), com.cz.fitnessdiary.utils.ChallengeManager.TYPE_WATER_MASTER);
            Toast.makeText(getContext(), "挑战已开启！", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ═══════════════════════ 配置 14 个首页平级卡片 ═══════════════════════

    private void showWorkspaceSettingsDialog() {
        SharedPreferences sp = requireContext().getSharedPreferences("home_cards_prefs", Context.MODE_PRIVATE);
        String orderStr = sp.getString("home_cards_order", "sport,diet,water,sleep,habit,medication,weight,measurement,bowel,menstrual,step,mood");
        List<String> items = new ArrayList<>(Arrays.asList(orderStr.split(",")));

        // 移除已废弃的"顶部外露指标"配置项
        items.remove("missions");

        // 动态补齐可能缺失的ID
        boolean changed = false;
        for (String id : Arrays.asList("sport","diet","water","sleep","habit","medication","weight","measurement","bowel","menstrual","step","mood")) {
            if (!items.contains(id)) {
                items.add(id);
                changed = true;
            }
        }
        if (changed) {
            sp.edit().putString("home_cards_order", String.join(",", items)).apply();
        }

        // 用 ScrollView 包装以免列表过长在小屏上溢出
        NestedScrollView sv = new NestedScrollView(requireContext());
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(10), dp(20), dp(10));
        sv.addView(container);

        refreshSettingsListInLayout(container, items, sp);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("配置首页卡片 (平级排序)")
                .setView(sv)
                .setPositiveButton("保存", (dialog, which) -> {
                    sp.edit().putString("home_cards_order", String.join(",", items)).apply();
                    Toast.makeText(getContext(), "首页卡片配置已更新", Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshSettingsListInLayout(LinearLayout container, List<String> items, SharedPreferences sp) {
        container.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            String id = items.get(i);
            final int index = i;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            CheckBox cb = new CheckBox(requireContext());
            cb.setText(getWorkspaceCardName(id));
            cb.setChecked(sp.getBoolean("show_card_" + id, getDefaultCardVisibility(id)));
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sp.edit().putBoolean("show_card_" + id, isChecked).apply();
            });

            LinearLayout.LayoutParams lpCb = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            cb.setLayoutParams(lpCb);
            row.addView(cb);

            // Move Up
            ImageButton btnUp = new ImageButton(requireContext());
            btnUp.setImageResource(android.R.drawable.arrow_up_float);
            btnUp.setBackground(null);
            btnUp.setPadding(dp(8), dp(8), dp(8), dp(8));
            btnUp.setEnabled(index > 0);
            btnUp.setOnClickListener(v -> {
                String temp = items.get(index);
                items.set(index, items.get(index - 1));
                items.set(index - 1, temp);
                refreshSettingsListInLayout(container, items, sp);
            });
            row.addView(btnUp);

            // Move Down
            ImageButton btnDown = new ImageButton(requireContext());
            btnDown.setImageResource(android.R.drawable.arrow_down_float);
            btnDown.setBackground(null);
            btnDown.setPadding(dp(8), dp(8), dp(8), dp(8));
            btnDown.setEnabled(index < items.size() - 1);
            btnDown.setOnClickListener(v -> {
                String temp = items.get(index);
                items.set(index, items.get(index + 1));
                items.set(index + 1, temp);
                refreshSettingsListInLayout(container, items, sp);
            });
            row.addView(btnDown);

            container.addView(row);
        }
    }

    private String getWorkspaceCardName(String id) {
        switch (id) {
            // [Removed v2.4] case "briefing": return "健康日报";
            case "sport": return "运动记录卡片";
            case "diet": return "饮食记录卡片";
            case "water": return "记喝水";
            case "sleep": return "记睡眠";
            case "habit": return "记习惯";
            case "medication": return "记用药";
            case "weight": return "记体重";
            case "measurement": return "记围度";
            case "bowel": return "记便便";
            case "menstrual": return "记经期";
            case "step": return "记步数";
            case "mood": return "记心情";
            default: return id;
        }
    }

    private boolean getDefaultCardVisibility(String id) {
        return true; // 所有卡片默认全开启显示
    }

    // ═══════════════════════ Helper Methods ═══════════════════════

    private interface NumberConsumer {
        void accept(Double v);
    }

    private interface TextConsumer {
        void accept(String v);
    }

    private void quickNumberInput(String title, String hint, NumberConsumer c) {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint(hint);
        et.setPadding(dp(16), dp(10), dp(16), dp(10));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(et)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = et.getText().toString().trim();
                    if (!text.isEmpty()) {
                        try {
                            c.accept(Double.parseDouble(text));
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void quickTextInput(String title, String hint, TextConsumer c) {
        EditText et = new EditText(requireContext());
        et.setHint(hint);
        et.setPadding(dp(16), dp(10), dp(16), dp(10));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(et)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = et.getText().toString().trim();
                    if (!text.isEmpty()) {
                        c.accept(text);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int getIntOrDef(EditText et, int def) {
        String s = et.getText().toString().trim();
        if (s.isEmpty()) return def;
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return def;
        }
    }

    private int dp(int x) {
        Context ctx = getContext();
        if (ctx == null && hostActivity != null) ctx = hostActivity;
        if (ctx == null) return (int) (x * 1.5f);
        return Math.round(x * ctx.getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
