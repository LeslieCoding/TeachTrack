# QUALITY/03 — Acceptance Protocol（DeliveryProof）

`QUALITY/02_DEFINITION_OF_DONE.md` 定义的是"这段实现对不对"；本文件定义
"用户真的能不能拿到手用"。两者不是替代关系——DoD 是底线，本文件是面向
真实终端用户交付时，DoD 之上必须追加的验证层。动机：Build 通过、进程能
跑、Headless 初始化成功，都不能证明用户双击图标之后真的能打开、看见、
点、用。

## 最高原则

```
BUILD PASSED          ≠ APPLICATION WORKS
PROCESS RUNNING        ≠ GUI WORKS
GUI VISIBLE             ≠ FEATURE WORKS
INTERNAL ENTRYPOINT WORKS ≠ USER ENTRYPOINT WORKS
HEADLESS STARTUP PASSED   ≠ VISUAL ACCEPTANCE PASSED
SOURCE CODE COMPLETE       ≠ PRODUCT DELIVERED
```

`id: LAEP-ACC-001 · MUST` — 没有对应等级的 Evidence（`CORE/04` 的
OBSERVED/MEASURED），任何 MUST 级验收结论不得标记 PASS；没跑过就写
`NOT_RUN`/`NOT_VERIFIED`，不得靠推理/印象直接填 PASS。

## Acceptance Status（只允许这七个词）

`PASS · FAIL · PARTIAL · NOT_RUN · NOT_VERIFIED · BLOCKED · NOT_APPLICABLE`

禁止 "Probably Works" / "Should Work" / "Looks Correct" / "理论上" /
"预计" / "代码上已经没问题" 这类模糊状态出现在任何验收结论里。

## 禁止的验收替代

以下每一行左边都不能拿来当右边的证据：

| 已验证的 | 不能替代证明 |
|---|---|
| Internal 命令能跑（`python -m pkg.app` 之类） | User Launcher 能跑（`run.bat`/安装后的快捷方式） |
| Headless/Offscreen 启动成功 | 真实窗口可见 |
| Unit Test 全绿 | Core User Journey 走得通 |
| 进程存活（Process Alive） | 窗口有响应（Window Responsive） |
| 窗口可见（Window Visible） | 核心功能真的能用（Core Feature Works） |
| 依赖包已安装（`pip list`/`npm list` 命中） | 代码要用的 API 真的存在 |

## Acceptance Level 阶梯（A0–A9）

| 级 | 验证什么 | 典型手段 | 能证明 | 不能证明 |
|---|---|---|---|---|
| A0 STATIC | 语法/Lint/类型/Import 结构 | 静态分析 | 代码静态状态可接受 | 能不能运行 |
| A1 BUILD | 编译/打包/产物生成 | Build 命令 | 能产出构建结果 | 能不能启动 |
| A2 INTERNAL_RUNTIME | 开发者内部入口跑起来 | `python -m pkg.app`/`gradle run` 等 | Runtime 能初始化 | 用户入口是否正常 |
| A3 USER_ENTRYPOINT | 用户真实使用的入口 | `run.bat`/安装后快捷方式/已装 APK/部署 URL | Launcher/安装链路本身正常 | UI 是否可见 |
| A4 VISIBLE_UI | 窗口真正可见（GUI 项目） | 真实启动 + Screenshot（有条件时） | 主界面渲染出来了 | 交互是否正常 |
| A5 INTERACTION | 关键 UI 交互 | 点击/切页/输入/加载 | 基本交互可用 | 核心业务流程是否走得通 |
| A6 CORE_USER_JOURNEY | 核心业务流程 | 见下方 Core User Journey | 核心场景可用 | 有没有引入回归 |
| A7 REGRESSION | 改动没破坏已有行为 | 逐条核对 Invariant Registry | 没有已知回归 | 交付物本身是否存在 |
| A8 DELIVERY | 最终交付物本身 | 安装/运行 APK/EXE/Installer/AppImage | 交付物可用 | — |
| A9 USER_READY | 以上适用项全部通过 | — | 可以交付 | — |

不适用的层级（例如 CLI 项目没有 A4/A5）标记 `NOT_APPLICABLE`，不是不提。
哪些层级在哪个 Scale 是 MUST，见本文件末尾"按 Scale 的要求"。

## User Entrypoint First

