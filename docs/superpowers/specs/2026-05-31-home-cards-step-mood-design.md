# Home Page Enhancement: Step Card, Mood Card, Exercise Calories, Diet Macros

**Date:** 2026-05-31
**Status:** Approved

## Overview

Four changes to the home page (CheckInFragment):
1. New "步数" (Steps) small card — sensor auto + manual entry, configurable target
2. New "情绪" (Mood) small card — 5-emoji daily picker
3. Exercise card now shows today's total calorie burn (workout + steps)
4. Diet card now shows carb and protein intake with mini progress bars

---

## 1. Database Changes (v21 → v22)

### 1.1 New Entity: StepRecord

```java
@Entity(tableName = "step_record")
public class StepRecord {
    @PrimaryKey(autoGenerate = true) private int id;
    @ColumnInfo(name = "date")        private long date;       // epoch at 00:00
    @ColumnInfo(name = "steps")       private int steps;
    @ColumnInfo(name = "source")      private int source;      // 0=sensor, 1=manual, 2=hybrid
    @ColumnInfo(name = "create_time") private long createTime;
}
```

- Unique constraint on `date` (one record per day)
- `source`: 0 = pure sensor, 1 = pure manual entry, 2 = sensor base + user adjusted

### 1.2 New Entity: MoodRecord

```java
@Entity(tableName = "mood_record")
public class MoodRecord {
    @PrimaryKey(autoGenerate = true) private int id;
    @ColumnInfo(name = "date")        private long date;       // epoch at 00:00
    @ColumnInfo(name = "mood_code")   private String moodCode; // HAPPY|NEUTRAL|SAD|IRRITABLE|ANXIOUS
    @ColumnInfo(name = "note")        private String note;
    @ColumnInfo(name = "create_time") private long createTime;
}
```

- Unique constraint on `date` (one mood per day)
- Mood codes map: HAPPY→😊开心, NEUTRAL→😐一般, SAD→😢低落, IRRITABLE→😡烦躁, ANXIOUS→😰焦虑

### 1.3 Migration MIGRATION_21_22

```sql
CREATE TABLE step_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date INTEGER NOT NULL,
    steps INTEGER NOT NULL DEFAULT 0,
    source INTEGER NOT NULL DEFAULT 0,
    create_time INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_step_date ON step_record(date);

CREATE TABLE mood_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date INTEGER NOT NULL,
    mood_code TEXT,
    note TEXT,
    create_time INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_mood_date ON mood_record(date);
```

---

## 2. New Files

| File | Purpose |
|---|---|
| `database/entity/StepRecord.java` | Step record entity |
| `database/entity/MoodRecord.java` | Mood record entity |
| `database/dao/StepRecordDao.java` | Step CRUD + queries |
| `database/dao/MoodRecordDao.java` | Mood CRUD + queries |
| `repository/StepRecordRepository.java` | Step data access |
| `repository/MoodRecordRepository.java` | Mood data access |
| `service/StepSensorService.java` | Foreground service for TYPE_STEP_COUNTER |
| `res/layout/view_home_card_step.xml` | Step card layout |
| `res/layout/view_home_card_mood.xml` | Mood card layout |
| `res/layout/dialog_step_input.xml` | Step manual input dialog |
| `res/layout/dialog_step_target.xml` | Step target config dialog |
| `res/drawable/bg_card_3d_step.xml` | Step card 3D background |
| `res/drawable/bg_card_3d_mood.xml` | Mood card 3D background |

---

## 3. Modified Files

### 3.1 AppDatabase.java
- Add `StepRecord.class`, `MoodRecord.class` to `@Database` entities
- Add `stepRecordDao()`, `moodRecordDao()` accessor methods
- Add `MIGRATION_21_22`
- Bump version to 22

### 3.2 CheckInFragment.java
- Initialize step card: bind sensor data, show progress toward target
- Initialize mood card: render 5 emoji row, highlight today's selection
- Exercise card: add calorie burn row = Σ(workout MET calcs) + step calories
- Diet card: add carbs & protein rows with mini progress bars

### 3.3 HomeDashboardViewModel.java
- Add `LiveData<StepRecord> todayStep`
- Add `LiveData<MoodRecord> todayMood`
- Add `LiveData<Integer> todayExerciseCalories` (computed)
- Add `LiveData<Double> todayCarbs`, `LiveData<Double> todayProtein`
- Add `setTodaySteps(int)`, `setTodayMood(String)`, `setStepTarget(int)`
- Load step target from SharedPreferences (default 8000)

