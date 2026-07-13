package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.ExtraExerciseLog;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.utils.DateUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/** Bottom sheet for entering the actual values of one exercise on one date. */
public class DailyExerciseEditorBottomSheet extends BottomSheetDialogFragment {

    public static final String RESULT_PLAN_SAVED = "daily_plan_actual_saved";
    public static final String RESULT_EXTRA_SAVED = "daily_extra_exercise_saved";

    private static final String ARG_MODE = "mode";
    private static final String ARG_DATE = "date";
    private static final String ARG_PLAN = "plan";
    private static final String ARG_LOG = "log";
    private static final String ARG_EXTRA = "extra";
    private static final String ARG_NAME = "name";
    private static final String ARG_BODY_PART = "body_part";
    private static final String ARG_CATEGORY = "category";
    private static final String ARG_LIBRARY_ID = "library_id";
    private static final String MODE_PLAN = "plan";
    private static final String MODE_EXTRA = "extra";

    private TrainingPlan plan;
    private DailyLog existingPlanLog;
    private ExtraExerciseLog existingExtraLog;
    private String extraName;
    private String extraBodyPart;
    private String extraCategory;
    private long extraLibraryId;
    private long date;

    private EditText etName;
    private EditText etSets;
    private EditText etReps;
    private EditText etWeight;
    private EditText etDuration;
    private TextView tvTarget;
    private MaterialButton btnTimer;
    private MaterialButton btnTimerReset;
    private android.os.CountDownTimer countDownTimer;
    private int timerTotalSeconds;
    private int timerRemainingSeconds;
    private boolean timerRunning;

