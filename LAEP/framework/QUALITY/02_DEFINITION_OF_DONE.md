# QUALITY/02 — Definition of Done

## 任务级 DoD

```
Implemented
+ Build Passed
+ Required Tests Passed（OBSERVED/MEASURED，见 CORE/04）
+ No Critical Regression（含逐条核对相关 Invariant）
+ Documentation Updated
```

全部满足才能标记 `DONE`。这是所有 Scale 共用的底线，Scale 只决定"哪些
测试是 Required"，不降低这条标准本身。

Profile 可以在此基础上追加自己的必需 Gate（不得降低，只能加严）。例如
`ANDROID_APP` 追加 Install Passed + Launch Passed + No Critical Crash，
见 `PROFILES/ANDROID_APP/DEPLOYMENT/00_ANDROID_DEPLOYMENT.md`。

涉及管理员/sudo 操作的任务，追加：No Redundant Elevation + No Security
Policy Bypass（见 `AUTOMATION/04_PRIVILEGE_PREFLIGHT.md`）。

面向真实终端用户交付的项目/功能，追加 `QUALITY/03_ACCEPTANCE_PROTOCOL.md`
定义的 USER_READY_DoD（用户真实入口已验证、GUI 项目的可见性已验证、核心
能力未被静默降级、核心用户旅程已走通）——"代码写完了""Build 通过了""进程
能启动"都不等于这一层通过，不得把这几者当作等价物（`QUALITY/03` 的
"禁止的验收替代"表）。

## 质量门禁（Quality Gates）

```
Gate 0  Requirement Understood
        （若属于新需求/重大功能/架构变更：Engineering Baseline 已
          DoR PASS，见 CORE/01）
Gate 1  Specification Ready
Gate 2  Architecture Ready
Gate 3  Implementation Ready
Gate 4  Code Complete
Gate 5  Build Passed
Gate 6  Tests Passed
Gate 7  Regression Passed（含 Invariant 核对）
Gate 8  Security / Risk Check Passed
Gate 9  Acceptance Passed（只能由 OBSERVED/MEASURED 证据关闭）
Gate 10 Documentation Updated
Gate 11 Project State Updated
Gate 12 Traceability Row Closed（S3+，不是 MISSING）
Gate 13 Baseline After 已记录（若为优化类任务，见 CORE/04）
Gate 14 Git 事务已干净提交（AUTOMATION/03）
Gate 15 File Lease 已释放（若持有过，AGENTS/00）
Gate 16 User Entrypoint Verified（面向真实用户交付时，QUALITY/03）
Gate 17 Visible UI Verified（GUI 项目，QUALITY/03 Black Screen Gate）
Gate 18 Core User Journey Verified（QUALITY/03）
Gate 19 Delivery Artifact Verified（若项目预期产出独立交付物，QUALITY/03）
```

Gate 16-19 不适用的项目类型（如无独立交付物的 LIBRARY）标记
`NOT_APPLICABLE`，不是不检查就默认通过。

以上全部是 **AI 内部检查点**，不是用户审批点（`CORE/08` §8.3）——通过即
授权立刻进入下一道，不等用户确认。

## Visual Regression（UI 类任务）

改动前后必须留有证据：Golden Screenshot、Layout Bounds、文本溢出、
不同分辨率/DPI、主题（如支持）、以及每一种交互状态（hover/pressed/
disabled/loading/empty/error）。"UI 已优化"没有前后对比证据，只是一句
ASSUMED 的话，关不掉 MUST 级别的 UI 验收标准。

## 项目/版本级 Final Completion

见 `CORE/08_AI_EXECUTION_RULES.md` §8.14：当前 Scope 全部实现、必要构建/
测试全部通过、回归通过、验收标准全部通过、P0/P1 阻塞问题数 = 0、文档同步、
STATE 已更新、必要清理已完成，才允许宣布 `PROJECT / RELEASE COMPLETE`。
面向真实终端用户的项目，"验收标准全部通过"具体指 `QUALITY/03` 的 Final
Status 达到 `USER_READY`（而不是 `PARTIALLY_READY`/`NOT_READY`/
`BLOCKED`）——宣布完成前先过同文件的 Completion Guard（`CORE/08` §8.16）。
