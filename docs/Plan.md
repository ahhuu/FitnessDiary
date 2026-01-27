指令目标： 请在当前目录下为一个 Android App 生成完整的项目结构和代码。

1. 项目基础配置

项目名称： FitnessDiary

包名： com.cz.fitnessdiary

语言： Java (必须是 Java，不要 Kotlin)

构建工具： Gradle (Groovy DSL)

最低兼容版本： Android API 26 (Oreo)

核心架构： MVVM (Model-View-ViewModel)

关键依赖库：

Android Jetpack (ViewModel, LiveData, Lifecycle)

Room Database (本地数据库)

ViewBinding (替代 findViewById)

Navigation Component (单 Activity 多 Fragment 架构)

Material Design 3 (UI 组件库)

2. 数据库设计 (Room) 请创建 database 包，并生成以下 Entity、DAO 和 Database 类：

User (Entity): 字段包含 uid (主键), name (String), height (float), weight (float), isRegistered (boolean)。

TrainingPlan (Entity): 字段包含 planId (主键), name (String), description (String), createTime (long)。

DailyLog (Entity): 字段包含 logId (主键), planId (外键), date (long, 对应当天的 0点时间戳), isCompleted (boolean)。

FoodRecord (Entity): 字段包含 foodId (主键), foodName (String), calories (int), recordDate (long)。

3. UI 页面结构与导航 请创建 ui 包，并生成以下 Fragment 和 Activity：

MainActivity: 包含 FragmentContainerView 和 BottomNavigationView。

WelcomeFragment (或 Activity):

逻辑： App 启动时检查 User 表。如果为空，显示此页面；否则直接显示主页。

功能： 输入昵称、身高、体重，点击“开始”保存用户并标记注册成功。

CheckInFragment (首页 - 每日打卡):

UI： 顶部显示日期，下方用 MaterialCardView 列表展示当日训练计划。

功能： 点击 CheckBox 完成打卡，数据存入 DailyLog。

PlanFragment (第二页 - 计划管理):

UI： 列表展示所有计划，右下角 FAB 按钮添加新计划。

DietFragment (第三页 - 饮食记录):

UI： 顶部卡片显示“今日总热量”，下方列表显示今日食物。

功能： 记录食物，自动计算总热量。

4. UI 设计规范 (Gemini 风格)

风格： 严格遵循 Material Design 3。

配色： 莫兰迪色系（清新的薄荷绿或天空蓝为主色），背景色为 #F5F5F5 (Off-white)。

组件： 所有列表项必须包含在 MaterialCardView 中，圆角 16dp，卡片背景纯白，去掉分割线。

排版： 使用大号标题，增加页面边距 (Padding 16dp+)，保持界面极简、干净。

5. 输出要求 请直接生成以下核心文件：

app/build.gradle (完整的依赖配置)

AppDatabase.java 和所有 Entity/DAO 类

MainActivity.java 和它的布局文件

所有 Fragment 的 Java 类和 XML 布局文件

相关的 ViewModel 和 Repository 类