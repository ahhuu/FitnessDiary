package com.cz.fitnessdiary.database;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class Migration27To28Test {
    private static final String TEST_DATABASE = "migration-27-28";

    @Rule
    public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class.getCanonicalName());

    @Test
    public void migrate27To28_preservesSchemaAndAddsCloudBindingColumns() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(TEST_DATABASE, 27);
        database.execSQL("INSERT INTO user (uid, name, height, weight, is_registered, gender, " +
                "goal_type, activity_level, daily_calorie_target, age, nickname, goal, avatar_uri, " +
                "target_protein, target_carbs, target_fat, daily_water_target) VALUES " +
                "(7, 'Local User', 168, 62.5, 1, 0, 0, 1.2, 1800, 26, 'Runner', '减脂', NULL, " +
                "90, 220, 50, 2000)");
        database.close();

        database = helper.runMigrationsAndValidate(TEST_DATABASE, 28, true, AppDatabase.MIGRATION_27_28);
        try (Cursor cursor = database.query("SELECT * FROM user WHERE uid = 7")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Local User", cursor.getString(cursor.getColumnIndexOrThrow("name")));
            assertEquals(62.5f, cursor.getFloat(cursor.getColumnIndexOrThrow("weight")), 0.001f);
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("leancloud_user_id")));
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("cloud_bound_at")));
        }
        database.close();
    }
}
