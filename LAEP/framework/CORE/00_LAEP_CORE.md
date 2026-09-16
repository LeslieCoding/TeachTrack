# CORE/00 — LAEP Core: 元规则

## CORE 边界

CORE 只允许包含**所有项目都真正需要**的基础规范。任何 Android / Windows /
Linux / AOSP / Kernel / Web / 桌面 / 具体技术栈专属的规则，一律不得出现在
CORE 里，必须放进对应的 `PROFILES/<NAME>/PROFILE.md`。判断标准很简单：
"如果把这条规则拿掉，会不会有任何一个 Profile 因此无法正常工作？"——会，
才允许留在 CORE；不会，就该搬去 Profile。

## Core + Profile + Scale + Project Config

一个项目实际生效的规则集合 = CORE（全部）+ 该项目选定的 Profile（1 个或
多个，如 `OPERATING_SYSTEM + MULTI_REPOSITORY`）+ 该项目选定的 Scale（S1-S5
恰好 1 个）+ 该项目 `PROJECT_CONFIG/project.yaml` 里的具体覆盖值。装载顺序
与优先级见 `framework/README.md`。

## 规则等级：MUST / SHOULD / MAY

- **MUST** — 必须满足，否则任务不能标记 DONE。
- **SHOULD** — 默认应满足；不满足必须在 `STATE/DECISIONS.md` 记录理由。
- **MAY** — 可选；具体是否采用由 Profile/Scale/Project Config 决定
  （例如"个人小工具 MAY 不配置完整 CI/CD"）。

## 规则 ID

重要规则应带唯一 ID，格式 `LAEP-<DOMAIN>-<NNN>`（DOMAIN 例如 CORE、REQ、
CODE、TEST、SEC、GOV、SCALE），带 `version` / `status`(ACTIVE|DEPRECATED) /
`supersedes`。规则永远不原地修改——要变更就发布新版本、把旧版本标记
DEPRECATED，并在新版本里 `supersedes` 旧版本，防止两个 Agent 因为读到不同
时间点的快照而互相打架。这条规则本身同样适用于 `OWNER_PROFILE`（见下）。

## 十条核心原则（详细展开于 `02_ENGINEERING_RULES.md`）

Specification First · Read Before Write · Single Source of Truth ·
AI Is Not Memory · Autonomous but Controlled（`08_AI_EXECUTION_RULES.md`）·
Evidence-based Completion · Preserve Existing Correct Behavior ·
Minimum Necessary Change · Fix Root Cause · User Intent > AI Preference。

## OWNER_PROFILE（用户的长期个人偏好——不要和"Profile"系统混淆）

用户本人跨项目的长期工作偏好（例如"默认用中文回复""交付要完整不要碎片化"
"改完必须自己重启验证"）存放在 `<LAEP_HOME>/OWNER_PROFILE.yaml`，是全局唯一
的一份，版本化、可被更新的规则覆盖（同样用 supersession 机制）。每个项目在
`INIT_PROJECT` / `RESUME_PROJECT` 时把当前快照同步一份到本项目
`/LAEP/OWNER_PROFILE/PROFILE.yaml`，保证项目脱离 `<LAEP_HOME>` 也能独立被
另一个 Agent 接手。**这与 `PROFILES/`（项目类型：Android/Web/操作系统…）是
两个完全不同的概念，注意不要用错目录。** 优先级：`OWNER_PROFILE` 高于项目
自身的 Profile/Scale/CORE，低于用户当前这一轮的明确指令。

## 冲突检测

发现 Prompt 冲突 / Config 冲突 / 需求冲突 / 架构冲突时，不得静默选择其中
一个——必须按 `framework/README.md` 的优先级表明确判定，并记入
`STATE/DECISIONS.md`，让冲突和裁决都可追溯。

## LAEP 自身的版本治理

语义化版本：MAJOR = 破坏性协议变更，MINOR = 新增协议不破坏既有结构，
PATCH = 文字/细则修正。每次重大升级必须做一次 Rule Review：合并重复规则、
清理过时规则、检查 Profile 是否污染了 CORE——防止"新规则不断叠加、旧规则
从不清理"的 Prompt 腐化（对应 `WORKFLOWS/AUDIT_PROJECT.md` 的自检项之一）。
