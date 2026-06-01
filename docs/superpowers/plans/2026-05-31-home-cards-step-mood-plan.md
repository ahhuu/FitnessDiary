# Home Page Step Card, Mood Card, Exercise Calories & Diet Macros — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add step tracking card, daily mood card, exercise calorie burn display, and diet carb/protein display to the CheckInFragment home page.

**Architecture:** Follows existing MVVM pattern — new Room entities (StepRecord, MoodRecord) → DAOs → Repositories → HomeDashboardViewModel → CheckInFragment. Exercise calories are computed in-fragment from TrainingPlan + DailyLog data. Diet macros reuse existing DietViewModel LiveData. Step sensor uses Android TYPE_STEP_COUNTER registered in the fragment lifecycle.

**Tech Stack:** Java 17, Room 2.6.1, ViewBinding, Material Design 3, Android Sensor API

---

## File Structure

### New files (create):
| File | Purpose |
|------|---------|
| `database/entity/StepRecord.java` | Step record entity |
| `database/entity/MoodRecord.java` | Mood record entity |
| `database/dao/StepRecordDao.java` | Step CRUD + date queries |
| `database/dao/MoodRecordDao.java` | Mood CRUD + date queries |
| `repository/StepRecordRepository.java` | Step data access |
| `repository/MoodRecordRepository.java` | Mood data access |
| `utils/StepSensorHelper.java` | TYPE_STEP_COUNTER listener, baseline management |
| `ui/fragment/MoodPickerBottomSheet.java` | 5-emoji mood selection bottom sheet |
| `res/layout/view_home_card_step.xml` | Step card layout |
| `res/layout/view_home_card_mood.xml` | Mood card layout |
| `res/layout/bottom_sheet_mood_picker.xml` | Mood picker bottom sheet |
| `res/drawable/bg_card_3d_step.xml` | Step card 3D background |
| `res/drawable/bg_card_3d_mood.xml` | Mood card 3D background |
| `res/drawable/ic_hero_step.xml` | Step card icon (footprint) |
| `res/drawable/ic_hero_mood.xml` | Mood card icon (heart) |

### Modified files:
| File | Changes |
|------|---------|
| `database/AppDatabase.java` | +2 entities, +2 DAOs, MIGRATION_21_22, version 22 |
| `repository/HomeDashboardRepository.java` | +StepRecordRepository, +MoodRecordRepository |
| `viewmodel/HomeDashboardViewModel.java` | +step/mood LiveData, +step target from SharedPrefs |
| `ui/adapter/EditCardsAdapter.java` | +CARD_STEP, +CARD_MOOD, +icon resolution |
| `ui/fragment/CheckInFragment.java` | +card keys, +cacheCards, +setupActions, +observeData, +loadCardConfig, +applyCardConfig, +showEditCardsDialog, +exercise calories, +diet macros |
| `res/layout/fragment_checkin.xml` | +step/mood card placeholders, +calorie row in sport card, +macro rows in diet card |

---

### Task 1: Create StepRecord Entity

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/database/entity/StepRecord.java`

- [ ] **Step 1: Write StepRecord entity**

```java
package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "step_record", indices = {@Index(value = "date", unique = true)})
public class StepRecord {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "date")
    private long date; // 当天0点时间戳

    @ColumnInfo(name = "steps")
    private int steps;

    @ColumnInfo(name = "source")
    private int source; // 0=sensor, 1=manual, 2=hybrid

    @ColumnInfo(name = "create_time")
    private long createTime;

    public StepRecord(long date, int steps, int source, long createTime) {
        this.date = date;
        this.steps = steps;
        this.source = source;
        this.createTime = createTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public int getSteps() { return steps; }
    public void setSteps(int steps) { this.steps = steps; }
    public int getSource() { return source; }
    public void setSource(int source) { this.source = source; }
    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/database/entity/StepRecord.java
git commit -m "feat: add StepRecord entity for daily step tracking"
```

---

### Task 2: Create MoodRecord Entity

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/database/entity/MoodRecord.java`

- [ ] **Step 1: Write MoodRecord entity**

```java
package com.cz.fitnessdiary.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "mood_record", indices = {@Index(value = "date", unique = true)})
public class MoodRecord {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "date")
    private long date; // 当天0点时间戳

    @ColumnInfo(name = "mood_code")
    private String moodCode; // HAPPY, NEUTRAL, SAD, IRRITABLE, ANXIOUS

    @ColumnInfo(name = "note")
    private String note;

    @ColumnInfo(name = "create_time")
    private long createTime;

    public MoodRecord(long date, String moodCode, String note, long createTime) {
        this.date = date;
        this.moodCode = moodCode;
        this.note = note;
        this.createTime = createTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public String getMoodCode() { return moodCode; }
    public void setMoodCode(String moodCode) { this.moodCode = moodCode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/database/entity/MoodRecord.java
git commit -m "feat: add MoodRecord entity for daily mood tracking"
```

---

### Task 3: Create StepRecordDao

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/database/dao/StepRecordDao.java`

- [ ] **Step 1: Write StepRecordDao**

```java
package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.StepRecord;

@Dao
public interface StepRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(StepRecord record);

    @Query("SELECT * FROM step_record WHERE date = :date LIMIT 1")
    StepRecord getByDateSync(long date);

    @Query("SELECT * FROM step_record WHERE date = :date LIMIT 1")
    LiveData<StepRecord> getByDate(long date);

    @Query("SELECT * FROM step_record ORDER BY date DESC LIMIT 7")
    LiveData<java.util.List<StepRecord>> getRecentWeek();
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/database/dao/StepRecordDao.java
git commit -m "feat: add StepRecordDao for step record queries"
```

---

### Task 4: Create MoodRecordDao

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/database/dao/MoodRecordDao.java`

- [ ] **Step 1: Write MoodRecordDao**

```java
package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.MoodRecord;

@Dao
public interface MoodRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(MoodRecord record);

    @Query("SELECT * FROM mood_record WHERE date = :date LIMIT 1")
    MoodRecord getByDateSync(long date);

    @Query("SELECT * FROM mood_record WHERE date = :date LIMIT 1")
    LiveData<MoodRecord> getByDate(long date);
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/database/dao/MoodRecordDao.java
git commit -m "feat: add MoodRecordDao for mood record queries"
```

---

### Task 5: Update AppDatabase (migration 21→22)

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/database/AppDatabase.java`

- [ ] **Step 1: Add new imports at top of AppDatabase.java**

After line 39 (`import com.cz.fitnessdiary.database.dao.MenstrualCycleDao;`), add:
```java
import com.cz.fitnessdiary.database.dao.StepRecordDao;
import com.cz.fitnessdiary.database.dao.MoodRecordDao;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.MoodRecord;
```

- [ ] **Step 2: Update @Database annotation — add StepRecord.class and MoodRecord.class, bump version to 22**

Replace lines 58-63:
```java
@Database(entities = { User.class, TrainingPlan.class, DailyLog.class, FoodRecord.class,
        FoodLibrary.class, ExerciseLibrary.class, SleepRecord.class, ChatMessageEntity.class,
        ChatSessionEntity.class, WeightRecord.class, WaterRecord.class, MedicationRecord.class, CustomTracker.class,
        CustomRecord.class, ReminderSchedule.class, HabitItem.class,
        HabitRecord.class, BodyMeasurement.class, BowelMovement.class,
        MenstrualCycle.class, StepRecord.class, MoodRecord.class }, version = 22, exportSchema = true)
```

- [ ] **Step 3: Add DAO accessor methods**

After line 111 (`public abstract MenstrualCycleDao menstrualCycleDao();`), add:
```java
public abstract StepRecordDao stepRecordDao();
public abstract MoodRecordDao moodRecordDao();
```

- [ ] **Step 4: Add MIGRATION_21_22**

After line 470 (end of MIGRATION_20_21 block), add:
```java
static final Migration MIGRATION_21_22 = new Migration(21, 22) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `step_record` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`steps` INTEGER NOT NULL DEFAULT 0, " +
                "`source` INTEGER NOT NULL DEFAULT 0, " +
                "`create_time` INTEGER NOT NULL)");
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_step_record_date` ON `step_record` (`date`)");

        database.execSQL("CREATE TABLE IF NOT EXISTS `mood_record` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`mood_code` TEXT, " +
                "`note` TEXT, " +
                "`create_time` INTEGER NOT NULL)");
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mood_record_date` ON `mood_record` (`date`)");
    }
};
```

- [ ] **Step 5: Add MIGRATION_21_22 to the migration chain**

Replace lines 483-487 (the `.addMigrations(...)` chain), adding `MIGRATION_21_22`:
```java
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
        MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
        MIGRATION_20_21, MIGRATION_21_22)
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/database/AppDatabase.java
git commit -m "feat: add StepRecord and MoodRecord to DB with migration 21→22"
```

---

### Task 6: Create StepRecordRepository

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/repository/StepRecordRepository.java`

