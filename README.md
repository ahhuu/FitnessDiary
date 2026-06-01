# FitnessDiary (健身日记)

![FitnessDiary Logo](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

FitnessDiary 是一款极简、高效的 Android 个人健康 management 应用，提供每日打卡、训练计划、饮食记录、身体指标分析、成就系统与 AI 助手的全方位服务。

---

## 🌟 核心功能

- **🚀 每日打卡**：首页卡片式布局，记录连续坚持天数，追踪今日训练任务与热量消耗。
- **📋 训练计划**：分类管理动作，支持组数/次数设定，提供直观的运动消耗计算。
- **🥗 智能饮食**：全天候记录餐点，自动计算热量及碳水、蛋白质等宏量营养素。
- **👣 步数追踪**：手机传感器自动记录 + 手动修正，支持每日目标配置。
- **😊 情绪记录**：每日 5 档表情选择，轻松记录心情起伏。
- **💧 健康习惯**：喝水、睡眠、用药、体重、围度、便便、经期等多维度习惯追踪。
- **🏆 成就系统**：21 天打卡挑战、等级进度与勋章解锁，陪伴你持续坚持。
- **🤖 AI 私教**：集成了先进的大语言模型，提供智能训练计划制定、饮食分析与进度评估。

---

## 🛠️ 技术架构

### 核心技术栈
- **开发语言**: Java 17 & Kotlin 1.9 (MVVM 架构)
- **本地存储**: Room Database 2.6+
- **界面设计**: Material Design 3, ViewBinding, Navigation Component
- **动画效果**: Lottie Animation

### 关键依赖库
- **数据可视化**: [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) (提供体重、睡眠等趋势统计图表)
- **网络与解析**: OkHttp 4.12 & Gson
- **图片加载**: Glide
- **条码扫描**: ZXing (支持扫描食品条形码快速录入)

---

## 🚀 快速上手

### 1. 配置 AI 密钥 (可选)
在项目根目录下的 `local.properties` 文件中配置您的 AI 平台 API Key：
```properties
gemini.api.key=YOUR_GEMINI_KEY
deepseek.api.key=YOUR_DEEPSEEK_KEY
qwen.api.key=YOUR_QWEN_KEY
```

### 2. 构建与运行
```bash
# Windows
gradlew.bat assembleDebug
gradlew.bat installDebug

# macOS / Linux
./gradlew assembleDebug
./gradlew installDebug
```
