# 应用修复与功能完善实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复5个应用问题：臀围指标缺失、版本号不匹配、单位/主题设置不生效、常用食谱功能缺失、提醒系统不完善

**Architecture:** 分三阶段实施。Phase 1 三个独立 Bug 修复可并行；Phase 2 提醒系统重设计依赖 Settings 基础设施；Phase 3 常用食谱为独立新建功能。数据库从 v26 迁移到 v27。

**Tech Stack:** Java 17, Room 2.6.1, ViewBinding, Material Design 3, SharedPreferences, AlarmManager

**Base:** 当前数据库版本 26，`versionName "2.3.1"`

## Global Constraints

- 所有 import 必须补全，显式类型声明优先于 `var`
- UI 极简大留白，优先 `MaterialCardView`、`FloatingActionButton`
- 数据库始终存储原始单位（kg / kcal），仅在显示层做转换
- 预设提醒不可被用户删除（`isPreset = true`）
- 新 Entity 和扩展 Entity 统一通过 MIGRATION_26_27 迁移
- 提交信息不含 `Co-Authored-By` trailer

---

### Task 1: 臀围指标补充（Phase 1 — Bug 修复）

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/BodyDataDetailBottomSheetFragment.java`

**Interfaces:**
- Consumes: 无
- Produces: 无（仅内部修改）

- [ ] **Step 1: 在 indicators 数组中添加"臀围"**

打开 `BodyDataDetailBottomSheetFragment.java`，在第 385 行的 `indicators` 数组中，在 `"腰围"` 之后插入 `"臀围"`：

```java
// 修改前 (line 385):
private final String[] indicators = {"体重", "体脂率", "腰围", "胸围", "大腿围", "小腿围", "臂围", "肩宽", "脖围"};

// 修改后:
private final String[] indicators = {"体重", "体脂率", "腰围", "臀围", "胸围", "大腿围", "小腿围", "臂围", "肩宽", "脖围"};
```

- [ ] **Step 2: 验证**

确保 `mapIndicatorToDbType()` 方法（第 637 行）已包含臀围映射（已经存在：`if ("臀围".equals(indicator) || "HIP".equals(indicator)) return "HIP";`），无需修改。

- [ ] **Step 3: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/BodyDataDetailBottomSheetFragment.java
git commit -m "fix: add hip circumference to body data chart indicators"
```

---

### Task 2: 版本号修正（Phase 1 — Bug 修复）

**Files:**
- Modify: `app/src/main/res/layout/fragment_profile.xml`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java`

**Interfaces:**
- Consumes: `BuildConfig.VERSION_NAME`（构建系统自动生成）
- Produces: 无

- [ ] **Step 1: 给版本号 TextView 添加 id**

打开 `fragment_profile.xml`，找到第 880-884 行的版本号 TextView，添加 `android:id="@+id/tv_version"`，并移除硬编码的 `android:text="v2.2"`：

```xml
<!-- 修改前 (line 880-884): -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="v2.2"
    android:textSize="14sp"
    android:textColor="@color/text_hint" />

<!-- 修改后: -->
<TextView
    android:id="@+id/tv_version"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textSize="14sp"
    android:textColor="@color/text_hint" />
```

- [ ] **Step 2: 在 onViewCreated 中动态设置版本号**

打开 `ProfileFragment.java`，找到 `onViewCreated` 方法（或 `initViews` 方法），在合适位置（在 `binding = FragmentProfileBinding.inflate(...)` 之后，约第 80 行附近）添加：

```java
// 在 onViewCreated 中 binding 初始化之后添加:
binding.tvVersion.setText("v" + BuildConfig.VERSION_NAME);
```

- [ ] **Step 3: 修复关于对话框中的版本号**

打开 `ProfileFragment.java`，找到 `showAboutFitnessDiaryDialog()` 方法（第 1559 行），将硬编码的 `v2.2` 替换为动态版本号：

```java
// 修改前 (line 1563):
.setMessage("FitnessDiary 健身日记 v2.2\n\n"

// 修改后:
.setMessage("FitnessDiary 健身日记 v" + BuildConfig.VERSION_NAME + "\n\n"
```

- [ ] **Step 4: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/layout/fragment_profile.xml app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java
git commit -m "fix: read app version dynamically from BuildConfig"
```

---

### Task 3: UnitUtils 工具类（Phase 1 — 基础设施）

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/utils/UnitUtils.java`

**Interfaces:**
- Produces: `UnitUtils.convertWeight(float kg, String targetUnit): float`, `UnitUtils.convertEnergy(float kcal, String targetUnit): float`, `UnitUtils.formatWeight(float kg, String targetUnit): String`, `UnitUtils.formatEnergy(float kcal, String targetUnit): String`, `UnitUtils.getWeightUnitSymbol(String unit): String`, `UnitUtils.getEnergyUnitSymbol(String unit): String`, `UnitUtils.getWeightUnit(Context context): String`, `UnitUtils.getEnergyUnit(Context context): String`

- [ ] **Step 1: 创建 UnitUtils.java**

在 `app/src/main/java/com/cz/fitnessdiary/utils/UnitUtils.java` 新建文件：

```java
package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * 单位换算工具类
 * 数据库存储原始单位（kg / kcal），显示层按用户偏好转换
 */
public class UnitUtils {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_WEIGHT_UNIT = "weight_unit";
    private static final String KEY_ENERGY_UNIT = "energy_unit";
    private static final String DEFAULT_WEIGHT_UNIT = "kg";
    private static final String DEFAULT_ENERGY_UNIT = "kcal";

    // ── 换算常量 ──
    private static final float KG_TO_LBS = 2.20462f;
    private static final float KCAL_TO_KJ = 4.184f;

    // ── 读取用户偏好 ──

    public static String getWeightUnit(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_WEIGHT_UNIT, DEFAULT_WEIGHT_UNIT);
    }

    public static String getEnergyUnit(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ENERGY_UNIT, DEFAULT_ENERGY_UNIT);
    }

    public static void setWeightUnit(Context context, String unit) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_WEIGHT_UNIT, unit).apply();
    }

    public static void setEnergyUnit(Context context, String unit) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_ENERGY_UNIT, unit).apply();
    }

    // ── 数值换算 ──

    public static float convertWeight(float kg, String targetUnit) {
        if ("lbs".equals(targetUnit)) return kg * KG_TO_LBS;
        return kg;
    }

    public static float convertEnergy(float kcal, String targetUnit) {
        if ("kj".equals(targetUnit)) return kcal * KCAL_TO_KJ;
        return kcal;
    }

    // ── 格式化显示 ──

    public static String formatWeight(float kg, String targetUnit) {
        if ("lbs".equals(targetUnit)) {
            return String.format(Locale.getDefault(), "%.1f", kg * KG_TO_LBS);
        }
        return String.format(Locale.getDefault(), "%.1f", kg);
    }

    public static String formatWeight(float kg, Context context) {
        return formatWeight(kg, getWeightUnit(context));
    }

    public static String formatEnergy(float kcal, String targetUnit) {
        if ("kj".equals(targetUnit)) {
            return String.format(Locale.getDefault(), "%.0f", kcal * KCAL_TO_KJ);
        }
        return String.format(Locale.getDefault(), "%.0f", kcal);
    }

    public static String formatEnergy(float kcal, Context context) {
        return formatEnergy(kcal, getEnergyUnit(context));
    }

    // ── 单位符号 ──

    public static String getWeightUnitSymbol(String unit) {
        if ("lbs".equals(unit)) return "lbs";
        return "kg";
    }

    public static String getWeightUnitSymbol(Context context) {
        return getWeightUnitSymbol(getWeightUnit(context));
    }

    public static String getEnergyUnitSymbol(String unit) {
        if ("kj".equals(unit)) return "kJ";
        return "kcal";
    }

    public static String getEnergyUnitSymbol(Context context) {
        return getEnergyUnitSymbol(getEnergyUnit(context));
    }

    public static String getWeightUnitDisplay(String unit) {
        if ("lbs".equals(unit)) return "磅";
        return "千克";
    }

    public static String getEnergyUnitDisplay(String unit) {
        if ("kj".equals(unit)) return "千焦";
        return "千卡";
    }

    // ── 主题设置 ──

    private static final String KEY_THEME_MODE = "theme_mode";
    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_SYSTEM = 2;

    public static int getThemeMode(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public static void setThemeMode(Context context, int mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_THEME_MODE, mode).apply();
    }
}
```

- [ ] **Step 2: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/cz/fitnessdiary/utils/UnitUtils.java
git commit -m "feat: add UnitUtils for weight/energy unit conversion and theme persistence"
```

---