`id: LAEP-ACC-002 · MUST` — 最终验收必须验证 A3（用户真实入口），不能只
验证 A2（开发者内部命令）就宣称通过。开发调试用的
`PYTHONPATH=src python -m pkg.app` 或 `gradlew run` 方便开发，但不是用户
会执行的东西；最终用户可能是双击 `run.bat`/`TVBox.exe`，或者安装后从
开始菜单/Launcher 启动——验收必须冲着后者去。

**Launcher Acceptance**：任何 `.bat`/`.cmd`/`.ps1`/`.sh`/Desktop Entry/
EXE Wrapper/快捷方式必须独立验证：Working Directory、可执行文件路径、
解释器路径、环境变量（含 `PYTHONPATH`/`JAVA_HOME`）、参数、引号/带空格
路径、退出行为、错误输出。Launcher 失败 = A3 FAIL，不管 A2 是否 PASS。

**Environment Parity**：验证要用项目真实运行时的解释器/环境（例如项目用
`venv`，验证也必须用该 `venv` 的解释器），不能"系统 Python 跑通了"就
宣称项目通过——记录 Interpreter/Runtime Version/Dependency Version/
Working Directory/Platform，避免"开发环境 PASS，用户环境 FAIL"。

## Framework/API Compatibility Gate

`id: LAEP-ACC-003 · MUST` — 依赖包已安装（`pip show`/`npm list` 命中）
不等于代码要用的 API 真的存在于当前安装的版本里。关键 Import 必须真实
执行验证（`import module` / `from module import RequiredClass`），不能
只检查包名存在。典型故障：代码写的是 Qt5 API，实际安装的是 Qt6，包检查
能通过，真实 import 会失败。

## Silent Degradation / Critical Capability Guard

`id: LAEP-ACC-004 · MUST` — 很多代码用 `try: import capability / except:
disable capability` 这种模式，结果 App 能启动但核心功能已经被静默禁用。
`ACCEPTANCE_MANIFEST.yaml`（见 `TEMPLATES/ACCEPTANCE_MANIFEST_TEMPLATE.yaml`）
里标记 `required: true` 的能力，一旦在运行时被自动禁用，即使主窗口正常
启动，Final Status 也不得 `USER_READY`——"没有 Crash"不等于"核心功能还在"。

日志 Warning 分三级：`INFO_WARNING`（不影响用户功能）、
`DEGRADED_WARNING`（部分能力下降，需要记录但不一定阻断）、
`CRITICAL_WARNING`（核心功能不可用，直接阻断 `USER_READY`）。

## GUI Acceptance / Black Screen Gate

*仅对有真实窗口的项目适用——通常是装载了 `PROFILES/DESKTOP_APP` 或
`PROFILES/ANDROID_APP` 的项目，具体窗口系统细节见对应 Profile。*

GUI 验收至少确认：进程存活、主窗口已创建、窗口可见、非零尺寸、主要
Widget/内容已渲染、没有立即崩溃或致命启动对话框、没有持续性的全黑/
全空白主区域。环境支持截图时，启动后必须实际获取窗口 Screenshot 并确认
不是纯黑/纯空白、包含预期 UI 元素；环境不支持视觉验证时，明确标记
`UI_VISUAL = NOT_VERIFIED`，不得写 `PASS`。

**Headless/Offscreen 不能自动升级为 Visible UI PASS**：类似
`QT_QPA_PLATFORM=offscreen` 这类测试只能证明 A2（程序能初始化、主窗口能
构造、没有立即异常），永远不能自动等同于 A4 `VISIBLE_UI = PASS`——环境
允许真实 GUI 时必须优先真实启动，Headless 只是环境不支持时的 Fallback，
且必须在报告里如实标注这一层只做到了 A2。

**Startup Liveness**：App 必须至少保持运行到配置的最短存活时间
（`ACCEPTANCE_MANIFEST.yaml` 的 `startup.minimum_alive_seconds`），期间
监测 Crash/Fatal Error/Unhandled Exception/意外退出。

**Window Responsive Gate**：进程存活不等于窗口可用，至少要排除 UI 线程
死锁、初始化死循环、窗口无响应（Not Responding）。

## Core User Journey

每个项目至少定义 1～5 条核心用户旅程（例如"启动 App → 首页出现 → 打开
播放页 → 播放器初始化 → 执行核心播放流程"）。最终验收不能只测代码单元，
必须至少走通这些旅程里标记为 Critical 的部分（A6）。

## Clean Verification

条件允许时，最终 User Entry 测试应从新 Shell/新进程执行，不依赖开发过程
残留的当前目录、临时 PATH、手工 export、Debug 环境变量、遗留进程——防止
"开发环境能跑，用户环境跑不起来"这种假阳性。S4/S5（以及任何项目发布前）
应额外做一次 Clean Restart：关闭 App、清理测试进程、重新启动，确认不是
"第一次靠开发环境残留状态才成功"。

