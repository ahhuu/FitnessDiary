# 应用修复与功能完善设计方案

**日期:** 2026-07-03
**版本:** v2.3.1 → v2.3.2

---

## 概述

本次迭代包含5个问题修复/功能完善，按复杂度分为两类：

| 类型     | 问题                                     | 复杂度 |
| -------- | ---------------------------------------- | ------ |
| Bug修复  | 1. 身体数据中心图表缺少臀围指标          | 低     |
| Bug修复  | 2. 关于页版本号 v2.2 → 2.3.1            | 低     |
| Bug修复  | 3. 显示与单位设置不生效（含主题切换）    | 中     |
| 功能开发 | 4. 常用食谱功能（食谱组合+食物收藏）     | 高     |
| 功能开发 | 5. 提醒系统重设计（完全自定义+预设模板） | 高     |

---

## 一、臀围指标补充

### 问题

`BodyDataDetailBottomSheetFragment`（身体数据中心底部弹窗）的图表指标列表包含：体重、体脂率、腰围、胸围、大腿围、小腿围、臂围、肩宽、脖围 — **唯独缺少臀围(HIP)**。数据库和主详情页均已支持臀围。

### 方案

1. 在 `BodyDataDetailBottomSheetFragment` 的指标映射列表中添加 `"臀围"` → `"HIP"`
2. 在 `dialog_body_data_detail.xml` 人体剪影布局中补充臀围显示行（腰围与大腿围之间）

### 改动文件

- `BodyDataDetailBottomSheetFragment.java` — 补充指标列表 + 映射方法
- `dialog_body_data_detail.xml` — 补充臀围剪影行

---

## 二、版本号修正

### 问题

`fragment_profile.xml` 和 `ProfileFragment.showAboutFitnessDiaryDialog()` 中硬编码 `"v2.2"`，实际 `build.gradle` 中 `versionName = "2.3.1"`。

### 方案

通过 `BuildConfig.VERSION_NAME` 动态获取版本号。在 `ProfileFragment` 中用代码设置版本号文本，关于对话框中也引用 `BuildConfig.VERSION_NAME`。

### 改动文件

- `fragment_profile.xml` — 保留 TextView id（去掉硬编码 v2.2 文本或保留默认值）
- `ProfileFragment.java` — `onViewCreated` 中 `binding.tvVersion.setText("v" + BuildConfig.VERSION_NAME)`，关于对话框同步修改

---

## 三、显示与单位设置修复

### 问题

- 体重单位(kg/lbs)和热量单位(kcal/kj)选择后只弹 Toast，不持久化、不生效
- 主题切换(Light/Dark/System)也未实际生效

### 方案

#### 3.1 存储

使用 SharedPreferences（文件 `"app_settings"`）：

| Key             | 类型   | 可选值                                                              | 默认值                       |
| --------------- | ------ | ------------------------------------------------------------------- | ---------------------------- |
| `weight_unit` | String | `"kg"`, `"lbs"`                                                 | `"kg"`                     |
| `energy_unit` | String | `"kcal"`, `"kj"`                                                | `"kcal"`                   |
| `theme_mode`  | int    | `MODE_NIGHT_NO`, `MODE_NIGHT_YES`, `MODE_NIGHT_FOLLOW_SYSTEM` | `MODE_NIGHT_FOLLOW_SYSTEM` |

#### 3.2 生效位置

**体重单位**：主页体重卡片、身体数据中心底部弹窗、Profile 页体重趋势、所有 `WeightChartView`

**热量单位**：饮食页能量状态横条、饮食卡片 calorie 显示、健康评分中的热量字段

**主题**：在 `MainActivity.onCreate()` 中读取 `theme_mode` 并调用 `AppCompatDelegate.setDefaultNightMode()`，确保在 `setContentView()` 之前执行。设置变更后立即重建 Activity 生效。

#### 3.3 单位换算

| 换算       | 公式       |
| ---------- | ---------- |
| kg → lbs  | × 2.20462 |
| kcal → kj | × 4.184   |

**原则**：数据库始终存储原始单位（kg / kcal），仅在显示层做转换。

#### 3.4 工具类

新增 `UnitUtils.java`：