### Task 4: 显示与单位设置修复（Phase 1 — 设置持久化 + 主题生效）

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/MainActivity.java`

**Interfaces:**
- Consumes: `UnitUtils.setWeightUnit()`, `UnitUtils.setEnergyUnit()`, `UnitUtils.setThemeMode()`, `UnitUtils.getThemeMode()`
- Produces: 无

- [ ] **Step 1: 重写 showUnitAndDisplaySettingsDialog() — 持久化设置**

打开 `ProfileFragment.java`，找到 `showUnitAndDisplaySettingsDialog()` 方法（第 325-364 行），替换为：

```java
private void showUnitAndDisplaySettingsDialog() {
    String[] settings = {"重量单位 (当前: " + UnitUtils.getWeightUnitDisplay(UnitUtils.getWeightUnit(requireContext())) + ")",
            "热量单位 (当前: " + UnitUtils.getEnergyUnitDisplay(UnitUtils.getEnergyUnit(requireContext())) + ")",
            "外观显示设置"};
    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("🌓 显示与单位设置")
            .setItems(settings, (dialog, which) -> {
                if (which == 0) {
                    String[] weights = {"kg (千克)", "lbs (磅)"};
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("选择重量单位")
                            .setItems(weights, (d, w) -> {
                                String unit = (w == 0) ? "kg" : "lbs";
                                UnitUtils.setWeightUnit(requireContext(), unit);
                                Toast.makeText(getContext(), "重量单位已切换为 " + UnitUtils.getWeightUnitDisplay(unit), Toast.LENGTH_SHORT).show();
                            })
                            .show();
                } else if (which == 1) {
                    String[] calories = {"kcal (千卡)", "kj (千焦)"};
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("选择热量单位")
                            .setItems(calories, (d, c) -> {
                                String unit = (c == 0) ? "kcal" : "kj";
                                UnitUtils.setEnergyUnit(requireContext(), unit);
                                Toast.makeText(getContext(), "热量单位已切换为 " + UnitUtils.getEnergyUnitDisplay(unit), Toast.LENGTH_SHORT).show();
                            })
                            .show();
                } else {
                    String[] themes = {"浅色模式", "深色模式", "跟随系统"};
                    int currentTheme = UnitUtils.getThemeMode(requireContext());
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("外观显示设置")
                            .setItems(themes, (d, t) -> {
                                UnitUtils.setThemeMode(requireContext(), t);
                                int mode;
                                if (t == 0) {
                                    mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
                                } else if (t == 1) {
                                    mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
                                } else {
                                    mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                                }
                                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode);
                                Toast.makeText(getContext(), "外观主题已设为：" + themes[t], Toast.LENGTH_SHORT).show();
                            })
                            .show();
                }
            })
            .show();
}
```

- [ ] **Step 2: 在 MainActivity.onCreate() 中初始化主题**

打开 `MainActivity.java`，在 `onCreate()` 方法中，在 `super.onCreate()` 之后、`setContentView()` 之前插入主题初始化：

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    // ▼ 新增：在 super.onCreate() 之后、setContentView() 之前初始化主题
    int themeMode = UnitUtils.getThemeMode(this);
    if (themeMode == UnitUtils.THEME_LIGHT) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    } else if (themeMode == UnitUtils.THEME_DARK) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    } else {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    // ▲ 新增结束

    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    // ... 其余代码不变
}
```

注意：需要检查 `super.onCreate(savedInstanceState)` 与 `AppCompatDelegate.setDefaultNightMode()` 的调用顺序。如果当前代码是 `super.onCreate()` 在第一行，则主题初始化代码必须放在 `super.onCreate()` 之前：

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    // 主题必须在 super.onCreate() 之前设置
    int themeMode = UnitUtils.getThemeMode(this);
    if (themeMode == UnitUtils.THEME_LIGHT) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    } else if (themeMode == UnitUtils.THEME_DARK) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    } else {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    super.onCreate(savedInstanceState);
    // ... 其余代码不变
}
```

- [ ] **Step 3: 更新设置行显示当前单位**

找到 `fragment_profile.xml` 中单位设置行右侧的提示文本（第 600 行附近 `android:text="kg / kcal"`），在 `ProfileFragment.onViewCreated()` 中动态更新它。首先给这个 TextView 添加 id：

在 `fragment_profile.xml` 中修改单位提示行：
```xml
<!-- 修改前: -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="kg / kcal"
    ... />
<!-- 修改后: -->
<TextView
    android:id="@+id/tv_unit_summary"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textSize="14sp"
    android:textColor="@color/text_hint" />
```

然后在 `ProfileFragment.onViewCreated()` 中添加：
```java
binding.tvUnitSummary.setText(UnitUtils.getWeightUnitSymbol(requireContext()) + " / " + UnitUtils.getEnergyUnitSymbol(requireContext()));
```

- [ ] **Step 4: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java app/src/main/java/com/cz/fitnessdiary/ui/MainActivity.java app/src/main/res/layout/fragment_profile.xml
git commit -m "fix: persist display/unit settings and apply theme on startup"
```

---

### Task 5: 单位设置在各显示页生效（Phase 1 — 显示层应用）

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/BodyDataDetailBottomSheetFragment.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java`

**Interfaces:**
- Consumes: `UnitUtils.getWeightUnit()`, `UnitUtils.getEnergyUnit()`, `UnitUtils.formatWeight()`, `UnitUtils.formatEnergy()`, `UnitUtils.getWeightUnitSymbol()`, `UnitUtils.getEnergyUnitSymbol()`

- [ ] **Step 1: BodyDataDetailBottomSheetFragment — 体重显示**

打开 `BodyDataDetailBottomSheetFragment.java`，在 `loadBaseUserData()` 方法（约第 186 行）中，找到体重显示行。需要将原始 kg 值按用户单位转换：

```java
// 在 loadBaseUserData() 中，找到设置体重的代码段，修改为:
float weightKg = /* 从数据库读取的原始 kg 值 */;
String weightUnit = UnitUtils.getWeightUnit(getContext());
tvValWeight.setText(UnitUtils.formatWeight(weightKg, weightUnit) + " " + UnitUtils.getWeightUnitSymbol(weightUnit));
```

具体修改点需要根据实际代码上下文调整。在 `BodyDataDetailBottomSheetFragment` 中找到体重显示相关的 TextView 赋值，套用 `UnitUtils.formatWeight()` 和 `UnitUtils.getWeightUnitSymbol()`。

- [ ] **Step 2: CheckInFragment — 体重卡片和热量显示**

打开 `CheckInFragment.java`，搜索体重显示和热量显示的相关代码。找到设置 `tv_weight_value` 或类似 TextView 的位置，使用 `UnitUtils` 进行单位转换：

```java
// 体重显示示例:
float weightKg = /* 从数据库读取 */;
binding.tvWeightValue.setText(UnitUtils.formatWeight(weightKg, requireContext()));
binding.tvWeightUnit.setText(UnitUtils.getWeightUnitSymbol(requireContext()));

// 热量显示示例:
float caloriesKcal = /* 从数据库读取 */;
String energyUnit = UnitUtils.getEnergyUnit(requireContext());
binding.tvCalorieValue.setText(UnitUtils.formatEnergy(caloriesKcal, energyUnit));
binding.tvCalorieUnit.setText(UnitUtils.getEnergyUnitSymbol(energyUnit));
```

- [ ] **Step 3: DietFragment — 能量状态横条热量单位**

打开 `DietFragment.java`，找到能量显示相关代码，应用热量单位转换。同样使用 `UnitUtils.formatEnergy()` 和 `UnitUtils.getEnergyUnitSymbol()`。

- [ ] **Step 4: ProfileFragment — 体重趋势**

打开 `ProfileFragment.java`，找到体重趋势显示代码，应用 `UnitUtils.formatWeight()` 进行转换。

- [ ] **Step 5: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java app/src/main/java/com/cz/fitnessdiary/ui/fragment/BodyDataDetailBottomSheetFragment.java app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java
git commit -m "feat: apply unit preferences to weight and energy displays across pages"
```

---

### Task 6: 数据库 Migration + Entity 扩展（Phase 1 — 基础设施，为 Phase 2/3 铺路）

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/database/entity/ReminderSchedule.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/database/entity/Recipe.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/database/entity/FavoriteFood.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/database/AppDatabase.java`

**Interfaces:**
- Produces: `Recipe` entity, `FavoriteFood` entity, `ReminderSchedule` 新增 `isPreset`/`sortOrder` 字段, `MIGRATION_26_27`

- [ ] **Step 1: 扩展 ReminderSchedule Entity**

打开 `ReminderSchedule.java`，在 `content` 字段之后添加两个新字段：

```java
// 在 content 字段之后（第 34 行后）添加:
@ColumnInfo(name = "is_preset")
private boolean isPreset;

