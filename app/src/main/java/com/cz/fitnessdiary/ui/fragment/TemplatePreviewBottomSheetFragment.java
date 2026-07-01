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

    private String currentVersionKey = "gym";

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

        // 初始化 RecyclerView
        TemplateExerciseAdapter exerciseAdapter = new TemplateExerciseAdapter();
        binding.rvExercises.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvExercises.setAdapter(exerciseAdapter);

        // 校验设备版本支持状态并设置默认选中
        java.util.Map<String, com.cz.fitnessdiary.model.TrainingTemplate.TemplateVersion> versions = template.getVersions();
        boolean hasGym = versions != null && versions.containsKey("gym");
        boolean hasHome = versions != null && versions.containsKey("home");
        boolean hasBodyweight = versions != null && versions.containsKey("bodyweight");

        binding.btnGym.setEnabled(hasGym);
        if (!hasGym) {
            binding.btnGym.setText("健身房 (不支持)");
        }
        binding.btnHome.setEnabled(hasHome);
        if (!hasHome) {
            binding.btnHome.setText("居家 (不支持)");
        }
        binding.btnBodyweight.setEnabled(hasBodyweight);
        if (!hasBodyweight) {
            binding.btnBodyweight.setText("自重 (不支持)");
        }

        // 选择一个默认激活的版本
        if (hasGym) {
            currentVersionKey = "gym";
            binding.toggleGroupEquipment.check(R.id.btn_gym);
        } else if (hasHome) {
            currentVersionKey = "home";
            binding.toggleGroupEquipment.check(R.id.btn_home);
        } else if (hasBodyweight) {
            currentVersionKey = "bodyweight";
            binding.toggleGroupEquipment.check(R.id.btn_bodyweight);
        }

        // 刷新列表显示
        refreshExercises(exerciseAdapter);

        // 绑定版本切换事件
        binding.toggleGroupEquipment.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_gym) {
                    currentVersionKey = "gym";
                } else if (checkedId == R.id.btn_home) {
                    currentVersionKey = "home";
                } else if (checkedId == R.id.btn_bodyweight) {
                    currentVersionKey = "bodyweight";
                }
                refreshExercises(exerciseAdapter);
            }
        });

        binding.btnImport.setOnClickListener(v -> {
            java.util.List<com.cz.fitnessdiary.model.TemplateExercise> exercises = template.getExercisesForVersion(currentVersionKey);
            int size = exercises != null ? exercises.size() : 0;
            PlanViewModel planViewModel = new ViewModelProvider(requireActivity()).get(PlanViewModel.class);
            planViewModel.importTemplate(exercises, template.getName());
            Toast.makeText(requireContext(), "成功导入 " + size + " 个训练计划", Toast.LENGTH_SHORT).show();
            
            // 发送通知让计划页面切到“当前计划”Tab
            getParentFragmentManager().setFragmentResult("plan_imported_request", new Bundle());
            
            dismiss();
        });
    }

    private void refreshExercises(TemplateExerciseAdapter adapter) {
        java.util.List<com.cz.fitnessdiary.model.TemplateExercise> exercises = template.getExercisesForVersion(currentVersionKey);
        adapter.setExercises(exercises);
        int exerciseCount = exercises != null ? exercises.size() : 0;
        binding.btnImport.setText("一键导入（" + exerciseCount + " 个动作）");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
