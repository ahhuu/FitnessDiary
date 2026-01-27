package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.cz.fitnessdiary.database.entity.User;
import com.cz.fitnessdiary.repository.UserRepository;
import com.cz.fitnessdiary.utils.CalorieCalculatorUtils;

/**
 * 欢迎页 ViewModel - 2.0 版本
 * 添加智能卡路里计算功能
 */
public class WelcomeViewModel extends AndroidViewModel {

    private UserRepository userRepository;

    public WelcomeViewModel(@NonNull Application application) {
        super(application);
        userRepository = new UserRepository(application);
    }

    /**
     * 注册用户（1.0版本，兼容性保留）
     */
    public void registerUser(String name, float height, float weight) {
        User user = new User(name, height, weight, true);
        userRepository.insert(user);
    }

    /**
     * 注册用户（2.0版本，包含完整信息）
     * 
     * @param name          昵称
     * @param height        身高 (cm)
     * @param weight        体重 (kg)
     * @param age           年龄
     * @param gender        性别 (0=女, 1=男)
     * @param goalType      目标类型 (0=减脂, 1=增肌, 2=保持)
     * @param activityLevel 活动系数 (1.2-1.9)
     */
    public void registerUserWithGoal(String name, float height, float weight, int age,
            int gender, int goalType, float activityLevel) {
        // 1. 计算 BMR
        int bmr = CalorieCalculatorUtils.calculateBMR(gender, weight, height, age);

        // 2. 计算 TDEE
        int tdee = CalorieCalculatorUtils.calculateTDEE(bmr, activityLevel);

        // 3. 根据目标计算每日卡路里目标
        int dailyCalorieTarget = CalorieCalculatorUtils.calculateTargetCalories(tdee, goalType);

        // 4. 创建用户并保存
        User user = new User(name, height, weight, true,
                gender, goalType, activityLevel, dailyCalorieTarget, age);

        // 5. 设置昵称（将 name 同时设置到 nickname 字段）
        user.setNickname(name);

        // 6. 设置目标文字（根据 goalType）
        String goalText = "";
        switch (goalType) {
            case 0:
                goalText = "减脂";
                break;
            case 1:
                goalText = "增肌";
                break;
            case 2:
                goalText = "保持";
                break;
        }
        user.setGoal(goalText);

        userRepository.insert(user);
    }

    /**
     * 预览计算结果（不保存）
     * 用于在UI上实时显示计算结果
     * 
     * @return 每日卡路里目标
     */
    public int previewCalorieTarget(float height, float weight, int age,
            int gender, int goalType, float activityLevel) {
        int bmr = CalorieCalculatorUtils.calculateBMR(gender, weight, height, age);
        int tdee = CalorieCalculatorUtils.calculateTDEE(bmr, activityLevel);
        return CalorieCalculatorUtils.calculateTargetCalories(tdee, goalType);
    }
}
