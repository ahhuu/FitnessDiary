package com.cz.fitnessdiary.utils;

import java.util.Calendar;
import java.util.List;

/**
 * 日期工具类
 * 处理日期相关的计算和格式化
 */
public class DateUtils {
    
    /**
     * 获取今天0点的时间戳
     * 
     * @return 今天0点的时间戳 (毫秒)
     */
    public static long getTodayStartTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
    
    /**
     * 获取指定日期的0点时间戳
     * 
     * @param timestamp 任意时间戳
     * @return 该日期0点的时间戳
     */
    public static long getDayStartTimestamp(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
    
    /**
     * 获取明天0点的时间戳
     * 
     * @return 明天0点的时间戳 (毫秒)
     */
    public static long getTomorrowStartTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
    
    /**
     * 计算连续打卡天数
     * 从最近的一天往前推，遇到未打卡的日期就停止
     * 
     * @param checkedDateTimestamps 已打卡日期的时间戳列表（已按时间戳降序排列）
     * @return 连续打卡天数
     */
    public static int calculateConsecutiveDays(List<Long> checkedDateTimestamps) {
        if (checkedDateTimestamps == null || checkedDateTimestamps.isEmpty()) {
            return 0;
        }
        
        long todayStart = getTodayStartTimestamp();
        long oneDayMillis = 24 * 60 * 60 * 1000L;
        
        int consecutiveDays = 0;
        long expectedDate = todayStart;
        
        for (Long checkedDate : checkedDateTimestamps) {
            // 允许一天的误差（考虑时区等因素）
            long diff = Math.abs(expectedDate - checkedDate);
            if (diff < oneDayMillis / 2) {
                consecutiveDays++;
                expectedDate -= oneDayMillis; // 往前推一天
            } else if (checkedDate < expectedDate) {
                // 遇到不连续的日期，结束计算
                break;
            }
        }
        
        return consecutiveDays;
    }
    
    /**
     * 判断指定日期是否是今天
     * 
     * @param timestamp 时间戳
     * @return true 如果是今天
     */
    public static boolean isToday(long timestamp) {
        long todayStart = getTodayStartTimestamp();
        long todayEnd = getTomorrowStartTimestamp();
        return timestamp >= todayStart && timestamp < todayEnd;
    }
    
    /**
     * 获取两个日期之间相差的天数
     * 
     * @param startTimestamp 开始时间戳
     * @param endTimestamp 结束时间戳
     * @return 相差天数
     */
    public static int getDaysBetween(long startTimestamp, long endTimestamp) {
        long diff = Math.abs(endTimestamp - startTimestamp);
        return (int) (diff / (24 * 60 * 60 * 1000L));
    }
    
    /**
     * 获取本周的日期列表（周一到周日的0点时间戳）
     * 
     * @return 本周日期列表
     */
    public static long[] getThisWeekDates() {
        Calendar calendar = Calendar.getInstance();
        
        // 设置为本周周一
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int offset = (dayOfWeek == Calendar.SUNDAY) ? -6 : (2 - dayOfWeek);
        calendar.add(Calendar.DAY_OF_MONTH, offset);
        
        // 设置为0点
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        long[] weekDates = new long[7];
        for (int i = 0; i < 7; i++) {
            weekDates[i] = calendar.getTimeInMillis();
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        return weekDates;
    }
}
