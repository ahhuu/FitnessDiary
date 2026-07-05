# 训练计划管理重构与 AI 智能制定设计方案

本方案旨在解决目前 FitnessDiary 训练计划管理页面使用隐蔽、模板导入功能不可用且缺失经典分类的问题，并新增交互式的 AI 分步计划制定功能。

## 1. 痛点分析与重构思路

### 1.1 计划种类切换隐蔽，不易发现及点击

* **现状**：当前界面依靠点击活跃计划卡片 `layout_hero_stats` 弹出对话框来切换“基础/进阶/自定义”计划。该操作缺乏视觉提示，用户很难发现其可点击性。
* **重构方案**：在计划管理顶栏返回键和数据卡片之间，新增一个 Material 3 Segmented Button 或是 TabLayout。使用优雅的拟物化/微渐变背景，清晰标明“基础计划”、“进阶计划”、“自定义”，使用户一眼便知当前分类，且单点即可立即切换。

### 1.2 模板导入数据结构错乱与场景版本缺失

* **现状**：
  1. `TrainingTemplate.java` 实体定义与 `training_templates.json` 的结构不一致（JSON 结构中 exercises 嵌套在 versions 里，而实体中为外层字段），导致一键导入会导入 0 个动作。
  2. 原本的模板列表缺乏循序渐进的引导（如按增肌/减脂分类，再按新手/进阶/高级划分），且无法在导入时选择设备场景（健身房/居家/自重）。
* **重构方案**：
  1. **实体解析升级**：更新 `TrainingTemplate.java`，添加 `versions` 属性（内部结构为 `Map<String, TemplateVersion>`），以正确解析不同场景版本的动作。
  2. **模板数据扩充**：在 `training_templates.json` 中，扩充包含**经典三分化**（胸背/肩臂/腿）、**推拉腿分化**以及**自重核心燃脂**等更丰富、更符合健身逻辑的模板。
  3. **分类层级划分**：
     * **主分类**：按 **增肌**、**减脂** 等目标筛选。
     * **难度分级**：按 **新手 (初级)**、**进阶 (中级)**、**高级** 渐进式展示。
  4. **交互预览与场景选择**：在 `TemplatePreviewBottomSheetFragment` 中加入“场景版本”选择器（健身房、居家、自重）。选择不同场景时，动作列表实时刷新展示，点击导入即可导入该场景下的对应动作。

### 1.3 缺失个性化 AI 制定计划功能

* **现状**：目前仅有全局的 AI 聊天框，无法根据用户的具体画像（场景、频次、重点部位、目标）生成高度契合的训练计划。
* **重构方案**：右下角悬浮按钮（FAB）在点击后，在原有的“新建空白计划”、“从模板导入”中，新增一个 **“AI 制定计划”** 选项。点击后启动分步向导（Wizard）：
  * **第一步：训练目标**：选择主要目标（增肌、减脂、塑形）、每周训练天数（2~5天）、重点突击部位（可多选：胸、背、肩、臂、腹、腿）。
  * **第二步：场景选择**：选择训练设备场景（健身房、居家、自重）。
  * **第三步：智能生成与 AI 扩充**：
    1. **本地筛选推荐**：基于用户的前两步选择，从 `exercise_library.json` 本地库中匹配推荐基础动作，并在列表里以卡片形式展示，允许用户打勾、调整组数/次数。
    2. **AI 在线扩充**：用户可以点击“AI在线扩充”按钮，系统会将用户参数与本地动作发送至 DeepSeek 接口，让 AI 额外智能推荐 2-3 个动作，动态插入到推荐列表并赋予专属 AI 推荐角标。
  * **第四步：一键导入与智能排程**：点击生成后，智能将这批动作分配到对应的训练日（例如选 3 天则排入周一/三/五），写入数据库，完成计划创建。

---

## 2. 数据与逻辑架构设计

### 2.1 模板数据模型重构

修改 `TrainingTemplate` 实体，实现多版本适配：

```java
public class TrainingTemplate implements Serializable {
    private String name;
    private String shortDescription;
    private String description;
    private int difficulty; // 1=新手, 2=进阶, 3=高级
    private String goal; // 增肌, 减脂, 增力
    private int daysPerWeek;
    private Map<String, TemplateVersion> versions; // gym, home, bodyweight

    public static class TemplateVersion implements Serializable {
        private String equipment;
        private List<TemplateExercise> exercises;
      
        public String getEquipment() { return equipment; }
        public List<TemplateExercise> getExercises() { return exercises; }
    }
  
    // Getters and Setters...
    public TemplateVersion getVersion(String key) {
        return versions != null ? versions.get(key) : null;
    }
}
```

### 2.2 AI 在线扩充协议设计

向 AI 发送的系统 Instruction 及用户 Prompt 约定：

* **System Instruction**:
  `你是一个专业的健身 AI 私教。请基于用户的训练目标、设备场景和重点部位，为他额外推荐 2 到 3 个动作。必须以严谨的 JSON 格式输出，格式为：[{"name":"动作名","sets":3,"reps":12,"duration":0,"category":"部位","description":"动作要领"}]，除 JSON 外不要有任何多余的解释。`
* **Prompt**:
  `目标: 增肌, 设备场景: 居家(哑铃), 重点部位: 胸部/手臂, 每周训练: 3天。请额外推荐动作。`

---

## 3. UI 交互规范与视觉设计

* **极简、大留白**：采用 M3 风格，高对比度圆角卡片，以及带有按压微动效的 Segmented Button。
* **分步向导（Wizard）**：AI 制定计划采用流式卡片切换，带有淡入淡出动画，让每一步操作都清晰有度。
* **设备选择 Chip 组**：在预览模板时，展示 “健身房”、“居家”、“自重” 选项，切换时动作列表实时响应刷新。

