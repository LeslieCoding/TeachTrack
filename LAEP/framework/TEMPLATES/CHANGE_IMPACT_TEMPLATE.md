# TEMPLATES/CHANGE_IMPACT_TEMPLATE

修改核心模块或已 BASELINED 需求前，`GOVERNANCE/03_CHANGE_GOVERNANCE.md`
要求的分析记录：

```
Change: <改动是什么>
Affected Files: [...]
Affected Modules: [...]
Affected APIs: [...]
Affected Tests: [...]
Potential Regression: <可能影响到的既有行为/Invariant>
Risk Level: R0 | R1 | R2 | R3 | R4 | R5
Rollback Strategy: <如果出问题怎么退回去>
Decision: PROCEED | REVISE | STOP-05（触发用户参与）
```
