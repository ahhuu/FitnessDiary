package com.cz.fitnessdiary.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.cz.fitnessdiary.database.dao.DailyLogDao;
import com.cz.fitnessdiary.database.dao.ExerciseLibraryDao;
import com.cz.fitnessdiary.database.dao.FoodLibraryDao;
import com.cz.fitnessdiary.database.dao.FoodRecordDao;
import com.cz.fitnessdiary.database.dao.TrainingPlanDao;
import com.cz.fitnessdiary.database.dao.UserDao;
import com.cz.fitnessdiary.database.dao.ChatMessageDao;
import com.cz.fitnessdiary.database.entity.ChatMessageEntity;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.SleepRecord;
import com.cz.fitnessdiary.database.dao.SleepRecordDao;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.ChatSessionEntity;
import com.cz.fitnessdiary.database.dao.WeightRecordDao;
import com.cz.fitnessdiary.database.dao.WaterRecordDao;
import com.cz.fitnessdiary.database.dao.MedicationRecordDao;
import com.cz.fitnessdiary.database.dao.CustomTrackerDao;
import com.cz.fitnessdiary.database.dao.CustomRecordDao;
import com.cz.fitnessdiary.database.dao.ReminderScheduleDao;
import com.cz.fitnessdiary.database.dao.HabitItemDao;
import com.cz.fitnessdiary.database.dao.HabitRecordDao;
import com.cz.fitnessdiary.database.dao.BodyMeasurementDao;
import com.cz.fitnessdiary.database.dao.BowelMovementDao;
import com.cz.fitnessdiary.database.dao.MenstrualCycleDao;
import com.cz.fitnessdiary.database.dao.StepRecordDao;
import com.cz.fitnessdiary.database.dao.MoodRecordDao;
import com.cz.fitnessdiary.database.entity.WeightRecord;
import com.cz.fitnessdiary.database.entity.WaterRecord;
import com.cz.fitnessdiary.database.entity.MedicationRecord;
import com.cz.fitnessdiary.database.entity.CustomTracker;
import com.cz.fitnessdiary.database.entity.CustomRecord;
import com.cz.fitnessdiary.database.entity.ReminderSchedule;
import com.cz.fitnessdiary.database.entity.HabitItem;
import com.cz.fitnessdiary.database.entity.HabitRecord;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.database.entity.BowelMovement;
import com.cz.fitnessdiary.database.entity.MenstrualCycle;
import com.cz.fitnessdiary.database.entity.StepRecord;
import com.cz.fitnessdiary.database.entity.MoodRecord;

import java.util.concurrent.Executors;

/**
 * Room 数据库主类 - 2.0 版本
 * 使用单例模式确保整个应用只有一个数据库实例
 */
