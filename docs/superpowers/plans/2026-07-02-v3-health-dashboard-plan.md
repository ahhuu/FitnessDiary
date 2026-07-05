# FitnessDiary v3.0 全能健康管家 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 将 FitnessDiary 从功能模块拼盘升级为数据整合型的全能健康管家，实施五大改进维度：首页健康仪表盘、跨模块数据关联、快捷录入优化、AI日报与智能提醒、新手引导系统。

**Architecture:** 不改变现有 Entity/DAO/Repository 结构。新增聚合查询层架在现有 DAO 之上，新增 BottomSheet 统一录入入口，新增 AI 服务复用现有通道，新增三层引导系统。

**Tech Stack:** Java 17 + Kotlin 1.9.24, MVVM (ViewModel + Repository + Room DAO), ViewBinding, Material Design 3, Navigation Component

## Global Constraints

- 不新建 Room Entity 表，聚合层只读不写
- 不改变导航结构（仍为 5 Tab：记录/锻炼/饮食/AI私教/我的）
- 现有 22 Entity / 22 DAO / 21 Repository 不变
- 现有 AI 服务接口不变（DeepSeekService / QwenService），只新增调用方
- UI 极简大留白，优先 MaterialCardView、FloatingActionButton
- 补全所有 import，显式类型声明优先于 var
- 所有 DB 操作在 Executors.newSingleThreadExecutor() 上执行
- 日期处理使用 DateUtils.getTodayStartTimestamp() 归一化
- 数据库版本不变更
- 不删除现有 HealthScoreCalculator 的原有方法（向后兼容）

---

## File Structure Map

新增:
  model/DailyHealthSnapshot.java          — 健康数据快照 POJO
  model/HealthScoreBreakdown.java         — 评分明细 POJO
  model/WeeklyTrend.java                  — 周趋势 POJO
  repository/HealthAggregationRepository.java — 跨模块聚合查询（只读）
  ui/bottomSheet/QuickEntryBottomSheet.java   — 统一快捷录入弹窗
  ui/viewmodel/QuickEntryViewModel.java       — 快捷录入 ViewModel
  service/DailyBriefingService.java           — AI 日报生成
  service/LocalBriefingGenerator.java         — 本地降级规则引擎
  ui/guide/PageGuide.java                     — 页面引导数据模型
  ui/guide/GuideStep.java                     — 引导步骤数据模型
  ui/guide/TargetedGuideOverlay.java          — 引导高亮遮罩 View
  ui/guide/GuideStateManager.java             — 引导状态管理
  ui/fragment/OnboardingOverlayFragment.java  — 全局引导弹窗

修改:
  ui/fragment/CheckInFragment.java + fragment_check_in.xml       — 首页重构
  ui/fragment/MainHomeFragment.java + fragment_main_home.xml     — FAB 集成
  ui/fragment/DietFragment.java + fragment_diet.xml              — 能量横条
  ui/fragment/PlanFragment.java + fragment_plan.xml              — 跨模块摘要
  utils/HealthScoreCalculator.java                               — 新评分维度
  utils/SmartReminderHelper.java                                 — 上下文提醒
  receiver/ReminderReceiver.java                                 — 提醒增强
  res/layout/ 各页面空状态 XML

新增布局:
  res/layout/fragment_onboarding_overlay.xml
  res/layout/bottom_sheet_quick_entry.xml
  res/layout/view_dashboard_header.xml
  res/layout/view_dashboard_summary_card.xml
  res/layout/view_ai_daily_briefing.xml
  res/layout/view_guide_tooltip.xml

## Phase 1: 基础数据层（支撑维度1+2）

### Task 1: 数据模型 POJO

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/model/DailyHealthSnapshot.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/model/HealthScoreBreakdown.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/model/WeeklyTrend.java`

**Interfaces:**
- Consumes: nothing (全新 POJO)
- Produces: `DailyHealthSnapshot`（所有字段 public final 或带 getter），`HealthScoreBreakdown`（5 个 int 评分 + computeTotal()），`WeeklyTrend`（label/unit/values/change/direction）

- [ ] **Step 1: 创建 DailyHealthSnapshot.java**

字段：exerciseCalories, dietCalories, stepCalories, bmr (int)；sleepHours (double)；sleepQuality (int, 1-5, 0=无数据)；waterMl, steps, moodLevel, medicationTaken, medicationTotal, bowelCount (int)；weightKg, weightTrend (float)；completedPlans, totalPlans, consecutiveDays (int)；currentPlanName (String)；healthScore, energyBalance (int)。提供 `computeEnergyBalance()` 方法：`energyBalance = dietCalories - (bmr + exerciseCalories + stepCalories)`。

- [ ] **Step 2: 创建 HealthScoreBreakdown.java**

字段：exerciseScore (0-25), dietScore (0-25), habitsScore (0-20), bodyMetricsScore (0-15), consistencyScore (0-15), totalScore。提供 `computeTotal()` 方法累加五项。

- [ ] **Step 3: 创建 WeeklyTrend.java**

字段：label (String), unit (String), values (List\<Float\>), change (float), direction (int: 1=上升好/-1=下降好/0=中性)。构造器中自动计算 change = values[last] - values[0]。提供 `getChangeText()` 返回格式化字符串。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/model/DailyHealthSnapshot.java \
        app/src/main/java/com/cz/fitnessdiary/model/HealthScoreBreakdown.java \
        app/src/main/java/com/cz/fitnessdiary/model/WeeklyTrend.java
git commit -m "feat(v3): add DailyHealthSnapshot, HealthScoreBreakdown, WeeklyTrend models"
```

