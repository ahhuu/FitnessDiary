# 睡眠与体重详情页分析看板及 AI 诊断设计文档

本文档定义了在睡眠详情页和体重详情页中新增的分析看板与“✨ AI 评估报告”功能的设计与实现。

---

## 1. 睡眠数据分析与 AI 诊断设计

### 1.1 UI 布局改造

* **修改文件**: `app/src/main/res/layout/fragment_sleep_record_detail.xml`
* **设计**:
  - 在“睡眠记录”图表 CardView 下方，新增一个“睡眠规律与质量分析 (近30天)” CardView。
  - 内置两列布局呈现“平均睡眠时长”、“充足率占比”等统计项。
  - 呈现“最晚入睡时间”、“睡眠异常警告”等状态。
  - 提供本地睡眠调理建议模块。
  - 增加“✨ 生成 AI 睡眠诊断报告”按钮。

### 1.2 ViewModel 数据聚合

* **修改文件**: `app/src/main/java/com/cz/fitnessdiary/viewmodel/SleepDetailViewModel.java`
* **新增 LiveData**:
  - `avgSleepDuration` (Double): 近30天平均睡眠时长。
  - `sufficientRatio` (Float): 充足睡眠（6-9小时）天数占比。
  - `latestBedtime` (String): 近30天内最晚入睡的时间点（如 01:20）。
  - `sleepAdvice` (String): 本地基于时长及熬夜频次的调理小建议。
  - `sleepWarning` (String): 熬夜频次或时长过短的警告。
* **聚合逻辑**:
  - 在 `refreshStatsSeries()` 或单独的 refresh 方法中，异步读取近 30 天内所有的 `SleepRecord`。
  - 遍历计算平均睡眠小时数。
  - 计算睡眠时长在 6 到 9 小时之间的记录占比。
  - 解析 `startTime` 中的时分，寻找最晚入睡的记录。

### 1.3 Fragment 对接与 AI 报告生成

* **修改文件**: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/SleepRecordDetailFragment.java`
* **对接逻辑**:
  - 观察新增的 LiveData 并渲染到卡片对应的控件上。
  - 点击“✨ 生成 AI 睡眠诊断报告”按钮，将近30天均值、充足占比、最晚入睡时间数据组装为 Prompt，调用 `DeepSeekService.sendMessage` 发送给大模型。
  - 同样实现 Markdown-HTML 转换排版，并在弹窗对话框中优雅展示。
  - **完备兜底**：离线或请求出错时，利用本地睡眠健康引擎生成一份精美排版的本地调理建议报告。

---

## 2. 体重数据分析与 AI 诊断设计

### 2.1 UI 布局改造

* **修改文件**: `app/src/main/res/layout/fragment_weight_record_detail.xml`
* **设计**:
  - 在“体重记录”图表下方，新增一个“体重趋势与目标进度分析 (近30天)” CardView。
  - 显示“用户当前目标”（减脂/增肌/保持）及“目标体重”。
  - 显示“月度体重增减变化”（如：-1.5 kg / +0.8 kg）和当前最新 BMI。
  - 显示“目标达成进度”百分比（对于减脂或增肌目标，智能根据初始体重和当前最新体重计算完成度）。
  - 呈现结合增肌/减脂健身目标的本地科学饮食与力量训练小贴士。
  - 增加“✨ 生成 AI 体重与目标分析报告”按钮。

### 2.2 ViewModel 数据聚合

* **修改文件**: `app/src/main/java/com/cz/fitnessdiary/viewmodel/WeightDetailViewModel.java`
* **新增 LiveData**:
  - `weightGoalType` (Integer): 用户当前的目标类型 (0=减脂, 1=增肌, 2=保持)。
  - `weightTrendVal` (Float): 近30天内的体重变化差值（最新记录 - 30天前首个记录）。
  - `goalProgressPct` (Float): 增肌/减脂目标的达成进度百分比。
  - `weightAdvice` (String): 基于增肌/减脂目标及体重变化趋势的本地调理与运动建议。
  - `weightBmi` (Float): 当前最新 BMI 状态。
* **聚合逻辑**:
  - 在 refreshStats() 的异步逻辑中，同步读取 `db.userDao().getUserSync()` 确认用户的身高、初始体重、当前目标及目标体重。
  - 读取近 30 天内所有的 `WeightRecord`。
  - 依据最新体重与首条体重计算增减趋势。
  - 根据目标类型计算进度：
    - 减脂进度 = `(初始体重 - 当前体重) / (初始体重 - 目标体重) * 100%`（限制在 0-100%）。
    - 增肌进度 = `(当前体重 - 初始体重) / (目标体重 - 初始体重) * 100%`。

### 2.3 Fragment 对接与 AI 报告生成

* **修改文件**: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/WeightRecordDetailFragment.java`
* **对接逻辑**:
  - 绑定布局中的卡片控件，观察并动态渲染趋势、目标状态、进度和 BMI。
  - 点击“✨ 生成 AI 体重与目标分析报告”按钮时，将健身目标、初始/最新/目标体重、近30天变化趋势及 BMI 组合成 Prompt 送入大模型获取定制的膳食运动方案。
  - **完备兜底**：离线时，本地医学引擎会根据增肌/减脂目标状态智能生成详尽的增肌饮食/减脂训练本地调理指南，保证高可用性。
