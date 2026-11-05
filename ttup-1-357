# CORE/03 — 代码规则

## 开发顺序

理解需求 → 定位相关模块 → 阅读相关代码和测试 → 设计最小改动 → 实现 →
构建 → 测试 → 修复 → 回归。新增代码前先搜索项目里是否已有类似实现。

## 代码质量关注点

Single Responsibility · DRY · KISS · 清晰命名 · 错误处理 · 资源管理 ·
线程安全 · 空值安全 · 边界校验 · 状态管理 · 配置分离 · 可测试性 · 可观测性。

## 错误分类

必须区分：User Error / Validation Error / Business Error / Network Error /
IO Error / Database Error / Permission Error / System Error / Unexpected
Error。禁止 catch 后直接吞掉；禁止返回错误却不记录上下文。

## 日志

只分 DEBUG / INFO / WARN / ERROR；绝不记录密码、Token、API Key、原始个人
数据；日志必须带上下文（request_id / task_id / module / operation）。

## 配置

不得硬编码路径、密钥、环境地址、数据库密码、生产配置；区分
dev/test/prod，个人规模项目可以简化成默认配置/用户配置/运行时配置三层。

## 依赖引入

新增依赖前必须评估：是否真的需要、有没有替代方案、License、是否有人在
维护、安全状况（已知 CVE）、体积、兼容性、长期升级风险。

## Git 事务原则

每一次有意义的改动都是一次事务，不是攒一堆改动最后才测一次：

```
Known Good State → 小改动 → build → test → PASS → commit
                                        ↓ FAIL
                                    rollback，不要在失败状态上继续叠加
```

每个 commit 尽量对应一个逻辑改动，message 里带 Task ID / Requirement ID /
跑过的测试 / 风险等级，方便日后 `git bisect`。展开见
`AUTOMATION/03_GIT_TRANSACTION_PROTOCOL.md`。
