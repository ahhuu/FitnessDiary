**App 2.0 核心逻辑升级：智能饮食与排期系统**

**任务目标：**
在现有 UI 基础上，深度完善业务逻辑。重点解决饮食记录的单位换算、营养素追踪、训练计划的分类与按周排期，以及自动化的打卡判定逻辑。

**任务清单：**

#### 1. 数据库升级 (Database Migration)
请修改 Entity 类，并更新 `FoodDatabase` 的预填充数据：

* **`FoodLibrary` (食物库) 新增字段：**
    * `proteinPer100g` (double): 每100克蛋白质含量。
    * `carbsPer100g` (double): 每100克碳水含量.
    * `servingUnit` (String): 常用单位 (如 "个", "碗", "片").
    * `weightPerUnit` (int): 常用单位对应的克数 (如 1碗=150g).
    * **预填充数据更新：** 请重新生成 `AppDatabase` 回调中的初始化数据，务必包含这 4 个新字段。
        * *示例：* "米饭", 热量116, 蛋白2.6, 碳水25, 单位"碗", 单重150.
        * *示例：* "鸡蛋", 热量143, 蛋白13, 碳水1, 单位"个", 单重50.

* **`FoodRecord` (饮食记录) 新增字段：**
    * `protein` (double): 该条记录包含的蛋白质.
    * `carbs` (double): 该条记录包含的碳水.

* **`User` (用户) 新增字段：**
    * `targetProtein` (int): 每日目标蛋白质 (克).
    * `targetCarbs` (int): 每日目标碳水 (克).

* **`TrainingPlan` (训练计划) 新增字段：**
    * `category` (String): 训练部位/分类 (如 "胸部", "有氧", "核心").
    * `scheduledDays` (String): 计划执行日 (格式如 "1,3,5" 代表周一、三、五；"0" 代表每天).

#### 2. UI 组件修复与升级 (UI Polishing)

* **修复搜索框重叠 (`dialog_add_food.xml`):**
    * 目前的 `EditText` 与 Hint 文字重叠。
    * **必须替换为** `com.google.android.material.textfield.TextInputLayout` 配合 `TextInputEditText`。
    * 样式设置：`style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"`。
    * 确保 Hint 浮动显示，不再遮挡输入内容。

* **饮食页显示宏量营养素 (`fragment_diet.xml`):**
    * 在顶部卡片的“热量进度条”下方，新增两个较细的进度条（使用 `LinearProgressIndicator`）：
        * 左侧：**蛋白质进度** (颜色建议淡蓝色)。下方文字标注 "蛋白质: 已摄入/目标"。
        * 右侧：**碳水进度** (颜色建议淡黄色)。下方文字标注 "碳水: 已摄入/目标"。

#### 3. 业务逻辑实现 (Business Logic)

* **A. 智能饮食记录 (`DietViewModel`):**
    * **单位换算逻辑：** 在添加食物弹窗中，默认填入该食物的 `weightPerUnit`（例如选鸡蛋，自动填50克），让用户感知“1个”的概念。当然用户也可以手动修改克数。
    * **宏量计算：** 添加记录时，根据输入重量自动计算 `protein` 和 `carbs` 并存入数据库。
    * **目标推荐：** 在 `ProfileViewModel` 计算 TDEE 时，同步计算宏量目标：
        * *增肌模式：* 蛋白 = 体重(kg) * 2.0; 剩余热量分配给碳水。
        * *减脂模式：* 蛋白 = 体重(kg) * 1.5; 限制碳水。

* **B. 训练排期与分类 (`PlanFragment`):**
    * **添加计划弹窗升级：** 新增“分类”输入框（或下拉选框）和“重复时间”选择器（七个 CheckBox 代表周一到周日）。
    * **列表归类：** `RecyclerView` 列表不再是一股脑显示，而是根据 `category` 进行分组展示（如果实现复杂，至少在 Item 上显示分类标签）。

* **C. 智能打卡逻辑 (`CheckInViewModel`):**
    * **按日过滤：** `getTodayPlans()` 方法必须升级。先获取今天是星期几 (Calendar.DAY_OF_WEEK)，然后**只筛选出** `scheduledDays` 包含今天的计划。
    * **全勤判定：**
        * 监听 CheckBox 点击事件。
        * 每次点击后，查询：`今日已完成任务数` vs `今日应完成任务数`。
        * 如果 **相等 (All Completed)** -> 视为“今日打卡成功”，更新右上角的“连续坚持天数”。
        * (之前的逻辑是点一个算一次打卡，现在改为“完成所有任务”才算今日打卡)。

**请直接给出以下文件的完整代码：**
1.  **Entity 类：** `FoodLibrary.java`, `User.java`, `TrainingPlan.java`。
2.  **DAO 类：** `FoodLibraryDao.java` (确保能查新字段)。
3.  **布局文件：** `dialog_add_food.xml` (修复搜索框), `fragment_diet.xml` (增加营养素进度条), `dialog_add_plan.xml` (增加排期选择)。
4.  **ViewModel 类：** `DietViewModel.java` (处理营养素), `CheckInViewModel.java` (处理按日过滤和全勤判断)。
5.  **Database:** `AppDatabase.java` (更新预填充数据)。