```java
public class UnitUtils {
    public static float convertWeight(float kg, String targetUnit) { ... }
    public static float convertEnergy(float kcal, String targetUnit) { ... }
    public static String formatWeight(float kg, String targetUnit) { ... }
    public static String formatEnergy(float kcal, String targetUnit) { ... }
    public static String getWeightUnitDisplay(String unit) { ... }  // "kg"→"千克", "lbs"→"磅"
    public static String getEnergyUnitDisplay(String unit) { ... }  // "kcal"→"千卡", "kj"→"千焦"
    public static String getWeightUnitSymbol(String unit) { ... }   // "kg"→"kg", "lbs"→"lbs"
    public static String getEnergyUnitSymbol(String unit) { ... }   // "kcal"→"kcal", "kj"→"kJ"
}
```

### 改动文件

- `ProfileFragment.java` — 持久化设置逻辑
- `MainActivity.java` — 主题初始化
- `UnitUtils.java` (新增) — 单位换算工具类
- `CheckInFragment.java` — 体重/热量显示转换
- `DietFragment.java` — 能量状态横条热量单位
- `BodyDataDetailBottomSheetFragment.java` — 体重单位
- `ProfileFragment.java` — Profile 页体重趋势
- 所有自定义 `*ChartView.java` — 如有体重/热量单位标签

---

## 四、常用食谱功能

### 功能范围

1. **食谱组合**：保存多道食物为一个食谱，一键批量记录到饮食日志
2. **食物收藏**：收藏常用食物，在饮食页快速选择

### 数据模型

#### 4.1 Recipe Entity（食谱表）

| 字段          | 类型            | 说明                           |
| ------------- | --------------- | ------------------------------ |
| id            | long (PK, auto) | 主键                           |
| name          | String          | 食谱名称                       |
| foodsJson     | String          | 食物列表 JSON 数组             |
| totalCalories | float           | 汇总热量（冗余，方便列表展示） |
| mealType      | int             | 适用餐段 0-3，-1=通用          |
| isFavorite    | boolean         | 收藏标记                       |
| createdAt     | long            | 创建时间                       |
| updatedAt     | long            | 最后使用时间                   |

foodsJson 格式：

```json
[
  {
    "foodName": "全麦面包",
    "calories": 246,
    "protein": 8.5,
    "carbs": 46.0,
    "fat": 3.5,
    "servings": 1.0,
    "servingUnit": "片"
  },
  ...
]
```

#### 4.2 FavoriteFood Entity（收藏食物表）

| 字段          | 类型            | 说明         |
| ------------- | --------------- | ------------ |
| id            | long (PK, auto) | 主键         |
| foodName      | String          | 食物名称     |
| calories      | float           | 每份热量     |
| protein       | float           | 蛋白质       |
| carbs         | float           | 碳水         |
| fat           | float           | 脂肪         |
| foodLibraryId | long (nullable) | 关联食物库ID |
| createdAt     | long            | 创建时间     |

### 新增 DAO

- `RecipeDao.java` — CRUD + 按餐段/收藏筛选查询
- `FavoriteFoodDao.java` — CRUD + 按名称查询（防重复收藏）

### 新增 Repository

- `RecipeRepository.java`
- `FavoriteFoodRepository.java`

### 新增 ViewModel

- `RecipeViewModel.java`

### UI 设计

#### 4.3 食谱列表页 `RecipeListFragment`

- 两个 Tab：「我的食谱」和「收藏食物」
- 食谱 Tab：RecyclerView 列表，每项显示食谱名称、食物数量、总热量、一键记录按钮
- 收藏食物 Tab：GridView 或列表，每项可点击快速添加到饮食
- FAB → 弹出 `AddRecipeBottomSheet` / `AddFavoriteFoodBottomSheet`

#### 4.4 食谱编辑 `EditRecipeBottomSheet`

- 输入食谱名称
- 选择食物列表（从食物库搜索 + 手动输入）
- 每项食物可设置份量
- 显示总热量汇总
- 保存按钮

#### 4.5 食谱一键记录

点击食谱 → 弹出餐段选择（早餐/午餐/晚餐/加餐）→ 批量写入 `FoodRecord` → Toast 提示成功

#### 4.6 饮食页快捷区域

在 `DietFragment` 的常用食物 chips 下方新增一行「⭐ 收藏」标签，展示收藏食物 chips，点击直接记录。

