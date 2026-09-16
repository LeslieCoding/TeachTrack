# CORE/02 — 工程原则

## 十条核心原则

1. **Specification First** — 动手写代码前先搞清楚：要什么、为什么、什么
   叫做完、什么不能变、现在代码是什么状态。新/大需求靠 `01_REQUIREMENT_RULES.md`
   的 REG 产出的 Engineering Baseline 来提供这层理解，不是靠听一句话直接改码。
2. **Read Before Write** — 改任何代码前先读相关代码、测试、配置、依赖、
   数据模型、接口、调用链。不允许在不了解现有实现的情况下重新发明一遍。
3. **Single Source of Truth** — 项目事实活在文件里，不活在聊天记录里。
4. **AI Is Not Memory** — 不能假设自己会记得这次会话；每次重新进入项目都
   必须靠 `STATE/` 里的文件恢复上下文。
5. **Autonomous but Controlled** — 默认模式是持续自主执行
   （`08_AI_EXECUTION_RULES.md`），但不可逆/高风险操作仍需谨慎，见
   `GOVERNANCE/03_CHANGE_GOVERNANCE.md` 的风险分级。
6. **Evidence-based Completion** — "代码写完了"不算完成，见
   `QUALITY/02_DEFINITION_OF_DONE.md` 和 `04_TEST_RULES.md` 的证据协议。
7. **Preserve Existing Correct Behavior** — 靠下面的 Invariant Registry
   落实成可测试的具体条目，而不是一句空话。
8. **Minimum Necessary Change** — 用最小的、能正确满足需求的改动，不顺带
   做无关的大重构。
9. **Fix Root Cause** — 真正修 Bug 要修到根因；隐藏错误、吞异常、删测试、
   关功能都不算修复。
10. **User Intent > AI Preference** — AI 自己觉得"这样更优雅"不能压过用户
    明确要求，也不能压过 `OWNER_PROFILE` 里当前生效（未被 supersede）的规则。

## Invariant Registry（不可破坏清单）

"保留原有功能"太空泛，测不了。每个项目维护一份编号清单，写清楚这次改动
绝对不能破坏什么：

```
INV-001  OEM_PERIOD 原有触发方式必须保留。
INV-002  现有存档格式必须继续可读。
INV-003  新增 DOCX 支持不能导致现有 PDF 导入失效。
INV-004  清理功能默认不能永久删除用户文件。
```

用户任何一句"别把 X 弄坏""保持 Y 能用""只优化…不要影响…"，当场记一条新的
INV，而不是只在这一轮对话里口头答应。回归测试必须逐条验证受影响的 INV
并报告 PASS/FAIL，"保留了原有行为"这种笼统描述不算验收依据，具体的 INV
编号才算。

## 数据归属原则

**数据属于系统，不属于某个 App/模块。** 如果项目存在或将存在多个
App/模块/前端/Agent 共享同一批数据，必须用统一的、带稳定 ID 的实体
（例如 `student_id`），所有模块引用同一个 ID，禁止各自维护一份互相重复、
可能不一致的私有副本。每个核心实体至少定义：名称、唯一 ID、字段
（类型/是否必填/默认值/校验规则）、关系、归属方、Source of Truth、
读写权限、生命周期、迁移策略、版本、审计要求。
