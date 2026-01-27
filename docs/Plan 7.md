🎨 UI 最终修饰指令：风格统一与布局修复
指令目标： 修复 CheckInFragment 的布局 Bug，统一图标设计语言，并完善 ProfileFragment 的显示逻辑。

任务清单：

1. 修复每日打卡页的大白框 (fragment_check_in.xml)
问题描述： 页面顶部出现了一个没有任何内容的空白 MaterialCardView（位于“本周打卡”上方）。 修改要求：

删除 根布局下最顶部的那个空的 MaterialCardView。

确保页面层级是：根布局 -> 标题栏 (如果有) -> “本周打卡”卡片 (这是第一个可见元素) -> “今日训练”列表。

2. 重绘火焰图标 (ic_hero_fire.xml)
设计要求：

风格统一： 必须模仿 ic_hero_dumbbell.xml 的设计语言——使用 “粗线条”、“几何化”、“断点式” 的设计，而不是封闭的卡通图形。

颜色： 保持橙红渐变。

Path Data： 使用以下更抽象、现代的火焰路径数据：

Path 1 (主火苗): M12,22c4.97,0 9,-4.03 9,-9c0,-4.97 -9,-13 -9,-13c0,0 -9,8.03 -9,13c0,4.97 4.03,9 9,9z (底座)

Path 2 (内部高光/断点): M12,18c2.21,0 4,-1.79 4,-4c0,-2.21 -4,-6 -4,-6c0,0 -4,3.79 -4,6c0,2.21 1.79,4 4,4z (内部镂空感)

注意： 请根据“断点风格”适当调整路径，使其看起来像是由两三笔粗线条组成的抽象火焰。

3. 优化“我的”页面 (fragment_profile.xml & ProfileFragment.java)
修改要求：

布局文件 (fragment_profile.xml):

删除 “点击更换头像” 这个 TextView。

垂直居中 用户名 (tvUsername)：因为删除了提示语，用户名应该在头像右侧垂直居中显示。

逻辑文件 (ProfileFragment.java):

修复用户名不显示的问题： 在观察 User 数据时，添加默认值逻辑。

代码逻辑：

Java
// 如果 user.username 为空，显示 "健身达人"，否则显示真实名字
String name = (user.username == null || user.username.isEmpty()) ? "健身达人" : user.username;
binding.tvUsername.setText(name);
请直接给出以下 3 个文件的完整修改后代码：

fragment_check_in.xml (移除顶部多余白框)。

ic_hero_fire.xml (全新的几何风格火焰)。

fragment_profile.xml (移除提示语，优化布局)。

ProfileFragment.java (修复用户名显示逻辑)。


🛠️ 修复指令：恢复连续打卡 (Streak Counter)
问题诊断： 刚刚误删了 tvConsecutiveDays 对应的布局容器，导致 CheckInFragment.java 编译报错（找不到符号）。 原设计的连续打卡显示是一个独立的卡片，视觉上造成了“大片空白”。

修复目标：

修复报错： 重新在 XML 中定义 tvConsecutiveDays。

UI 优化： 不要使用独立的卡片。将“连续坚持 X 天”的文字，作为“副标题”或“角标”整合进“本周打卡”卡片内部（位于右上角）。

具体 XML 修改 (fragment_check_in.xml):

请重写 fragment_check_in.xml，保留之前的 Gemini 风格，并做如下调整：

顶部卡片布局升级：

将“本周打卡”卡片内部的布局改为 ConstraintLayout（以便灵活定位）。

左上角： 保持“本周打卡”标题（带日历图标）。

右上角： 新增一个 TextView，ID 必须为 tvConsecutiveDays。

样式建议：

文字颜色：使用橙色或主题色 (#FF9800 或 @color/fitnessdiary_primary)。

字号：14sp。

文字内容示例："🔥 已连续 3 天" (带个火苗Emoji增强氛围)。

位置：app:layout_constraintTop_toTopOf="parent", app:layout_constraintRight_toRightOf="parent"。

下方布局： 保持原本的 7 个圆圈和“今日训练”列表不变。

请直接给出修复后的完整 fragment_check_in.xml 代码。