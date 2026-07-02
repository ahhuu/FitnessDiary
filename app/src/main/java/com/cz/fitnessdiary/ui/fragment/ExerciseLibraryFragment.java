package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.cz.fitnessdiary.databinding.FragmentExerciseLibraryBinding;
import com.cz.fitnessdiary.repository.ExerciseLibraryRepository;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.cz.fitnessdiary.ui.guide.GuideStateManager;
import com.cz.fitnessdiary.ui.guide.GuideStep;
import com.cz.fitnessdiary.ui.guide.PageGuide;
import com.cz.fitnessdiary.ui.guide.TargetedGuideOverlay;

/**
 * 动作库页面 - 双栏交互式极简布局
 * 左侧侧边栏大分类支持折叠/展开子部位；右侧包含器械完整筛选和动作卡片
 */
public class ExerciseLibraryFragment extends Fragment {

    private FragmentExerciseLibraryBinding binding;
    private ExerciseLibraryRepository repository;

    private final List<ExerciseLibrary> allExercises = new ArrayList<>();
    private final List<SidebarItem> sidebarItems = new ArrayList<>();

    private SidebarAdapter sidebarAdapter;
    private ExerciseCardAdapter exerciseAdapter;
    private View emptyStateView;

    private String activeParent = "全部"; // 当前选中的大部位，"全部" 表示不过滤部位
    private String activeChild = "全部";
    private int selectedBodyPartPosition = 0;
    private int selectedEquipmentId = R.id.chip_eq_all;



    // 大分类与子部位的固定映射关系
    private static final Map<String, List<String>> BODY_SUB_MAP = new HashMap<>();
    static {
        BODY_SUB_MAP.put("胸部", Arrays.asList("全部", "上胸", "中下胸"));
        BODY_SUB_MAP.put("背部", Arrays.asList("全部", "背阔肌", "上背/斜方肌", "下背部"));
        BODY_SUB_MAP.put("腿部", Arrays.asList("全部", "股四头肌", "腘绳肌/臀部", "小腿"));
        BODY_SUB_MAP.put("肩部", Arrays.asList("全部", "三角肌前束", "三角肌中束", "三角肌后束"));
        BODY_SUB_MAP.put("手臂", Arrays.asList("全部", "肱二头肌", "肱三头肌", "前臂"));
        BODY_SUB_MAP.put("腹部", Arrays.asList("全部", "上腹肌", "下腹肌", "腹外斜肌"));
        BODY_SUB_MAP.put("全身", Arrays.asList("全部", "有氧", "综合"));
        BODY_SUB_MAP.put("拉伸", Arrays.asList("全部", "上身拉伸", "下身拉伸", "脊柱拉伸", "全身拉伸"));
    }

    // 大部位名称映射（展示名与数据库存的映射，如：胸 -> 胸部）
    private static final Map<String, String> DISPLAY_TO_DB_MAP = new HashMap<>();
    static {
        DISPLAY_TO_DB_MAP.put("全部", "全部");
        DISPLAY_TO_DB_MAP.put("胸", "胸部");
        DISPLAY_TO_DB_MAP.put("背", "背部");
        DISPLAY_TO_DB_MAP.put("腿", "腿部");
        DISPLAY_TO_DB_MAP.put("肩", "肩部");
        DISPLAY_TO_DB_MAP.put("臂", "手臂");
        DISPLAY_TO_DB_MAP.put("腹", "腹部");
        DISPLAY_TO_DB_MAP.put("全身", "全身");
        DISPLAY_TO_DB_MAP.put("拉伸", "拉伸");
    }
    
