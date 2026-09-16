# GOVERNANCE/03 — Change Governance（变更治理）

## Change Impact Analysis

S3 及以上默认启用。修改核心模块前必须分析：

```
Change → Affected Files → Affected Modules → Affected APIs
       → Affected Tests → Potential Regression → Risk Level → Rollback Strategy
```

修改已 BASELINED 需求同样要走这一套（`CORE/01_REQUIREMENT_RULES.md`）。
模板见 `TEMPLATES/CHANGE_IMPACT_TEMPLATE.md`。

## 风险分级

```
R0  文档
R1  UI
R2  业务逻辑
R3  核心 Framework / 大范围重构 / 复杂迁移
R4  权限 / 数据 / 系统服务
R5  Kernel / Boot / Security / Recovery   （仅 S5，见 SCALE/S5_MEGA.md）
```

- R0-R2：自动执行，无需特别流程。
- R3：只要在用户总体授权范围内，默认仍可自主执行——先做风险评估、建恢复点/
  备份、执行、验证，失败则回滚；明显超出授权范围才需要用户参与
  （与 `CORE/08_AI_EXECUTION_RULES.md` 的 STOP 条件体系一致，不是另一套
  独立标准）。
- R4/R5：自动提升测试要求、备份要求、Review 要求；R5 默认要求用户参与
  （见 `SCALE/S5_MEGA.md`）。

## Rollback Strategy

大范围重构、Kernel 修改、数据迁移、权限修改、API Breaking Change，动手前
必须设计好回滚方案，不是出问题才现想。数据库相关见 `SECURITY/`（Schema
Version/Migration/Backup 一节）。

## Commit Governance

每个 commit 单一目的、可解释、可回滚，避免"巨大、混杂、无法分析的
Commit"。展开见 `AUTOMATION/03_GIT_TRANSACTION_PROTOCOL.md`。

## Branch Strategy

按 Scale 自动选择：S1 可以直接在单一分支上开发；S3+ 可以采用
`main / develop / feature/* / release/* / hotfix/*`，具体策略根据项目
实际情况决定，不强制统一模板。

## Build Reproducibility（S3+）

要求"相同源码 + 相同环境 = 可重复 Build"，记录 Toolchain / SDK / Compiler /
Dependencies / Build Config，见 `SECURITY/`（环境相关内容）与各 Profile
里的构建说明。
