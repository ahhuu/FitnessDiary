package com.cz.fitnessdiary.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;

import com.cz.fitnessdiary.database.AppDatabase;
import com.cz.fitnessdiary.database.entity.BodyMeasurement;
import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.database.entity.WeightRecord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 生成身体数据前后对比分享图的实用工具。
 */
public class ShareCardGenerator {

    private static final int CARD_W = 900;
    private static final int CARD_H = 1260; // 调高高度以支持 10 个全量围度指标和大白边界
    private static final int BG_COLOR_START = 0xFFECEFF4;
    private static final int BG_COLOR_END   = 0xFFD8DEE9;
    private static final int TEXT_COLOR     = 0xFF2E3440;
    private static final int SUB_COLOR      = 0xFF708090;

    public static Bitmap generateBeforeAfterCard(Context context, long beforeDate, long afterDate) {
        Bitmap bitmap = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        AppDatabase db = AppDatabase.getInstance(context);
        User user = db.userDao().getUserSync();

        // 1. 绘制莫兰迪灰渐变大底色
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient bgGrad = new LinearGradient(0, 0, 0, CARD_H,
                BG_COLOR_START, BG_COLOR_END, Shader.TileMode.CLAMP);
        bgPaint.setShader(bgGrad);
        canvas.drawRect(0, 0, CARD_W, CARD_H, bgPaint);

        // 2. 绘制白色浮动大卡片容器 (四角柔和圆化)
        Paint cardBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBg.setColor(Color.WHITE);
        RectF cardRect = new RectF(48, 48, CARD_W - 48, CARD_H - 120);
        canvas.drawRoundRect(cardRect, 36, 36, cardBg);

        float y = 110f;

        // 3. 绘制标题
        SimpleDateFormat sdf = new SimpleDateFormat("MM月dd日", Locale.getDefault());
        String title = sdf.format(new Date(beforeDate)) + " ➔ " + sdf.format(new Date(afterDate)) + " 变化报告";
        
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.parseColor("#0F3D39"));
        titlePaint.setTextSize(44f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(title, 88f, y, titlePaint);
        y += 50f;

        // 4. 用户基础资料小文字
        Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(SUB_COLOR);
        subPaint.setTextSize(24f);
        if (user != null) {
            String info = (user.getNickname() != null ? user.getNickname() : "健友") +
                    "  |  " + (user.getGender() == 1 ? "男" : "女") + " · " + user.getAge() + " 岁";
            canvas.drawText(info, 88f, y, subPaint);
        }
        y += 40f;

        // 5. 优雅的表头网格细线与表头文字
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#ECEFF1"));
        linePaint.setStrokeWidth(2f);
        canvas.drawLine(88f, y, CARD_W - 88f, y, linePaint);
        y += 45f;

        Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(Color.parseColor("#90A4AE"));
        headerPaint.setTextSize(22f);
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        canvas.drawText("测度指标", 88f, y, headerPaint);
        canvas.drawText("初始记录", 350f, y, headerPaint);
        canvas.drawText("最新记录", 520f, y, headerPaint);
        canvas.drawText("周期变化", 690f, y, headerPaint);
        
        y += 25f;
        canvas.drawLine(88f, y, CARD_W - 88f, y, linePaint);
        y += 45f;

        // 6. 核心数据行绘制 (包括全部 8 个围度 + 体脂率 + 体重)
        Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(TEXT_COLOR);
        bodyPaint.setTextSize(28f);

        // 全部围度指标及体脂、体重
        String[] types = {"WEIGHT", "BODY_FAT", "NECK", "SHOULDER", "CHEST", "ARM", "WAIST", "HIP", "THIGH", "CALF"};
        String[] names = {"体重 (kg)", "体脂率 (%)", "颈围 (cm)", "肩宽 (cm)", "胸围 (cm)", "臂围 (cm)", "腰围 (cm)", "臀围 (cm)", "大腿围 (cm)", "小腿围 (cm)"};

        for (int i = 0; i < types.length; i++) {
            float oldV;
            float newV;
            if ("WEIGHT".equals(types[i])) {
                oldV = getWeightAtOrBefore(db, beforeDate);
                newV = getWeightAtOrBefore(db, afterDate);
            } else {
                oldV = getMeasurementAtOrBefore(db, types[i], beforeDate);
                newV = getMeasurementAtOrBefore(db, types[i], afterDate);
            }

            // 任意一个时期有数据就显示该指标，否则跳过以节省高度空余
            if (oldV > 0 || newV > 0) {
                drawPremiumComparisonRow(canvas, names[i], oldV, newV, y, bodyPaint, linePaint);
                y += 76f; // 舒展而有呼吸感的行距
            }
        }

        // 7. 优雅底栏
        y = CARD_H - 170f;
        canvas.drawLine(88f, y, CARD_W - 88f, y, linePaint);
        
        y += 40f;
        subPaint.setTextSize(22f);
        subPaint.setColor(Color.parseColor("#9EAAB6"));
        String footerText = "由 身体数据中心 连线生成  ·  " + 
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        canvas.drawText(footerText, 88f, y, subPaint);

        return bitmap;
    }

