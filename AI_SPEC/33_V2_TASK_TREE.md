# TeachTrack 2.0 Task Tree

## `V2-EPIC-001` TeachTrack Copilot

- `V2-F01` Version and design foundation
  - `V2-T01` bump version name/code and add version metadata
  - `V2-T02` add Copilot domain package boundaries and feature flag
  - `V2-V01` build and 1.x regression tests
- `V2-F02` Command domain contract
  - `V2-T10` define command envelope and strict parser
  - `V2-T11` define action definitions and confirmation levels
  - `V2-T12` implement validation and ambiguity states
  - `V2-V02` parser/property/unit tests
- `V2-F03` Action Registry
  - `V2-T20` read-only search/navigation actions
  - `V2-T21` student/profile/contact actions
  - `V2-T22` course/plan/homework/task actions
  - `V2-T23` exam/score/knowledge/problem actions
  - `V2-T24` communication/template/tag/attachment actions
  - `V2-T25` export/analytics/dashboard actions with safety limits
  - `V2-V03` registry coverage and repository integration tests
- `V2-F04` Provider Gateway
  - `V2-T30` provider-neutral interface and fake provider
  - `V2-T31` configurable provider settings and protected secret storage
  - `V2-T32` provider test and timeout/error handling
  - `V2-V04` no-key/offline/failure tests
- `V2-F05` Home command composer
  - `V2-T40` text input, draft, preview, result, and history UI
  - `V2-T41` voice permission and transcription adapter
  - `V2-T42` confirmation and undo UX
  - `V2-V05` OPPO Pad UI workflow evidence
- `V2-F06` Privacy, audit, and release controls
  - `V2-T50` redaction and consent gate
  - `V2-T51` local audit log and sensitive-action review
  - `V2-T52` backup exclusion/provider secret cleanup
  - `V2-V06` security regression and backup inspection
- `V2-F07` End-to-end acceptance
  - `V2-T60` typed create-student flow
  - `V2-T61` typed score/course/task flow
  - `V2-T62` voice-to-draft-to-confirm flow
  - `V2-T63` ambiguous and denied-action flow
  - `V2-T64` provider outage/offline fallback flow
  - `V2-V07` two-round physical-device test and release audit

Dependencies: F02 before F03/F04; F03 and F04 before F05; F06 before any external-provider acceptance; F07 after all feature verification.
