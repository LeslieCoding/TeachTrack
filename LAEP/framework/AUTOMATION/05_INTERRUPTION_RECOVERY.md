# AUTOMATION/05 — Interruption Recovery

*横切能力，新增于 LAEP 5.3.0 DeliveryProof。解决的问题：Agent 执行到一半
被超时/崩溃/用户手动中断/上下文丢失/会话重启打断，恢复时不能想当然地
相信中断前留下的 `IN_PROGRESS`/`DONE` 状态——必须先核实真实状态，再决定
下一步。*

## Task 状态新增 INTERRUPTED

任务执行中途停止，不得直接标记 `DONE`，必须标记 `INTERRUPTED`（写入
`STATE/CURRENT_STATE.md` 的"进行中的任务"一项），恢复后走下面的
RECONCILE，再转回 `IN_PROGRESS` 或重新执行——不允许"没做完但看起来做了
一半就默认算完成"。

## RECONCILE 流程

任何一次新的进入（新会话、`RESUME_PROJECT.md`、或明确怀疑上次执行被
打断）都必须先做一次核实，而不是直接信任 `STATE/CURRENT_STATE.md` 里
写的内容：

```
读 STATE/CURRENT_STATE.md
→ git status（有没有未提交的改动、有没有半成品文件）
→ git diff（这次中断具体改了什么，改完整了没有）
→ 检查上次任务涉及的文件是否处于自洽状态（不是语法都不完整）
→ 检查最近一次测试/构建证据的时间戳和新鲜度（CORE/04 的 Evidence
   Expiration，`QUALITY/03`）
→ 确定任务真实状态：
   - 确认已完成且自洽 → 更新为 DONE，继续下一任务
   - 确认未完成 → 标记 IN_PROGRESS，从可靠的断点继续，而不是从零重做
   - 状态不可判断/文件处于损坏的中间态 → 标记 INTERRUPTED，视情况回退到
     AUTOMATION/01_CHECKPOINT_PROTOCOL.md 记录的最近 Known Good State
```

`id: LAEP-REC-001 · MUST` — 恢复执行前不得直接信任旧的 `IN_PROGRESS`/
`DONE` 状态；必须先跑完 RECONCILE 再继续。

## 与 Self-Healing Loop 的区别

`AUTOMATION/02_SELF_HEALING_LOOP.md` 处理的是"代码跑起来但有 Bug"；本文件
处理的是"上一次执行本身没跑完"。两者可能连续触发（RECONCILE 发现代码处于
半成品状态 → 走 Self-Healing 修到自洽 → 再继续原任务），但触发条件不同，
不要混淆。

## 与 Checkpoint 的配合

S4+ 项目的 `AUTOMATION/01_CHECKPOINT_PROTOCOL.md` 提供"退回去的稳定点"；
本文件提供"先搞清楚现在到底是什么状态"。中断发生在两个 Checkpoint 之间、
且无法可靠判断当前状态时，优先退回最近一个 Checkpoint 重新推进，而不是
在不确定的中间状态上继续叠加改动。
