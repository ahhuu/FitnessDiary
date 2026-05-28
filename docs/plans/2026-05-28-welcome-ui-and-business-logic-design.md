# Welcome UI, Onboarding, Achievements, AI Coach Sidebar, and Challenge Dialogs Refactoring

这个设计文档说明了为解决以下六个核心问题所采取的设计和重构计划：
1. **封面欢迎页 (Stage 1) 的 Logo 质感提升**：去除 `img_welcome_hero.webp` 的白色底色，使其能够自然融于应用背景色。
2. **完善资料页 (Stage 2) 的视觉精简与重构**：将冗长平铺的输入项通过卡片化、水平并排输入框的形式收紧，采用 Material 3 的 Outlined 风格，提升质感。
3. **Onboarding 滑屏页图标及文案优化**：将小尺寸的 vector 图标放大，去掉 AI 私教页的白色背景，并将文案调整为高度契合应用功能的设计。
4. **成就系统“计划大师”条件修改**：修正“初识规划”与“计划大师”的判断条件，只统计用户自定义创建的前缀为 `自定义-` 的计划数量。
5. **AI私教历史对话抽屉防遮挡处理**：通过在抽屉布局的 LinearLayout 中增加 `paddingBottom` 与 `clipToPadding="false"`，将底部操作按钮和列表元素提至胶囊导航栏上方，解决遮挡无法点击的问题。
6. **21天挑战弹窗精美重构**：设计专门的自定义弹窗界面，取代原生的单调列表和文字警告，提升使用体验。

---

## 1. 详细设计方案

### 1.1 图像处理与矢量图尺寸调整

- **白色底色去除**：
  编写 Python 抠图脚本，读取 `img_welcome_hero.webp`。遍历每个像素，判断其 RGB 是否均大于阈值 `240`。若是，则将其 alpha 通道值根据与纯白的距离进行平滑羽化计算（避免边缘锯齿），使底色完全透明，并写回保存。
  由此，图 1 封面页以及 Onboarding 智能教练页面的白色方框底色均会被完美剥离。
  
