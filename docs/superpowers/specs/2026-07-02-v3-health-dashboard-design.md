# FitnessDiary v3.0 全能健康管家 设计方案

本方案旨在将 FitnessDiary 从"功能模块拼盘"升级为"数据整合型的全能健康管家"，解决功能整合度不强的问题，为应用商店上架竞争做准备。

## 1. 背景与目标

### 1.1 当前痛点

- **数据各自为政**：训练、饮食、习惯、身体指标独立存储展示，缺少交叉关联
- **首页缺乏总览**：现有首页是卡片拼盘（2 大卡 + 10 小卡），没有统一的健康评分或关键指标一览
- **录入流程割裂**：训练→Plan 页、饮食→Diet 页、习惯→首页小卡片，三条分散路径
- **AI 能力孤立**：AI 私教是独立 Tab，未与各功能模块联动
- **缺少引导**：新用户进入后不知道从哪里开始，各页面功能发现成本高

### 1.2 目标定位

「全能健康管家」—— 以数据整合和洞察为核心卖点，覆盖健身 + 饮食 + 健康习惯 + 身体指标的全维度追踪。

### 1.3 版本策略

v3.0 集中大版本，1-2 个月一次性推出整合体验。Health Connect 集成、数据备份导出等延后到 v3.1。

---

## 2. 五大改进维度

```
维度1：首页仪表盘    维度2：跨模块数据关联    维度3：快捷录入优化
维度4：AI 日报与智能提醒    维度5：新手引导系统
```

---

## 3. 维度1：首页健康仪表盘

### 3.1 布局重构

将现有 `CheckInFragment` 的 2+10 卡片 GridLayout 改为 ScrollView 垂直三区域布局：

```
┌──────────────────────────────┐
│        健康评分环             │  ← 区域1：核心指标区
│   85分  · 今日能量 +120kcal  │
│   [连续打卡 47 天] [等级 Lv.8] │
├──────────────────────────────┤
│  ▸ 训练: 已消耗 320 kcal     │  ← 区域2：今日摘要卡片组
│  ▸ 饮食: 已摄入 1850 kcal    │     （可展开查看完整卡片）
│  ▸ 睡眠: 7h 23min · 质量优   │
│  ▸ 步数: 6,842 / 8,000      │
│  ▸ 饮水: 5 / 8 杯           │
│  ▸ 心情: 😊                  │
│  [展开全部 ▼]                │
├──────────────────────────────┤
│  🤖 AI 日报                  │  ← 区域3：智能洞察
│  "昨天蛋白质摄入偏低，但训练 │
│   强度适中。今天建议补充..."  │
├──────────────────────────────┤
│  [➕] FAB 快捷记录            │  ← 快捷入口
└──────────────────────────────┘
```

### 3.2 健康评分引擎

新增 `HealthScoreEngine`（纯计算模块，不涉及 DB 写入），每日数据变更时重算：

```java
ScoreBreakdown {
    exerciseScore    // 0-25分：训练频率 + 时长 + MET 强度
    dietScore        // 0-25分：热量达标率 + 宏量营养素均衡度
    habitsScore      // 0-20分：水/睡眠/步数/用药/便便/心情完成率
    bodyMetricsScore // 0-15分：体重/围度趋势（接近目标→高分）
    consistencyScore // 0-15分：连续打卡天数 + 本周完整度
}
```

评分规则（示例）：
- **训练**：当日有训练记录 → 15 分；达到目标时长 → +5；高强度(MET>6) → +5
- **饮食**：热量在目标 ±10% → 20 分；±20% → 15 分；超标 50% → 5 分
- **习惯**：7 个核心习惯各占约 3 分，完成即得分
- **身体**：体重趋势与目标一致 → 高分；波动大或偏离 → 低分
- **坚持**：连续打卡天数映射为 0-15 分段

### 3.3 能量天平

环形图展示：`摄入热量 - (BMR + 训练消耗 + 步数消耗)`：

- 🟢 绿色段：缺口 200-500 kcal（减脂推荐区）
- 🔵 蓝色段：平衡 ±200 kcal（维持区）
- 🟠 橙色段：盈余 >500 kcal（增肌区）

需跨 Diet + Training + Step + Profile(BMR 公式) 四个模块查询。

### 3.4 保留现有卡片

现有 10 个小卡片不会丢弃。摘要模式下每项显示一行核心数据；点击展开为完整卡片（复用现有 UI），所有现有功能（拖拽排序、显示/隐藏）保留。