#### 4.7 Profile 入口

`ProfileFragment` 中"我的常用食谱"按钮：

- 移除 alpha=0.5 置灰
- 移除 Toast 占位
- 绑定导航到 `RecipeListFragment`

### 改动文件

| 文件                                       | 动作                                |
| ------------------------------------------ | ----------------------------------- |
| `database/entity/Recipe.java`            | 新建                                |
| `database/entity/FavoriteFood.java`      | 新建                                |
| `database/dao/RecipeDao.java`            | 新建                                |
| `database/dao/FavoriteFoodDao.java`      | 新建                                |
| `repository/RecipeRepository.java`       | 新建                                |
| `repository/FavoriteFoodRepository.java` | 新建                                |
| `viewmodel/RecipeViewModel.java`         | 新建                                |
| `ui/fragment/RecipeListFragment.java`    | 新建                                |
| `ui/adapter/RecipeAdapter.java`          | 新建                                |
| `ui/adapter/FavoriteFoodAdapter.java`    | 新建                                |
| `res/layout/fragment_recipe_list.xml`    | 新建                                |
| `res/layout/item_recipe.xml`             | 新建                                |
| `res/layout/item_favorite_food.xml`      | 新建                                |
| `res/layout/dialog_edit_recipe.xml`      | 新建                                |
| `database/AppDatabase.java`              | 添加 Entity + DAO + Migration       |
| `res/navigation/nav_graph.xml`           | 添加 recipeListFragment destination |
| `ProfileFragment.java`                   | 激活入口                            |
| `DietFragment.java`                      | 添加收藏食物快捷区                  |
| `res/layout/fragment_profile.xml`        | 取消置灰                            |
| `res/layout/fragment_diet.xml`           | 添加收藏区                          |

---

## 五、提醒系统重设计

### 目标

完全自定义提醒系统，支持用户创建任意提醒，同时预置常见模板。

### 数据模型

利用已有 `ReminderSchedule` Entity，扩展以下字段：

| 新增字段  | 类型    | 说明                       |
| --------- | ------- | -------------------------- |
| isPreset  | boolean | 是否预设模板（预设不可删） |
| sortOrder | int     | 排序                       |

已有字段：`id`, `moduleType`, `targetId`, `hour`, `minute`, `repeatDays`, `isEnabled`, `title`, `content`

### 预设模板

首次启动时插入6个预设提醒：

| 模板              | 时间       | 重复 | module_type      |
| ----------------- | ---------- | ---- | ---------------- |
| ☀️ 早晨健康概要 | 08:00      | 每天 | morning_summary  |
| 🌙 晚间记录提醒   | 20:00      | 每天 | evening_reminder |
| 💧 饮水提醒       | 10:00      | 每天 | water            |
| 💊 服药打卡提醒   | 09:00      | 每天 | medication       |
| 🏃 训练提醒       | 19:00      | 每天 | training         |
| 📊 健康周报       | 周一 09:00 | 周一 | weekly_report    |

### 提醒调度

- 开关开启 → `ReminderManager.scheduleReminder(context, schedule)`
- 开关关闭 → `ReminderManager.cancelReminder(context, schedule)`
- 时间变更 → 先取消再重新调度
- `ReminderReceiver` 接到广播后：
  - `moduleType` 匹配预定义类型 → 调用 `SmartReminderHelper` 生成智能文案
  - `moduleType` 为 `custom` → 使用 `title`/`content` 直接显示
- 每次触发后自动重新调度下一次

### UI 设计

#### 5.1 提醒设置页（替换现有 `dialog_notification_settings.xml`）

使用 `RecyclerView` 替代固定开关：

```
┌─────────────────────────────┐
│  提醒与通知管理           ✕  │
│                             │
│  📋 预设提醒                │
│  ┌─────────────────────────┐│
│  │ ☀️ 早晨健康概要   [ON]  ││
│  │    ⏰ 08:00             ││
│  ├─────────────────────────┤│
│  │ 🌙 晚间记录提醒   [ON]  ││
│  │    ⏰ 20:00             ││
│  ├─────────────────────────┤│
│  │ 💧 饮水提醒       [ON]  ││
│  │    ⏰ 10:00, 14:00      ││
│  ├─────────────────────────┤│
│  │ 💊 服药打卡提醒   [OFF] ││
│  │    ⏰ 09:00             ││
│  └─────────────────────────┘│
│                             │
│  ✏️ 自定义提醒              │
│  ┌─────────────────────────┐│
│  │ 📝 称体重       [ON]    ││
│  │    ⏰ 每天 08:30  🗑    ││
│  ├─────────────────────────┤│
│  │ + 添加自定义提醒        ││
│  └─────────────────────────┘│
│                             │
│  [保存并关闭]               │
└─────────────────────────────┘
```