    public static DailyExerciseEditorBottomSheet newForPlan(TrainingPlan plan, DailyLog log, long date) {
        DailyExerciseEditorBottomSheet sheet = new DailyExerciseEditorBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, MODE_PLAN);
        args.putSerializable(ARG_PLAN, plan);
        args.putLong(ARG_DATE, date);
        if (log != null) {
            args.putInt("log_id", log.getLogId());
            args.putInt("actual_sets", log.getActualSets());
            args.putInt("actual_reps", log.getActualReps());
            args.putFloat("actual_weight", log.getActualWeight());
            args.putInt("actual_duration", log.getDuration());
        }
        sheet.setArguments(args);
        return sheet;
    }

    public static DailyExerciseEditorBottomSheet newForExtraDraft(long date, String name,
            String bodyPart, String category, long libraryId) {
        DailyExerciseEditorBottomSheet sheet = new DailyExerciseEditorBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, MODE_EXTRA);
        args.putLong(ARG_DATE, date);
        args.putString(ARG_NAME, name);
        args.putString(ARG_BODY_PART, bodyPart);
        args.putString(ARG_CATEGORY, category);
        args.putLong(ARG_LIBRARY_ID, libraryId);
        sheet.setArguments(args);
        return sheet;
    }

    public static DailyExerciseEditorBottomSheet newForExtra(ExtraExerciseLog log, long date) {
        DailyExerciseEditorBottomSheet sheet = new DailyExerciseEditorBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, MODE_EXTRA);
        args.putLong(ARG_DATE, date);
        args.putSerializable(ARG_EXTRA, log);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        date = DateUtils.getDayStartTimestamp(args.getLong(ARG_DATE, DateUtils.getTodayStartTimestamp()));
        if (MODE_PLAN.equals(args.getString(ARG_MODE))) {
            plan = (TrainingPlan) args.getSerializable(ARG_PLAN);
            if (args.containsKey("log_id")) {
                existingPlanLog = new DailyLog(plan.getPlanId(), date, false);
                existingPlanLog.setLogId(args.getInt("log_id"));
                existingPlanLog.setActualSets(args.getInt("actual_sets", 0));
                existingPlanLog.setActualReps(args.getInt("actual_reps", 0));
                existingPlanLog.setActualWeight(args.getFloat("actual_weight", 0));
                existingPlanLog.setDuration(args.getInt("actual_duration", 0));
            }
        } else {
            existingExtraLog = (ExtraExerciseLog) args.getSerializable(ARG_EXTRA);
            if (existingExtraLog != null) {
                extraName = existingExtraLog.getName();
                extraBodyPart = existingExtraLog.getBodyPart();
                extraCategory = existingExtraLog.getCategory();
                extraLibraryId = existingExtraLog.getLibraryId();
            } else {
                extraName = args.getString(ARG_NAME, "");
                extraBodyPart = args.getString(ARG_BODY_PART, "其他");
                extraCategory = args.getString(ARG_CATEGORY, "其他");
                extraLibraryId = args.getLong(ARG_LIBRARY_ID, 0);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_daily_exercise_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView tvTitle = view.findViewById(R.id.tv_editor_title);
        etName = view.findViewById(R.id.et_editor_name);
        etSets = view.findViewById(R.id.et_editor_sets);
        etReps = view.findViewById(R.id.et_editor_reps);
        etWeight = view.findViewById(R.id.et_editor_weight);
        etDuration = view.findViewById(R.id.et_editor_duration);
        tvTarget = view.findViewById(R.id.tv_editor_target);
        btnTimer = view.findViewById(R.id.btn_editor_timer);
        btnTimerReset = view.findViewById(R.id.btn_editor_timer_reset);
        btnTimer.setText("\u5f00\u59cb\u8ba1\u65f6");
        btnTimerReset.setText("\u91cd\u7f6e\u8ba1\u65f6");
        MaterialButton btnSave = view.findViewById(R.id.btn_editor_save);
        MaterialButton btnCancel = view.findViewById(R.id.btn_editor_cancel);

        if (plan != null) {
            tvTitle.setText("记录今日实际训练");
            etName.setVisibility(View.GONE);
            view.findViewById(R.id.til_editor_name).setVisibility(View.GONE);
            tvTarget.setText("计划目标：" + formatMetrics(plan.getSets(), plan.getReps(), plan.getWeight(), plan.getDuration()));
            setEditorValue(etSets, getActualOrTarget(existingPlanLog == null ? 0 : existingPlanLog.getActualSets(), plan.getSets()));
            setEditorValue(etReps, getActualOrTarget(existingPlanLog == null ? 0 : existingPlanLog.getActualReps(), plan.getReps()));
            setEditorValue(etWeight, getActualFloatOrTarget(existingPlanLog == null ? 0 : existingPlanLog.getActualWeight(), plan.getWeight()));
            setEditorValue(etDuration, getActualOrTarget(existingPlanLog == null ? 0 : existingPlanLog.getDuration(), plan.getDuration()));
        } else {
            tvTitle.setText("记录今日额外动作");
            etName.setVisibility(View.VISIBLE);
            view.findViewById(R.id.til_editor_name).setVisibility(View.VISIBLE);
            etName.setText(extraName);
            etName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            tvTarget.setText("仅记录今天，不会加入长期计划");
            if (existingExtraLog != null) {
                setEditorValue(etSets, existingExtraLog.getSets());
                setEditorValue(etReps, existingExtraLog.getReps());
                setEditorValue(etWeight, existingExtraLog.getWeight());
                setEditorValue(etDuration, existingExtraLog.getDuration());
            }
        }

        btnTimer.setOnClickListener(v -> toggleTimer());
        btnTimerReset.setOnClickListener(v -> resetTimer());
        btnSave.setOnClickListener(v -> save());
        btnCancel.setOnClickListener(v -> dismiss());
    }

    private int getActualOrTarget(int actual, int target) {
        return actual > 0 ? actual : target;
    }

    private float getActualFloatOrTarget(float actual, float target) {
        return actual > 0 ? actual : target;
    }

    private void setEditorValue(EditText editText, int value) {
        editText.setText(value > 0 ? String.valueOf(value) : "");
    }

    private void setEditorValue(EditText editText, float value) {
        editText.setText(value > 0 ? String.format(Locale.getDefault(), "%.1f", value) : "");
    }

    private void toggleTimer() {
        if (timerRunning) {
            pauseTimer();
        } else if (timerRemainingSeconds > 0) {
            resumeTimer();
        } else {
            startTimer();
        }
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        timerRunning = false;
        btnTimer.setEnabled(true);
        btnTimer.setText("\u7ee7\u7eed\u8ba1\u65f6");
    }

    private void resumeTimer() {
        if (timerRemainingSeconds <= 0) {
            startTimer();
            return;
        }
        final int totalSeconds = timerTotalSeconds;
        timerRunning = true;
        btnTimer.setEnabled(true);
        btnTimerReset.setEnabled(true);
        countDownTimer = new android.os.CountDownTimer(timerRemainingSeconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerRemainingSeconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                btnTimer.setText(String.format(Locale.getDefault(), "\u6682\u505c %02d:%02d",
                        timerRemainingSeconds / 60, timerRemainingSeconds % 60));
            }

            @Override
            public void onFinish() {
                timerRemainingSeconds = 0;
                timerRunning = false;
                countDownTimer = null;
                etDuration.setText(String.valueOf(totalSeconds));
                btnTimer.setText("\u8ba1\u65f6\u5b8c\u6210");
                btnTimer.setEnabled(true);
                btnTimerReset.setEnabled(true);
                Toast.makeText(requireContext(), "\u65f6\u957f\u5df2\u56de\u586b\uff0c\u70b9\u51fb\u4fdd\u5b58\u8bb0\u5f55", Toast.LENGTH_SHORT).show();
            }
        };
        countDownTimer.start();
    }

    private void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        timerTotalSeconds = 0;
        timerRemainingSeconds = 0;
        timerRunning = false;
        btnTimer.setEnabled(true);
        btnTimer.setText("\u5f00\u59cb\u8ba1\u65f6");
        btnTimerReset.setEnabled(false);
    }

    private void startTimer() {
        int duration = parseInt(etDuration, 0);
        if (duration <= 0 && plan != null) {
            duration = plan.getDuration();
        }
        if (duration <= 0) {
            Toast.makeText(requireContext(), "请先填写时长，或在计划中设置预设时长", Toast.LENGTH_SHORT).show();
            return;
        }

        final int totalSeconds = duration;
        timerTotalSeconds = totalSeconds;
        timerRemainingSeconds = totalSeconds;
        timerRunning = true;
        btnTimer.setEnabled(true);
        btnTimerReset.setEnabled(true);
        countDownTimer = new android.os.CountDownTimer(totalSeconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerRemainingSeconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                long remaining = timerRemainingSeconds;
                btnTimer.setText(String.format(Locale.getDefault(), "计时中 %02d:%02d", remaining / 60, remaining % 60));
            }

            @Override
            public void onFinish() {
                timerRemainingSeconds = 0;
                timerRunning = false;
                countDownTimer = null;
                etDuration.setText(String.valueOf(totalSeconds));
                btnTimer.setText("计时完成");
                btnTimer.setEnabled(true);
                btnTimerReset.setEnabled(true);
                Toast.makeText(requireContext(), "时长已回填，点击保存记录", Toast.LENGTH_SHORT).show();
            }
        };
        countDownTimer.start();
    }

    private void save() {
        String name = plan != null ? plan.getName() : etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "请输入动作名称", Toast.LENGTH_SHORT).show();
            return;
        }

        int sets = parseInt(etSets, -1);
        int reps = parseInt(etReps, -1);
        float weight = parseFloat(etWeight, -1);
        int duration = parseInt(etDuration, -1);
        if (sets < 0 || reps < 0 || weight < 0 || duration < 0) {
            Toast.makeText(requireContext(), "请输入不小于 0 的数值", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sets == 0 && reps == 0 && weight == 0 && duration == 0) {
            Toast.makeText(requireContext(), "至少填写一项实际训练数据", Toast.LENGTH_SHORT).show();
            return;
        }

        if (plan != null) {
            Bundle result = new Bundle();
            result.putInt("plan_id", plan.getPlanId());
            result.putLong("date", date);
            result.putInt("sets", sets);
            result.putInt("reps", reps);
            result.putFloat("weight", weight);
            result.putInt("duration", duration);
            getParentFragmentManager().setFragmentResult(RESULT_PLAN_SAVED, result);
        } else {
            long createdAt = existingExtraLog == null ? System.currentTimeMillis() : existingExtraLog.getCreatedAt();
            ExtraExerciseLog log = new ExtraExerciseLog(date, name, extraBodyPart, extraCategory,
                    extraLibraryId, sets, reps, weight, duration, true, createdAt);
            if (existingExtraLog != null) {
                log.setId(existingExtraLog.getId());
            }
            Bundle result = new Bundle();
            result.putSerializable("log", log);
            getParentFragmentManager().setFragmentResult(RESULT_EXTRA_SAVED, result);
        }
        dismiss();
    }

    private int parseInt(EditText editText, int invalidValue) {
        String value = editText.getText().toString().trim();
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return invalidValue;
        }
    }

    private float parseFloat(EditText editText, float invalidValue) {
        String value = editText.getText().toString().trim();
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return invalidValue;
        }
    }

    private String formatMetrics(int sets, int reps, float weight, int duration) {
        StringBuilder builder = new StringBuilder();
        if (sets > 0 || reps > 0) {
            builder.append(sets).append(" 组 × ").append(reps).append(" 次");
        }
        if (weight > 0) {
            appendSeparator(builder);
            builder.append(String.format(Locale.getDefault(), "%.1f kg", weight));
        }
        if (duration > 0) {
            appendSeparator(builder).append(duration).append(" 秒");
        }
        return builder.length() == 0 ? "未设置目标" : builder.toString();
    }

    private StringBuilder appendSeparator(StringBuilder builder) {
        if (builder.length() > 0) {
            builder.append(" · ");
        }
        return builder;
    }

    @Override
    public void onDestroyView() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        super.onDestroyView();
    }
}
