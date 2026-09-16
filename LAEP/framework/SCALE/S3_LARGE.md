# SCALE/S3 — Large

## 适用于

企业级应用、教学工作台一类的长期维护产品、多模块大型软件。

## 在 S2 基础上新增

- **架构治理**全量启用（`GOVERNANCE/02_ARCHITECTURE_GOVERNANCE.md`）：
  模块边界、系统边界、依赖关系、API、数据流、服务划分、技术债，都要有
  明确记录。
- **数据模型治理**：`CORE/02_ENGINEERING_RULES.md` 的数据归属原则强制
  执行，每个核心实体的字段/关系/归属/生命周期都要写清楚。
- **API Governance**（`GOVERNANCE/02` 内）：任何公共 API 修改必须分析
  向后/向前兼容性、消费方、版本策略、废弃与迁移方案。
- **Change Impact Analysis** 强制启用（`GOVERNANCE/03_CHANGE_GOVERNANCE.md`）：
  改动核心模块前必须先分析受影响文件/模块/API/测试/回归范围/风险/回滚方案。
- **Regression Test** 成为标准流程的一部分，不再是可选项。
- **Dependency Management**：维护依赖关系，改一个模块前先搞清楚谁依赖它。
- **Release Management**：Version / Build / Release Candidate / Stable
  流程正式启用（`GOVERNANCE/05_RELEASE_GOVERNANCE.md`）。
- **Requirements Engineering**：REG 全量启用，包括 Assumption Register 和
  Requirement Drift Detection，Traceability Matrix 从 MAY 变成 MUST。

## 风险等级

R0-R3 是常态；个别触及权限/数据的改动可能达到 R4（见
`GOVERNANCE/03_CHANGE_GOVERNANCE.md`），但把 R4 当作系统性、常规治理对象
（而不是偶发情况）是从 S4 起才要求的标准流程，见 `SCALE/S4_SYSTEM.md`。
R3（核心架构/大范围重构/复杂迁移）默认仍可自主执行——只要已经在用户总体
授权范围内——但必须先做风险评估、建恢复点、执行、验证、失败可回滚。

## 两轮最终测试

项目准备交付前，必须执行两轮完整测试（`QUALITY/01_TESTING_STRATEGY.md`）：
第一轮跑完整套件并修复所有阻塞问题，第二轮重新完整跑一遍，确认第一轮的
修复没有引入新问题。

## Acceptance（验收）

在 S2 基础上追加 `QUALITY/03_ACCEPTANCE_PROTOCOL.md` 的 A7（回归）和
A8（交付物本身验证）；最终 `USER_READY` 判定要求 Fresh Verification
（`AGENTS/10_ACCEPTANCE_AGENT.md`），并开始跟踪 Evidence 新鲜度
（`TEMPLATES/EVIDENCE_REGISTRY_TEMPLATE.yaml`）。