#### 5.2 交互行为

- **Switch 切换**：即时保存 + 调度/取消闹钟
- **时间区域点击**：弹出 `TimePickerDialog`，设置后即时更新
- **编辑按钮**：弹出 `EditReminderBottomSheet`（自定义提醒）
- **删除按钮**：仅自定义提醒显示，预设模板隐藏
- **FAB/添加行**：弹出 `EditReminderBottomSheet` 新建模式

#### 5.3 编辑弹窗 `EditReminderBottomSheet`

| 字段     | 控件                           |
| -------- | ------------------------------ |
| 提醒标题 | TextInputEditText              |
| 提醒内容 | TextInputEditText（可选）      |
| 时间     | TimePicker / TextInputEditText |
| 重复日   | ChipGroup（周一~周日 + 每天）  |
| 模块分类 | 下拉选择（可选）               |

### 闹钟调度

使用 `AlarmManager.setAlarmClock()` 确保精确触发。每个提醒使用唯一 `requestCode`（基于 `schedule.id * 100 + dayOffset` 生成）。

### 改动文件

| 文件                                            | 动作                          |
| ----------------------------------------------- | ----------------------------- |
| `database/entity/ReminderSchedule.java`       | 添加 isPreset、sortOrder 字段 |
| `database/dao/ReminderScheduleDao.java`       | 可能添加查询方法              |
| `utils/ReminderManager.java`                  | 重构：支持动态 schedule 调度  |
| `receiver/ReminderReceiver.java`              | 支持自定义 reminder 文案      |
| `ui/fragment/ProfileFragment.java`            | 重做提醒设置 UI               |
| `res/layout/dialog_notification_settings.xml` | 重写为 RecyclerView 布局      |
| `res/layout/item_reminder_schedule.xml`       | 新建                          |
| `res/layout/dialog_edit_reminder.xml`         | 新建                          |
| `database/AppDatabase.java`                   | Migration                     |
| `database/ReminderPresetDataLoader.java`      | 新建：首次插入预设            |
| `ui/adapter/ReminderScheduleAdapter.java`     | 新建                          |

---

## 数据库 Migration

新增 Entity：`Recipe`、`FavoriteFood`
扩展 Entity：`ReminderSchedule` (添加 is_preset, sort_order)

Migration 24: 版本 23 → 24

```sql
-- Recipe 表
CREATE TABLE IF NOT EXISTS recipe (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    foods_json TEXT NOT NULL,
    total_calories REAL NOT NULL DEFAULT 0,
    meal_type INTEGER NOT NULL DEFAULT -1,
    is_favorite INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- FavoriteFood 表
CREATE TABLE IF NOT EXISTS favorite_food (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    food_name TEXT NOT NULL,
    calories REAL NOT NULL DEFAULT 0,
    protein REAL NOT NULL DEFAULT 0,
    carbs REAL NOT NULL DEFAULT 0,
    fat REAL NOT NULL DEFAULT 0,
    food_library_id INTEGER,
    created_at INTEGER NOT NULL
);

-- ReminderSchedule 扩展
ALTER TABLE reminder_schedule ADD COLUMN is_preset INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reminder_schedule ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
```

---

## 导航图更新

在 `nav_graph.xml` 中添加：

- `recipeListFragment` — 常用食谱列表页（从 ProfileFragment 导航）

---

## 实施顺序

按依赖关系和风险：

1. **Phase 1（独立 Bug 修复，可并行）**

   - 臀围指标补充
   - 版本号修正
   - 显示与单位设置修复（含主题）
2. **Phase 2（功能开发，依赖 Phase 1 的 Settings 基础设施）**

   - 提醒系统重设计
3. **Phase 3（独立功能开发）**

   - 常用食谱功能
