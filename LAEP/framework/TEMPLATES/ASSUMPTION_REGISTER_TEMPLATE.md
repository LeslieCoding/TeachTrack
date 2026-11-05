# TEMPLATES/ASSUMPTION_REGISTER_TEMPLATE

REG 在完成需求过程中做出的每一条假设都要登记一条：

```
Assumption ID: ASM-<id>
Related Requirement: REQ-<id>
Assumption: <假设了什么>
Confidence: HIGH | MEDIUM | LOW
Impact if wrong: <假设错了会导致什么问题>
How it would surface: <什么证据会显示这个假设是错的>
Status: OPEN | CONFIRMED | INVALIDATED
```

`AUDIT_PROJECT` / `FINALIZE_PROJECT` 工作流必须复核所有 `OPEN` 条目。
