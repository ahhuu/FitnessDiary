# 🛠️ 身体数据中心——「图表」标签页重构设计文档

## 1. 重构背景与现状痛点
在当前 FitnessDiary 应用的二级身体数据中心中，“图表 (Chart) 标签页”具有以下三个关键痛点：
1. **时间轴颠倒 (Bug)**: 体重折线图在 Java 侧处理时被无谓地执行了 `Collections.reverse` 反转，导致折线走势与实际时间相反（左边为新，右边为旧），且对比看板计算出的“最新值”实为“最老值”。
2. **空间极致受限**: 左侧纵向指标侧边栏占据了屏幕近 1/3 的宽度，使得图表本身被压缩在窄小的右半边，折线起伏细节被严重挤压遮挡。
3. **视觉体验粗糙**: 指标选中状态仅为单调的浅绿背景块，折线图折角生硬，数据点顶格贴边，缺乏呼吸感与渐变投影，与高级感、极简清新的美学严重不符。

---

## 2. 目标设计方案

### 2.1 空间彻底释放：顶部横滑胶囊 TabBar
- **结构变更**: 砍掉左侧垂直 `ScrollView` 侧边栏，将 `layout_tab_chart` 的排列方向调整为 `vertical`（纵向排列）。
- **微型标签栏**: 在最顶部放置一个包装在 `HorizontalScrollView` 里的横向线性布局 `layout_chart_indicators`。
- **胶囊 Tab (Chip)**: 每一个指标切换 TextView 改为圆润的“胶囊小气泡”：
  - 上下 padding 8dp，左右 16dp，外边距 margin 6dp。
  - 选中状态：背景为 App 主色调（绿色）实心圆角胶囊背景，字色为纯白色（`#FFFFFF`）并加粗。
  - 未选中状态：背景为米灰色（`@color/fitnessdiary_surface`），字色为中灰（`@color/text_secondary`）。

### 2.2 视觉高档化：全宽大折线图包裹面板
- **整合大卡片**: 在胶囊 TabBar 下方，引入一个白色大 `MaterialCardView`（圆角 `24dp`），将对比看板、折线图、无数据提示、快速录入按钮**封装为一个有机的滑动长卡片面板**。这能继承第一阶段“物理卡片边界”的美学规范，达成强烈的看板模块感。
- **贝塞尔曲线平滑**: 对 `LineDataSet` 调用 `setMode(LineDataSet.Mode.CUBIC_BEZIER)` 开启三阶贝塞尔平滑。
- **上下 15% 动态间距**: 对图表 Y 轴设置 `setSpaceTop(15f)` 和 `setSpaceBottom(15f)` 自动留白，永不贴边顶格。
- **渐变阴影填充**: 开启 `setDrawFilled(true)`，使用由 App 主色渐变淡化至透明的 `GradientDrawable` 填充折线下方闭合空间。

### 2.3 逻辑纠偏：升序时间轴与最新值计算
- **移去体重反转**: 直接以 Room 返回的升序 `records` 列表添加数据点并计算对比指标，恢复 X 轴“由过去流向未来”的认知习惯。

---

## 3. 影响文件与修改范围
1. **[dialog_body_data_detail.xml](file:///d:/code/JaProject/FitnessDiary/app/src/main/res/layout/dialog_body_data_detail.xml)**:
   - 重构 `layout_tab_chart` 下的 XML 布局体系。
2. **[BodyDataDetailBottomSheetFragment.java](file:///d:/code/JaProject/FitnessDiary/app/src/main/java/com/cz/fitnessdiary/ui/fragment/BodyDataDetailBottomSheetFragment.java)**:
   - 更改 `setupChartIndicators` 和 `updateIndicatorSelectionBg` 动态生成胶囊 Tab 气泡。
   - 修正 `refreshChartData` 体重排序及最新值读取越界，增加贝塞尔、动态间距和渐变阴影。