---

## 4. 维度2：跨模块数据关联

### 4.1 关联矩阵

| 关联线 | 数据来源 | 展示位置 | 用户价值 |
|--------|---------|---------|---------|
| **能量天平** | Diet(摄入)+Training(消耗)+Step(消耗)+Profile(BMR) | 首页环形图 | 一眼看懂今天是亏还是盈 |
| **睡眠→训练** | Sleep(时长/质量)+Training(表现) | 训练详情页底部 | "昨晚睡得好，今天训练超额完成" |
| **饮食→体重** | Diet(热量/碳水)+Weight(趋势) | 饮食页面提示条 | "本周碳水减少，体重↓0.3kg" |
| **训练→步数** | Training(消耗)+Step(步数) | 首页合并展示 | 避免"练了一小时还差步数"的困惑 |
| **心情→全部** | Mood(5档)+当日所有数据 | 历史日历 | 交叉看心情和完成度的关系 |

### 4.2 技术方案：聚合查询层

不改变现有 22 个 Entity 和 22 个 DAO。新增 `HealthAggregationRepository` 纯只读聚合层：

```java
public class HealthAggregationRepository {
    private final AppDatabase db;
    private final ExecutorService executor;

    // 聚合今日健康全貌
    public DailyHealthSnapshot getTodaySnapshot() {
        DailyHealthSnapshot snapshot = new DailyHealthSnapshot();
        snapshot.exerciseCalories = db.exerciseLogDao().getTodayCaloriesSum();
        snapshot.dietCalories = db.dietDao().getTodayTotalCalories();
        snapshot.stepCalories = db.stepRecordDao().getTodayCalories();
        snapshot.bmr = calculateBMR(profile);
        snapshot.sleepHours = db.sleepRecordDao().getTodayHours();
        snapshot.sleepQuality = db.sleepRecordDao().getTodayQuality();
        snapshot.moodLevel = db.moodRecordDao().getTodayMood();
        snapshot.steps = db.stepRecordDao().getTodaySteps();
        snapshot.weight = db.weightRecordDao().getLatestWeight();
        // ... 其余指标
        return snapshot;
    }

    public int calculateHealthScore(DailyHealthSnapshot data) { /* 评分算法 */ }
    public List<WeeklyTrend> getWeeklyTrends() { /* 7天趋势 */ }
}
```

关键设计决策：
- 聚合层**只读不写**，不新建 Entity 表
- 实时跨 DAO 查询 + 计算，无数据同步问题
- ViewModel 通过聚合层获取仪表盘数据

### 4.3 在现有页面中嵌入关联

- **训练详情页底部**：新增「📊 影响因素」区域，显示前日睡眠 + 今日饮食摘要
- **饮食页面顶部**：新增「⚖️ 能量状态」横条，显示热量进度 + 今日已消耗
- **历史日历**：点击某天弹出 BottomSheet，展示当日所有维度摘要（不仅是训练）

---

## 5. 维度3：快捷录入优化

### 5.1 统一快捷 FAB

首页底部 FAB 点击展开三个快捷操作面板：

```
     [🍽️ 饮食] [🏋️ 训练] [📝 习惯]
             \    |    /
              [   ➕   ]
```

### 5.2 快捷记录饮食 BottomSheet

```
┌──────────────────────────────────────┐
│  快速记录饮食          [完成] [✕]   │
├──────────────────────────────────────┤
│  📷 拍照识别                         │
│  ┌──────────────────────────────────┐│
│  │  [拍摄/从相册选择食物照片]       ││
│  └──────────────────────────────────┘│
│          —— 或手动输入 ——            │
│  🔍 搜索食物...[                ]   │
│  ┌──────────────────────────────────┐│
│  │ 🍚 米饭      150g   180 kcal    ││
│  │ 🍗 鸡胸肉    200g   260 kcal    ││
│  │       + 添加食物                 ││
│  └──────────────────────────────────┘│
│  餐次: [早餐 ▾]  日期: 今天         │
│  预估: 共 440 kcal                  │
└──────────────────────────────────────┘
```

复用现有的 `FoodImageAnalyzer`（拍照识别）和 `OpenFoodFactsService`（扫码）。

### 5.3 快速记录训练 BottomSheet

