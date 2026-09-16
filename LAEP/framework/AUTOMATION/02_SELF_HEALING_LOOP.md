# AUTOMATION/02 — Self-Healing Loop（自愈开发循环）

`CORE/06_ERROR_HANDLING.md` 定义的 Diagnose → Root Cause → Fix → Retest
循环，允许持续运行直到满足 `QUALITY/02_DEFINITION_OF_DONE.md`：

```
Test → Bug → Analyze → Fix → Regression → （问题解决？）
  否 → 回到 Analyze（同一根因第 3 次失败 → 触发 Stagnation Detection，
       见 CORE/06 与 CORE/08 §8.6）
  是 → 结束本轮，SELECT NEXT TASK（CORE/08 §8.4 主循环）
```

这条循环是持续自主执行（`CORE/08`）能够安全运行的关键机制之一：没有它，
"不问用户、自主推进"会退化成"遇到问题就无限乱试"；有了 Stagnation
Detection 的硬性熔断，才能在保持自主的同时不把项目越改越坏。
