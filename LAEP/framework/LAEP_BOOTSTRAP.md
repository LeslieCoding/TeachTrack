# LAEP_BOOTSTRAP

任何 AI Agent 第一次（或再次）进入一个项目，必须按以下顺序装载，
不得跳步，也不得因为"觉得用不上"而提前省略——省略的判断权属于
Profile/Scale 本身（哪个文件该不该被装载，由 Profile/Scale 的选择决定，
不是由 Agent 临场判断）：

```
READ  framework/LAEP_BOOTSTRAP.md         (本文件)
  ↓
LOAD  framework/CORE/*                    (全部 9 个文件，任何项目都装载)
  ↓
LOAD  <project>/LAEP/PROJECT_CONFIG/project.yaml
        不存在 → 这是新项目，转 WORKFLOWS/INIT_PROJECT.md，本流程到此为止
  ↓
LOAD  framework/PROFILES/<project.yaml: profiles[]>/PROFILE.md
  ↓
LOAD  framework/SCALE/S<project.yaml: scale>_*.md
  ↓
LOAD  framework/GOVERNANCE/* 中 Scale 文件标注为"本级启用"的部分
        （S1/S2 通常只启用 Scope + Validation 的轻量子集，
        S3+ 逐步启用全部五项，见对应 SCALE 文件）
  ↓
LOAD  <project>/LAEP/STATE/*              (当前状态、Backlog、已知问题、
                                            技术债、决策记录)
  ↓
CONTINUE
        按 WORKFLOWS/RESUME_PROJECT.md 判断"现在该做什么"并开始执行
```

## 关键约束

- **CORE 永远全部装载**，且只包含所有项目都真正需要的规则；CORE 里不允许
  出现任何 Android/Windows/Linux/AOSP/Kernel/Web/桌面专属规则——那些必须
  在对应 Profile 里，见 `CORE/00_LAEP_CORE.md` 的"CORE 边界"一节。
- **一个项目只装载它自己 Profile/Scale 对应的那部分**，不装载其余 11 个
  Profile 或其他 4 个 Scale 等级的内容。一个 Todo App 项目永远不会读到
  `PROFILES/OPERATING_SYSTEM/PROFILE.md`。
- **PROJECT_CONFIG 是最高优先的项目级事实来源**：如果用户已经明确配置了
  Profile/Scale，禁止 Agent 自作主张改用别的组合；只有在 `project.yaml`
  完全不存在时，才由 Agent 自动识别并推荐（见
  `WORKFLOWS/INIT_PROJECT.md`）。
- **不要求用户一次性填几十个参数**。理想流程是：用户用一句话描述项目 →
  LAEP 分析 → 推荐 Profile/Scale → 生成 `project.yaml` → 开始开发；用户
  随时可以覆盖推荐结果。
- 装载之后，AI 的一切行为默认处于 `CORE/08_AI_EXECUTION_RULES.md` 定义的
  持续自主执行模式（Continuous Autonomous Execution）——本 Bootstrap 序列
  本身不是暂停点，走完就应该直接进入实际工作，不需要向用户逐项汇报"我读完
  CORE 了""我读完 Profile 了"。
