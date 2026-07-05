# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Windows
gradlew.bat assembleDebug
gradlew.bat installDebug

# macOS / Linux
./gradlew assembleDebug
./gradlew installDebug
```

No test suite exists yet (no `app/src/test` or `app/src/androidTest` directories).

## Tech Stack & Constraints

- **Language:** Java 17 (primary) + Kotlin 1.9.24 (for a few service files)
- **Architecture:** MVVM (ViewModel + Repository + Room DAO)
- **UI:** ViewBinding, Material Design 3, Navigation Component (single-activity)
- **Min/Target SDK:** 26 / 34, `applicationId: com.cz.fitnessdiary`
- **Key libraries:** Room 2.6.1, MPAndroidChart, Glide, OkHttp 4.12, Gson, ZXing (barcode), Lottie, DashScope SDK (Qwen AI)

API keys are loaded from `local.properties` into `BuildConfig`:
- `gemini.api.key` → `GEMINI_API_KEY`
- `deepseek.api.key` → `DEEPSEEK_API_KEY`
- `qwen.api.key` → `QWEN_API_KEY`

## Architecture

### Single Activity + Navigation

`MainActivity` is the only Activity. It dynamically sets the nav graph start destination based on whether the user is registered (`WelcomeFragment` vs `MainHomeFragment`). 

`MainHomeFragment` contains a customized bottom navigation bar hosting five core functional tabs:
1. `CheckInFragment` (记录) — v2.3: 首页右上角健康评分环（五维度评分），健康日报卡片（可折叠），FAB 四功能区快捷入口。Daily checklist for steps, water, sleep, habits, weight, mood, etc.
2. `PlanFragment` (日历历史) — v2.3: 月度日历，单元格显示训练标记，日期弹窗查看当日全维度摘要与训练详情。Monthly calendar view displaying workout logs and customizable color notes.
3. `DietFragment` (饮食记录) — v2.3: 顶部能量状态横条（摄入 vs 消耗实时对比）。Nutrients tracking and meal log.
4. `AIChatFragment` (AI私教) — Interactive conversation with DeepSeek/Qwen AI assistants.
5. `ProfileFragment` (我的) — User profile and achievement settings (v2.3: 数据周报更新).

The original workout plan list manager and chart statistics are migrated to secondary pages:
* `PlanManageFragment` (训练计划管理) — 三 Tab 视图（当前计划 / 探索计划库 / 个人计划），支持”基础/进阶/自定义”分类展示、官方多设备场景模板一键导入及分步式 AI 智能制定计划。
* `PlanStatsFragment` (周/月数据统计) — v2.3: 更新训练数据统计报表。
* `ExerciseLibraryFragment` (动作库) — Provides dual-pane interaction with muscle category sidebar and exercises grid list. Supports fuzzy searching, equipment-based filtering, detailed tutorials inside a BottomSheet, and adding custom exercises.

The launcher intent can carry a `shortcut_id` for app shortcuts or reminder routing extras (`EXTRA_MODULE_TYPE`, `EXTRA_TARGET_ID`) that `routeToReminderTargetIfNeeded()` resolves to the correct fragment.

### Data Layer

```
Entity (@Entity table) → DAO (@Dao interface) → Repository (plain class) → ViewModel (AndroidViewModel)
```

- **24 entities** in `database/entity/`, **24 DAOs** in `database/dao/`, **24 repositories** in `repository/` (includes `HealthAggregationRepository` added in v2.3, `Recipe`/`FavoriteFood` added in v2.4)
- `AppDatabase` is a Room singleton (DCL pattern), current version **27**
- Repository classes extend `AndroidViewModel` pattern — they take `Application` in constructor to get the DB instance
- All DB operations run on `Executors.newSingleThreadExecutor()` — not on the main thread but also not via Room's built-in async support
- **No reactive patterns** (LiveData only at the ViewModel→View boundary); Repository methods return plain lists/objects

### Database Migrations

When adding new entities or columns:
1. Add the `@Entity` / `@ColumnInfo` to the relevant class
2. Add a new `static final Migration MIGRATION_X_Y` in `AppDatabase`
3. Append it to the `.addMigrations(...)` chain in `getInstance()`
4. Bump the `version` in `@Database`
5. Keep all old migrations in place — Room replays them sequentially on existing installs

Current migration note:
- `MIGRATION_25_26` performs a one-time backfill of historical `food_record.fat` values from the matching `food_library.fat_per_100g` and `food_record.servings`, so the app does not need to rescan the full table on page open.

### Pre-filled Data

`assets/food_library.json` (299 items) and `assets/exercise_library.json` (133 movements) are loaded on first DB creation via `FoodLibraryDataLoader` and `ExerciseLibraryDataLoader`. These use "insert if not exists by name" logic; `ExerciseLibraryDataLoader` now clears stale rows on version bump to ensure deleted exercises don't persist. Re-sync by calling their `loadIfNeeded()` methods.

### AI Services

Three AI providers, all in `service/`:
- **DeepSeekService.kt** — via OkHttp + Gson, calls DeepSeek chat API
- **QwenService.kt** — via Alibaba DashScope SDK
- **WebSearchService.java** — web search integration
- **FoodImageAnalyzer.java** / **FoodParser.java** — image→nutrition pipeline
- **OpenFoodFactsService.java** — barcode lookup
- **DailyBriefingService.java** (v2.3) — 健康日报生成服务，聚合健康数据调用 AI 生成每日简报，失败时降级到本地规则引擎
- **LocalBriefingGenerator.java** (v2.3) — 本地规则引擎降级方案，基于当日快照生成模板化日报

The `AiCallback.kt` interface unifies callbacks across providers.

### v2.3 New Components

**健康数据聚合层**：
- `HealthAggregationRepository` — 只读聚合查询层，跨 11 个 DAO 查询生成 `DailyHealthSnapshot`
- `HealthScoreCalculator.UserProfile` / `calculateBreakdown()` — 五维度评分引擎（训练 0-25 / 饮食 0-25 / 习惯 0-20 / 身体 0-15 / 坚持 0-15）
- `DailyHealthSnapshot` / `HealthScoreBreakdown` / `WeeklyTrend` — 聚合数据模型

**快捷录入**：
- `QuickEntryBottomSheet` — 四功能区按钮快捷入口弹窗（智能助理/核心记录/身体指标/生活日常）
- `QuickEntryViewModel` — 聚合 FoodRecordRepository、TrainingPlanRepository、HomeDashboardRepository 的录入逻辑

**新手引导系统** (`ui/guide/`)：
- `OnboardingOverlayFragment` — 全屏全局引导弹窗
- `PageGuide` / `GuideStep` — 引导步骤数据模型
- `TargetedGuideOverlay` — 半透明遮罩 + 高亮挖洞 + Tooltip 引导
- `GuideStateManager` — SharedPreferences 管理引导完成状态

**跨模块关联**：
- `SportRecordDetailFragment` (v2.3) — 训练详情页底部"影响因素"卡片（前日睡眠、今日饮食）
- `DietFragment` (v2.3) — 顶部"能量状态横条"（摄入 vs 消耗实时对比）；移除能量余额 Badge
- `DateSummaryBottomSheet` (v2.3) — 日历日期弹窗展示全维度摘要 + 训练详情（组数/容量/时长）+ 备注 + 8 色选择器

**目标体重** (v2.3)：
- `WeightRecordDetailFragment` 新增目标体重显示与编辑，默认基于 BMI 健康范围 + 增肌/减脂目标自动计算，支持手动修改

**报表更新** (v2.3)：
- `PlanStatsFragment` — 日历报表页训练数据统计
- `ReportBottomSheetFragment` / `ReportViewModel` — 个人中心数据周报

### Background & Sensors

- `ReminderReceiver` extends `BroadcastReceiver` — handles 6 custom actions (training reminder, record reminder, morning summary, evening reminder, inactivity nudge, weekly report) plus boot-completed to re-schedule alarms after reboot
- `ReminderManager` is a static utility that schedules/cancels alarms via `AlarmManager`, stores preferences in `SharedPreferences`
- `SmartReminderHelper` provides "smart push" logic with activity-based timing
- `HomeWidgetProvider` is a home screen app widget, updated via `ACTION_WIDGET_REFRESH` broadcast
- `StepSensorHelper` — listens to `Sensor.TYPE_STEP_COUNTER`, manages baseline via SharedPreferences, persists daily steps to `step_record` table. Requires `ACTIVITY_RECOGNITION` permission (runtime request on API 29+)

### Custom UI Widgets

`ui/widget/` contains hand-drawn views: `StreakCalendarView`, `WaterCupProgressView`, `BristolChartView`, `GradientCircularProgressView`, measurement/sleep/weight chart views, and spark-line views.

### Home Page Cards

The `CheckInFragment` home page has 2 main cards (sport, diet) + 10 small cards in a 2-column GridLayout:
- **Main cards**: sport (training check-in + calorie burn), diet (calories + carbs/protein macros)
- **Small cards** (managed via "管理卡片" dialog, drag-reorder + toggle): water, sleep, habit, medication, weight, measurement, bowel, menstrual, step, mood
- **Step card**: sensor auto-count + manual entry, configurable target (default 8000, SharedPreferences), detail page with edit/delete
- **Mood card**: 5-emoji daily picker via `MoodPickerBottomSheet` (BottomSheetDialogFragment), shows selected emoji on card
- Exercise calorie formula: `Σ(MET × 3.5 × weight(kg) × duration(s) / 200 / 60) + steps × 0.04 kcal`
- Diet macros reuse existing `DietViewModel.getTodayTotalCarbs()` / `getTodayTotalProtein()`

## Key Patterns

- **Fragments use ViewBinding**: Inflate with `FragmentXxxBinding.inflate(inflater, container, false)`, store in a field, access views via `binding.xxx`
- **Bottom sheets** extend `BottomSheetDialogFragment` (e.g., `AddFoodBottomSheetFragment`, `QuickAiChatBottomSheet`)
- **Adapters** live in `ui/adapter/` and follow standard `RecyclerView.Adapter` patterns
- **`model/`** package contains POJOs that are NOT Room entities — UI state models like `DailyMission`, `Achievement`, `HomeCardUiModel`, `TrainingTemplate`
- **Date handling** uses `DateUtils.getTodayStartTimestamp()` (millisecond epoch at 00:00) — all date comparisons use this normalized format
- **Error handling** goes through `ErrorHandler.java` utility

## Behavior Rules

行为约束以 `.agent/rules/fitnessdiary-app.md` 为准。摘要：
- 补全所有 import
- 报错先分析 Root Cause 再修复
- 显式类型声明优先于 `var`
- UI 极简大留白，优先 `MaterialCardView`、`FloatingActionButton`