```
┌──────────────────────────────────────┐
│  快速记录训练          [完成] [✕]   │
├──────────────────────────────────────┤
│  选择计划: [胸背日 ▾]  或自由记录   │
│  ┌──────────────────────────────────┐│
│  │ 🏋️ 卧推  [4]组 [10]次 60kg ✓  ││
│  │ 🏋️ 飞鸟  [3]组 [12]次 20kg    ││
│  │       + 添加动作                 ││
│  └──────────────────────────────────┘│
│  预计消耗: ~320 kcal                │
└──────────────────────────────────────┘
```

### 5.4 快速记录习惯 BottomSheet

```
┌──────────────────────────────────────┐
│  快速记录习惯          [完成] [✕]   │
├──────────────────────────────────────┤
│  💧 饮水    [−]  5  [+]   / 8 杯    │
│  😊 心情    😫 😞 😐 🙂 😄          │
│  👣 步数    输入步数...[______]      │
│  💊 用药    [✓ 已服用]              │
│  🚽 便便    [布里斯托 1-7 ▾]        │
│  ⚖️ 体重    输入体重...[______] kg  │
│  📏 围度    输入围度...[______] cm  │
└──────────────────────────────────────┘
```

### 5.5 技术实现

- `QuickEntryBottomSheet`：`BottomSheetDialogFragment`，内部用 TabLayout 切换三种模式
- 每种模式复用现有子组件（`FoodSearchView`、`ExercisePickerView`、习惯录入控件）
- `QuickEntryViewModel`：聚合饮食、训练、习惯三个 Repository

---

## 6. 维度4：AI 日报与智能提醒

### 6.1 AI 日报生成流程

```
每天定时触发（早上 8:00 或用户首次打开 APP）
        │
        ▼
┌─────────────────────────┐
│ HealthAggregationRepo   │  ← 聚合昨日 + 近 7 天数据
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│ DailyBriefingService    │  ← 构建 Prompt + 调用 AI
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│ DeepSeek/Qwen API       │  ← 复用现有 AI 通道
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│ 首页 "AI 日报" 卡片     │  ← 结果缓存到 SharedPrefs
└─────────────────────────┘
```

### 6.2 Prompt 协议

```markdown
System:
你是专业健康教练。基于用户提供的健康数据，生成今日简报。
以 JSON 格式输出：
{
  "greeting": "一句话问候",
  "scoreComment": "对昨日健康评分的简短评价(20字内)",
  "highlights": ["亮点1", "亮点2"],
  "suggestion": "今日具体建议(50字内)",
  "motivation": "一句鼓励的话"
}

User:
昨日数据：
- 训练：消耗320kcal，完成胸背训练
- 饮食：摄入2100kcal（超标300），蛋白质120g（达标），碳水偏高
- 睡眠：6.5h，质量一般
- 步数：5200步（未达标）
- 体重：78.2kg（持平）
- 心情：3/5
近7天趋势：训练完成率5/7，体重下降0.3kg
```

### 6.3 降级策略

| 场景 | 策略 |
|------|------|
| AI 调用失败 | 用本地规则引擎生成模板化日报（`LocalBriefingGenerator`） |
| 网络不可用 | 显示本地规则引擎生成版本，标记"离线简报" |
| 未配置 API Key | 始终使用本地规则引擎 |

### 6.4 首页展示样式

```
┌──────────────────────────────────────┐
│  🤖 AI 日报           2026/07/02    │
│  ─────────────────────────────────── │
│  "昨天热量超标但蛋白质达标，睡眠偏少 │
│   可能是心情低落的原因。              │
│                                      │
│   💡 今日建议：增加20分钟有氧，      │
│      补充今天蛋白质摄入，争取        │
│      今晚11点前睡觉                  │
│                                      │
│   💬 "进步不在一天，在每一天"        │
│                          [🔄 刷新]   │
└──────────────────────────────────────┘
```

### 6.5 智能提醒升级

在现有 `SmartReminderHelper` 基础上增强提醒文案的数据上下文：

| 原提醒 | 升级后 |
|--------|--------|
| "该运动了！" | "今天还没运动，摄入已达 1600kcal，建议散步 30 分钟消耗 ~150kcal" |
| "该睡觉了" | "已过 23 点，昨晚只睡了 6 小时，今晚早点休息有助于明天训练恢复" |
| "记录饮食" | "今天还没记录午餐，昨天的蛋白质缺口较大，可以考虑高蛋白食物" |

