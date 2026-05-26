package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.cz.fitnessdiary.databinding.FragmentExerciseLibraryBottomSheetBinding;
import com.cz.fitnessdiary.repository.ExerciseLibraryRepository;
import com.cz.fitnessdiary.ui.adapter.ExerciseLibraryAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseLibraryBottomSheet extends BottomSheetDialogFragment {

    private FragmentExerciseLibraryBottomSheetBinding binding;
    private ExerciseLibraryRepository repository;
    private ExerciseLibraryAdapter adapter;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private OnExerciseSelectedListener selectedListener;

    public interface OnExerciseSelectedListener {
        void onExerciseSelected(ExerciseLibrary exercise);
    }

    public static ExerciseLibraryBottomSheet newInstance() {
        return new ExerciseLibraryBottomSheet();
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    public void setOnExerciseSelectedListener(OnExerciseSelectedListener listener) {
        this.selectedListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExerciseLibraryBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new ExerciseLibraryRepository(requireContext());

        adapter = new ExerciseLibraryAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        adapter.setOnExerciseClickListener(exercise -> {
            if (selectedListener != null) {
                selectedListener.onExerciseSelected(exercise);
            }
            dismiss();
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String keyword = s.toString().trim();
                searchExercises(keyword);
            }
        });

        loadAllExercises();
    }

    private void loadAllExercises() {
        executor.execute(() -> {
            List<ExerciseLibrary> all = repository.getAllExercisesSync();
            if (all != null && isAdded()) {
                requireActivity().runOnUiThread(() -> adapter.setExercises(all));
            }
        });
    }

    private void searchExercises(String keyword) {
        if (keyword.isEmpty()) {
            loadAllExercises();
            return;
        }
        executor.execute(() -> {
            List<ExerciseLibrary> results = repository.searchExercises(keyword);
            if (results != null && isAdded()) {
                requireActivity().runOnUiThread(() -> adapter.setExercises(results));
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