@ColumnInfo(name = "sort_order")
private int sortOrder;
```

更新构造函数（第 37-47 行）加入新字段：

```java
public ReminderSchedule(String moduleType, long targetId, int hour, int minute,
                        String repeatDays, boolean isEnabled, String title, String content,
                        boolean isPreset, int sortOrder) {
    this.moduleType = moduleType;
    this.targetId = targetId;
    this.hour = hour;
    this.minute = minute;
    this.repeatDays = repeatDays;
    this.isEnabled = isEnabled;
    this.title = title;
    this.content = content;
    this.isPreset = isPreset;
    this.sortOrder = sortOrder;
}
```

添加 getter/setter：
```java
public boolean isPreset() { return isPreset; }
public void setPreset(boolean isPreset) { this.isPreset = isPreset; }
public int getSortOrder() { return sortOrder; }
public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
```

- [ ] **Step 2: 创建 Recipe Entity**

创建 `app/src/main/java/com/cz/fitnessdiary/database/entity/Recipe.java`：

```java
package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recipe")
public class Recipe {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "foods_json")
    private String foodsJson;

    @ColumnInfo(name = "total_calories")
    private float totalCalories;

    @ColumnInfo(name = "meal_type")
    private int mealType; // 0=早餐,1=午餐,2=晚餐,3=加餐,-1=通用

    @ColumnInfo(name = "is_favorite")
    private boolean isFavorite;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public Recipe(String name, String foodsJson, float totalCalories, int mealType,
                  boolean isFavorite, long createdAt, long updatedAt) {
        this.name = name;
        this.foodsJson = foodsJson;
        this.totalCalories = totalCalories;
        this.mealType = mealType;
        this.isFavorite = isFavorite;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Getters ──
    public long getId() { return id; }
    public String getName() { return name; }
    public String getFoodsJson() { return foodsJson; }
    public float getTotalCalories() { return totalCalories; }
    public int getMealType() { return mealType; }
    public boolean isFavorite() { return isFavorite; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    // ── Setters ──
    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setFoodsJson(String foodsJson) { this.foodsJson = foodsJson; }
    public void setTotalCalories(float totalCalories) { this.totalCalories = totalCalories; }
    public void setMealType(int mealType) { this.mealType = mealType; }
    public void setIsFavorite(boolean isFavorite) { this.isFavorite = isFavorite; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: 创建 FavoriteFood Entity**

创建 `app/src/main/java/com/cz/fitnessdiary/database/entity/FavoriteFood.java`：

```java
package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorite_food")
public class FavoriteFood {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "food_name")
    private String foodName;

    @ColumnInfo(name = "calories")
    private float calories;

    @ColumnInfo(name = "protein")
    private float protein;

    @ColumnInfo(name = "carbs")
    private float carbs;

    @ColumnInfo(name = "fat")
    private float fat;

    @ColumnInfo(name = "food_library_id")
    private Long foodLibraryId; // nullable

    @ColumnInfo(name = "created_at")
    private long createdAt;

    public FavoriteFood(String foodName, float calories, float protein, float carbs, float fat,
                        Long foodLibraryId, long createdAt) {
        this.foodName = foodName;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.foodLibraryId = foodLibraryId;
        this.createdAt = createdAt;
    }

    // ── Getters ──
    public long getId() { return id; }
    public String getFoodName() { return foodName; }
    public float getCalories() { return calories; }
    public float getProtein() { return protein; }
    public float getCarbs() { return carbs; }
    public float getFat() { return fat; }
    public Long getFoodLibraryId() { return foodLibraryId; }
    public long getCreatedAt() { return createdAt; }

    // ── Setters ──
    public void setId(long id) { this.id = id; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public void setCalories(float calories) { this.calories = calories; }
    public void setProtein(float protein) { this.protein = protein; }
    public void setCarbs(float carbs) { this.carbs = carbs; }
    public void setFat(float fat) { this.fat = fat; }
    public void setFoodLibraryId(Long foodLibraryId) { this.foodLibraryId = foodLibraryId; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: 在 AppDatabase 中注册新 Entity、DAO 和 Migration**

打开 `AppDatabase.java`：

**4a.** 更新 `@Database` 注解（第 62 行），在 `entities` 列表末尾添加 `Recipe.class, FavoriteFood.class`，将 `version` 从 `26` 改为 `27`：

```java
@Database(entities = { User.class, TrainingPlan.class, DailyLog.class, FoodRecord.class,
        FoodLibrary.class, ExerciseLibrary.class, SleepRecord.class, ChatMessageEntity.class,
        ChatSessionEntity.class, WeightRecord.class, WaterRecord.class, MedicationRecord.class, CustomTracker.class,
        CustomRecord.class, ReminderSchedule.class, HabitItem.class,
        HabitRecord.class, BodyMeasurement.class, BowelMovement.class,
        MenstrualCycle.class, StepRecord.class, MoodRecord.class,
        Recipe.class, FavoriteFood.class }, version = 27, exportSchema = true)
```

**4b.** 添加新 DAO 抽象方法（在类体中，与其他 DAO 方法一起）：

```java
public abstract RecipeDao recipeDao();
public abstract FavoriteFoodDao favoriteFoodDao();
```

**4c.** 添加 `MIGRATION_26_27`（在最后一个 migration `MIGRATION_25_26` 之后）：

```java
static final Migration MIGRATION_26_27 = new Migration(26, 27) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        // Recipe 表
        database.execSQL("CREATE TABLE IF NOT EXISTS recipe (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "foods_json TEXT NOT NULL, " +
                "total_calories REAL NOT NULL DEFAULT 0, " +
                "meal_type INTEGER NOT NULL DEFAULT -1, " +
                "is_favorite INTEGER NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)");

        // FavoriteFood 表
        database.execSQL("CREATE TABLE IF NOT EXISTS favorite_food (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "food_name TEXT NOT NULL, " +
                "calories REAL NOT NULL DEFAULT 0, " +
                "protein REAL NOT NULL DEFAULT 0, " +
                "carbs REAL NOT NULL DEFAULT 0, " +
                "fat REAL NOT NULL DEFAULT 0, " +
                "food_library_id INTEGER, " +
                "created_at INTEGER NOT NULL)");

        // ReminderSchedule 扩展
        database.execSQL("ALTER TABLE reminder_schedule ADD COLUMN is_preset INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE reminder_schedule ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
    }
};
```

**4d.** 在 `getInstance()` 的 `.addMigrations()` 链末尾追加 `MIGRATION_26_27`：

```java
// 修改前 (最后一行):
.addMigrations(..., MIGRATION_25_26)

// 修改后:
.addMigrations(..., MIGRATION_25_26, MIGRATION_26_27)
```

- [ ] **Step 5: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/cz/fitnessdiary/database/entity/ReminderSchedule.java app/src/main/java/com/cz/fitnessdiary/database/entity/Recipe.java app/src/main/java/com/cz/fitnessdiary/database/entity/FavoriteFood.java app/src/main/java/com/cz/fitnessdiary/database/AppDatabase.java
git commit -m "feat: add Recipe, FavoriteFood entities and extend ReminderSchedule with migration 26->27"
```

---

### Task 7: 常用食谱 DAO + Repository + ViewModel（Phase 3 — 数据层）

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/database/dao/RecipeDao.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/database/dao/FavoriteFoodDao.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/repository/RecipeRepository.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/repository/FavoriteFoodRepository.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/viewmodel/RecipeViewModel.java`

**Interfaces:**
- Consumes: `Recipe` Entity, `FavoriteFood` Entity, `AppDatabase`
- Produces: `RecipeDao`, `FavoriteFoodDao`, `RecipeRepository`, `FavoriteFoodRepository`, `RecipeViewModel`

- [ ] **Step 1: 创建 RecipeDao.java**

```java
package com.cz.fitnessdiary.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cz.fitnessdiary.database.entity.Recipe;

import java.util.List;

@Dao
public interface RecipeDao {

    @Insert
    long insert(Recipe recipe);

    @Update
    void update(Recipe recipe);

    @Delete
    void delete(Recipe recipe);

    @Query("SELECT * FROM recipe ORDER BY is_favorite DESC, updated_at DESC")
    List<Recipe> getAll();

    @Query("SELECT * FROM recipe WHERE meal_type = :mealType OR meal_type = -1 ORDER BY is_favorite DESC, updated_at DESC")
    List<Recipe> getByMealType(int mealType);

    @Query("SELECT * FROM recipe WHERE is_favorite = 1 ORDER BY updated_at DESC")
    List<Recipe> getFavorites();

    @Query("SELECT * FROM recipe WHERE id = :id")
    Recipe getById(long id);

    @Query("DELETE FROM recipe WHERE id = :id")
    void deleteById(long id);
}
```

- [ ] **Step 2: 创建 FavoriteFoodDao.java**

```java
package com.cz.fitnessdiary.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.FavoriteFood;

import java.util.List;

@Dao
public interface FavoriteFoodDao {

    @Insert
    long insert(FavoriteFood food);

    @Delete
    void delete(FavoriteFood food);

    @Query("SELECT * FROM favorite_food ORDER BY created_at DESC")
    List<FavoriteFood> getAll();

    @Query("SELECT * FROM favorite_food WHERE food_name = :foodName LIMIT 1")
    FavoriteFood getByName(String foodName);

    @Query("SELECT COUNT(*) FROM favorite_food WHERE food_name = :foodName")
    int countByName(String foodName);

    @Query("DELETE FROM favorite_food WHERE id = :id")
    void deleteById(long id);
}
```

- [ ] **Step 3: 创建 RecipeRepository.java**

```java
package com.cz.fitnessdiary.repository;

import android.app.Application;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.RecipeDao;
import com.cz.fitnessdiary.database.entity.Recipe;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecipeRepository {

    private final RecipeDao recipeDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RecipeRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        recipeDao = db.recipeDao();
    }

    public void insert(Recipe recipe, OnOperationCompleteListener listener) {
        executor.execute(() -> {
            long id = recipeDao.insert(recipe);
            if (listener != null) listener.onSuccess(id);
        });
    }

    public void update(Recipe recipe) {
        executor.execute(() -> recipeDao.update(recipe));
    }

    public void delete(Recipe recipe) {
        executor.execute(() -> recipeDao.delete(recipe));
    }

    public void deleteById(long id) {
        executor.execute(() -> recipeDao.deleteById(id));
    }

    public void getAll(OnDataLoadedListener<List<Recipe>> listener) {
        executor.execute(() -> {
            List<Recipe> list = recipeDao.getAll();
            if (listener != null) listener.onLoaded(list);
        });
    }

    public void getById(long id, OnDataLoadedListener<Recipe> listener) {
        executor.execute(() -> {
            Recipe recipe = recipeDao.getById(id);
            if (listener != null) listener.onLoaded(recipe);
        });
    }

    public interface OnOperationCompleteListener {
        void onSuccess(long result);
    }

    public interface OnDataLoadedListener<T> {
        void onLoaded(T data);
    }
}
```

- [ ] **Step 4: 创建 FavoriteFoodRepository.java**

```java
package com.cz.fitnessdiary.repository;

import android.app.Application;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.FavoriteFoodDao;
import com.cz.fitnessdiary.database.entity.FavoriteFood;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavoriteFoodRepository {

    private final FavoriteFoodDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FavoriteFoodRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        dao = db.favoriteFoodDao();
    }

    public void insert(FavoriteFood food) {
        executor.execute(() -> dao.insert(food));
    }

    public void delete(FavoriteFood food) {
        executor.execute(() -> dao.delete(food));
    }

    public void deleteById(long id) {
        executor.execute(() -> dao.deleteById(id));
    }

    public void getAll(OnDataLoadedListener<List<FavoriteFood>> listener) {
        executor.execute(() -> {
            List<FavoriteFood> list = dao.getAll();
            if (listener != null) listener.onLoaded(list);
        });
    }

    public void existsByName(String name, OnDataLoadedListener<Boolean> listener) {
        executor.execute(() -> {
            int count = dao.countByName(name);
            if (listener != null) listener.onLoaded(count > 0);
        });
    }

    public interface OnDataLoadedListener<T> {
        void onLoaded(T data);
    }
}
```

- [ ] **Step 5: 创建 RecipeViewModel.java**

```java
package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.cz.fitnessdiary.database.entity.FavoriteFood;
import com.cz.fitnessdiary.database.entity.Recipe;
import com.cz.fitnessdiary.repository.FavoriteFoodRepository;
import com.cz.fitnessdiary.repository.RecipeRepository;

