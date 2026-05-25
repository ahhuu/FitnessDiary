package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.text.TextPaint;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Generates a weekly health report card image and shares it.
 */
public class ShareUtils {

    private static final int CARD_WIDTH = 1080;
    private static final int CARD_HEIGHT = 1400;
    private static final int PADDING = 60;
    private static final int CORNER = 40;

    public static class WeekSummary {
        public String weekRange;
        public int exerciseDays;
        public int totalExerciseMinutes;
        public int avgCaloriesConsumed;
        public int calorieTarget;
        public float weightStart;
        public float weightEnd;
        public int waterAvgMl;
        public int habitCompletionRate; // 0-100
    }

    /**
     * Generate and share a weekly report card.
     */
    public static void shareWeekReport(Context context, WeekSummary data) {
        Bitmap bitmap = generateCard(context, data);
        if (bitmap == null) return;

        try {
            File dir = new File(context.getCacheDir(), "share");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "weekly_report.png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos);
            fos.close();

            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(shareIntent, "分享健康周报"));
        } catch (Exception e) {
            ErrorHandler.showError(context instanceof android.app.Activity
                    ? (android.app.Activity) context : null, "生成报告失败: " + e.getMessage(), null);
        }
    }

    private static Bitmap generateCard(Context context, WeekSummary d) {
        Bitmap bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Background
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#F0F4F9"));
        canvas.drawRoundRect(new RectF(0, 0, CARD_WIDTH, CARD_HEIGHT), CORNER, CORNER, bgPaint);

        // Top accent bar
        Paint accentPaint = new Paint();
        accentPaint.setColor(Color.parseColor("#006A6A"));
        canvas.drawRoundRect(new RectF(0, 0, CARD_WIDTH, 200), CORNER, CORNER, accentPaint);
        canvas.drawRect(new RectF(0, 160, CARD_WIDTH, 200), accentPaint);

        // Title
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(56);
        titlePaint.setFakeBoldText(true);
        canvas.drawText("健康周报", PADDING, 90, titlePaint);

        TextPaint datePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        datePaint.setColor(Color.argb(180, 255, 255, 255));
        datePaint.setTextSize(30);
        canvas.drawText(d.weekRange, PADDING, 140, datePaint);

        // Gen date
        TextPaint genPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        genPaint.setColor(Color.argb(120, 255, 255, 255));
        genPaint.setTextSize(24);
        String genDate = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText("生成于 " + genDate, PADDING, 178, genPaint);

        // Stats cards
        int cardY = 240;
        int cardSpacing = 30;
        int cardHeight = 200;

        cardY = drawStatCard(canvas, cardY, cardHeight, "运动天数", String.valueOf(d.exerciseDays), "天",
                "累计 " + d.totalExerciseMinutes + " 分钟", "#1976D2");
        cardY += cardSpacing;
        cardY = drawStatCard(canvas, cardY, cardHeight, "日均摄入", String.valueOf(d.avgCaloriesConsumed), "千卡",
                "目标 " + d.calorieTarget + " 千卡/日", "#E87328");
        cardY += cardSpacing;

        String weightChange;
        String weightColor;
        if (d.weightStart > 0 && d.weightEnd > 0) {
            float diff = d.weightEnd - d.weightStart;
            weightChange = diff > 0 ? "+" + String.format("%.1f", diff) : String.format("%.1f", diff);
            weightColor = diff > 0 ? "#EF4444" : "#34A853";
        } else {
            weightChange = "--";
            weightColor = "#757575";
        }
        cardY = drawStatCard(canvas, cardY, cardHeight, "体重变化", weightChange, "kg",
                d.weightStart > 0 ? String.format("%.1f → %.1f kg", d.weightStart, d.weightEnd) : "暂无数据", weightColor);
        cardY += cardSpacing;
        cardY = drawStatCard(canvas, cardY, cardHeight, "日均饮水", String.valueOf(d.waterAvgMl), "ml",
                "目标 1600ml/日", "#007BFF");
        cardY += cardSpacing;
        cardY = drawStatCard(canvas, cardY, cardHeight, "习惯完成率", d.habitCompletionRate + "%", "",
                "坚持就是胜利", "#FFB300");

        // Footer
        TextPaint footerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.parseColor("#9E9E9E"));
        footerPaint.setTextSize(24);
        canvas.drawText("FitnessDiary · 健康日记", PADDING, CARD_HEIGHT - 40, footerPaint);

        return bitmap;
    }

    private static int drawStatCard(Canvas canvas, int y, int height, String label, String value,
            String unit, String sub, String accentColorStr) {
        int accentColor = Color.parseColor(accentColorStr);

        // Card background
        Paint cardBg = new Paint();
        cardBg.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(PADDING, y, CARD_WIDTH - PADDING, y + height), 20, 20, cardBg);

        // Left accent bar
        Paint accentBar = new Paint();
        accentBar.setColor(accentColor);
        canvas.drawRoundRect(new RectF(PADDING, y + 20, PADDING + 8, y + height - 20), 4, 4, accentBar);

        int textX = PADDING + 36;

        // Label
        TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#757575"));
        labelPaint.setTextSize(28);
        canvas.drawText(label, textX, y + 50, labelPaint);

        // Value + unit
        TextPaint valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(accentColor);
        valuePaint.setTextSize(56);
        valuePaint.setFakeBoldText(true);
        float valueWidth = valuePaint.measureText(value);

        TextPaint unitPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        unitPaint.setColor(Color.parseColor("#757575"));
        unitPaint.setTextSize(28);
        float unitWidth = unit.isEmpty() ? 0 : unitPaint.measureText(" " + unit);

        float totalWidth = valueWidth + unitWidth;
        float valueX = textX;
        canvas.drawText(value, valueX, y + 115, valuePaint);
        if (!unit.isEmpty()) {
            canvas.drawText(" " + unit, valueX + valueWidth, y + 115, unitPaint);
        }

        // Subtitle (right-aligned)
        TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.parseColor("#9E9E9E"));
        subPaint.setTextSize(26);
        float subWidth = subPaint.measureText(sub);
        canvas.drawText(sub, CARD_WIDTH - PADDING - subWidth, y + 115, subPaint);

        return y + height;
    }
}