---

### Task 2: HealthAggregationRepository + 扩展 HealthScoreCalculator

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/repository/HealthAggregationRepository.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/utils/HealthScoreCalculator.java`

**Interfaces:**
- Consumes: AppDatabase (所有 DAO), DailyHealthSnapshot (Task 1), DateUtils, CalorieCalculatorUtils
- Produces: `public DailyHealthSnapshot getTodaySnapshot()`，`public DailyHealthSnapshot getDateSnapshot(long date)`，`public List<WeeklyTrend> getWeeklyTrends()`，`public HealthScoreBreakdown getTodayScoreBreakdown(UserProfile)`

- [ ] **Step 1: 创建 HealthAggregationRepository.java**

参考设计文档 4.2 节的完整实现。继承 Application 构造模式（与现有 Repository 一致）。核心方法 `getDateSnapshot(long dateTs)` 依次查询：
1. FoodRecordDao.getTotalCaloriesByDateRangeSync() → dietCalories
2. DailyLogDao.getTodayCompletedCountSync() / getTodayPlanCountSync() → training data
3. StepRecordDao.getByDateSync() → steps, stepCalories = steps * 0.04
4. SleepRecordDao.getSleepRecordsByDateRangeSync() → sleepHours, sleepQuality
5. WaterRecordDao.getTodayTotalSync() → waterMl
6. MedicationRecordDao.getRecordsByDateRangeSync() → medication
7. BowelMovementDao.getRecordsByDateRangeSync() → bowelCount
8. MoodRecordDao.getByDateSync() → moodLevel
9. WeightRecordDao.getLatestRecordSync() + 7 天趋势 → weightKg, weightTrend
10. UserDao.getUserSync() + CalorieCalculatorUtils.calculateBMR() → bmr
11. 调用 s.computeEnergyBalance()

`getWeeklyTrends()` 循环 7 天调用 getDateSnapshot()，构建训练消耗/饮食摄入/睡眠时长/体重的 WeeklyTrend 列表。

- [ ] **Step 2: 扩展 HealthScoreCalculator.java**

在现有类末尾添加（不删除原有 calculateToday/calculateForDate 等方法）：

(1) 添加静态内部类 `UserProfile`：字段 dailyCalorieTarget(2000), waterTargetMl(2000), goalType("maintain"/"lose"/"gain"), weightKg, heightCm, age, gender。

(2) 添加 `public static HealthScoreBreakdown calculateBreakdown(DailyHealthSnapshot data, UserProfile profile)` — 调用 5 个私有评分方法并 computeTotal()。

(3) 添加 5 个私有评分方法（逻辑见设计文档 3.2 节）：
- `calcExerciseScore`: completedPlans>0→15; >=totalPlans→+5; calories>300→+5; cap 25
- `calcDietScore`: ratio 0.9-1.1→20; 0.8-1.2→15; <0.5 or >1.5→5; cap 25
- `calcHabitsScore`: sleep(4)+water(4)+steps(3)+med(3)+bowel(3)+mood(3); cap 20
- `calcBodyMetricsScore`: base 8; trend vs goal(+7/+3); cap 15
- `calcConsistencyScore`: 30天→15, 21→13, 14→11, 7→9, 3→6, 1→3, 0→0

(4) 添加 `public static int calculateFromSnapshot(DailyHealthSnapshot data, UserProfile profile)` 便捷方法。

- [ ] **Step 3: 验证编译通过**

```bash
gradlew.bat assembleDebug
```
确保新增类和方法与现有代码无冲突。原有 HealthScoreCalculator 的 public 方法签名不变。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/repository/HealthAggregationRepository.java \
        app/src/main/java/com/cz/fitnessdiary/utils/HealthScoreCalculator.java
git commit -m "feat(v3): add HealthAggregationRepository and extend HealthScoreCalculator"
```

## Phase 2: 首页仪表盘 UI（维度1）

### Task 3: 仪表盘布局 XML

**Files:**
- Create: `app/src/main/res/layout/view_dashboard_header.xml`
- Create: `app/src/main/res/layout/view_dashboard_summary_card.xml`
- Create: `app/src/main/res/layout/view_ai_daily_briefing.xml`
- Modify: `app/src/main/res/layout/fragment_check_in.xml`

**Interfaces:**
- Consumes: 无
- Produces: 三个可 include 的布局组件 + 重构后的首页布局

- [ ] **Step 1: 创建 view_dashboard_header.xml**

核心指标区域。垂直 LinearLayout，包含：
- `GradientCircularProgressView`（复用现有组件）居中显示健康总分，id=`dashboard_score_ring`
- 下方 `TextView` 显示"今日能量 +XXX kcal"（id=`dashboard_energy_text`）
- 再下方水平 LinearLayout 显示连续打卡天数 + 等级（id=`dashboard_streak_text`, `dashboard_level_text`）

背景使用 `MaterialCardView` 包裹，圆角 16dp，elevation 4dp。

- [ ] **Step 2: 创建 view_dashboard_summary_card.xml**

