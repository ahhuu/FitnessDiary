# 通知提醒 Bug 修复与设置优化设计文档

## 1. 存在的问题

在 FitnessDiary 项目中，用户反馈了关于通知提醒的三个主要 Bug：
1. **如果在 APP 里弹出对应提醒通知，应用会闪退**：
   - **根本原因**：`ReminderReceiver.onReceive` 接收到闹钟广播时，是在主线程上执行的。但在处理 `ACTION_RECORD_REMINDER` (每日记录提醒) 和 `ACTION_WEEKLY_REPORT` (健康周报) 时，直接同步调用了访问 Room 数据库的方法（如 `ReminderManager.restoreAll` 和 `WeeklyReportHelper.getSummary`）。Android Room 默认禁止在主线程同步访问数据库，违反该规则会直接抛出 `IllegalStateException`，从而导致在前台/后台瞬间闪退。
   - **智能提醒快照获取失败**：在 `ACTION_MORNING_SUMMARY` / `ACTION_EVENING_REMINDER` 触发时，虽然 `tryGetTodaySnapshot()` 内部使用了 `try-catch`，但是由于它在主线程查库依然会引发 Room 报错，导致捕获异常并返回 `null`，使得智能简报无法读取昨日/今日的健康快照，总是退化为默认文案。
2. **通知类型太多，需要缩减**：
   - 目前除了“每日记录提醒”之外，还有 5 个智能提醒广播（早晨概要、晚间提醒、不活跃挽留、健康周报、欢迎广播）。其中，“不活跃挽留”和“智能助手已启动欢迎”通知较为鸡肋且属于打扰型通知。
3. **有些通知与提醒管理设置中没有的也会通知**：
   - 目前在“我的 - 系统通用设置 - 通知与提醒管理”设置界面（`dialog_notification_settings.xml`）中，只有用户在数据库里增删改的“预设/自定义记录提醒”。而早晨概要、晚间提醒、健康周报这 3 个系统级智能提醒在后台默认隐式运行，设置界面没有提供任何开关，用户无法将其关闭。

---

## 2. 解决方案

### A. 解决主线程查库闪退：广播异步化（`goAsync()`）
为了避免在 `BroadcastReceiver` 的主线程执行 Room 数据库操作导致闪退，我们将使用 Android 标准的异步接收器方案：
- 在 `ReminderReceiver.onReceive` 开始时，调用 `final PendingResult pendingResult = goAsync();`。
- 将全部处理逻辑放到后台的 `ExecutorService` 线程池中执行。
- 处理完毕后，在 `finally` 块中调用 `pendingResult.finish();`，从而优雅、安全地完成广播的接收与通知的发送，完全绕开主线程查询限制，同时彻底解决智能快照总是返回 `null` 的隐形 Bug。

### B. 缩减通知类型
- 彻底砍掉**不活跃挽留（ACTION_INACTIVITY_NUDGE）**和**欢迎广播（ACTION_SMART_WELCOME）**。
- 将所有的通知只缩减保留为：
  1. **记录提醒**：预设提醒（饮水、运动、睡眠等）及用户自定义添加的提醒（通过 `ReminderSchedule` 在数据库管理）。
  2. **智能助理提醒**：早晨概要、晚间提醒、健康周报（这 3 个为应用固定核心通知，并在设置中提供显式开关控制）。

### C. 智能助理提醒开关同步展示到设置界面
- 在 `dialog_notification_settings.xml` 增加一个独立的主题板块：**🧠 智能助理提醒**。
- 显式展示 3 个开关及其时间修改入口：
  - **早晨概要**：默认 08:00（Switch 开启/关闭，点击时间修改）。
  - **晚间提醒**：默认 20:00（Switch 开启/关闭，点击时间修改）。
  - **健康周报**：默认周一 09:00（Switch 开启/关闭，点击可修改“周几”与“具体时间”）。
- 在 `ProfileFragment.java` 弹出的设置 Dialog 中，对这 3 个卡片绑定 SharedPreferences 控制逻辑与 `TimePickerDialog`，当开关和时间变化时，直接调起 `ReminderManager` 中对应方法启用/取消闹钟，并将智能提醒的总开关隐式同步（当子项有开启时即保持自启恢复，不需要再依赖未暴露给用户的总开关）。

---

## 3. 具体修改细节

### 1) 布局修改 (`dialog_notification_settings.xml`)
在原有 RecyclerView 和 MaterialButton 之下增加：
- 一个 `TextView` 作为“智能助理提醒”小标题。
- 三个垂直布局块，对应早晨概要、晚间提醒、健康周报。每一块包含：
  - 水平排列的标题 `TextView` 与 `MaterialSwitch`。
  - 显示时间的 `TextView`（具有点击波纹效果）。
  - 底部分割线 `View`。

### 2) 广播接收器重构 (`ReminderReceiver.java`)
- `onReceive` 开头调用 `goAsync`。
- 使用 `Executors.newSingleThreadExecutor()` 在子线程运行：
  - 处理 `ACTION_BOOT_COMPLETED` 时：后台恢复提醒闹钟。
  - 处理 `ACTION_REMINDER` (旧训练提醒) 时：发送通知。
  - 处理 `ACTION_RECORD_REMINDER` 时：发送通知，后台恢复闹钟。
  - 处理 `ACTION_MORNING_SUMMARY` 时：正常获取快照，生成早晨通知。
  - 处理 `ACTION_EVENING_REMINDER` 时：正常获取快照，生成晚间通知。
  - 处理 `ACTION_WEEKLY_REPORT` 时：异步查询并生成周报，发送通知。
  - 清理/移除不活跃挽留及欢迎通知逻辑。
  - 在 `finally` 块中调用 `pendingResult.finish()`。

### 3) 提醒管理器调整 (`ReminderManager.java`)
- 开机自启恢复时，不再依赖总开关 `smart_reminder_enabled`，而是直接根据子开关（`KEY_MORNING_ENABLED`, `KEY_EVENING_ENABLED`, `KEY_WEEKLY_ENABLED`）的启用状态进行 restore。
- 移除/废弃不活跃挽留与欢迎通知的调度逻辑。
- 调整 `restoreSmartReminders` 恢复逻辑。

### 4) 设置弹窗绑定 (`ProfileFragment.java`)
- 在 `showNotificationSettingsDialog()` 中绑定新增的 Switch 与时间 TextView。
- 通过 SharedPreferences 保存并实时刷新提醒状态，修改时间时弹出 TimePicker 并重新设置 AlarmManager 闹钟。
