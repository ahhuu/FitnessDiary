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
public class Migration29To30Test {
    private static final String TEST_DATABASE = "migration-29-30";

    @Rule
    public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class.getCanonicalName());

    @Test
    public void migrate29To30_preservesDailyLogsAndAddsActualColumns() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(TEST_DATABASE, 29);
        database.execSQL("INSERT INTO daily_log (plan_id, date, is_completed, duration) " +
                "VALUES (11, 1720000000000, 1, 900)");
        database.close();

        database = helper.runMigrationsAndValidate(
                TEST_DATABASE, 30, true, AppDatabase.MIGRATION_29_30);
        try (Cursor cursor = database.query("SELECT * FROM daily_log WHERE plan_id = 11")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1720000000000L,
                    cursor.getLong(cursor.getColumnIndexOrThrow("date")));
            assertEquals(900, cursor.getInt(cursor.getColumnIndexOrThrow("duration")));
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("actual_sets")));
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("actual_reps")));
            assertEquals(0f, cursor.getFloat(cursor.getColumnIndexOrThrow("actual_weight")), 0.001f);
        }
        try (Cursor cursor = database.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'extra_exercise_log'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("extra_exercise_log", cursor.getString(0));
        }
        database.close();
    }
}
