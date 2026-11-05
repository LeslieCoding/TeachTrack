# PROJECT_CONFIG/SCHEMA — project.yaml 字段定义

每个项目的 `/LAEP/PROJECT_CONFIG/project.yaml`（模板见
`TEMPLATES/PROJECT_CONFIG_TEMPLATE.yaml`）至少包含：

| 字段 | 说明 |
|---|---|
| `project_name` | 项目名称 |
| `laep_version` | 该项目当前对齐的 `framework/VERSION` |
| `project_type` | 自由文本，简述项目性质 |
| `scale` | `S1`\|`S2`\|`S3`\|`S4`\|`S5`，见 `SCALE/` |
| `profiles` | 数组，一个或多个，对应 `PROFILES/<NAME>/` |
| `target_platform` | 目标运行平台 |
| `primary_language` | 主语言 |
| `secondary_languages` | 次要语言，数组 |
| `repository_mode` | `single`\|`multi`，multi 时需要
`PROFILES/MULTI_REPOSITORY/PROFILE.md` |
| `risk_level_default` | 该项目默认风险基线（不改变单次改动的实际风险判断，
只是一个参考基线） |
| `release_strategy` | 自由文本，简述发布策略 |
| `test_level` | 自由文本，简述测试要求水位 |
| `security_level` | 自由文本，简述安全要求水位 |
| `continuous_execution` | 布尔值，默认为 `true`，对应 `CORE/08` |
| `acceptance_profile` | `CLI_APP`\|`DESKTOP_GUI`\|`ANDROID_APP`\|`WEB_APP`\|`BACKEND_SERVICE`\|`LIBRARY`\|`OPERATING_SYSTEM`\|`EMBEDDED_SYSTEM`，见 `QUALITY/03_ACCEPTANCE_PROTOCOL.md`；决定验收重点，与 `profiles` 字段独立但通常对应 |

**这是项目级最高优先级的事实来源**：一旦存在，任何自动识别结果都不得
覆盖它，只能在用户明确要求时被修改（见 `WORKFLOWS/INIT_PROJECT.md` 第 4
步、`WORKFLOWS/UPGRADE_PROJECT.md`）。

## execution.privilege（通用字段，非 Profile 专属）

与 `android:` 不同，权限管理适用于所有项目（任何 Profile、任何 OS），
因此这个块是 `TEMPLATES/PROJECT_CONFIG_TEMPLATE.yaml` 通用模板的一部分：

```yaml
execution:
  privilege:
    mode: adaptive              # adaptive | normal_only | require_elevated_session
    reuse_current_session: true
    batch_privileged_operations: true
    avoid_redundant_elevation: true
    allow_security_bypass: false
```

- `adaptive`（默认）：需要时按 `AUTOMATION/04_PRIVILEGE_PREFLIGHT.md`
  自动判断和批处理。
- `normal_only`：任何需要管理员/sudo 的任务一律不执行，报告
  `BLOCKED_BY_PRIVILEGE_POLICY`，交给用户决定。
- `require_elevated_session`：项目本身大量需要管理员（如系统工具类
  项目），启动时一次性建立正确的开发权限上下文，之后不再逐任务弹窗。
- `allow_security_bypass` **只能是 `false`**——即使配置文件被改成
  `true`，LAEP 也必须忽略、按 `false` 执行；不存在合法途径打开它。

## Acceptance Manifest（通用字段，非 Profile 专属）

面向真实终端用户交付的项目，本地维护 `/LAEP/ACCEPTANCE_MANIFEST.yaml`
（模板见 `TEMPLATES/ACCEPTANCE_MANIFEST_TEMPLATE.yaml`），记录用户真实
入口、核心用户旅程、关键能力清单，供 `WORKFLOWS/ACCEPT_PROJECT.md` 和
`AGENTS/10_ACCEPTANCE_AGENT.md` 直接读取执行，避免每次验收都重新猜测
"该测什么"。LIBRARY 一类没有独立用户入口的项目可以省略，在
`acceptance_profile` 里说明即可。

## Profile 专属扩展块

装载了某个 Profile 的项目，`project.yaml` 可以附加该 Profile 定义的
额外配置块，字段由对应 Profile 文档定义，不属于本 Schema 的通用部分。
例如 `ANDROID_APP` 定义了 `android:` 块（部署模式、目标设备等），见
`PROFILES/ANDROID_APP/DEPLOYMENT/00_ANDROID_DEPLOYMENT.md`。新增这类
扩展块时只应在对应 Profile 内生效，不得反向要求通用模板
（`TEMPLATES/PROJECT_CONFIG_TEMPLATE.yaml`）为所有项目类型都携带它。
