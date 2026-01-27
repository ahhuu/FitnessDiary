🛠️ UI 修复指令：Profile 页面优化
指令目标： 修复 ProfileFragment 中的布局冗余（重复的清除按钮），并将“我的成就”板块升级为可折叠的纯白卡片风格，保持全局 UI 一致性。

任务清单：

1. 布局去重与修正 (fragment_profile.xml)
删除顶部冗余按钮： 找到位于“我的成就”上方的那个“清除所有数据”的 MaterialCardView 或布局代码，将其删除。

修复底部按钮： 确保页面最底部的“清除所有数据”按钮保留。

关键： 确保底部这个按钮的 android:id 与 Java 代码中设置了监听器的 ID 保持一致（通常是 @+id/btn_clear_data）。如果之前监听的是上面的按钮，请将那个 ID 赋给底部的按钮。

2. 成就板块交互升级 (Collapsible Card)
修改 fragment_profile.xml：

将整个“我的成就”区域（标题 + RecyclerView）包裹在一个新的 MaterialCardView 中（作为父容器）。

父容器样式： 纯白背景 (@color/fitnessdiary_surface)，24dp 圆角。

头部设计 (Header): 使用 ConstraintLayout 或 LinearLayout。

左侧：🏆 图标 + “我的成就”标题。

右侧：添加一个 折叠/展开箭头 (ImageView, id=@+id/iv_achievement_arrow, src=ic_expand_more).

内容区域 (Body): RecyclerView (id=@+id/rv_achievements)。

实现折叠逻辑 (ProfileFragment.java):

为父容器（或头部）设置点击监听。

点击时：

切换 RecyclerView 的 Visibility (VISIBLE <-> GONE)。

旋转箭头图标 (rotation: 0 <-> 180)。

3. 成就卡片视觉统一 (item_achievement.xml)
背景修正： 现在的成就子项背景似乎有颜色。

修改要求：

根布局 MaterialCardView 的背景色强制设为 纯白 (@color/fitnessdiary_surface)。

边框处理： 如果需要区分，可以加一个极淡的灰色边框 (strokeWidth="1dp", strokeColor="#F0F0F0")，或者保留默认的阴影效果。

确保内部的图标和文字居中对齐，留白适中。

请直接给出以下文件的完整代码：

fragment_profile.xml (去重、加折叠父容器)。

item_achievement.xml (纯白风格)。

ProfileFragment.java (添加折叠动画逻辑)。