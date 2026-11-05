# LAEP — Large-scale Adaptive Engineering Platform

大规模自适应 AI 工程治理平台。版本见 `VERSION`。

## 这是什么

LAEP 不是某一个项目的开发规范，而是**所有项目共用的全局工程宪法**，以
"Core + Profile + Scale + Project Config" 的方式组合，让治理强度自动匹配
项目实际的复杂度和风险，而不是让每个项目都被迫套用同一套最大公约数流程。

> 项目不应该被迫适应开发框架。
> 开发框架应该根据项目规模、复杂度和风险，自适应地为项目提供恰到好处的工程治理。

## 任何 AI Agent 进入一个项目前，必须做什么

1. 读 `LAEP_BOOTSTRAP.md`（组合装载顺序）。
2. 读本项目 `/LAEP/PROJECT_CONFIG/project.yaml`（项目已选定的 Profile/Scale，
   如果不存在，说明这是新项目，走 `WORKFLOWS/INIT_PROJECT.md`）。
3. 装载 `CORE/`（永远全部装载，9 个文件，与项目类型无关）。
4. 装载 `PROJECT_CONFIG` 中列出的 Profile（`PROFILES/<NAME>/PROFILE.md`）。
5. 装载 `PROJECT_CONFIG` 中列出的 Scale（`SCALE/S<n>_*.md`）。
6. 读本项目 `/LAEP/STATE/`（当前状态、Backlog、已知问题、技术债、决策记录）。
7. 继续项目工作——具体怎么继续，见 `WORKFLOWS/`。

## 如何识别项目 / 如何选 Profile / 如何选 Scale

见 `WORKFLOWS/INIT_PROJECT.md` 的"自动项目识别"一节，以及 `SCALE/00_SCALE_OVERVIEW.md`。
简单说：Profile 决定"这是什么类型的项目"（Android App / Web / 操作系统…），
Scale 决定"这个项目该被治理多严格"（S1 极简 ~ S5 超大型）。两者独立选择、
可以组合，例如 `ANDROID_APP + S2`、`OPERATING_SYSTEM + MULTI_REPOSITORY + S5`。

## 如何开始工作 / 如何保存状态 / 如何测试 / 如何完成任务

- 开始工作、恢复工作：`WORKFLOWS/RESUME_PROJECT.md`
- 保存状态：`STATE/`（模板见 `TEMPLATES/`，实例存在每个项目自己的
  `/LAEP/STATE/` 下）
- 测试：`QUALITY/01_TESTING_STRATEGY.md` + `QUALITY/02_DEFINITION_OF_DONE.md`
- 验收（用户真的能不能用）：`QUALITY/03_ACCEPTANCE_PROTOCOL.md` +
  `WORKFLOWS/ACCEPT_PROJECT.md`
- 完成任务/项目：`WORKFLOWS/FINALIZE_PROJECT.md`（自动包含验收一步）

## 目录一览

```
framework/
  VERSION                     当前 LAEP 版本
  README.md                   本文件
  LAEP_BOOTSTRAP.md           AI 进入项目的第一入口
  CORE/                       所有项目都装载的通用核心规则（9 个文件）
  PROFILES/                   项目类型专属规则，按需装载
  SCALE/                      S1-S5 规模分级，决定治理强度
  GOVERNANCE/                 五大治理体系：Scope/Architecture/Change/Validation/Release
  AGENTS/                     Agent 职责边界（单 Agent 模式下由同一个 Agent 顺序扮演）
  WORKFLOWS/                  七大标准工作流（含验收 ACCEPT_PROJECT）
  QUALITY/                    测试策略与 Definition of Done
  SECURITY/                   安全治理
  TEMPLATES/                  可直接使用的结构化模板
  AUTOMATION/                 Checkpoint / 自愈循环 / Git 事务
  MIGRATION/                  版本迁移指南
  PROJECT_CONFIG/             project.yaml 的字段定义（Schema）
```

## 规则优先级

```
用户当前明确指令
  ↓
本项目 PROJECT_CONFIG（含 OWNER_PROFILE 覆盖，见 CORE/00）
  ↓
本项目当前 Scope（GOVERNANCE/01_SCOPE_GOVERNANCE.md 中定义的当前版本范围）
  ↓
所选 Profile
  ↓
所选 Scale
  ↓
LAEP CORE
  ↓
AI 默认行为
```

发生冲突：按上表由上至下取高优先级，并记入本项目 `STATE/DECISIONS.md`。

## 与 LAEP 4.1 的关系

LAEP 4.1（单文件版，含 REG/CAED/Evidence/Invariant/Traceability 等机制）的
全部内容都被保留，只是重新分类装进了上面这套目录里——没有被推翻或删除。
详见 `MIGRATION/LAEP4_TO_LAEP5.md`。
