# 本轮 AI 改动代码审查待处理清单

日期：2026-07-15
范围：MiMo 替换、省 Token、饮食单位与营养基准、饮食详情页 AI 记录、图片次数限制、私教思考过程设置及相关 UI。

## 结论

当前业务代码可以编译运行，但还存在发布安全风险、饮食营养数据正确性风险、额度并发问题和少量冗余代码。本文件只记录待处理项，本轮审查没有修改业务代码。

验证结果：

- `:app:compileDebugJavaWithJavac`：通过
- `:app:testDebugUnitTest`：通过
- `assembleDebug`：通过
- `git diff --check`：通过
- `:app:lintDebug`：失败，7 个 error、约 2076 个 warning

## P0：发布前必须处理

### 1. API Key 和会员码不能放进 APK

相关位置：

- `app/build.gradle`：`DEEPSEEK_API_KEY`、`MIMO_API_KEY`、`MEMBERSHIP_UNLOCK_CODE`
- `app/src/main/java/com/cz/fitnessdiary/service/DeepSeekService.kt`
- `app/src/main/java/com/cz/fitnessdiary/service/MiMoService.kt`
- `app/src/main/java/com/cz/fitnessdiary/service/FoodImageQuotaStore.java`

当前密钥和会员码通过 `BuildConfig` 进入 APK，可被反编译获取。会员码一旦泄露，所有用户都能解锁；本地 SharedPreferences 额度也不能作为真实的账号级限制。

处理建议：

- AI 请求改为服务端代理。
- 会员权益和图片额度改为服务端按账号校验。
- 客户端只保存短期会话凭证，不保存供应商 Key 或固定会员码。
- 轮换已经出现在本地配置、日志或共享环境中的供应商 Key。

### 2. 清理无用的 Baidu 配置并轮换密钥

已检查当前源码、`app/build.gradle` 和根 Gradle 配置，未发现 `baidu.api.key` 或 `baidu.secret.key` 的调用、BuildConfig 注入或网络请求；它们目前只存在于 `local.properties`，属于遗留无效配置。

处理建议：

- 如果确认没有其他本地脚本依赖，删除这两项配置。
- 如果曾经提交、上传、截图或发送过，立即在百度控制台作废并重新生成。
- 同样删除 `local.properties` 中残留的旧 Qwen Key，避免误用或泄露。

## P1：优先修复

### 3. 未知饮食单位会被错误接受

相关位置：

- `app/src/main/java/com/cz/fitnessdiary/utils/FoodUnitUtils.java`
- `app/src/main/java/com/cz/fitnessdiary/model/ImageFoodItemDraft.java`
- `app/src/main/java/com/cz/fitnessdiary/service/AiDietTextAnalyzer.java`
- `app/src/main/java/com/cz/fitnessdiary/service/FoodImageAnalyzer.java`

`isSupported()` 先把未知单位归一化成“份”，再判断是否在合法集合中，因此 `scoop` 等未知单位可能被当成合法的“份”。原始单位也会在草稿模型中丢失，确认页无法准确提示用户。

处理建议：保留原始单位或单独返回 `UNKNOWN`，只有明确白名单单位才能通过确认；未知单位必须保持 `needsReview=true`。

### 4. 修改数量/单位后没有重新计算营养值

相关位置：

- `app/src/main/java/com/cz/fitnessdiary/service/AiDietTextAnalyzer.java`
- `app/src/main/java/com/cz/fitnessdiary/service/FoodImageAnalyzer.java`
- `app/src/main/java/com/cz/fitnessdiary/ui/fragment/FoodImageConfirmBottomSheet.java`
- `app/src/main/java/com/cz/fitnessdiary/viewmodel/DietViewModel.java`

解析时会根据 `PER_100G`、`PER_100ML`、`PER_UNIT` 计算一次，但确认页修改数量或单位时只更新字段，不重新换算。最终可能把旧数量的营养值保存到新数量上。

处理建议：草稿保存“原始营养基准值”和“当前总量”，数量/单位变化时统一重新计算；无法可靠换算时禁止保存，要求用户手动填写总营养值。

### 5. 图片额度不是可靠的用户级限制

相关位置：

- `app/src/main/java/com/cz/fitnessdiary/service/FoodImageQuotaStore.java`
- `app/src/main/java/com/cz/fitnessdiary/viewmodel/DietViewModel.java`
- `app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java`

额度使用本地 `User.uid`，用户为空时使用固定 key `0`。如果 LiveData 尚未加载，第一次请求可能记到 key `0`，后续真实用户又获得新的额度。卸载重装、清除应用数据也会重置额度。

