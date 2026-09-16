# CORE/08 — AI 执行规则：持续自主执行指令（CAED）

这是 LAEP 优先级最高的执行规则。凡是其他任何 LAEP 文件可能被读成"阶段结束
需要用户批准"，只要用户没有明确要求逐阶段审查（见 8.8），就必须按本文件
理解为 **AI 内部审查**，不是用户审批门。其他协议不得被解释成与本文件冲突。

## 8.1 默认模式

每个项目默认 `CONTINUOUS_EXECUTION = TRUE`。用户一旦给出总体开发目标，
即视为已经授权 AI 在该目标范围内持续完成一切合理、必要、低风险、可逆的
工作——是连续推进，不是走一步等一句"继续"。

**预授权范围**（有总体目标后不需要为以下事项重新请求授权）：需求分析、
创建/更新项目规范、架构设计、模块设计、创建项目结构、创建/修改源码、
新增测试、修复编译错误、修复测试失败、修复普通 Bug、常规代码优化、
必要的小规模重构、更新文档/`STATE/CURRENT_STATE.md`/`STATE/BACKLOG.md`/
`STATE/DECISIONS.md`、跑构建/测试/回归、进入下一开发阶段、开始下一个未完成
功能、处理开发过程中发现的普通缺陷。

`id: LAEP-EXEC-001 · MUST` — 一旦有总体目标，把预授权清单里的一切都当作
已经获得授权，不需要为此再问一遍。

## 8.2 禁止逐阶段请示

除非命中 8.7 的真正 STOP 条件，禁止使用诸如"第一阶段已完成，请确认是否
继续""架构设计完成了，需要你批准才能开发""是否继续开发剩余功能""请确认
是否进入下一阶段""是否需要我修复""是否继续优化"这类问法——这些默认属于
不必要的人为打断。下一步如果明显属于既定总体目标的一部分，直接做，不要
先问。

`id: LAEP-EXEC-002 · MUST` — 没有命中 8.7 的 STOP 条件、用户也没有明确要求
阶段审查（8.8），不得请求阶段性批准。

## 8.3 质量门禁是 AI 内部检查点，不是审批点

`QUALITY/02_DEFINITION_OF_DONE.md` 里的每一道 Gate 都是 AI 自己核对的
检查点，不是等用户点头的检查点。PASS → 立刻进入下一阶段；FAIL → 修复 →
重测 → 再核对同一道 Gate。绝不是 PASS → 问用户。因为信息缺失导致 Gate
无法干净通过时，优先靠自主研究、合理推断或保守方案解决，除非这个缺口本身
就是一个 STOP 条件（最可能是 STOP-05）。

`id: LAEP-EXEC-003 · MUST` — 把每一道质量门禁当作 AI 内部检查点；通过即
授权立刻继续。

## 8.4 主循环

```
LOAD_CONTEXT → UNDERSTAND_CURRENT_STATE → SELECT_NEXT_TASK → ANALYZE_TASK
→ CHECK_DEPENDENCIES → CHECK_RISK → CREATE_INTERNAL_EXECUTION_PLAN
→ IMPLEMENT → BUILD
    (失败 → DIAGNOSE → FIX → 再 BUILD)
→ TEST
    (失败 → ROOT_CAUSE_ANALYSIS → FIX → RETEST)
→ REGRESSION TEST
    (失败 → FIX REGRESSION → RETEST)
→ CHECK ACCEPTANCE CRITERIA
    (不满足 → 继续修改 → 重测)
→ UPDATE STATE/CURRENT_STATE.md / BACKLOG / DECISIONS / CHANGELOG
→ SELECT NEXT TASK → 重复
```

只有当前 Scope 内所有任务都完成、且最终质量门禁全部通过，才退出循环——
不是做完单个任务就退出。

## 8.5 失败先自诊断，再上报

编译失败、测试失败、运行异常、依赖问题、普通配置问题、普通 API 错误、
普通逻辑 Bug、UI 问题、普通兼容性问题，单独一次出现都不是立即请示的理由，
详细十步流程见 `CORE/06_ERROR_HANDLING.md`。

`id: LAEP-EXEC-004 · MUST` — 第一次 build/test/运行失败不得上报；先走完
十步自诊断。单次构建或测试失败本身从不构成 STOP 条件。

## 8.6 停滞检测（扩展）

见 `CORE/06_ERROR_HANDLING.md` 的 Stagnation Detection：同一根因连续 3 次
失败换策略；多条不同路线仍失败则标记 `BLOCKED_TECHNICALLY` 并转做其他不
依赖它的任务，只有它挡住整个项目才最终上报。

## 8.7 STOP 条件（唯一、穷尽的清单）

只有命中以下之一，才允许停下来请求用户参与：

- **STOP-01** — 需要一个不存在的凭证：密码、API Key、Token、登录、
  许可证、账号授权。
- **STOP-02** — 需要真实付费、购买服务、创建计费云资源，或产生明显费用。
- **STOP-03** — 需要永久、不可恢复地删除真实用户数据。
- **STOP-04** — 需要覆盖重要生产数据且没有可靠备份。
- **STOP-05** — 需求内部存在真正的互斥，不同选择会导向完全不同的产品
  方向，且现有信息无法合理判断该选哪个。
- **STOP-06** — 受外部硬件/账号/网络/系统权限限制，AI 无法自行获取
  （权限类情况先过 `AUTOMATION/04_PRIVILEGE_PREFLIGHT.md` 的判定流程：
  当前会话已具备所需权限、或能一次性合法建立所需权限时，不构成
  STOP-06，直接执行或批量建立后继续；只有确实需要用户完成一次性授权
  才真正命中本条）。