    private static void drawPremiumComparisonRow(Canvas canvas, String label, float oldV, float newV,
                                                 float y, Paint bodyPaint, Paint linePaint) {
        // 绘制指标名
        canvas.drawText(label, 88f, y, bodyPaint);

        String oldStr = oldV > 0 ? String.format(Locale.getDefault(), "%.1f", oldV) : "—";
        String newStr = newV > 0 ? String.format(Locale.getDefault(), "%.1f", newV) : "—";
        
        // 绘制旧值与新值
        canvas.drawText(oldStr, 350f, y, bodyPaint);
        canvas.drawText("➔", 450f, y, bodyPaint);
        canvas.drawText(newStr, 520f, y, bodyPaint);

        // 计算差值
        String diffStr;
        int badgeColor = 0xFF90A4AE; // 默认灰色
        int textColor  = Color.WHITE;

        if (oldV > 0 && newV > 0) {
            float diff = newV - oldV;
            if (Math.abs(diff) < 0.01f) {
                diffStr = "0.0";
                badgeColor = 0xFFCFD8DC; // 淡灰色
                textColor  = 0xFF546E7A;
            } else if (diff > 0) {
                diffStr = "+" + String.format(Locale.getDefault(), "%.1f", diff);
                badgeColor = 0xFFE3F2FD; // 莫兰迪淡蓝 (表示增长)
                textColor  = 0xFF1565C0;
            } else {
                diffStr = String.format(Locale.getDefault(), "%.1f", diff);
                badgeColor = 0xFFE8F5E9; // 莫兰迪淡绿 (表示降低/减脂)
                textColor  = 0xFF2E7D32;
            }
        } else {
            diffStr = "—";
            badgeColor = 0xFFECEFF1;
            textColor  = 0xFF78909C;
        }

        // 绘制带有淡色小圆角的背景气泡 (Badge)
        Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(badgeColor);

        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(24f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float textW = textPaint.measureText(diffStr);

        RectF badgeRect = new RectF(
                690f,
                y - 28f,
                690f + textW + 28f,
                y + 12f
        );
        canvas.drawRoundRect(badgeRect, 8f, 8f, badgePaint);
        
        // 绘制气泡中的变化值
        canvas.drawText(diffStr, badgeRect.left + 14f, y - 2f, textPaint);

        // 绘制行下方的极细分割虚线，保持网格整洁
        canvas.drawLine(88f, y + 24f, CARD_W - 88f, y + 24f, linePaint);
    }

    private static float getWeightAtOrBefore(AppDatabase db, long date) {
        List<WeightRecord> list = db.weightRecordDao().getRecordsByDateRangeSync(
                date, date + 86400000L);
        if (list != null && !list.isEmpty()) return list.get(0).getWeight();
        WeightRecord prev = db.weightRecordDao().getLatestRecordBeforeSync(date + 86400000L);
        return prev != null ? prev.getWeight() : 0;
    }

    private static float getMeasurementAtOrBefore(AppDatabase db, String type, long date) {
        List<BodyMeasurement> list = db.bodyMeasurementDao().getByTypeAndDateRangeSync(
                type, date, date + 86400000L);
        if (list != null && !list.isEmpty()) return list.get(list.size() - 1).getValue();
        List<BodyMeasurement> all = db.bodyMeasurementDao().getByTypeAndDateRangeSync(
                type, 0, date + 86400000L);
        if (all != null && !all.isEmpty()) return all.get(all.size() - 1).getValue();
        return 0;
    }
}
