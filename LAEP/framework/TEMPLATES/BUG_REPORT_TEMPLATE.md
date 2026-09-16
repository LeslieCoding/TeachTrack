# TEMPLATES/BUG_REPORT_TEMPLATE

`STATE/KNOWN_ISSUES.md` 里每条问题的标准结构：

```
ID: BUG-<id>
Severity: P0 | P1 | P2 | P3
Status: OPEN | IN_PROGRESS | FIXED | WONT_FIX | BLOCKED_TECHNICALLY
Module: <受影响模块>
Description: <描述>
Reproduction: <复现步骤>
Expected: <期望行为>
Actual: <实际行为>
Root Cause: <根因，定位到之前先留空，不要瞎猜就填>
Fix: <修复方式>
Regression Test: <验证修复且未引入新问题的测试>
```
