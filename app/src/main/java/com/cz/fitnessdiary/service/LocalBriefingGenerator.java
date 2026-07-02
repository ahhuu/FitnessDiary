package com.cz.fitnessdiary.service;

import com.cz.fitnessdiary.model.DailyHealthSnapshot;
import com.cz.fitnessdiary.model.HealthScoreBreakdown;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * 本地简报生成器 - v3.0
 * 纯规则引擎，无 Android 依赖。作为 AI 服务的降级方案。
 */
public final class LocalBriefingGenerator {

    private static final Random RANDOM = new Random();

    private static final String[] MOTIVATIONS = {
            "每一次努力，都是对自己的投资。继续坚持！",
            "健身是最好的整容，坚持是最好的天赋。",
            "昨天的汗水是今天的资本，明天的你会感谢今天努力的自己。",
            "不要等待完美的开始，开始就是完美。",
            "自律给人自由，坚持方得始终。",
            "每一滴汗水都在雕刻更好的自己。",
            "不积跬步，无以至千里；不积小流，无以成江海。",
            "身体是革命的本钱，健康是最大的财富。",
            "运动是良药，坚持是疗效。",
            "没有人能随随便便成功，自律的路上你并不孤单。",
            "当你觉得累的时候，你正在走上坡路。",
            "今天的不习惯，是明天的好习惯。",
            "健康的身体是实现所有梦想的基础。",
            "与其仰望别人，不如塑造自己。",
            "坚持21天，养成一个好习惯；坚持一年，遇见一个全新的自己。",
            "不必追求完美，只需比昨天的自己更好。",
            "每一次坚持都是对意志力的锻炼。",
            "健康的生活方式，从每一个小习惯开始。",
            "你流下的汗水，终将浇灌出美好的未来。",
            "相信时间的力量，日积月累，终见成效。"
    };

    private LocalBriefingGenerator() {
        // Utility class
    }

    /**
     * 基于每日健康快照和评分明细生成简报
     *
     * @param snapshot  每日健康数据快照
     * @param breakdown 健康评分明细
     * @return 填充完整的 DailyBriefing
     */
    public static DailyBriefingService.DailyBriefing generate(
            DailyHealthSnapshot snapshot, HealthScoreBreakdown breakdown) {
        DailyBriefingService.DailyBriefing briefing = new DailyBriefingService.DailyBriefing();
        briefing.greeting = generateGreeting(snapshot);
        briefing.scoreComment = generateScoreComment(breakdown.totalScore);
        briefing.highlights = generateHighlights(snapshot, breakdown);
        briefing.suggestion = generateSuggestion(snapshot, breakdown);
        briefing.motivation = pickMotivation();
        briefing.isLocal = true;
        briefing.generatedAt = System.currentTimeMillis();
        return briefing;
    }

