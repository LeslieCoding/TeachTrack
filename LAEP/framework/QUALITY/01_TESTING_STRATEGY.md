# QUALITY/01 — 测试策略（按 Scale 动态生成）

## 测试金字塔层级

Unit / Integration / System / Regression / Performance / Security /
Compatibility。S1 不需要全部启用，S5 可能全部启用——具体每一级在哪个 Scale
启用，见 `SCALE/` 对应文件；本文件只定义每一级"测什么"。

- **Unit** — 单个函数/类/模块的正确性。
- **Integration** — 模块之间协作的正确性。
- **System / Functional** — 端到端功能是否符合验收标准。面向真实用户
  入口的项目，这一层必须包含 `QUALITY/03_ACCEPTANCE_PROTOCOL.md` 定义的
  User Entrypoint / 可见性 / Core User Journey 验证，不能只测开发者内部
  命令能跑通。
- **Regression** — 改动后确认没有破坏既有正确行为，逐条核对
  Invariant Registry（`CORE/02`）。
- **Performance** — 有 Baseline（`CORE/04`）时的 Before/After 对比。
- **Security** — 见 `SECURITY/`。
- **Compatibility** — 跨环境/版本/设备时启用，S3+ 用
  `TEMPLATES/` 里的兼容性矩阵模板记录结果。

## 场景覆盖

任何重要功能至少考虑：Happy Path、边界值、空输入、Null、非法输入、超大
输入、权限拒绝、网络失败、超时、磁盘满、数据库失败、重启、升级、并发操作、
重复点击、重复请求、操作中断。

## 两轮最终测试（S3+，见 `WORKFLOWS/FINALIZE_PROJECT.md`）

Round 1：完整跑一遍 Build/Unit/Integration/Functional/Regression，修复
所有阻塞问题。Round 2：重新完整跑一遍，确认 Round 1 的修复没有引入新问题。
两轮都要有 Evidence Protocol 标签（`CORE/04`），不能凭印象说"应该没问题"。

## Dead Code Cleanup

两轮测试完成后执行（`CORE/07`）：检查未使用代码、临时文件、Debug 文件、
重复代码、旧依赖、无用资源、构建缓存、测试产生的垃圾文件，禁止为了减小
体积误删必要源码。
