# BottomSheet 与 FAB 重构设计文档 - 2026-07-02

本设计文档旨在解决 FitnessDiary 项目中与悬浮按钮（FAB）、首页卡片布局、日历备注渲染 bug、饮食页面结构调整以及引导页精简等 6 项需求的优化与重构方案。

## 需求分解与设计方案

### 1. 移除全屏引导，保留页面引导
* **原因:** 用户体验精简，避免过长的新手阻碍。
* **设计:** 在 [MainHomeFragment.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/ui/fragment/MainHomeFragment.java) 的 `onViewCreated()` 中，注释或移除展示 `OnboardingOverlayFragment` 的代码，并调用 `guideManager.markGlobalOnboardingDone()` 以保证全局标记被标记为已完成。各页面的分步引导（如记录、饮食、我的等）依然正常保留触发。

### 2. 底部导航栏与右下角 FAB 重构，任意页面快捷添加
* **原因:** 便于在其它非首页的 Tab（如日历、饮食、AI、我的）快速进行各种健康记录的添加。
* **设计:** 
  * 调整 [fragment_main_home.xml](file:///d:/code/JaProject/FitnessDiary/app/src/main/res/layout/fragment_main_home.xml)：将 `fab_quick_entry` 与 `bottom_nav_custom` (自定义底导胶囊) 移到同一水平高度并排展示。
  * `bottom_nav_custom` 右侧对齐到 `fab_quick_entry` 的左侧，加上适当边距（如 12dp）。
  * 将 [MainHomeFragment.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/ui/fragment/MainHomeFragment.java) 里的 `updateFabVisibility(int position)` 改为不隐藏 FAB，使得 FAB 在任意页面都始终显示。
  * 重构 FAB 弹窗（`QuickEntryBottomSheet`）：不再以分 Tab（饮食/训练/习惯）的繁琐方式展示，而是重构为一个**快捷工作台面板**：
    * **顶部快捷记录区 (Icons Panel)**：提供 12 个记录类型的图标入口。图标背景为柔和圆圈，上面展示对应小卡片左上角的图标（如 `ic_hero_dumbbell`、`ic_hero_diet`、`ic_hero_water` 等），下方写有名称。点击这些图标将直接跳转进入对应的详情 Fragment（例如运动进入 `sportRecordDetailFragment`，饮食切换到饮食页，心情弹出情绪选择器，其余跳转至相应 DetailFragment）。

### 3. AI日报改为健康日报，移除图标
* **设计:** 修改 [view_ai_daily_briefing.xml](file:///d:/code/JaProject/FitnessDiary/app/src/main/res/layout/view_ai_daily_briefing.xml) 中标题 TextView 的文本，把 `🤖 AI 日报` 改为 `健康日报`，除去机器人表情，保留为普通文字。

### 4. 首页卡片移动到 FAB 弹窗，自定义显示状态与顺序
* **原因:** 极致简化首页，保持“极简大留白”设计风格，默认状态下首页只展示运动、饮食、睡眠、体重四个核心卡片，其余小卡片默认隐藏。被移走的今日任务、健康日报、打卡热力图等，统一聚合至 FAB 的快捷弹窗内，并允许用户自由调整其顺序和显隐。
* **设计:**
  * 在首页 [CheckInFragment.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java) 的 `loadCardConfig()` 中，修改 10 个小卡片的默认可见性：睡眠（`sleep`）和体重（`weight`）默认为 `true`，其余 8 个小卡片（喝水、习惯、用药、围度、便便、经期、步数、情绪）默认为 `false`。
  * 在 [fragment_checkin.xml](file:///d:/code/JaProject/FitnessDiary/app/src/main/res/layout/fragment_checkin.xml) 中，将今日任务（`layout_daily_missions`）、健康日报（`view_ai_daily_briefing`）、21天挑战进度（`card_challenge`）、底部三个操作按钮（挑战、日历、卡片管理）全部设为 `android:visibility="gone"`。
  * 在 FAB 弹窗 [QuickEntryBottomSheet.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/ui/bottomSheet/QuickEntryBottomSheet.java) 内建立一个**动态工作台容器**，将以下 6 个板块以卡片/列表形式嵌入：
    1. **AI快捷助理 (ai)**：展示 AI 私教聊天按钮，点击调起 `QuickAiChatBottomSheet`。
    2. **今日任务 (missions)**：展示任务列表。
    3. **健康日报 (briefing)**：展示健康日报（AI日报），支持刷新和展开。
    4. **所有记录卡片 (records)**：以 GridLayout 精简网格形式展示 10 个小记录卡片，让用户能在此快捷查看/录入各卡片状态。
    5. **21天挑战 (challenge)**：展示当前的 21 天挑战进度。
    6. **打卡日历 (calendar)**：展示打卡热力图，可点击查看。
    7. **管理卡片 (settings)**：提供一个配置 FAB 面板的按钮，用于打开“编辑快捷卡片”的对话框，允许通过拖拽/勾选的方式调整这 6 个卡片板块的显隐和排列顺序。
  * 使用 SharedPreferences 保存这 6 个板块的显隐开关和顺序（如 `fab_cards_order` = `"ai,missions,briefing,records,challenge,calendar"`），弹窗在 `onViewCreated` 时根据此配置动态添加和加载这些子 View。

### 5. 日历备注显示 bug 修复，风格一致且居底
* **原因:** 日历备注目前没有圆角背景，在浅色背景下文字对比度低，且未能与其他容量、运动、项数等标签风格保持一致。
* **设计:** 修改 [PlanFragment.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/ui/fragment/PlanFragment.java) 中备注渲染代码。由于其它标签都是“浅色圆角背景 + 深色文字”，我们将备注渲染方式也重构为：
  * 文字色直接使用备注配置色：`noteColor`（如 `#82716B`）。
  * 背景色设为该备注颜色带有 10% 不透明度的色值：`"#1A" + noteColor.replace("#", "")`（例如 `#1A82716B`）。
  * 备注在 cell item 的 labels 容器中作为最后的子 View 添加，故其始终居于最下方。

### 6. 删除饮食页面红框的“能量余额 Badge”区域
* **设计:** 
  * 在 [fragment_diet.xml](file:///d:/code/JaProject/FitnessDiary/app/src/main/res/layout/fragment_diet.xml) 中移除 id 为 `card_energy_status` 的 MaterialCardView 及其所有子控件。
  * 在 [DietFragment.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java) 中，将 `bindEnergyStatus(DailyHealthSnapshot)` 方法实现设为空（即不绑定任何属性），并确保不会对已被删除的 `binding.tvEnergyBurned` 等产生引用报错。
