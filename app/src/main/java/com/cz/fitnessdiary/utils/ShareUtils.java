package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextPaint;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 生成并分享精美的健康周报卡片图片。
 */
public class ShareUtils {

    private static final int CARD_WIDTH = 1080;
    private static final int CARD_HEIGHT = 2292; // 调高以适应 9 个全指标卡片与大留白
    private static final int PADDING = 64;
    private static final int CORNER = 48;

    public static class WeekSummary {
        public String weekRange;
        public int exerciseDays;
        public int totalExerciseMinutes;
        public int avgCaloriesConsumed;
        public int calorieTarget;
        public float weightStart;
        public float weightEnd;
        public int waterAvgMl;
        public int waterTarget;
        public int habitCompletionRate; // 0-100

        // 新增全量卡片指标
        public int avgSteps;
        public int stepTarget;
        public float avgSleepDuration;
        public float avgSleepQuality; // 星星数
        public int totalActiveCalories; // 运动消耗卡路里

        // 新增排便与情绪
        public float avgMoodScore;
        public String primaryMood;
        public int moodDays;
        public int bowelCount;
        public int bowelAbnormalCount;
    }

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

        // 1. 背景绘制 (微渐变高级莫兰迪灰色背景，具有柔和的高级感)
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient bgGradient = new LinearGradient(0, 0, 0, CARD_HEIGHT,
                Color.parseColor("#F5F7FA"), Color.parseColor("#E4E8F0"), Shader.TileMode.CLAMP);
        bgPaint.setShader(bgGradient);
        canvas.drawRect(0, 0, CARD_WIDTH, CARD_HEIGHT, bgPaint);

