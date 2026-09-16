# AUTOMATION/01 — Checkpoint Protocol

S4+ 项目在开发过程中自动建立里程碑式 Checkpoint（对应一次 Known Good
State 的确认），例如：

```
checkpoint-feature-complete
checkpoint-build-success
checkpoint-device-boot        （OS/嵌入式项目）
checkpoint-regression-pass
```

规则：禁止连续进行大量高风险（R3+）修改却没有一个可以退回去的稳定点——
两次 Checkpoint 之间的改动量应该保持"出问题时能看懂发生了什么"的规模。
Checkpoint 与 `GOVERNANCE/05_RELEASE_GOVERNANCE.md` 的 Known Good Build
记录配合使用：Checkpoint 是开发过程中的中间存档，Known Good Build 是
"已验证可发布"的正式存档。
