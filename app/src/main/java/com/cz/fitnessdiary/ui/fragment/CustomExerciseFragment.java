package com.cz.fitnessdiary.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.cz.fitnessdiary.repository.ExerciseLibraryRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义动作管理页面
 * 官方动作（名称在assets/exercise_library.json中）仅可查看，自定义动作可编辑/删除
 */
public class CustomExerciseFragment extends Fragment {

    private static Set<String> officialNames = null;

    private ExerciseLibraryRepository repository;
    private List<ExerciseLibrary> allExercises = new ArrayList<>();
    private ExerciseListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_custom_exercise_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new ExerciseLibraryRepository(requireActivity().getApplication());

        // 预加载官方动作名称白名单 (必须在UI线程，因为需要AssetManager)
        ensureOfficialNamesLoaded(requireContext());

        View btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> Navigation.findNavController(requireView()).navigateUp());

        RecyclerView rv = view.findViewById(R.id.rv_exercises);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ExerciseListAdapter();
        rv.setAdapter(adapter);

        view.findViewById(R.id.fab_add).setOnClickListener(v -> showAddExerciseDialog());

        loadExercises();
    }

    private void loadExercises() {
        new Thread(() -> {
            List<ExerciseLibrary> exercises = repository.getAllExercisesSync();
            if (exercises == null) exercises = new ArrayList<>();
            // 排序：自定义动作在前，官方在后；同类按id倒序(新的在前)
            Collections.sort(exercises, new Comparator<ExerciseLibrary>() {
                @Override
                public int compare(ExerciseLibrary a, ExerciseLibrary b) {
                    boolean aIsOfficial = isOfficial(a);
                    boolean bIsOfficial = isOfficial(b);
                    if (aIsOfficial != bIsOfficial) {
                        return aIsOfficial ? 1 : -1;
                    }
                    return Long.compare(b.getId(), a.getId());
                }
            });
            List<ExerciseLibrary> finalExercises = exercises;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    allExercises.clear();
                    allExercises.addAll(finalExercises);
                    adapter.notifyDataSetChanged();
                });
            }
        }).start();
    }

    /**
     * 以名称判断是否为官方动作：从 assets/exercise_library.json 加载官方动作名称白名单
     */
    private static void ensureOfficialNamesLoaded(Context context) {
        if (officialNames != null) return;
        officialNames = new HashSet<>();
        try {
            java.io.InputStreamReader reader = new java.io.InputStreamReader(
                    context.getAssets().open("exercise_library.json"), "UTF-8");
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> root = gson.fromJson(reader, type);
            reader.close();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> exercises = (List<Map<String, Object>>) root.get("exercises");
            if (exercises != null) {
                for (Map<String, Object> ex : exercises) {
                    String name = (String) ex.get("name");
                    if (name != null) officialNames.add(name);
                }
            }
        } catch (Exception e) {
            // 解析失败则退回到空集合，所有动作视为自定义
        }
    }

    private boolean isOfficial(ExerciseLibrary exercise) {
        return officialNames != null && officialNames.contains(exercise.getName());
    }

    private void showAddExerciseDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_custom_exercise, null);
        Spinner spinnerBodyPart = dialogView.findViewById(R.id.spinner_body_part);
        Spinner spinnerEquipment = dialogView.findViewById(R.id.spinner_equipment);
        EditText etName = dialogView.findViewById(R.id.et_exercise_name);
        EditText etSubCategory = dialogView.findViewById(R.id.et_sub_category);
        EditText etDescription = dialogView.findViewById(R.id.et_description);

        List<String> partOptions = Arrays.asList("胸部", "背部", "腿部", "肩部", "手臂", "腹部", "臀部", "全身", "拉伸");
        android.widget.ArrayAdapter<String> partAdapter = new android.widget.ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, partOptions);
        partAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBodyPart.setAdapter(partAdapter);

        List<String> eqOptions = Arrays.asList("自重", "杠铃", "哑铃", "壶铃", "拉力器", "固定器械", "其他");
        android.widget.ArrayAdapter<String> eqAdapter = new android.widget.ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, eqOptions);
        eqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEquipment.setAdapter(eqAdapter);

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "动作名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String bodyPart = spinnerBodyPart.getSelectedItem().toString();
                    String subCat = etSubCategory.getText().toString().trim();
                    String equipment = spinnerEquipment.getSelectedItem().toString();
                    String desc = etDescription.getText().toString().trim();

                    String category = bodyPart + ": " + (subCat.isEmpty() ? "基础" : subCat);

                    ExerciseLibrary customEx = new ExerciseLibrary(
                            name, bodyPart, subCat.isEmpty() ? "基础" : subCat, desc, 2, equipment, category
                    );

                    new Thread(() -> {
                        repository.insert(customEx);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();
                                loadExercises();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditExerciseDialog(final ExerciseLibrary exercise) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_custom_exercise, null);
        Spinner spinnerBodyPart = dialogView.findViewById(R.id.spinner_body_part);
        Spinner spinnerEquipment = dialogView.findViewById(R.id.spinner_equipment);
        EditText etName = dialogView.findViewById(R.id.et_exercise_name);
        EditText etSubCategory = dialogView.findViewById(R.id.et_sub_category);
        EditText etDescription = dialogView.findViewById(R.id.et_description);

        // Pre-fill with existing values
        etName.setText(exercise.getName());

        List<String> partOptions = Arrays.asList("胸部", "背部", "腿部", "肩部", "手臂", "腹部", "臀部", "全身", "拉伸");
        android.widget.ArrayAdapter<String> partAdapter = new android.widget.ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, partOptions);
        partAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBodyPart.setAdapter(partAdapter);

        // Set body part spinner selection
        String bodyPart = exercise.getBodyPart();
        for (int i = 0; i < partOptions.size(); i++) {
            if (partOptions.get(i).equals(bodyPart)) {
                spinnerBodyPart.setSelection(i);
                break;
            }
        }

        etSubCategory.setText(exercise.getSubCategory() != null && !"基础".equals(exercise.getSubCategory())
                ? exercise.getSubCategory() : "");

        List<String> eqOptions = Arrays.asList("自重", "杠铃", "哑铃", "壶铃", "拉力器", "固定器械", "其他");
        android.widget.ArrayAdapter<String> eqAdapter = new android.widget.ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, eqOptions);
        eqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEquipment.setAdapter(eqAdapter);

        // Set equipment spinner selection
        String equipment = exercise.getEquipment();
        for (int i = 0; i < eqOptions.size(); i++) {
            if (eqOptions.get(i).equals(equipment)) {
                spinnerEquipment.setSelection(i);
                break;
            }
        }

        etDescription.setText(exercise.getDescription());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑自定义动作")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "动作名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    exercise.setName(name);
                    exercise.setBodyPart(spinnerBodyPart.getSelectedItem().toString());
                    String subCat = etSubCategory.getText().toString().trim();
                    exercise.setSubCategory(subCat.isEmpty() ? "基础" : subCat);
                    exercise.setEquipment(spinnerEquipment.getSelectedItem().toString());
                    exercise.setDescription(etDescription.getText().toString().trim());
                    exercise.setCategory(exercise.getBodyPart() + ": " + exercise.getSubCategory());

                    new Thread(() -> {
                        repository.update(exercise);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "更新成功", Toast.LENGTH_SHORT).show();
                                loadExercises();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteExerciseDialog(final ExerciseLibrary exercise) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除自定义动作")
                .setMessage("确定要删除「" + exercise.getName() + "」吗？此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    new Thread(() -> {
                        repository.delete(exercise);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                                loadExercises();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ────────────────────── 列表适配器 ──────────────────────

    private class ExerciseListAdapter extends RecyclerView.Adapter<ExerciseListAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_custom_exercise, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ExerciseLibrary ex = allExercises.get(position);
            holder.tvName.setText(ex.getName());
            holder.tvBodyPart.setText(ex.getBodyPart());
            holder.tvEquipment.setText(ex.getEquipment());

            boolean official = isOfficial(ex);
            holder.tvOfficialBadge.setVisibility(official ? View.VISIBLE : View.GONE);
            holder.btnEdit.setVisibility(official ? View.GONE : View.VISIBLE);
            holder.btnDelete.setVisibility(official ? View.GONE : View.VISIBLE);

            holder.btnEdit.setOnClickListener(v -> showEditExerciseDialog(ex));
            holder.btnDelete.setOnClickListener(v -> showDeleteExerciseDialog(ex));
        }

        @Override
        public int getItemCount() {
            return allExercises.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvBodyPart;
            TextView tvEquipment;
            TextView tvOfficialBadge;
            android.widget.ImageButton btnEdit;
            android.widget.ImageButton btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_exercise_name);
                tvBodyPart = itemView.findViewById(R.id.tv_body_part);
                tvEquipment = itemView.findViewById(R.id.tv_equipment);
                tvOfficialBadge = itemView.findViewById(R.id.tv_official_badge);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
