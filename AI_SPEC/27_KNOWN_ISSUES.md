# TeachTrack Known Issues

## Baseline issues

1. Release signing key is not configured; the release artifact is unsigned.
2. Some historical logs/source literals contain old encoding contamination; new round logs use UTF-8.
3. Prior physical-device evidence exists on both devices; the current physical-test target is OnePlus `92f73a16`, while OPPO Pad evidence is historical.
4. OPPO Pad passed the complete SAF backup/restore flow, backup-save cancellation, CSV export, and invalid-file rejection. OnePlus has now passed installation and instrumentation; a second full teacher workflow round remains to be covered.

## V2 issues

5. Copilot foundation and the local action adapters now cover the declared local action IDs at the registry boundary; physical semantic acceptance and audit/undo coverage remain partial, so the V2 verification matrix remains `PARTIAL`.
6. No provider credential or vendor-specific endpoint has been supplied; real network adapters cannot be accepted yet.
7. Runtime voice permission and the system recognizer boundary are implemented. The prior OPPO Pad recognizer returned no text and exited; end-to-end voice acceptance remains open.
8. Sensitive-data consent, audit redaction, provider settings, and complete semantic coverage still require implementation and testing.
9. OnePlus-only instrumentation has now passed 4 tests in the latest run; the full two-round physical regression remains open.

10. Provider configuration UI, offline gateway, and the generic OpenAI-compatible cloud adapter are implemented. Real vendor acceptance is pending a user-provided endpoint/model/API Key; no credential is present in the repository or test screenshots.
11. OAuth Authorization Code + PKCE configuration, browser launch, callback validation, encrypted token storage, and logout are implemented. End-to-end login still requires a real identity-provider client registration and redirect URI allow-list entry.
