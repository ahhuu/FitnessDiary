package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ShareCardGenerator {

    private static final int CARD_W = 800;
    private static final int CARD_H = 1000;
    private static final int BG_COLOR = 0xFFF5F7FA;
    private static final int TITLE_COLOR = 0xFF006A6A;
    private static final int TEXT_COLOR = 0xFF333333;
    private static final int SUB_COLOR = 0xFF888888;
    private static final int ACCENT = 0xFF4CAF50;

    public static Bitmap generateBeforeAfterCard(Context context, long beforeDate, long afterDate) {
        Bitmap bitmap = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        AppDatabase db = AppDatabase.getInstance(context);
        User user = db.userDao().getUserSync();

        // Background
        canvas.drawColor(BG_COLOR);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(TITLE_COLOR);
        titlePaint.setTextSize(48f);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

        Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(TEXT_COLOR);
        bodyPaint.setTextSize(32f);

        Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(SUB_COLOR);
        subPaint.setTextSize(26f);

        Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        accentPaint.setColor(ACCENT);
        accentPaint.setTextSize(30f);
        accentPaint.setTypeface(Typeface.DEFAULT_BOLD);

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFFE0E0E0);
        linePaint.setStrokeWidth(2f);

        float y = 60f;

        // Title
        SimpleDateFormat sdf = new SimpleDateFormat("MM月dd日", Locale.getDefault());
        String title = sdf.format(new Date(beforeDate)) + " → " + sdf.format(new Date(afterDate)) + " 变化报告";
        canvas.drawText(title, 40f, y, titlePaint);
        y += 70f;

        // User info
        if (user != null) {
            String info = (user.getNickname() != null ? user.getNickname() : "用户") +
                    " · " + (user.getGender() == 1 ? "男" : "女") + " · " + user.getAge() + "岁";
            canvas.drawText(info, 40f, y, subPaint);
            y += 45f;
        }
        canvas.drawLine(40f, y, CARD_W - 40f, y, linePaint);
        y += 30f;

        // Weight comparison - fallback to nearest record before date
        float oldWeight = getWeightAtOrBefore(db, beforeDate);
        float newWeight = getWeightAtOrBefore(db, afterDate);
        if (oldWeight > 0 || newWeight > 0) {
            drawComparisonRow(canvas, "体重 (kg)", oldWeight, newWeight, y, bodyPaint, accentPaint);
            y += 55f;
        }

        // Body measurements - fallback to nearest record before date
        String[] types = {"BODY_FAT", "WAIST", "HIP", "CHEST", "ARM", "THIGH", "CALF"};
        String[] names = {"体脂率 (%)", "腰围 (cm)", "臀围 (cm)", "胸围 (cm)", "臂围 (cm)", "大腿围 (cm)", "小腿围 (cm)"};
        for (int i = 0; i < types.length; i++) {
            float oldV = getMeasurementAtOrBefore(db, types[i], beforeDate);
            float newV = getMeasurementAtOrBefore(db, types[i], afterDate);
            if (oldV > 0 || newV > 0) {
                drawComparisonRow(canvas, names[i], oldV, newV, y, bodyPaint, accentPaint);
                y += 50f;
            }
        }

        // Footer
        y += 30f;
        canvas.drawLine(40f, y, CARD_W - 40f, y, linePaint);
        y += 40f;
        subPaint.setTextSize(22f);
        canvas.drawText("由 健康日记 生成 · " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()),
                40f, y, subPaint);

        return bitmap;
    }

    private static void drawComparisonRow(Canvas canvas, String label, float oldV, float newV,
                                           float y, Paint bodyPaint, Paint accentPaint) {
        canvas.drawText(label, 40f, y, bodyPaint);
        String oldStr = oldV > 0 ? String.format(Locale.getDefault(), "%.1f", oldV) : "--";
        String newStr = newV > 0 ? String.format(Locale.getDefault(), "%.1f", newV) : "--";
        String diffStr;
        if (oldV > 0 && newV > 0) {
            float diff = newV - oldV;
            diffStr = (diff >= 0 ? "+" : "") + String.format(Locale.getDefault(), "%.1f", diff);
        } else {
            diffStr = "--";
        }
        canvas.drawText(oldStr, 320f, y, bodyPaint);
        canvas.drawText("→", 440f, y, bodyPaint);
        canvas.drawText(newStr, 500f, y, bodyPaint);
        canvas.drawText(diffStr, 620f, y, accentPaint);
    }

    private static float getWeightAtOrBefore(AppDatabase db, long date) {
        // Try exact date first
        java.util.List<WeightRecord> list = db.weightRecordDao().getRecordsByDateRangeSync(
                date, date + 86400000L);
        if (list != null && !list.isEmpty()) return list.get(0).getWeight();
        // Fallback to last record before this date
        WeightRecord prev = db.weightRecordDao().getLatestRecordBeforeSync(date + 86400000L);
        return prev != null ? prev.getWeight() : 0;
    }

    private static float getMeasurementAtOrBefore(AppDatabase db, String type, long date) {
        // Try exact date first
        java.util.List<BodyMeasurement> list = db.bodyMeasurementDao().getByTypeAndDateRangeSync(
                type, date, date + 86400000L);
        if (list != null && !list.isEmpty()) return list.get(0).getValue();
        // Fallback to the last record before this date (query wider range)
        java.util.List<BodyMeasurement> all = db.bodyMeasurementDao().getByTypeAndDateRangeSync(
                type, 0, date + 86400000L);
        if (all != null && !all.isEmpty()) return all.get(0).getValue();
        return 0;
    }
}
