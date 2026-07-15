# 2026-07-14 AI 请求与饮食识别改动

## 完成事项

- 统一省 Token 请求策略：图片最长边压缩至 768px、JPEG 质量约 75%，限制聊天历史与输出长度，并缓存相同图片的最近识别结果。
- 将图片识别与聊天图片分析从 Qwen/DashScope 切换为 MiMo-V2.5，文本请求继续使用 DeepSeek。
- 增加本地 AI Token 使用统计，不保存图片 Base64；API Key 通过 `local.properties` 注入。
- 完善饮食识别的数量、单位和营养基准，支持 `TOTAL_PORTION`、`PER_100G`、`PER_100ML`、`PER_UNIT`，不明确的单位必须人工确认。
- 饮食详情页移除独立拍照/相册和扫码按钮，改为统一的 AI 记录入口；文字、图片、扫码结果统一进入可编辑确认面板，分别保存为独立 `FoodRecord`。
- 运动详情页不新增 AI 训练识别入口，继续使用已有“添加额外动作”，避免重复入口；私教页和计划管理页已有 AI 功能保持不变。
- 饮食文字记录先从本地饮食库按食物名称与数量匹配，只有完全没有匹配项时才调用 DeepSeek，减少不必要的 Token 消耗。
- 新增详情页底部弹窗遵循现有 Material 3 风格，保持私教页面原有界面和行为不变。

## 验证

- `:app:testDebugUnitTest`
- `:app:compileDebugJavaWithJavac`
- `assembleDebug`
- `git diff --check`

## 备注

- 本次在保留请求策略、MiMo 替换和饮食单位/营养基准改动的基础上，补充详情页 AI 记录闭环；私教页面仍不接入新的详情页记录流程。
