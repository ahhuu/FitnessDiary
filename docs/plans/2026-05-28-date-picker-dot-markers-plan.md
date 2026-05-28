# 日期选择器记录标记 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为运动记录和饮食 MaterialDatePicker 添加记录绿点标记，修复饮食绿点 Bug，首页 30 天横滑日期加绿点

**Architecture:** 统一用本地零点 `getDayStartTimestamp()` 做日期标识，消灭 UTC 转换差异。饮食/运动/首页各自从 ViewModel 获取 `Set<Long>` 记录日期，用相同模式在 UI 层画绿点。

**Tech Stack:** Java 17, Room LiveData, MaterialDatePicker DayViewDecorator, RecyclerView Adapter

---

### Task 1: 修复两个 ViewModel 时区 Bug

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/viewmodel/DietViewModel.java:146`
- Modify: `app/src/main/java/com/cz/fitnessdiary/viewmodel/CheckInViewModel.java:56`

**原因:** `getUtcDayStartTimestamp()` 将本地零点转 UTC 零点时产生 8 小时偏移，历史记录全部匹配失败。

- [ ] **Step 1: 改 DietViewModel**

```java
// 第 146 行，改这一行
dates.add(DateUtils.getDayStartTimestamp(ts));
```

- [ ] **Step 2: 改 CheckInViewModel**

```java
// 第 56 行，改这一行  
potentialDates.add(DateUtils.getDayStartTimestamp(log.getDate()));
```

- [ ] **Step 3: 编译验证**

```bash
gradlew.bat assembleDebug
```

---

### Task 2: 修复 DietFragment 装饰器时区

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java:218-221`

- [ ] **Step 1: 替换装饰器时间戳构造方式**

将第 218-221 行：
```java
java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
cal.set(year, month, day, 0, 0, 0);
cal.set(java.util.Calendar.MILLISECOND, 0);
long utcStart = cal.getTimeInMillis();
```

改为：
```java
// 使用中午 UTC 避免跨日，再转本地零点
java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
cal.set(year, month, day, 12, 0, 0);
cal.set(java.util.Calendar.MILLISECOND, 0);
long noonUtc = cal.getTimeInMillis();
long dayStart = DateUtils.getDayStartTimestamp(noonUtc);
```

- [ ] **Step 2: 同步更新匹配行**

将第 225 行 `if (finalRecordedDates.contains(utcStart))` 改为：
```java
if (finalRecordedDates.contains(dayStart)) {
```

- [ ] **Step 3: 编译验证**

```bash
gradlew.bat assembleDebug
```

---

### Task 3: 运动记录添加绿点装饰器

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/SportRecordDetailFragment.java:143-155`

- [ ] **Step 1: 添加 import**

在文件头部 import 区补充：
```java
import com.google.android.material.datepicker.DayViewDecorator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import androidx.core.content.ContextCompat;
import java.util.HashSet;
import java.util.Set;
```

- [ ] **Step 2: 替换 showDatePicker 方法**

将现有的 `showDatePicker()` 方法（第 143-155 行）替换为：

```java
private void showDatePicker() {
    Long currentSelection = checkInViewModel.getSelectedDate().getValue();
    if (currentSelection == null) {
        currentSelection = System.currentTimeMillis();
    }

    // 获取已打卡日期
    Set<Long> checkedDates = checkInViewModel.getRecordedDates().getValue();
    if (checkedDates == null) checkedDates = new HashSet<>();
    final Set<Long> finalCheckedDates = checkedDates;

    // 装饰器：有运动打卡的日期画绿点
    DayViewDecorator decorator = new DayViewDecorator() {
        @Override
        public Drawable getCompoundDrawableBottom(android.content.Context context,
                int year, int month, int day, boolean valid, boolean selected) {
            java.util.Calendar cal = java.util.Calendar.getInstance(
                    java.util.TimeZone.getTimeZone("UTC"));
            cal.set(year, month, day, 12, 0, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long dayStart = DateUtils.getDayStartTimestamp(cal.getTimeInMillis());

            if (finalCheckedDates.contains(dayStart)) {
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setSize(12, 12);
                dot.setColor(ContextCompat.getColor(requireContext(), R.color.color_success));
                return new InsetDrawable(dot, 0, 0, 0, 4);
            }
            return null;
        }

        @Override public void writeToParcel(android.os.Parcel d, int f) {}
        @Override public int describeContents() { return 0; }
    };

    MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("选择日期")
            .setSelection(DateUtils.localToUtcDayStart(currentSelection))
            .setDayViewDecorator(decorator)
            .setCalendarConstraints(new CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointBackward.now())
                    .build())
            .build();
    datePicker.addOnPositiveButtonClickListener(
            selection -> checkInViewModel.setSelectedDate(selection));
    datePicker.show(getParentFragmentManager(), "SPORT_DETAIL_DATE_PICKER");
}
```

- [ ] **Step 3: 编译验证**

```bash
gradlew.bat assembleDebug
```

---

### Task 4: 横滑日期布局加绿点

**Files:**
- Modify: `app/src/main/res/layout/item_date_nav.xml`

- [ ] **Step 1: 在日期数字下方加圆点 View**

在 `tv_nav_day` 之后、`</LinearLayout>` 之前加：

```xml
<View
    android:id="@+id/v_nav_dot"
    android:layout_width="6dp"
    android:layout_height="6dp"
    android:layout_marginTop="2dp"
    android:background="@drawable/bg_nav_dot"
    android:visibility="gone" />