import java.util.ArrayList;
import java.util.List;

public class RecipeViewModel extends AndroidViewModel {

    private final RecipeRepository recipeRepo;
    private final FavoriteFoodRepository favRepo;

    private List<Recipe> recipeList = new ArrayList<>();
    private List<FavoriteFood> favList = new ArrayList<>();

    public RecipeViewModel(@NonNull Application application) {
        super(application);
        recipeRepo = new RecipeRepository(application);
        favRepo = new FavoriteFoodRepository(application);
    }

    // ── 食谱 ──

    public void loadRecipes(Runnable onDone) {
        recipeRepo.getAll(data -> {
            recipeList = data != null ? data : new ArrayList<>();
            if (onDone != null) onDone.run();
        });
    }

    public List<Recipe> getRecipeList() { return recipeList; }

    public void saveRecipe(Recipe recipe) {
        recipeRepo.insert(recipe, id -> {});
    }

    public void deleteRecipe(Recipe recipe) {
        recipeRepo.delete(recipe);
    }

    public void deleteRecipeById(long id) {
        recipeRepo.deleteById(id);
    }

    // ── 收藏食物 ──

    public void loadFavoriteFoods(Runnable onDone) {
        favRepo.getAll(data -> {
            favList = data != null ? data : new ArrayList<>();
            if (onDone != null) onDone.run();
        });
    }

    public List<FavoriteFood> getFavoriteFoodList() { return favList; }

    public void addFavoriteFood(FavoriteFood food) {
        favRepo.insert(food);
    }

    public void deleteFavoriteFood(FavoriteFood food) {
        favRepo.delete(food);
    }

    public void deleteFavoriteFoodById(long id) {
        favRepo.deleteById(id);
    }

    public void isFoodFavorited(String name, FavoriteFoodRepository.OnDataLoadedListener<Boolean> listener) {
        favRepo.existsByName(name, listener);
    }
}
```

- [ ] **Step 6: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/cz/fitnessdiary/database/dao/RecipeDao.java app/src/main/java/com/cz/fitnessdiary/database/dao/FavoriteFoodDao.java app/src/main/java/com/cz/fitnessdiary/repository/RecipeRepository.java app/src/main/java/com/cz/fitnessdiary/repository/FavoriteFoodRepository.java app/src/main/java/com/cz/fitnessdiary/viewmodel/RecipeViewModel.java
git commit -m "feat: add Recipe and FavoriteFood data layer (DAO, Repository, ViewModel)"
```

---

### Task 8: 常用食谱 UI 布局文件（Phase 3 — 布局资源）

**Files:**
- Create: `app/src/main/res/layout/fragment_recipe_list.xml`
- Create: `app/src/main/res/layout/item_recipe.xml`
- Create: `app/src/main/res/layout/item_favorite_food.xml`
- Create: `app/src/main/res/layout/dialog_edit_recipe.xml`

- [ ] **Step 1: 创建 fragment_recipe_list.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background">

    <!-- 顶部 Tab -->
    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tab_layout"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="#FFFFFF"
        app:tabIndicatorColor="@color/fitnessdiary_primary"
        app:tabSelectedTextColor="@color/fitnessdiary_primary"
        app:tabTextColor="@color/text_secondary"
        app:tabMode="fixed" />

    <!-- RecyclerView 容器 -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_content"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:padding="16dp" />

    <!-- FAB -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_add"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end|bottom"
        android:layout_margin="16dp"
        android:src="@drawable/ic_add"
        app:backgroundTint="@color/fitnessdiary_primary"
        app:tint="#FFFFFF" />
</LinearLayout>
```

- [ ] **Step 2: 创建 item_recipe.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp"
    app:cardCornerRadius="16dp"
    app:cardElevation="1dp"
    app:cardBackgroundColor="#FFFFFF"
    app:strokeWidth="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <TextView
                android:id="@+id/tv_recipe_name"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:textSize="16sp"
                android:textStyle="bold"
                android:textColor="@color/text_primary" />

            <TextView
                android:id="@+id/tv_recipe_calories"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textColor="@color/diet_primary" />
        </LinearLayout>

        <TextView
            android:id="@+id/tv_recipe_food_count"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textSize="12sp"
            android:textColor="@color/text_hint" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:orientation="horizontal"
            android:gravity="end">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_record_recipe"
                style="@style/Widget.Material3.Button.TonalButton"
                android:layout_width="wrap_content"
                android:layout_height="36dp"
                android:text="一键记录"
                android:textSize="12sp"
                app:cornerRadius="12dp" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_delete_recipe"
                style="@style/Widget.Material3.Button.TextButton"
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:minWidth="0dp"
                android:padding="0dp"
                android:text="🗑"
                android:textSize="16sp"
                android:layout_marginStart="4dp" />
        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 3: 创建 item_favorite_food.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    app:cardCornerRadius="12dp"
    app:cardElevation="1dp"
    app:cardBackgroundColor="#FFFFFF"
    app:strokeWidth="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="12dp">

        <TextView
            android:id="@+id/tv_fav_food_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textSize="14sp"
            android:textStyle="bold"
            android:textColor="@color/text_primary" />

        <TextView
            android:id="@+id/tv_fav_food_calories"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="13sp"
            android:textColor="@color/diet_primary"
            android:layout_marginEnd="8dp" />

        <ImageButton
            android:id="@+id/btn_remove_fav"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:src="@drawable/ic_close"
            android:background="?attr/selectableItemBackgroundBorderless"
            app:tint="@color/text_hint"
            android:contentDescription="取消收藏" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 4: 创建 dialog_edit_recipe.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="📝 编辑食谱"
        android:textSize="18sp"
        android:textStyle="bold"
        android:textColor="@color/text_primary"
        android:layout_marginBottom="16dp" />

    <com.google.android.material.textfield.TextInputLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="食谱名称"
        app:boxCornerRadiusBottomEnd="12dp"
        app:boxCornerRadiusBottomStart="12dp"
        app:boxCornerRadiusTopEnd="12dp"
        app:boxCornerRadiusTopStart="12dp"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/et_recipe_name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />
    </com.google.android.material.textfield.TextInputLayout>

    <TextView
        android:id="@+id/tv_total_calories"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="总热量: 0 kcal"
        android:textSize="14sp"
        android:textColor="@color/diet_primary" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="已选食物:"
        android:textSize="13sp"
        android:textColor="@color/text_secondary" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_food_items"
        android:layout_width="match_parent"
        android:layout_height="200dp"
        android:layout_marginTop="8dp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_add_food_item"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="8dp"
        android:text="+ 添加食物"
        app:cornerRadius="12dp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_save_recipe"
        style="@style/Widget.Material3.Button"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="16dp"
        android:text="保存食谱"
        app:cornerRadius="12dp" />
</LinearLayout>
```

- [ ] **Step 5: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add app/src/main/res/layout/fragment_recipe_list.xml app/src/main/res/layout/item_recipe.xml app/src/main/res/layout/item_favorite_food.xml app/src/main/res/layout/dialog_edit_recipe.xml
git commit -m "feat: add recipe list and edit UI layouts"
```

---

### Task 9: 常用食谱 Fragment + Adapter（Phase 3 — UI 层）

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/adapter/RecipeAdapter.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/adapter/FavoriteFoodAdapter.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/RecipeListFragment.java`

**Interfaces:**
- Consumes: `RecipeViewModel`, `Recipe`, `FavoriteFood`
- Produces: `RecipeListFragment` (navigation destination)

- [ ] **Step 1: 创建 RecipeAdapter.java**

```java
package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.Recipe;

import java.util.ArrayList;
import java.util.List;

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.ViewHolder> {

    private List<Recipe> recipes = new ArrayList<>();
    private OnRecipeActionListener listener;

    public interface OnRecipeActionListener {
        void onRecord(Recipe recipe);
        void onDelete(Recipe recipe);
    }

    public RecipeAdapter(OnRecipeActionListener listener) {
        this.listener = listener;
    }

    public void setRecipes(List<Recipe> recipes) {
        this.recipes = recipes != null ? recipes : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recipe, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Recipe recipe = recipes.get(position);
        holder.tvName.setText(recipe.getName());
        holder.tvCalories.setText((int) recipe.getTotalCalories() + " kcal");

        // 从 JSON 解析食物数量
        int foodCount = 0;
        try {
            foodCount = new com.google.gson.Gson()
                    .fromJson(recipe.getFoodsJson(), com.google.gson.JsonArray.class).size();
        } catch (Exception ignored) {}
        holder.tvFoodCount.setText(foodCount + " 道食物");

        holder.btnRecord.setOnClickListener(v -> {
            if (listener != null) listener.onRecord(recipe);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(recipe);
        });
    }

    @Override
    public int getItemCount() { return recipes.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCalories, tvFoodCount;
        com.google.android.material.button.MaterialButton btnRecord, btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_recipe_name);
            tvCalories = itemView.findViewById(R.id.tv_recipe_calories);
            tvFoodCount = itemView.findViewById(R.id.tv_recipe_food_count);
            btnRecord = itemView.findViewById(R.id.btn_record_recipe);
            btnDelete = itemView.findViewById(R.id.btn_delete_recipe);
        }
    }
}
```

- [ ] **Step 2: 创建 FavoriteFoodAdapter.java**

