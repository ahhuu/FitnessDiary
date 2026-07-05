# 动作库功能设计文档

本文档描述了在 FitnessDiary 应用中新增“动作库”功能模块的设计方案。

## 需求概述

1. **入口位置**：在“计划”页面（`PlanFragment`）顶部的右上角，将动作库入口按钮放置在“计划管理”（哑铃图标）与“显示设置”（设置图标）的中间。
2. **页面交互**：
   - 顶部提供搜索框，支持动作名称模糊搜索。
   - 搜索框右侧提供 `+` 按钮，允许用户手动添加自定义动作。
   - 主体为双栏式布局，左侧为大类（肌肉部位如胸、背、腿等），支持高亮切换；右侧为动作列表。
   - 右侧列表顶部提供器械过滤标签（如置顶、杠铃、哑铃、壶铃等），支持快速筛选。
   - 右侧动作为网格卡片流，卡片包含左上角“讲解”标签、动作图（若无图则展示淡蓝色首字艺术占位）、动作名称。
   - 点击动作卡片能查看该动作的“难度等级”、“适用器械”、“动作描述要领”等。
3. **视觉设计**：颜色统一采用现在的计划主题蓝色（`#2E6BB3` 系列）。

## 架构与数据流

### 1. 数据库实体

复用现有的 `ExerciseLibrary` 实体表：
- `tableName`: `"exercise_library"`
- 字段包括：`id` (主键), `name` (动作名称), `body_part` (部位), `sub_category` (子分类), `description` (要领描述), `difficulty` (难度: 1-3), `equipment` (器械)。

### 2. 界面层 (UI)

- **入口按钮**：在 `fragment_plan.xml` 中新增 `btn_exercise_library`。
- **动作库页面**：
  - `ExerciseLibraryFragment` 对应 `fragment_exercise_library.xml`。
  - 左侧大类列表：使用 RecyclerView 或垂直布局的 LinearLayout。
  - 右侧器械筛选：使用 HorizontalScrollView + ChipGroup。
  - 右侧动作列表：使用 RecyclerView + GridLayoutManager（2列）。
- **动作详情弹窗**：点击动作卡片后，弹出 `BottomSheetDialogFragment` 或 `MaterialAlertDialog` 展现动作的难度星级、器械要求及动作详情。

### 3. 数据层 (Repository & DAO)

- 复用 `ExerciseLibraryRepository`，其底层调用 `ExerciseLibraryDao` 的如下方法：
  - `searchExercises(String keyword)`
  - `getDistinctBodyParts()`
  - `getExercisesByCategory(String bodyPart, String subCategory)`
  - `insert(ExerciseLibrary)` 插入自定义动作。

---

## 详细界面布局设计

### 1. `fragment_exercise_library.xml`
- 顶层使用 `LinearLayout`（垂直）作为主背景。
- 顶部导航与搜索栏：包含返回键、输入框（带搜索图标与清除键）、加号按钮。
- 主体部分使用水平的 `LinearLayout`：
  - **左侧栏**（宽 `90dp`）：使用 RecyclerView（`rv_body_parts`）或垂直滚动条，用于部位选择。每个 Item 有一个左侧的蓝色指示条和文字高亮效果。
  - **右侧栏**（宽 `weight=1`）：
    - 顶部器械过滤：`HorizontalScrollView` 包裹的 `ChipGroup`。
    - 动作网格：RecyclerView（`rv_exercises`），使用 2 列的 `GridLayoutManager`。

### 2. 动作卡片 `item_exercise_card.xml`
- 使用 `MaterialCardView`，圆角 `12dp`，背景为纯白，带微弱阴影。
- 内部布局：
  - 一个 `FrameLayout` 用于放置动作示意图。
    - 若没有图，展示一个淡蓝色渐变的圆形或圆角矩形，居中展示动作首字（如“推”、“拉”），富有设计感。
    - 左上角重叠展示一个“讲解”的 Badge（深蓝色背景，白色文字）。
  - 下方文字区：展示动作的名称，字体采用 `text_primary`。

---

## 验证与测试方案

- 检查 `PlanFragment` 入口按钮是否能正确跳转到 `ExerciseLibraryFragment`。
- 测试模糊搜索功能，输入“卧推”是否能实时刷出相关动作。
- 测试左侧大类联动，点击“背”后右侧是否切换为背部动作。
- 测试右侧顶部的器械筛选（如“杠铃”/“哑铃”/“自重”）是否能与大类部位叠加筛选。
- 测试点击 `+` 按钮弹出对话框，输入自定义动作信息并保存后，是否能在列表中即时查看到。
- 测试点击卡片后，详情弹窗是否能完整展现该动作的各项属性。