- **矢量图尺寸放大**：
  修改 [ic_hero_dumbbell.xml](file:///d:/code/JaProject/FitnessDiary/app/src/main/res/drawable/ic_hero_dumbbell.xml) 和 [ic_hero_diet.xml](file:///d:/code/JaProject/FitnessDiary/app/src/main/res/drawable/ic_hero_diet.xml) 的 `android:width` 和 `android:height` 属性为 `140dp`（原先为 `30dp`），保证在 `200dp` 容器中以清晰的大图显示。

### 1.2 Onboarding 滑屏页文案升级

修改 [OnboardingFragment.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/ui/fragment/OnboardingFragment.java) 中的文案配置：
- **Page 0 (记录)**：
  - 标题：“全方位健康手账”
  - 描述：“轻松记录每日运动、饮食、饮水与睡眠\n科学管理体重与围度，见证身体的每一次蜕变”
- **Page 1 (AI私教)**：
  - 标题：“专属 AI 私教”
  - 描述：“随时对话答疑，为您定制个性化训练与饮食计划\n智能分析卡路里与进度，让您的健身更科学、更高效”
- **Page 2 (挑战/成就)**：
  - 标题：“21天挑战与成就”
  - 描述：“开启21天挑战培养健康好习惯，解锁丰富成就徽章\n让每一次坚持都有迹可循，成为更好的自己”

### 1.3 “完善资料”页面重写

修改 [fragment_welcome.xml](file:///d:/code/JaProject/FitnessDiary/app/src/main/res/layout/fragment_welcome.xml) 的 Stage 2 表单布局：
- **布局分块卡片化**：
  使用 `com.google.android.material.card.MaterialCardView` 作为卡片包裹，减少页面零碎的白色大留白。
  - **卡片 1：账号设置**（昵称、年龄、性别）：
    将“昵称输入框”与“年龄输入框”放在同一个水平 `LinearLayout` 里，横向平分空间。
  - **卡片 2：身体指标**（身高、体重）：
    将“身高 (cm)”和“体重 (kg)”并排排列（`layout_weight="1"`），使数据录入模块高内聚。
  - **卡片 3：运动目标**（健身目标、活动水平）。
- **输入框质感升级**：
  将原本的 transparent TextInputLayout 风格全部升级为 `@style/Widget.Material3.TextInputLayout.OutlinedBox`，设置圆角，提供极佳的 Material 3 焦点框浮动效果，取代传统的细线下划线。
- **UI 精简性**：
  将原本庞大冗长、需要多次下滑的输入页面收纳在单屏内，大幅降低用户注册流失率。

### 1.4 成就系统逻辑订正

- 修改 [AchievementCenterViewModel.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/viewmodel/AchievementCenterViewModel.java) 的 `refreshAll` 中的统计逻辑。
- 修改 [ProfileViewModel.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/viewmodel/ProfileViewModel.java) 的 `checkAchievements` 统计逻辑。
- **核心逻辑**：
  原本 `planCount` 直接使用 `trainingPlanDao.getAllPlansList().size()`，现改为：
  ```java
  int planCount = 0;
  List<TrainingPlan> plans = trainingPlanDao.getAllPlansList();
  if (plans != null) {
      for (TrainingPlan plan : plans) {
          String cat = plan.getCategory();
          if (cat != null && cat.startsWith("自定义-")) {
              planCount++;
          }
      }
  }
  ```
  只有用户自己点击新增或编辑时以 `"自定义-"` 保存的计划才会被纳入成就计数，排除系统在 `checkAndSeedLibrary` 中自动填充的 `基础-` 和 `进阶-` 计划。

### 1.5 AI私教抽屉布局调优

在 [fragment_ai_chat.xml](file:///d:/code/JaProject/FitnessDiary/app/src/main/res/layout/fragment_ai_chat.xml) 中：
- 定位到 `NavigationView` 内部的 `LinearLayout` 容器。
- 增加属性：`android:paddingBottom="92dp"` 和 `android:clipToPadding="false"`。
- 这样，整个侧滑面板被抽出时，其内部的内容（最下方的“清空所有记录”操作栏）会在视觉上以及事件点击区域上提，完美避开主页胶囊导航栏（68dp高+12dp margin），彻底消除遮挡隐患，不需要更改 activity 层级的复杂层级代码。

### 1.6 21天挑战弹窗自定义重构

- **新建 `dialog_challenge_picker.xml`**：
  弹窗内垂直摆放四个精致设计的 `MaterialCardView` 代表四种挑战。
  每一个 Card 包含：
  - 左侧大圆形带背景色的 Emoji（如减脂冲刺是橙色背景配 🔥）。
  - 右侧主标题和挑战说明（如“21天每日热量不超标 · 累计3天超标则失败”）。
  - 水波纹效果与点击态，让选择流程极为畅快和有现代感。
- **新建 `dialog_challenge_active.xml`**：
  展示当前正在进行的挑战详情：
  - 卡片头部展示大 Emoji、名称与规则。
  - 核心位置提供一个 `ProgressBar`，醒目展示打卡进度 `第 X / 21 天`。
  - 进度条下方展示剩余失败机会，以红/灰图标或文字明确显示。
  - 提供醒目的“继续挑战”大按钮和隐藏在下方的文本按钮“放弃挑战”，避免用户错按。
- **绑定代码**：
  在 [CheckInFragment.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java) 的 `showChallengeDialog` 与 `showChallengeTypePicker` 方法中，用 `MaterialAlertDialogBuilder` 分别渲染这两个布局，并在 Java 中进行视图查找与逻辑关联。

---

## 2. 验证方案

### 2.1 编译与测试
1. 使用 `./gradlew.bat assembleDebug` 校验代码是否能够正常通过 Java 17 的严苛编译。
2. 运行应用，验证封面页、完善资料页、Onboarding 各滑屏的 UI 质感与大小。
3. 自定义创建计划，检查个人中心和成就中心的“初识规划(3+)”及“计划大师(10+)”进度是否按照预期从 0 开始计数，在创建 3 个和 10 个自定义计划后是否触发成就解锁通知。
4. 打开 AI 私教页，点击左上角历史图标，拉出侧边栏，检查“清空所有记录”按钮是否浮在胶囊导航栏上方，并且能正常点击。
5. 在记录首页，点击 21 天挑战入口，检查选择挑战与进行中挑战的弹窗是否以极高质感的自定义布局渲染，进度条与文字说明是否清晰明了。
