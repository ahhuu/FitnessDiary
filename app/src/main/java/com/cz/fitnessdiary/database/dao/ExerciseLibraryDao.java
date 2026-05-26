package com.cz.fitnessdiary.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cz.fitnessdiary.database.entity.ExerciseLibrary;

import java.util.List;

@Dao
public interface ExerciseLibraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ExerciseLibrary> exercises);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ExerciseLibrary exercise);

    @androidx.room.Update
    void update(ExerciseLibrary exercise);

    @androidx.room.Delete
    void delete(ExerciseLibrary exercise);

    @Query("SELECT * FROM exercise_library WHERE name LIKE '%' || :keyword || '%' ORDER BY name LIMIT 20")
    List<ExerciseLibrary> searchExercises(String keyword);

    @Query("SELECT * FROM exercise_library WHERE name LIKE '%' || :keyword || '%' ORDER BY name LIMIT 20")
    LiveData<List<ExerciseLibrary>> searchExercisesLive(String keyword);

    @Query("SELECT * FROM exercise_library ORDER BY body_part, sub_category, name")
    LiveData<List<ExerciseLibrary>> getAllExercises();

    @Query("SELECT * FROM exercise_library WHERE body_part = :bodyPart ORDER BY sub_category, name")
    LiveData<List<ExerciseLibrary>> getExercisesByBodyPart(String bodyPart);

    @Query("SELECT * FROM exercise_library WHERE name = :name LIMIT 1")
    ExerciseLibrary getExerciseByName(String name);

    @Query("SELECT * FROM exercise_library ORDER BY body_part, sub_category, name")
    List<ExerciseLibrary> getAllExercisesSync();

    @Query("SELECT COUNT(*) FROM exercise_library")
    int getExerciseCount();

    @Query("SELECT DISTINCT body_part FROM exercise_library ORDER BY body_part")
    List<String> getDistinctBodyParts();

    @Query("SELECT DISTINCT sub_category FROM exercise_library WHERE body_part = :bodyPart ORDER BY sub_category")
    List<String> getSubCategoriesByBodyPart(String bodyPart);

    @Query("SELECT * FROM exercise_library WHERE body_part = :bodyPart AND sub_category = :subCategory ORDER BY name")
    List<ExerciseLibrary> getExercisesByCategory(String bodyPart, String subCategory);
}