实现方式：`ReminderReceiver` 触发时，调用 `HealthAggregationRepository.getTodaySnapshot()` 获取上下文数据，`ReminderTemplateEngine` 填充文案。

---

## 7. 维度5：新手引导系统

### 7.1 三层引导架构

```
第一层：全局引导（首次启动，4 步滑页）
    ↓
第二层：页面引导（首次进入各页面，Tooltip 高亮）
    ↓
第三层：空状态引导（列表为空时，引导性占位）
```

### 7.2 第一层：全局引导

在 WelcomeFragment 注册完成后触发，全屏 `DialogFragment` + ViewPager2 滑动翻页：

```
┌──────────────────────────────────────┐
│         欢迎来到 FitnessDiary        │
│                                      │
│   🏠 健康仪表盘                      │
│   查看每日健康评分、能量天平和       │
│   AI 智能建议                        │
│                       [→]           │
│   ────────────────────────────────   │
│   ➕ 快捷记录                        │
│   点击 + 按钮，一键记录训练、       │
│   饮食和习惯                         │
│                       [→]           │
│   ────────────────────────────────   │
│   📊 数据看板                        │
│   在历史页面查看所有健康数据的       │
│   趋势和交叉分析                     │
│                       [→]           │
│   ────────────────────────────────   │
│   🤖 AI 私教                        │
│   随时咨询训练和饮食问题，           │
│   获取个性化建议                     │
│                       [→]           │
│                                      │
│         [开始使用]                   │
└──────────────────────────────────────┘
```

实现：`OnboardingOverlayFragment`（全屏 DialogFragment），最后一页点击"开始使用"关闭。

### 7.3 第二层：页面引导（Tooltip 高亮）

首次进入各页面时，半透明遮罩 + Tooltip 指示关键 UI：

| 页面 | 高亮步骤 |
|------|---------|
| **首页仪表盘** | ① 健康评分环 → "这是你的每日健康总分" ② 能量天平 → "摄入 vs 消耗一目了然" ③ FAB → "点击这里快速记录" |
| **训练计划页** (PlanFragment) | ① 日历 → "点击日期查看当天训练" ② 计划管理入口 → "管理你的训练计划" ③ 动作库入口 → "浏览 100+ 训练动作" |
| **饮食记录页** (DietFragment) | ① 搜索框 → "搜索食物或拍照识别" ② 扫码按钮 → "扫描条形码快速录入" ③ 今日汇总 → "查看热量和营养素" |
| **训练计划管理** (PlanManageFragment) | ① 三 Tab → "当前计划 / 探索计划库 / 个人计划" ② 模板导入 → "一键导入官方训练模板" |
| **动作库** (ExerciseLibraryFragment) | ① 搜索框 → "搜索动作名称" ② 左侧部位栏 → "按肌肉部位筛选" ③ 器械过滤 → "按器械类型筛选" |
| **AI 私教** (AIChatFragment) | ① 输入框 → "直接提问训练或饮食问题" ② 快捷问题 → "点击预设问题快速开始" |
| **个人中心** (ProfileFragment) | ① 成就区 → "打卡挑战和勋章墙" ② 设置入口 → "配置目标和提醒" |

实现方式：

```java
public class PageGuide {
    String pageKey;          // 页面标识
    List<GuideStep> steps;

    static class GuideStep {
        int targetViewId;    // 要高亮的 View ID
        String title;        // 提示标题
        String description;  // 提示描述
        GuideAnchor anchor;  // TOP / BOTTOM / LEFT / RIGHT
    }
}
```

引导覆盖层逻辑：
1. 计算目标 View 的屏幕坐标
2. 全屏遮罩上"挖洞"（Canvas clip 出高亮区域）
3. 目标旁显示 Tooltip 卡片
4. 点击遮罩或"下一步"进入下一步
5. 完成后标记该页面引导已完成

### 7.4 第三层：空状态引导

当列表/数据为空时，显示引导性占位视图：

```
改进前                      改进后
┌──────────────┐       ┌──────────────────────┐
│              │       │  🏋️                  │
│   暂无数据    │  →   │                      │
│              │       │  还没有训练计划      │
│              │       │                      │
│              │       │  [从模板导入]        │
│              │       │  [AI 智能制定]       │
│              │       │  [新建空白计划]      │
└──────────────┘       └──────────────────────┘
```

