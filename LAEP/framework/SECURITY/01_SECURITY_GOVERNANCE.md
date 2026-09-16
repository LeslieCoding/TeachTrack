# SECURITY/01 — Security Governance

安全不是项目最后一步，必须贯穿整个开发过程。

## 基础清单

权限最小化、Secrets 管理（不硬编码、不入库）、加密（传输/静止）、
输入校验、依赖漏洞扫描、认证（Authentication）、授权（Authorization）、
沙箱隔离、安全存储。检查覆盖：Injection、路径穿越、不安全反序列化、
数据泄露、文件权限、命令执行、SQL 注入、XSS、CSRF、不安全的自更新机制。

## 权限提升边界

不得为减少人工确认而关闭或降低 UAC 安全等级、使用任何 UAC bypass 技术
（fodhelper/eventvwr/computerdefaults/DLL 劫持/Token 窃取等）、绕过
sudo 密码策略、存储管理员/sudo/UAC 密码或凭据（不论明文还是任何编码
形式）、禁用 Defender/杀毒软件/SmartScreen 来解决普通开发权限问题、或
创建无限制的永久高权限服务/后台进程。权限相关的完整决策流程、会话复用
与批处理原则见 `AUTOMATION/04_PRIVILEGE_PREFLIGHT.md`——本节只定义
"绝对不允许"的边界，具体怎么减少重复弹窗由那份文件负责。

## 安全删除机制

涉及 `rm` / `delete` / `drop` / `format` / `reset` 一类操作，必须先评估
风险；删除重要文件前必须确认存在 Backup / Git 历史 / Recovery 路径，
对照 `CORE/08_AI_EXECUTION_RULES.md` 的 STOP-03/STOP-04（不可恢复的用户
数据删除、无备份覆盖生产数据）。

## 数据库治理

涉及数据库结构变更：Schema Version、Migration、Backup、Rollback、Data
Integrity Test 缺一不可。禁止直接修改生产 Schema 而没有迁移方案。

## 日志治理

统一 Build Log / Error Log / Test Log / Deployment Log，避免错误信息
散落各处难以排查；日志本身仍遵守 `CORE/03_CODE_RULES.md` 的脱敏要求
（不记密码/Token/API Key/原始个人数据）。

## 何时启用到什么程度

S1/S2 至少覆盖"不硬编码密钥 + 基础输入校验 + 安全删除评估"；S4/S5 全量
启用，安全测试成为标准测试层级的一部分，S5 额外要求独立 Security Audit
环节（`SCALE/S5_MEGA.md`）。