```java
package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FavoriteFood;

import java.util.ArrayList;
import java.util.List;

public class FavoriteFoodAdapter extends RecyclerView.Adapter<FavoriteFoodAdapter.ViewHolder> {

    private List<FavoriteFood> foods = new ArrayList<>();
    private OnFavoriteFoodActionListener listener;

    public interface OnFavoriteFoodActionListener {
        void onRecord(FavoriteFood food);
        void onRemove(FavoriteFood food);
    }

    public FavoriteFoodAdapter(OnFavoriteFoodActionListener listener) {
        this.listener = listener;
    }

    public void setFoods(List<FavoriteFood> foods) {
        this.foods = foods != null ? foods : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_food, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoriteFood food = foods.get(position);
        holder.tvName.setText(food.getFoodName());
        holder.tvCalories.setText((int) food.getCalories() + " kcal");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRecord(food);
        });
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) listener.onRemove(food);
        });
    }

    @Override
    public int getItemCount() { return foods.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCalories;
        android.widget.ImageButton btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_fav_food_name);
            tvCalories = itemView.findViewById(R.id.tv_fav_food_calories);
            btnRemove = itemView.findViewById(R.id.btn_remove_fav);
        }
    }
}
```

- [ ] **Step 3: 创建 RecipeListFragment.java**

```java
package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.FavoriteFood;
import com.cz.fitnessdiary.database.entity.Recipe;
import com.cz.fitnessdiary.databinding.FragmentRecipeListBinding;
import com.cz.fitnessdiary.ui.adapter.FavoriteFoodAdapter;
import com.cz.fitnessdiary.ui.adapter.RecipeAdapter;
import com.cz.fitnessdiary.viewmodel.RecipeViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;

public class RecipeListFragment extends Fragment {

    private FragmentRecipeListBinding binding;
    private RecipeViewModel viewModel;
    private RecipeAdapter recipeAdapter;
    private FavoriteFoodAdapter favAdapter;
    private boolean isRecipeTab = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRecipeListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RecipeViewModel.class);

        setupTabs();
        setupAdapters();
        setupFab();

        loadData();
    }

    private void setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("我的食谱"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("收藏食物"));
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isRecipeTab = (tab.getPosition() == 0);
                updateContentVisibility();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupAdapters() {
        recipeAdapter = new RecipeAdapter(new RecipeAdapter.OnRecipeActionListener() {
            @Override
            public void onRecord(Recipe recipe) {
                showMealPickerForRecipe(recipe);
            }

            @Override
            public void onDelete(Recipe recipe) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除食谱")
                        .setMessage("确定要删除「" + recipe.getName() + "」吗？")
                        .setPositiveButton("删除", (d, w) -> {
                            viewModel.deleteRecipe(recipe);
                            loadData();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        favAdapter = new FavoriteFoodAdapter(new FavoriteFoodAdapter.OnFavoriteFoodActionListener() {
            @Override
            public void onRecord(FavoriteFood food) {
                showMealPickerForFavFood(food);
            }

            @Override
            public void onRemove(FavoriteFood food) {
                viewModel.deleteFavoriteFood(food);
                loadData();
                Toast.makeText(getContext(), "已取消收藏", Toast.LENGTH_SHORT).show();
            }
        });

        binding.rvContent.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupFab() {
        binding.fabAdd.setOnClickListener(v -> {
            if (isRecipeTab) {
                showEditRecipeDialog(null);
            } else {
                showAddFavFoodDialog();
            }
        });
    }

    private void loadData() {
        if (isRecipeTab) {
            viewModel.loadRecipes(() -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        recipeAdapter.setRecipes(viewModel.getRecipeList());
                    });
                }
            });
        } else {
            viewModel.loadFavoriteFoods(() -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        favAdapter.setFoods(viewModel.getFavoriteFoodList());
                    });
                }
            });
        }
    }

    private void updateContentVisibility() {
        if (isRecipeTab) {
            binding.rvContent.setAdapter(recipeAdapter);
        } else {
            binding.rvContent.setAdapter(favAdapter);
        }
        loadData();
    }

    // ── 一键记录：选择餐段 ──

    private void showMealPickerForRecipe(Recipe recipe) {
        String[] meals = {"☀️ 早餐", "🌞 午餐", "🌙 晚餐", "🍪 加餐"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("记录「" + recipe.getName() + "」到...")
                .setItems(meals, (dialog, which) -> {
                    // 批量写入 FoodRecord — 通过 DietViewModel 或直接操作 FoodRecordRepository
                    recordRecipeToMeal(recipe, which);
                    // 更新最后使用时间
                    recipe.setUpdatedAt(System.currentTimeMillis());
                    viewModel.saveRecipe(recipe);
                    Toast.makeText(getContext(), "✅ 已记录 " + recipe.getName(), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showMealPickerForFavFood(FavoriteFood food) {
        String[] meals = {"☀️ 早餐", "🌞 午餐", "🌙 晚餐", "🍪 加餐"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("记录「" + food.getFoodName() + "」到...")
                .setItems(meals, (dialog, which) -> {
                    recordFoodToMeal(food, which);
                    Toast.makeText(getContext(), "✅ 已添加 1 份 " + food.getFoodName(), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void recordRecipeToMeal(Recipe recipe, int mealType) {
        // 解析 foodsJson，逐条写入 FoodRecord
        // 通过新的 FoodRecordRepository 实例操作
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonArray foods = gson.fromJson(recipe.getFoodsJson(), com.google.gson.JsonArray.class);
        com.cz.fitnessdiary.repository.FoodRecordRepository foodRepo =
                new com.cz.fitnessdiary.repository.FoodRecordRepository(requireActivity().getApplication());

        for (int i = 0; i < foods.size(); i++) {
            com.google.gson.JsonObject item = foods.get(i).getAsJsonObject();
            com.cz.fitnessdiary.database.entity.FoodRecord record =
                    new com.cz.fitnessdiary.database.entity.FoodRecord();
            record.setFoodName(item.get("foodName").getAsString());
            record.setCalories(item.get("calories").getAsFloat());
            record.setProtein(item.get("protein").getAsFloat());
            record.setCarbs(item.get("carbs").getAsFloat());
            record.setFat(item.has("fat") ? item.get("fat").getAsFloat() : 0f);
            record.setRecordDate(System.currentTimeMillis());
            record.setMealType(mealType);
            record.setServings(item.has("servings") ? item.get("servings").getAsFloat() : 1.0f);
            record.setServingUnit(item.has("servingUnit") ? item.get("servingUnit").getAsString() : "份");
            foodRepo.insert(record);
        }
    }

    private void recordFoodToMeal(FavoriteFood food, int mealType) {
        com.cz.fitnessdiary.repository.FoodRecordRepository foodRepo =
                new com.cz.fitnessdiary.repository.FoodRecordRepository(requireActivity().getApplication());
        com.cz.fitnessdiary.database.entity.FoodRecord record =
                new com.cz.fitnessdiary.database.entity.FoodRecord();
        record.setFoodName(food.getFoodName());
        record.setCalories(food.getCalories());
        record.setProtein(food.getProtein());
        record.setCarbs(food.getCarbs());
        record.setFat(food.getFat());
        record.setRecordDate(System.currentTimeMillis());
        record.setMealType(mealType);
        record.setServings(1.0f);
        record.setServingUnit("份");
        foodRepo.insert(record);
    }

    // ── 食谱编辑弹窗（简化版，后续可扩展为完整 BottomSheet） ──

    private void showEditRecipeDialog(@Nullable Recipe existingRecipe) {
        // 简化实现：弹出输入框先让用户输入名称，后续可通过底部弹窗完整编辑
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_edit_recipe, null, false);
        // ... 绑定编辑逻辑（详见后续 Task 扩展）
    }

    private void showAddFavFoodDialog() {
        // 简化实现：弹出搜索框从食物库选择
        Toast.makeText(getContext(), "请从饮食页面长按食物进行收藏", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
```

- [ ] **Step 4: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/adapter/RecipeAdapter.java app/src/main/java/com/cz/fitnessdiary/ui/adapter/FavoriteFoodAdapter.java app/src/main/java/com/cz/fitnessdiary/ui/fragment/RecipeListFragment.java
git commit -m "feat: add RecipeListFragment with dual-tab recipe and favorite food views"
```

---

### Task 10: 导航 + Profile/Diet 入口激活（Phase 3 — 集成）

**Files:**
- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java`
- Modify: `app/src/main/res/layout/fragment_profile.xml`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java`
- Modify: `app/src/main/res/layout/fragment_diet.xml`

- [ ] **Step 1: 在 nav_graph.xml 中添加 recipeListFragment**

在 `nav_graph.xml` 中添加新的 fragment destination：

```xml
<fragment
    android:id="@+id/recipeListFragment"
    android:name="com.cz.fitnessdiary.ui.fragment.RecipeListFragment"
    android:label="我的常用食谱" />
```

在 `ProfileFragment` 的 `<action>` 中添加导航动作：

```xml
<action
    android:id="@+id/action_profileFragment_to_recipeListFragment"
    app:destination="@id/recipeListFragment" />
```

- [ ] **Step 2: 激活 ProfileFragment 中的入口**

在 `ProfileFragment.java` 中，修改 `btnMyRecipes` 点击处理（第 727-729 行）：

```java
// 修改前:
binding.btnMyRecipes.setOnClickListener(v -> {
    Toast.makeText(getContext(), "🍳 常用食谱功能正在规划中，敬请期待！", Toast.LENGTH_SHORT).show();
});

// 修改后:
binding.btnMyRecipes.setOnClickListener(v -> {
    Navigation.findNavController(v).navigate(R.id.action_profileFragment_to_recipeListFragment);
});
```

在 `fragment_profile.xml` 中，移除 `btn_my_recipes` 的 `android:alpha="0.5"` 置灰：

```xml
<!-- 修改前 (line 326): -->
<LinearLayout
    android:id="@+id/btn_my_recipes"
    ...
    android:alpha="0.5">

