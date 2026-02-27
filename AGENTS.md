# AGENTS.md — FitnessDiary 项目协作指南

> 说明：本文档基于仓库现状与 .agent 规则生成，旨在帮助协作代理快速理解项目约束与构建方式。

## 项目概述
- 应用定位：FitnessDiary（健身日记）是一款极简、高效的 Android 个人健康管理应用，包含每日打卡、训练计划、饮食记录、身体指标分析与成就系统等功能。
- 主要技术栈：Java、MVVM、Room、ViewBinding、Material Design 3、Navigation Component。

## 仓库结构
- `app/src/main/java/com/cz/fitnessdiary/database`：Room 数据库与入口 `AppDatabase`。
- `app/src/main/java/com/cz/fitnessdiary/database/dao`：各实体 DAO。
- `app/src/main/java/com/cz/fitnessdiary/database/entity`：Room 实体。
- `app/src/main/java/com/cz/fitnessdiary/repository`：仓库层。
- `app/src/main/java/com/cz/fitnessdiary/ui`：Activity、Fragment 与 Adapter。
- `app/src/main/java/com/cz/fitnessdiary/viewmodel`：ViewModel 层。
- `app/src/main/java/com/cz/fitnessdiary/utils`：通用工具类。
- `docs/`：历史方案文档与数据库备份（只读参考）。

## 构建与运行
- Windows：
  - `gradlew.bat assembleDebug`
  - `gradlew.bat installDebug`
- 其他系统：
  - `./gradlew assembleDebug`
  - `./gradlew installDebug`

## 测试
- 当前未检测到 `app/src/test` 或 `app/src/androidTest` 测试源码目录。
- 标准命令（若后续补充测试可使用）：
  - `gradlew.bat test`
  - `gradlew.bat connectedAndroidTest`

## 配置与密钥
- `app/build.gradle` 会从 `local.properties` 读取并注入 BuildConfig：
  - `gemini.api.key`
  - `deepseek.api.key`
  - `qwen.api.key`

## 代理工作规则（来自 .agent/rules/fitnessdiary-app.md）
- 角色设定：Android 架构专家，面向初学者协作。
- 语言约束：
  - 必须兼容 Java 17。
  - 允许使用 Java 17 特性（包含 `var`），但需保证可读性。
  - 仍建议显式声明变量类型以便初学者理解。
- 架构/库约束：
  - MVVM、Room、ViewBinding、Material Design 3、Navigation Component。
- 交互与输出规范：
  - 修改文件时必须输出完整文件内容。
  - 必须补全必要 import。
  - 修改前先用通俗比喻解释，再分步指导。
- UI/UX：清新、极简、大留白；优先 `MaterialCardView`、`FloatingActionButton`、`CoordinatorLayout`。
- 报错处理：先分析 Root Cause，再给修复代码。

## 版本一致性说明
- `.agent` 规则已更新为 Java 17。
- `app/build.gradle` 当前 `compileOptions` 与 `kotlinOptions` 使用 Java 17（`sourceCompatibility/targetCompatibility` 为 17，`jvmTarget` 为 17）。
- 协作代理应按 Java 17 编写/修改代码。

## 关键信息清单
- `compileSdk`: 34
- `targetSdk`: 34
- `minSdk`: 26
- `applicationId`: `com.cz.fitnessdiary`
