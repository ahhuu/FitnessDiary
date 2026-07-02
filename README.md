# FitnessDiary (健身日记)

![FitnessDiary Logo](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

FitnessDiary 是一款极简、高效的 Android 个人健康 management 应用，提供每日打卡、训练计划、饮食记录、身体指标分析、成就系统与 AI 助手的全方位服务。

---

## 🌟 核心功能

- **🏠 健康仪表盘**：首页右上角健康评分环，五维度（训练/饮食/习惯/身体/坚持）综合评分，点击查看评分明细；健康日报卡片每日自动生成个性化健康简报，支持折叠展开。
- **➕ 快捷记录**：FAB 四功能区按钮快捷入口，一键直达常用操作。
- **🚀 每日打卡**：首页卡片式布局，记录连续坚持天数，追踪今日训练任务与热量消耗。
- **📋 训练计划**：月度日历历史页面，单元格显示训练标记；日期弹窗查看当日全维度摘要与训练详情。训练计划业务拆分为三个独立页面：计划管理、周/月数据统计、动作库。
- **🥗 智能饮食**：全天候记录餐点，自动计算热量及碳水、蛋白质等宏量营养素，饮食页顶部能量状态横条实时展示摄入 vs 消耗。
- **👣 步数追踪**：手机传感器自动记录 + 手动修正，支持每日目标配置。
- **😊 情绪记录**：每日 5 档表情选择，轻松记录心情起伏。
- **💧 健康习惯**：喝水、睡眠、用药、体重、围度、便便、经期等多维度习惯追踪。
- **⚖️ 目标体重**：基于用户身体数据与增肌/减脂目标科学计算目标体重及随时记录。
- **🏆 成就系统**：21 天打卡挑战、等级进度与勋章解锁，陪伴你持续坚持。
- **🤖 AI 私教**：集成大语言模型，提供智能训练计划制定、饮食分析与进度评估。
- **🎓 新手引导**：日历页面简要引导，帮助新用户快速了解核心功能。

---

## 🛠️ 技术架构

### 核心技术栈
- **开发语言**: Java 17 & Kotlin 1.9 (MVVM 架构)
- **本地存储**: Room Database 2.6+
- **界面设计**: Material Design 3, ViewBinding, Navigation Component (单 Activity 多 Fragment)
- **动画效果**: Lottie Animation
- **AI 服务**: DeepSeek API + 阿里云 DashScope SDK (Qwen 通义千问)

### 关键依赖库
- **数据可视化**: [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) (体重/睡眠/训练热量趋势统计图表)
- **网络与解析**: OkHttp 4.12 & Gson
- **图片加载**: Glide (含 GIF 动图支持，用于动作库动图预览)
- **条码扫描**: ZXing (扫描食品条形码快速录入)
- **AI 平台**: 阿里云 DashScope SDK (Qwen 通义千问)、DeepSeek API (智能训练计划制定与饮食分析)

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