```

- [ ] **Step 2: 创建圆点背景 drawable**

新建 `app/src/main/res/drawable/bg_nav_dot.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/color_success" />
</shape>
```

---

### Task 5: DateNavigatorAdapter 支持绿点

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/adapter/DateNavigatorAdapter.java`

- [ ] **Step 1: 加字段和方法**

```java
private java.util.Set<Long> recordedDates = new java.util.HashSet<>();

public void setRecordedDates(java.util.Set<Long> dates) {
    this.recordedDates = dates != null ? dates : new java.util.HashSet<>();
    notifyDataSetChanged();
}
```

- [ ] **Step 2: ViewHolder 加 dot 引用**

```java
android.view.View vDot;

ViewHolder(View v) {
    super(v);
    tvDay = v.findViewById(R.id.tv_nav_day);
    tvWeek = v.findViewById(R.id.tv_nav_week);
    vDot = v.findViewById(R.id.v_nav_dot);
}
```

- [ ] **Step 3: onBindViewHolder 加绿点控制**

在 `onBindViewHolder` 方法末尾（`holder.itemView.setOnClickListener` 之后）加：

```java
// 绿点：当天有记录则显示
if (recordedDates.contains(DateUtils.getDayStartTimestamp(dateTs))) {
    holder.vDot.setVisibility(android.view.View.VISIBLE);
} else {
    holder.vDot.setVisibility(android.view.View.GONE);
}
```

- [ ] **Step 4: 编译验证**

```bash
gradlew.bat assembleDebug
```

---

### Task 6: CheckInFragment 接入记录日期

**Files:**
- Modify: `app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java:172-183`

- [ ] **Step 1: 在 selectedDate observer 后加 recordedDates observer**

在 `checkInViewModel.getSelectedDate().observe(...)` 块（第 172-183 行）之后加入：

```java
// 横滑日期绿点
checkInViewModel.getRecordedDates().observe(getViewLifecycleOwner(), dates -> {
    if (dateNavAdapter != null) {
        dateNavAdapter.setRecordedDates(dates);
    }
});
```

- [ ] **Step 2: 编译验证并安装测试**

```bash
gradlew.bat assembleDebug
gradlew.bat installDebug
```

---

### Task 7: 提交

- [ ] **Step 1: 提交所有改动**

```bash
git add app/src/main/java/com/cz/fitnessdiary/viewmodel/DietViewModel.java
git add app/src/main/java/com/cz/fitnessdiary/viewmodel/CheckInViewModel.java
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/SportRecordDetailFragment.java
git add app/src/main/java/com/cz/fitnessdiary/ui/adapter/DateNavigatorAdapter.java
git add app/src/main/java/com/cz/fitnessdiary/ui/fragment/CheckInFragment.java
git add app/src/main/res/layout/item_date_nav.xml
git add app/src/main/res/drawable/bg_nav_dot.xml
git commit -m "feat: add record dot markers to date pickers and date navigator"
```
