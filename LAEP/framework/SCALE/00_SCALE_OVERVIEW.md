# SCALE — 项目规模分级总览

```
S1 = Small     极小型工具/Demo/原型/单功能应用
S2 = Standard  普通 App/Web/桌面/常规商业软件
S3 = Large     企业级应用/多模块大型软件/长期维护产品
S4 = System    系统工具/系统服务/深层 OS 集成/基础设施软件
S5 = Mega      操作系统/AOSP/多仓库平台/超大型长期工程
```

核心原则：**Simple Project → Simple Process，Complex Project → Strong
Governance。** 绝不允许因为 LAEP 支持操作系统级项目，就让一个计算器 App
被迫走大型系统级流程；也绝不允许系统级项目因为图快而长期依赖临时方案。

## 自动项目识别

新项目启动（`WORKFLOWS/INIT_PROJECT.md`）时，AI 自动分析：项目类型、代码
规模、仓库数量、模块数量、技术栈、是否涉及系统权限/Kernel/硬件/云端/
数据库/多端、是否长期维护、是否高风险，据此推荐 Profile + Scale +
Governance Level。**但如果项目已有 `PROJECT_CONFIG/project.yaml`，用户的
明确配置永远优先于自动识别结果。**

## Scale Upgrade（规模自动升级）

项目从 S2 成长为 S3 时，LAEP 必须支持"Scale Upgrade"而不需要重建项目：
更新 `project.yaml` 的 `scale` 字段，补齐新等级要求但旧等级没有的
STATE/GOVERNANCE 产物（例如 S2→S3 需要补 Traceability Matrix、Dependency
Graph），已有的产物原样保留、不推倒重写，走 `WORKFLOWS/UPGRADE_PROJECT.md`。

## 跳过不适用的规则

如果某个 Profile/Scale 的规则和当前项目无关，必须跳过（Skip），不允许
"为了遵守规则而遵守规则"。哪些跳过、哪些启用，由具体的 `S<n>_*.md` 文件
明确列出，不是由 Agent 临场判断。

## 五档速览

| Scale | 文档产出 | Governance 启用 | 典型例子 |
|---|---|---|---|
| S1 | 几乎不需要 | Scope（极简）+ Validation（基础） | Todo App、小工具、Demo |
| S2 | 基础架构+测试文档 | + Change（轻量） | 普通 Android/Web/桌面 App |
| S3 | 架构治理+数据治理文档 | 全部五项启用 | 企业级教学工作台 |
| S4 | + 权限/安全/兼容矩阵 | 全部五项 + 风险升级 | 系统工具、系统服务 |
| S5 | + 多仓库/设备矩阵/Release Gate | 全部五项 + 最高强度 | 操作系统、AOSP、LeslieOS |

详见 `S1_SMALL.md` … `S5_MEGA.md`。
