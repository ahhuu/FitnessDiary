# 日期选择器标记功能 — 设计文档

**日期:** 2026-05-28
**目标:** 为运动记录和饮食页面的日期选择器添加记录标记（绿点），修复饮食绿点 Bug，首页横滑日期加标记

---

## 问题分析

### Bug：饮食绿点不生效

`DietViewModel.getRecordedDates()` 用 `getUtcDayStartTimestamp()` 将记录时间戳转为 UTC 零点。历史记录存的 `recordDate` 为本地零点（如 `27日 00:00 CST`），转 UTC 后变为 `26日 16:00 UTC`，截断为 `UTC May 26`。MaterialDatePicker 装饰器回调给 `UTC May 27` → 历史记录全匹配失败。

**根因:** ViewModel 用 UTC 零点，装饰器也构造 UTC 零点，但输入数据是本地零点 → 两类 zeropoint 不重合。

**修复:** 统一用本地零点 `getDayStartTimestamp()` 做日期标识。

### 缺失：运动记录无标记

`SportRecordDetailFragment` 有 MaterialDatePicker 但无 `DayViewDecorator`。

### 缺失：首页 30 天横滑无标记

`DateNavigatorAdapter` 只有日期文字，无记录标记。

---

## 修改范围

| 文件 | 改动 | 说明 |
|------|------|------|
| `DietViewModel.java:146` | `getUtcDayStartTimestamp(ts)` → `getDayStartTimestamp(ts)` | 修复绿点 Bug |
| `DietFragment.java:199-261` | 装饰器时间戳构造改为本地零点 | 与 ViewModel 对齐 |
| `SportRecordDetailFragment.java:148-155` | 新增 DayViewDecorator | 运动记录绿点 |
| `DateNavigatorAdapter.java` | 新增 `recordedDates` 字段 + 绑定逻辑 | 30天横滑标记 |
| `res/layout/item_date_navigator.xml` | 日期下方加 6dp 绿色圆点 | 布局支持 |
| `CheckInFragment.java` | 传记录日期集合给 adapter | 数据接入 |

### 不涉及

- 不新增 DAO / 实体 / 数据库迁移
- 不改变 UI 整体布局结构
- 复用现有 `getRecordedDates()` 和 `getDayStartTimestamp()`

---

## 实现细节

### 1. 复用 `getDayStartTimestamp()` 对齐时区

```java
// DietViewModel.java:146  — 改这一行
dates.add(DateUtils.getDayStartTimestamp(ts));
```

### 2. 装饰器时间戳构造统一

DietFragment 和 SportRecordDetailFragment 共用以下模式：

```java
Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
utcCal.set(year, month, day, 12, 0, 0);  // 中午 UTC，避免跨日
long noonUtc = utcCal.getTimeInMillis();
long localDayStart = DateUtils.getDayStartTimestamp(noonUtc);
// 匹配: localDayStart ∈ recordedDates
```

### 3. SportRecordDetailFragment 新增装饰器

在 `showDatePickerDialog()` 中，从 `CheckInViewModel.getRecordedDates()` 获取已打卡日期，添加 DayViewDecorator 绘制绿点。

### 4. 横滑日期绿点

- `item_date_navigator.xml` 中加一个 6dp CircleView（默认 `android:visibility="gone"`）
- `DateNavigatorAdapter` 新增 `setRecordedDates(Set<Long>)` 方法
- 绑定 item 时：`dotView.setVisibility(recordedDates.contains(dateStart) ? VISIBLE : GONE)`
- `CheckInFragment` 观察 `getRecordedDates()` 变化后调用 adapter 更新