## Delivery Artifact Verification

存在最终交付物（APK/EXE/Installer/AppImage/部署包）时，必须验证交付物
本身（安装 APK 后启动、运行最终 EXE、执行 Installer 后启动已安装程序），
不能只验证源码/开发命令能跑。用户预期得到一个可运行的产物但项目最终只有
源码时，必须明确标记 `DELIVERY_INCOMPLETE`，不能含糊带过。记录 Artifact
Path/Type/Version/Size/Build Time。

## Acceptance Profile（按项目类型；不要与 `PROFILES/` 系统混淆）

*这是"验收时重点测什么"的分类，与 `PROFILES/<NAME>/PROFILE.md`（项目
类型专属工程规则）是两个独立维度，通常按 `project.yaml` 的 `profiles[]`
自动映射，冲突时以项目实际情况为准：*

| Acceptance Profile | 验收重点 |
|---|---|
| CLI_APP | 真实命令、参数、`--help`、非法输入、Exit Code、输出内容 |
| DESKTOP_GUI | Launcher（A3）+ 窗口可见（A4）+ 交互（A5）+ 核心旅程（A6），见 `PROFILES/DESKTOP_APP/PROFILE.md` |
| ANDROID_APP | 继承 Zero-Touch（`PROFILES/ANDROID_APP/DEPLOYMENT/00`）：Build→Install→Launch→Runtime 本身已覆盖 A2-A4，本文件补充 A5/A6/Critical Capability |
| WEB_APP | 区分 Dev Server 与 Production Build，最终验收对象是 Production Build：可访问、主 UI、核心路由、关键请求 |
| BACKEND_SERVICE | Service Startup、Health Check、关键 API、持久化是否生效 |
| LIBRARY | 无独立用户入口，A3-A6 记 `NOT_APPLICABLE`，验收落在真实调用方代码能否通过（集成测试） |
| OPERATING_SYSTEM / EMBEDDED_SYSTEM | 在本阶梯之上追加 Boot/硬件/Recovery/兼容性验证，见 `PROFILES/OPERATING_SYSTEM/PROFILE.md` |

## Capability Matrix / Graceful Degradation 不等于成功

项目记录每个关键能力：`CAPABILITY / IMPLEMENTED / RUNTIME_AVAILABLE /
VERIFIED / CRITICAL`。任何一行 `CRITICAL: true` 且 `RUNTIME_AVAILABLE:
NO` 或 `VERIFIED: NO`，Final Status 必须 FAIL，不受其他能力正常影响。
项目设计允许的 Optional 能力可以 Graceful Degradation（不可用时不崩溃），
但这只说明"没有崩溃"，不能把整体状态判为完全成功——Critical 能力的
Graceful Degradation 同样只是避免 Crash，不能当作功能本身没问题。

## Runnable Spine / First Visible Value

新项目/新功能不应该"开发很久才第一次运行"。展开的 Runnable Spine、
Runnable Checkpoint、TTFV（Time To First Visible Value）、
Work ≠ Value 等开发节奏原则定义在 `CORE/07_DELIVERY_RULES.md`（那是
"怎么开发"的规则，本文件是"怎么验收"的规则），两者配合形成
`FAST TO START, STRICT TO FINISH`。

## DeliveryProof 状态机

```
SPECIFIED → IMPLEMENTING → BUILDABLE → RUNNABLE_INTERNAL
→ RUNNABLE_USER_ENTRY → VISIBLE → INTERACTIVE → CORE_JOURNEY_PASS
→ REGRESSION_PASS → DELIVERY_VERIFIED → USER_READY
```

不允许跳级宣称（例如 `IMPLEMENTING`/`BUILDABLE`/`RUNNABLE_INTERNAL`
直接跳到 `USER_READY`）；不适用的阶段（如 CLI 项目没有 `VISIBLE`）标记
`NOT_APPLICABLE` 后可以跳过，但必须写明，不能悄悄不提。任何一级失败：
`FAILED → ROOT_CAUSE → FIX → REVERIFY`（复用 `CORE/06` 的自愈循环），
直到通过或命中 `CORE/08` 的 STOP 条件/`BLOCKED_TECHNICALLY`。

## Completion Guard

`id: LAEP-ACC-005 · MUST` — 宣布 `USER_READY` 之前，自查以下问题，任一
答案为"否"就禁止宣布：

