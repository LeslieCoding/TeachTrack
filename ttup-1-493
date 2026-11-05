# ANDROID_APP/DEPLOYMENT/06 — Log Capture & Crash Auto-Fix

## 自动 Logcat

部署成功后自动进入日志观察，必须按 package / PID 过滤，并聚焦
`AndroidRuntime`、`FATAL EXCEPTION`、`ANR`、`System.err` 等关键标签——
**禁止把整个 Android 系统几十万行日志原样丢给 AI 处理**，那不是有效
输入，是噪音。

## Crash 自动修复循环

发现 `FATAL EXCEPTION`：

```
Capture → Analyze Stack Trace → Find Source → Fix
→ Build → Install → Launch → Retest
```

用户不需要"截图报错再发给 AI"——这条循环由 AI 自己跑完，遵循
`CORE/06_ERROR_HANDLING.md` 的自愈纪律（连续 3 次同一根因失败同样触发
Stagnation Detection，不是 Android 专属的另一套机制，是 CORE 机制在这里
的具体应用）。

## 自动截图 / UI Hierarchy

测试出现 UI 异常时，允许自动 `capture screenshot`，不需要每次要求用户
"请截图发给我"。需要调试界面结构时，允许自动获取 UI hierarchy，配合
Test Agent 判断控件是否存在、页面是否正确跳转、文本/状态是否符合预期。
