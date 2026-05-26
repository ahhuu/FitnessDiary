package com.cz.fitnessdiary.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "exercise_library", indices = {
        @Index(value = { "name" }, name = "index_exercise_library_name", unique = true) })
public class ExerciseLibrary implements java.io.Serializable {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "body_part")
    private String bodyPart;

    @ColumnInfo(name = "sub_category")
    private String subCategory;

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "difficulty")
    private int difficulty; // 1=初级, 2=中级, 3=高级

    @ColumnInfo(name = "equipment")
    private String equipment;

    @ColumnInfo(name = "category")
    private String category; // "bodyPart: subCategory" 格式

    public ExerciseLibrary(@NonNull String name, String bodyPart, String subCategory,
                           String description, int difficulty, String equipment, String category) {
        this.name = name;
        this.bodyPart = bodyPart;
        this.subCategory = subCategory;
        this.description = description;
        this.difficulty = difficulty;
        this.equipment = equipment;
        this.category = category;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBodyPart() { return bodyPart; }
    public void setBodyPart(String bodyPart) { this.bodyPart = bodyPart; }

    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = Math.max(1, Math.min(3, difficulty)); }

    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
