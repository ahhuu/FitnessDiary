package com.cz.fitnessdiary.service;

import com.cz.fitnessdiary.database.entity.FoodLibrary;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 食物数据解析工具类
 * 负责从大模型的文本中提取食物名称、热量、蛋白质和碳水
 */
public class FoodParser {

    /**
     * 从 Markdown/文本中解析第一个匹配到的食物对象
     */
    public static FoodLibrary parseFirstFood(String text) {
        if (text == null || text.isEmpty())
            return null;

        // 1. 提取名称 (通常在 ### 或 ** 后面，或者第一行)
        String name = "未知食物";
        Pattern namePattern = Pattern.compile("(?m)^(?:###|\\*\\*|名称)[:：]?\\s*([^\\n（(：:*]+)");
        Matcher nameMatcher = namePattern.matcher(text);
        if (nameMatcher.find()) {
            name = nameMatcher.group(1).trim().replace("*", "");
        }

        // 2. 提取热量 (寻找 "热量"、"能量"、"卡路里" 后的数字)
        // 允许 **热量** 这种加粗格式，允许 "千卡/大卡/kcal"
        int calories = 0;
        Pattern calPattern = Pattern.compile(
                "(?:\\*\\*)?(?:热量|能量|卡路里)(?:\\*\\*)?[:：]?\\s*(?:约)?\\s*(\\d+)(?:-\\d+)?\\s*(?:大卡|kcal|千卡)",
                Pattern.CASE_INSENSITIVE);
        Matcher calMatcher = calPattern.matcher(text);
        if (calMatcher.find()) {
            calories = Integer.parseInt(calMatcher.group(1));
        }

        // 3. 提取蛋白质
        double protein = 0;
        Pattern proPattern = Pattern.compile(
                "(?:\\*\\*)?蛋白质(?:\\*\\*)?[:：]?\\s*(?:约)?\\s*(\\d+(?:\\.\\d+)?)(?:-\\d+(?:\\.\\d+)?)?\\s*g",
                Pattern.CASE_INSENSITIVE);
        Matcher proMatcher = proPattern.matcher(text);
        if (proMatcher.find()) {
            protein = Double.parseDouble(proMatcher.group(1));
        }

        // 4. 提取碳水
        double carbs = 0;
        Pattern carbPattern = Pattern.compile(
                "(?:\\*\\*)?(?:碳水|碳水化合物)(?:\\*\\*)?[:：]?\\s*(?:约)?\\s*(\\d+(?:\\.\\d+)?)(?:-\\d+(?:\\.\\d+)?)?\\s*g",
                Pattern.CASE_INSENSITIVE);
        Matcher carbMatcher = carbPattern.matcher(text);
        if (carbMatcher.find()) {
            carbs = Double.parseDouble(carbMatcher.group(1));
        }

        // 只有获取到热量才认为解析有效
        if (calories > 0) {
            return new FoodLibrary(name, calories, protein, carbs, "g", 100, "AI识别");
        }
        return null;
    }
}
