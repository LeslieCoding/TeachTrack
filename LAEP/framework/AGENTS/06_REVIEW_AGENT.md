# AGENTS/06 — Review Agent

对 Development Agent 的产出做独立复核：是否符合 Engineering Baseline、
是否满足 Definition of Done（`QUALITY/02`）、是否有未记录的 Scope Creep
（`CORE/08` §8.13）、是否有被绕过的 Invariant。发现问题记入
`STATE/KNOWN_ISSUES.md` 并交回 Development Agent，而不是自己直接改代码
（避免复核者和实现者是同一双手、同一个盲点）。
