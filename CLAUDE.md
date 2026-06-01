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

`MainActivity` is the only Activity. It dynamically sets the nav graph start destination based on whether the user is registered (`WelcomeFragment` vs `MainHomeFragment`). The launcher intent can carry a `shortcut_id` for app shortcuts or reminder routing extras (`EXTRA_MODULE_TYPE`, `EXTRA_TARGET_ID`) that `routeToReminderTargetIfNeeded()` resolves to the correct fragment.

### Data Layer

```
Entity (@Entity table) → DAO (@Dao interface) → Repository (plain class) → ViewModel (AndroidViewModel)
```

- **22 entities** in `database/entity/`, **22 DAOs** in `database/dao/`, **21 repositories** in `repository/`
- `AppDatabase` is a Room singleton (DCL pattern), current version **22**
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

### Pre-filled Data

`assets/food_library.json` (299 items) and `assets/exercise_library.json` (120 movements) are loaded on first DB creation via `FoodLibraryDataLoader` and `ExerciseLibraryDataLoader`. These use "insert if not exists by name" logic and can be re-synced by calling their `loadIfNeeded()` methods.

### AI Services

Three AI providers, all in `service/`:
- **DeepSeekService.kt** — via OkHttp + Gson, calls DeepSeek chat API
- **QwenService.kt** — via Alibaba DashScope SDK
- **WebSearchService.java** — web search integration
- **FoodImageAnalyzer.java** / **FoodParser.java** — image→nutrition pipeline
- **OpenFoodFactsService.java** — barcode lookup

The `AiCallback.kt` interface unifies callbacks across providers.

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
