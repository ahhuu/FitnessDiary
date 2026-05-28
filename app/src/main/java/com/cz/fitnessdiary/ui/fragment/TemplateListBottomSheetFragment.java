package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.databinding.FragmentTemplateListBottomSheetBinding;
import com.cz.fitnessdiary.model.TrainingTemplate;
import com.cz.fitnessdiary.ui.adapter.TemplateListAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class TemplateListBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentTemplateListBottomSheetBinding binding;
    private TemplateListAdapter adapter;
    private List<TrainingTemplate> allTemplates = new ArrayList<>();

    public static TemplateListBottomSheetFragment newInstance() {
        return new TemplateListBottomSheetFragment();
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTemplateListBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new TemplateListAdapter();
        binding.rvTemplates.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTemplates.setAdapter(adapter);

        adapter.setOnTemplateClickListener(template -> {
            TemplatePreviewBottomSheetFragment preview =
                    TemplatePreviewBottomSheetFragment.newInstance(template);
            preview.show(getParentFragmentManager(), "TemplatePreviewBottomSheet");
        });

        setupFilterChips();
        loadTemplates();
    }

    private void setupFilterChips() {
        View.OnClickListener clickListener = v -> {
            binding.chipAll.setSelected(v == binding.chipAll);
            binding.chipBulking.setSelected(v == binding.chipBulking);
            binding.chipCutting.setSelected(v == binding.chipCutting);
            binding.chipStrength.setSelected(v == binding.chipStrength);

            String filter = "全部";
            if (v == binding.chipBulking) {
                filter = "增肌";
            } else if (v == binding.chipCutting) {
                filter = "减脂";
            } else if (v == binding.chipStrength) {
                filter = "增力";
            }
            adapter.setFilter(filter);
        };

        binding.chipAll.setOnClickListener(clickListener);
        binding.chipBulking.setOnClickListener(clickListener);
        binding.chipCutting.setOnClickListener(clickListener);
        binding.chipStrength.setOnClickListener(clickListener);

        // 默认选中全部
        binding.chipAll.setSelected(true);
    }

    private void loadTemplates() {
        try {
            InputStreamReader reader = new InputStreamReader(
                    requireContext().getAssets().open("training_templates.json"), "UTF-8");
            Type type = new TypeToken<TemplateListWrapper>() {}.getType();
            TemplateListWrapper wrapper = new Gson().fromJson(reader, type);
            reader.close();

            if (wrapper != null && wrapper.templates != null) {
                allTemplates = wrapper.templates;
                adapter.setTemplates(allTemplates);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class TemplateListWrapper {
        List<TrainingTemplate> templates;
    }
}
