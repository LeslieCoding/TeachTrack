# TEMPLATES/TASK_PACKET_TEMPLATE

用于把一个任务编译成弱模型/新 Agent 不需要理解项目审美和业务背景也能
直接执行的自包含单元。

```
TASK-<id>

Objective:
  <一句话目标>

Allowed to modify:
  <文件列表>

Forbidden to modify:
  <明确禁止改动的范围，如"业务逻辑" "数据库" "导航系统">

Symptom:
  <现象，若是 Bug>

Root cause hypothesis:
  <根因假设，若是 Bug>

Implementation requirements:
  - <要点 1>
  - <要点 2>

Acceptance:
  <环境/条件 1>   PASS
  <环境/条件 2>   PASS

Regression:
  <必须保持不变的行为，最好直接引用 Invariant Registry 的 INV 编号>
```

把 Objective 关联回对应的 Requirement/Engineering Baseline ID，保持
`RAW-INPUT → REG → Requirement → ... → Task` 这条链不断。
