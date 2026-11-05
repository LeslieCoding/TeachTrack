# CORE/07 — 交付规则

## Feature Freeze / Scope Freeze

当前版本只开发本版本 Scope 文件里列出的功能
（`GOVERNANCE/01_SCOPE_GOVERNANCE.md`）。开发过程中冒出的新想法，默认流程：

```
NEW IDEA → STATE/BACKLOG.md → 留到下一版本评估
```

除非用户明确说"把这个加进当前版本范围"，否则不得擅自把新想法塞进当前版本。

## Backlog

`STATE/BACKLOG.md` 记录所有还没进入当前开发范围的功能想法，防止它们
凭空消失，也防止它们未经允许污染当前 Scope。

## Anti-Overengineering（禁止过度工程）

S1/S2 项目禁止为了"可扩展性""企业级""未来可能用得上"而搭建当前完全不需要
的复杂架构。原则：**Solve Today's Problem Without Blocking Tomorrow.**
一个 Todo App 不需要插件系统、不需要微服务拆分、不需要多租户设计。

## Anti-Underengineering（禁止欠工程）

反过来，S4/S5 系统级项目禁止为了图快而长期依赖临时方案；必须认真考虑
Reliability、Compatibility、Security、Maintainability、Recovery。哪种
倾向更危险取决于 Scale，判断标准见 `SCALE/` 对应文件，不是凭感觉决定。

## Release Candidate → Stable → Maintenance Mode

大项目发布前建立 RC：原则上冻结新功能，只允许 Bug Fix / Security Fix /
Blocking Issue Fix。只有通过 Release Test、Regression、Security、
Upgrade/Migration 测试后才能定义为 Stable。进入 Stable 后转入
Maintenance Mode，重点处理 Bug、安全、兼容性、性能，不再常规加功能。
详见 `GOVERNANCE/05_RELEASE_GOVERNANCE.md`。

## Runnable Spine First

新项目/新功能不得"开发很久才第一次运行"。优先搭出最小可运行骨架
（入口 → 主窗口/主路由 → 基本布局 → 成功启动），验证这条骨架真的能跑
起来，再逐步往上叠加功能——原则：**MAKE IT RUN EARLY, THEN EXPAND**。
开发过程中标记若干 Runnable Checkpoint（例如"骨架完成""主导航完成""核心
功能完成"），每个 Checkpoint 都必须能启动；某次改动导致项目无法启动，
优先恢复"能启动"这个状态，而不是继续在无法启动的状态上叠加新功能
（除非明确是在隔离的 Feature Branch 上短期开发）。

## First Visible Value（TTFV）

在 `CORE/08` 定义的持续自主执行节奏上，GUI/有界面类项目额外关注 TTFV
（Time To First Visible Value）：从开始开发到第一次真正打开并看到有效
窗口/界面的时间。禁止"几十个模块全部写完才第一次运行 GUI"这种模式——
FAST TO START 不能只体现在"开始写代码快"，也要体现在"多久能看到第一个
真实可见的结果"。验收侧的对应要求见 `QUALITY/03_ACCEPTANCE_PROTOCOL.md`。

## Work ≠ Value

```
FILES CREATED   ≠ VALUE DELIVERED
TOKENS USED      ≠ PROGRESS
TIME SPENT        ≠ COMPLETION
```

真正的进度用 Runnable / Verified / User-visible Result 衡量，不是文件数、
代码行数、生成的文档数量。执行型项目禁止在前期生成大量不产生直接交付
价值的文档——新增文档必须能回答"它是否帮助执行/恢复/验收/维护"，回答
不了就不创建（Documentation Budget，呼应 `CORE/05` 的 Documentation as
Code：文档服务于工程，不是工程量的替代证明）。

## Dead Code Cleanup

两轮最终测试完成后执行清理：未使用代码、临时文件、Debug 文件、重复代码、
旧依赖、无用资源、构建缓存、测试产生的垃圾文件。**禁止为了缩小项目体积
而误删必要源码**——只删确认无用的东西，不确定就先搁置、记入
`STATE/TECH_DEBT.md`，不要因为怕麻烦就直接删掉。
