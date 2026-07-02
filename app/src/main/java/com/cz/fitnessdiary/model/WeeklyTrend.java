package com.cz.fitnessdiary.model;

import java.util.List;

/**
 * 周度趋势数据 - v3.0
 * 用于展示某项指标在一周内的变化趋势
 */
public class WeeklyTrend {

    public String label;               // 指标名称（如 "体重", "热量"）
    public String unit;                // 单位（如 "kg", "kcal"）
    public List<Float> values;         // 每日数值序列（7 天）
    public float change;               // 变化量（last - first）
    public int direction;              // 变化方向：1=上升好, -1=下降好, 0=中性

    /**
     * 构造器，自动计算 change
     *
     * @param label  指标名称
     * @param unit   单位
     * @param values 数值序列
     */
    public WeeklyTrend(String label, String unit, List<Float> values) {
        this.label = label;
        this.unit = unit;
        this.values = values;
        if (values != null && values.size() >= 2) {
            this.change = values.get(values.size() - 1) - values.get(0);
        } else {
            this.change = 0f;
        }
    }

    /**
     * 返回格式化的变化文本，例如 "+0.5kg" 或 "-0.3kcal"
     *
     * @return 格式化字符串
     */
    public String getChangeText() {
        String sign = change >= 0 ? "+" : "";
        return sign + String.format("%.1f", change) + unit;
    }
}
