🛠️ 专项优化指令：UI 紧凑化 & Profile 交互升级
指令目标： 根据最新测试反馈，对 UI 进行“瘦身”处理，减少不必要的留白；修复 Profile 页面功能缺失；并优化视觉图标。

任务清单：

1. 全局 UI "瘦身" (Layout Tightening)
问题： 所有页面的顶部 Summary 卡片高度过大，内部留白过多，导致视觉松散。 修改要求：

针对 fragment_plan.xml, fragment_diet.xml, fragment_check_in.xml, fragment_profile.xml 中的顶部主卡片 (MaterialCardView)：

将卡片内部的 android:padding (或 paddingVertical) 从原来的数值统一减少至 16dp。

针对 fragment_check_in.xml：彻底检查顶部。目前的截图显示“本周打卡”上方有一大块莫名其妙的空白区域。请检查是否多余的 marginTop 或未使用的 View 占位，将其移除，使标题栏紧凑。

2. Profile 页面重构 (fragment_profile.xml & Logic)
问题： 头部卡片太大且空，未显示用户名，无法更改头像。 修改要求：

布局优化： 将头部卡片改为水平布局 (Horizontal)。

左侧：圆形头像 (ShapeableImageView, 大小 64dp)。

中间：垂直显示用户名 (TextSize 20sp, Bold) 和一句短语 "点击更换头像" (TextSize 12sp, Gray)。

去除原来巨大的空白区域。

功能实现 (ProfileFragment.java):

头像点击： 为头像添加 OnClickListener，调用系统图库 (Intent.ACTION_PICK) 选择图片。

数据保存： 获取图片 URI 后，申请持久化权限 (takePersistableUriPermission)，并将 URI 字符串保存到 User 数据库的新字段 avatarUri 中。

用户名显示： 确保从数据库加载 User 时，将 username 设置到 TextView。

3. 饮食页视觉修复 (fragment_diet.xml & Vector)
问题： 底部空状态的图标多余；顶部火焰图标不美观。 修改要求：

空状态优化： 在 layout_empty (空状态布局) 中，移除 ImageView，只保留提示文字 "还没有饮食记录哦"，保持底部清爽。

火焰图标重绘 (ic_hero_fire.xml):

请使用更极简、流线型的 Path 数据（类似 Tinder 或流行健身 App 的热量图标），去除复杂的锯齿，改用平滑曲线。颜色保持渐变。

4. 数据库升级 (User Entity)
在 User.java 中新增字段：public String avatarUri; (用于存储头像路径)。

注意： 请确保在 AppDatabase 中更新版本号或处理迁移（由于是开发阶段，提示我卸载重装即可）。

请直接给出以下修改后的代码：

ic_hero_fire.xml (新版图标)。

fragment_profile.xml (新版紧凑布局)。

fragment_check_in.xml (修复顶部空白)。

fragment_diet.xml (移除空状态图标，紧凑化顶部)。

User.java (新增字段)。

ProfileFragment.java (包含头像选择和权限处理逻辑)。