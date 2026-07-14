package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Workout rest timer with configurable duration and vibration alert.
 */
public class RestTimerManager {

    private CountDownTimer countDownTimer;
    private long remainingMillis;
    private boolean isRunning;
    private boolean isPaused;
    private long totalDuration;

    public interface TimerCallback {
        void onTick(long remainingSeconds);
        void onFinish();
    }

    private TimerCallback callback;

    public RestTimerManager() {
    }

    public void setCallback(TimerCallback callback) {
        this.callback = callback;
    }

    public void start(int totalSeconds) {
        cancel();
        totalDuration = totalSeconds * 1000L;
        remainingMillis = totalDuration;
        isRunning = true;
        isPaused = false;

        countDownTimer = new CountDownTimer(totalDuration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                if (callback != null) {
                    callback.onTick(millisUntilFinished / 1000);
                }
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                isRunning = false;
                if (callback != null) {
                    callback.onFinish();
                }
            }
        }.start();
    }

    public void pause() {
        if (countDownTimer != null && isRunning && !isPaused) {
            countDownTimer.cancel();
            isPaused = true;
        }
    }

    public void resume() {
        if (isPaused && remainingMillis > 0) {
            countDownTimer = new CountDownTimer(remainingMillis, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    remainingMillis = millisUntilFinished;
                    if (callback != null) {
                        callback.onTick(millisUntilFinished / 1000);
                    }
                }

                @Override
                public void onFinish() {
                    remainingMillis = 0;
                    isRunning = false;
                    isPaused = false;
                    if (callback != null) {
                        callback.onFinish();
                    }
                }
            }.start();
            isPaused = false;
            isRunning = true;
        }
    }

    public void cancel() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        isRunning = false;
        isPaused = false;
        remainingMillis = 0;
    }

    public boolean isRunning() {
        return isRunning && !isPaused;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public long getRemainingSeconds() {
        return remainingMillis / 1000;
    }

    public void vibrate(Context context) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 200, 200, 200, 200, 400};
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            } catch (SecurityException e) {
                // Ignore missing vibration permission on some OEM roms
            }
        }
    }
}