### 3.4 HomeDashboardRepository.java
- Add `StepRecordRepository`, `MoodRecordRepository` fields
- Add query methods for today's step/mood

### 3.5 EditCardsAdapter.java
- Add `CARD_STEP` ("step"), `CARD_MOOD` ("mood") to card configs
- Add corresponding icon resources

### 3.6 FoodRecordDao.java
- Add `getDateCarbsTotal(long startOfDay, long endOfDay)` → `LiveData<Double>`
- Add `getDateProteinTotal(long startOfDay, long endOfDay)` → `LiveData<Double>`

### 3.7 fragment_checkin.xml
- Add step card and mood card placeholders in GridLayout

### 3.8 AndroidManifest.xml
- Add `<service android:name=".service.StepSensorService" android:foregroundServiceType="health" />`
- Add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_HEALTH` permissions

---

## 4. Step Sensor Logic

```
StepSensorService extends Service
  ├─ Registers Sensor.TYPE_STEP_COUNTER (system-wide cumulative since boot)
  ├─ On first launch: records baseline offset
  ├─ Daily steps = current counter - baseline (reset at midnight via AlarmManager)
  ├─ Writes to StepRecordRepository every 5 minutes
  ├─ Shows persistent notification with current step count
  └─ On user manual edit: sets source=2 (hybrid), uses manual value as override
```

Step calorie formula: `steps × 0.04 kcal/step` (adult average)

Step target: stored in `SharedPreferences` key `step_target`, default **8000**, editable via long-press → dialog.

---

## 5. Exercise Calorie Calculation

```
Today Exercise Calories = Σ(workout calories) + step calories

Workout calories per plan = MET × 3.5 × weight(kg) × duration(seconds) / (200 × 60)

MET mapping by category:
  "有氧" / "cardio"   → 7.0
  "力量" / "strength" → 3.5
  "HIIT"              → 8.0
  "瑜伽" / "拉伸"     → 2.5
  default              → 4.0

User weight: from WeightRecord latest entry, fallback 70kg
```

Display on exercise card: "🔥 今日消耗 XXX 千卡"

---

## 6. Diet Macros Display

Query from `FoodRecordDao`:
```java
@Query("SELECT SUM(carbs) FROM food_record WHERE record_date BETWEEN :start AND :end")
LiveData<Double> getDateCarbsTotal(long start, long end);

@Query("SELECT SUM(protein) FROM food_record WHERE record_date BETWEEN :start AND :end")
LiveData<Double> getDateProteinTotal(long start, long end);
```

Display on diet card below calories:
- "🍚碳水 XXg" with mini progress bar (target: 250g default from SharedPreferences)
- "🥩蛋白质 XXg" with mini progress bar (target: 60g default from SharedPreferences)

---

## 7. UI Layout

### Step Card
```
┌──────────────────┐
│ 👣 步数      +   │
│ 今日 00:00       │
│    6,432         │
│ ████████░░  80%  │
│ ≈257千卡 | 目标8000│
└──────────────────┘
```
- Quick-add button opens number input dialog
- Long-press card opens target config dialog
- Progress bar fill = steps / target

### Mood Card (collapsed — shows today's mood only)
```
┌──────────────────┐
│ 💗 情绪           │
│ 今天 14:30       │
│                  │
│      😊          │
│     开心         │
│ 今天感觉很不错！  │
└──────────────────┘
```
- If mood set today: show large emoji + mood name text
- If not set yet: show "点击记录" placeholder text
- **Tap card** → opens `MoodPickerBottomSheet` with 5 emoji row for selection
- Selected emoji in picker: full opacity + scale 1.3 + accent ring; unselected: 40% opacity
- Picker saves immediately on tap, card refreshes automatically
- Card header icon uses 💗 (heart) to avoid conflicting with mood emojis
- Bottom summary line: mood-based text (e.g. "今天感觉很不错！") matching other cards' style

### Exercise Card (add row)
```
┌──────────────────┐
│ [existing content]│
│ ─────────────────│
│ 🔥 今日消耗       │
│    320 千卡       │
│ 运动280 + 步数40  │
└──────────────────┘
```

### Diet Card (add rows)
```
┌──────────────────┐
│ [existing content]│
│ ─────────────────│
│ 🍚碳水  85g /250g│
│ ██████░░░░  34%  │
│ 🥩蛋白质 42g /60g │
│ ██████████  70%  │
└──────────────────┘
```