- [ ] **Step 1: Write StepRecordRepository**

```java
package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.StepRecordDao;
import com.cz.fitnessdiary.database.entity.StepRecord;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StepRecordRepository {

    private final StepRecordDao dao;
    private final ExecutorService executorService;

    public StepRecordRepository(Application application) {
        dao = AppDatabase.getInstance(application).stepRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insertOrUpdate(StepRecord record) {
        executorService.execute(() -> dao.insertOrUpdate(record));
    }

    public StepRecord getByDateSync(long date) {
        return dao.getByDateSync(date);
    }

    public LiveData<StepRecord> getByDate(long date) {
        return dao.getByDate(date);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/repository/StepRecordRepository.java
git commit -m "feat: add StepRecordRepository"
```

---

### Task 7: Create MoodRecordRepository

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/repository/MoodRecordRepository.java`

- [ ] **Step 1: Write MoodRecordRepository**

```java
package com.cz.fitnessdiary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.MoodRecordDao;
import com.cz.fitnessdiary.database.entity.MoodRecord;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MoodRecordRepository {

    private final MoodRecordDao dao;
    private final ExecutorService executorService;

    public MoodRecordRepository(Application application) {
        dao = AppDatabase.getInstance(application).moodRecordDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insertOrUpdate(MoodRecord record) {
        executorService.execute(() -> dao.insertOrUpdate(record));
    }

    public MoodRecord getByDateSync(long date) {
        return dao.getByDateSync(date);
    }

    public LiveData<MoodRecord> getByDate(long date) {
        return dao.getByDate(date);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/repository/MoodRecordRepository.java
git commit -m "feat: add MoodRecordRepository"
```

---

### Task 8: Create drawable resources (icons + 3D backgrounds)

**Files:**
- Create: `app/src/main/res/drawable/ic_hero_step.xml`
- Create: `app/src/main/res/drawable/ic_hero_mood.xml`
- Create: `app/src/main/res/drawable/bg_card_3d_step.xml`
- Create: `app/src/main/res/drawable/bg_card_3d_mood.xml`

- [ ] **Step 1: Write ic_hero_step.xml (footprint icon)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- Footprint -->
    <path
        android:fillColor="#FF7043"
        android:pathData="M12,3c-2.2,0 -4,1.8 -4,4 0,1.1 0.4,2.1 1.2,2.8l-1.7,8.2h2l1,-5 1,5h2l-1.7,-8.2c0.8,-0.7 1.2,-1.7 1.2,-2.8 0,-2.2 -1.8,-4 -4,-4zM12,5c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2z"/>
    <path
        android:fillColor="#BF360C"
        android:pathData="M6,18c-0.8,0 -1.5,0.7 -1.5,1.5v1.5c0,0.6 0.4,1 1,1h3v-4H6zM18,18h-2.5v4h3c0.6,0 1,-0.4 1,-1v-1.5c0,-0.8 -0.7,-1.5 -1.5,-1.5z"/>
</vector>
```

- [ ] **Step 2: Write ic_hero_mood.xml (heart icon)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#E91E63"
        android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"/>
</vector>
```

- [ ] **Step 3: Write bg_card_3d_step.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:left="1.5dp" android:top="1.5dp" android:right="0dp" android:bottom="0dp">
        <shape android:shape="rectangle">
            <corners android:radius="24dp"/>
            <solid android:color="#C5C0B8"/>
        </shape>
    </item>
    <item android:left="0dp" android:top="0dp" android:right="1.5dp" android:bottom="1.5dp">
        <shape android:shape="rectangle">
            <corners android:radius="24dp"/>
            <solid android:color="#FFFFFF"/>
        </shape>
    </item>
    <item android:left="1.5dp" android:top="1.5dp" android:right="1.5dp" android:bottom="1.5dp">
        <shape android:shape="rectangle">
            <corners android:radius="22.5dp"/>
            <gradient
                android:startColor="#FFF8F0"
                android:endColor="#FFE8D0"
                android:angle="315"
                android:type="linear"/>
        </shape>
    </item>
</layer-list>
```

- [ ] **Step 4: Write bg_card_3d_mood.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:left="1.5dp" android:top="1.5dp" android:right="0dp" android:bottom="0dp">
        <shape android:shape="rectangle">
            <corners android:radius="24dp"/>
            <solid android:color="#D0C0CC"/>
        </shape>
    </item>
    <item android:left="0dp" android:top="0dp" android:right="1.5dp" android:bottom="1.5dp">
        <shape android:shape="rectangle">
            <corners android:radius="24dp"/>
            <solid android:color="#FFFFFF"/>
        </shape>
    </item>
    <item android:left="1.5dp" android:top="1.5dp" android:right="1.5dp" android:bottom="1.5dp">
        <shape android:shape="rectangle">
            <corners android:radius="22.5dp"/>
            <gradient
                android:startColor="#FDF5FA"
                android:endColor="#F0DAEA"
                android:angle="315"
                android:type="linear"/>
        </shape>
    </item>
</layer-list>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_hero_step.xml app/src/main/res/drawable/ic_hero_mood.xml app/src/main/res/drawable/bg_card_3d_step.xml app/src/main/res/drawable/bg_card_3d_mood.xml
git commit -m "feat: add step and mood drawable resources (icons, 3D backgrounds)"
```

---

### Task 9: Create Step Card Layout

**Files:**
- Create: `app/src/main/res/layout/view_home_card_step.xml`

- [ ] **Step 1: Write view_home_card_step.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/card_step"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="140dp"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    app:cardBackgroundColor="@android:color/transparent"
    app:cardCornerRadius="24dp"
    app:cardElevation="3dp"
    app:strokeWidth="0dp"
    android:outlineSpotShadowColor="#1A4E483F"
    android:outlineAmbientShadowColor="#1A4E483F">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="@drawable/bg_card_3d_step"
        android:padding="14dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageView
                android:id="@+id/iv_step_icon"
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_hero_step"
                app:tint="#FF7043" />

            <TextView
                android:id="@+id/tv_step_title"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:maxLines="1"
                android:ellipsize="end"
                android:text="步数"
                android:textColor="@color/text_primary"
                android:textSize="15sp"
                android:textStyle="bold" />

            <ImageButton
                android:id="@+id/btn_add_step"
                android:layout_width="30dp"
                android:layout_height="30dp"
                android:background="@android:color/transparent"
                android:foreground="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="添加步数"
                android:src="@drawable/ic_add"
                app:tint="#FF7043" />
        </LinearLayout>

        <TextView
            android:id="@+id/tv_step_update"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:maxLines="1"
            android:ellipsize="end"
            android:text="暂无更新"
            android:textColor="@color/text_secondary"
            android:textSize="11sp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:gravity="bottom"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tv_step_value"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="0"
                android:textColor="#FF7043"
                android:textSize="22sp"
                android:textStyle="bold" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="4dp"
                android:text="步"
                android:textColor="@color/text_secondary"
                android:textSize="12sp" />
        </LinearLayout>

        <ProgressBar
            android:id="@+id/progress_step"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="match_parent"
            android:layout_height="4dp"
            android:layout_marginTop="8dp"
            android:progressDrawable="@drawable/gradient_progress_step"
            android:max="100"
            android:progress="0" />

        <TextView
            android:id="@+id/tv_step_summary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:maxLines="1"
            android:ellipsize="end"
            android:text="目标 8000 步"
            android:textColor="@color/text_secondary"
            android:textSize="11sp" />
    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Create gradient_progress_step.xml drawable**

Create: `app/src/main/res/drawable/gradient_progress_step.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape>
            <corners android:radius="2dp"/>
            <solid android:color="#E0E0E0"/>
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape>
                <corners android:radius="2dp"/>
                <gradient
                    android:startColor="#FF7043"
                    android:endColor="#FF5722"
                    android:angle="0"/>
            </shape>
        </clip>
    </item>
</layer-list>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/view_home_card_step.xml app/src/main/res/drawable/gradient_progress_step.xml
git commit -m "feat: add step card layout and progress drawable"
```

---

### Task 10: Create Mood Card Layout

**Files:**
- Create: `app/src/main/res/layout/view_home_card_mood.xml`

- [ ] **Step 1: Write view_home_card_mood.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/card_mood"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="140dp"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    app:cardBackgroundColor="@android:color/transparent"
    app:cardCornerRadius="24dp"
    app:cardElevation="3dp"
    app:strokeWidth="0dp"
    android:outlineSpotShadowColor="#1A4E483F"
    android:outlineAmbientShadowColor="#1A4E483F">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="@drawable/bg_card_3d_mood"
        android:padding="14dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageView
                android:id="@+id/iv_mood_icon"
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:src="@drawable/ic_hero_mood"
                app:tint="#E91E63" />

            <TextView
                android:id="@+id/tv_mood_title"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:maxLines="1"
                android:ellipsize="end"
                android:text="情绪"
                android:textColor="@color/text_primary"
                android:textSize="15sp"
                android:textStyle="bold" />
        </LinearLayout>

        <TextView
            android:id="@+id/tv_mood_update"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:maxLines="1"
            android:ellipsize="end"
            android:text="暂无更新"
            android:textColor="@color/text_secondary"
            android:textSize="11sp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:gravity="center"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tv_mood_emoji"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="—"
                android:textSize="36sp"
                android:gravity="center" />

            <TextView
                android:id="@+id/tv_mood_name"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="点击记录"
                android:textColor="@color/text_secondary"
                android:textSize="14sp" />
        </LinearLayout>

        <TextView
            android:id="@+id/tv_mood_summary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:maxLines="1"
            android:ellipsize="end"
            android:text="记录每日心情"
            android:textColor="@color/text_secondary"
            android:textSize="11sp" />
    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/layout/view_home_card_mood.xml
git commit -m "feat: add mood card layout"
```

---

### Task 11: Create MoodPickerBottomSheet

**Files:**
- Create: `app/src/main/res/layout/bottom_sheet_mood_picker.xml`
- Create: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/MoodPickerBottomSheet.java`

- [ ] **Step 1: Write bottom_sheet_mood_picker.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp"
    android:gravity="center">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="今天感觉如何？"
        android:textColor="@color/text_primary"
        android:textSize="18sp"
        android:textStyle="bold"
        android:layout_marginBottom="20dp" />

    <LinearLayout
        android:id="@+id/layout_mood_options"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:orientation="horizontal">

        <!-- Mood option template: repeated 5 times in Java code -->
    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_mood_done"
        android:layout_width="wrap_content"
        android:layout_height="48dp"
        android:layout_marginTop="20dp"
        android:text="完成"
        app:cornerRadius="24dp"
        style="@style/Widget.Material3.Button.TonalButton" />
</LinearLayout>
```

- [ ] **Step 2: Write MoodPickerBottomSheet.java**

```java
package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.LinkedHashMap;
import java.util.Map;

public class MoodPickerBottomSheet extends BottomSheetDialogFragment {

    private static final Map<String, String[]> MOOD_MAP = new LinkedHashMap<>();

    static {
        MOOD_MAP.put("HAPPY", new String[]{"😊", "开心"});
        MOOD_MAP.put("NEUTRAL", new String[]{"😐", "一般"});
        MOOD_MAP.put("SAD", new String[]{"😢", "低落"});
        MOOD_MAP.put("IRRITABLE", new String[]{"😡", "烦躁"});
        MOOD_MAP.put("ANXIOUS", new String[]{"😰", "焦虑"});
    }

    public interface OnMoodSelectedListener {
        void onMoodSelected(String moodCode);
    }

    private OnMoodSelectedListener listener;
    private String currentMoodCode;

    public static MoodPickerBottomSheet newInstance(String currentMoodCode) {
        MoodPickerBottomSheet sheet = new MoodPickerBottomSheet();
        Bundle args = new Bundle();
        args.putString("current_mood", currentMoodCode);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnMoodSelectedListener(OnMoodSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_mood_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            currentMoodCode = getArguments().getString("current_mood");
        }

        LinearLayout optionsLayout = view.findViewById(R.id.layout_mood_options);

        for (Map.Entry<String, String[]> entry : MOOD_MAP.entrySet()) {
            String code = entry.getKey();
            String emoji = entry.getValue()[0];
            String name = entry.getValue()[1];

            LinearLayout item = new LinearLayout(requireContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(android.view.Gravity.CENTER);
            item.setPadding(dp(10), dp(6), dp(10), dp(6));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            item.setLayoutParams(lp);

            TextView tvEmoji = new TextView(requireContext());
            tvEmoji.setText(emoji);
            tvEmoji.setTextSize(28);
            tvEmoji.setGravity(android.view.Gravity.CENTER);

            TextView tvName = new TextView(requireContext());
            tvName.setText(name);
            tvName.setTextSize(11);
            tvName.setTextColor(requireContext().getResources().getColor(R.color.text_secondary, null));
            tvName.setGravity(android.view.Gravity.CENTER);

            boolean isSelected = code.equals(currentMoodCode);
            tvEmoji.setAlpha(isSelected ? 1.0f : 0.4f);
            tvName.setAlpha(isSelected ? 1.0f : 0.4f);

            item.setOnClickListener(v -> {
                currentMoodCode = code;
                if (listener != null) {
                    listener.onMoodSelected(code);
                }
                dismiss();
            });

            item.addView(tvEmoji);
            item.addView(tvName);
            optionsLayout.addView(item);
        }

        view.findViewById(R.id.btn_mood_done).setOnClickListener(v -> dismiss());
    }

    private int dp(int x) {
        return Math.round(x * requireContext().getResources().getDisplayMetrics().density);
    }

    public static String getMoodEmoji(String code) {
        String[] pair = MOOD_MAP.get(code);
        return pair != null ? pair[0] : "—";
    }

    public static String getMoodName(String code) {
        String[] pair = MOOD_MAP.get(code);
        return pair != null ? pair[1] : "点击记录";
    }

    public static String getMoodSummary(String code) {
        switch (code != null ? code : "") {
            case "HAPPY": return "今天感觉很不错！";
            case "NEUTRAL": return "平平淡淡才是真";
            case "SAD": return "抱抱，明天会更好";
            case "IRRITABLE": return "深呼吸，放轻松";
            case "ANXIOUS": return "别担心，一切都会好的";
            default: return "记录每日心情";
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/bottom_sheet_mood_picker.xml app/src/main/java/com/cz/fitnessdiary/ui/fragment/MoodPickerBottomSheet.java
git commit -m "feat: add MoodPickerBottomSheet with 5-emoji picker"
```

---

### Task 12: Create StepSensorHelper

**Files:**
- Create: `app/src/main/java/com/cz/fitnessdiary/utils/StepSensorHelper.java`

- [ ] **Step 1: Write StepSensorHelper**

```java
package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.StepRecord;

import java.util.concurrent.Executors;

/**
 * Helper to read Android TYPE_STEP_COUNTER sensor.
 * Stores baseline offset in SharedPreferences to compute daily steps.
 */
public class StepSensorHelper implements SensorEventListener {

    private static final String PREF_NAME = "step_sensor_prefs";
    private static final String KEY_BASELINE = "sensor_baseline";
    private static final String KEY_BASELINE_DATE = "baseline_date";

    private final Context context;
    private final SensorManager sensorManager;
    private final Sensor stepSensor;
    private boolean running;
    private StepUpdateCallback callback;

    public interface StepUpdateCallback {
        void onStepsUpdated(int todaySteps);
    }

    public StepSensorHelper(Context context) {
        this.context = context.getApplicationContext();
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.stepSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
    }

    public boolean isSensorAvailable() {
        return stepSensor != null;
    }

    public void setCallback(StepUpdateCallback callback) {
        this.callback = callback;
    }

    public void start() {
        if (stepSensor == null || running) return;
        running = true;
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void stop() {
        if (!running) return;
        running = false;
        sensorManager.unregisterListener(this);
    }

    /**
     * Read the stored baseline and compute today's sensor-step count.
     * Returns 0 if sensor is unavailable or baseline not yet set.
     */
    public int getTodaySensorSteps() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long baseline = sp.getLong(KEY_BASELINE, -1);
        long baselineDate = sp.getLong(KEY_BASELINE_DATE, 0);
        long today = DateUtils.getTodayStartTimestamp();

        if (baseline < 0) return 0;

        // Reset baseline if it's a new day
        if (baselineDate < today) {
            // The baseline is outdated; we need a new reading
            return 0;
        }

        return 0; // Will be filled from onSensorChanged
    }

    /**
     * Save the current sensor baseline for today.
     */
    public void snapshotAndSaveBaseline(float totalStepsSinceBoot) {
        long today = DateUtils.getTodayStartTimestamp();
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putLong(KEY_BASELINE, (long) totalStepsSinceBoot)
                .putLong(KEY_BASELINE_DATE, today)
                .apply();
    }

    /**
     * Get today's steps from sensor, computing (current - baseline).
     */
    public int resolveTodaySteps(float totalStepsSinceBoot) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long baseline = sp.getLong(KEY_BASELINE, -1);
        long baselineDate = sp.getLong(KEY_BASELINE_DATE, 0);
        long today = DateUtils.getTodayStartTimestamp();

        if (baseline < 0 || baselineDate < today) {
            // First reading today — set baseline
            snapshotAndSaveBaseline(totalStepsSinceBoot);
            return 0;
        }

        int steps = (int) (totalStepsSinceBoot - baseline);
        return Math.max(0, steps);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int todaySteps = resolveTodaySteps(event.values[0]);

        // Persist to DB every ~100 steps or on significant change
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                long today = DateUtils.getTodayStartTimestamp();
                StepRecord existing = AppDatabase.getInstance(context).stepRecordDao().getByDateSync(today);
                if (existing != null) {
                    // Only update if sensor is the primary source (source=0 or 2)
                    if (existing.getSource() == 0 || existing.getSource() == 2) {
                        existing.setSteps(todaySteps);
                        existing.setSource(0);
                        existing.setCreateTime(System.currentTimeMillis());
                        AppDatabase.getInstance(context).stepRecordDao().insertOrUpdate(existing);
                    }
                } else if (todaySteps > 0) {
                    StepRecord record = new StepRecord(today, todaySteps, 0, System.currentTimeMillis());
                    AppDatabase.getInstance(context).stepRecordDao().insertOrUpdate(record);
                }
            } catch (Exception ignored) {}
        });

        if (callback != null) {
            callback.onStepsUpdated(todaySteps);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/utils/StepSensorHelper.java
git commit -m "feat: add StepSensorHelper for TYPE_STEP_COUNTER integration"
```

---

### Task 13: Update HomeDashboardRepository

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/repository/HomeDashboardRepository.java`

- [ ] **Step 1: Add StepRecordRepository and MoodRecordRepository fields**

After line 31 (`private final MenstrualCycleRepository menstrualCycleRepository;`), add:
```java
private final StepRecordRepository stepRecordRepository;
private final MoodRecordRepository moodRecordRepository;
```

- [ ] **Step 2: Initialize in constructor**

After line 42 (`this.menstrualCycleRepository = new MenstrualCycleRepository(application);`), add:
```java
this.stepRecordRepository = new StepRecordRepository(application);
this.moodRecordRepository = new MoodRecordRepository(application);
```

- [ ] **Step 3: Add import statements**

After line 16 (`import com.cz.fitnessdiary.database.entity.MenstrualCycle;`), add:
```java
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.MoodRecord;
```

- [ ] **Step 4: Add step and mood query methods at end of class (before closing `}`)**

```java
// ── Step Record ──

public LiveData<StepRecord> getStepByDate(long date) {
    return stepRecordRepository.getByDate(date);
}

public void insertOrUpdateStep(StepRecord record) {
    stepRecordRepository.insertOrUpdate(record);
}

public StepRecord getStepByDateSync(long date) {
    return stepRecordRepository.getByDateSync(date);
}

// ── Mood Record ──

public LiveData<MoodRecord> getMoodByDate(long date) {
    return moodRecordRepository.getByDate(date);
}

public void insertOrUpdateMood(MoodRecord record) {
    moodRecordRepository.insertOrUpdate(record);
}

public MoodRecord getMoodByDateSync(long date) {
    return moodRecordRepository.getByDateSync(date);
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/repository/HomeDashboardRepository.java
git commit -m "feat: add step and mood data access to HomeDashboardRepository"
```

---

### Task 14: Update HomeDashboardViewModel

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/viewmodel/HomeDashboardViewModel.java`

- [ ] **Step 1: Add imports**

After line 18 (`import com.cz.fitnessdiary.database.entity.WeightRecord;`), add:
```java
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.MoodRecord;
import android.content.SharedPreferences;
```

- [ ] **Step 2: Add step/mood LiveData and step target methods**

After line 242 (end of existing menstrual methods, before the `buildRecordTimestampForSelectedDate` method), add:

```java
// ── Step card data ──

public LiveData<StepRecord> getTodayStep() {
    return Transformations.switchMap(dayStart, date -> repository.getStepByDate(date));
}

public void setTodaySteps(int steps, int source) {
    new Thread(() -> {
        long today = DateUtils.getTodayStartTimestamp();
        StepRecord existing = repository.getStepByDateSync(today);
        if (existing != null) {
            existing.setSteps(steps);
            existing.setSource(source);
            existing.setCreateTime(System.currentTimeMillis());
            repository.insertOrUpdateStep(existing);
        } else {
            repository.insertOrUpdateStep(new StepRecord(today, steps, source, System.currentTimeMillis()));
        }
    }).start();
}

public int getStepTarget() {
    SharedPreferences sp = getApplication().getSharedPreferences("fitness_diary_prefs", android.content.Context.MODE_PRIVATE);
    return sp.getInt("step_target", 8000);
}

public void setStepTarget(int target) {
    getApplication().getSharedPreferences("fitness_diary_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putInt("step_target", target).apply();
}

public LiveData<Integer> getTodayStepCalories() {
    return Transformations.map(getTodayStep(), step -> {
        if (step == null || step.getSteps() <= 0) return 0;
        return (int) (step.getSteps() * 0.04);
    });
}

// ── Mood card data ──

public LiveData<MoodRecord> getTodayMood() {
    return Transformations.switchMap(dayStart, date -> repository.getMoodByDate(date));
}

public void setTodayMood(String moodCode) {
    new Thread(() -> {
        long today = DateUtils.getTodayStartTimestamp();
        MoodRecord record = new MoodRecord(today, moodCode, null, System.currentTimeMillis());
        repository.insertOrUpdateMood(record);
    }).start();
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/viewmodel/HomeDashboardViewModel.java
git commit -m "feat: add step and mood LiveData + step target to HomeDashboardViewModel"
```

---

### Task 15: Update EditCardsAdapter

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/adapter/EditCardsAdapter.java`

- [ ] **Step 1: Add CARD_STEP and CARD_MOOD icon resolutions**

In the `resolveIconRes` method (line 76), after the `menstrual` case (line 99), add:
```java
if ("step".equals(id)) {
    return R.drawable.ic_hero_step;
}
if ("mood".equals(id)) {
    return R.drawable.ic_hero_mood;
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/adapter/EditCardsAdapter.java
git commit -m "feat: add step and mood card icons to EditCardsAdapter"
```

---

### Task 16: Update fragment_checkin.xml (add card placeholders, exercise calorie row, diet macro rows)

**Files:**
- Modify: `app/src/main/res/layout/fragment_checkin.xml`

- [ ] **Step 1: Add exercise calorie row to sport card**

After the sport ProgressBar (line 419) and before the closing `</LinearLayout>` (line 420), add:
```xml
<!-- 今日消耗 -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="10dp"
    android:gravity="center_vertical"
    android:orientation="horizontal">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="🔥"
        android:textSize="14sp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="4dp"
        android:text="今日消耗"
        android:textColor="@color/text_secondary"
        android:textSize="12sp" />

    <TextView
        android:id="@+id/tv_sport_calories"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="end"
        android:text="0 千卡"
        android:textColor="#FF7043"
        android:textSize="14sp"
        android:textStyle="bold" />
</LinearLayout>

<TextView
    android:id="@+id/tv_sport_cal_breakdown"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="2dp"
    android:text=""
    android:textColor="@color/text_hint"
    android:textSize="11sp" />
```

- [ ] **Step 2: Add diet macro rows to diet card**

After the diet ProgressBar (line 496) and before the closing `</LinearLayout>` (line 497), add:
```xml
<!-- 碳水 -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="10dp"
    android:gravity="center_vertical"
    android:orientation="horizontal">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="🍚碳水"
        android:textColor="@color/text_secondary"
        android:textSize="12sp" />

    <TextView
        android:id="@+id/tv_diet_carbs"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="6dp"
        android:layout_weight="1"
        android:text="0g"
        android:textColor="@color/text_primary"
        android:textSize="12sp" />

    <ProgressBar
        android:id="@+id/progress_diet_carbs"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="80dp"
        android:layout_height="3dp"
        android:progressDrawable="@drawable/gradient_progress_carbs"
        android:max="100"
        android:progress="0" />
</LinearLayout>

<!-- 蛋白质 -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="6dp"
    android:gravity="center_vertical"
    android:orientation="horizontal">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="🥩蛋白质"
        android:textColor="@color/text_secondary"
        android:textSize="12sp" />

    <TextView
        android:id="@+id/tv_diet_protein"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="6dp"
        android:layout_weight="1"
        android:text="0g"
        android:textColor="@color/text_primary"
        android:textSize="12sp" />

    <ProgressBar
        android:id="@+id/progress_diet_protein"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="80dp"
        android:layout_height="3dp"
        android:progressDrawable="@drawable/gradient_progress_protein"
        android:max="100"
        android:progress="0" />
</LinearLayout>
```

- [ ] **Step 3: Add step and mood card placeholders in GridLayout**

After the menstrual card FrameLayout (line 537-538), add:
```xml
<FrameLayout android:layout_width="0dp" android:layout_height="wrap_content" app:layout_columnWeight="1" android:layout_marginEnd="8dp" android:layout_marginBottom="16dp">
    <include layout="@layout/view_home_card_step" />
</FrameLayout>

<FrameLayout android:layout_width="0dp" android:layout_height="wrap_content" app:layout_columnWeight="1" android:layout_marginStart="8dp" android:layout_marginBottom="16dp">
    <include layout="@layout/view_home_card_mood" />
</FrameLayout>
```

- [ ] **Step 4: Create gradient_progress_carbs.xml**

Create: `app/src/main/res/drawable/gradient_progress_carbs.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape><corners android:radius="1.5dp"/><solid android:color="#E8E8E8"/></shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape><corners android:radius="1.5dp"/>
                <gradient android:startColor="#FFB74D" android:endColor="#FF9800" android:angle="0"/>
            </shape>
        </clip>
    </item>
</layer-list>
```

- [ ] **Step 5: Create gradient_progress_protein.xml**

Create: `app/src/main/res/drawable/gradient_progress_protein.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape><corners android:radius="1.5dp"/><solid android:color="#E8E8E8"/></shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape><corners android:radius="1.5dp"/>
                <gradient android:startColor="#EF5350" android:endColor="#C62828" android:angle="0"/>
            </shape>
        </clip>
    </item>
</layer-list>
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/fragment_checkin.xml app/src/main/res/drawable/gradient_progress_carbs.xml app/src/main/res/drawable/gradient_progress_protein.xml
git commit -m "feat: add step/mood card placeholders, exercise calorie row, diet macro rows to layout"
```

---

### Task 17: Update CheckInFragment — card constants, cacheCards, setupActions

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java`

- [ ] **Step 1: Add CARD_STEP and CARD_MOOD constants and preference keys**

After line 73 (`private static final String CARD_MENSTRUAL = "menstrual";`), add:
```java
private static final String CARD_STEP = "step";
private static final String CARD_MOOD = "mood";
```

After line 63 (`private static final String KEY_SHOW_MENSTRUAL = "show_menstrual";`), add:
```java
private static final String KEY_SHOW_STEP = "show_step";
private static final String KEY_SHOW_MOOD = "show_mood";
```

- [ ] **Step 2: Add StepSensorHelper field**

After line 93 (`private int timerSelectedSeconds = 60;`), add:
```java
private com.cz.fitnessdiary.utils.StepSensorHelper stepSensorHelper;
```

- [ ] **Step 3: Add step/mood cards to cacheCards**

After line 223 (`cachedCards.put(CARD_MENSTRUAL, pool.findViewById(R.id.card_menstrual_small));`), add:
```java
cachedCards.put(CARD_STEP, pool.findViewById(R.id.card_step));
cachedCards.put(CARD_MOOD, pool.findViewById(R.id.card_mood));
```

- [ ] **Step 4: Add diet macros observation in cacheCards**

After the existing diet observation block (after line 249 — end of `updateOverallProgress();`), add:
```java
// 饮食宏量营养素观察
dietViewModel.getTodayTotalCarbs().observe(getViewLifecycleOwner(), carbs -> {
    updateDietMacros();
});

dietViewModel.getTodayTotalProtein().observe(getViewLifecycleOwner(), protein -> {
    updateDietMacros();
});

dietViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
    updateDietMacros();
});
```

- [ ] **Step 5: Add step and mood card click listeners in setupActions**

After line 300 (the menstrual card click listener), add:
```java
v(R.id.card_step).setOnClickListener(v -> showStepInputDialog());
v(R.id.card_step).setOnLongClickListener(v -> { showStepTargetDialog(); return true; });
v(R.id.card_mood).setOnClickListener(v -> showMoodPicker());

v(R.id.btn_add_step).setOnClickListener(v -> showStepInputDialog());
```

- [ ] **Step 6: Initialize StepSensorHelper in onViewCreated**

After line 114 (`restTimerManager = new RestTimerManager();`), add:
```java
stepSensorHelper = new com.cz.fitnessdiary.utils.StepSensorHelper(requireContext());
```

- [ ] **Step 7: Start/stop sensor in onResume/onPause**

In the existing `onResume()` method (line 128), after the existing code block, add:
```java
if (stepSensorHelper != null && stepSensorHelper.isSensorAvailable()) {
    stepSensorHelper.start();
}
```

Add a new `onPause()` override after `onResume()`:
```java
@Override
public void onPause() {
    super.onPause();
    if (stepSensorHelper != null) {
        stepSensorHelper.stop();
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java
git commit -m "feat: add step/mood card constants, cache, click handlers to CheckInFragment"
```

---

### Task 18: Update CheckInFragment — step/mood observeData, loadCardConfig, applyCardConfig, showEditCardsDialog

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java`

- [ ] **Step 1: Add step and mood LiveData observers at end of observeData()**

After the menstrual observation block (line 894 — end of `observeData()`), add:
```java
// 步数卡片观察
homeDashboardViewModel.getTodayStep().observe(getViewLifecycleOwner(), step -> {
    if (step != null && step.getSteps() > 0) {
        int steps = step.getSteps();
        setTextIfExists(R.id.tv_step_value, String.valueOf(steps));
        setTextIfExists(R.id.tv_step_update, getSelectedDateUpdateText(step.getCreateTime()));
        int target = homeDashboardViewModel.getStepTarget();
        int pct = target > 0 ? Math.min(steps * 100 / target, 100) : 0;
        int cal = (int) (steps * 0.04);
        setTextIfExists(R.id.tv_step_summary,
                "≈" + cal + "千卡 | 目标" + target + "步");
        View card = cachedCards.get(CARD_STEP);
        if (card != null) {
            android.widget.ProgressBar p = card.findViewById(R.id.progress_step);
            if (p != null) p.setProgress(pct);
        }
    } else {
        setTextIfExists(R.id.tv_step_value, "0");
        setTextIfExists(R.id.tv_step_update, "暂无更新");
        int target = homeDashboardViewModel.getStepTarget();
        setTextIfExists(R.id.tv_step_summary, "目标 " + target + " 步");
        View card = cachedCards.get(CARD_STEP);
        if (card != null) {
            android.widget.ProgressBar p = card.findViewById(R.id.progress_step);
            if (p != null) p.setProgress(0);
        }
    }
});

// 情绪卡片观察
homeDashboardViewModel.getTodayMood().observe(getViewLifecycleOwner(), mood -> {
    if (mood != null && mood.getMoodCode() != null) {
        String emoji = MoodPickerBottomSheet.getMoodEmoji(mood.getMoodCode());
        String name = MoodPickerBottomSheet.getMoodName(mood.getMoodCode());
        String summary = MoodPickerBottomSheet.getMoodSummary(mood.getMoodCode());
        setTextIfExists(R.id.tv_mood_emoji, emoji);
        setTextIfExists(R.id.tv_mood_name, name);
        setTextIfExists(R.id.tv_mood_summary, summary);
        setTextIfExists(R.id.tv_mood_update, getSelectedDateUpdateText(mood.getCreateTime()));
    } else {
        setTextIfExists(R.id.tv_mood_emoji, "—");
        setTextIfExists(R.id.tv_mood_name, "点击记录");
        setTextIfExists(R.id.tv_mood_summary, "记录每日心情");
        setTextIfExists(R.id.tv_mood_update, "暂无更新");
    }
});
```

- [ ] **Step 2: Add computeAndDisplayExerciseCalories() call to refreshSportCard()**

In `refreshSportCard()` (around line 1273), add at the end of the method (before the closing `}`):
```java
computeAndDisplayExerciseCalories();
```

- [ ] **Step 3: Add updateDietMacros() method**

After the existing `updateDietProgress()` method (around line 265), add:
```java
private void updateDietMacros() {
    Double carbs = dietViewModel.getTodayTotalCarbs().getValue();
    Double protein = dietViewModel.getTodayTotalProtein().getValue();
    User user = dietViewModel.getCurrentUser().getValue();

    int carbsTarget = (user != null && user.getTargetCarbs() > 0) ? user.getTargetCarbs() : 250;
    int proteinTarget = (user != null && user.getTargetProtein() > 0) ? user.getTargetProtein() : 60;

    double carbsVal = carbs != null ? carbs : 0;
    double proteinVal = protein != null ? protein : 0;

    TextView tvCarbs = binding.getRoot().findViewById(R.id.tv_diet_carbs);
    if (tvCarbs != null) {
        tvCarbs.setText((int) carbsVal + "g / " + carbsTarget + "g");
    }
    ProgressBar pCarbs = binding.getRoot().findViewById(R.id.progress_diet_carbs);
    if (pCarbs != null) {
        pCarbs.setProgress(Math.min((int) (carbsVal * 100 / carbsTarget), 100));
    }

    TextView tvProtein = binding.getRoot().findViewById(R.id.tv_diet_protein);
    if (tvProtein != null) {
        tvProtein.setText((int) proteinVal + "g / " + proteinTarget + "g");
    }
    ProgressBar pProtein = binding.getRoot().findViewById(R.id.progress_diet_protein);
    if (pProtein != null) {
        pProtein.setProgress(Math.min((int) (proteinVal * 100 / proteinTarget), 100));
    }
}
```

- [ ] **Step 4: Add computeAndDisplayExerciseCalories() method**

After `updateDietMacros()`, add:
```java
private void computeAndDisplayExerciseCalories() {
    new Thread(() -> {
        int totalCal = 0;
        // Workout calories
        if (!currentPlans.isEmpty()) {
            java.util.HashSet<Integer> donePlanIds = new java.util.HashSet<>();
            for (DailyLog log : currentLogs) {
                if (log.isCompleted()) donePlanIds.add(log.getPlanId());
            }
            // Get user weight
            float weightKg = 70f;
            try {
                com.cz.fitnessdiary.database.AppDatabase db =
                        com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
                com.cz.fitnessdiary.database.entity.WeightRecord latestW =
                        db.weightRecordDao().getLatestRecordSync();
                if (latestW != null && latestW.getWeight() > 0) weightKg = latestW.getWeight();
                else {
                    com.cz.fitnessdiary.database.entity.User u = db.userDao().getUserSync();
                    if (u != null && u.getWeight() > 0) weightKg = u.getWeight();
                }
            } catch (Exception ignored) {}

            for (TrainingPlan plan : currentPlans) {
                if (!donePlanIds.contains(plan.getPlanId())) continue;
                int durationSec = 0;
                for (DailyLog log : currentLogs) {
                    if (log.getPlanId() == plan.getPlanId() && log.isCompleted()) {
                        durationSec = log.getDuration() > 0 ? log.getDuration() : plan.getDuration();
                        break;
                    }
                }
                if (durationSec <= 0) durationSec = 1800; // default 30min
                double met = getMetForCategory(plan.getCategory());
                double cal = met * 3.5 * weightKg * durationSec / (200.0 * 60.0);
                totalCal += (int) cal;
            }
        }
        // Step calories
        long today = com.cz.fitnessdiary.utils.DateUtils.getTodayStartTimestamp();
        com.cz.fitnessdiary.database.entity.StepRecord step = null;
        try {
            com.cz.fitnessdiary.database.AppDatabase db =
                    com.cz.fitnessdiary.database.AppDatabase.getInstance(requireContext());
            step = db.stepRecordDao().getByDateSync(today);
        } catch (Exception ignored) {}
        int stepCal = 0;
        if (step != null && step.getSteps() > 0) {
            stepCal = (int) (step.getSteps() * 0.04);
            totalCal += stepCal;
        }

        int workoutCal = totalCal - stepCal;
        final int finalTotal = totalCal;
        final int finalWorkout = workoutCal;
        final int finalStepCal = stepCal;

        requireActivity().runOnUiThread(() -> {
            setTextIfExists(R.id.tv_sport_calories, finalTotal + " 千卡");
            if (finalWorkout > 0 && finalStepCal > 0) {
                setTextIfExists(R.id.tv_sport_cal_breakdown, "运动" + finalWorkout + " + 步数" + finalStepCal);
            } else if (finalWorkout > 0) {
                setTextIfExists(R.id.tv_sport_cal_breakdown, "运动消耗 " + finalWorkout + " 千卡");
            } else if (finalStepCal > 0) {
                setTextIfExists(R.id.tv_sport_cal_breakdown, "步数消耗 " + finalStepCal + " 千卡");
            } else {
                setTextIfExists(R.id.tv_sport_cal_breakdown, "");
            }
        });
    }).start();
}

private double getMetForCategory(String category) {
    if (category == null) return 4.0;
    String cat = category.toLowerCase();
    if (cat.contains("有氧") || cat.contains("cardio") || cat.contains("跑步") || cat.contains("骑行")) return 7.0;
    if (cat.contains("hiit")) return 8.0;
    if (cat.contains("瑜伽") || cat.contains("拉伸") || cat.contains("yoga")) return 2.5;
    if (cat.contains("力量") || cat.contains("strength")) return 3.5;
    return 4.0;
}
```

- [ ] **Step 5: Add step input dialog method**

After the existing `quickTextInput` method (line 374), add:
```java
private void showStepInputDialog() {
    android.widget.EditText et = new android.widget.EditText(requireContext());
    et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    et.setHint("输入今日步数");
    // Pre-fill with current value
    Integer currentStep = null;
    com.cz.fitnessdiary.database.entity.StepRecord existing = homeDashboardViewModel.getTodayStep().getValue();
    if (existing != null && existing.getSteps() > 0) {
        et.setText(String.valueOf(existing.getSteps()));
    }

    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("记录步数")
            .setView(et)
            .setPositiveButton("保存", (d, w) -> {
                try {
                    int steps = Integer.parseInt(et.getText().toString().trim());
                    int source = (existing != null && existing.getSource() == 0) ? 2 : 1;
                    homeDashboardViewModel.setTodaySteps(steps, source);
                    // Also refresh exercise calories
                    computeAndDisplayExerciseCalories();
                } catch (Exception e) {
                    android.widget.Toast.makeText(getContext(), "请输入正确数字", android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
}

private void showStepTargetDialog() {
    android.widget.EditText et = new android.widget.EditText(requireContext());
    et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    et.setHint("目标步数");
    et.setText(String.valueOf(homeDashboardViewModel.getStepTarget()));

    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("设置步数目标")
            .setView(et)
            .setPositiveButton("保存", (d, w) -> {
                try {
                    int target = Integer.parseInt(et.getText().toString().trim());
                    if (target > 0) {
                        homeDashboardViewModel.setStepTarget(target);
                        // Trigger refresh to update progress bar
                        com.cz.fitnessdiary.database.entity.StepRecord existing = homeDashboardViewModel.getTodayStep().getValue();
                        if (existing != null) {
                            int pct = Math.min(existing.getSteps() * 100 / target, 100);
                            setTextIfExists(R.id.tv_step_summary,
                                    "≈" + (int)(existing.getSteps() * 0.04) + "千卡 | 目标" + target + "步");
                            View card = cachedCards.get(CARD_STEP);
                            if (card != null) {
                                android.widget.ProgressBar p = card.findViewById(R.id.progress_step);
                                if (p != null) p.setProgress(pct);
                            }
                        } else {
                            setTextIfExists(R.id.tv_step_summary, "目标 " + target + " 步");
                        }
                    }
                } catch (Exception e) {
                    android.widget.Toast.makeText(getContext(), "请输入正确数字", android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
}

private void showMoodPicker() {
    com.cz.fitnessdiary.database.entity.MoodRecord existing = homeDashboardViewModel.getTodayMood().getValue();
    String currentCode = existing != null ? existing.getMoodCode() : null;
    MoodPickerBottomSheet sheet = MoodPickerBottomSheet.newInstance(currentCode);
    sheet.setOnMoodSelectedListener(code -> {
        homeDashboardViewModel.setTodayMood(code);
    });
    sheet.show(getParentFragmentManager(), "MOOD_PICKER");
}
```

- [ ] **Step 6: Update loadCardConfig() — add CARD_STEP and CARD_MOOD to default order**

In `loadCardConfig()`, update the default order string (line 1629) to include step and mood. Replace:
```java
String raw = sp.getString(KEY_SMALL_ORDER,
        CARD_WATER + "," + CARD_SLEEP + "," + CARD_HABIT + "," + CARD_MEDICATION + "," + CARD_WEIGHT + ","
                + CARD_MEASUREMENT + "," + CARD_BOWEL + "," + CARD_MENSTRUAL);
```
with:
```java
String raw = sp.getString(KEY_SMALL_ORDER,
        CARD_WATER + "," + CARD_SLEEP + "," + CARD_HABIT + "," + CARD_MEDICATION + "," + CARD_WEIGHT + ","
                + CARD_MEASUREMENT + "," + CARD_BOWEL + "," + CARD_MENSTRUAL + "," + CARD_STEP + "," + CARD_MOOD);
```

Also add fallback additions after the existing menstrual card fallback (after line 1654):
```java
if (!smallCardOrder.contains(CARD_STEP))
    smallCardOrder.add(CARD_STEP);
if (!smallCardOrder.contains(CARD_MOOD))
    smallCardOrder.add(CARD_MOOD);
```

- [ ] **Step 7: Update applyCardConfig() — add CARD_STEP and CARD_MOOD visibility checks**

After the menstrual card visibility check (line 1678-1679), add:
```java
if (CARD_STEP.equals(id) && isCardEnabled(KEY_SHOW_STEP, true))
    enabled.add(id);
if (CARD_MOOD.equals(id) && isCardEnabled(KEY_SHOW_MOOD, true))
    enabled.add(id);
```

- [ ] **Step 8: Update showEditCardsDialog() — add CARD_STEP and CARD_MOOD to config list**

After the menstrual card config block (line 1570-1571), add:
```java
} else if (CARD_STEP.equals(id)) {
    name = "步数记录";
    visible = isCardEnabled(KEY_SHOW_STEP, true);
} else if (CARD_MOOD.equals(id)) {
    name = "情绪记录";
    visible = isCardEnabled(KEY_SHOW_MOOD, true);
```

In the save action (after line 1618 — menstrual card save), add:
```java
} else if (CARD_STEP.equals(cfg.id)) {
    editor.putBoolean(KEY_SHOW_STEP, cfg.visible);
} else if (CARD_MOOD.equals(cfg.id)) {
    editor.putBoolean(KEY_SHOW_MOOD, cfg.visible);
```

- [ ] **Step 9: Add MoodPickerBottomSheet import at top**

After line 39 (`import com.cz.fitnessdiary.viewmodel.HomeDashboardViewModel;`), add:
```java
import com.cz.fitnessdiary.ui.fragment.MoodPickerBottomSheet;
```

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java
git commit -m "feat: add step/mood observers, dialogs, exercise calorie & diet macro display to CheckInFragment"
```

---

### Task 19: Build and verify

- [ ] **Step 1: Build the project**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL — no compilation errors.

- [ ] **Step 2: Fix any compilation errors**

Iterate until clean build. Check for:
- Missing imports (use IDE auto-import or manually add)
- Wrong method names (verify against actual entity/DAO signatures)
- R.id / R.drawable / R.layout references (verify XML file names match)

- [ ] **Step 3: Verify on device**

Install and verify:
- Step card shows in grid, tap to input steps, long-press to set target
- Mood card shows in grid, tap to open emoji picker, select saves immediately
- Exercise card shows "今日消耗" with calorie total
- Diet card shows carb and protein rows with progress bars
- "管理卡片" dialog includes step and mood cards with drag-to-reorder
- Step sensor automatically counts steps (if device has sensor)

- [ ] **Step 4: Commit any final fixes**