@Database(entities = { User.class, TrainingPlan.class, DailyLog.class, FoodRecord.class,
        FoodLibrary.class, ExerciseLibrary.class, SleepRecord.class, ChatMessageEntity.class,
        ChatSessionEntity.class, WeightRecord.class, WaterRecord.class, MedicationRecord.class, CustomTracker.class,
        CustomRecord.class, ReminderSchedule.class, HabitItem.class,
        HabitRecord.class, BodyMeasurement.class, BowelMovement.class,
        MenstrualCycle.class, StepRecord.class, MoodRecord.class }, version = 23, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    // 数据库名称
    private static final String DATABASE_NAME = "fitness_diary_db";

    // 单例实例
    private static volatile AppDatabase INSTANCE;

    // 抽象方法，返回各个 DAO
    public abstract UserDao userDao();

    public abstract TrainingPlanDao trainingPlanDao();

    public abstract DailyLogDao dailyLogDao();

    public abstract FoodRecordDao foodRecordDao();

    public abstract FoodLibraryDao foodLibraryDao();

    public abstract SleepRecordDao sleepRecordDao();

    public abstract ChatMessageDao chatMessageDao();

    public abstract com.cz.fitnessdiary.database.dao.ChatSessionDao chatSessionDao();

    public abstract WeightRecordDao weightRecordDao();

    public abstract WaterRecordDao waterRecordDao();

    public abstract MedicationRecordDao medicationRecordDao();

    public abstract CustomTrackerDao customTrackerDao();

    public abstract CustomRecordDao customRecordDao();

    public abstract ReminderScheduleDao reminderScheduleDao();

    public abstract HabitItemDao habitItemDao();

    public abstract HabitRecordDao habitRecordDao();

    public abstract ExerciseLibraryDao exerciseLibraryDao();

    public abstract BodyMeasurementDao bodyMeasurementDao();

    public abstract BowelMovementDao bowelMovementDao();

    public abstract MenstrualCycleDao menstrualCycleDao();

    public abstract StepRecordDao stepRecordDao();

    public abstract MoodRecordDao moodRecordDao();

    /**
     * 数据库迁移：Version 1 -> Version 2
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // ... (省略 V1->V2 迁移逻辑)
        }
    };

    /**
     * 数据库迁移：Version 2 -> Version 3 (Plan 8)
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // ... (保留 V2->V3 逻辑)
            database.execSQL("ALTER TABLE food_library ADD COLUMN protein_per_100g REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE food_library ADD COLUMN carbs_per_100g REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE food_library ADD COLUMN serving_unit TEXT");
            database.execSQL("ALTER TABLE food_library ADD COLUMN weight_per_unit INTEGER NOT NULL DEFAULT 100");
            database.execSQL("ALTER TABLE food_record ADD COLUMN protein REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE food_record ADD COLUMN carbs REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user ADD COLUMN target_protein INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user ADD COLUMN target_carbs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE training_plan ADD COLUMN category TEXT");
            database.execSQL("ALTER TABLE training_plan ADD COLUMN scheduled_days TEXT");
        }
    };

    /**
     * 数据库迁移：Version 3 -> Version 4 (Plan 9)
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 为 food_record 添加 meal_type 和 servings 字段
            database.execSQL("ALTER TABLE food_record ADD COLUMN meal_type INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE food_record ADD COLUMN servings REAL NOT NULL DEFAULT 1.0");
        }
    };

    /**
     * 数据库迁移：Version 4 -> Version 5 (Plan 30)
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 为 food_library 添加 category 字段
            database.execSQL("ALTER TABLE food_library ADD COLUMN category TEXT DEFAULT '其他'");
        }
    };

    /**
     * 数据库迁移：Version 5 -> Version 6 (v1.2)
     */
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 为 training_plan 添加 duration 字段
            database.execSQL("ALTER TABLE training_plan ADD COLUMN duration INTEGER NOT NULL DEFAULT 0");
            // 为 daily_log 添加 duration 字段
            database.execSQL("ALTER TABLE daily_log ADD COLUMN duration INTEGER NOT NULL DEFAULT 0");
            // 为 food_record 添加 serving_unit 字段 (v1.2)
            database.execSQL("ALTER TABLE food_record ADD COLUMN serving_unit TEXT");
        }
    };

    /**
     * 数据库迁移：Version 6 -> Version 7 (v2.0 优化)
     * 核心逻辑：由于修改了 FoodLibrary 的主键（从 name 改为自增 id），
     * SQLite 不支持直接修改主键，必须通过【创建新表 -> 迁移数据 -> 删除旧表】的重建方式。
     */
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 1. 创建符合 v2.0 结构的新临时表
            database.execSQL("CREATE TABLE IF NOT EXISTS `food_library_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`calories_per_100g` INTEGER NOT NULL, " +
                    "`protein_per_100g` REAL NOT NULL, " +
                    "`carbs_per_100g` REAL NOT NULL, " +
                    "`serving_unit` TEXT, " +
                    "`weight_per_unit` INTEGER NOT NULL, " +
                    "`category` TEXT)");

            // 2. 将旧表数据导入新表 (id 会自动生成)
            database.execSQL("INSERT INTO `food_library_new` (" +
                    "`name`, `calories_per_100g`, `protein_per_100g`, `carbs_per_100g`, " +
                    "`serving_unit`, `weight_per_unit`, `category`) " +
                    "SELECT `name`, `calories_per_100g`, `protein_per_100g`, `carbs_per_100g`, " +
                    "`serving_unit`, `weight_per_unit`, `category` FROM `food_library` " +
                    "WHERE `name` IS NOT NULL");

            // 3. 删除旧表
            database.execSQL("DROP TABLE `food_library`");

            // 4. 重命名新表为正式名称
            database.execSQL("ALTER TABLE `food_library_new` RENAME TO `food_library`");

            // 5. 为 name 建立索引（用于搜索加速）
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_food_library_name` ON `food_library` (`name`) ");
        }
    };

    /**
     * 数据库迁移：Version 7 -> Version 8 (增加睡眠记录)
     */
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `sleep_record` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`start_time` INTEGER NOT NULL, " +
                    "`end_time` INTEGER NOT NULL, " +
                    "`duration` INTEGER NOT NULL, " +
                    "`quality` INTEGER NOT NULL, " +
                    "`notes` TEXT)");
        }
    };

    /**
     * 数据库迁移：Version 8 -> Version 9 (修复食物库重复项)
     * 逻辑：通过创建带唯一约束的新表并 GROUP BY name 插入数据来去重
     */
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 1. 创建新临时表 (去重复)
            database.execSQL("CREATE TABLE IF NOT EXISTS `food_library_temp` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`calories_per_100g` INTEGER NOT NULL, " +
                    "`protein_per_100g` REAL NOT NULL, " +
                    "`carbs_per_100g` REAL NOT NULL, " +
                    "`serving_unit` TEXT, " +
                    "`weight_per_unit` INTEGER NOT NULL, " +
                    "`category` TEXT)");

            // 2. 将旧表数据导入新表，使用 GROUP BY name 去重
            database.execSQL("INSERT OR IGNORE INTO `food_library_temp` (" +
                    "`name`, `calories_per_100g`, `protein_per_100g`, `carbs_per_100g`, " +
                    "`serving_unit`, `weight_per_unit`, `category`) " +
                    "SELECT `name`, `calories_per_100g`, `protein_per_100g`, `carbs_per_100g`, " +
                    "`serving_unit`, `weight_per_unit`, `category` FROM `food_library` " +
                    "GROUP BY `name` " +
                    "HAVING `name` IS NOT NULL");

            // 3. 删除旧表（同步删除旧索引）
            database.execSQL("DROP TABLE `food_library`");

            // 4. 重命名新表
            database.execSQL("ALTER TABLE `food_library_temp` RENAME TO `food_library`");

            // 5. 在最终表上重建唯一索引
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_library_name` ON `food_library` (`name`) ");
        }
    };

    /**
     * 数据库迁移：Version 9 -> Version 10 (增加聊天记录持久化)
     */
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`content` TEXT, " +
                    "`is_user` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL)");
        }
    };

    /**
     * 数据库迁移：Version 10 -> Version 11 (增加思维链支持)
     */
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE chat_messages ADD COLUMN reasoning TEXT");
        }
    };

    /**
     * 数据库迁移：Version 11 -> Version 12 (增加多会话支持)
     */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // ... (保持原有逻辑)
            database.execSQL("CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT, " +
                    "`start_time` INTEGER NOT NULL, " +
                    "`last_updated` INTEGER NOT NULL)");
            database.execSQL("ALTER TABLE chat_messages ADD COLUMN session_id INTEGER NOT NULL DEFAULT 1");
            database.execSQL("INSERT OR IGNORE INTO chat_sessions (id, title, start_time, last_updated) " +
                    "VALUES (1, '默认对话', " + System.currentTimeMillis() + ", " + System.currentTimeMillis() + ")");
        }
    };

    /**
     * 数据库迁移：Version 12 -> Version 13 (增加文件夹分类支持)
     */
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE chat_sessions ADD COLUMN folder_name TEXT");
        }
    };

    /**
     * 数据库迁移：Version 13 -> Version 14 (增加多媒体路径字段)
     */
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE chat_messages ADD COLUMN media_path TEXT");
        }
    };

    /**
     * 数据库迁移：Version 14 -> Version 15 (新增首页记录模块)
     */
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weight_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weight` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `note` TEXT)");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `water_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount_ml` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `note` TEXT)");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medication_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `dosage` TEXT, `is_taken` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `note` TEXT)");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_tracker` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `unit` TEXT, `color_hex` TEXT, `is_enabled` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL)");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tracker_id` INTEGER NOT NULL, `numeric_value` REAL, `text_value` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`tracker_id`) REFERENCES `custom_tracker`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_custom_record_tracker_id` ON `custom_record` (`tracker_id`)");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminder_schedule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `module_type` TEXT, `target_id` INTEGER NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `repeat_days` TEXT, `is_enabled` INTEGER NOT NULL, `title` TEXT, `content` TEXT)");
        }
    };
    /**
     * 数据库迁移：Version 15 -> Version 16 (新增习惯模块)
     */
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `is_default` INTEGER NOT NULL, `is_enabled` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, `auto_rule` TEXT)");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habit_id` INTEGER NOT NULL, `record_date` INTEGER NOT NULL, `is_completed` INTEGER NOT NULL, `source` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`habit_id`) REFERENCES `habit_item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_record_habit_id_record_date` ON `habit_record` (`habit_id`, `record_date`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_record_habit_id` ON `habit_record` (`habit_id`)");
            database.execSQL(
                    "INSERT INTO `habit_item` (`name`, `is_default`, `is_enabled`, `sort_order`, `auto_rule`) VALUES ('每日打卡', 1, 1, 0, 'DAILY_CHECKIN')");
            database.execSQL(
                    "INSERT INTO `habit_item` (`name`, `is_default`, `is_enabled`, `sort_order`, `auto_rule`) VALUES ('早餐', 1, 1, 1, 'BREAKFAST')");
            database.execSQL(
                    "INSERT INTO `habit_item` (`name`, `is_default`, `is_enabled`, `sort_order`, `auto_rule`) VALUES ('早睡', 1, 1, 2, 'EARLY_SLEEP')");
        }
    };

    /**
     * 数据库迁移：Version 16 -> Version 17 (新增习惯描述 & 每日服药次数)
     */
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // HabitItem 新增 description 字段
            database.execSQL("ALTER TABLE `habit_item` ADD COLUMN `description` TEXT");
            // MedicationRecord 新增 daily_total 字段，默认1次
            database.execSQL("ALTER TABLE `medication_record` ADD COLUMN `daily_total` INTEGER NOT NULL DEFAULT 1");
        }
    };

    /**
     * 数据库迁移：Version 17 -> Version 18 (食物库新增脂肪字段)
     */
    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE `food_library` ADD COLUMN `fat_per_100g` REAL NOT NULL DEFAULT 0");
        }
    };

    /**
     * 数据库迁移：Version 18 -> Version 19 (新增运动库)
     */
    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `exercise_library` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`body_part` TEXT, " +
                    "`sub_category` TEXT, " +
                    "`description` TEXT, " +
                    "`difficulty` INTEGER NOT NULL DEFAULT 1, " +
                    "`equipment` TEXT, " +
                    "`category` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_library_name` ON `exercise_library` (`name`)");
        }
    };

    /**
     * 数据库迁移：Version 19 -> Version 20 (新增围度、便便、经期记录)
     */
    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `body_measurement` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`measurement_type` TEXT NOT NULL, " +
                    "`value` REAL NOT NULL, " +
                    "`unit` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`note` TEXT)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_body_measurement_type_time` " +
                    "ON `body_measurement` (`measurement_type`, `timestamp` DESC)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `bowel_movement` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`bristol_type` INTEGER NOT NULL, " +
                    "`color` TEXT, " +
                    "`volume` TEXT, " +
                    "`smell` TEXT, " +
                    "`process_feeling` TEXT, " +
                    "`duration_seconds` INTEGER NOT NULL DEFAULT 0, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`note` TEXT)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_bowel_movement_time` " +
                    "ON `bowel_movement` (`timestamp` DESC)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `menstrual_cycle` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`start_date` INTEGER NOT NULL, " +
                    "`end_date` INTEGER, " +
                    "`flow_intensity` TEXT, " +
                    "`symptoms` TEXT, " +
                    "`mood` TEXT, " +
                    "`notes` TEXT, " +
                    "`timestamp` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_menstrual_cycle_start` " +
                    "ON `menstrual_cycle` (`start_date` DESC)");
        }
    };

    static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `user` ADD COLUMN `daily_water_target` INTEGER NOT NULL DEFAULT 2000");
        }
    };

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

    static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE training_plan ADD COLUMN weight REAL NOT NULL DEFAULT 0");
        }
    };

    /**
     * 获取数据库实例（单例模式）
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                                    MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                                    MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)
                            // 迁移
                            // [Migration Pre-reservation]
                            // 未来如果需要修改数据库结构（例如 Plan 40+），请在此添加新的 Migration 策略。
                            // 即使恢复了旧版本的备份数据库，Room 也会自动检测版本并执行这些迁移脚本，
                            // 从而确保数据结构的实时统一。
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    // 数据库首次创建时预填充食物库和运动库
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        updateOfficialFoodLibrary(context);
                                        updateOfficialExerciseLibrary(context);
                                    });
                                }

                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    // Plan 30: 移除这里的 prepopulate 调用，因为太频繁且影响性能
                                    // 初始化逻辑应仅由 onCreate 或 Repository 检查触发
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 更新/同步官方食物库数据。
     * 委托给 FoodLibraryDataLoader，从 assets/food_library.json 加载。
     */
    public static void updateOfficialFoodLibrary(Context context) {
        FoodLibraryDataLoader.loadIfNeeded(context);
    }

    /**
     * 更新/同步官方运动库数据。
     * 委托给 ExerciseLibraryDataLoader，从 assets/exercise_library.json 加载。
     */
    public static void updateOfficialExerciseLibrary(Context context) {
        ExerciseLibraryDataLoader.loadIfNeeded(context);
    }
}
