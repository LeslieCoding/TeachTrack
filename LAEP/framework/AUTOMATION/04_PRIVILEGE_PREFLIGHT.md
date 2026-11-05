# AUTOMATION/04 — Privilege-Aware Execution Manager（PAEM）

*横切能力，不属于任何单一 Profile——适用于所有需要与操作系统权限打交道
的项目（Windows/Linux/macOS，任何 Scale）。目标：在不绕过操作系统安全
机制的前提下，最大程度减少人工权限操作，让同一个已授权会话持续复用
已有权限。*

## 最重要的一条规则

**看到"需要管理员权限"不得立即新开管理员终端。** 必须先判断
`CURRENT PROCESS PRIVILEGE`，按下面的决策函数走。

```
execute_with_required_privilege(task):
  required = task.required_privilege        # NORMAL | ADMIN
  current  = detect_current_privilege()      # NORMAL | ADMIN

  if required == NORMAL:
      run in current session
  elif required == ADMIN and current == ADMIN:
      run in current session                 # 直接复用，不再嵌套提权
  elif required == ADMIN and current != ADMIN:
      report to Privilege Manager            # 见下方"统一决策"
```

## Windows 权限预检测（Privilege Preflight）

任何需要系统级修改的任务开始前：

```
Detect OS → Detect User → Detect Current Privilege
→ Detect Required Privilege → Select Execution Context
```

当前 PowerShell/CMD/Terminal/IDE 内置终端/Agent Shell 是否已具备管理员
权限，用可靠方式检测（Windows Access Token / Administrator Role 等），
不得凭猜测判断。检测结果、当前 Shell 类型（PowerShell 5/7、CMD、IDE
内置终端……不得假设所有用户都是 `powershell.exe`）写入
`STATE/PRIVILEGE_STATE.md`（模板见
`framework/STATE/PRIVILEGE_STATE_TEMPLATE.md`），避免每个任务都重新
检测、反复弹窗。

## 当前终端优先原则

```
Current Terminal → Current Process Tree
→ Existing Privileged Worker → New Privileged Session
```

最后一项只在前三项都不可行、且确实需要时才允许。当前终端已经是管理员时，
**所有可以直接执行的管理员命令必须直接执行**——不生成命令给用户复制、
不开新窗口、不触发 UAC、不调用 `Start-Process -Verb RunAs` /
`runas.exe` / 管理员 Shell 嵌套。

## 禁止重复提权

绝对禁止：已经在管理员终端里，还去启动另一个管理员 PowerShell/CMD、
或再次触发 UAC。也禁止对一批命令逐条 `RunAs`（RunAs A / RunAs B / RunAs
C……形成一堆独立 UAC 请求）。权限是 **Session Context**，不是
**Command Context**——按会话管理，不按单条命令管理。

## 未授权时的处理

当前不是管理员、任务确实需要管理员权限：**不得假装自己拥有管理员权限，
不得绕过 UAC，不得使用漏洞/注册表技巧/计划任务滥用/DLL 劫持/Token 窃取
等任何方式绕过系统安全确认**（Windows 首次授予管理员权限属于操作系统
安全边界，LAEP 只能减少重复确认，不能未经用户授权偷偷获得）。状态设为
`PRIVILEGE_REQUIRED`，如实告知用户，不得谎称"已经自动获得管理员权限"。

## 一次授权原则（批处理）

确实需要提权时，先汇总当前阶段**全部**需要管理员权限的操作（例如：
安装驱动、注册服务、修改受保护目录、配置防火墙规则），然后建立**一个**
合法管理员会话集中完成，而不是"4 个操作 = 4 次 UAC"。授权建立后设
`PRIVILEGED_SESSION_ACTIVE = TRUE`，后续需要管理员权限的子任务继续复用
同一权限上下文，直到当前开发会话/项目结束。

## 统一决策：Privilege Manager

权限上下文的决策权**只属于 Master Agent / Privilege Manager**
（见 `AGENTS/00_MASTER_AGENT.md`）。普通 Agent（Development/Test/
DevOps/…）遇到 `ACCESS_DENIED` / `ERROR_ELEVATION_REQUIRED` /
`Permission Denied`，**必须上报给 Privilege Manager，不得自行 RunAs**。
所有 Agent 共享同一份 `PRIVILEGE_STATE`，不允许出现"Development Agent
已知道当前是管理员，Test Agent 却又启动一个管理员终端"这种状态不一致。

## ACCESS_DENIED 不一定等于需要管理员

看到 Access Denied 先分析真实原因，不得盲目提权：文件被占用、ACL 限制、
只读属性、杀毒软件拦截、进程所有权不一致、路径错误、权限不足——只有确认
是真正的权限问题才升级为需要管理员，其余情况按对应原因修复
（呼应 `CORE/06_ERROR_HANDLING.md` 的自诊断纪律，本质是同一原则在权限
场景下的具体应用）。