<!-- 修改后: -->
<LinearLayout
    android:id="@+id/btn_my_recipes"
    ...
    >
```

- [ ] **Step 3: DietFragment — 添加收藏食物快捷区**

在 `DietFragment.java` 中，在 `getFrequentFoods()` 观察者代码之后，添加收藏食物加载逻辑：

```java
// 在 onViewCreated 中添加收藏食物观察
com.cz.fitnessdiary.viewmodel.RecipeViewModel recipeVM =
        new ViewModelProvider(this).get(com.cz.fitnessdiary.viewmodel.RecipeViewModel.class);

recipeVM.loadFavoriteFoods(() -> {
    if (getActivity() != null) {
        getActivity().runOnUiThread(() -> {
            binding.cgFavoriteFoods.removeAllViews();
            java.util.List<com.cz.fitnessdiary.database.entity.FavoriteFood> favs = recipeVM.getFavoriteFoodList();
            if (favs != null && !favs.isEmpty()) {
                for (com.cz.fitnessdiary.database.entity.FavoriteFood food : favs) {
                    com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                    chip.setText("⭐ " + food.getFoodName());
                    chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFFFF3E0));
                    chip.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.diet_primary));
                    chip.setCloseIconVisible(false);
                    chip.setOnClickListener(v -> {
                        String[] meals = {"☀️ 早餐", "🌞 午餐", "🌙 晚餐", "🍪 加餐"};
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("记录「" + food.getFoodName() + "」到...")
                                .setItems(meals, (dialog, which) -> {
                                    viewModel.addFoodRecordSmart(food.getFoodName(), 1.0f, which);
                                    Toast.makeText(requireContext(), "✅ 已添加 1 份 " + food.getFoodName(), Toast.LENGTH_SHORT).show();
                                })
                                .show();
                    });
                    binding.cgFavoriteFoods.addView(chip);
                }
            }
        });
    }
});
```

在 `fragment_diet.xml` 中，在常用食物 ChipGroup 之后添加收藏食物区域：

```xml
<!-- 在 cg_frequent_foods 之后添加: -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="⭐ 收藏食物"
    android:textSize="13sp"
    android:textColor="@color/text_secondary"
    android:layout_marginTop="12dp" />

<com.google.android.material.chip.ChipGroup
    android:id="@+id/cg_favorite_foods"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:singleLine="false" />
```

- [ ] **Step 4: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/navigation/nav_graph.xml app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java app/src/main/res/layout/fragment_profile.xml app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java app/src/main/res/layout/fragment_diet.xml
git commit -m "feat: activate recipe entry in Profile and add favorite food chips to Diet page"
```

---

### Task 11: 提醒预设数据加载器（Phase 2 — 基础设施）

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/database/ReminderPresetDataLoader.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/MainActivity.java`

**Interfaces:**
- Consumes: `ReminderScheduleDao`, `AppDatabase`
- Produces: 预设提醒数据的首次插入

- [ ] **Step 1: 创建 ReminderPresetDataLoader.java**

```java
package com.cz.fitnessdiary.database;

import android.content.Context;

import com.cz.fitnessdiary.database.dao.ReminderScheduleDao;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 首次启动时插入预设提醒模板到数据库
 * 预设模板 isPreset=true，用户不可删除
 */
public class ReminderPresetDataLoader {

    private static final String PREFS_NAME = "reminder_presets";
    private static final String KEY_PRESETS_LOADED = "presets_loaded_v2";

    private static final String[][] PRESETS = {
            // {title, moduleType, hour, minute, repeatDays, sortOrder}
            {"☀️ 早晨健康概要", "morning_summary", "8", "0", "0,1,2,3,4,5,6", "0"},
            {"🌙 晚间记录提醒", "evening_reminder", "20", "0", "0,1,2,3,4,5,6", "1"},
            {"💧 饮水提醒", "water", "10", "0", "0,1,2,3,4,5,6", "2"},
            {"💊 服药打卡提醒", "medication", "9", "0", "0,1,2,3,4,5,6", "3"},
            {"🏃 训练提醒", "training", "19", "0", "0,1,2,3,4,5,6", "4"},
            {"📊 健康周报", "weekly_report", "9", "0", "1", "5"},
    };

    public static void loadIfNeeded(Context context) {
        if (context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PRESETS_LOADED, false)) {
            return; // 已加载
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
            ReminderScheduleDao dao = db.reminderScheduleDao();

            // 检查是否已有预设
            int presetCount = dao.countByPreset(true);
            if (presetCount > 0) {
                // 已有预设，标记完成
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_PRESETS_LOADED, true).apply();
                return;
            }

            for (String[] p : PRESETS) {
                ReminderSchedule schedule = new ReminderSchedule(
                        p[1],                      // moduleType
                        0,                         // targetId
                        Integer.parseInt(p[2]),     // hour
                        Integer.parseInt(p[3]),     // minute
                        p[4],                       // repeatDays
                        true,                       // isEnabled
                        p[0],                       // title
                        "",                         // content
                        true,                       // isPreset
                        Integer.parseInt(p[5])      // sortOrder
                );
                dao.insert(schedule);
            }

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_PRESETS_LOADED, true).apply();
        });
        executor.shutdown();
    }
}
```

- [ ] **Step 2: ReminderScheduleDao 添加 countByPreset 方法**

打开 `ReminderScheduleDao.java`，添加：

```java
@Query("SELECT COUNT(*) FROM reminder_schedule WHERE is_preset = :isPreset")
int countByPreset(boolean isPreset);
```

- [ ] **Step 3: 在 MainActivity.onCreate() 中调用**

在 `MainActivity.java` 的 `onCreate()` 中，在 Executors 线程块内添加预设加载：

```java
Executors.newSingleThreadExecutor().execute(() -> {
    FoodLibraryDataLoader.loadIfNeeded(getApplicationContext());
    ExerciseLibraryDataLoader.loadIfNeeded(getApplicationContext());
    ReminderPresetDataLoader.loadIfNeeded(getApplicationContext()); // 新增
    if (ReminderManager.isSmartReminderEnabled(getApplicationContext())) {
        ReminderManager.restoreSmartReminders(getApplicationContext());
    }
});
```

- [ ] **Step 4: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/cz/fitnessdiary/database/ReminderPresetDataLoader.java app/src/main/java/com/cz/fitnessdiary/database/dao/ReminderScheduleDao.java app/src/main/java/com/cz/fitnessdiary/ui/MainActivity.java
git commit -m "feat: add reminder preset loader with 6 default templates"
```

---

### Task 12: ReminderManager 重构 + 提醒设置 UI（Phase 2 — 核心）

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/utils/ReminderManager.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/receiver/ReminderReceiver.java`
- Create: `app/src/main/res/layout/item_reminder_schedule.xml`
- Create: `app/src/main/res/layout/dialog_edit_reminder.xml`
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/adapter/ReminderScheduleAdapter.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java`
- Replace: `app/src/main/res/layout/dialog_notification_settings.xml`

**Interfaces:**
- Consumes: `ReminderSchedule` (updated), `ReminderScheduleDao`, `ReminderManager`
- Produces: 完整的提醒管理 UI + 调度引擎

- [ ] **Step 1: ReminderManager 添加动态调度方法**

打开 `ReminderManager.java`，添加新方法：

```java
/**
 * 根据 ReminderSchedule 实体调度闹钟
 */
public static void scheduleReminder(Context context, ReminderSchedule schedule) {
    if (schedule == null || !schedule.isEnabled()) return;
    
    int[] repeatDays = parseRepeatDays(schedule.getRepeatDays());
    int requestCodeBase = (int) (schedule.getId() * 100);
    
    for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
        int dayOfWeek = dayOffset; // 0=周日,...,6=周六
        if (repeatDays != null && repeatDays.length > 0) {
            // 检查这一天是否在重复日列表中
            boolean found = false;
            for (int d : repeatDays) {
                if (d == dayOfWeek) { found = true; break; }
            }
            if (!found) continue;
        }
        
        int requestCode = requestCodeBase + dayOffset;
        scheduleExact(context, requestCode, schedule.getHour(), schedule.getMinute(),
                dayOfWeek, buildReminderIntent(context, schedule));
    }
}

/**
 * 取消某个 reminder schedule 的所有闹钟
 */
public static void cancelReminder(Context context, ReminderSchedule schedule) {
    if (schedule == null) return;
    AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    int requestCodeBase = (int) (schedule.getId() * 100);
    for (int i = 0; i < 7; i++) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_RECORD_REMINDER);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCodeBase + i, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }
}

private static Intent buildReminderIntent(Context context, ReminderSchedule schedule) {
    Intent intent = new Intent(context, ReminderReceiver.class);
    intent.setAction(ACTION_RECORD_REMINDER);
    intent.putExtra("schedule_id", schedule.getId());
    intent.putExtra("module_type", schedule.getModuleType());
    intent.putExtra("title", schedule.getTitle());
    intent.putExtra("content", schedule.getContent());
    intent.putExtra("target_id", schedule.getTargetId());
    return intent;
}

private static int[] parseRepeatDays(String repeatDaysStr) {
    if (repeatDaysStr == null || repeatDaysStr.isEmpty()) return new int[0];
    String[] parts = repeatDaysStr.split(",");
    int[] days = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
        try { days[i] = Integer.parseInt(parts[i].trim()); }
        catch (NumberFormatException e) { days[i] = 0; }
    }
    return days;
}

/**
 * 恢复所有启用的 reminder schedule
 */
public static void restoreAllReminders(Context context) {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(() -> {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        List<ReminderSchedule> schedules = db.reminderScheduleDao().getAllEnabled();
        for (ReminderSchedule s : schedules) {
            scheduleReminder(context, s);
        }
    });
    executor.shutdown();
}
```

