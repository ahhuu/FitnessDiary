**App 3.1 饮食版块重构：网格视图、上下文交互与食物百科**

**任务目标：**
基于用户反馈，彻底重构饮食记录页面的 UI 布局与交互逻辑。目标是提高屏幕空间利用率（采用 2x2 网格），简化添加流程（去除冗余选项），并新增食物查询入口。

**任务清单：**

#### 1. 饮食主页布局重构 (`fragment_diet.xml`)
* **根布局调整：** 使用 `NestedScrollView` 包裹内容，确保小屏幕也能滚动。
* **顶部：** 保持原有的 "今日已摄入" 仪表盘卡片不变。
* **中部 - 餐点网格 (Meal Grid):**
    * **废弃** 原有的 `RecyclerView` 列表模式。
    * **新增** 一个 `GridLayout` (columnCount="2") 或 `ConstraintLayout`。
    * **放置 4 个固定卡片：** 分别代表 [早餐]、[午餐]、[晚餐]、[加餐]。
    * **布局排布：**
        * 第一行左：早餐 | 第一行右：午餐
        * 第二行左：晚餐 | 第二行右：加餐
    * *样式细节：* 卡片高度固定（例如 `160dp`），保持整齐。
* **底部 - 食物百科 (Food Wiki):**
    * 在网格下方添加一个 **搜索入口卡片**。
    * **样式：** 纯白圆角背景，中间是一个 `EditText` (不可编辑，仅作为按钮) 或 `TextView`。
    * **内容：** 图标 🔍 + 文字 "查询食物热量/营养素..."。
    * *交互：* 点击该区域弹出提示 "食物百科功能开发中"。
* **移除冗余：** 将右下角的悬浮按钮 (`FloatingActionButton`) **设为 GONE 或删除**。

#### 2. 餐点卡片设计 (`include_meal_card.xml`)
创建一个独立的 layout 文件用于复用（include 4次）。
* **UI 结构：**
    * **右上角：** 一个小的、精致的绿色加号图标 (`ImageButton`), 背景透明或淡绿圆形。
    * **左上角：** 餐点名称 (如 "早餐")，字号 16sp，加粗。
    * **左下角/中间：** 显示摄入热量 (如 "360 千卡") 或简略的食物列表 (如 "鸡蛋, 牛奶...")。
    * **去除** 原来巨大的 "点击添加食物" 按钮。

#### 3. 添加食物弹窗优化 (`dialog_add_food.xml` & Logic)
* **布局美化：**
    * **缩短输入框：** 将 "食物名称" 输入框的左右 Margin 增大，或者设置固定宽度，使其看起来不那么宽大空旷。建议使用 `TextInputLayout` 的 `OutlinedBox` 风格，看起来更精致。
    * **隐藏餐点类型：** 将 `RadioGroup` (餐点类型选择) 的 `visibility` 设为 **GONE**。
* **上下文逻辑 (`DietFragment.java`):**
    * **传参：** 修改 `showAddFoodDialog` 方法，增加参数 `int mealType`。
    * **自动定位：** 当点击“早餐卡片”上的加号时，调用 `showAddFoodDialog(MEAL_BREAKFAST)`。
    * **保存逻辑：** 在保存数据时，直接使用传入的 `mealType`，无需用户手动选择。

#### 4. 代码逻辑适配 (`DietFragment.java` & `DietViewModel.java`)
* **数据绑定更新：**
    * 由于移除了 RecyclerView，需要在 `onViewCreated` 中分别观察 ViewModel 的数据。
    * 根据 `mealType` 过滤数据，分别更新 4 个卡片的内容 (热量总和、食物摘要)。
    * *提示：* 可以写一个辅助方法 `updateMealCard(View cardView, List<FoodRecord> records)` 来复用逻辑。

**请直接给出以下文件的完整代码：**
1.  **`fragment_diet.xml`** (全新的网格布局 + 底部搜索).
2.  **`include_meal_card.xml`** (新的紧凑型卡片布局).
3.  **`dialog_add_food.xml`** (隐藏单选框，美化输入框).
4.  **`DietFragment.java`** (适配新的卡片点击逻辑和数据更新逻辑).


**App 3.2 饮食版块专项修复：联想输入、布局优化与食物百科**

**任务目标：**
修复饮食记录中食物输入无法自动关联数据库的问题，优化网格布局的视觉间距，并实装底部的“食物百科”查询功能。

**任务清单：**

#### 1. 修复食物输入与自动匹配 (`DietFragment.java`)
**问题：** 输入 "鸡" 没有下拉提示；且直接添加后热量为 0，未匹配数据库。
**修复逻辑：**
* **绑定适配器：** 在 `showSmartAddFoodDialog` (或 `showAddFoodDialog`) 中，确保获取 `FoodLibrary` 的所有名称，创建 `ArrayAdapter` 并设置给 `MaterialAutoCompleteTextView`。
* **点击事件：** 当用户点击下拉列表项时，自动将该食物的 `caloriesPerUnit` 或 `caloriesPer100g` 填入逻辑变量。
* **兜底匹配 (关键)：** 在用户点击 **"添加" 按钮** 时，如果热量仍为 0 (说明用户可能手打名字没点下拉)，尝试在 `foodList` 中**按名称再次查找**。
    * *逻辑：* `FoodLibrary match = findFoodByName(inputName);`
    * If match found -> use its calories.
    * If no match -> keep 0 or default.

#### 2. 优化时间显示与卡片间距
* **时间格式 (`DietFragment.java`):**
    * 在 `showMealDetailsDialog` 中，将时间格式化代码修改为：
    * `SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())`。
* **卡片间距 (`include_meal_card.xml`):**
    * 找到根布局 `MaterialCardView`。
    * **新增属性：** `android:layout_margin="6dp"`。
    * 这将解决网格布局中卡片紧贴在一起的问题，使界面更美观。

#### 3. 实装 "食物百科" 功能 (`DietFragment.java`)
**需求：** 底部搜索框目前无响应。点击应弹出 "食物查询" 窗口。
**实现步骤：**
* **监听：** 在 `onViewCreated` 中给底部搜索卡片 (`cv_food_wiki` 或对应 ID) 设置 `OnClickListener`。
* **弹窗逻辑 (`showFoodWikiDialog`):**
    * 弹出一个 `MaterialAlertDialog` 或 `BottomSheetDialog`。
    * **内容：** 包含一个 `SearchView` (或 EditText) 和一个 `RecyclerView`。
    * **列表：** 展示数据库中所有 `FoodLibrary` 数据（显示名称、热量、营养素）。
    * **过滤：** 当在 SearchView 输入文字时，实时过滤 RecyclerView 的列表。
    * *注：* 此功能仅供查询，点击列表项可弹出详情（含蛋白质、碳水数据），但不添加到饮食记录。

**请直接给出以下文件的修改片段或完整代码：**
1.  **`DietFragment.java`** (重点：AddDialog 的 Adapter 绑定与兜底匹配逻辑、Wiki 点击逻辑、Wiki 弹窗实现)。
2.  **`include_meal_card.xml`** (增加 Margin)。
3.  **`dialog_food_wiki.xml`** (新增：用于百科查询的弹窗布局，含搜索栏和列表)。
4.  **`FoodLibraryAdapter.java`** (新增：用于百科列表展示的简单适配器)。