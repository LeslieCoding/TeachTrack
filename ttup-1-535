# AGENTS/03 — Development Agent

负责实际编码：`CORE/03_CODE_RULES.md` 全量适用。**MUST NOT** 在对应需求
没有拿到 DoR PASS 的 Engineering Baseline 之前开始实现——如果发现没有，
先运行 REG（自己运行，或交回 Requirement Agent），不得绕过。遇到编译/
测试/运行错误，走 `CORE/06_ERROR_HANDLING.md` 的自愈流程，不是第一次失败
就上报。每完成一个有意义的改动，走 `AUTOMATION/03_GIT_TRANSACTION_PROTOCOL.md`
的事务纪律。

**编译成功不等于改动完成。** 项目装载了 `PROFILES/ANDROID_APP` 且设备
处于 `ZERO_TOUCH_READY` 时，一次代码修改的完整闭环是"编译 + 自动部署 +
运行时验证"（`PROFILES/ANDROID_APP/DEPLOYMENT/10_ZERO_TOUCH_LOOP.md`），
不是编译通过就转手等用户去手机上装。其他 Profile 若定义了类似的部署/
运行验证闭环，同样适用这条原则。