单行摘要卡片。水平 LinearLayout，包含：
- 左侧 icon `ImageView`（24x24dp，tint 主题蓝色）
- 中间标题 `TextView`（14sp）+ 数据 `TextView`（16sp bold）
- 右侧箭头 `ImageButton`（点击展开完整卡片）

圆角 12dp，strokeColor `#E0E0E0`，padding 16dp。适用于训练/饮食/睡眠/步数/饮水/心情等摘要行。

- [ ] **Step 3: 创建 view_ai_daily_briefing.xml**

MaterialCardView 包裹，包含：
- 标题栏：🤖 AI 日报 + 日期 + 刷新按钮
- 分隔线
- 内容区：问候语 + 亮点列表 + 今日建议 + 鼓励语
- 状态指示：加载中（shimmer placeholder）/ 离线标记

- [ ] **Step 4: 重构 fragment_check_in.xml**

将现有布局从"GridLayout 卡片阵"改为垂直三区域布局：
1. `<include layout="@layout/view_dashboard_header" />`（区域1：核心指标）
2. 摘要卡片容器 LinearLayout（id=`dashboard_summary_container`）（区域2：动态添加摘要卡片）
3. `<include layout="@layout/view_ai_daily_briefing" />`（区域3：AI 日报）
4. 保留底部的"21天挑战""打卡日历""管理卡片"按钮

保留现有所有 card 的 `<include>` 但默认 `visibility="gone"`（点击摘要行展开时显示）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/view_dashboard_header.xml \
        app/src/main/res/layout/view_dashboard_summary_card.xml \
        app/src/main/res/layout/view_ai_daily_briefing.xml \
        app/src/main/res/layout/fragment_check_in.xml
git commit -m "feat(v3): add dashboard layout components and restructure home page"
```

---

### Task 4: 仪表盘 ViewModel 与 Fragment 逻辑

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/viewmodel/CheckInViewModel.java`（如有需要）

**Interfaces:**
- Consumes: HealthAggregationRepository (Task 2), DailyHealthSnapshot (Task 1), HealthScoreBreakdown (Task 1), 现有 HomeDashboardViewModel
- Produces: 仪表盘数据绑定 + 摘要卡片交互逻辑

- [ ] **Step 1: 在 CheckInFragment 中添加仪表盘初始化方法**

新增字段：
```java
private HealthAggregationRepository healthAggregationRepo;
private DailyHealthSnapshot todaySnapshot;
private List<View> summaryCardViews = new ArrayList<>();
```

新增方法 `initDashboard()`，在 `onViewCreated()` 末尾调用：
1. 创建 HealthAggregationRepository 实例
2. 在后台线程调用 `getTodaySnapshot()` 获取快照
3. 回到主线程后调用 `bindDashboardHeader(snapshot)` 更新区域1
4. 调用 `buildSummaryCards(snapshot)` 动态构建区域2摘要卡片
5. 调用 `initEnergyBalanceRing()` 更新能量天平环

- [ ] **Step 2: 实现 bindDashboardHeader()**

```java
private void bindDashboardHeader(DailyHealthSnapshot snapshot) {
    // 健康评分环
    binding.dashboardScoreRing.setProgress(snapshot.healthScore);
    // 能量天平文字
    String energyText;
    if (snapshot.energyBalance < -200) energyText = "消耗 > 摄入";
    else if (snapshot.energyBalance > 200) energyText = "摄入 > 消耗";
    else energyText = "能量平衡";
    binding.dashboardEnergyText.setText(
        String.format(Locale.getDefault(), "今日能量 %+dkcal", snapshot.energyBalance));
    // 连续打卡
    binding.dashboardStreakText.setText(
        String.format("连续打卡 %d 天", snapshot.consecutiveDays));
}
```

- [ ] **Step 3: 实现 buildSummaryCards()**

按顺序为 训练/饮食/睡眠/步数/饮水/心情 创建摘要卡片行，每行是一个 `view_dashboard_summary_card.xml` 的 inflate 实例：
- 训练：显示消耗 kcal + 完成进度
- 饮食：显示摄入 kcal
- 睡眠：显示时长 + 质量
- 步数：显示步数 + 完成比例
- 饮水：显示杯数 + 完成比例
- 心情：显示 emoji

每张卡片设置 `setOnClickListener` 跳转到对应的详情页面（复用现有导航逻辑）。默认显示 5 项，底部显示"展开全部 ▼"按钮。

保留现有 `applyCardConfig()` 中的卡片显示/隐藏偏好逻辑，但改为控制摘要卡片的可见性而非 GridLayout 渲染。

- [ ] **Step 4: 保留向后兼容**

