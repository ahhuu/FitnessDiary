package com.cz.fitnessdiary.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.databinding.FragmentAddPlanBottomSheetBinding;
import com.cz.fitnessdiary.utils.PermissionHelper;
import com.cz.fitnessdiary.viewmodel.PlanViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * 训练计划底栏弹窗 (支持新增和编辑)
 */
public class AddPlanBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentAddPlanBottomSheetBinding binding;
    private PlanViewModel viewModel;
    private TrainingPlan existingPlan = null;
    private String selectedMediaUri = null;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    public static AddPlanBottomSheetFragment newInstance(@Nullable TrainingPlan plan) {
        AddPlanBottomSheetFragment fragment = new AddPlanBottomSheetFragment();
        if (plan != null) {
            Bundle args = new Bundle();
            args.putSerializable("plan", plan);
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 注册相册选择器
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri mediaUri = result.getData().getData();
                        if (mediaUri != null) {
                            // [v1.1] 物理存储：本地化图片
                            java.io.File localFile = com.cz.fitnessdiary.utils.MediaManager
                                    .saveToInternal(requireContext(), mediaUri);
                            if (localFile != null) {
                                selectedMediaUri = localFile.getAbsolutePath();
                                updateMediaPreview();
                            } else {
                                Toast.makeText(requireContext(), "素材本地化失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAddPlanBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(PlanViewModel.class);

        if (getArguments() != null) {
            existingPlan = (TrainingPlan) getArguments().getSerializable("plan");
        }

        setupUI();
        setupListeners();
        setupCategoryAutoComplete();
    }

    private void setupCategoryAutoComplete() {
        // 由于布局中虽然 ID 是 et_plan_category 但类型是 TextInputEditText，
        // 我们需要确保它是能够处理联想的（如果 xml 中没改，这里需要动态注入或转换）
        // 建议 xml 也要改，但在代码中可以先尝试这种注入方式。
        // v1.2: 观察 ViewModel 联想分类列表
        viewModel.getUniqueCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null && !categories.isEmpty()) {
                android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        categories);

                // 进行安全转换，如果 xml 已经是 AutoCompleteTextView 或子类
                if (binding.etPlanCategory instanceof android.widget.AutoCompleteTextView) {
                    android.widget.AutoCompleteTextView autoCategory = (android.widget.AutoCompleteTextView) binding.etPlanCategory;
                    autoCategory.setAdapter(adapter);
                    autoCategory.setThreshold(0);
                    // 点击即显示
                    autoCategory.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus && autoCategory.getText().toString().isEmpty()) {
                            autoCategory.showDropDown();
                        }
                    });
                    autoCategory.setOnClickListener(v -> autoCategory.showDropDown());
                }
            }
        });
    }

    private void setupUI() {
        if (existingPlan != null) {
            binding.tvTitle.setText("编辑训练计划");
            binding.etPlanName.setText(existingPlan.getName());
            binding.etPlanDescription.setText(existingPlan.getDescription());
            binding.etSets.setText(String.valueOf(existingPlan.getSets()));
            binding.etReps.setText(String.valueOf(existingPlan.getReps()));
            binding.etWeight.setText(String.valueOf(existingPlan.getWeight()));

            // 显示时去掉前缀，保留最后一段（部位名）
            // e.g. "基础-胸"→"胸", "自定义-我的计划-胸"→"胸"
            String displayCat = existingPlan.getCategory();
            if (displayCat != null && displayCat.contains("-")) {
                int lastDash = displayCat.lastIndexOf("-");
                displayCat = displayCat.substring(lastDash + 1);
            }
            binding.etPlanCategory.setText(displayCat);

            binding.etDuration.setText(String.valueOf(existingPlan.getDuration()));
            binding.btnSave.setText("保存修改");

            selectedMediaUri = existingPlan.getMediaUri();
            updateMediaPreview();

            // 填充排期
            String scheduledDays = existingPlan.getScheduledDays();
            if ("none".equals(scheduledDays)) {
                // 真正不排期，保持所有的 Chip 不勾选
            } else if (scheduledDays != null && !scheduledDays.isEmpty() && !scheduledDays.equals("0")) {
                String[] days = scheduledDays.split(",");
                for (String day : days) {
                    for (int i = 0; i < binding.chipDaysGroup.getChildCount(); i++) {
                        View child = binding.chipDaysGroup.getChildAt(i);
                        if (child instanceof Chip) {
                            Chip chip = (Chip) child;
                            if (chip.getTag() != null && chip.getTag().toString().equals(day.trim())) {
                                chip.setChecked(true);
                            }
                        }
                    }
                }
            } else {
                // 如果没有排期，默认每天排期（全部勾选）
                checkAllDays();
            }
        } else {
            binding.tvTitle.setText("新增训练计划");
            // 新增计划时，默认每天排期（全部勾选）
            checkAllDays();
        }
    }

    private void checkAllDays() {
        for (int i = 0; i < binding.chipDaysGroup.getChildCount(); i++) {
            View child = binding.chipDaysGroup.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(true);
            }
        }
    }

    private void setupListeners() {
        binding.btnPickFromLibrary.setOnClickListener(v -> {
            ExerciseLibraryBottomSheet bottomSheet = ExerciseLibraryBottomSheet.newInstance();
            bottomSheet.setOnExerciseSelectedListener(exercise -> fillFromExercise(exercise));
            bottomSheet.show(getParentFragmentManager(), "ExerciseLibraryBottomSheet");
        });

        binding.btnAddMedia.setOnClickListener(v -> {
            if (!PermissionHelper.hasMediaPermission(requireContext())) {
                PermissionHelper.requestMediaPermission(requireActivity());
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try {
                imagePickerLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "无法打开相册", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnPickGif.setOnClickListener(v -> showGifPickerDialog());

        binding.btnSave.setOnClickListener(v -> savePlan());
    }

    /**
     * 官方GIF图示选择器：列出assets/gifs/目录，支持模糊搜索
     */
    private void showGifPickerDialog() {
        new Thread(() -> {
            String[] gifFiles;
            try {
                gifFiles = requireContext().getAssets().list("gifs");
            } catch (java.io.IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "无法读取GIF目录", Toast.LENGTH_SHORT).show());
                }
                return;
            }
            if (gifFiles == null || gifFiles.length == 0) return;
            final String[] finalFiles = gifFiles;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 全屏搜索对话框
                    android.app.Dialog dialog = new android.app.Dialog(requireContext(),
                            android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
                    View view = LayoutInflater.from(requireContext())
                            .inflate(R.layout.dialog_food_wiki, null);
                    dialog.setContentView(view);

                    EditText etSearch = view.findViewById(R.id.et_search_query);
                    etSearch.setHint("搜索GIF动作名称...");
                    View btnBack = view.findViewById(R.id.btn_back);
                    androidx.recyclerview.widget.RecyclerView rv = view.findViewById(R.id.rv_food_results);
                    View fabAdd = view.findViewById(R.id.fab_add_food);
                    fabAdd.setVisibility(View.GONE);

                    btnBack.setOnClickListener(v2 -> dialog.dismiss());
                    rv.setLayoutManager(new LinearLayoutManager(requireContext()));

                    // RecyclerView适配器：显示GIF文件名列表
                    class GifAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<GifAdapter.VH> {
                        List<String> items = new java.util.ArrayList<>();
                        void setItems(List<String> list) { items = list; notifyDataSetChanged(); }
                        @NonNull @Override
                        public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
                            TextView tv = new TextView(p.getContext());
                            tv.setPadding(32, 24, 32, 24);
                            tv.setTextSize(15);
                            tv.setTextColor(0xFF333333);
                            return new VH(tv);
                        }
                        @Override
                        public void onBindViewHolder(@NonNull VH h, int pos) {
                            String name = items.get(pos);
                            ((TextView)h.itemView).setText(name);
                            h.itemView.setOnClickListener(v -> {
                                // 找回原始文件名（含.gif）
                                String gifName = name + ".gif";
                                selectedMediaUri = "file:///android_asset/gifs/" + gifName;
                                updateMediaPreview();
                                dialog.dismiss();
                                Toast.makeText(requireContext(), "已选择: " + name, Toast.LENGTH_SHORT).show();
                            });
                        }
                        @Override public int getItemCount() { return items.size(); }
                        class VH extends RecyclerView.ViewHolder {
                            VH(View v) { super(v); }
                        }
                    }

                    GifAdapter gifAdapter = new GifAdapter();
                    rv.setAdapter(gifAdapter);
                    gifAdapter.setItems(java.util.Arrays.asList(stripExtensions(finalFiles)));

                    // 模糊搜索
                    etSearch.addTextChangedListener(new android.text.TextWatcher() {
                        @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                            String q = s.toString().trim().toLowerCase();
                            java.util.List<String> filtered = new java.util.ArrayList<>();
                            for (String f : finalFiles) {
                                String name = stripExt(f);
                                if (q.isEmpty() || name.toLowerCase().contains(q)) filtered.add(name);
                            }
                            gifAdapter.setItems(filtered);
                        }
                        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                        @Override public void afterTextChanged(android.text.Editable s) {}
                    });

                    dialog.show();
                });
            }
        }).start();
    }

    private static String stripExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String[] stripExtensions(String[] files) {
        String[] result = new String[files.length];
        for (int i = 0; i < files.length; i++) result[i] = stripExt(files[i]);
        return result;
    }

    private void fillFromExercise(ExerciseLibrary exercise) {
        binding.etPlanName.setText(exercise.getName());
        if (exercise.getDescription() != null && !exercise.getDescription().isEmpty()) {
            binding.etPlanDescription.setText(exercise.getDescription());
        }
        binding.etPlanCategory.setText(exercise.getBodyPart());
    }

    private void updateMediaPreview() {
        if (selectedMediaUri != null) {
            binding.ivMediaPreview.setVisibility(View.VISIBLE);
            Object loadTarget = selectedMediaUri.startsWith("/") ? new java.io.File(selectedMediaUri)
                    : Uri.parse(selectedMediaUri);
            Glide.with(this).load(loadTarget).centerCrop().into(binding.ivMediaPreview);
        } else {
            binding.ivMediaPreview.setVisibility(View.GONE);
        }
    }

    private void savePlan() {
        String name = binding.etPlanName.getText().toString().trim();
        String description = binding.etPlanDescription.getText().toString().trim();
        String setsStr = binding.etSets.getText().toString().trim();
        String repsStr = binding.etReps.getText().toString().trim();
        String weightStr = binding.etWeight.getText().toString().trim();
        String category = binding.etPlanCategory.getText().toString().trim();
        String durationStr = binding.etDuration.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(getContext(), "请输入计划名称", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int sets = setsStr.isEmpty() ? 0 : Integer.parseInt(setsStr);
            int reps = repsStr.isEmpty() ? 0 : Integer.parseInt(repsStr);
            float weight = parseFloat(weightStr);
            int duration = durationStr.isEmpty() ? 0 : Integer.parseInt(durationStr);

            // 获取排期
            StringBuilder daysBuilder = new StringBuilder();
            for (int i = 0; i < binding.chipDaysGroup.getChildCount(); i++) {
                Chip chip = (Chip) binding.chipDaysGroup.getChildAt(i);
                if (chip.isChecked() && chip.getTag() != null) {
                    if (daysBuilder.length() > 0)
                        daysBuilder.append(",");
                    daysBuilder.append(chip.getTag().toString());
                }
            }
            String scheduledDays = daysBuilder.length() == 0 ? "none" : daysBuilder.toString();

            // 新增计划统一归类为 "自定义"；编辑计划保留原有完整前缀
            String finalCategory;
            if (existingPlan != null) {
                // 编辑时保留原分类前缀(最后一段之前的所有部分)
                // e.g. "基础-胸"→前缀"基础-", "自定义-我的计划-胸"→前缀"自定义-我的计划-"
                String originalCat = existingPlan.getCategory();
                String prefix = "";
                if (originalCat != null && originalCat.contains("-")) {
                    int lastDash = originalCat.lastIndexOf("-");
                    prefix = originalCat.substring(0, lastDash + 1);
                }
                finalCategory = category.isEmpty() ? "无分类" : (prefix + category);
            } else {
                // 新增计划归入当前活跃的个人计划模板
                String activePlan = viewModel.getActivePersonalPlanName().getValue();
                if (activePlan == null || activePlan.isEmpty()) activePlan = "默认自定义计划";
                String targetPrefix = "自定义-" + activePlan + "-";
                finalCategory = category.isEmpty() ? "未分类" : category;
                if (!finalCategory.startsWith("基础-") && !finalCategory.startsWith("进阶-")
                        && !finalCategory.startsWith("自定义-")) {
                    finalCategory = targetPrefix + finalCategory;
                }
            }

            if (existingPlan == null) {
                TrainingPlan newPlan = new TrainingPlan(name, description, System.currentTimeMillis(), sets, reps,
                        selectedMediaUri);
                newPlan.setCategory(finalCategory);
                newPlan.setScheduledDays(scheduledDays);
                newPlan.setDuration(duration);
                newPlan.setWeight(weight);
                viewModel.addPlan(newPlan);
                Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();
            } else {
                existingPlan.setName(name);
                existingPlan.setDescription(description);
                existingPlan.setSets(sets);
                existingPlan.setReps(reps);
                existingPlan.setCategory(finalCategory);
                existingPlan.setScheduledDays(scheduledDays);
                existingPlan.setMediaUri(selectedMediaUri);
                existingPlan.setDuration(duration);
                existingPlan.setWeight(weight);
                viewModel.updatePlan(existingPlan);
                Toast.makeText(getContext(), "修改成功", Toast.LENGTH_SHORT).show();
            }
            dismiss();
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private float parseFloat(String s) {
        if (s == null || s.isEmpty()) return 0f;
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return 0f; }
    }
}
