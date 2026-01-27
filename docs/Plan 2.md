**指令目标：**
对 App 进行全局 UI 升级，目标是复刻 **Google Gemini App** 的视觉风格，但使用我自己的命名规范。

**1. 颜色定义 (colors.xml)**
请首先更新 `res/values/colors.xml`，添加以下配色（使用 fitnessdiary 前缀）：
* `fitnessdiary_background`: `#F0F4F9` (页面底色，极浅的蓝灰色)
* `fitnessdiary_surface`: `#FFFFFF` (卡片颜色，纯白)
* `fitnessdiary_primary`: `#006A6A` (深青色，用于文字或图标)
* `fitnessdiary_accent_container`: `#C4EED0` (薄荷绿，用于FAB按钮背景)
* `fitnessdiary_on_accent_container`: `#04210F` (深色，用于FAB上的加号颜色)
* `text_primary`: `#1F1F1F` (主要文字)
* `text_secondary`: `#757575` (次要文字)

**2. 样式统一 (Styles)**
在布局文件中，请遵循以下规范：
* **页面背景：** 所有 Fragment 的根布局 `background` 设为 `@color/fitnessdiary_background`。
* **卡片样式 (MaterialCardView)：**
    * `app:cardBackgroundColor` = `@color/fitnessdiary_surface`
    * `app:cardCornerRadius` = `24dp` (大圆角)
    * `app:cardElevation` = `0dp` (扁平化)
    * `app:strokeWidth` = `0dp` (无边框，靠背景色差区分)。
    * `android:layout_margin` = `16dp`。

**3. 布局文件修改任务**

请重写以下三个文件的布局代码，确保引用上述新的颜色名称：

* **`fragment_check_in.xml` (每日打卡):**
    * 应用上述卡片样式。
    * 确保卡片在淡蓝灰背景上清晰浮起。

* **`fragment_plan.xml` (训练计划):**
    * **修复按钮：** 使用 `FloatingActionButton`。
    * FAB 属性：`app:backgroundTint="@color/fitnessdiary_accent_container"`，`app:tint="@color/fitnessdiary_on_accent_container"`。
    * 列表 Item (`item_training_plan.xml`): 也请同步修改为新风格。

* **`fragment_diet.xml` (饮食记录):**
    * **修复按钮：** 同样替换为标准的 `FloatingActionButton`，保持样式统一。
    * 顶部仪表盘卡片请保持清晰大气的风格。


