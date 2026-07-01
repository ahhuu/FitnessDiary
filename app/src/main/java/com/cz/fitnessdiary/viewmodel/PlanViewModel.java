package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;

import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.model.PlanGroup;
import com.cz.fitnessdiary.model.TemplateExercise;
import com.cz.fitnessdiary.repository.TrainingPlanRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练计划 ViewModel - 3.0 版本 (Plan 10)
 * 新增计划库概览和分组展示功能
 */
public class PlanViewModel extends AndroidViewModel {
 
    private TrainingPlanRepository repository;
    private LiveData<List<TrainingPlan>> allPlans;
 
    // Plan 10 新增字段与多模板隔离控制
    private MutableLiveData<String> filterMode = new MutableLiveData<>("基础");
    private MutableLiveData<String> activePersonalPlanName = new MutableLiveData<>("默认自定义计划");
    private MediatorLiveData<List<TrainingPlan>> filteredPlans = new MediatorLiveData<>();
    private LiveData<Integer> totalPlanCount;
    private LiveData<String> coveredCategories;
    private LiveData<List<PlanGroup>> groupedPlans;
    private LiveData<List<String>> uniqueCategories;
    private LiveData<List<String>> personalPlanNames;
 
    public PlanViewModel(@NonNull Application application) {
        super(application);
        repository = new TrainingPlanRepository(application);
        allPlans = repository.getAllPlans();
 
        // [v1.2] 从持久化存储读取模式，默认为基础
        android.content.SharedPreferences sp = application.getSharedPreferences("fitness_diary_prefs",
                android.content.Context.MODE_PRIVATE);
        String savedMode = sp.getString("current_plan_mode", "基础");
        filterMode.setValue(savedMode);
 
        String savedActivePlan = sp.getString("active_personal_plan_name", "默认自定义计划");
        activePersonalPlanName.setValue(savedActivePlan);
 
        // [v1.2] 执行迁移：将无前缀的(老版本/备份恢复)分类归入自定义
        repository.migrateLegacyToCustom();
 
        // [v1.2] 检查并注入基础/进阶计划库
        checkAndSeedLibrary();
 
        // 绑定多源 LiveData 驱动过滤计划列表
        filteredPlans.addSource(allPlans, plans -> updateFilteredPlans());
        filteredPlans.addSource(filterMode, mode -> updateFilteredPlans());
        filteredPlans.addSource(activePersonalPlanName, name -> updateFilteredPlans());
 
        // 计算总计划数 (基于过滤后)
        totalPlanCount = Transformations.map(filteredPlans, plans -> {
            return plans == null ? 0 : plans.size();
        });
 
        // 提取覆盖部位 (使用 parts.length - 1 兼容多段 Category)
        coveredCategories = Transformations.map(filteredPlans, plans -> {
            if (plans == null || plans.isEmpty()) {
                return "暂无";
            }
 
            java.util.Set<String> categories = new java.util.LinkedHashSet<>();
            for (TrainingPlan plan : plans) {
                String category = plan.getCategory();
                if (category != null && category.contains("-")) {
                    String[] parts = category.split("-");
                    category = parts[parts.length - 1]; // 提取最后一段部位名
                }
                if (category != null && !category.trim().isEmpty() && !category.equals("未分类")) {
                    categories.add(category.trim());
                }
            }
 
            if (categories.isEmpty()) {
                return "未分类";
            }
 
            StringBuilder sb = new StringBuilder();
            for (String cat : categories) {
                if (sb.length() > 0)
                    sb.append("、");
                sb.append(cat);
            }
            return sb.toString();
        });
 
        // 分组计划列表 (展示时去掉前缀，取最后一段部位)
        groupedPlans = Transformations.map(filteredPlans, plans -> {
            List<PlanGroup> groups = new ArrayList<>();
            if (plans == null || plans.isEmpty()) {
                return groups;
            }
 
            Map<String, PlanGroup> groupMap = new LinkedHashMap<>();
            for (TrainingPlan plan : plans) {
                String fullCategory = plan.getCategory();
                String displayCategory = fullCategory;
                if (fullCategory != null && fullCategory.contains("-")) {
                    String[] parts = fullCategory.split("-");
                    displayCategory = parts[parts.length - 1];
                }
 
                if (displayCategory == null || displayCategory.trim().isEmpty()) {
                    displayCategory = "未分类";
                }
 
                if (!groupMap.containsKey(displayCategory)) {
                    groupMap.put(displayCategory, new PlanGroup(displayCategory));
                }
                groupMap.get(displayCategory).addPlan(plan);
            }
 
            groups.addAll(groupMap.values());
            return groups;
        });
 
        // 提取唯一分类列表 (用于联想，取最后一段部位)
        uniqueCategories = Transformations.map(allPlans, plans -> {
            java.util.Set<String> categories = new java.util.LinkedHashSet<>();
            if (plans != null) {
                for (TrainingPlan plan : plans) {
                    String category = plan.getCategory();
                    if (category != null && category.contains("-")) {
                        String[] parts = category.split("-");
                        category = parts[parts.length - 1];
                    }
                    if (category != null && !category.trim().isEmpty() && !category.equals("无分类")) {
                        categories.add(category.trim());
                    }
                }
            }
            return new ArrayList<>(categories);
        });
 
        // 提取所有的个人计划模板名称列表
        personalPlanNames = Transformations.map(allPlans, plans -> {
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            if (plans != null) {
                for (TrainingPlan plan : plans) {
                    String cat = plan.getCategory();
                    if (cat != null && cat.startsWith("自定义-")) {
                        String[] parts = cat.split("-");
                        if (parts.length >= 3) {
                            names.add(parts[1]);
                        } else {
                            names.add("默认自定义计划");
                        }
                    }
                }
            }
            return new ArrayList<>(names);
        });
    }
 
