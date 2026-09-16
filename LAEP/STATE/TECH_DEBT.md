# TECH_DEBT

- TD-001: `app/src/main/java/com/leslie/teachtrack/copilot/AiCommandRuntime.kt`
  defines a typed command contract — `AppCommandRegistry` / `AppCommand`
  (`CreateCourse`/`AddScore`/`UpdateKnowledge`/`Query`) / `CommandPlan` /
  `AppCommandValidator` — that looks like an earlier, more principled
  attempt to replace the current string-based `CopilotCommand` contract: it
  carries explicit `CommandRisk` tiers (READ_ONLY/SAFE_WRITE/
  SENSITIVE_WRITE/DESTRUCTIVE), closer to
  `GOVERNANCE/03_CHANGE_GOVERNANCE.md`'s risk-tiering than the live
  system's simple `readOnly` boolean check. It is never called from
  `LocalCopilotActionRegistry`, `ProviderGateway`, `CopilotService`,
  `MainActivity`, or `CopilotComposer` — its only caller is
  `CopilotContractsTest.kt`'s two isolated unit tests
  (`typedCommandValidatorRejectsImpossibleScore`,
  `typedCommandValidatorRequiresCourseContent`). `StudentEntityResolver`/
  `EntityResolution` in the same file ARE live (used by
  `LocalCopilotActionRegistry`) and are out of scope for this item.
  Disposition (deferred to next version, not touched by this round of AI
  coverage work — removing it now would also require removing two passing
  tests, for no functional gain toward the current coverage goal): either
  formally absorb this risk-tier design into the live `CommandRisk`/
  `allowedActions` system and replace the string contract, or confirm it is
  unnecessary and delete it together with its two tests, per
  `CORE/07_DELIVERY_RULES.md`'s Dead Code Cleanup — after the two final
  test rounds, not left unrecorded out of convenience.

- TD-002: `FeatureScreens.kt` defines two overloads named
  `TemplateSummaryCard` — one read-only
  (`@Composable private fun TemplateSummaryCard(templates: List<...>)`) and
  one with management actions
  (`@Composable private fun TemplateSummaryCard(vm, templates)`),
  disambiguated by Kotlin overload resolution. Legal and compiles cleanly,
  readability only; left as-is pending a later UI cleanup pass.
