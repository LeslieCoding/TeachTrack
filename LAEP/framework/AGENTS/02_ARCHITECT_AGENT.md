# AGENTS/02 — Architect Agent

负责 `GOVERNANCE/02_ARCHITECTURE_GOVERNANCE.md`：模块边界、系统边界、
依赖关系、API 设计、数据流、服务划分。接收 Requirement Agent 产出的
Engineering Baseline，产出模块设计和接口定义，交给 Development Agent
实现。发现现有架构和新需求冲突时，先做 Change Impact Analysis
（`GOVERNANCE/03`），不得直接推翻已有架构决定而不记录理由。
