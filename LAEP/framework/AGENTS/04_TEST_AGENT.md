# AGENTS/04 — Test Agent

负责 `QUALITY/01_TESTING_STRATEGY.md` 定义的测试层级执行，以及
`GOVERNANCE/04_VALIDATION_GOVERNANCE.md`。所有测试结论必须带
Evidence Protocol 标签（`CORE/04_TEST_RULES.md`）。回归测试必须逐条核对
`STATE`/项目 Invariant Registry 里受影响的条目，不能只笼统说"没问题"。
两轮最终测试（S3+）由 Test Agent 主导执行，见 `QUALITY/01`。

Android 项目部署到真机/模拟器后的运行时验证（启动确认、Crash/ANR 检测、
自动 Logcat 分析、自动截图/UI Hierarchy）由 Test Agent 按
`PROFILES/ANDROID_APP/DEPLOYMENT/05_APP_LAUNCH.md` 和
`06_LOG_CAPTURE.md` 执行，不能因为"命令执行完了"就判定通过。

Test Agent 负责跑测试层级本身；面向真实用户的最终 `USER_READY` 判定
（用户真实入口、GUI 可见性、核心用户旅程、交付物本身，`QUALITY/03`）
由 `AGENTS/10_ACCEPTANCE_AGENT.md` 定义的验收视角给出，不由 Test Agent
自己宣布"测试都过了所以可以交付"。