确保现有的每日任务（DailyMission）区域、运动卡、饮食卡仍然正常工作。现有的"管理卡片"对话框仍然可用，但改为控制摘要行的显示/隐藏。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java
git commit -m "feat(v3): implement dashboard header and summary cards in CheckInFragment"
```

## Phase 3: 跨模块数据关联（维度2）

### Task 5: 训练详情页影响因素

**Files:**
- Modify: 训练/打卡详情 Fragment（定位现有的训练记录详情页）
- Modify: 对应的布局 XML

**Interfaces:**
- Consumes: HealthAggregationRepository (Task 2), DailyHealthSnapshot (Task 1)

- [ ] **Step 1: 定位训练详情页**

在 `CheckInFragment` 中，现有的运动卡展开或跳转到哪个详情页？根据代码分析，`btn_add_sport` 导航到 `sportRecordDetailFragment`。找到这个 Fragment 的路径和布局文件。

- [ ] **Step 2: 在详情页底部添加影响因素区域**

在布局底部添加一个 MaterialCardView（id=`card_factors`），包含：
- 标题："📊 影响因素"
- 前日睡眠摘要行："昨晚睡眠 7.5h · 质量优"（来自 HealthAggregationRepository.getDateSnapshot(yesterday)）
- 今日饮食摘要行："今日已摄入 1850 kcal"
- 当睡眠 < 6h 时显示橙色提示："睡眠不足可能影响训练表现"

数据从 HealthAggregationRepository 在后台线程获取，UI 更新在主线程。

- [ ] **Step 3: Commit**

```bash
git add <训练详情 Fragment 和布局路径>
git commit -m "feat(v3): add influencing factors section to training detail page"
```

---

### Task 6: 饮食页面能量状态横条

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java`
- Modify: `app/src/main/res/layout/fragment_diet.xml`

**Interfaces:**
- Consumes: HealthAggregationRepository (Task 2), DailyHealthSnapshot (Task 1)

- [ ] **Step 1: 在 fragment_diet.xml 顶部添加能量横条**

在现有搜索栏下方添加一个 MaterialCardView（id=`card_energy_status`，高度 48dp），水平 LinearLayout：
- 左侧火焰 icon + "今日消耗 XXX kcal"（训练+步数消耗）
- 中间分隔线
- 右侧食物 icon + "已摄入 XXX kcal"
- 最右侧净差值 badge：`能量余额 +XXX kcal`（绿色/蓝色/橙色）

- [ ] **Step 2: 在 DietFragment 中绑定数据**

在 `onViewCreated()` 中，后台线程从 HealthAggregationRepository 获取今日快照，主线程更新能量横条的三个 TextView。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java \
        app/src/main/res/layout/fragment_diet.xml
git commit -m "feat(v3): add energy status bar to diet page"
```

---

### Task 7: 历史日历跨模块摘要

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/PlanFragment.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/bottomSheet/DateSummaryBottomSheet.java`
- Create: `app/src/main/res/layout/bottom_sheet_date_summary.xml`

**Interfaces:**
- Consumes: HealthAggregationRepository (Task 2), DailyHealthSnapshot (Task 1)

- [ ] **Step 1: 创建 bottom_sheet_date_summary.xml**

MaterialCardView BottomSheet 布局，包含：
- 标题：日期 + 健康评分 badge
- 训练行：完成计划数 + 消耗热量
- 饮食行：摄入热量 + 宏量营养素（如有数据）
- 习惯行：睡眠/饮水/步数/心情的 icon+值 一行展示
- 体重行（如有记录）
- 底部"查看详情"按钮 → 导航到当日完整记录

- [ ] **Step 2: 创建 DateSummaryBottomSheet.java**

继承 `BottomSheetDialogFragment`。构造器接收 `long dateTimestamp`。在 `onViewCreated()` 中从 HealthAggregationRepository 获取该日期的快照，填充 UI。

- [ ] **Step 3: 修改 PlanFragment 日历点击事件**

在现有的日历日期点击回调中，将原来的单一训练记录展示替换为弹出 `DateSummaryBottomSheet`。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/PlanFragment.java \
        app/src/main/java/com/cz/fitnessdiary/ui/bottomSheet/DateSummaryBottomSheet.java \
        app/src/main/res/layout/bottom_sheet_date_summary.xml
git commit -m "feat(v3): add cross-module date summary bottom sheet to calendar"
```

## Phase 4: 快捷录入优化（维度3）

### Task 8: QuickEntryBottomSheet + QuickEntryViewModel

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/bottomSheet/QuickEntryBottomSheet.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/viewmodel/QuickEntryViewModel.java`
- Create: `app/src/main/res/layout/bottom_sheet_quick_entry.xml`
- Modify: `app/src/main/res/layout/fragment_main_home.xml`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/MainHomeFragment.java`

**Interfaces:**
- Consumes: HealthAggregationRepository (Task 2), 现有 FoodRecordRepository, TrainingPlanRepository, HomeDashboardRepository
- Produces: `QuickEntryBottomSheet`（三种录入模式）, `QuickEntryViewModel`（聚合录入逻辑）

- [ ] **Step 1: 创建 QuickEntryViewModel.java**

继承 `AndroidViewModel`。持有三个 Repository 引用：FoodRecordRepository, TrainingPlanRepository, HomeDashboardRepository。

核心方法：
```java
// 快速添加饮食记录
public void quickAddFood(long foodId, float servings, int mealType, long date)

// 快速完成训练计划
public void quickCompletePlan(int planId, long date)

