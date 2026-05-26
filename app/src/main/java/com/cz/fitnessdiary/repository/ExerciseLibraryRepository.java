package com.cz.fitnessdiary.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.dao.ExerciseLibraryDao;
import com.cz.fitnessdiary.database.entity.ExerciseLibrary;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseLibraryRepository {

    private ExerciseLibraryDao dao;
    private ExecutorService executorService;

    public ExerciseLibraryRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        dao = database.exerciseLibraryDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public List<ExerciseLibrary> searchExercises(String keyword) {
        return dao.searchExercises(keyword);
    }

    public LiveData<List<ExerciseLibrary>> searchExercisesLive(String keyword) {
        return dao.searchExercisesLive(keyword);
    }

    public LiveData<List<ExerciseLibrary>> getAllExercises() {
        return dao.getAllExercises();
    }

    public LiveData<List<ExerciseLibrary>> getExercisesByBodyPart(String bodyPart) {
        return dao.getExercisesByBodyPart(bodyPart);
    }

    public ExerciseLibrary getExerciseByName(String name) {
        return dao.getExerciseByName(name);
    }

    public List<ExerciseLibrary> getAllExercisesSync() {
        return dao.getAllExercisesSync();
    }

    public List<ExerciseLibrary> getExercisesByCategory(String bodyPart, String subCategory) {
        return dao.getExercisesByCategory(bodyPart, subCategory);
    }

    public List<String> getDistinctBodyParts() {
        return dao.getDistinctBodyParts();
    }

    public List<String> getSubCategoriesByBodyPart(String bodyPart) {
        return dao.getSubCategoriesByBodyPart(bodyPart);
    }

    public void insert(ExerciseLibrary exercise) {
        executorService.execute(() -> dao.insert(exercise));
    }

    public void update(ExerciseLibrary exercise) {
        executorService.execute(() -> dao.update(exercise));
    }

    public void delete(ExerciseLibrary exercise) {
        executorService.execute(() -> dao.delete(exercise));
    }
}
