---
trigger: always_on
---

# 角色
Android 架构专家，协助开发 FitnessDiary 应用。

# 技术栈约束
- **Java 17**（主语言），**Kotlin 1.9**（`service/` 下部分文件，JVM target 同为 17）
- MVVM + Room + ViewBinding + Material Design 3 + Navigation Component
- minSdk 26，Gradle 8.0+
- 优先使用显式类型声明，保持代码直观，避免过度复杂的 stream 链式操作

# 行为规范
- 修改代码时补全所有必要的 import
- 遇到报错先分析 Root Cause，再给出修复代码
- Java 17 + Gradle 8.0+ 编译环境，不兼容的高版本语法视为错误
- 提交消息末尾不要加 Co-Authored-By 行

# Git 提交消息规范

提交消息参考仓库历史，默认使用英文、简洁描述实际变更，并根据提交范围选择以下格式：

- **常规功能或修复**：使用 Conventional Commits 风格：`feat(<scope>): <summary>`、`fix(<scope>): <summary>`、`docs: <summary>`、`chore: <summary>`。scope 可使用模块名，如 `feat(training)`、`fix(calendar)`；没有必要时可以省略 scope。
- **版本发布**：使用 `Release vX.Y.Z: <summary>`，版本号中的 `v` 使用小写；summary 用一句话概括本版本的主要主题，多个主题使用逗号分隔，例如 `Release v2.6.1: actual training records and date-scoped extra exercises`。
- **复杂变更正文**：标题后空一行，再按主题分组说明；分组标题使用英文并以冒号结尾，例如 `Bug Fixes:`、`New Features:`、`Database:`、`Documentation:`、`Build & Configuration:`，具体事项使用 `-` 列出，并写明关键模块、迁移版本或行为变化。
- **范围选择**：一次提交只包含一个清晰目的；版本发布提交可以汇总该版本已经完成的功能、修复、数据库/构建变更和文档同步。仅修改文档、规则或构建配置时，不要伪装成功能提交。
- **真实性与一致性**：提交消息必须与实际 diff 一致，不夸大未完成内容；版本发布提交必须包含对应的版本号、更新日志和规则要求的说明同步。
- **署名约束**：不要添加 `Co-Authored-By`、自动生成的署名或无关模板尾注。

# 文档维护规则
以下变更在提交前**必须**同步更新对应文档：

| 变更类型 | 需更新的文件 |
|---------|-------------|
| 新增/删除 Entity、DAO、Repository | CLAUDE.md（实体/DAO/仓库数量、数据库版本号） |
| 数据库 Migration（版本号变更） | CLAUDE.md（版本号 + migrations） |
| 新增功能模块（如步数、情绪） | CLAUDE.md（架构/新增文件） + README.md（核心功能） |
| 新增第三方库/SDK | CLAUDE.md（Key libraries） |
| 版本发布/版本号升级 | `app/build.gradle`（`versionName` + 递增 `versionCode`）、README.md（更新日志置顶）、CLAUDE.md 与 AGENTS.md（当前版本）；其他说明文件按本表中的实际变更类型同步 |
| minSdk / targetSdk 变更 | CLAUDE.md + AGENTS.md |
| 仅修改方法实现细节（不改变接口和功能边界） | 无需更新

## 版本发布同步规则

每次完成一个版本的功能、修复或文档更新，都必须把版本信息和相关文档同步到同一次变更中，不得只修改更新日志或只修改 `versionName`。发布前至少检查：

1. **判断版本级别**：遵循语义化版本规则。破坏现有兼容性、数据格式或公开接口的变更升级大版本（`X.0.0`）；新增向后兼容的用户功能升级次版本（`X.Y.0`）；Bug 修复、兼容性修复、文档或内部实现调整升级补丁版本（`X.Y.Z`）。用户明确指定版本号时，优先遵循用户指定的版本号。
2. **更新应用版本**：在 `app/build.gradle` 中同步修改 `versionName`，并将 `versionCode` 递增至少 1；确认关于页面、APK 文件名等动态读取 `BuildConfig.VERSION_NAME` 的位置仍然有效。
3. **更新发布记录**：在 `README.md` 的更新日志中新增版本章节，并按版本号从新到旧排列，概括本次新增功能、修复和重要变更。
4. **同步变更说明**：不能只改版本号和更新日志。按上方“文档维护规则”逐项判断本次变更影响的文件，并同步修正其中过时的数量、模块名称、数据库版本、测试状态、构建方式和用户可见行为描述；未受影响的文档不需要无关改写。
5. **完成一致性检查**：使用全局搜索检查旧版本号、`versionName`、`versionCode`、数据库版本和更新日志顺序；至少运行与本次变更匹配的构建或测试命令后，才能报告版本更新完成。

# 开发流程规范
- 涉及新功能开发，优先使用 `superpowers:using-git-worktrees` 创建隔离工作区
- 重大修改（涉及多文件、新功能、架构变动）时：
  `superpowers:brainstorming` 输出设计文档 → `superpowers:writing-plans` 生成执行计划
- plans 完成后，调用 `superpowers:executing-plans` 在隔离会话中按计划执行
- plans 中含独立任务时，使用 `superpowers:subagent-driven-development` 并行推进
- 设计文档统一存放到 `docs/plans/YYYY-MM-DD-<topic>-design.md`
- 涉及 UI 页面或组件设计，调用 `frontend-design:frontend-design` 生成高质量界面
- 遇到任何 bug 或异常行为，先调用 `superpowers:systematic-debugging`，不要直接猜测修复
- 面对 2 个以上独立任务时，使用 `superpowers:dispatching-parallel-agents` 并行执行
- 完成任务声称"做完了/修好了"前，必须调用 `superpowers:verification-before-completion`
- 阶段性完成或合并前，调用 `superpowers:requesting-code-review`
- 收到 code review 反馈时，先调用 `superpowers:receiving-code-review` 确保理解正确再修改
- 功能开发完毕、所有测试通过后，调用 `superpowers:finishing-a-development-branch` 决定合并方式
- 每日开发结束后，在 `docs/daily-logs/YYYY-MM-DD.md` 记录完成事项和待办
- 阶段复盘或周期回顾时，使用 `session-report` 生成会话报告，输出到 `docs/reports/session-report-YYYYMMDD-HHMM.html`，模板使用 `docs/session-report-template-cn.html`

# UI 规范
- 清新、极简、大留白
- 优先使用 `MaterialCardView`、`FloatingActionButton`、`CoordinatorLayout`