// 快速记录习惯（水/步数/心情/用药/便便/体重）
public void quickRecordHabit(String cardKey, Object value, long date)
```

所有方法在 `Executors.newSingleThreadExecutor()` 上执行 DB 写入。

- [ ] **Step 2: 创建 bottom_sheet_quick_entry.xml**

Material 3 BottomSheet 布局，顶部 TabLayout 三 Tab：
- "🍽️ 饮食" Tab
- "🏋️ 训练" Tab
- "📝 习惯" Tab

每个 Tab 的内容区域用 FrameLayout 容器（id=`quick_entry_content`），根据选中 Tab 动态替换子布局。

- [ ] **Step 3: 创建 QuickEntryBottomSheet.java**

继承 `BottomSheetDialogFragment`。

在 `onViewCreated()` 中：
1. 设置 TabLayout 三 Tab + TabSelectedListener
2. 饮食 Tab 内容：加载食物搜索 EditText + RecyclerView（复用现有 FoodSearchAdapter）
3. 训练 Tab 内容：加载活跃计划 Spinner + 动作列表 RecyclerView
4. 习惯 Tab 内容：加载饮水 +/- 按钮、心情 5 表情选择器、步数输入框、用药勾选框、便便选择器、体重输入框

复用现有组件：
- 饮食搜索复用 `DietFragment` 中的食物搜索逻辑
- 训练计划和动作选择复用 `PlanManageFragment` 中的选择逻辑
- 习惯记录复用 `HomeDashboardViewModel` 中的 addWater/setTodaySteps/setTodayMood 等方法

- [ ] **Step 4: 在 MainHomeFragment 中集成 FAB**

在 `fragment_main_home.xml` 底部添加 FloatingActionButton（id=`fab_quick_entry`），位置在底部导航栏上方 16dp，图标使用 `@android:drawable/ic_input_add`。

在 `MainHomeFragment.java` 中绑定点击事件：
```java
binding.fabQuickEntry.setOnClickListener(v -> {
    QuickEntryBottomSheet sheet = new QuickEntryBottomSheet();
    sheet.show(getChildFragmentManager(), "QuickEntryBottomSheet");
});
```

FAB 默认显示在首页（TAB_CHECKIN），其他 Tab 时隐藏（通过 ViewPager2 的 OnPageChangeCallback 控制 visibility）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/bottomSheet/QuickEntryBottomSheet.java \
        app/src/main/java/com/cz/fitnessdiary/ui/viewmodel/QuickEntryViewModel.java \
        app/src/main/res/layout/bottom_sheet_quick_entry.xml \
        app/src/main/res/layout/fragment_main_home.xml \
        app/src/main/java/com/cz/fitnessdiary/ui/fragment/MainHomeFragment.java
git commit -m "feat(v3): add QuickEntryBottomSheet with three-mode quick recording"
```

## Phase 5: AI 日报与智能提醒（维度4）

### Task 9: DailyBriefingService + LocalBriefingGenerator

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/service/DailyBriefingService.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/service/LocalBriefingGenerator.java`

**Interfaces:**
- Consumes: HealthAggregationRepository (Task 2), DeepSeekService / QwenService（现有）, AiCallback（现有）
- Produces: `DailyBriefingService.generateBriefing(callback)` → DailyBriefing 数据对象, `LocalBriefingGenerator.generate(snapshot)` → 本地降级简报

- [ ] **Step 1: 创建 LocalBriefingGenerator.java**

纯 Java 类，无 Android 依赖。核心方法：

```java
public static DailyBriefing generate(DailyHealthSnapshot snapshot,
                                      HealthScoreBreakdown breakdown) {
    DailyBriefing briefing = new DailyBriefing();
    // 问候语
    briefing.greeting = generateGreeting(snapshot);
    // 评分评价
    briefing.scoreComment = generateScoreComment(breakdown.totalScore);
    // 亮点
    briefing.highlights = generateHighlights(snapshot, breakdown);
    // 建议
    briefing.suggestion = generateSuggestion(snapshot, breakdown);
    // 鼓励
    briefing.motivation = pickMotivation();
    briefing.isLocal = true;
    return briefing;
}
```

规则示例：
- 评分 ≥ 85："表现优异！各项指标均衡"
- 评分 60-84："中规中矩，还有提升空间"
- 评分 < 60："今天有些放松，明天加油"
- 亮点逻辑：热量缺口合适→"热量控制得当"、睡眠≥7h→"睡眠充足"、训练消耗>300→"训练强度到位"
- 建议逻辑：碳水偏高→"减少主食摄入"、步数不足→"多走2000步"、睡眠不足→"提前30分钟上床"

- [ ] **Step 2: 创建 DailyBriefing 内部数据类**

在 `DailyBriefingService.java` 中定义：

```java
public static class DailyBriefing {
    public String greeting;
    public String scoreComment;
    public List<String> highlights;
    public String suggestion;
    public String motivation;
    public boolean isLocal;  // true=本地生成, false=AI生成
    public long generatedAt;
}
```

- [ ] **Step 3: 创建 DailyBriefingService.java**

核心方法：

```java
public void generateBriefing(DailyBriefingCallback callback) {
    executor.execute(() -> {
        // 1. 获取昨日数据快照
        long yesterday = DateUtils.getTodayStartTimestamp() - 86400000L;
        DailyHealthSnapshot snapshot = aggregationRepo.getDateSnapshot(yesterday);

        // 2. 获取评分明细
        HealthScoreBreakdown breakdown =
            HealthScoreCalculator.calculateBreakdown(snapshot, buildUserProfile());

        // 3. 尝试 AI 生成
        if (isApiKeyConfigured()) {
            callAiForBriefing(snapshot, breakdown, callback);
        } else {
            // 降级为本地生成
            DailyBriefing briefing = LocalBriefingGenerator.generate(snapshot, breakdown);
            new Handler(Looper.getMainLooper()).post(() -> callback.onBriefingReady(briefing));
        }
    });
}

