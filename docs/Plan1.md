🚀 健身日记 App 2.0：智能化升级方案 
指令目标： 在现有 FitnessDiary 项目基础上进行大规模功能重构和升级。请保持 MVVM 架构和 Material Design 3 风格。

1. 数据库升级 (Room Database Migration)
请修改 Entity 定义并更新 AppDatabase：

修改 TrainingPlan (增加细节 & 媒体):

新增字段 sets (int, 组数).

新增字段 reps (int, 每组次数/时间).

新增字段 mediaUri (String, 用于存储图片或视频的本地文件路径).

修改 User (增加健身目标):

新增字段 gender (int, 0女 1男).

新增字段 goalType (int, 0=减脂, 1=增肌, 2=保持).

新增字段 activityLevel (float, 活动系数, 如 1.2).

新增 FoodLibrary (本地食物库):

字段：name (String, 主键), caloriesPer100g (int).

重要： 请在 AppDatabase 的 onCreate 回调中，预先插入 20-30 种常见食物数据（如：米饭-116, 鸡胸肉-165, 牛奶-54, 鸡蛋-143, 苹果-52 等），作为基础数据源。

2. 功能模块升级
A. 欢迎页升级 (WelcomeFragment)
逻辑升级： 在输入身高体重后，增加“性别”和“目标”选择（使用 ChipGroup 或 RadioGroup）。

智能计算：

在 ViewModel 中实现 BMR 公式 (Mifflin-St Jeor)。

根据用户选择的目标，自动计算每日推荐摄入量 (TDEE)。

减脂 = TDEE - 500，增肌 = TDEE + 300。

将计算出的 dailyCalorieTarget 存入 SharedPreference 或 User 表。

B. 训练计划升级 (PlanFragment)
添加/编辑弹窗升级：

增加“组数”、“次数/时长”输入框。

增加“添加演示”按钮，点击调用 ActivityResultLauncher 打开手机相册选择图片或视频。

选中后，在弹窗内显示缩略图。

列表展示升级：

Item 布局左侧增加 ImageView 显示缩略图（如果有）。

显示详细数据（例如：“4组 x 12个”）。

C. 饮食记录升级 (DietFragment) —— 核心智能化
输入方式彻底改变：

将输入框改为 AutoCompleteTextView。

关联 FoodLibrary 数据：用户输入“鸡”自动联想“鸡胸肉”。

选中食物后，自动填入卡路里系数，用户只需输入“摄入重量(克)”，App 自动计算单餐热量。

仪表盘反馈：

顶部卡片改为环形进度条 (CircularProgressIndicator)。

显示：已摄入 / 目标 (例如 1500 / 1800)。

智能反馈颜色：

减脂模式下：超过目标值变红（警告）。

增肌模式下：未达目标值变黄（提醒），达标变绿。

底部增加文本提示：“您今日热量缺口 300 千卡，继续保持！”

D. 每日打卡升级 (CheckInFragment)
增加打卡日历： 引入 CalendarView 或简易的一周视图，已打卡的日期显示绿色圆点。

增加“连续打卡”统计： 计算并在顶部显示“已连续坚持 X 天”。

3. 代码生成要求
工具类： 请生成 CalorieCalculatorUtils.java 处理复杂的数学公式。

图片加载： 使用 Glide 库（请自动添加到 build.gradle）来加载相册选中的图片/视频缩略图。

权限处理： 自动处理读取相册存储的运行时权限 (READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE)。

