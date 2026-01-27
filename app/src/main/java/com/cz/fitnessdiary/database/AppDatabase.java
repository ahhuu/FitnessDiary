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
        FoodLibrary.class }, version = 5, exportSchema = false)
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
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5) // 添加迁移策略
                            .fallbackToDestructiveMigration()
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    // 数据库首次创建时预填充食物库
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        prepopulateFoodLibrary(context);
                                    });
                                }

                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    // Plan 30: 每次打开数据库都尝试同步一次食物库分类（防止老用户数据缺失）
                                    // 因为使用的策略是 OnConflictStrategy.REPLACE，所以不会重复插入
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        prepopulateFoodLibrary(context);
                                    });
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 预填充食物库数据 (Plan 30: 完整 50+ 种食物分类注入)
     * 分类包含：主食、家常菜、优质蛋白质、蔬菜 & 水果、零食饮品
     */
    private static void prepopulateFoodLibrary(Context context) {
        AppDatabase db = getInstance(context);
        FoodLibraryDao foodLibraryDao = db.foodLibraryDao();

        List<FoodLibrary> foods = new ArrayList<>();

        // ==========================================
        // 1. 主食 (Staples)
        // ==========================================
        String catStaple = "主食 (Staples)";
        foods.add(new FoodLibrary("米饭", 116, 2.6, 25.9, "碗", 150, catStaple));
        foods.add(new FoodLibrary("杂粮饭", 120, 3.5, 23.0, "碗", 150, catStaple));
        foods.add(new FoodLibrary("馒头", 223, 7.0, 47.0, "个", 100, catStaple));
        foods.add(new FoodLibrary("花卷", 210, 6.5, 45.0, "个", 80, catStaple));
        foods.add(new FoodLibrary("肉包子", 250, 8.0, 35.0, "个", 80, catStaple));
        foods.add(new FoodLibrary("水饺(猪肉白菜)", 220, 9.0, 24.0, "个", 20, catStaple));
        foods.add(new FoodLibrary("馄饨/抄手", 200, 7.0, 22.0, "碗", 250, catStaple));
        foods.add(new FoodLibrary("油条", 388, 6.0, 51.0, "根", 60, catStaple));
        foods.add(new FoodLibrary("煎饼果子", 180, 6.0, 25.0, "套", 300, catStaple));
        foods.add(new FoodLibrary("手抓饼", 300, 7.0, 35.0, "个", 120, catStaple));

        // --- 汤面类 ---
        foods.add(new FoodLibrary("红烧牛肉面", 150, 7.0, 18.0, "碗", 450, catStaple));
        foods.add(new FoodLibrary("泡椒鸡杂面", 175, 8.0, 20.0, "碗", 400, catStaple));
        foods.add(new FoodLibrary("番茄鸡蛋面", 120, 4.5, 19.0, "碗", 450, catStaple));
        foods.add(new FoodLibrary("重庆小面", 200, 5.0, 25.0, "碗", 350, catStaple));
        foods.add(new FoodLibrary("兰州拉面", 110, 6.0, 22.0, "碗", 450, catStaple));
        foods.add(new FoodLibrary("螺蛳粉", 240, 5.0, 30.0, "碗", 400, catStaple));
        foods.add(new FoodLibrary("桂林米粉", 160, 4.0, 32.0, "碗", 350, catStaple));

        // --- 拌面/炒面类 ---
        foods.add(new FoodLibrary("武汉热干面", 280, 8.0, 30.0, "碗", 300, catStaple));
        foods.add(new FoodLibrary("老北京炸酱面", 230, 8.0, 35.0, "碗", 350, catStaple));
        foods.add(new FoodLibrary("蛋炒面", 260, 7.0, 38.0, "盘", 300, catStaple));
        foods.add(new FoodLibrary("凉面", 185, 4.0, 32.0, "碗", 250, catStaple));
        foods.add(new FoodLibrary("方便面(泡)", 450, 9.0, 60.0, "包", 100, catStaple));

        // --- 粗粮薯类 ---
        foods.add(new FoodLibrary("玉米", 112, 4.0, 22.8, "根", 200, catStaple));
        foods.add(new FoodLibrary("红薯", 86, 1.6, 20.1, "个", 200, catStaple));
        foods.add(new FoodLibrary("紫薯", 106, 1.5, 25.0, "个", 150, catStaple));
        foods.add(new FoodLibrary("全麦面包", 246, 10.0, 45.0, "片", 35, catStaple));
        foods.add(new FoodLibrary("燕麦片", 370, 15.0, 62.0, "勺", 30, catStaple));

        // ==========================================
        // 2. 家常菜 (Dishes)
        // ==========================================
        String catDish = "家常菜 (Dishes)";
        foods.add(new FoodLibrary("西红柿炒蛋", 85, 5.0, 3.0, "盘", 300, catDish));
        foods.add(new FoodLibrary("宫保鸡丁", 160, 15.0, 8.0, "盘", 300, catDish));
        foods.add(new FoodLibrary("鱼香肉丝", 180, 10.0, 12.0, "盘", 300, catDish));
        foods.add(new FoodLibrary("麻婆豆腐", 130, 8.0, 5.0, "盘", 300, catDish));
        foods.add(new FoodLibrary("回锅肉", 430, 10.0, 5.0, "盘", 250, catDish));
        foods.add(new FoodLibrary("红烧肉", 450, 10.0, 5.0, "份", 150, catDish));
        foods.add(new FoodLibrary("青椒炒肉", 140, 12.0, 4.0, "盘", 250, catDish));
        foods.add(new FoodLibrary("土豆牛腩", 125, 8.0, 10.0, "盘", 350, catDish));
        foods.add(new FoodLibrary("清蒸鱼", 95, 18.0, 2.0, "条", 300, catDish));
        foods.add(new FoodLibrary("酸辣土豆丝", 110, 2.5, 18.0, "盘", 250, catDish));
        foods.add(new FoodLibrary("拍黄瓜", 25, 1.0, 4.0, "盘", 200, catDish));
        foods.add(new FoodLibrary("炒青菜", 45, 2.0, 4.0, "盘", 250, catDish));
        foods.add(new FoodLibrary("凉拌海带", 40, 1.5, 6.0, "盘", 200, catDish));

        // ==========================================
        // 3. 优质蛋白质 (Protein)
        // ==========================================
        String catProtein = "优质蛋白质 (Protein)";
        foods.add(new FoodLibrary("煮鸡蛋", 143, 13.0, 1.0, "个", 50, catProtein));
        foods.add(new FoodLibrary("煎鸡蛋", 200, 13.0, 1.0, "个", 50, catProtein));
        foods.add(new FoodLibrary("鸡胸肉", 133, 31.0, 0.0, "块", 200, catProtein));
        foods.add(new FoodLibrary("鸡腿(去皮)", 160, 22.0, 0.0, "个", 150, catProtein));
        foods.add(new FoodLibrary("卤牛肉", 150, 26.0, 2.0, "份", 100, catProtein));
        foods.add(new FoodLibrary("牛排", 200, 25.0, 0.0, "块", 200, catProtein));
        foods.add(new FoodLibrary("基围虾", 93, 18.0, 2.0, "只", 15, catProtein));
        foods.add(new FoodLibrary("三文鱼", 160, 20.0, 0.0, "块", 150, catProtein));
        foods.add(new FoodLibrary("纯牛奶", 54, 3.0, 5.0, "盒", 250, catProtein));
        foods.add(new FoodLibrary("豆浆(无糖)", 31, 3.0, 1.5, "杯", 300, catProtein));
        foods.add(new FoodLibrary("豆腐", 81, 8.0, 4.0, "块", 100, catProtein));

        // ==========================================
        // 4. 蔬菜 & 水果 (Veg & Fruits)
        // ==========================================
        String catVegFruit = "蔬菜 & 水果 (Veg & Fruits)";
        foods.add(new FoodLibrary("西蓝花", 34, 4.0, 6.0, "朵", 100, catVegFruit));
        foods.add(new FoodLibrary("生菜", 15, 1.3, 2.0, "颗", 200, catVegFruit));
        foods.add(new FoodLibrary("菠菜", 23, 2.9, 3.6, "把", 200, catVegFruit));
        foods.add(new FoodLibrary("黄瓜", 16, 0.8, 2.0, "根", 200, catVegFruit));
        foods.add(new FoodLibrary("西红柿", 18, 0.9, 3.9, "个", 150, catVegFruit));
        foods.add(new FoodLibrary("胡萝卜", 39, 1.0, 8.0, "根", 150, catVegFruit));
        foods.add(new FoodLibrary("大白菜", 18, 1.5, 3.2, "份", 200, catVegFruit));
        foods.add(new FoodLibrary("苹果", 52, 0.3, 14.0, "个", 200, catVegFruit));
        foods.add(new FoodLibrary("香蕉", 91, 1.1, 22.0, "根", 150, catVegFruit));
        foods.add(new FoodLibrary("西瓜", 31, 0.6, 6.0, "片", 300, catVegFruit));
        foods.add(new FoodLibrary("葡萄", 45, 0.5, 10.0, "串", 200, catVegFruit));
        foods.add(new FoodLibrary("橙子", 47, 1.0, 11.0, "个", 200, catVegFruit));
        foods.add(new FoodLibrary("猕猴桃", 60, 1.1, 14.0, "个", 100, catVegFruit));
        foods.add(new FoodLibrary("火龙果", 51, 1.0, 13.0, "个", 300, catVegFruit));
        foods.add(new FoodLibrary("蓝莓", 57, 0.7, 14.0, "盒", 125, catVegFruit));

        // ==========================================
        // 5. 零食饮品 (Snacks & Drinks)
        // ==========================================
        String catSnack = "零食饮品 (Snacks & Drinks)";
        foods.add(new FoodLibrary("酸奶", 72, 2.5, 9.3, "杯", 200, catSnack));
        foods.add(new FoodLibrary("可乐", 43, 0.0, 10.6, "罐", 330, catSnack));
        foods.add(new FoodLibrary("无糖可乐", 0, 0.0, 0.0, "罐", 330, catSnack));
        foods.add(new FoodLibrary("美式咖啡", 2, 0.1, 0.0, "杯", 350, catSnack));
        foods.add(new FoodLibrary("拿铁", 55, 3.0, 4.0, "杯", 350, catSnack));
        foods.add(new FoodLibrary("薯片", 536, 7.0, 53.0, "包", 70, catSnack));
        foods.add(new FoodLibrary("混合坚果", 500, 20.0, 20.0, "把", 30, catSnack));
        foods.add(new FoodLibrary("黑巧克力(85%)", 550, 8.0, 20.0, "块", 20, catSnack));

        // 批量插入 (使用 REPLACE 策略，确保老用户也能补全分类信息)
        foodLibraryDao.insertAll(foods);
    }
}