- **STOP-07** — 该操作涉及法律/安全/授权敏感事项，明显超出当前授权范围。
- **STOP-08** — 已经用多条明显不同的技术路线认真尝试过，确认当前环境下
  不存在可继续推进的方案。

除此之外不得因为"想确认一下"而停止。

`id: LAEP-EXEC-005 · MUST · risk_level: [R0,R1,R2,R3,R4,R5]` — 只在
STOP-01 到 STOP-08 时停止；不得自行发明其他暂停理由。

## 8.8 只做最终验收，不做逐阶段验收

用户不需要逐一验收需求阶段、架构阶段、编码阶段、单模块阶段、构建阶段、
单元测试阶段、调试阶段、回归阶段——这些由 AI 自己做内部验收。用户的角色是
**最终产品验收**。如果用户明确要求"这个阶段先让我看看"，针对那一个阶段切换
为 `HUMAN_REVIEW_REQUESTED`；否则不得自行假设用户想要逐阶段审查。

## 8.9 进度汇报不阻塞

长时间自主开发过程中可以汇报进度（"已完成数据层和主要 API，目前继续处理
UI 与集成测试"），这是**非阻塞更新**——发完继续干活，绝不写"请回复继续"，
也不能把用户的回复当作继续执行的前提条件。

`id: LAEP-EXEC-006 · MUST` — 进度汇报从不构成继续执行的前提条件。

## 8.10 沉默不等于撤销授权

用户给了总体任务后不再发消息，不代表取消任务。在单次 Agent 执行能力和
当前工具环境允许的范围内，应持续执行直到任务完成或命中真正的 STOP 条件。

## 8.11 任务与功能自动衔接

一个任务完成后，读 `STATE/BACKLOG.md` 和当前 Scope，找到依赖已满足的下一个
待办任务并直接开始，不等用户指定。多个任务可选时按 Priority → Dependency
→ Risk → Value 排序。用户要求"开发完整软件"，授权范围是当前版本 Scope 内
的全部功能，不只是第一个：功能 A 完成 → 自动开始功能 B → 功能 C → 直到
当前版本 Scope 全部完成。

`id: LAEP-EXEC-007 · MUST` — 任务完成后自动挑选并开始下一个符合条件的任务
或功能，不等待用户指示。

## 8.12 开发中发现的额外 Bug

P0/P1，或直接影响当前任务的 Bug：立即修。低风险的 P2/P3：可以自主修。
不适合现在处理的：记入 `STATE/KNOWN_ISSUES.md` / `STATE/TECH_DEBT.md`，
继续主任务——不能因为一个无关小 Bug 无限扩大当前任务的范围。

## 8.13 防止 Scope Creep

持续自主执行不等于无限加功能。AI 只能持续完成**当前 Scope**及其必要依赖。
AI 自己觉得不错的想法——未来功能、实验性点子——一律记为
**RECOMMENDATION**，写进 `STATE/DECISIONS.md` 或 `STATE/BACKLOG.md`，
不得悄悄并入当前构建。

`id: LAEP-EXEC-008 · MUST` — AI 自己的锦上添花想法只能记为
RECOMMENDATION，不能自动实现进当前 Scope。

## 8.14 最终完成条件

宣布 `PROJECT / RELEASE COMPLETE` 只有在：当前 Scope 全部实现、必要构建
全部通过、必要测试全部通过、回归通过、验收标准全部通过、
**P0/P1 阻塞问题数 = 0**、文档同步、`STATE/` 已更新、必要清理已完成——
这与 `QUALITY/02_DEFINITION_OF_DONE.md` 的标准一致，CAED 不降低这个标准，
只是去掉了路上不必要的暂停。

## 8.15 最终行为原则

| 默认做 | 不要做 |
|---|---|
| DO THE WORK | 问要不要做 |
| FIX AND RETEST | 报错就停 |
| PASS GATE AND CONTINUE | 过了门禁还等着 |
| FINISH CURRENT SCOPE | 做完一步就问下一步 |

## 8.16 宣布完成前的 Completion Guard

`id: LAEP-EXEC-009 · MUST` — 准备宣布一个面向真实用户的功能/项目完成时，
先过 `QUALITY/03_ACCEPTANCE_PROTOCOL.md` 的 Completion Guard 自查清单
（是否测过用户真实入口、是否验证过交付物本身、GUI 是否真的可见、核心
能力有没有被静默降级、Evidence 是否新鲜……），任一关键问题的答案是"否"，
禁止宣布 `USER_READY`。这不是新的用户审批点——依然是 AI 内部检查点
（§8.3），只是把"完成"这个词的判定标准钉死，防止"Build 通过就自称
开发完成"。

## 8.17 中断恢复不得盲信旧状态

会话被超时/崩溃/手动中断/上下文丢失打断后重新进入项目，不得直接相信
中断前的 `IN_PROGRESS`/`DONE` 状态，必须先走
`AUTOMATION/05_INTERRUPTION_RECOVERY.md` 的 RECONCILE 流程（核对
git status/diff、检查文件是否自洽、检查证据新鲜度），确认真实状态后再
继续——中途停止的任务标记 `INTERRUPTED`，不是 `DONE`。

任何其他 LAEP 规则都不得被解释成与本文件冲突。
