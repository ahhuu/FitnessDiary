 

# 21天挑战升级设计方案

本项目标在于将原有的“21天挑战”系统升级为一个板块化、高颜值、支持用户完全自定义挑战的健康促进核心引擎。

## 1. 核心需求剖析

1. **板块化分类**：将挑战项目划分为“饮食轻食 🥑”、“日常运动 🏃”、“作息健康 🌙”、“我的定制 ✏️”四大板块，以清晰的过滤 Chip 展现。
2. **丰富的预设挑战库**：
   - **饮食板块**：减脂冲刺（控制热量）、控糖挑战（手动）、饮水达人（多喝水）
   - **运动板块**：健身达人（运动日志）、万步达人（步数记录）、日常拉伸（手动）
   - **作息板块**：早睡挑战（睡眠记录）、早起打卡（手动）、冥想静心（手动）
3. **用户自定义挑战**：
   - 支持自定义名称、描述、允许失败的天数（容错机会）。
   - 支持自定义 Emoji 图标（网格选择器）。
   - 支持关联校验卡片（热量、运动、睡眠、饮水、步数、手动打卡）。
4. **高颜值 UI/UX 设计**：
   - 弃用传统的 AlertDialog，统一采用圆润柔滑的 `BottomSheetDialogFragment`。
   - 使用卡片式悬浮立体设计，带浅色高光渐变背景色。
   - 挑战详情页显示 **21 个指示圆点/微型方格网格**，以直观且高级的打卡印章形式呈现每日的历史实况。
   - 自定义输入表单具备极佳的响应式设计与字段校验。
5. **潜在逻辑 Bug 订正（重要）**：
   - **原逻辑 Bug**：`ChallengeManager.checkToday()` 在白天（如上午10点）打开应用时，会直接校验**今天**的数据。由于用户刚起床或今天还未结束，会导致判定今日不达标（直接扣除失败天数），且由于 `last_check` 被更新为今天，导致当晚无法再重新计算。
   - **修复逻辑**：实行**结算昨天（以 `lastCheck` 为基准）**的多日补偿结算算法。当今天打开 APP 且 `lastCheck != today` 时，依次校验从 `lastCheck` 往后每一天（如昨天、前天）的完整历史数据。第一天开启挑战时，只做 `lastCheck = today` 标记，不执行扣分。

---

## 2. 数据库与持久化设计

由于本应用为离线单机应用，为了最大限度降低 Room 迁移带来的库升级复杂性，我们将用户的自定义挑战、打卡轨迹以 JSON 序列化存入 `challenge_prefs`。

### 2.1 自定义挑战模型 (`Challenge`)

```java
public class Challenge {
    private String id;          // 唯一标识 (UUID)
    private String name;        // 标题，如 "戒断晚间甜食"
    private String desc;        // 说明，如 "21天不吃高糖零食"
    private String emoji;       // 图标，如 "❌"
    private int category;       // 分类：0-饮食, 1-运动, 2-作息, 3-自定义
    private int maxFails;       // 允许失败天数
    private String bindCard;    // 校验绑定：FAT_LOSS / MUSCLE_GAIN / EARLY_SLEEP / WATER_MASTER / STEP / NONE (手动)
}
```

### 2.2 手动打卡数据状态

针对 `bindCard = "NONE"` 的手动挑战打卡：
当用户打卡时，将打卡数据存入 SharedPreferences 键 `"checked_day_" + startTimestamp`（boolean）。
结算时，读取对应结算日的值，校验完毕后将键清理。

---

## 3. UI 交互模块与状态设计

`ChallengeBottomSheetFragment` 将承载整个交互：

```
                    ┌──────────────────────────────┐
                    │ ChallengeBottomSheetFragment │
                    └──────────────┬───────────────┘
                                   │
                ┌──────────────────┼──────────────────┐
                ▼                  ▼                  ▼
      ┌──────────────────┐   ┌──────────────┐   ┌────────────┐
      │  进行中详情面板  │   │  选择库面板  │   │ 自定义面板 │
      └──────────────────┘   └──────────────┘   └────────────┘
```

### 3.1 预设挑战库定义

```java
// ChallengeManager.java
public static List<Challenge> getPresetChallenges() {
    List<Challenge> list = new ArrayList<>();
    // 饮食板块
    list.add(new Challenge("FAT_LOSS", "减脂冲刺", "21天每日热量不超标\n累计3天超标则失败", "🔥", 0, 3, "FAT_LOSS"));
    list.add(new Challenge("WATER_MASTER", "饮水达人", "21天饮水≥2000ml\n累计3天不达标则失败", "💧", 0, 3, "WATER_MASTER"));
    list.add(new Challenge("DIET_SUGAR", "控糖抗糖", "21天控制糖分零食摄入\n累计2天越界则失败", "🍬", 0, 2, "NONE"));
  
    // 运动板块
    list.add(new Challenge("MUSCLE_GAIN", "健身达人", "21天运动不间断\n连续2天中断则失败", "💪", 1, 2, "MUSCLE_GAIN"));
    list.add(new Challenge("WALK_10K", "万步达人", "21天每日步数达10000步\n累计3天不达标则失败", "🏃", 1, 3, "STEP"));
    list.add(new Challenge("DAILY_STRETCH", "日常拉伸", "21天每日完成身体拉伸\n累计2天中断则失败", "🧘", 1, 2, "NONE"));

    // 作息板块
    list.add(new Challenge("EARLY_SLEEP", "早睡挑战", "21天23:00前入睡\n累计3天晚睡则失败", "🌙", 2, 3, "EARLY_SLEEP"));
    list.add(new Challenge("EARLY_WAKE", "高效早起", "21天早晨7:30前起床\n累计3天迟起则失败", "⏰", 2, 3, "NONE"));
    list.add(new Challenge("MINDFULNESS", "冥想静心", "21天每日完成10分钟正念\n累计2天不达标则失败", "🧘", 2, 2, "NONE"));
    return list;
}
```

---

## 4. 关键验证算法的订正与多日补偿

```java
// 多日循环结算，由 lastCheck 追赶到 today
long tempCheck = lastCheck;
while (tempCheck < today) {
    long checkStart = tempCheck;
    long checkEnd = tempCheck + 86400000L;
  
    int currentDayIndex = (int) ((checkStart - start) / 86400000L) + 1;
    if (currentDayIndex > 21) {
        // 超出挑战天数，正常结束
        break;
    }
  
    boolean dayFailed = verifyDayData(context, bindCard, checkStart, checkEnd);
    if (dayFailed) {
        fails++;
        if (fails >= maxFails) {
            status = "FAILED";
            break;
        }
    }
  
    tempCheck += 86400000L;
}
```

---

## 5. UI 美学设计

1. **打卡轨迹格**：在进行中详情面板，使用一个横向 7 列、纵向 3 行的网格，放置 21 个印章小块。
   - `已达标`：绿叶微光卡片
   - `未达标`：红心灰底卡片
   - `未来天`：极简浅灰圆环
2. **呼吸打卡 Button**：手动挑战时，打卡按钮增加 Material 3 充盈动画及微秒的阻尼反馈，大幅度提升打卡完成的成就感。
3. **输入微选单**：自定义面板的打卡类型与 Emoji 网格在选中时自带涟漪效果，且背景色自适应亮起，视觉质感极为高级。
