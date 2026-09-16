# CORE/04 — 测试与证据规则

具体该跑哪些测试层级由 Scale 决定，见 `QUALITY/01_TESTING_STRATEGY.md`；
这里是所有规模都适用的基础纪律。

## Evidence Protocol（证据协议）

测试报告、验收报告、完成报告里的每一条事实性结论都必须标注证据等级：

- **OBSERVED** — 亲自跑过、亲眼看到结果。
- **MEASURED** — 真的采集到了数字（计时器/Profiler/日志）。
- **INFERRED** — 从相关证据推断，不是直接目睹。
- **ASSUMED** — 相信是真的，但这次会话没有核实。
- **NOT_VERIFIED** — 明确说明没有检查过，而不是干脆不提。

```
Android 模拟器：       OBSERVED — 跑过，12 步全部通过
ColorOS 14 真机：      NOT_VERIFIED
静态兼容性扫描：       MEASURED — lint 输出已附
Ubuntu 上的预期表现：  INFERRED，来自 Linux Mint 通过 + 同内核族推断
```

不能写"ColorOS 14 真机测试通过"，除非真的在那台设备上跑过。任何
MUST 级别的验收标准，只能被 OBSERVED 或 MEASURED 关闭；
INFERRED/ASSUMED/NOT_VERIFIED 都关不掉一条 MUST——如果有人这么关了，
这本身就是一个要在完成报告里指出的违规，不能悄悄放过。

## Baseline Protocol（优化类任务）

任何目标是"优化/更快/更流畅/降延迟/省内存/更稳定"的任务，动手改之前必须
用具体指标记一份 Before 基线，改完用同样的指标、同样的环境记一份 After：

```
Before：Input latency 42ms / Frame time 19ms / Memory 180MB
After： Input latency 24ms / Frame time 12ms / Memory 172MB
```

没有 Before/After 配对，"性能有所提升"只是一句 ASSUMED 的话，不能算完成。

## Definition of Done 的证据要求

见 `QUALITY/02_DEFINITION_OF_DONE.md`——一句话总结：Implemented + Build
Passed + Required Tests Passed（OBSERVED/MEASURED）+ No Critical
Regression（含 Invariant 逐条核实）+ Documentation Updated，才允许标记
DONE。面向真实终端用户交付时，这条底线之上还有一层更严格的要求，见
`QUALITY/03_ACCEPTANCE_PROTOCOL.md`。

## Evidence 新鲜度与自我陈述

Evidence 必须绑定产生它时的代码状态（commit/diff 指纹）；相关文件在某次
验证 PASS 之后又被修改，该 Evidence 立即失效（`STALE`），不能拿旧结果
证明新代码——细节见 `QUALITY/03` 的 Evidence Expiration 一节。同时：
**AI 自己说"功能已完成""运行正常""已经修复"不构成 Evidence**——Evidence
只能来自工具输出、Runtime 观察、测试结果、日志、Screenshot、真实产物。