## Least Privilege

能用普通权限完成就用普通权限，不得为了"方便"让整个开发过程长期以最高
权限运行。可以在任务开始时把 Normal Tasks 和 Privileged Tasks 分组，
尽量按 `Normal Work → Batch Privileged Work → Normal Work` 的顺序安排，
减少权限切换次数。

## Android Zero-Touch 集成

`adb devices` / `adb install` / `adb shell` / `adb logcat` /
`gradlew assembleDebug` 这些正常情况下不需要管理员权限，**禁止为了
"保险"给 Android 自动部署默认加管理员**。只有真正涉及驱动安装、USB
权限修复、系统服务这类操作时，才按本文件的批处理原则纳入一次性管理员
会话。详见 `PROFILES/ANDROID_APP/DEPLOYMENT/00_ANDROID_DEPLOYMENT.md`
的对应说明。

## Windows 具体场景

- **驱动 / 服务**：识别为 `PRIVILEGE_REQUIRED`；已有管理员上下文直接
  执行，否则纳入一次性 Privileged Batch。
- **防火墙**：只有确实需要时才修改规则，禁止开发一个普通 App 就顺手
  创建防火墙规则。
- **受保护目录**（`Program Files`/`Windows`/`System32`/`HKLM`/系统服务
  路径）：自动判断权限，但不得通过关闭 ACL 或修改整个目录安全设置来
  "方便开发"。
- **注册表**：区分 `HKCU`（通常不需要管理员）和 `HKLM`（需要），不得
  一律用管理员执行。

## Linux / macOS

已经 `UID = 0` 时不再 `sudo`；存在合法 sudo credential cache 时正常
复用系统原生 sudo 机制，不绕过 sudo 密码策略。macOS 同样遵守
Admin Authorization / sudo 的系统安全边界，不额外发明绕过方式。

## 环境保留

如果必须建立新的合法管理员上下文，保留当前项目目录、环境变量
（`JAVA_HOME`/`ANDROID_HOME`/`ANDROID_SDK_ROOT`/`PATH`/Gradle/项目专属
ENV）、Project Config、目标设备、Build 状态、Git 分支——不能让管理员
终端打开后变成 `C:\Windows\System32`、找不到项目，也不能因为提权导致
`adb not found` / `java not found` / `gradle not found`。

## Scoped Privileged Worker（大型连续任务可选）

大型连续开发任务可以设计一个"权限 Worker"，但必须满足：通过正常系统
授权建立、生命周期受 LAEP 管理、只接受允许范围内的任务、不暴露任意
远程执行接口、不永久后台驻留、项目结束或用户关闭项目后立即 `Terminate`
并清理临时资源。**禁止**把它做成永久 SYSTEM 服务、隐藏管理员守护进程，
或不受限的常驻高权限 RPC——这些不是"提高效率"，是留后门。

## 绝对禁止清单

不得：自动关闭 UAC、修改 UAC 安全等级、使用任何 UAC bypass 技术
（fodhelper/eventvwr/computerdefaults/DLL 劫持/Token 窃取等）、窃取或
伪造管理员 Token、保存用户管理员密码/sudo 密码/UAC 凭据（明文或任何
形式）、默认让整个项目以 SYSTEM 权限运行、创建无限制的永久管理员服务、
为解决普通开发权限问题而禁用 Defender/杀毒软件/SmartScreen。

`project.yaml` 里 `execution.privilege.allow_security_bypass` 这个字段
**只能是 `false`**——即使被人为改成 `true`，LAEP 也必须忽略它、按
`false` 执行，不存在任何合法途径把它打开。见
`PROJECT_CONFIG/SCHEMA.md`。

## Definition of Done 扩展（涉及管理员操作的任务）

在 `QUALITY/02_DEFINITION_OF_DONE.md` 通用标准之上追加：

```
Operation Completed + No Redundant Elevation
+ No Security Policy Bypass + PRIVILEGE_STATE Updated
```

## 状态展示（可选）

```
HOST: WINDOWS   SHELL: POWERSHELL   PRIVILEGE: ADMIN
SESSION_REUSE: ACTIVE   REDUNDANT_UAC: DISABLED
```

或：

```
PRIVILEGE: STANDARD   ADMIN_REQUIRED: NO
```

## 简单项目不受影响

S1/S2 项目如果本身不需要管理员权限，整个开发过程不应出现任何 UAC——
本模块只在真正需要系统权限时才介入，不给不需要它的项目增加负担
（见 `SCALE/S1_SMALL.md` 的对应说明）。默认永远是 Normal User，管理员
只是"确有需要时的例外"，不是默认姿态。