适用场景：
- 训练计划列表为空 → "从模板开始或让 AI 帮你制定"
- 饮食记录为空 → "拍照识别第一餐或搜索食物"
- 习惯记录为空 → "添加你的第一个健康习惯"
- 动作库收藏为空 → "浏览动作库，收藏常用动作"

实现：在各 Adapter / Fragment 中，数据为空时切换 `empty_state_layout`（插图 + 文案 + 快捷操作按钮）。

### 7.5 引导状态管理

```java
public class GuideStateManager {
    // 全局引导
    boolean isGlobalOnboardingDone();
    void markGlobalOnboardingDone();

    // 页面引导
    boolean isPageGuideDone(String pageKey);
    void markPageGuideDone(String pageKey);

    // 空状态首次展示（仅首次弹引导）
    boolean isFirstEmptyState(String pageKey);
}
```

SharedPreferences 持久化，引导完成状态不会因卸载重装（或清除数据）而丢失。

---

## 8. 新增文件清单

### 8.1 新增类（预估）

| 类名 | 位置 | 职责 |
|------|------|------|
| `DailyHealthSnapshot` | `model/` | 健康数据快照 POJO |
| `HealthScoreEngine` | `engine/` 或 `util/` | 健康评分计算 |
| `HealthAggregationRepository` | `repository/` | 跨模块聚合查询（只读） |
| `QuickEntryBottomSheet` | `ui/bottomSheet/` | 统一快捷录入弹窗 |
| `QuickEntryViewModel` | `ui/viewmodel/` | 快捷录入 ViewModel |
| `DailyBriefingService` | `service/` | AI 日报生成服务 |
| `LocalBriefingGenerator` | `service/` | 本地规则日报降级 |
| `ReminderTemplateEngine` | `service/` | 提醒文案上下文填充 |
| `OnboardingOverlayFragment` | `ui/fragment/` | 全局引导弹窗 |
| `PageGuide` / `GuideStep` | `ui/guide/` | 页面引导数据模型 |
| `TargetedGuideOverlay` | `ui/widget/` | 引导高亮遮罩自定义 View |
| `GuideStateManager` | `util/` | 引导状态管理 |

### 8.2 修改文件（预估）

| 文件 | 改动内容 |
|------|---------|
| `CheckInFragment.java` + `fragment_check_in.xml` | 首页布局重构为三区域仪表盘 |
| `PlanFragment.java` + `fragment_plan.xml` | 日历点击弹出跨模块摘要 BottomSheet |
| `DietFragment.java` + `fragment_diet.xml` | 顶部新增能量状态横条 |
| `MainHomeFragment.java` | FAB 替换为快捷录入展开菜单 |
| `ProfileFragment.java` | 新增目标配置项（BMR 计算用） |
| `SmartReminderHelper.java` | 提醒文案增强 |
| `ReminderReceiver.java` | 触发时获取健康数据上下文 |
| 各 Fragment 空状态 XML | 增加引导性占位布局 |

### 8.3 不变更

- 22 个 Entity / 22 个 DAO / 21 个 Repository 结构不变
- 数据库版本不变更（聚合层不写表）
- 现有 AI 服务接口不变（`DeepSeekService` / `QwenService` 不变，新增调用方 `DailyBriefingService`）
- 导航结构不变（仍为 5 Tab）

---

## 9. 风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| AI 日报输出不稳定 | 中 | 中 | 结构化 JSON 输出 + 本地规则降级 |
| 首页性能劣化（多 DAO 并发查询） | 低 | 中 | ExecutorService 线程池 + 分批次加载 + 缓存 |
| 评分算法争议（用户不认可评分） | 中 | 低 | 评分透明化（点击显示详细分项）+ 后续迭代调整权重 |
| 2 个月开发量超预期 | 中 | 高 | 维度5（新手引导）可降级为 v3.0 后期或 v3.0.1 |
| FAB 三个入口优先级争议 | 低 | 低 | 基于使用频率数据分析后可调整默认展示顺序 |

---

## 10. 成功指标

- 首页仪表盘加载时间 < 500ms（含跨模块查询 + 评分计算）
- AI 日报生成成功率 > 95%（含降级）
- 用户单日跨模块使用率提升（从"仅用 1 个模块" 到 "2+ 模块"的比例）
- 新手次日留存提升（通过引导完成率间接衡量）
- 应用商店评分 ≥ 4.5（需配合崩溃率 < 0.1% 和 UI 流畅度）