private void callAiForBriefing(DailyHealthSnapshot snapshot,
        HealthScoreBreakdown breakdown, DailyBriefingCallback callback) {
    String userPrompt = buildUserPrompt(snapshot, breakdown);
    String systemPrompt = "你是专业健康教练。基于用户健康数据生成今日简报。"
        + "以JSON格式输出：{\"greeting\":\"...\",\"scoreComment\":\"...\","
        + "\"highlights\":[\"...\"],\"suggestion\":\"...\",\"motivation\":\"...\"}";

    // 复用 DeepSeekService（默认）或 QwenService
    DeepSeekService.INSTANCE.sendMessage(systemPrompt, null, userPrompt,
        new AiCallback() {
            @Override
            public void onSuccess(String response, String reasoning) {
                DailyBriefing briefing = parseAiResponse(response);
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onBriefingReady(briefing));
            }
            @Override
            public void onError(String error) {
                // 降级
                DailyBriefing briefing = LocalBriefingGenerator.generate(snapshot, breakdown);
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onBriefingReady(briefing));
            }
        });
}
```

构建 Prompt 时，将 snapshot 数据格式化为自然语言（参考设计文档 6.2 节）。

- [ ] **Step 4: 缓存策略**

在 SharedPreferences（key: `"daily_briefing"`）中缓存最后一次生成的简报 JSON。每次请求时检查：
- 如果缓存日期 = 今天 AND 缓存有效 → 直接返回缓存
- 否则 → 重新生成并更新缓存

提供 `getCachedBriefing()` 和 `invalidateCache()` 方法。

- [ ] **Step 5: 在首页集成 AI 日报卡片**

在 CheckInFragment 的 `bindDashboardHeader()` 之后调用 `loadDailyBriefing()`：

```java
private void loadDailyBriefing() {
    DailyBriefing cached = briefingService.getCachedBriefing();
    if (cached != null) {
        bindBriefingCard(cached);
        return;
    }
    // 显示 shimmer loading
    binding.cardAiBriefing.showLoading();
    briefingService.generateBriefing(briefing -> {
        binding.cardAiBriefing.hideLoading();
        bindBriefingCard(briefing);
    });
}
```

`bindBriefingCard()` 填充 view_ai_daily_briefing.xml 的各 TextView。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/service/DailyBriefingService.java \
        app/src/main/java/com/cz/fitnessdiary/service/LocalBriefingGenerator.java \
        app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java
git commit -m "feat(v3): add AI daily briefing service with local fallback"
```

---

### Task 10: 智能提醒升级

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/utils/SmartReminderHelper.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/receiver/ReminderReceiver.java`

**Interfaces:**
- Consumes: HealthAggregationRepository (Task 2)

- [ ] **Step 1: 增强 SmartReminderHelper 的文案生成**

在现有的 `getMorningContent(Context)`、`getEveningContent(Context)`、`getInactivityContent()` 方法中，注入当前健康数据上下文：

```java
// 在 ReminderReceiver.onReceive() 触发时
DailyHealthSnapshot snapshot = new HealthAggregationRepository(
    (Application) context.getApplicationContext()).getTodaySnapshot();

// 传递给增强后的方法
String content = SmartReminderHelper.getMorningContent(context, snapshot);
```

修改三个 content 方法签名，增加 `DailyHealthSnapshot` 参数（可空，为空时退化为现有逻辑）：

- `getMorningContent(Context, DailyHealthSnapshot)`：在原文案基础上追加"昨日消耗XXX kcal，摄入XXX kcal"等上下文
- `getEveningContent(Context, DailyHealthSnapshot)`：在原文案基础上追加"今日热量缺口XXX kcal，建议XXX"
- `getInactivityContent(DailyHealthSnapshot)`：追加"最近体重趋势XXX"等

- [ ] **Step 2: 修改 ReminderReceiver 调用**

在 `onReceive()` 中，调用 `SmartReminderHelper` 前先获取 HealthAggregationRepository 快照：

```java
if (snapshot == null) {
    try {
        Application app = (Application) context.getApplicationContext();
        HealthAggregationRepository repo = new HealthAggregationRepository(app);
        snapshot = repo.getTodaySnapshot();
    } catch (Exception e) {
        // 降级：使用原有文案（不传 snapshot）
    }
}
```

确保即使获取快照失败，提醒功能也不受影响。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/utils/SmartReminderHelper.java \
        app/src/main/java/com/cz/fitnessdiary/receiver/ReminderReceiver.java
git commit -m "feat(v3): enhance smart reminders with health data context"
```

## Phase 6: 新手引导系统（维度5）

### Task 11: GuideStateManager + 引导数据模型

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/guide/GuideStep.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/guide/PageGuide.java`
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/guide/GuideStateManager.java`

**Interfaces:**
- Consumes: SharedPreferences
- Produces: `GuideStateManager`（引导状态 CRUD），`PageGuide`（页面引导步骤定义），`GuideStep`（单步数据）

- [ ] **Step 1: 创建 GuideStep.java**

```java
package com.cz.fitnessdiary.ui.guide;

public class GuideStep {
    public int targetViewId;      // 要高亮的 View ID
    public String title;          // 提示标题
    public String description;    // 提示描述
    public Anchor anchor;         // TOP / BOTTOM / LEFT / RIGHT

    public enum Anchor { TOP, BOTTOM, LEFT, RIGHT }

    public GuideStep(int targetViewId, String title, String description, Anchor anchor) {
        this.targetViewId = targetViewId;
        this.title = title;
        this.description = description;
        this.anchor = anchor;
    }
}
```