- [ ] **Step 2: ReminderReceiver 支持自定义提醒**

打开 `ReminderReceiver.java`，修改 `ACTION_RECORD_REMINDER` 的处理逻辑（第 56-69 行），使其支持 `custom` moduleType：

```java
} else if (ACTION_RECORD_REMINDER.equals(action)) {
    long scheduleId = intent.getLongExtra("schedule_id", -1);
    String moduleType = intent.getStringExtra("module_type");
    String title = intent.getStringExtra("title");
    String content = intent.getStringExtra("content");

    String notificationTitle;
    String notificationContent;

    if ("custom".equals(moduleType) || moduleType == null || 
        !(moduleType.equals("morning_summary") || moduleType.equals("evening_reminder") ||
          moduleType.equals("water") || moduleType.equals("medication") ||
          moduleType.equals("training") || moduleType.equals("weekly_report"))) {
        // 自定义提醒：直接使用 title 和 content
        notificationTitle = (title != null && !title.isEmpty()) ? title : "健康提醒";
        notificationContent = (content != null && !content.isEmpty()) ? content : "该记录今天的健康数据啦！";
    } else {
        // 预设类型：调用 SmartReminderHelper 生成智能文案
        SmartReminderHelper helper = new SmartReminderHelper();
        DailyHealthSnapshot snapshot = new HealthAggregationRepository((Application) appContext)
                .getTodaySnapshotSync();
        switch (moduleType) {
            case "morning_summary":
                notificationTitle = "☀️ 早晨健康概要";
                notificationContent = helper.buildMorningContent(snapshot);
                break;
            case "evening_reminder":
                notificationTitle = "🌙 晚间记录提醒";
                notificationContent = helper.buildEveningContent(snapshot);
                break;
            case "water":
                notificationTitle = "💧 饮水提醒";
                notificationContent = "别忘了记录今天的饮水哦，保持充足水分摄入！";
                break;
            case "medication":
                notificationTitle = "💊 服药打卡提醒";
                notificationContent = "按时服药，健康每一天！";
                break;
            case "training":
                notificationTitle = "🏃 训练提醒";
                notificationContent = "今天的训练计划完成了吗？加油！";
                break;
            case "weekly_report":
                notificationTitle = "📊 健康周报";
                notificationContent = helper.buildWeeklyContent();
                break;
            default:
                notificationTitle = title != null ? title : "健康提醒";
                notificationContent = content != null ? content : "该记录今天的健康数据啦！";
        }
    }

    showNotification(appContext, scheduleId, notificationTitle, notificationContent);
    // 重新调度下一次
    restoreAllReminders(appContext);
}
```

- [ ] **Step 3: 创建 item_reminder_schedule.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingVertical="8dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tv_reminder_title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textStyle="bold"
                android:textColor="@color/text_primary" />
        </LinearLayout>

        <com.google.android.material.materialswitch.MaterialSwitch
            android:id="@+id/switch_reminder"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/layout_time_row"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginTop="4dp">

        <TextView
            android:id="@+id/tv_reminder_time"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="⏰ 08:00"
            android:textSize="12sp"
            android:textColor="@color/text_secondary" />

        <ImageButton
            android:id="@+id/btn_delete_reminder"
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:src="@drawable/ic_delete"
            android:background="?attr/selectableItemBackgroundBorderless"
            app:tint="@color/text_hint"
            android:contentDescription="删除"
            android:visibility="gone" />
    </LinearLayout>

    <View
        android:layout_width="match_parent"
        android:layout_height="0.5dp"
        android:background="@color/divider"
        android:layout_marginTop="8dp" />
</LinearLayout>
```

- [ ] **Step 4: 创建 dialog_edit_reminder.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="✏️ 编辑提醒"
        android:textSize="18sp"
        android:textStyle="bold"
        android:textColor="@color/text_primary"
        android:layout_marginBottom="16dp" />

    <com.google.android.material.textfield.TextInputLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="提醒标题"
        app:boxCornerRadiusBottomEnd="12dp"
        app:boxCornerRadiusBottomStart="12dp"
        app:boxCornerRadiusTopEnd="12dp"
        app:boxCornerRadiusTopStart="12dp"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/et_reminder_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />
    </com.google.android.material.textfield.TextInputLayout>

    <com.google.android.material.textfield.TextInputLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:hint="提醒内容（可选）"
        app:boxCornerRadiusBottomEnd="12dp"
        app:boxCornerRadiusBottomStart="12dp"
        app:boxCornerRadiusTopEnd="12dp"
        app:boxCornerRadiusTopStart="12dp"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/et_reminder_content"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />
    </com.google.android.material.textfield.TextInputLayout>

    <LinearLayout
        android:id="@+id/btn_time_picker"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="12dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="?attr/selectableItemBackground"
        android:paddingHorizontal="12dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="⏰ 提醒时间"
            android:textSize="14sp"
            android:textColor="@color/text_primary" />

        <TextView
            android:id="@+id/tv_selected_time"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:gravity="end"
            android:text="08:00"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="@color/fitnessdiary_primary" />
    </LinearLayout>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="重复日"
        android:textSize="13sp"
        android:textColor="@color/text_secondary" />

    <com.google.android.material.chip.ChipGroup
        android:id="@+id/cg_repeat_days"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        app:singleSelection="false">
        <!-- 动态填充：周一~周日 + 每天 -->
    </com.google.android.material.chip.ChipGroup>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_save_reminder"
        style="@style/Widget.Material3.Button"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="16dp"
        android:text="保存提醒"
        app:cornerRadius="12dp" />
</LinearLayout>
```

- [ ] **Step 5: 创建 ReminderScheduleAdapter.java**

```java
package com.cz.fitnessdiary.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cz.fitnessdiary.R;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class ReminderScheduleAdapter extends RecyclerView.Adapter<ReminderScheduleAdapter.ViewHolder> {

    private List<ReminderSchedule> schedules = new ArrayList<>();
    private OnReminderActionListener listener;

    public interface OnReminderActionListener {
        void onToggle(ReminderSchedule schedule, boolean enabled);
        void onTimeClick(ReminderSchedule schedule);
        void onEdit(ReminderSchedule schedule);
        void onDelete(ReminderSchedule schedule);
    }

    public ReminderScheduleAdapter(OnReminderActionListener listener) {
        this.listener = listener;
    }

    public void setSchedules(List<ReminderSchedule> schedules) {
        this.schedules = schedules != null ? schedules : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reminder_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReminderSchedule schedule = schedules.get(position);
        holder.tvTitle.setText(schedule.getTitle());
        holder.tvTime.setText(String.format("⏰ %02d:%02d", schedule.getHour(), schedule.getMinute()));

        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(schedule.isEnabled());
        holder.switchEnabled.setOnCheckedChangeListener((btn, checked) -> {
            schedule.setEnabled(checked);
            if (listener != null) listener.onToggle(schedule, checked);
        });

        holder.tvTime.setOnClickListener(v -> {
            if (listener != null) listener.onTimeClick(schedule);
        });

        // 预设模板隐藏删除按钮
        holder.btnDelete.setVisibility(schedule.isPreset() ? View.GONE : View.VISIBLE);
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(schedule);
        });
    }

    @Override
    public int getItemCount() { return schedules.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime;
        MaterialSwitch switchEnabled;
        ImageButton btnDelete;
        View layoutTimeRow;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_reminder_title);
            tvTime = itemView.findViewById(R.id.tv_reminder_time);
            switchEnabled = itemView.findViewById(R.id.switch_reminder);
            btnDelete = itemView.findViewById(R.id.btn_delete_reminder);
            layoutTimeRow = itemView.findViewById(R.id.layout_time_row);
        }
    }
}
```

- [ ] **Step 6: 重写 dialog_notification_settings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="🔔 通知与提醒管理"
        android:textSize="18sp"
        android:textStyle="bold"
        android:textColor="@color/text_primary"
        android:layout_marginBottom="4dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="开关即时生效，点击时间修改提醒时刻"
        android:textSize="11sp"
        android:textColor="@color/text_hint"
        android:layout_marginBottom="12dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="📋 预设提醒"
        android:textSize="13sp"
        android:textColor="@color/text_secondary"
        android:layout_marginBottom="4dp" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_preset_reminders"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:nestedScrollingEnabled="false" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="✏️ 自定义提醒"
        android:textSize="13sp"
        android:textColor="@color/text_secondary"
        android:layout_marginTop="16dp"
        android:layout_marginBottom="4dp" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_custom_reminders"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:nestedScrollingEnabled="false" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_add_custom_reminder"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="match_parent"
        android:layout_height="40dp"
        android:layout_marginTop="8dp"
        android:text="+ 添加自定义提醒"
        app:cornerRadius="12dp" />
