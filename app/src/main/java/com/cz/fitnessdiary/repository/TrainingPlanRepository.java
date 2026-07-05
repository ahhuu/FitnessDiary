package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.TrainingPlanDao;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.ui.widget.HomeWidgetProvider;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 训练计划数据仓库
 * 管理训练计划的增删改查操作
 */
public class TrainingPlanRepository {

    private final TrainingPlanDao trainingPlanDao;
    private final LiveData<List<TrainingPlan>> allPlans;
    private final ExecutorService executorService;
    private final Application application;

    public TrainingPlanRepository(Application application) {
        this.application = application;
        AppDatabase database = AppDatabase.getInstance(application);
        trainingPlanDao = database.trainingPlanDao();
        allPlans = trainingPlanDao.getAllPlans();
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * 获取所有训练计划
     */
    public LiveData<List<TrainingPlan>> getAllPlans() {
        return allPlans;
    }

    /**
     * 获取所有训练计划 (同步)
     */
    public List<TrainingPlan> getAllPlansSync() {
        try {
            return executorService.submit(() -> trainingPlanDao.getAllPlansList()).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 插入新的训练计划
     */
    public void insert(TrainingPlan plan) {
        executorService.execute(() -> {
            trainingPlanDao.insert(plan);
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    public void insertAll(List<TrainingPlan> plans) {
        executorService.execute(() -> {
            trainingPlanDao.insertAll(plans);
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    /**
     * [v1.2] 将现有无前缀的分类迁移到 '自定义-' 前缀
     * 保证用户备份恢复的数据归类为自定义，而不混入基础库
     */
    public void migrateLegacyToCustom() {
        executorService.execute(() -> {
            List<TrainingPlan> plans = trainingPlanDao.getAllPlansList();
            if (plans != null) {
                // 系统原本默认的动作列表
                java.util.Set<String> officialNames = new java.util.HashSet<>(java.util.Arrays.asList(
                    "标准俯卧撑", "宽距俯卧撑", "自重深蹲", "箭步蹲", "平板支撑", "卷腹", "澳洲引体向上", "俯身T字伸展",
                    "支架俯卧撑", "哑铃卧推", "窄距支架臂屈伸", "引体向上", "杠铃划船", "阿诺德推举", "动态平板支撑", "悬垂举腿", "龙旗"
                ));

                // 读取官方动作库中的全部名称
                try {
                    android.content.res.AssetManager am = application.getAssets();
                    java.io.InputStream is = am.open("exercise_library.json");
                    byte[] buffer = new byte[is.available()];
                    is.read(buffer);
                    is.close();
                    String json = new String(buffer, "UTF-8");
                    org.json.JSONObject obj = new org.json.JSONObject(json);
                    org.json.JSONArray arr = obj.getJSONArray("exercises");
                    for (int i = 0; i < arr.length(); i++) {
                        officialNames.add(arr.getJSONObject(i).getString("name"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 读取官方模板里的动作名称
                try {
                    android.content.res.AssetManager am = application.getAssets();
                    java.io.InputStream is = am.open("training_templates.json");
                    byte[] buffer = new byte[is.available()];
                    is.read(buffer);
                    is.close();
                    String json = new String(buffer, "UTF-8");
                    org.json.JSONObject obj = new org.json.JSONObject(json);
                    org.json.JSONArray templates = obj.getJSONArray("templates");
                    for (int i = 0; i < templates.length(); i++) {
                        org.json.JSONObject temp = templates.getJSONObject(i);
                        if (temp.has("versions")) {
                            org.json.JSONObject versions = temp.getJSONObject("versions");
                            java.util.Iterator<String> keys = versions.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                org.json.JSONArray exArr = versions.getJSONArray(key);
                                for (int j = 0; j < exArr.length(); j++) {
                                    officialNames.add(exArr.getJSONObject(j).getString("name"));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                for (TrainingPlan plan : plans) {
                    String cat = plan.getCategory();
                    if (cat == null) continue;

                    // 核心净化条件：如果是内置官方动作名字，且没有自定义图片，绝不是用户的自定义动作，我们直接从自定义中剔除删除，保持清爽。
                    boolean isOfficialNoImage = officialNames.contains(plan.getName())
                            && (plan.getMediaUri() == null || plan.getMediaUri().trim().isEmpty());

                    if (isOfficialNoImage) {
                        // 如果它已经被错标为自定义，或者是无前缀，一律删除，由种子重新注入到基础/进阶中去
                        if (!cat.startsWith("基础-") && !cat.startsWith("进阶-")) {
                            trainingPlanDao.delete(plan);
                        }
                    } else {
                        // 如果是非官方名字或含有自定义图片的动作，这才是真正的用户自定义，我们妥善迁移并保存
                        if (!cat.startsWith("基础-") && !cat.startsWith("进阶-") && !cat.startsWith("自定义-")) {
                            plan.setCategory("自定义-默认自定义计划-" + cat);
                            trainingPlanDao.update(plan);
                        } else if (cat.startsWith("自定义-")) {
                            String[] parts = cat.split("-");
                            if (parts.length < 3) {
                                // 2段格式为损坏数据（第一次fix的bug导致），尝试从计划名推断部位
                                String fallback = parts.length > 1 ? parts[1] : "未分类";
                                String guessed = guessBodyPart(fallback, plan.getName());
                                plan.setCategory("自定义-默认自定义计划-" + guessed);
                                trainingPlanDao.update(plan);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * 从文本中猜测训练部位（用于修复损坏的分类数据）
     */
    private static String guessBodyPart(String fallback, String planName) {
        String[][] bodyParts = {
            {"胸部", "胸"}, {"背部", "背"}, {"腿部", "腿"}, {"肩部", "肩"},
            {"手臂", "臂"}, {"腹部", "腹"}, {"臀部", "臀"}, {"腰部", "腰"},
            {"核心", "核心"}, {"有氧", "有氧"}, {"全身", "全身"}
        };
        if (planName != null) {
            for (String[] bp : bodyParts) {
                if (planName.contains(bp[0]) || planName.contains(bp[1])) return bp[0];
            }
        }
        for (String[] bp : bodyParts) {
            if (fallback.contains(bp[0]) || fallback.contains(bp[1])) return bp[0];
        }
        return fallback;
    }

    /**
     * 更新训练计划
     */
    public void update(TrainingPlan plan) {
        executorService.execute(() -> {
            trainingPlanDao.update(plan);
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    /**
     * 删除训练计划
     */
    public void delete(TrainingPlan plan) {
        executorService.execute(() -> {
            trainingPlanDao.delete(plan);
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    /**
     * 根据 ID 获取训练计划
     */
    public TrainingPlan getPlanById(int planId) {
        try {
            return executorService.submit(() -> trainingPlanDao.getPlanById(planId)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 批量更新分类名称
     */
    public void updateCategory(String oldCategory, String newCategory) {
        executorService.execute(() -> {
            trainingPlanDao.updateCategory(oldCategory, newCategory);
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    /**
     * 按分类前缀删除全部计划（模板导入替换用）
     */
    public void deleteByCategoryPrefix(String prefix) {
        executorService.execute(() -> {
            trainingPlanDao.deleteByCategoryPrefix(prefix);
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    /**
     * 按分类前缀模糊查询
     */
    public List<TrainingPlan> getByCategoryPrefixSync(String prefix) {
        return trainingPlanDao.getByCategoryPrefix(prefix);
    }

    /**
     * 合并计划：将 oldPrefix 下所有动作归类到 newPrefix，用动作名智能识别部位
     */
    public void mergePlans(String oldPrefix, String newPrefix) {
        executorService.execute(() -> {
            // 获取源计划所有动作
            List<TrainingPlan> sourcePlans = trainingPlanDao.getByCategoryPrefix(oldPrefix);
            if (sourcePlans != null) {
                for (TrainingPlan plan : sourcePlans) {
                    String bodyPart = guessBodyPartFromName(plan.getName());
                    String oldCat = plan.getCategory();
                    // 重建分类：新前缀 + 动作名推断的部位
                    String newCat = newPrefix + bodyPart;
                    if (!newCat.equals(oldCat)) {
                        plan.setCategory(newCat);
                        trainingPlanDao.update(plan);
                    }
                }
            }
            // 删除源前缀下的空分类（如果所有动作都被移走了，旧前缀下就没了）
            // 这里不需要额外删除，update已经改变了category
            HomeWidgetProvider.requestRefresh(application);
        });
    }

    /**
     * 从动作名称推断训练部位
     */
    public static String guessBodyPartFromName(String name) {
        if (name == null) return "其他";
        String[][] mapping = {
            {"胸部", "胸"}, {"背部", "背"}, {"腿部", "腿"}, {"肩部", "肩"},
            {"手臂", "臂"}, {"腹部", "腹"}, {"臀部", "臀"}, {"腰部", "腰"},
            {"核心", "核心"}, {"有氧", "有氧"}, {"全身", "全身"},
            {"肱二", "手臂"}, {"肱三", "手臂"}, {"二头", "手臂"}, {"三头", "手臂"},
            {"深蹲", "腿部"}, {"卧推", "胸部"}, {"卷腹", "腹部"}, {"划船", "背部"},
            {"推举", "肩部"}, {"弯举", "手臂"}, {"硬拉", "背部"},
            {"俯卧撑", "胸部"}, {"引体", "背部"}, {"平板支撑", "核心"},
            {"跑步", "有氧"}, {"跳绳", "有氧"}, {"单车", "有氧"},
        };
        for (String[] m : mapping) {
            if (name.contains(m[1])) return m[0];
        }
        return "其他";
    }
}