    private void updateFilteredPlans() {
        List<TrainingPlan> plans = allPlans.getValue();
        String mode = filterMode.getValue();
        String activePersonal = activePersonalPlanName.getValue();
 
        List<TrainingPlan> filtered = new ArrayList<>();
        if (plans != null && mode != null) {
            for (TrainingPlan plan : plans) {
                String cat = plan.getCategory();
                if (cat == null) continue;
 
                if ("基础".equals(mode) || "进阶".equals(mode)) {
                    if (cat.startsWith(mode + "-")) {
                        filtered.add(plan);
                    }
                } else if ("自定义".equals(mode)) {
                    String prefix = "自定义-" + (activePersonal != null ? activePersonal : "默认自定义计划") + "-";
                    if (cat.startsWith(prefix)) {
                        filtered.add(plan);
                    }
                }
            }
        }
        filteredPlans.setValue(filtered);
    }

    public LiveData<List<TrainingPlan>> getAllPlans() {
        return allPlans;
    }

    public LiveData<Integer> getTotalPlanCount() {
        return totalPlanCount;
    }

    public LiveData<String> getCoveredCategories() {
        return coveredCategories;
    }

    public LiveData<List<PlanGroup>> getGroupedPlans() {
        return groupedPlans;
    }

    public void insert(TrainingPlan plan) {
        repository.insert(plan);
    }

    public void update(TrainingPlan plan) {
        repository.update(plan);
    }

    public void delete(TrainingPlan plan) {
        repository.delete(plan);
    }

    // 2.0 方法别名
    public void addPlan(TrainingPlan plan) {
        insert(plan);
    }

    public void deletePlan(TrainingPlan plan) {
        delete(plan);
    }

    // Plan 10 新增方法
    public void updatePlan(TrainingPlan plan) {
        update(plan);
    }

    // Plan 28: 批量更新分类名称
    public void updateCategory(String oldCategory, String newCategory) {
        repository.updateCategory(oldCategory, newCategory);
    }

    public LiveData<String> getActivePersonalPlanName() {
        return activePersonalPlanName;
    }

    public void setActivePersonalPlanName(String name) {
        activePersonalPlanName.setValue(name);
        android.content.SharedPreferences sp = getApplication().getSharedPreferences("fitness_diary_prefs",
                android.content.Context.MODE_PRIVATE);
        sp.edit().putString("active_personal_plan_name", name).apply();
    }

    public LiveData<List<String>> getPersonalPlanNames() {
        return personalPlanNames;
    }

    public void deletePersonalPlan(String templateName) {
        new Thread(() -> {
            List<TrainingPlan> plans = repository.getAllPlansSync();
            if (plans != null) {
                String prefix = "自定义-" + templateName + "-";
                for (TrainingPlan p : plans) {
                    if (p.getCategory() != null && p.getCategory().startsWith(prefix)) {
                        repository.delete(p);
                    }
                }
            }
        }).start();
    }

