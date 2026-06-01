package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.StepRecord;

import java.util.concurrent.Executors;

/**
 * Helper to read Android TYPE_STEP_COUNTER sensor.
 * Stores baseline offset in SharedPreferences to compute daily steps.
 */
public class StepSensorHelper implements SensorEventListener {

    private static final String PREF_NAME = "step_sensor_prefs";
    private static final String KEY_BASELINE = "sensor_baseline";
    private static final String KEY_BASELINE_DATE = "baseline_date";

    private final Context context;
    private final SensorManager sensorManager;
    private final Sensor stepSensor;
    private boolean running;
    private StepUpdateCallback callback;

    public interface StepUpdateCallback {
        void onStepsUpdated(int todaySteps);
    }

    public StepSensorHelper(Context context) {
        this.context = context.getApplicationContext();
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.stepSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
    }

    public boolean isSensorAvailable() {
        return stepSensor != null;
    }

    public void setCallback(StepUpdateCallback callback) {
        this.callback = callback;
    }

    public void start() {
        if (stepSensor == null || running) return;
        running = true;
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void stop() {
        if (!running) return;
        running = false;
        sensorManager.unregisterListener(this);
    }

    /**
     * Save the current sensor baseline for today.
     */
    public void snapshotAndSaveBaseline(float totalStepsSinceBoot) {
        long today = DateUtils.getTodayStartTimestamp();
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putLong(KEY_BASELINE, (long) totalStepsSinceBoot)
                .putLong(KEY_BASELINE_DATE, today)
                .apply();
    }

    /**
     * Get today's steps from sensor, computing (current - baseline).
     * If baseline is stale (different day), reset it.
     */
    public int resolveTodaySteps(float totalStepsSinceBoot) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long baseline = sp.getLong(KEY_BASELINE, -1);
        long baselineDate = sp.getLong(KEY_BASELINE_DATE, 0);
        long today = DateUtils.getTodayStartTimestamp();

        if (baseline < 0 || baselineDate < today) {
            snapshotAndSaveBaseline(totalStepsSinceBoot);
            return 0;
        }

        int steps = (int) (totalStepsSinceBoot - baseline);
        return Math.max(0, steps);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int todaySteps = resolveTodaySteps(event.values[0]);

        // Persist to DB every sensor update (throttled by SENSOR_DELAY_NORMAL)
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                long today = DateUtils.getTodayStartTimestamp();
                StepRecord existing = AppDatabase.getInstance(context).stepRecordDao().getByDateSync(today);
                if (existing != null) {
                    if (existing.getSource() == 0 || existing.getSource() == 2) {
                        existing.setSteps(todaySteps);
                        existing.setSource(0);
                        existing.setCreateTime(System.currentTimeMillis());
                        AppDatabase.getInstance(context).stepRecordDao().insertOrUpdate(existing);
                    }
                } else if (todaySteps > 0) {
                    StepRecord record = new StepRecord(today, todaySteps, 0, System.currentTimeMillis());
                    AppDatabase.getInstance(context).stepRecordDao().insertOrUpdate(record);
                }
            } catch (Exception ignored) {}
        });

        if (callback != null) {
            callback.onStepsUpdated(todaySteps);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