处理建议：服务端按稳定账号 ID 计数；本地只作为展示缓存。用户信息未加载时应阻止图片请求，而不是使用默认用户 key。

### 6. 额度拒绝路径可能放行旧的进行中请求

相关位置：`app/src/main/java/com/cz/fitnessdiary/viewmodel/DietViewModel.java`

当前先校验额度，额度不足直接返回，之后才递增 `foodAnalyzeToken`。如果之前的请求仍在进行，旧请求仍可能通过原 token 校验并更新识别结果。

处理建议：进入任何新请求前先递增 token，使旧请求立即失效；再进行额度检查。

### 7. “重新识别”没有真正绕过缓存

相关位置：

- `app/src/main/java/com/cz/fitnessdiary/viewmodel/DietViewModel.java`
- `app/src/main/java/com/cz/fitnessdiary/ui/fragment/DietFragment.java`

已有 `forceAnalyzeMealImage()` 会清缓存，但当前没有调用方；确认页重试最终调用普通 `analyzeMealImage()`，相同图片会直接返回缓存结果，不会重新请求 MiMo。

此外，文字和扫码草稿也显示同一个重试按钮，点击后可能重试上一张旧图片。

处理建议：给草稿增加来源类型；只有图片来源显示“重新识别”，并明确调用清缓存的强制重试方法。

### 8. 保存动作缺少一次性保护

相关位置：

- `app/src/main/java/com/cz/fitnessdiary/ui/fragment/FoodImageConfirmBottomSheet.java`
- `app/src/main/java/com/cz/fitnessdiary/viewmodel/DietViewModel.java`

确认按钮发送结果后没有立即禁用，保存层也没有草稿 ID 或幂等保护。快速双击可能写入重复的 `FoodRecord`。

处理建议：点击后立即禁用按钮；为草稿生成唯一 ID；保存层增加一次性消费或幂等校验。

### 9. 空草稿可能保存为“请手动补充食物”

相关位置：`app/src/main/java/com/cz/fitnessdiary/ui/fragment/FoodImageConfirmBottomSheet.java`

删除全部项目后，确认页会自动插入一个占位食物，且占位项目默认不一定需要复核，可能被保存成零营养记录。

处理建议：没有有效食物名称时禁止保存，不要把占位文本写入 `FoodRecord`。

## P2：后续清理和改进

### 10. 本地饮食库部分匹配会静默丢失未匹配食物

`DietLibraryTextMatcher` 只要匹配到一项，页面就不会调用 AI。输入中其他不在饮食库的食物会被忽略，也没有提示用户。

处理建议：显示“已匹配项目”和“未匹配文本”；用户确认后再决定是否调用 AI 补充。

### 11. 食物库同步规则过于宽松

`FoodUnitUtils.isReliableLibraryUnit()` 对容器、包装和份数单位只要存在估算重量就可能返回可靠，和“无法可靠换算时不自动同步”的产品规则不完全一致。

处理建议：只有质量单位，或有明确可靠换算关系且用户确认过的单位，才能同步到食物库。

### 12. 清理未使用接口和兼容代码

当前未发现调用方的代码：

- `DietViewModel.forceAnalyzeMealImage()`
- `DietViewModel.saveImageMealDraftLegacy()`
- `AiUsageStore.getTodayTokens()`
- `AiUsageStore.getMonthTokens()`
- `AiUsageStore.getCacheHitTokens()`
- `AiUsageStore.clear()`

处理建议：如果近期没有使用计划，删除；否则补上页面入口和测试，避免形成“看似支持、实际未接入”的功能。

### 13. 修复本轮新增 lint 错误

`app/src/main/res/layout/item_food_draft_edit.xml` 使用了 `android:tint`，应改为 `app:tint`。当前 lint 失败的其他错误主要来自既有主题、资源和 Manifest 配置，但本轮新增布局至少引入了这一项错误。

### 14. 降低日志和错误响应中的隐私暴露

`DeepSeekService` 和 `MiMoService` 会把供应商完整错误响应写入 Logcat。应避免记录可能包含请求上下文、配额信息或内部错误细节的原始响应，生产构建应进一步降低日志级别。

## 已确认正常的部分

- 当前 `app/src/main` 已无 Qwen/DashScope 运行时调用。
- MiMo 图片压缩最长边和 JPEG 质量策略已集中处理。
- 食物图片识别不携带聊天历史。
- 私教思考过程默认隐藏，设置入口已放入“显示与单位设置”。
- 编译、单元测试、Debug APK 构建均通过。
