🚀 App 2.0 深度优化指令：饮食分餐、智能份数与逻辑修复
指令目标： 本次迭代旨在解决 UI 留白过大问题，重构饮食记录逻辑（支持分餐、按份数计算），扩展本地食物库，并修复每日打卡的逻辑漏洞。

任务清单：

1. UI 视觉紧凑化与排版优化 (UI Compact & Layout)
通用卡片瘦身：

检查 item_training_plan.xml (训练列表), item_food_record.xml (食物列表), 以及 fragment_profile.xml 中的顶部个人信息卡片。

减少高度： 将卡片内部容器的 paddingVertical 统一减少至 12dp。

减少间距： 将列表项之间的 marginBottom 减少至 8dp。

训练计划卡片重构 (item_training_plan.xml):

目前排版混乱。请重写为标准的 左图右文 布局：

左侧： ShapeableImageView (64dp x 64dp, 圆角 8dp, scaleType="centerCrop").

右侧： 垂直 LinearLayout (包含 标题, 描述, 组数信息)。

右下角/最右侧： 保留红色的“删除”文本。

确保文字垂直居中对齐图片，不再留有大片空白。

2. 饮食记录核心升级 (Diet Logic Upgrade)
A. 数据库升级 (FoodLibrary & FoodRecord):

FoodLibrary 新增字段：

unitName (String): 单位名称 (如 "碗", "个", "盘").

weightPerUnit (int): 每单位对应的克数 (如 1碗=150g).

FoodRecord 新增字段：

mealType (int): 0=早餐, 1=午餐, 2=晚餐, 3=加餐.

servings (float): 摄入份数 (如 1.5 碗).

B. 添加食物弹窗改造 (dialog_add_food.xml):

输入框变更：

搜索框 Hint 改为 "食物"。

重量输入框 彻底改为 "摄入份数" (InputType=numberDecimal)。

单位动态显示： 在份数输入框后方添加一个 TextView，当选中食物时，显示其单位（例如选中米饭，显示 "碗"）。

新增分餐选择： 添加一个 RadioGroup (横向)，包含 4 个 RadioButton: "早餐", "午餐", "晚餐", "加餐"。默认选中当前时间段对应的餐点。

C. 列表展示优化 (fragment_diet.xml):

不再是一股脑显示。请使用 带标题的分组列表 (Sectioned List) 或者简单的逻辑：在 DietAdapter 中根据 mealType 对数据排序，并在 UI 上用不同的图标或文字区分餐点（例如在食物名称前加 "[早餐]" 标签）。

3. 本地化数据扩充 (Data Localization)
AppDatabase 预填充数据升级：

请清空旧数据，预写入 30+ 种中国常见家常菜和主食。

数据示例：

"米饭": 116千卡/100g, 单位"碗", 单重150g.

"西红柿炒蛋": 85千卡/100g, 单位"盘", 单重300g.

"宫保鸡丁": 160千卡/100g, 单位"盘", 单重350g.

"煮鸡蛋": 143千卡/100g, 单位"个", 单重50g.

"肉包子": 250千卡/100g, 单位"个", 单重80g.

"豆浆": 35千卡/100g, 单位"杯", 单重250g.

4. 打卡逻辑漏洞修复 (Check-in Logic Fix)
问题描述： 目前 CheckInViewModel 逻辑是：如果完成数 >= 总计划数，就判定为打卡成功。导致当总计划数为 0 时，直接显示为“已打卡（绿色）”。

修复逻辑：

修改 isAllCompleted 判定条件：

return totalPlans > 0 && completedPlans == totalPlans;

即：只有当今日有计划(>0) 且 全部完成时，才算打卡成功。

如果今日无计划，右上角圆圈应保持灰色（未打卡状态），或者显示一种特殊的“无计划”状态（空心圈）。

UI 反馈： 确保 fragment_check_in.xml 顶部的 7 个圆圈严格遵循此逻辑变色。

请直接给出以下文件的完整代码：

item_training_plan.xml (重构后的紧凑布局)。

dialog_add_food.xml (增加份数和分餐选择)。

AppDatabase.java (包含中国菜谱的新数据)。

FoodLibrary.java & FoodRecord.java (更新字段)。

CheckInViewModel.java (修复打卡判定逻辑)。

DietViewModel.java (处理份数计算热量逻辑：totalCal = servings * weightPerUnit * (calPer100g / 100)).

fragment_profile.xml (个人卡片高度调优)。