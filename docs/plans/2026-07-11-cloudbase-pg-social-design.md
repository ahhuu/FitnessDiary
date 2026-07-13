# FitnessDiary CloudBase PostgreSQL 社交设计

## 目标

提供可选的邮箱验证码登录、好友关系和仅分享用户主动确认摘要的动态功能。完整健康记录继续只保存在 Android Room。

## 架构

`Android (OkHttp/Gson) -> CloudBase PostgreSQL REST/RPC`

Android 只保存短期 access token 和 Keystore 加密的 refresh token。社交写入统一进入 `SECURITY DEFINER` RPC，由数据库通过 `auth.uid()`、RLS 和白名单校验执行最终授权。

## 数据边界

`profiles`、好友关系、动态、点赞、通知、屏蔽、举报和幂等键由 schema 创建。`posts.health_summary` 只允许 `workoutMinutes`、`checkInDays`、`steps` 和真实 `achievement`；体重、饮食明细、经期、备注等原始数据不上传。

当前版本暂时关闭动态图片和头像上传，数据库中的图片字段仅作为未来独立安全评审的预留字段。

## 验证

执行 `cloudbase-api/tests/social_security_regression.sql`，并使用两个认证测试账号验证越权资料更新、非法摘要、私密动态泄露、双向屏蔽和拒绝后重新申请等场景。