    public void renamePersonalPlan(String oldName, String newName) {
        if (oldName == null || newName == null || newName.trim().isEmpty()) return;
        new Thread(() -> {
            List<TrainingPlan> plans = repository.getAllPlansSync();
            if (plans != null) {
                String oldPrefix = "自定义-" + oldName + "-";
                String newPrefix = "自定义-" + newName.trim() + "-";
                for (TrainingPlan p : plans) {
                    if (p.getCategory() != null && p.getCategory().startsWith(oldPrefix)) {
                        String suffix = p.getCategory().substring(oldPrefix.length());
                        p.setCategory(newPrefix + suffix);
                        repository.update(p);
                    }
                }
            }
            if (oldName.equals(activePersonalPlanName.getValue())) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    setActivePersonalPlanName(newName.trim());
                });
            }
        }).start();
    }

    /**
     * 将模板导入为个人自定义计划模板，并自动设置为当前执行
     */
    public void importTemplate(List<TemplateExercise> exercises, String customName) {
        if (exercises == null || exercises.isEmpty() || customName == null || customName.trim().isEmpty()) return;
        String prefix = "自定义-" + customName.trim() + "-";
        
        // 覆盖式写入：先清空本模板下旧动作
        repository.deleteByCategoryPrefix(prefix);

        List<TrainingPlan> plans = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (TemplateExercise ex : exercises) {
            String category = ex.getCategory();
            String finalCategory = (category != null && !category.isEmpty())
                    ? prefix + category
                    : prefix + "无分类";

            TrainingPlan plan = new TrainingPlan(
                    ex.getName(),
                    ex.getDescription() != null ? ex.getDescription() : "",
                    now,
                    ex.getSets(),
                    ex.getReps(),
                    null);
            plan.setCategory(finalCategory);
            plan.setScheduledDays(ex.getScheduledDays() != null ? ex.getScheduledDays() : "0");
            plan.setDuration(ex.getDuration());
            plans.add(plan);
        }
        repository.insertAll(plans);
        setActivePersonalPlanName(customName.trim());
        setFilterMode("自定义");
    }

    /**
     * 从训练模板导入动作列表
     * 将模板中的每个动作转换为 TrainingPlan 并批量插入
     * @param exercises 模板动作列表
     * @param templateDifficulty 模板难度 1=初级→基础, 2/3=中高级→进阶
     */
    public void importTemplate(List<TemplateExercise> exercises, int templateDifficulty) {
        if (exercises == null || exercises.isEmpty()) return;
        String prefix = templateDifficulty == 1 ? "基础-" : "进阶-";
        // 先删除同模式下旧计划，再插入新计划（替换逻辑）
        repository.deleteByCategoryPrefix(prefix);

        List<TrainingPlan> plans = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (TemplateExercise ex : exercises) {
            String category = ex.getCategory();
            String finalCategory = (category != null && !category.isEmpty())
                    ? prefix + category
                    : prefix + "无分类";

            TrainingPlan plan = new TrainingPlan(
                    ex.getName(),
                    ex.getDescription() != null ? ex.getDescription() : "",
                    now,
                    ex.getSets(),
                    ex.getReps(),
                    null);
            plan.setCategory(finalCategory);
            plan.setScheduledDays(ex.getScheduledDays() != null ? ex.getScheduledDays() : "0");
            plan.setDuration(ex.getDuration());
            plans.add(plan);
        }
        repository.insertAll(plans);
        // 自动切换到对应模式
        setFilterMode(templateDifficulty == 1 ? "基础" : "进阶");
    }

    public LiveData<List<String>> getUniqueCategories() {
        return uniqueCategories;
    }

    // [v1.2] 模式切换控制
    public LiveData<String> getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(String mode) {
        filterMode.setValue(mode);
        // [v1.2] 持久化选择
        android.content.SharedPreferences sp = getApplication().getSharedPreferences("fitness_diary_prefs",
                android.content.Context.MODE_PRIVATE);
        sp.edit().putString("current_plan_mode", mode).apply();
    }

    /**
     * [v1.2] 检查并注入计划库
     * 分别检查基础和进阶计划，如果缺失则补充
     */
    public void checkAndSeedLibrary() {
        new Thread(() -> {
            List<TrainingPlan> all = repository.getAllPlansSync();
            boolean hasBase = false;
            boolean hasAdvanced = false;

            if (all != null) {
                for (TrainingPlan p : all) {
                    String cat = p.getCategory();
                    if (cat != null) {
                        if (cat.startsWith("基础-"))
                            hasBase = true;
                        if (cat.startsWith("进阶-"))
                            hasAdvanced = true;
                    }
                }
            }

            long now = System.currentTimeMillis();
            List<TrainingPlan> plansToInsert = new ArrayList<>();

            // 1. 注入基础计划 (Bodyweight / Basic)
            if (!hasBase) {
                plansToInsert.add(createPlan("标准俯卧撑", "胸", "基础-", "经典胸肌与三头肌训练", 4, 12, 0, "1,3,5", now));
                plansToInsert.add(createPlan("宽距俯卧撑", "胸", "基础-", "侧重胸大肌外侧", 4, 10, 0, "1,3,5", now));

                plansToInsert.add(createPlan("自重深蹲", "腿", "基础-", "下肢力量根基", 4, 20, 0, "2,4,6", now));
                plansToInsert.add(createPlan("箭步蹲", "腿", "基础-", "单腿稳定性与臀部刺激", 3, 15, 0, "2,4,6", now));

                plansToInsert.add(createPlan("平板支撑", "腹", "基础-", "核心稳定性训练", 3, 1, 60, "1,2,3,4,5,6,7", now));
                plansToInsert.add(createPlan("卷腹", "腹", "基础-", "腹直肌上部孤立", 4, 15, 0, "1,3,5", now));

                plansToInsert.add(createPlan("澳洲引体向上", "背", "基础-", "背部入门动作", 4, 10, 0, "2,4,6", now));
                plansToInsert.add(createPlan("俯身T字伸展", "背", "基础-", "改善圆肩驼背", 3, 15, 0, "2,4,6", now));
            }

            // 2. 注入进阶计划 (Weights / Advanced Skills)
            if (!hasAdvanced) {
                plansToInsert.add(createPlan("支架俯卧撑", "胸", "进阶-", "增加胸肌拉伸幅度", 4, 15, 0, "1,3,5", now));
                plansToInsert.add(createPlan("哑铃卧推", "胸", "进阶-", "增加肌肉维度", 4, 12, 0, "1,3,5", now));
                plansToInsert.add(createPlan("窄距支架臂屈伸", "肱三", "进阶-", "手臂孤立训练", 4, 10, 0, "1,3,5", now));

                plansToInsert.add(createPlan("引体向上", "背", "进阶-", "顶级拉力训练", 4, 8, 0, "2,4,6", now));
                plansToInsert.add(createPlan("杠铃划船", "背", "进阶-", "背部核心训练", 4, 12, 0, "2,4,6", now));
                plansToInsert.add(createPlan("阿诺德推举", "肩", "进阶-", "肩部全方位雕刻", 4, 12, 0, "2,4,6", now));

                plansToInsert.add(createPlan("动态平板支撑", "腹", "进阶-", "动态核心精雕", 3, 1, 60, "1,2,3,4,5,6,7", now));
                plansToInsert.add(createPlan("悬垂举腿", "腹", "进阶-", "极致下腹轰炸", 4, 15, 0, "2,4,6", now));
                plansToInsert.add(createPlan("龙旗", "腹", "进阶-", "李小龙同款核心王牌", 3, 8, 0, "2,4,6", now));
            }

            if (!plansToInsert.isEmpty()) {
                repository.insertAll(plansToInsert);
            }
        }).start();
    }

    // 通用计划创建方法
    private TrainingPlan createPlan(String name, String categoryBodyPart, String categoryPrefix, String desc,
            int sets, int reps, int duration, String days, long now) {
        TrainingPlan plan = new TrainingPlan(name, desc, now, sets, reps, null);
        plan.setCategory(categoryPrefix + categoryBodyPart);
        plan.setDuration(duration);
        plan.setScheduledDays(days);
        return plan;
    }

    /**
     * [已废弃] 请使用 checkAndSeedLibrary
     */
    public void seedAdvancedPlans() {
        checkAndSeedLibrary();
    }

    public int getActionCountForPersonalPlan(String templateName) {
        List<TrainingPlan> plans = allPlans.getValue();
        int count = 0;
        if (plans != null) {
            String prefix = "自定义-" + templateName + "-";
            for (TrainingPlan p : plans) {
                if (p.getCategory() != null && p.getCategory().startsWith(prefix)) {
                    count++;
                }
            }
        }
        return count;
    }
}
