package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentTemplatePreviewBottomSheetBinding;
import com.cz.fitnessdiary.model.TemplateExercise;
import com.cz.fitnessdiary.model.TrainingTemplate;
import com.cz.fitnessdiary.ui.adapter.TemplateExerciseAdapter;
import com.cz.fitnessdiary.viewmodel.PlanViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class TemplatePreviewBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentTemplatePreviewBottomSheetBinding binding;
    private TrainingTemplate template;

    public static TemplatePreviewBottomSheetFragment newInstance(TrainingTemplate template) {
        TemplatePreviewBottomSheetFragment fragment = new TemplatePreviewBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable("template", template);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            template = (TrainingTemplate) getArguments().getSerializable("template");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTemplatePreviewBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (template == null) {
            dismiss();
            return;
        }

        binding.tvTemplateName.setText(template.getName());

        String diffText;
        switch (template.getDifficulty()) {
            case 1: diffText = "初级"; break;
            case 2: diffText = "中级"; break;
            case 3: diffText = "高级"; break;
            default: diffText = "";
        }
        binding.tvDifficulty.setText(diffText);
        binding.tvGoal.setText(template.getGoal());
        binding.tvDays.setText(template.getDaysPerWeek() + "天/周");
        binding.tvDescription.setText(template.getDescription());

        TemplateExerciseAdapter exerciseAdapter = new TemplateExerciseAdapter();
        binding.rvExercises.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvExercises.setAdapter(exerciseAdapter);
        exerciseAdapter.setExercises(template.getExercises());

        int exerciseCount = template.getExercises() != null ? template.getExercises().size() : 0;
        binding.btnImport.setText("一键导入（" + exerciseCount + " 个动作）");

        binding.btnImport.setOnClickListener(v -> {
            PlanViewModel planViewModel = new ViewModelProvider(requireActivity()).get(PlanViewModel.class);
            planViewModel.importTemplate(template.getExercises(), template.getDifficulty());
            Toast.makeText(requireContext(), "成功导入 " + exerciseCount + " 个训练计划", Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
