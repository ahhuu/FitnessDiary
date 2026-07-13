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

Unit tests live in `app/src/test`, Room migration instrumentation tests live in
`app/src/androidTest`, and CloudBase SQL contract checks live in `cloudbase-api/tests`.

## Tech Stack & Constraints

- **Language:** Java 17 (primary) + Kotlin 1.9.24 (for a few service files)
- **Architecture:** MVVM (ViewModel + Repository + Room DAO)
- **UI:** ViewBinding, Material Design 3, Navigation Component (single-activity)
- **Min/Target SDK:** 26 / 34, `applicationId: com.cz.fitnessdiary`
- **Current app release:** 2.6.0 (`versionCode 14`)
- **Key libraries:** Room 2.6.1, MPAndroidChart, Glide, OkHttp 4.12, Gson, ZXing (barcode), Lottie, DashScope SDK (Qwen AI)
- **Cloud account/social (beta):** CloudBase email-code authentication + PostgreSQL REST/RPC; optional and disabled safely when the environment id is absent

API keys are loaded from `local.properties` into `BuildConfig`:
- `gemini.api.key` → `GEMINI_API_KEY`
- `deepseek.api.key` → `DEEPSEEK_API_KEY`
- `qwen.api.key` → `QWEN_API_KEY`

Optional CloudBase setup uses `cloudbase.env-id`. Do not put PostgreSQL passwords, CloudBase management keys, or service credentials in the Android project. Release signing values are also local-only (`signing.store.*`, `signing.key.*`).

## Architecture

### Single Activity + Navigation

`MainActivity` is the only Activity. It dynamically sets the nav graph start destination based on whether the user is registered (`WelcomeFragment` vs `MainHomeFragment`). 

`MainHomeFragment` contains a customized bottom navigation bar (3D pill-style) hosting four core functional tabs:
1. `CheckInFragment` (记录) — Home dashboard with health score ring (five-dimension breakdown), collapsible daily briefing card, and FAB quick-entry shortcut. Daily checklist for steps, water, sleep, habits, weight, mood, etc.
2. `PlanFragment` (日历历史) — Monthly calendar with workout markers, dietary calories, and step counts per cell; date-picker bottom sheet for full daily summary across all dimensions.
3. `AIChatFragment` (AI私教) — Interactive conversation with DeepSeek/Qwen AI assistants; also serves as entry to AI smart plan creation, diet analysis, and progress assessment sub-flows.
4. `ProfileFragment` (我的) — User profile, achievement center and level system, body data dashboard, fitness toolbox, content assets, and system settings.

Secondary pages accessible from home cards, toolbar actions, or navigation:
* `DietFragment` (饮食记录) — Energy balance bar (intake vs burn), fat/carb/protein macro tracking, meal log with barcode scan and favorite-food chips.
* `PlanManageFragment` (训练计划管理) — Three-tab view (current plan / explore library / personal plans), official multi-device templates, and step-by-step AI plan wizard.
* `PlanStatsFragment` (周/月数据统计) — Training statistics reports with trend charts.
* `ExerciseLibraryFragment` (动作库) — Dual-pane muscle category sidebar with exercise grid; fuzzy search, equipment filtering, tutorial bottomsheets, and custom exercise management.
* `RecipeListFragment` (常用食谱) — Saved recipe management with one-tap batch food logging.

The launcher intent can carry a `shortcut_id` for app shortcuts or reminder routing extras (`EXTRA_MODULE_TYPE`, `EXTRA_TARGET_ID`) that `routeToReminderTargetIfNeeded()` resolves to the correct fragment.

### Data Layer

```
Entity (@Entity table) → DAO (@Dao interface) → Repository (plain class) → ViewModel (AndroidViewModel)
```

- **24 Room entities** in `database/entity/`, **24 DAOs** in `database/dao/`, plus local/domain repositories including the non-Room `AccountRepository` and `SocialRepository`
- `AppDatabase` is a Room singleton (DCL pattern), current version **29**
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

Current migration notes:
- `MIGRATION_25_26` performs a one-time backfill of historical `food_record.fat` values from the matching `food_library.fat_per_100g` and `food_record.servings`, so the app does not need to rescan the full table on page open.
- `MIGRATION_26_27` creates `recipe` and `favorite_food` tables, and adds `is_preset` / `sort_order` columns to `reminder_schedule`.
- `MIGRATION_27_28` adds the first nullable cloud-account binding field and `cloud_bound_at` to the local `user` table. No health record table is uploaded or assigned a cloud owner.
- `MIGRATION_28_29` rebuilds the local `user` table to use `cloud_user_id`, preserving the binding and all local profile values. The earlier migration column remains only as an on-device schema compatibility detail.

### CloudBase Account & Social Beta

- `FitnessDiaryApplication` initializes only local CloudBase HTTP helpers; missing `cloudbase.env-id` never blocks local features.
- `AccountRepository` owns CloudBase email-code authentication and local profile binding. `User.isRegistered` remains the local onboarding flag and must not be treated as cloud login state.
- `SocialRepository` calls CloudBase PostgreSQL REST/RPC through `CloudApiClient`; Fragments must not directly issue cloud requests or attach tokens.
- The schema, indexes, RLS policies and `SECURITY DEFINER` RPC functions live in `cloudbase-api/src/main/resources/db/migration/V1__create_social_schema.sql`. Each RPC must authenticate with `auth.uid()` internally; public table reads must remain disabled.
- Only explicitly confirmed `workoutMinutes`, `checkInDays`, `steps`, or `achievement` summaries may be uploaded. Weight, diet details, menstrual records, notes, and other raw health data remain local.
- Social image posting and avatar upload are intentionally disabled until a separate secure object-storage flow is reviewed. Existing nullable image columns are reserved for future use.

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

### Background & Sensors

- `ReminderReceiver` extends `BroadcastReceiver` — handles 5 custom actions (training reminder, record reminder, morning summary, evening reminder, weekly report) plus boot-completed to re-schedule alarms after reboot
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