```
是否测试过用户真实入口（不是开发者内部命令）？
是否真的运行过最终交付物？
如果是 GUI：是否验证过窗口真实可见（不是只做了 Headless）？
是否有任何 required=true 的能力被静默降级？
是否走过核心用户旅程？
Evidence 是否还新鲜（见下）？
证据产生之后代码有没有再改动？
验证环境是否正确（不是随便一个能跑的环境）？
有没有把 Headless 成功和真实可见成功搞混？
有没有仅凭推理就宣称成功？
```

## Evidence 新鲜度（Evidence Expiration）

`CORE/04_TEST_RULES.md` 的 Evidence Protocol 之上追加：Evidence 必须绑定
产生它时的代码状态（Commit/Diff 指纹、环境、命令、时间戳）。相关文件在
某次验收 PASS 之后又被修改，之前的 Evidence 立即失效（标记 `STALE`），
必须重新验证，不能拿旧结果证明新代码。`TEMPLATES/EVIDENCE_REGISTRY_TEMPLATE.yaml`
提供可选的机器可读记录结构，S3+ 建议启用。

## Model Claim Is Not Evidence

`id: LAEP-ACC-006 · MUST` — "功能已完成""程序运行正常""UI 已经修复"这类
模型自己的陈述不能作为 Evidence。Evidence 只能来自工具输出、Runtime
观察、测试结果、日志、Screenshot、真实产物——呼应 `CORE/04` 的 Evidence
Protocol，本条把它明确应用到"完成声明"这个具体场景。

## USER_READY_DoD

面向真实终端用户的交付，在 `QUALITY/02` 通用 DoD 之上追加（不替代，只
加严）：

```
Implemented + Build Passed + Required Tests Passed（OBSERVED/MEASURED）
+ Delivery Artifact 存在（若项目预期产出安装包/可执行文件）
+ User Entrypoint 已验证（A3）
+ 若为 GUI：Visible UI 已验证（A4）
+ 核心能力未被静默降级
+ Core User Journey 已走通（A6，标记为 Critical 的部分）
+ No Critical Regression（逐条核对 Invariant）
+ No Blocking Runtime Error
+ Evidence 未过期（未在证据产生后又修改相关代码）
+ Documentation Updated + Project State Updated
```

## Final Status（只允许这四种）

`USER_READY · PARTIALLY_READY · NOT_READY · BLOCKED`

GUI 未真正验证可见、核心能力被静默降级、Launcher 未测试——任一命中都
不得 `USER_READY`。`TEMPLATES/ACCEPTANCE_REPORT_TEMPLATE.md` 定义最终
报告结构。

## 弱模型验收（Weak-Model Guardrails）

模型能力越弱，验收越要机械化，不能让模型自己判断"看起来差不多就算完成"。
`AGENTS/00_MASTER_AGENT.md` 的 Tier A/B/C 分级在此基础上追加：Tier B/C
执行验收任务时必须拿到明确的 Launch Command、Working Directory、Test
Command、Expected Output/Window/Text/Artifact、Pass Condition（即一份
Acceptance 版 Task Packet），不能只丢一份 Acceptance Manifest 让它自己
摸索验收该怎么做。

`AUTHOR ≠ FINAL VERIFIER`：写代码的 Agent 容易产生确认偏差（"这是我写的
所以应该对"）。S2 以上项目的最终 `USER_READY` 判定，应尽量由
`AGENTS/10_ACCEPTANCE_AGENT.md` 定义的独立验收角色执行（单 Agent 顺序
扮演模式下，至少是"用一次 Fresh Verification 视角重新核对，不直接采信
开发阶段自己下的'已完成'结论"）。

## 按 Scale 的要求

| Scale | 本级 MUST 的 Acceptance Level | 备注 |
|---|---|---|
| S1 | A0-A3；GUI 项目另加 A4 | A5+ 为 SHOULD，不强制 Fresh Verification |
| S2 | + A5、A6（Critical 旅程） | 开始要求 Completion Guard 逐项自查 |
| S3 | + A7、A8 | 开始要求 Fresh Verification（`AGENTS/10`）与 Evidence Expiration 追踪 |
| S4 | + Clean Restart | Capability Matrix 强制维护 |
| S5 | 全部 A0-A9 + Clean Room | 与 `SCALE/S5_MEGA.md` 的 Compatibility Testing/Security Audit 共同构成发布前硬性关卡 |

不适用某一级的项目类型（如 LIBRARY 没有 A3-A6）标记 `NOT_APPLICABLE`，
不算未达标。
