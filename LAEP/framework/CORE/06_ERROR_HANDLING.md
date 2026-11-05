# CORE/06 — 错误处理与自愈开发

## Self-Healing Development

遇到 Compile Error / Test Error / Runtime Error / Dependency Error /
Configuration Error，默认执行，不是看到第一次错误就停下来问用户：

```
Diagnose → Root Cause → Fix → Retest
```

十步自诊断（展开见 `08_AI_EXECUTION_RULES.md`）：收集错误信息 → 分析日志 →
定位根因 → 检查最近改动 → 检查依赖 → 检查配置 → 最小修复 → 重新构建 →
重新测试 → 跑相关回归。只要问题能通过代码/配置/依赖/脚本/测试/局部架构
调整解决，就应该继续自主处理，不必第一次失败就请示。

## 权限相关错误不等于"需要管理员"

遇到 `Access Denied` / `Permission Denied` / `ERROR_ELEVATION_REQUIRED`，
先分析真实原因（文件占用、ACL、只读属性、杀毒软件拦截、进程所有权、
路径错误），再判断是否真的需要提权，不得看到"拒绝访问"就直接开管理员
终端。完整的权限决策流程见 `AUTOMATION/04_PRIVILEGE_PREFLIGHT.md`——
需要真正提权时，也不得自行 `RunAs`/`sudo`，一律上报 Master Agent 统一
决策（`AGENTS/00_MASTER_AGENT.md`）。

## Root Cause First

禁止"遇到错误 → 不断随机改代码 → 看运气"这种模式；必须先定位根因。也禁止
为了让错误消失而破坏架构（比如为了绕开一个类型错误就把类型系统关掉）。

## 禁止为了通过检查而造假

测试失败时，禁止用以下方式伪造成功：删除功能、注释掉核心代码、禁用测试、
跳过失败检查。这些是"让红灯变绿灯"，不是"修复问题"。

## 会话中断不是"失败"，但也不能默认"已完成"

上面几节处理的是"代码跑起来但有问题"；如果是上一次执行本身被打断（超时/
崩溃/手动中断/上下文丢失），走 `AUTOMATION/05_INTERRUPTION_RECOVERY.md`
的 RECONCILE 流程先核实真实状态，不要对着中断前的旧状态直接继续或直接
判定完成。

## Stagnation Detection（停滞检测）

同一个根因连续 **3 次**修复尝试仍未解决：停止当前策略 → 检查所有近期 diff →
回退到最后一个已知良好状态（`AUTOMATION/01_CHECKPOINT_PROTOCOL.md`） →
重新做根因分析，明确排除前 3 次尝试所基于的假设 → 换一条明显不同的路线
再试。多条明显不同的路线都失败：把这个任务标记为 `BLOCKED_TECHNICALLY`，
**转去做其他不依赖它的任务**，不要在一个任务上无限打转拖垮整个会话；只有
这个阻塞项确实挡住了整个项目继续推进时，才需要最终向用户说明。