    /**
     * 根据时段生成问候语
     */
    static String generateGreeting(DailyHealthSnapshot snapshot) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) {
            return "早上好！新的一天开始了，来看看昨天的表现吧。";
        } else if (hour >= 12 && hour < 14) {
            return "中午好！休息时间回顾一下昨天的健康数据。";
        } else if (hour >= 14 && hour < 18) {
            return "下午好！抽空看看你昨天的健康表现。";
        } else if (hour >= 18 && hour < 22) {
            return "晚上好！一起来回顾昨天的健康数据吧。";
        } else {
            return "夜深了，来看看今天的总结，做好明天计划吧。";
        }
    }

    /**
     * 根据总分生成评分评语
     */
    static String generateScoreComment(int totalScore) {
        if (totalScore >= 85) {
            return "表现优异！各项指标均衡，继续保持！";
        } else if (totalScore >= 70) {
            return "不错！大部分指标达标，还有提升空间。";
        } else if (totalScore >= 60) {
            return "中规中矩，还有不少提升空间，明天加油！";
        } else if (totalScore >= 40) {
            return "今天有些放松，几个关键指标需要加强关注。";
        } else {
            return "今天有些放松，明天加油，从一个小目标开始吧！";
        }
    }

    /**
     * 根据数据生成亮点列表
     */
    static List<String> generateHighlights(DailyHealthSnapshot snapshot, HealthScoreBreakdown breakdown) {
        List<String> highlights = new ArrayList<>();

        // 热量控制
        int totalOut = snapshot.bmr + snapshot.exerciseCalories + snapshot.stepCalories;
        if (totalOut > 0) {
            float ratio = (float) snapshot.dietCalories / totalOut;
            if (ratio >= 0.8f && ratio <= 1.1f) {
                highlights.add("热量控制得当");
            }
        }

        // 睡眠充足
        if (snapshot.sleepHours >= 7 && snapshot.sleepHours <= 9) {
            highlights.add("睡眠充足");
        }

        // 训练强度到位
        int totalExerciseCal = snapshot.exerciseCalories + snapshot.stepCalories;
        if (totalExerciseCal > 300) {
            highlights.add("训练强度到位");
        }

        // 训练计划完成
        if (snapshot.totalPlans > 0 && snapshot.completedPlans >= snapshot.totalPlans) {
            highlights.add("今日训练计划全部完成");
        }

        // 步数达标
        if (snapshot.steps >= 8000) {
            highlights.add("步数达标");
        } else if (snapshot.steps >= 5000) {
            highlights.add("步数尚可，还有提升空间");
        }

        // 饮水充足
        if (snapshot.waterMl >= 2000) {
            highlights.add("饮水充足");
        }

        // 心情不错
        if (snapshot.moodLevel >= 4) {
            highlights.add("心情不错");
        }

        // 坚持打卡
        if (snapshot.consecutiveDays >= 7) {
            highlights.add("连续打卡 " + snapshot.consecutiveDays + " 天，坚持不易");
        }

        // 如果没有亮点，给出一个通用提示
        if (highlights.isEmpty()) {
            highlights.add("今天数据较少，记得多记录健康数据哦");
        }

        return highlights;
    }

    /**
     * 根据数据生成建议
     */
    static String generateSuggestion(DailyHealthSnapshot snapshot, HealthScoreBreakdown breakdown) {
        List<String> suggestions = new ArrayList<>();

        // 热量平衡过高（摄入远超消耗）
        if (snapshot.energyBalance > 500) {
            suggestions.add("摄入热量偏高，建议减少主食份量");
        } else if (snapshot.energyBalance > 300) {
            suggestions.add("热量略有盈余，注意控制零食摄入");
        }

        // 步数不足
        if (snapshot.steps < 3000) {
            suggestions.add("今天步数严重不足，建议出门散步至少30分钟");
        } else if (snapshot.steps < 6000) {
            int deficit = 8000 - snapshot.steps;
            suggestions.add("步数还差 " + deficit + " 步，饭后多走动走动吧");
        } else if (snapshot.steps < 8000) {
            int deficit = 8000 - snapshot.steps;
            suggestions.add("离步数目标还差 " + deficit + " 步，再走一会儿吧");
        }

        // 睡眠不足
        if (snapshot.sleepHours > 0 && snapshot.sleepHours < 6) {
            suggestions.add("睡眠严重不足，今晚建议提前1小时上床");
        } else if (snapshot.sleepHours > 0 && snapshot.sleepHours < 7) {
            suggestions.add("睡眠略有不足，建议提前30分钟休息");
        }

        // 饮水不足
        if (snapshot.waterMl > 0 && snapshot.waterMl < 1000) {
            suggestions.add("饮水严重不足，记得多喝水（目标2000ml）");
        } else if (snapshot.waterMl > 0 && snapshot.waterMl < 1500) {
            suggestions.add("饮水量偏低，再喝几杯水就达标了哦");
        }

        // 训练计划未完成
        if (snapshot.totalPlans > 0 && snapshot.completedPlans == 0) {
            suggestions.add("今日训练计划未完成，抽时间动起来吧");
        } else if (snapshot.totalPlans > 0 && snapshot.completedPlans < snapshot.totalPlans) {
            suggestions.add("还有训练计划未完成，加油完成剩下的");
        }

        // 如果没有任何建议，给出积极鼓励
        if (suggestions.isEmpty()) {
            suggestions.add("今天表现不错，继续保持良好的生活习惯！");
        }

        // 返回第一条建议（最关键的）
        return suggestions.get(0);
    }

    /**
     * 随机选取一条鼓励语
     */
    static String pickMotivation() {
        return MOTIVATIONS[RANDOM.nextInt(MOTIVATIONS.length)];
    }
}