</LinearLayout>
```

- [ ] **Step 7: 重写 ProfileFragment.showNotificationSettingsDialog()**

打开 `ProfileFragment.java`，将 `showNotificationSettingsDialog()` 方法替换为基于 RecyclerView 的实现：

```java
private void showNotificationSettingsDialog() {
    View dialogView = getLayoutInflater().inflate(R.layout.dialog_notification_settings, null);

    androidx.recyclerview.widget.RecyclerView rvPreset = dialogView.findViewById(R.id.rv_preset_reminders);
    androidx.recyclerview.widget.RecyclerView rvCustom = dialogView.findViewById(R.id.rv_custom_reminders);
    com.google.android.material.button.MaterialButton btnAddCustom = dialogView.findViewById(R.id.btn_add_custom_reminder);

    rvPreset.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
    rvCustom.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));

    ReminderScheduleDao dao = AppDatabase.getInstance(requireContext().getApplicationContext()).reminderScheduleDao();

    ReminderScheduleAdapter.OnReminderActionListener listener = new ReminderScheduleAdapter.OnReminderActionListener() {
        @Override
        public void onToggle(ReminderSchedule schedule, boolean enabled) {
            new Thread(() -> {
                schedule.setEnabled(enabled);
                dao.update(schedule);
                if (enabled) {
                    ReminderManager.scheduleReminder(requireContext(), schedule);
                } else {
                    ReminderManager.cancelReminder(requireContext(), schedule);
                }
            }).start();
        }

        @Override
        public void onTimeClick(ReminderSchedule schedule) {
            TimePickerDialog timePicker = new TimePickerDialog(getContext(), (view, hour, minute) -> {
                schedule.setHour(hour);
                schedule.setMinute(minute);
                new Thread(() -> {
                    dao.update(schedule);
                    // 先取消再重新调度
                    ReminderManager.cancelReminder(requireContext(), schedule);
                    if (schedule.isEnabled()) {
                        ReminderManager.scheduleReminder(requireContext(), schedule);
                    }
                }).start();
                // 刷新列表
                loadReminders(dao, rvPreset, rvCustom, listener);
            }, schedule.getHour(), schedule.getMinute(), true);
            timePicker.show();
        }

        @Override
        public void onEdit(ReminderSchedule schedule) {
            showEditReminderDialog(schedule, dao, () -> loadReminders(dao, rvPreset, rvCustom, this));
        }

        @Override
        public void onDelete(ReminderSchedule schedule) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除提醒")
                    .setMessage("确定要删除「" + schedule.getTitle() + "」吗？")
                    .setPositiveButton("删除", (d, w) -> {
                        new Thread(() -> {
                            ReminderManager.cancelReminder(requireContext(), schedule);
                            dao.delete(schedule);
                        }).start();
                        loadReminders(dao, rvPreset, rvCustom, listener);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    };

    ReminderScheduleAdapter presetAdapter = new ReminderScheduleAdapter(listener);
    ReminderScheduleAdapter customAdapter = new ReminderScheduleAdapter(listener);
    rvPreset.setAdapter(presetAdapter);
    rvCustom.setAdapter(customAdapter);

    loadReminders(dao, rvPreset, rvCustom, listener);

    btnAddCustom.setOnClickListener(v -> {
        ReminderSchedule newSchedule = new ReminderSchedule(
                "custom", 0, 8, 0, "0,1,2,3,4,5,6",
                true, "新提醒", "", false, 99);
        showEditReminderDialog(newSchedule, dao, () -> loadReminders(dao, rvPreset, rvCustom, listener));
    });

    new MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .show();
}

private void loadReminders(ReminderScheduleDao dao,
                           androidx.recyclerview.widget.RecyclerView rvPreset,
                           androidx.recyclerview.widget.RecyclerView rvCustom,
                           ReminderScheduleAdapter.OnReminderActionListener listener) {
    new Thread(() -> {
        List<ReminderSchedule> presets = dao.getByPreset(true);
        List<ReminderSchedule> customs = dao.getByPreset(false);

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                ((ReminderScheduleAdapter) rvPreset.getAdapter()).setSchedules(presets);
                ((ReminderScheduleAdapter) rvCustom.getAdapter()).setSchedules(customs);
            });
        }
    }).start();
}

private void showEditReminderDialog(ReminderSchedule schedule, ReminderScheduleDao dao, Runnable onDone) {
    View dialogView = LayoutInflater.from(getContext())
            .inflate(R.layout.dialog_edit_reminder, null, false);
    com.google.android.material.textfield.TextInputEditText etTitle = dialogView.findViewById(R.id.et_reminder_title);
    com.google.android.material.textfield.TextInputEditText etContent = dialogView.findViewById(R.id.et_reminder_content);
    TextView tvTime = dialogView.findViewById(R.id.tv_selected_time);
    android.widget.LinearLayout btnTimePicker = dialogView.findViewById(R.id.btn_time_picker);
    com.google.android.material.chip.ChipGroup cgRepeatDays = dialogView.findViewById(R.id.cg_repeat_days);
    com.google.android.material.button.MaterialButton btnSave = dialogView.findViewById(R.id.btn_save_reminder);

    etTitle.setText(schedule.getTitle());
    etContent.setText(schedule.getContent());
    tvTime.setText(String.format("%02d:%02d", schedule.getHour(), schedule.getMinute()));

    final int[] selectedHour = {schedule.getHour()};
    final int[] selectedMinute = {schedule.getMinute()};

    btnTimePicker.setOnClickListener(v -> {
        TimePickerDialog tpd = new TimePickerDialog(getContext(), (view, h, m) -> {
            selectedHour[0] = h;
            selectedMinute[0] = m;
            tvTime.setText(String.format("%02d:%02d", h, m));
        }, selectedHour[0], selectedMinute[0], true);
        tpd.show();
    });

    // 填充重复日 chips
    String[] dayLabels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日", "每天"};
    for (String label : dayLabels) {
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
        chip.setText(label);
        chip.setCheckable(true);
        cgRepeatDays.addView(chip);
    }

    MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("保存", (d, w) -> {
                schedule.setTitle(etTitle.getText() != null ? etTitle.getText().toString() : "新提醒");
                schedule.setContent(etContent.getText() != null ? etContent.getText().toString() : "");
                schedule.setHour(selectedHour[0]);
                schedule.setMinute(selectedMinute[0]);

                // 收集选中的重复日
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cgRepeatDays.getChildCount(); i++) {
                    com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) cgRepeatDays.getChildAt(i);
                    if (chip.isChecked()) {
                        if (i == 7) { // "每天" 被选中
                            sb.setLength(0);
                            sb.append("0,1,2,3,4,5,6");
                            break;
                        }
                        if (sb.length() > 0) sb.append(",");
                        sb.append(i);
                    }
                }
                if (sb.length() == 0) sb.append("0,1,2,3,4,5,6"); // 默认每天
                schedule.setRepeatDays(sb.toString());

                new Thread(() -> {
                    if (schedule.getId() == 0) {
                        long id = dao.insert(schedule);
                        schedule.setId(id);
                    } else {
                        dao.update(schedule);
                    }
                    ReminderManager.cancelReminder(requireContext(), schedule);
                    if (schedule.isEnabled()) {
                        ReminderManager.scheduleReminder(requireContext(), schedule);
                    }
                }).start();
                if (onDone != null) onDone.run();
            })
            .setNegativeButton("取消", null);

    dialogBuilder.show();
}
```

- [ ] **Step 8: 添加 ReminderScheduleDao 所需的新方法**

打开 `ReminderScheduleDao.java`，确保有以下方法：

```java
@Query("SELECT * FROM reminder_schedule WHERE is_preset = :isPreset ORDER BY sort_order ASC")
List<ReminderSchedule> getByPreset(boolean isPreset);

@Query("SELECT * FROM reminder_schedule WHERE is_enabled = 1")
List<ReminderSchedule> getAllEnabled();

@Query("SELECT COUNT(*) FROM reminder_schedule WHERE is_preset = :isPreset")
int countByPreset(boolean isPreset);

@Update
void update(ReminderSchedule schedule);
```

- [ ] **Step 9: 构建验证**

```bash
gradlew.bat assembleDebug
```
预期: BUILD SUCCESSFUL（可能需要多次迭代修复编译错误）

- [ ] **Step 10: 提交**

```bash
git add app/src/main/java/com/cz/fitnessdiary/utils/ReminderManager.java app/src/main/java/com/cz/fitnessdiary/receiver/ReminderReceiver.java app/src/main/res/layout/item_reminder_schedule.xml app/src/main/res/layout/dialog_edit_reminder.xml app/src/main/java/com/cz/fitnessdiary/ui/adapter/ReminderScheduleAdapter.java app/src/main/java/com/cz/fitnessdiary/ui/fragment/ProfileFragment.java app/src/main/res/layout/dialog_notification_settings.xml app/src/main/java/com/cz/fitnessdiary/database/dao/ReminderScheduleDao.java
git commit -m "feat: redesign reminder system with preset templates and custom scheduling"
```

---

### Task 13: 最终验证与收尾

- [ ] **Step 1: 全量构建**

```bash
gradlew.bat assembleDebug
```

修复所有编译错误，确保 BUILD SUCCESSFUL。

- [ ] **Step 2: 检查 import 补全**

确保所有新建和修改的 Java 文件 import 完整。特别关注：
- `ProfileFragment.java` 需 import `UnitUtils`, `ReminderScheduleDao`, `ReminderSchedule`, `ReminderScheduleAdapter`, `TimePickerDialog`, `ReminderManager`
- `RecipeListFragment.java` 需 import `RecipeViewModel`, `RecipeAdapter`, `FavoriteFoodAdapter`, `FoodRecordRepository`
- `MainActivity.java` 需 import `UnitUtils`, `ReminderPresetDataLoader`
- `ReminderManager.java` 需 import `ReminderSchedule`, `Executors`, `AppDatabase`

- [ ] **Step 3: 检查数据库 Schema 导出**

Room 会在 `app/schemas/` 目录下自动生成 `27.json`。确认文件存在且正确。

- [ ] **Step 4: 安装到设备验证**

```bash
gradlew.bat installDebug
```

验证清单：
1. ✅ 身体数据中心图表包含臀围
2. ✅ 关于页版本号显示 v2.3.1
3. ✅ 设置体重单位为 lbs 后,主页体重显示为 lbs
4. ✅ 设置热量单位为 kj 后,饮食页热量显示为 kJ
5. ✅ 切换主题后重启应用,主题生效
6. ✅ Profile 页「我的常用食谱」不再置灰,点击进入食谱列表
7. ✅ 提醒设置页面显示预设模板 + 自定义提醒,开关和时间修改生效

- [ ] **Step 5: 最终提交**

```bash
git add -A
git commit -m "chore: final cleanup and import fixes for v2.3.2 improvements"
```
