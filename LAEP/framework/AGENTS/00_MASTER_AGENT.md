# AGENTS/00 — Master Agent

## 单 Agent 模式说明

这套 Agent 层描述的是**职责边界**，不是要求项目真的跑多个独立的 Agent
进程。绝大多数项目（尤其是 S1-S3）只有一个 Claude Code Agent 在工作，
它按需要**顺序扮演**下面各个角色——读需求时扮演 Requirement Agent，写
架构时扮演 Architect Agent，写代码时扮演 Development Agent，等等。只有
真的配置了多 Agent 协作（多个独立会话/进程同时改同一个项目）时，才需要
真正按下面的边界拆分执行者，并启用 File Lease。

## 编译通过 ≠ 功能完成

Master Agent 必须知道"代码成功编译"不等于"功能开发完成"。项目 Profile
如果定义了部署/运行时验证闭环（例如 `ANDROID_APP` 的 Zero-Touch Loop，
见 `PROFILES/ANDROID_APP/DEPLOYMENT/10_ZERO_TOUCH_LOOP.md`），且当前具备
执行该闭环的条件（如真机已 `ZERO_TOUCH_READY`），一个功能至少要经过
Build + Deployment + Runtime Verification 才能进入验收，不能只看
Build Passed 就推进到下一个任务。

## 编译通过、进程能跑 ≠ 用户能用

在上一节基础上，`QUALITY/03_ACCEPTANCE_PROTOCOL.md` 把"能不能用"拆成了
明确的 Acceptance Level 阶梯（A0-A9）：Master Agent 不得把 Build Passed
或 Headless/内部命令跑起来当作用户可以拿到手使用的证明，尤其是 GUI 类
项目——真实窗口可见、核心能力没有被静默降级、核心用户旅程走得通，才
算数。最终 `USER_READY` 判定见 `AGENTS/10_ACCEPTANCE_AGENT.md`。

## Master Agent 职责

当前任务分解、Agent 调度、状态同步、冲突解决、优先级判断、项目推进、
最终验收。其他角色不得绕过 Master Agent 擅自修改项目方向——即使是单 Agent
顺序扮演角色的情况下，"绕过 Master Agent"也意味着"没有先回到
`CORE/01_REQUIREMENT_RULES.md` 的 REG 走完 Definition of Ready 就直接
开始写代码"，同样禁止。

## Agent State Sharing

所有角色必须共享同一份事实，不允许各自维护一套互相冲突的信息：

```
Project State（STATE/CURRENT_STATE.md）
Architecture State（GOVERNANCE/02 产出）
Current Version（STATE/CURRENT_VERSION.md）
Current Scope（GOVERNANCE/01 产出）
Known Issues（STATE/KNOWN_ISSUES.md）
Decision Log（STATE/DECISIONS.md）
```

## Privilege Manager（权限上下文决策）

权限上下文（是否需要提权、在哪个会话执行、要不要新建管理员/sudo
上下文）的决策权**只属于 Master Agent**，机制细节见
`AUTOMATION/04_PRIVILEGE_PREFLIGHT.md`。任何其他角色遇到
`ACCESS_DENIED`/`ERROR_ELEVATION_REQUIRED` 都必须上报，**不得自行
`RunAs`/`sudo`/开管理员终端**。所有角色共享同一份
`STATE/PRIVILEGE_STATE.md`，不允许出现"一个角色已经知道当前是管理员，
另一个角色却又去弹一次 UAC"这种状态不一致。

## File Lease（多 Agent 并发写入协调）

真正多 Agent 并发时，写代码前必须先声明写入范围，防止两个 Agent 同时改同
一个文件：

```
Task: TASK-32
Agent: DevelopmentAgent
Write Scope: StudentService.java, StudentRepository.java
Read Scope: *
Lease: ACTIVE
```

任意两个 ACTIVE 的 Lease 不得在 Write Scope 上重叠；任何 Agent 都可以随时
读取任何内容；任务结束（成功/失败/交接）立刻释放 Lease（`Lease: RELEASED`）。
发现某个文件已被别的 Agent 持有 ACTIVE 写锁，必须等待、协调或换一个任务，
不能硬写。

## Development Agent 不得绕过 REG

无论是独立角色还是单 Agent 顺序扮演，**写代码之前必须确认对应需求已经
通过 REG 拿到 DoR PASS 的 Engineering Baseline**（`CORE/01`）；没有的话，
先把这一步做完，而不是直接开始实现。见 `03_DEVELOPMENT_AGENT.md`。

## 模型能力分级（Task Packet 颗粒度依据）

Master Agent 决定一个任务该以什么颗粒度下发，取决于实际执行的模型能力：

- **Tier A**（强推理模型）— 可以自己承担需求理解、架构设计、复杂跨模块
  调试，给一份 Engineering Baseline 通常就够了。
- **Tier B**（中等 Coding Agent）— 需要明确的 Feature Spec、明确的文件
  范围、明确的验收标准，即一份 `TEMPLATES/TASK_PACKET_TEMPLATE.md`。
- **Tier C**（弱模型）— 需要更细粒度的任务、明确的文件、明确的参数、
  明确的步骤、明确的禁止事项，永远通过 Task Packet 下发，不能直接丢一份
  完整的 Engineering Baseline 或项目全貌让它自己摸索。

**模型越弱，Task Packet 必须写得越详细。** 单 Agent 模式下这条规则同样
适用于"自己给自己拆任务"——遇到复杂度明显超出当前可靠执行能力的任务，
也应该先把它拆成更小、更明确的 Task Packet，而不是硬啃。验收类任务同样
适用这条分级，Tier B/C 执行验收时用
`AGENTS/10_ACCEPTANCE_AGENT.md` 定义的 Acceptance Packet 格式下发，不能
只给一份 Acceptance Manifest 让弱模型自己判断"验收该怎么做"。

## 可选的大项目扩展角色（S4/S5）

`DEPENDENCY_AGENT`（专职依赖治理）、`RELEASE_AGENT`（专职发布流程）、
`PERFORMANCE_AGENT`（专职性能）、`PLATFORM_AGENT`（专职平台/设备兼容）。
S1-S3 不需要单独区分这些角色，由 Master Agent 统一协调即可。
