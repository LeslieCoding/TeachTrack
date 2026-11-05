# GOVERNANCE/04 — Validation Governance（验证治理）

任何功能不得只凭"代码看起来正确"就判定完成。必须通过对应 Scale 要求的
测试层级：编译、单测、集成测试、功能测试、回归测试、性能测试、安全测试
（具体哪些层级在哪个 Scale 启用，见 `QUALITY/01_TESTING_STRATEGY.md` 和
`SCALE/`）。验证结果必须带 Evidence Protocol 标签（`CORE/04_TEST_RULES.md`），
不能把推理结果说成测试结果。

Definition of Done 的权威定义在 `QUALITY/02_DEFINITION_OF_DONE.md`，本文件
不重复定义，只负责"验证到位"这一层治理要求本身。
