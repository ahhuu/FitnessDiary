package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.FoodLibrary;

import java.util.List;

/**
 * 食物库数据访问对象
 * 提供食物库的查询和批量插入操作
 */
@Dao
public interface FoodLibraryDao {

    /**
     * 批量插入食物数据（用于预填充）
     * 如果食物已存在则替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<FoodLibrary> foods);

    /**
     * 插入单个食物
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FoodLibrary food);

    /**
     * 模糊搜索食物（用于自动完成）
     * 
     * @param keyword 关键词
     * @return 匹配的食物列表
     */
    @Query("SELECT * FROM food_library WHERE name LIKE '%' || :keyword || '%' ORDER BY name LIMIT 20")
    List<FoodLibrary> searchFoods(String keyword);

    /**
     * 模糊搜索食物（LiveData版本）
     */
    @Query("SELECT * FROM food_library WHERE name LIKE '%' || :keyword || '%' ORDER BY name LIMIT 20")
    LiveData<List<FoodLibrary>> searchFoodsLive(String keyword);

    /**
     * 获取所有食物
     */
    @Query("SELECT * FROM food_library ORDER BY name")
    LiveData<List<FoodLibrary>> getAllFoods();

    /**
     * 根据名称精确查询食物
     */
    @Query("SELECT * FROM food_library WHERE name = :name LIMIT 1")
    FoodLibrary getFoodByName(String name);

    /**
     * 获取所有食物（同步方法）
     */
    @Query("SELECT * FROM food_library ORDER BY name")
    List<FoodLibrary> getAllFoodsSync();

    /**
     * 获取食物库数量（用于检查是否已初始化）
     */
    @Query("SELECT COUNT(*) FROM food_library")
    int getFoodCount();
}
