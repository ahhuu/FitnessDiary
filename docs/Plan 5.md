🚀 终极开发指令：功能闭环与 UI 细节打磨
指令目标： 这是项目上线前的最后一次重大迭代。我们需要完成第四个核心页面“我的 (Profile)”，同时对现有界面进行精细化视觉修复，解决“空状态”单调和“进度反馈”缺失的问题。

任务清单：

第一部分：现有 UI 细节打磨 (Polishing)
饮食页增加进度条 (fragment_diet.xml):

在顶部卡片的大数字 ("348 千卡") 下方，添加一个 Material 3 风格的 LinearProgressIndicator。

属性参考：android:layout_height="6dp", app:trackCornerRadius="3dp"。

逻辑：进度 = (当前摄入 / 目标摄入) * 100。如果超过 100%，进度条变红。

打卡页日历圆圈优化 (fragment_check_in.xml):

增强未选中状态 (灰色圆圈) 的可见性。

请为未选中的圆圈添加一个极淡的灰色填充 (#F5F5F5) 和稍深一点的边框 (#E0E0E0)，避免看不清。

全局“空状态”处理 (Empty States):

问题： 当 RecyclerView 数据为空时，页面下方一片空白。

要求： 在 fragment_plan.xml, fragment_diet.xml, fragment_check_in.xml 中各添加一个默认隐藏 (visibility="gone") 的 LinearLayout（包含一个灰色的 Icon 和一句提示文本，如“还没有记录哦”）。

逻辑： 在各自的 Fragment 代码中，当 adapter.getItemCount() == 0 时，显示这个空布局，隐藏 RecyclerView；反之则显示列表。

第二部分：新增“我的”模块 (Profile Feature)
导航配置:

更新 res/menu/bottom_nav_menu.xml，添加第四个 Item：navigation_profile (标题："我的", 图标：ic_person)。

更新 MainActivity 和 Navigation Graph 以支持跳转。

Profile 页面布局 (fragment_profile.xml):

风格： 严格遵循 Gemini 风格 (淡灰背景 #F0F4F9 + 纯白圆角卡片)。

头部： 简单的居中头像 (ImageView) 和 用户名。

核心数据 (Grid Layout): 使用 2x2 网格卡片展示：

体重 (kg)

身高 (cm)

BMI (自动计算)

BMR (基础代谢)

设置列表:

卡片 1: "当前目标" (显示：减脂/增肌，支持点击切换)。

卡片 2: "清除所有数据" (红色文字，用于测试)。

业务逻辑 (ProfileViewModel & ProfileFragment):

数据联动： 页面加载时读取 UserDao。

智能计算：

当用户点击修改体重/身高后，自动更新数据库，并重新计算 BMI 和 BMR。

当用户切换“目标”后，自动根据 BMR 重新计算每日推荐热量 (TDEE)，并保存到 User 表中，以便饮食页面的进度条能读取到最新的目标值。

请输出以下文件的完整代码：

修改后的 fragment_diet.xml, fragment_check_in.xml (含优化)。

三个 Fragment 的 Java 代码 (增加空状态控制逻辑)。

新增的 fragment_profile.xml。

新增的 ProfileFragment.java 和 ProfileViewModel.java。

修改后的 bottom_nav_menu.xml。