- [ ] **Step 2: 创建 PageGuide.java**

```java
package com.cz.fitnessdiary.ui.guide;

import java.util.List;

public class PageGuide {
    public String pageKey;          // 页面标识
    public List<GuideStep> steps;   // 引导步骤列表

    public PageGuide(String pageKey, List<GuideStep> steps) {
        this.pageKey = pageKey;
        this.steps = steps;
    }
}
```

- [ ] **Step 3: 创建 GuideStateManager.java**

```java
package com.cz.fitnessdiary.ui.guide;

import android.content.Context;
import android.content.SharedPreferences;

public class GuideStateManager {
    private static final String PREF_NAME = "guide_state";
    private static final String KEY_GLOBAL_DONE = "global_onboarding_done";
    private static final String KEY_PAGE_PREFIX = "page_guide_done_";
    private final SharedPreferences prefs;

    public GuideStateManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isGlobalOnboardingDone() {
        return prefs.getBoolean(KEY_GLOBAL_DONE, false);
    }

    public void markGlobalOnboardingDone() {
        prefs.edit().putBoolean(KEY_GLOBAL_DONE, true).apply();
    }

    public boolean isPageGuideDone(String pageKey) {
        return prefs.getBoolean(KEY_PAGE_PREFIX + pageKey, false);
    }

    public void markPageGuideDone(String pageKey) {
        prefs.edit().putBoolean(KEY_PAGE_PREFIX + pageKey, true).apply();
    }

    /** Reset all guides (for testing or "show tips again" option) */
    public void resetAll() {
        prefs.edit().clear().apply();
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/guide/GuideStep.java \
        app/src/main/java/com/cz/fitnessdiary/ui/guide/PageGuide.java \
        app/src/main/java/com/cz/fitnessdiary/ui/guide/GuideStateManager.java
git commit -m "feat(v3): add guide data models and state manager"
```

---

### Task 12: TargetedGuideOverlay 页面引导遮罩

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/guide/TargetedGuideOverlay.java`
- Create: `app/src/main/res/layout/view_guide_tooltip.xml`

**Interfaces:**
- Consumes: PageGuide (Task 11), GuideStep (Task 11), GuideStateManager (Task 11)
- Produces: 可附加到任意 Activity 的半透明遮罩 + 高亮挖洞 + Tooltip 引导

- [ ] **Step 1: 创建 view_guide_tooltip.xml**

小卡片布局，MaterialCardView 包裹：
- 标题 TextView（14sp bold）
- 描述 TextView（12sp）
- 底部水平 LinearLayout："跳过" 文字按钮 + "下一步 →" 文字按钮
- 小三角指示器（根据 anchor 方向调整位置）

背景白色，圆角 8dp，elevation 8dp，maxWidth 240dp。

- [ ] **Step 2: 创建 TargetedGuideOverlay.java**

自定义 `FrameLayout`（或直接在 Activity 的 DecorView 上叠加）：

```java
public class TargetedGuideOverlay {
    private final ViewGroup rootView;  // Activity.getWindow().getDecorView()
    private final PageGuide guide;
    private int currentStep = 0;
    private View overlayView;
    private View tooltipView;
    private GuideStateManager stateManager;
    private Runnable onComplete;

    public TargetedGuideOverlay(Activity activity, PageGuide guide,
                                 GuideStateManager stateManager, Runnable onComplete) {
        this.rootView = (ViewGroup) activity.getWindow().getDecorView();
        this.guide = guide;
        this.stateManager = stateManager;
        this.onComplete = onComplete;
    }

