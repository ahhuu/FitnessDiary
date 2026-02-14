package com.cz.fitnessdiary.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.cz.fitnessdiary.database.dao.DailyLogDao;
import com.cz.fitnessdiary.database.dao.FoodLibraryDao;
import com.cz.fitnessdiary.database.dao.FoodRecordDao;
import com.cz.fitnessdiary.database.dao.TrainingPlanDao;
import com.cz.fitnessdiary.database.dao.UserDao;
import com.cz.fitnessdiary.database.entity.DailyLog;
import com.cz.fitnessdiary.database.entity.FoodLibrary;
import com.cz.fitnessdiary.database.entity.FoodRecord;
import com.cz.fitnessdiary.database.entity.TrainingPlan;
import com.cz.fitnessdiary.database.entity.User;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Room 数据库主类 - 2.0 版本
 * 使用单例模式确保整个应用只有一个数据库实例
 */
@Database(entities = { User.class, TrainingPlan.class, DailyLog.class, FoodRecord.class,
        FoodLibrary.class }, version = 7, exportSchema = false)
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
                                    MIGRATION_6_7) // 添加 V7 迁移
                            // [Migration Pre-reservation]
                            // 未来如果需要修改数据库结构（例如 Plan 40+），请在此添加新的 Migration 策略。
                            // 即使恢复了旧版本的备份数据库，Room 也会自动检测版本并执行这些迁移脚本，
                            // 从而确保数据结构的实时统一。
                            .fallbackToDestructiveMigration()
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    // 数据库首次创建时预填充食物库
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        updateOfficialFoodLibrary(context);
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
     * [v1.2] 更新/同步官方食物库数据
     * 逻辑说明：使用 REPLACE 策略。如果数据库已存在同名食物，则更新其营养数据；
     * 如果不存在则新增。这确保了用户在恢复旧备份后，依然能获得最新版本增加的食物。
     */
    public static void updateOfficialFoodLibrary(Context context) {
        AppDatabase db = getInstance(context);
        FoodLibraryDao foodLibraryDao = db.foodLibraryDao();

        List<FoodLibrary> foods = new ArrayList<>();

        // ==========================================
        // 1. 主食系列 (Staples)
        // ==========================================
        String catStapleRice = "主食: 基础米面";
        String catStaplePorridge = "主食: 营养粥类";
        String catStapleTuber = "主食: 根茎薯类";
        String catStapleBun = "主食: 面点包子";
        String catStapleDumpling = "主食: 饺子馄饨";
        String catStapleNoodle = "主食: 汤粉面条";
        String catStapleFast = "主食: 西式快餐";

        // --- 基础米面 ---
        foods.add(new FoodLibrary("米饭", 116, 2.6, 25.9, "碗", 150, catStapleRice));
        foods.add(new FoodLibrary("杂粮饭", 120, 3.5, 23.0, "碗", 150, catStapleRice));
        foods.add(new FoodLibrary("炒饭", 260, 7.0, 38.0, "盘", 300, catStapleRice));

        // --- 粥类 ---
        foods.add(new FoodLibrary("皮蛋瘦肉粥", 220, 12.0, 25.0, "碗", 300, catStaplePorridge));
        foods.add(new FoodLibrary("香菇鸡肉粥", 180, 10.0, 22.0, "碗", 300, catStaplePorridge));
        foods.add(new FoodLibrary("八宝粥(含糖)", 260, 6.0, 55.0, "碗", 300, catStaplePorridge));
        foods.add(new FoodLibrary("黑米粥", 170, 5.0, 35.0, "碗", 300, catStaplePorridge));
        foods.add(new FoodLibrary("小米粥", 130, 3.5, 25.0, "碗", 300, catStaplePorridge));
        foods.add(new FoodLibrary("南瓜粥", 110, 2.0, 24.0, "碗", 300, catStaplePorridge));

        // --- 根茎/薯类 ---
        foods.add(new FoodLibrary("红薯", 86, 1.6, 20.1, "个", 200, catStapleTuber));
        foods.add(new FoodLibrary("紫薯", 106, 1.5, 25.0, "个", 150, catStapleTuber));
        foods.add(new FoodLibrary("土豆", 77, 2.0, 17.0, "个", 150, catStapleTuber));
        foods.add(new FoodLibrary("玉米", 112, 4.0, 22.8, "根", 200, catStapleTuber));

        // --- 面点/包子 ---
        foods.add(new FoodLibrary("馒头", 223, 7.0, 47.0, "个", 100, catStapleBun));
        foods.add(new FoodLibrary("肉包子", 250, 8.0, 35.0, "个", 80, catStapleBun));
        foods.add(new FoodLibrary("全麦面包", 246, 10.0, 45.0, "片", 35, catStapleBun));
        foods.add(new FoodLibrary("油条", 388, 6.0, 51.0, "根", 60, catStapleBun));

        // --- 饺子/馄饨 ---
        foods.add(new FoodLibrary("水饺(猪肉白菜)", 220, 9.0, 24.0, "个", 20, catStapleDumpling));
        foods.add(new FoodLibrary("馄饨/抄手", 200, 7.0, 22.0, "碗", 250, catStapleDumpling));
        foods.add(new FoodLibrary("小笼包", 150, 5.0, 20.0, "个", 50, catStapleDumpling));

        // --- 汤面/粉 ---
        foods.add(new FoodLibrary("红烧牛肉面", 150, 7.0, 18.0, "碗", 450, catStapleNoodle));
        foods.add(new FoodLibrary("兰州拉面", 110, 6.0, 22.0, "碗", 450, catStapleNoodle));
        foods.add(new FoodLibrary("螺蛳粉", 100, 3.0, 15.0, "碗", 400, catStapleNoodle));

        // --- 西式快餐 ---
        foods.add(new FoodLibrary("香辣鸡腿堡", 550, 18.0, 50.0, "个", 180, catStapleFast));
        foods.add(new FoodLibrary("板烧鸡腿堡", 400, 22.0, 42.0, "个", 180, catStapleFast));
        foods.add(new FoodLibrary("披萨(厚底)", 300, 12.0, 35.0, "片", 100, catStapleFast));

        // ==========================================
        // 2. 家常菜系列 (Dishes)
        // ==========================================
        String catDishMeat = "菜肴: 精选荤菜";
        String catDishVeg = "菜肴: 清爽素菜";

        // --- 荤菜 ---
        foods.add(new FoodLibrary("西红柿炒蛋", 85, 5.0, 3.0, "盘", 300, catDishMeat));
        foods.add(new FoodLibrary("宫保鸡丁", 160, 15.0, 8.0, "盘", 300, catDishMeat));
        foods.add(new FoodLibrary("鱼香肉丝", 180, 10.0, 12.0, "盘", 300, catDishMeat));
        foods.add(new FoodLibrary("红烧肉", 450, 10.0, 5.0, "份", 150, catDishMeat));

        // --- 素菜 ---
        foods.add(new FoodLibrary("酸辣土豆丝", 110, 2.5, 18.0, "盘", 250, catDishVeg));
        foods.add(new FoodLibrary("炒青菜", 45, 2.0, 4.0, "盘", 250, catDishVeg));
        foods.add(new FoodLibrary("拍黄瓜", 25, 1.0, 4.0, "盘", 200, catDishVeg));

        // ==========================================
        // 3. 优质蛋白质 (Protein)
        // ==========================================
        String catProteinDaily = "蛋白: 蛋奶豆制品";
        String catProteinMeat = "蛋白: 肉类海鲜";
        String catProteinSupplement = "蛋白: 健身补剂";

        // --- 蛋奶豆制品 ---
        foods.add(new FoodLibrary("煮鸡蛋", 143, 13.0, 1.0, "个", 50, catProteinDaily));
        foods.add(new FoodLibrary("纯牛奶", 54, 3.0, 5.0, "盒", 250, catProteinDaily));
        foods.add(new FoodLibrary("豆腐", 81, 8.0, 4.0, "块", 100, catProteinDaily));

        // --- 肉类海鲜 ---
        foods.add(new FoodLibrary("鸡胸肉", 133, 31.0, 0.0, "块", 200, catProteinMeat));
        foods.add(new FoodLibrary("卤牛肉", 150, 26.0, 2.0, "份", 100, catProteinMeat));
        foods.add(new FoodLibrary("基围虾/虾仁", 93, 18.0, 2.0, "份", 100, catProteinMeat));

        // --- 健身补剂 ---
        foods.add(new FoodLibrary("乳清蛋白粉", 130, 24.0, 3.0, "勺", 30, catProteinSupplement));
        foods.add(new FoodLibrary("蛋白棒", 180, 20.0, 15.0, "根", 60, catProteinSupplement));

        // ==========================================
        // 4. 蔬菜 & 水果 (Veg & Fruits)
        // ==========================================
        String catVeg = "蔬菜: 新鲜时蔬";
        String catFruit = "水果: 时令水果";

        // --- 蔬菜 ---
        foods.add(new FoodLibrary("西蓝花", 34, 4.0, 6.0, "朵", 100, catVeg));
        foods.add(new FoodLibrary("生菜", 15, 1.3, 2.0, "颗", 200, catVeg));
        foods.add(new FoodLibrary("西红柿", 18, 0.9, 3.9, "个", 150, catVeg));

        // --- 水果 ---
        foods.add(new FoodLibrary("苹果", 52, 0.3, 14.0, "个", 200, catFruit));
        foods.add(new FoodLibrary("香蕉", 91, 1.1, 22.0, "根", 150, catFruit));
        foods.add(new FoodLibrary("火龙果", 51, 1.0, 13.0, "个", 300, catFruit));

        // ==========================================
        // 5. 零食饮品 (Snacks & Drinks)
        // ==========================================
        String catSnackPack = "零食: 包装小吃";
        String catSnackDrink = "饮料: 咖啡奶茶";

        // --- 包装小吃 ---
        foods.add(new FoodLibrary("薯片", 536, 7.0, 53.0, "包", 70, catSnackPack));
        foods.add(new FoodLibrary("混合坚果", 500, 20.0, 20.0, "把", 30, catSnackPack));
        foods.add(new FoodLibrary("黑巧克力(85%)", 550, 8.0, 20.0, "块", 20, catSnackPack));

        // --- 咖啡奶茶 ---
        foods.add(new FoodLibrary("珍珠奶茶", 450, 5.0, 60.0, "杯", 500, catSnackDrink));
        foods.add(new FoodLibrary("美式咖啡", 5, 0.2, 0.0, "杯", 350, catSnackDrink));
        foods.add(new FoodLibrary("拿铁", 160, 8.0, 12.0, "杯", 350, catSnackDrink));

        // ==========================================
        // 6. 调料与油脂 (Condiments)
        // ==========================================
        String catCondiment = "调料/油脂 (Condiments)";
        foods.add(new FoodLibrary("植物油", 90, 0.0, 0.0, "勺(10g)", 10, catCondiment));
        foods.add(new FoodLibrary("橄榄油", 90, 0.0, 0.0, "勺(10g)", 10, catCondiment));
        foods.add(new FoodLibrary("番茄酱", 20, 0.0, 5.0, "勺", 15, catCondiment));

        // ==========================================
        // 7. 酒精饮料 (Alcohol)
        // ==========================================
        String catAlcohol = "酒精饮料 (Alcohol)";
        foods.add(new FoodLibrary("啤酒(一罐)", 140, 1.0, 10.0, "罐", 330, catAlcohol));
        foods.add(new FoodLibrary("红酒", 85, 0.0, 2.5, "杯", 100, catAlcohol));
        foods.add(new FoodLibrary("白酒(52度)", 280, 0.0, 0.0, "两", 50, catAlcohol));

        foodLibraryDao.insertAll(foods);
    }
}
