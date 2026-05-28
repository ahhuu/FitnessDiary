# AGENTS.md — FitnessDiary 项目入口

> 本文档为各 AI 编码代理提供项目入口索引。详细架构见 CLAUDE.md，行为约束见 .agent/rules/。

## 优先级

```
.agent/rules/*  >  CLAUDE.md  >  AGENTS.md
```

AGENTS.md 为通用参考，具体行为以 `.agent/rules/fitnessdiary-app.md` 为准。

## 项目概述

FitnessDiary（健身日记）是一款极简的 Android 个人健康管理应用，涵盖每日打卡、训练计划、饮食记录、身体指标分析、成就系统等功能。

- **技术栈:** Java 17 + Kotlin, MVVM, Room, ViewBinding, Material Design 3, Navigation Component
- **compileSdk / targetSdk / minSdk:** 34 / 34 / 26
- **applicationId:** `com.cz.fitnessdiary`

## 快速链接

| 文档 | 用途 |
|------|------|
| [CLAUDE.md](CLAUDE.md) | 构建命令、架构设计、数据流、迁移模式、后台系统 |
| [.agent/rules/fitnessdiary-app.md](.agent/rules/fitnessdiary-app.md) | 角色设定、技术栈约束、行为规范、UI 规范 |

## 构建

```bash
# Windows
gradlew.bat assembleDebug
gradlew.bat installDebug

# macOS / Linux
./gradlew assembleDebug
./gradlew installDebug
```

## 配置

`app/build.gradle` 从 `local.properties` 读取 API key 注入 BuildConfig：`gemini.api.key`、`deepseek.api.key`、`qwen.api.key`。

## 版本记录

- v1.8: compileOptions / kotlinOptions 已升级至 Java 17
- 当前无测试目录（`app/src/test`、`app/src/androidTest`）