    public void start() {
        if (stateManager.isPageGuideDone(guide.pageKey)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        showStep(0);
    }

    private void showStep(int index) {
        // 清除上一步的遮罩
        removeOverlay();

        GuideStep step = guide.steps.get(index);

        // 1. 创建全屏半透明遮罩
        overlayView = new View(rootView.getContext());
        overlayView.setBackgroundColor(0x99000000); // 60% 黑色
        overlayView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. 在目标 View 上"挖洞"——通过自定义 Drawable 或 Canvas clip
        // 找到目标 View 的屏幕坐标
        View targetView = rootView.findViewById(step.targetViewId);
        int[] location = new int[2];
        targetView.getLocationOnScreen(location);

        // 3. 创建 Tooltip View
        tooltipView = LayoutInflater.from(rootView.getContext())
            .inflate(R.layout.view_guide_tooltip, rootView, false);
        // 设置标题、描述、按钮事件

        // 4. 添加到 DecorView
        rootView.addView(overlayView);
        rootView.addView(tooltipView, createTooltipLayoutParams(step, location, targetView));

        // 5. "下一步"按钮 → showStep(index + 1) 或 complete()
        // 6. "跳过"按钮 → complete()

        // 遮罩点击 → 进入下一步
        overlayView.setOnClickListener(v -> advance(index));
    }

    private void advance(int currentIndex) {
        if (currentIndex + 1 < guide.steps.size()) {
            showStep(currentIndex + 1);
        } else {
            complete();
        }
    }

    private void complete() {
        removeOverlay();
        stateManager.markPageGuideDone(guide.pageKey);
        if (onComplete != null) onComplete.run();
    }

    private void removeOverlay() {
        if (overlayView != null) rootView.removeView(overlayView);
        if (tooltipView != null) rootView.removeView(tooltipView);
    }
}
```

"挖洞"实现：使用自定义 `Drawable` 重写 `draw(Canvas)`，先 `canvas.drawColor(0x99000000)` 填充遮罩，再通过 `canvas.clipRect(highlightRect, Op.DIFFERENCE)` 或 `PorterDuff.Mode.CLEAR` 在高亮区域透明化。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/guide/TargetedGuideOverlay.java \
        app/src/main/res/layout/view_guide_tooltip.xml
git commit -m "feat(v3): add TargetedGuideOverlay for per-page tooltip guides"
```

---

### Task 13: OnboardingOverlayFragment 全局引导 + 空状态

**Files:**
- Create: `app/src/main/res/layout/fragment_onboarding_overlay.xml`
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/OnboardingOverlayFragment.java`
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/MainHomeFragment.java`（触发入口）

**Interfaces:**
- Consumes: GuideStateManager (Task 11)
- Produces: 全屏全局引导弹窗

- [ ] **Step 1: 创建 fragment_onboarding_overlay.xml**

全屏 DialogFragment 布局，包含：
- ViewPager2（4 页引导内容）
- CircleIndicator 或简单的点状指示器
- "开始使用"按钮（最后一页显示）
- "跳过"文字按钮（右上角，所有页显示）

每页是一个 FrameLayout，包含：
- 居中的大 emoji / Lottie 动画
- 标题 TextView
- 描述 TextView
- 背景使用渐变或纯色

4 页内容（见设计文档 7.2 节）：
1. 健康仪表盘 → 📊 emoji + "查看每日健康评分、能量天平和 AI 智能建议"
2. 快捷记录 → ➕ emoji + "点击 + 按钮，一键记录训练、饮食和习惯"
3. 数据看板 → 📈 emoji + "在历史页面查看所有健康数据的趋势和交叉分析"
4. AI 私教 → 🤖 emoji + "随时咨询训练和饮食问题，获取个性化建议"

- [ ] **Step 2: 创建 OnboardingOverlayFragment.java**

继承 `DialogFragment`，全屏样式。在 `onViewCreated()` 中：
1. 设置 ViewPager2 Adapter（4 页固定内容）
2. 绑定 CircleIndicator
3. 绑定"开始使用"按钮 → `dismiss()` + `GuideStateManager.markGlobalOnboardingDone()`
4. 绑定"跳过"按钮 → `dismiss()` + `GuideStateManager.markGlobalOnboardingDone()`

- [ ] **Step 3: 在 MainHomeFragment 中触发全局引导**

在 `onViewCreated()` 末尾添加：

```java
// v3.0: 首次启动显示全局引导
GuideStateManager guideManager = new GuideStateManager(requireContext());
if (!guideManager.isGlobalOnboardingDone()) {
    new OnboardingOverlayFragment().show(
        getChildFragmentManager(), "OnboardingOverlay");
}
```

- [ ] **Step 4: 创建空状态布局并集成到各页面**

创建以下空状态 XML（按需）：
- `res/layout/empty_state_training_plan.xml`：🏋️ + "还没有训练计划" + 三个操作按钮
- `res/layout/empty_state_diet.xml`：🍽️ + "今天还没记录饮食" + 快捷操作
- `res/layout/empty_state_habit.xml`：📝 + "添加健康习惯" + 按钮
- `res/layout/empty_state_exercise_library.xml`：📚 + "收藏常用动作" + 按钮

在每个 Fragment 的 Adapter 或 Observer 中，当数据为空时 `recyclerView.setVisibility(GONE)` + `emptyStateView.setVisibility(VISIBLE)`。

- [ ] **Step 5: 提交空状态布局文件**

```bash
git add app/src/main/res/layout/empty_state_*.xml
```

- [ ] **Step 6: 在各 Fragment 中添加页面引导触发**

在以下 Fragment 的 `onViewCreated()` 末尾添加页面引导逻辑（参考设计文档 7.3 节对齐表）：

```java
// 示例：DietFragment
GuideStateManager guideManager = new GuideStateManager(requireContext());
if (!guideManager.isPageGuideDone("diet")) {
    PageGuide dietGuide = new PageGuide("diet", Arrays.asList(
        new GuideStep(R.id.search_food, "搜索食物", "输入名称或拍照识别", GuideStep.Anchor.BOTTOM),
        new GuideStep(R.id.btn_scan_barcode, "扫码录入", "扫描条形码快速录入食物", GuideStep.Anchor.BOTTOM),
        new GuideStep(R.id.today_summary, "今日汇总", "查看热量和营养素摄入", GuideStep.Anchor.TOP)
    ));
    new TargetedGuideOverlay(requireActivity(), dietGuide, guideManager, null).start();
}
```

各页面引导步骤见设计文档 7.3 节表格。

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/fragment_onboarding_overlay.xml \
        app/src/main/java/com/cz/fitnessdiary/ui/fragment/OnboardingOverlayFragment.java \
        app/src/main/java/com/cz/fitnessdiary/ui/fragment/MainHomeFragment.java
git commit -m "feat(v3): add global onboarding overlay, empty states, and page guides"
```
