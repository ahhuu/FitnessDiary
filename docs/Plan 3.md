**UI 优化指令：列表项风格统一化**

**观察反馈：**
我发现 `RecyclerView` 中的列表项 (`item_training_plan` 和 `item_food_record`) 与页面顶部的 Summary 卡片风格不统一。列表项似乎有多余的阴影或边框，导致视觉割裂。

**设计目标：**
请修改列表项的布局文件，使其与顶部的 Dashboard 卡片保持**完全一致的视觉材质**（Gemini 风格）。

**具体修改要求：**

1.  **修改 `item_training_plan.xml` 和 `item_food_record.xml`：**
    * 根布局必须使用 `com.google.android.material.card.MaterialCardView`。
    * **强制应用以下属性**（与 fragment_check_in 中的卡片一致）：
        * `app:cardBackgroundColor="@color/fitnessdiary_surface"` (纯白)
        * `app:cardCornerRadius="24dp"` (统一大圆角)
        * `app:cardElevation="0dp"` (去除阴影)
        * `app:strokeWidth="0dp"` (去除边框)
        * `android:layout_marginTop="8dp"` (列表项之间保持呼吸感)
        * `android:layout_marginBottom="8dp"`
        * `android:layout_marginLeft="16dp"`
        * `android:layout_marginRight="16dp"`

2.  **内容布局优化 (ConstraintLayout):**
    * 确保卡片内部的 `ImageView` (如果有) 也有对应的圆角处理，或者与卡片边缘保持距离。
    * 确保“删除”按钮或其他文本颜色使用 `@color/text_secondary` 或 `@color/fitnessdiary_primary`，保持字体风格统一。



3.  **问题描述：**
现在已经更新了 Plan 和 Diet 页面的列表项风格，但发现 `CheckInFragment` 中的“今日训练”列表项（带 CheckBox 的那个）依然保留了旧的样式（可能有阴影或圆角不一致），导致视觉上不统一。

**任务目标：**
找到 `DailyLogAdapter` 使用的布局文件（通常命名为 `item_daily_log.xml` 或 `item_check_in.xml`），并将其重写为与其他页面一致的 **Gemini 平面卡片风格**。

**具体修改要求：**

1.  **根布局容器：**
    * 必须使用 `com.google.android.material.card.MaterialCardView`。
    * 强制应用以下统一属性：
        * `app:cardBackgroundColor="@color/fitnessdiary_surface"` (纯白)
        * `app:cardCornerRadius="24dp"` (统一大圆角)
        * `app:cardElevation="0dp"` (去阴影)
        * `app:strokeWidth="0dp"` (去边框)
        * `android:layout_marginHorizontal="16dp"` (左右边距)
        * `android:layout_marginVertical="8dp"` (上下间距)

2.  **内容布局 (ConstraintLayout):**
    * **复选框 (CheckBox):** 请确保使用 `app:buttonTint="@color/fitnessdiary_primary"` 或主题色，使其与整体风格协调。
    * **文字样式：** 标题颜色 `@color/text_primary`，副标题颜色 `@color/text_secondary`。
    * 确保卡片内部有足够的 `padding` (例如 16dp)，不要让文字紧贴边缘。
