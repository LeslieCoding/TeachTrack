# TEMPLATES/ACCEPTANCE_REPORT_TEMPLATE

`FINALIZE_PROJECT` 产出的完成报告结构。Release 相关字段（`Final Status:
RELEASE_CANDIDATE | STABLE`）由 `GOVERNANCE/05_RELEASE_GOVERNANCE.md` 定义；
Delivery 相关字段（`User-Facing Status`）由
`QUALITY/03_ACCEPTANCE_PROTOCOL.md` 定义，两者回答的是不同问题
（"这个版本能不能发布" vs "用户拿到手真的能用吗"），都要给：

```
Completed Features: [...]
Known Limitations: [...]（每条带 Evidence Protocol 标签）
Test Result: <汇总，含两轮最终测试结果>
Build Result: <汇总>
Security Result: <汇总>
Performance Result: <汇总，含 Baseline Before/After（若适用）>

User Entrypoint Result: <A3，PASS|FAIL|NOT_VERIFIED|NOT_APPLICABLE>
Visible UI Result: <A4，若为 GUI 项目>
Core Journey Result: <A6，逐条旅程列结果>
Critical Capabilities: <Capability Matrix 汇总，任何 CRITICAL 且不可用的单独列出>
Delivery Artifact Result: <A8，若项目预期产出独立交付物>

Remaining Technical Debt: [...]
Future Improvement Suggestions: [...]

Final Status（Release，GOVERNANCE/05）: RELEASE_CANDIDATE | STABLE
User-Facing Status（Delivery，QUALITY/03）: USER_READY | PARTIALLY_READY | NOT_READY | BLOCKED
```

`User-Facing Status` 不是 `Final Status` 的同义词，不得用后者代替前者——
一个版本可以是 `STABLE` 但因为验收发现问题而是 `PARTIALLY_READY`，两者
分开报告。