        // 2. 顶部精致浅色大标题区 (M3风格，白底，小幅圆角，带有微弱的环境光投射感)
        Paint headerBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerBg.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(PADDING, 48, CARD_WIDTH - PADDING, 250), 32, 32, headerBg);

        // 标题左侧的一抹流光绿条
        Paint ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient ribbonGrad = new LinearGradient(PADDING, 88, PADDING + 12, 210,
                Color.parseColor("#008B8B"), Color.parseColor("#005A5A"), Shader.TileMode.CLAMP);
        ribbonPaint.setShader(ribbonGrad);
        canvas.drawRoundRect(new RectF(PADDING + 16, 88, PADDING + 28, 210), 6, 6, ribbonPaint);

        // 主标题
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.parseColor("#0F3D39"));
        titlePaint.setTextSize(54);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("健康数据周度简报", PADDING + 54, 142, titlePaint);

        // 日期区间
        TextPaint datePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        datePaint.setColor(Color.parseColor("#708090"));
        datePaint.setTextSize(26);
        String subTitleText = "统计周期: " + d.weekRange + "  |  生成于 " +
                new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText(subTitleText, PADDING + 54, 192, datePaint);

        // 3. 统计项精致卡片排列
        int startY = 282;
        int cardSpacing = 24;
        int cardHeight = 188;

        // 卡片 1: 运动与训练 (消耗)
        startY = drawPremiumStatCard(canvas, startY, cardHeight, "🏃 运动与消耗", 
                d.exerciseDays + " 天", 
                "累计 " + d.totalExerciseMinutes + " 分钟 · 消耗 " + d.totalActiveCalories + " kcal", 
                "坚持运动", "#1A73E8");
        startY += cardSpacing;

        // 卡片 2: 每日热量摄入 (饮食)
        startY = drawPremiumStatCard(canvas, startY, cardHeight, "🥗 膳食热量", 
                d.avgCaloriesConsumed + " kcal", 
                "日均摄入  (目标 " + d.calorieTarget + " kcal)", 
                d.avgCaloriesConsumed <= d.calorieTarget ? "热量未超标" : "热量偏多", "#E8711A");
        startY += cardSpacing;

        // 卡片 3: 每日行进步数 (步数)
        startY = drawPremiumStatCard(canvas, startY, cardHeight, "👣 行进步数", 
                d.avgSteps + " 步", 
                "日均步数  (目标 " + d.stepTarget + " 步)", 
                d.avgSteps >= d.stepTarget ? "步数达标" : "仍需努力", "#0F9D58");
        startY += cardSpacing;

        // 卡片 4: 睡眠时长与质量 (睡眠)
        StringBuilder stars = new StringBuilder();
        int qualityInt = Math.round(d.avgSleepQuality);
        if (qualityInt <= 0) stars.append("--");
        else {
            for (int i = 0; i < Math.min(qualityInt, 5); i++) stars.append("⭐");
        }
        startY = drawPremiumStatCard(canvas, startY, cardHeight, "🌙 睡眠监测", 
                String.format(Locale.getDefault(), "均 %.1f h", d.avgSleepDuration), 
                "日均时长  (质量: " + stars + ")", 
                d.avgSleepDuration >= 7f ? "睡眠充足" : "睡眠不足", "#6F42C1");
        startY += cardSpacing;

        // 卡片 5: 每日饮水 (饮水)
        startY = drawPremiumStatCard(canvas, startY, cardHeight, "💧 水分摄入", 
                d.waterAvgMl + " ml", 
                "日均饮水  (目标 " + d.waterTarget + " ml)", 
                d.waterAvgMl >= d.waterTarget ? "饮水充足" : "水分不足", "#00B0FF");
        startY += cardSpacing;

        // 卡片 6: 体重跟踪 (体重)
        String weightChangeText;
        String weightColor;
        String tip;
        if (d.weightStart > 0 && d.weightEnd > 0) {
            float diff = d.weightEnd - d.weightStart;
            weightChangeText = diff > 0 ? "+" + String.format(Locale.getDefault(), "%.1f", diff) : String.format(Locale.getDefault(), "%.1f", diff);
            weightColor = diff > 0 ? "#EF5350" : "#26A69A";
            tip = String.format(Locale.getDefault(), "本周趋势: %.1f → %.1f kg", d.weightStart, d.weightEnd);
        } else {
            weightChangeText = "--";
            weightColor = "#757575";
            tip = "本周暂未记录完整体重";
        }
        startY = drawPremiumStatCard(canvas, startY, cardHeight, "⚖️ 体重变化", 
                weightChangeText + " kg", 
                tip, 
                "平稳追踪", weightColor);
        startY += cardSpacing;

        // 卡片 7: 每日习惯完成情况 (习惯)
        startY = drawPremiumStatCard(canvas, startY, cardHeight, "✅ 习惯习惯", 
                d.habitCompletionRate + "%", 
                "本周习惯清单综合完成率", 
                d.habitCompletionRate >= 80 ? "习惯极佳" : "保持专注", "#F4B400");
        startY += cardSpacing;

        // 卡片 8: 便便记录 (排便)
        String bowelTip = "本周排便共记了 " + d.bowelCount + " 次";
        if (d.bowelCount > 0 && d.bowelAbnormalCount > 0) {
            bowelTip += " (含 " + d.bowelAbnormalCount + " 次便秘/腹泻/不畅)";
        } else if (d.bowelCount > 0) {
            bowelTip += " (状态正常)";
        }
        startY = drawPremiumStatCard(canvas, startY, cardHeight, "🚽 排便状态",
                d.bowelCount + " 次",
                bowelTip,
                d.bowelAbnormalCount > 0 ? "存在异常" : "排便规律", "#795548");
        startY += cardSpacing;

        // 卡片 9: 每日情绪 (情绪)
        String moodEmoji = "—";
        String moodName = "暂无记录";
        if ("HAPPY".equals(d.primaryMood)) { moodEmoji = "😊"; moodName = "开心"; }
        else if ("NEUTRAL".equals(d.primaryMood)) { moodEmoji = "😐"; moodName = "一般"; }
        else if ("SAD".equals(d.primaryMood)) { moodEmoji = "😢"; moodName = "低落"; }
        else if ("IRRITABLE".equals(d.primaryMood)) { moodEmoji = "😡"; moodName = "烦躁"; }
        else if ("ANXIOUS".equals(d.primaryMood)) { moodEmoji = "😰"; moodName = "焦虑"; }

        String moodSub = d.moodDays > 0 
                ? String.format(Locale.getDefault(), "综合评分 %.1f 分 (记录 %d 天)", d.avgMoodScore, d.moodDays)
                : "本周暂无心情打卡";
        String moodStatus = "情绪平稳";
        if (d.moodDays > 0) {
            if (d.avgMoodScore >= 4.0) moodStatus = "身心愉悦";
            else if (d.avgMoodScore < 3.0) moodStatus = "情绪低落";
        }
        startY = drawPremiumStatCard(canvas, startY, cardHeight, "🧠 每日情绪",
                moodEmoji + " " + moodName,
                moodSub,
                moodStatus, "#E91E63");

        // 4. Footer 优雅装饰
        TextPaint footerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.parseColor("#7F8C8D"));
        footerPaint.setTextSize(26);
        footerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText("用汗水雕刻时光 · FitnessDiary 健身日记", PADDING + 12, CARD_HEIGHT - 64, footerPaint);

        return bitmap;
    }

    private static int drawPremiumStatCard(Canvas canvas, int y, int height, String label, String value,
                                            String sub, String statusTag, String accentColorStr) {
        int accentColor = Color.parseColor(accentColorStr);

        // 1. 软白卡片底色 (带轻微阴影)
        Paint cardBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBg.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(PADDING, y, CARD_WIDTH - PADDING, y + height), 24, 24, cardBg);

        // 2. 左侧精致窄边标志带 (小幅度圆角，流光质感)
        Paint accentBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        accentBar.setColor(accentColor);
        canvas.drawRoundRect(new RectF(PADDING + 16, y + 24, PADDING + 24, y + height - 24), 4, 4, accentBar);

        int textX = PADDING + 48;

        // 3. 指标项小标题
        TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#5A6B7C"));
        labelPaint.setTextSize(26);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(label, textX, y + 54, labelPaint);

        // 4. 主数值及单位
        TextPaint valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(Color.parseColor("#1C2A38"));
        valuePaint.setTextSize(52);
        valuePaint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        canvas.drawText(value, textX, y + 126, valuePaint);

        // 5. 左下角次级说明文字
        TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.parseColor("#8E9AA8"));
        subPaint.setTextSize(22);
        canvas.drawText(sub, textX, y + 162, subPaint);

        // 6. 右侧精致高颜值标签 (Status Badge)
        TextPaint tagPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tagPaint.setColor(accentColor);
        tagPaint.setTextSize(24);
        tagPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float tagTextWidth = tagPaint.measureText(statusTag);

        // 绘制标签的半透明小背景
        Paint tagBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tagBgPaint.setColor((accentColor & 0x00FFFFFF) | 0x1A000000); // 10% 透明度
        RectF tagRect = new RectF(
                CARD_WIDTH - PADDING - tagTextWidth - 36,
                y + 74,
                CARD_WIDTH - PADDING - 12,
                y + 122
        );
        canvas.drawRoundRect(tagRect, 12, 12, tagBgPaint);

        // 标签文字居中绘制
        canvas.drawText(statusTag,
                tagRect.left + 12,
                tagRect.top + 33,
                tagPaint);

        return y + height;
    }
}
