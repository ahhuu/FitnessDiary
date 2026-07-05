package com.cz.fitnessdiary.repository;

import android.app.Application;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.FavoriteFoodDao;
import com.cz.fitnessdiary.database.entity.FavoriteFood;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavoriteFoodRepository {

    private final FavoriteFoodDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FavoriteFoodRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        dao = db.favoriteFoodDao();
    }

    public void insert(FavoriteFood food) {
        executor.execute(() -> dao.insert(food));
    }

    public void delete(FavoriteFood food) {
        executor.execute(() -> dao.delete(food));
    }

    public void deleteById(long id) {
        executor.execute(() -> dao.deleteById(id));
    }

    public void getAll(OnDataLoadedListener<List<FavoriteFood>> listener) {
        executor.execute(() -> {
            List<FavoriteFood> list = dao.getAll();
            if (listener != null) listener.onLoaded(list);
        });
    }

    public void existsByName(String name, OnDataLoadedListener<Boolean> listener) {
        executor.execute(() -> {
            int count = dao.countByName(name);
            if (listener != null) listener.onLoaded(count > 0);
        });
    }

    public interface OnDataLoadedListener<T> {
        void onLoaded(T data);
    }
}
