package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.NestedScrollView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.cz.fitnessdiary.repository.ExerciseLibraryRepository;
import com.cz.fitnessdiary.utils.DateUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Selects a library exercise or starts a one-off exercise by name. */
public class AddTodayExerciseBottomSheet extends BottomSheetDialogFragment {

    public interface OnExerciseSelectedListener {
        void onExerciseSelected(String name, String bodyPart, String category, long libraryId,
                boolean saveToLibrary);
    }

    private static final String ARG_DATE = "date";

    private final List<ExerciseLibrary> allExercises = new ArrayList<>();
    private final List<ExerciseLibrary> filteredExercises = new ArrayList<>();
    private ExerciseAdapter adapter;
    private ExerciseLibraryRepository repository;
    private OnExerciseSelectedListener listener;
    private EditText etSearch;
    private EditText etManualName;
    private CheckBox cbSaveToLibrary;
    private long date;

    public static AddTodayExerciseBottomSheet newInstance(long date) {
        AddTodayExerciseBottomSheet sheet = new AddTodayExerciseBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_DATE, date);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnExerciseSelectedListener(OnExerciseSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        date = DateUtils.getDayStartTimestamp(getArguments() == null
                ? DateUtils.getTodayStartTimestamp()
                : getArguments().getLong(ARG_DATE, DateUtils.getTodayStartTimestamp()));
        repository = new ExerciseLibraryRepository(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_today_exercise, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        etSearch = view.findViewById(R.id.et_today_exercise_search);
        etManualName = view.findViewById(R.id.et_today_exercise_name);
        NestedScrollView scrollView = view.findViewById(R.id.scroll_add_today_exercise);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (insetView, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int basePadding = Math.round(20f * getResources().getDisplayMetrics().density);
            insetView.setPadding(insetView.getPaddingLeft(), insetView.getPaddingTop(),
                    insetView.getPaddingRight(), basePadding + imeBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(scrollView);
        cbSaveToLibrary = view.findViewById(R.id.cb_save_to_library);
        RecyclerView rvExercises = view.findViewById(R.id.rv_today_exercise_library);
        MaterialButton btnManual = view.findViewById(R.id.btn_use_manual_exercise);
        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel_today_exercise);

        adapter = new ExerciseAdapter();
        rvExercises.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvExercises.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterExercises(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnManual.setOnClickListener(v -> {
            String name = etManualName.getText().toString().trim();
            if (name.isEmpty()) {
                etManualName.setError("请输入动作名称");
                return;
            }
            select(name, "其他", "其他", 0, cbSaveToLibrary.isChecked());
        });
        btnCancel.setOnClickListener(v -> dismiss());

        etManualName.setOnFocusChangeListener((focusedView, hasFocus) -> {
            if (hasFocus) {
                scrollView.postDelayed(() -> {
                    Rect visibleRect = new Rect(0, 0, focusedView.getWidth(), focusedView.getHeight());
                    scrollView.offsetDescendantRectToMyCoords(focusedView, visibleRect);
                    scrollView.requestChildRectangleOnScreen(
                            scrollView.getChildAt(0), visibleRect, false);
                }, 260L);
            }
        });

        new Thread(() -> {
            List<ExerciseLibrary> exercises = repository.getAllExercisesSync();
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                allExercises.clear();
                if (exercises != null) {
                    allExercises.addAll(exercises);
                }
                filterExercises(etSearch.getText().toString());
            });
        }).start();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        View bottomSheet = getView() == null ? null : (View) getView().getParent();
        if (bottomSheet != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void filterExercises(String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.getDefault());
        filteredExercises.clear();
        for (ExerciseLibrary exercise : allExercises) {
            String name = exercise.getName() == null ? "" : exercise.getName();
            if (query.isEmpty() || name.toLowerCase(Locale.getDefault()).contains(query)) {
                filteredExercises.add(exercise);
            }
            if (filteredExercises.size() >= 20) {
                break;
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void select(String name, String bodyPart, String category, long libraryId,
            boolean saveToLibrary) {
        if (listener != null) {
            listener.onExerciseSelected(name, bodyPart, category, libraryId, saveToLibrary);
        }
        dismiss();
    }

    private class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_today_exercise_choice, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ExerciseLibrary exercise = filteredExercises.get(position);
            holder.name.setText(exercise.getName());
            String bodyPart = exercise.getBodyPart() == null ? "其他" : exercise.getBodyPart();
            String equipment = exercise.getEquipment() == null ? "" : exercise.getEquipment();
            holder.subtitle.setText(equipment.isEmpty() ? bodyPart : bodyPart + " · " + equipment);
            holder.itemView.setOnClickListener(v -> select(exercise.getName(), bodyPart,
                    exercise.getCategory() == null ? bodyPart : exercise.getCategory(),
                    exercise.getId(), false));
        }

        @Override
        public int getItemCount() {
            return filteredExercises.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView subtitle;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tv_today_exercise_choice_name);
                subtitle = itemView.findViewById(R.id.tv_today_exercise_choice_subtitle);
            }
        }
    }
}
