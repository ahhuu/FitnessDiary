package com.cz.fitnessdiary.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.FoodLibraryDao;
import com.cz.fitnessdiary.database.entity.FoodLibrary;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 食物库数据仓库
 * 封装食物库的数据访问操作
 */
public class FoodLibraryRepository {

    private FoodLibraryDao foodLibraryDao;
    private ExecutorService executorService;

    public FoodLibraryRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        foodLibraryDao = database.foodLibraryDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * 模糊搜索食物（同步方法，在后台线程调用）
     */
    public List<FoodLibrary> searchFoods(String keyword) {
        return foodLibraryDao.searchFoods(keyword);
    }

    /**
     * 模糊搜索食物（LiveData版本）
     */
    public LiveData<List<FoodLibrary>> searchFoodsLive(String keyword) {
        return foodLibraryDao.searchFoodsLive(keyword);
    }

    /**
     * 获取所有食物
     */
    public LiveData<List<FoodLibrary>> getAllFoods() {
        return foodLibraryDao.getAllFoods();
    }

    /**
     * 根据名称精确查询食物（同步方法）
     */
    public FoodLibrary getFoodByName(String name) {
        return foodLibraryDao.getFoodByName(name);
    }

    /**
     * 获取所有食物（同步方法）
     */
    public List<FoodLibrary> getAllFoodsSync() {
        return foodLibraryDao.getAllFoodsSync();
    }

    /**
     * 插入食物
     */
    public void insert(FoodLibrary food) {
        executorService.execute(() -> foodLibraryDao.insert(food));
    }

    /**
     * 更新食物
     */
    public void update(FoodLibrary food) {
        executorService.execute(() -> foodLibraryDao.update(food));
    }

    /**
     * 检查并初始化食物库
     * (Plan 30: 逻辑已迁移至 AppDatabase.onCreate 统一处理，此处保留空实现或直接移除)
     */
    public void checkAndInitFoodLibrary() {
        // 逻辑已移除，统一由数据库创建时处理
    }
}
