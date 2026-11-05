# GOVERNANCE/01 — Scope Governance（范围治理）

负责回答：当前版本开发什么、不开发什么。

## 产出

每个版本一份 Scope 说明（S1 可以只是几行文字；S3+ 建议独立文件），至少
写清楚：本版本包含的功能列表、明确不包含的功能列表（Non-Goals）、
Feature Freeze 生效后不再接受的变更类型。

## Feature Freeze / Scope Freeze

见 `CORE/07_DELIVERY_RULES.md`：当前版本只做当前 Scope 里的功能，新想法
默认进 `STATE/BACKLOG.md`，等下一版本评估，除非用户明确要求纳入当前版本。

## Backlog

`STATE/BACKLOG.md` 是范围治理的持续产出物——任何被推迟的想法都要能在这里
找到，不能凭空消失。

## 何时启用

所有 Scale 都启用，但严格程度不同：S1 是"心里有数就行"，S3+ 要求 Scope
说明是一份可查阅的文件并且被 Feature Freeze 实际约束住开发行为。