    private static final Map<String, String> DB_TO_DISPLAY_MAP = new HashMap<>();
    static {
        DB_TO_DISPLAY_MAP.put("全部", "全部");
        DB_TO_DISPLAY_MAP.put("胸部", "胸");
        DB_TO_DISPLAY_MAP.put("背部", "背");
        DB_TO_DISPLAY_MAP.put("腿部", "腿");
        DB_TO_DISPLAY_MAP.put("肩部", "肩");
        DB_TO_DISPLAY_MAP.put("手臂", "臂");
        DB_TO_DISPLAY_MAP.put("腹部", "腹");
        DB_TO_DISPLAY_MAP.put("全身", "全身");
        DB_TO_DISPLAY_MAP.put("拉伸", "拉伸");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentExerciseLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new ExerciseLibraryRepository(requireContext());

        setupToolbar();
        setupRecyclerViews();
        setupFilters();
        loadData();
        setupExerciseLibraryGuide();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> {
            Navigation.findNavController(requireView()).navigateUp();
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterExercises();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnAddCustom.setOnClickListener(v -> {
            showAddCustomExerciseDialog();
        });
    }

    private void setupRecyclerViews() {
        binding.rvBodyParts.setLayoutManager(new LinearLayoutManager(getContext()));
        sidebarAdapter = new SidebarAdapter();
        binding.rvBodyParts.setAdapter(sidebarAdapter);

        binding.rvExercises.setLayoutManager(new GridLayoutManager(getContext(), 2));
        exerciseAdapter = new ExerciseCardAdapter();
        binding.rvExercises.setAdapter(exerciseAdapter);

        // Inflate and add empty state view below the exercises RecyclerView
        emptyStateView = LayoutInflater.from(requireContext())
                .inflate(R.layout.empty_state_exercise_library, binding.getRoot(), false);
        emptyStateView.setVisibility(View.GONE);
        // Add to parent container (LinearLayout that holds rv_exercises)
        android.widget.LinearLayout parentContainer = (android.widget.LinearLayout) binding.rvExercises.getParent();
        if (parentContainer != null) {
            parentContainer.addView(emptyStateView);
        }
    }

    private void setupFilters() {
        binding.chipGroupEquipment.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedEquipmentId = R.id.chip_eq_all;
                binding.chipEqAll.setChecked(true);
            } else {
                selectedEquipmentId = checkedIds.get(0);
            }
            filterExercises();
        });
    }

    private void loadData() {
        new Thread(() -> {
            List<ExerciseLibrary> dbList = repository.getAllExercisesSync();
            final List<ExerciseLibrary> finalDbList = dbList != null ? dbList : new ArrayList<>();

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    allExercises.clear();
                    allExercises.addAll(finalDbList);
                    initSidebarItems();
                    filterExercises();
                });
            }
        }).start();
    }

    /**
     * 初始化左侧侧边栏：顶部加「全部」项，默认选中全部展示所有动作
     */
    private void initSidebarItems() {
        sidebarItems.clear();

        // 顶部：「全部」特殊项（isHeader=true，parentName="全部"，始终不展开子项）
        sidebarItems.add(new SidebarItem(true, "全部", "全部", false));

        String[] displayOrder = {"胸", "背", "腿", "肩", "臂", "腹", "全身", "拉伸"};
        for (String disp : displayOrder) {
            String dbName = DISPLAY_TO_DB_MAP.get(disp);
            boolean isExpanded = dbName.equals(activeParent);
            SidebarItem parentItem = new SidebarItem(true, disp, dbName, isExpanded);
            sidebarItems.add(parentItem);

            if (isExpanded) {
                List<String> subs = BODY_SUB_MAP.get(dbName);
                if (subs != null) {
                    for (String sub : subs) {
                        sidebarItems.add(new SidebarItem(false, sub, dbName, false));
                    }
                }
            }
        }
        sidebarAdapter.notifyDataSetChanged();
    }

    /**
     * 左侧类别折叠展开逻辑控制
     */
    private void handleSidebarClick(SidebarItem clickedItem) {
        if (clickedItem.isHeader) {
            // 点击「全部」特殊项：展示所有动作，折叠所有大类
            if ("全部".equals(clickedItem.parentName)) {
                activeParent = "全部";
                activeChild = "全部";
                for (SidebarItem item : sidebarItems) {
                    if (item.isHeader) item.isExpanded = false;
                }
                rebuildSidebarStructure();
                filterExercises();
                return;
            }

            // 点击大类Header：如果已展开则收缩，如果未展开则展开
            if (clickedItem.isExpanded) {
                clickedItem.isExpanded = false;
                activeParent = clickedItem.parentName;
                activeChild = "全部";
            } else {
                for (SidebarItem item : sidebarItems) {
                    if (item.isHeader) item.isExpanded = false;
                }
                clickedItem.isExpanded = true;
                activeParent = clickedItem.parentName;
                activeChild = "全部"; // 默认选中该部位全部
            }
            rebuildSidebarStructure();
        } else {
            // 点击了具体的子部位Child
            activeParent = clickedItem.parentName;
            activeChild = clickedItem.name;
            sidebarAdapter.notifyDataSetChanged();
        }
        filterExercises();
    }

    private void rebuildSidebarStructure() {
        List<SidebarItem> newStructure = new ArrayList<>();

        // 顶部「全部」项
        newStructure.add(new SidebarItem(true, "全部", "全部", false));

        String[] displayOrder = {"胸", "背", "腿", "肩", "臂", "腹", "全身", "拉伸"};

        Map<String, Boolean> states = new HashMap<>();
        for (SidebarItem item : sidebarItems) {
            if (item.isHeader) {
                states.put(item.parentName, item.isExpanded);
            }
        }

        for (String disp : displayOrder) {
            String dbName = DISPLAY_TO_DB_MAP.get(disp);
            Boolean isExpanded = states.get(dbName);
            if (isExpanded == null) isExpanded = false;

            SidebarItem parentItem = new SidebarItem(true, disp, dbName, isExpanded);
            newStructure.add(parentItem);

            if (isExpanded) {
                List<String> subs = BODY_SUB_MAP.get(dbName);
                if (subs != null) {
                    for (String sub : subs) {
                        newStructure.add(new SidebarItem(false, sub, dbName, false));
                    }
                }
            }
        }

        sidebarItems.clear();
        sidebarItems.addAll(newStructure);
        sidebarAdapter.notifyDataSetChanged();
    }

    private void filterExercises() {
        List<ExerciseLibrary> filtered = new ArrayList<>();
        String keyword = binding.etSearch.getText().toString().trim().toLowerCase();

        for (ExerciseLibrary ex : allExercises) {
            // 1. 模糊搜索过滤
            if (!keyword.isEmpty()) {
                String name = ex.getName() != null ? ex.getName().toLowerCase() : "";
                if (!name.contains(keyword)) {
                    continue;
                }
            }

            // 2. 左侧部位大分类 & 细化子部位过滤
            // activeParent = "全部" 时不过滤部位，展示所有动作
            if (!"全部".equals(activeParent)) {
                String exBodyPart = ex.getBodyPart() != null ? ex.getBodyPart() : "";
                if (!exBodyPart.equals(activeParent)) {
                    continue;
                }
                if (!"全部".equals(activeChild)) {
                    if (!isExerciseInSubCategory(ex, activeParent, activeChild)) {
                        continue;
                    }
                }
            }

            // 3. 顶部器械完整过滤
            if (selectedEquipmentId != R.id.chip_eq_all) {
                String eq = ex.getEquipment() != null ? ex.getEquipment() : "";
                if (selectedEquipmentId == R.id.chip_eq_barbell) {
                    if (!eq.contains("杠") && !eq.equals("杠铃")) continue;
                } else if (selectedEquipmentId == R.id.chip_eq_dumbbell) {
                    if (!eq.contains("哑") && !eq.equals("哑铃")) continue;
                } else if (selectedEquipmentId == R.id.chip_eq_kettlebell) {
                    if (!eq.contains("壶") && !eq.equals("壶铃")) continue;
                } else if (selectedEquipmentId == R.id.chip_eq_bodyweight) {
                    if (!eq.contains("自重") && !eq.contains("徒手") && !eq.equals("无") && !eq.isEmpty()) continue;
                } else if (selectedEquipmentId == R.id.chip_eq_cable) {
                    // 拉力器筛选分支
                    if (!eq.contains("龙门架") && !eq.contains("拉力器") && !eq.contains("绳索")) continue;
                } else if (selectedEquipmentId == R.id.chip_eq_machine) {
                    // 固定器械筛选分支
                    if (!eq.contains("器械") && !eq.contains("固定器械") && !eq.contains("蝴蝶机")) continue;
                } else if (selectedEquipmentId == R.id.chip_eq_other) {
                    // 其他排除上述所有已选器械
                    if (eq.contains("杠") || eq.contains("哑") || eq.contains("壶") || 
                        eq.contains("自重") || eq.contains("徒手") || eq.contains("龙门架") || 
                        eq.contains("拉力器") || eq.contains("绳索") || eq.contains("器械") || 
                        eq.contains("固定器械") || eq.contains("蝴蝶机") || eq.equals("无") || eq.isEmpty()) {
                        continue;
                    }
                }
            }

            filtered.add(ex);
        }

        exerciseAdapter.setExercises(filtered);

        // Toggle empty state visibility
        if (filtered.isEmpty()) {
            exerciseAdapter.notifyDataSetChanged();
            binding.rvExercises.setVisibility(View.GONE);
            if (emptyStateView != null) {
                emptyStateView.setVisibility(View.VISIBLE);
            }
        } else {
            binding.rvExercises.setVisibility(View.VISIBLE);
            if (emptyStateView != null) {
                emptyStateView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 智能分类算法：根据动作名称或要领描述，将动作划分入各个子部位中
     */
    private boolean isExerciseInSubCategory(ExerciseLibrary ex, String parentPart, String subPart) {
        String name = ex.getName() != null ? ex.getName().toLowerCase() : "";
        String desc = ex.getDescription() != null ? ex.getDescription().toLowerCase() : "";

        if ("胸部".equals(parentPart)) {
            if ("上胸".equals(subPart)) {
                return name.contains("上斜") || desc.contains("上胸") || name.contains("下斜俯卧撑") || desc.contains("上部分");
            } else if ("中下胸".equals(subPart)) {
                return !name.contains("上斜") && !desc.contains("上胸");
            }
        }

        if ("背部".equals(parentPart)) {
            if ("背阔肌".equals(subPart)) {
                return name.contains("下拉") || name.contains("引体") || name.contains("划船") || desc.contains("背阔");
            } else if ("上背/斜方肌".equals(subPart)) {
                return name.contains("y字") || name.contains("t字") || name.contains("面拉") || name.contains("提拉") || desc.contains("上背") || desc.contains("斜方") || name.contains("耸肩");
            } else if ("下背部".equals(subPart)) {
                return name.contains("硬拉") || name.contains("超人") || desc.contains("下背") || desc.contains("竖脊");
            }
        }

        if ("腿部".equals(parentPart)) {
            if ("股四头肌".equals(subPart)) {
                return name.contains("深蹲") || name.contains("静蹲") || name.contains("分腿蹲") || name.contains("腿屈伸") || desc.contains("股四");
            } else if ("腘绳肌/臀部".equals(subPart)) {
                return name.contains("臀桥") || name.contains("罗马尼亚") || name.contains("硬拉") || desc.contains("臀部") || desc.contains("腘绳") || name.contains("后摆");
            } else if ("小腿".equals(subPart)) {
                return name.contains("提踵") || name.contains("小腿") || desc.contains("腓肠") || desc.contains("小腿");
            }
        }

        if ("肩部".equals(parentPart)) {
            if ("三角肌前束".equals(subPart)) {
                return name.contains("前平举") || name.contains("推举") || desc.contains("前束");
            } else if ("三角肌中束".equals(subPart)) {
                return name.contains("侧平举") || name.contains("提拉") || desc.contains("中束") || name.contains("阿诺德");
            } else if ("三角肌后束".equals(subPart)) {
                return name.contains("俯身") || name.contains("面拉") || desc.contains("后束");
            }
        }

        if ("手臂".equals(parentPart)) {
            if ("肱二头肌".equals(subPart)) {
                return name.contains("弯举") || desc.contains("二头");
            } else if ("肱三头肌".equals(subPart)) {
                return name.contains("臂屈伸") || name.contains("下压") || name.contains("窄距卧推") || desc.contains("三头");
            } else if ("前臂".equals(subPart)) {
                return name.contains("腕弯举") || name.contains("前臂") || desc.contains("前臂") || desc.contains("抓握");
            }
        }

        if ("腹部".equals(parentPart)) {
            if ("上腹肌".equals(subPart)) {
                return name.contains("卷腹") || name.contains("仰卧起坐") || desc.contains("上腹") || name.contains("平板支撑") || name.contains("悬垂");
            } else if ("下腹肌".equals(subPart)) {
                return name.contains("抬腿") || name.contains("举腿") || desc.contains("下腹");
            } else if ("腹外斜肌".equals(subPart)) {
                return name.contains("转体") || name.contains("斜肌") || name.contains("侧卷腹");
            }
        }

        if ("全身".equals(parentPart)) {
            if ("有氧".equals(subPart)) {
                return name.contains("开合跳") || name.contains("波比跳") ||
                       name.contains("高抬腿") || name.contains("战绳") || name.contains("跳绳") ||
                       name.contains("爬绳") || desc.contains("有氧") || desc.contains("心肺");
            } else if ("综合".equals(subPart)) {
                return name.contains("深蹲推举") || name.contains("农夫") || name.contains("土耳其") ||
                       desc.contains("全身力量") || desc.contains("综合");
            }
        }

        if ("拉伸".equals(parentPart)) {
            if ("上身拉伸".equals(subPart)) {
                return name.contains("肩膀") || name.contains("胸部拉伸") || name.contains("颈部") ||
                       name.contains("手腕") || name.contains("背部拉伸") ||
                       desc.contains("肩部") || desc.contains("含胸") || desc.contains("颈部");
            } else if ("下身拉伸".equals(subPart)) {
                return name.contains("大腿") || name.contains("小腿") || name.contains("臀部拉伸") ||
                       name.contains("髋") || name.contains("蝴蝶") || name.contains("鸽式") ||
                       desc.contains("腘绳") || desc.contains("股四头") || desc.contains("臀大肌") ||
                       desc.contains("腓肠") || desc.contains("髂腰");
            } else if ("脊柱拉伸".equals(subPart)) {
                return name.contains("脊柱") || name.contains("腰部") ||
                       name.contains("猫牛") || name.contains("眼镜蛇") || name.contains("转体拉伸") ||
                       desc.contains("脊柱") || desc.contains("腰背") || desc.contains("腰部");
            } else if ("全身拉伸".equals(subPart)) {
                return name.contains("瑜伽") || name.contains("拜日") || name.contains("下犬") ||
                       name.contains("三角式") || name.contains("婴儿") ||
                       desc.contains("全身柔韧") || desc.contains("全身拉伸") || desc.contains("全身紧张");
            }
        }


        return true;
    }

    private void showAddCustomExerciseDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_custom_exercise, null);
        Spinner spinnerBodyPart = dialogView.findViewById(R.id.spinner_body_part);
        Spinner spinnerEquipment = dialogView.findViewById(R.id.spinner_equipment);
        EditText etName = dialogView.findViewById(R.id.et_exercise_name);
        EditText etSubCategory = dialogView.findViewById(R.id.et_sub_category);
        EditText etDescription = dialogView.findViewById(R.id.et_description);

        List<String> partOptions = Arrays.asList("胸部", "背部", "腿部", "肩部", "手臂", "腹部", "臀部", "全身", "拉伸");
        ArrayAdapter<String> partAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, partOptions);
        partAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBodyPart.setAdapter(partAdapter);

        List<String> eqOptions = Arrays.asList("自重", "杠铃", "哑铃", "壶铃", "拉力器", "固定器械", "其他");
        ArrayAdapter<String> eqAdapter = new ArrayAdapter<>(requireContext(),
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
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();
                                loadData();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupExerciseLibraryGuide() {
        // 动作库页面无需引导气泡
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ────────────────────── 侧边栏列表实体 ──────────────────────
    public static class SidebarItem {
        public boolean isHeader;      // 是否是大类 Header
        public String name;           // 名字（如 胸 或 上胸）
        public String parentName;     // 父部位名（如 胸部）
        public boolean isExpanded;    // 是否展开

        public SidebarItem(boolean isHeader, String name, String parentName, boolean isExpanded) {
            this.isHeader = isHeader;
            this.name = name;
            this.parentName = parentName;
            this.isExpanded = isExpanded;
        }
    }

    // ────────────────────── 侧边栏列表适配器 ──────────────────────
    private class SidebarAdapter extends RecyclerView.Adapter<SidebarAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_body_part, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SidebarItem item = sidebarItems.get(position);
            holder.tvName.setText(item.name);

            if (item.isHeader) {
                holder.tvName.setTextSize(15f);
                holder.tvName.getPaint().setFakeBoldText(true);
                holder.tvName.setPadding(dpToPx(16f), 0, 0, 0);

                // 「全部」项：选中时高亮蓝色，否则正常灰底
                boolean isAllSelected = "全部".equals(item.parentName) && "全部".equals(activeParent);
                // 当前展开的大类 Header 也给一个浅高亮
                boolean isActiveParent = !("全部".equals(item.parentName))
                        && item.parentName.equals(activeParent);

                if (isAllSelected) {
                    holder.indicator.setVisibility(View.VISIBLE);
                    holder.tvName.setTextColor(getResources().getColor(R.color.white));
                    holder.itemView.setBackgroundColor(getResources().getColor(R.color.plan_blue_primary));
                } else if (isActiveParent) {
                    holder.indicator.setVisibility(View.INVISIBLE);
                    holder.tvName.setTextColor(getResources().getColor(R.color.plan_blue_primary));
                    holder.itemView.setBackgroundColor(getResources().getColor(R.color.fitnessdiary_surface_variant));
                } else {
                    holder.indicator.setVisibility(View.INVISIBLE);
                    holder.tvName.setTextColor(getResources().getColor(R.color.text_primary));
                    holder.itemView.setBackgroundColor(getResources().getColor(R.color.fitnessdiary_surface_variant));
                }
            } else {
                // Child (子分类部位) 样式：带内边距缩进
                holder.tvName.setTextSize(13f);
                holder.tvName.getPaint().setFakeBoldText(false);
                holder.tvName.setPadding(dpToPx(28f), 0, 0, 0);

                boolean isSelected = item.parentName.equals(activeParent) && item.name.equals(activeChild);
                holder.indicator.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
                holder.tvName.setTextColor(isSelected ?
                        getResources().getColor(R.color.white) :
                        getResources().getColor(R.color.text_secondary));
                holder.itemView.setBackgroundColor(isSelected ?
                        getResources().getColor(R.color.plan_blue_primary) :
                        getResources().getColor(R.color.white));
            }

            holder.itemView.setOnClickListener(v -> handleSidebarClick(item));
        }

        @Override
        public int getItemCount() {
            return sidebarItems.size();
        }

        private int dpToPx(float dp) {
            float density = getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            View indicator;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_body_part_name);
                indicator = itemView.findViewById(R.id.view_indicator);
            }
        }
    }

    // ────────────────────── 右栏动作卡片适配器 ──────────────────────
    private class ExerciseCardAdapter extends RecyclerView.Adapter<ExerciseCardAdapter.ViewHolder> {

        private final List<ExerciseLibrary> exercises = new ArrayList<>();

        public void setExercises(List<ExerciseLibrary> list) {
            this.exercises.clear();
            if (list != null) {
                this.exercises.addAll(list);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exercise_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ExerciseLibrary ex = exercises.get(position);
            holder.tvName.setText(ex.getName());

            // 1. 默认展示首字占位
            String name = ex.getName();
            if (name != null && name.length() > 0) {
                holder.tvAvatar.setText(name.substring(0, 1));
            } else {
                holder.tvAvatar.setText("练");
            }
            holder.tvAvatar.setVisibility(View.VISIBLE);

            // 2. 动态检测本地 assets 目录下是否存在中文同名的动图文件 (如 "哑铃上斜卧推.gif")
            String targetGifName = name + ".gif";
            boolean hasLocalGif = false;
            try {
                String[] files = holder.itemView.getContext().getAssets().list("gifs");
                if (files != null) {
                    for (String file : files) {
                        if (file.equalsIgnoreCase(targetGifName)) {
                            hasLocalGif = true;
                            break;
                        }
                    }
                }
            } catch (java.io.IOException e) {
                // ignore
            }

            if (hasLocalGif) {
                holder.ivCardGif.setVisibility(View.VISIBLE);
                String localAssetUrl = "file:///android_asset/gifs/" + targetGifName;

                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .asGif()
                        .load(localAssetUrl)
                        .listener(new com.bumptech.glide.request.RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, boolean isFirstResource) {
                                holder.tvAvatar.setVisibility(View.VISIBLE); // 失败时继续展示大字徽标
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(com.bumptech.glide.load.resource.gif.GifDrawable resource, Object model, com.bumptech.glide.request.target.Target<com.bumptech.glide.load.resource.gif.GifDrawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                holder.tvAvatar.setVisibility(View.GONE); // 本地秒开加载成功，隐藏文字
                                return false;
                            }
                        })
                        .into(holder.ivCardGif);
            } else {
                holder.ivCardGif.setVisibility(View.GONE);
                holder.tvAvatar.setVisibility(View.VISIBLE);
            }

            holder.itemView.setOnClickListener(v -> {
                ExerciseDetailBottomSheet detailSheet = ExerciseDetailBottomSheet.newInstance(ex);
                detailSheet.show(getParentFragmentManager(), "ExerciseDetailBottomSheet");
            });
        }

        @Override
        public int getItemCount() {
            return exercises.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvAvatar;
            android.widget.ImageView ivCardGif;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_exercise_name);
                tvAvatar = itemView.findViewById(R.id.tv_exercise_avatar);
                ivCardGif = itemView.findViewById(R.id.iv_exercise_card_gif);
            }
        }
    }
}
