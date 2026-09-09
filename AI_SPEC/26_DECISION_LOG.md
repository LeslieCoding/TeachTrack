# TeachTrack Decision Log

## D-20260909-01 — Major version naming

- Source: `USER_EXPLICIT`
- Decision: Use `TeachTrack 2.0 · TeachTrack Copilot`; Android `2.0.0-alpha.1`, version code `200001`.
- Reason: This is a major product direction, but implementation is not complete; alpha communicates status honestly.
- Rollback: Restore prior version metadata if the 1.x release line must be rebuilt.

## D-20260909-02 — Model is an interpreter, not an executor

- Source: `USER_INFERRED` plus P0 security policy.
- Decision: Cloud output becomes a versioned command envelope and can only call allow-listed local actions.
- Prohibited: raw SQL, arbitrary method calls, silent writes, automatic messages, and unconfirmed sensitive operations.

## D-20260909-03 — Provider abstraction first

- Source: `AI_RECOMMENDED` under user requirement for configurable vendors.
- Decision: Implement provider-neutral gateway and FakeProvider before any real vendor adapter.
- Reason: protects local-first behavior, avoids locking the product to one vendor, and enables deterministic tests.

## D-20260909-04 — Voice is optional input

- Source: `USER_EXPLICIT` with privacy constraint.
- Decision: request microphone permission only on use, turn speech into editable text, and never require voice for normal operation.

## D-20260909-05 — Physical test target switched to OPPO Pad

- Source: `USER_EXPLICIT`
- Decision: all subsequent installation, screenshot, UI, and physical regression tests use OPPO Pad only; OnePlus is excluded.
- Current state: OPPO Pad `2d64f765` is the active device target and is visible to ADB; OnePlus `92f73a16` is historical evidence only.

## D-20260909-06 — Physical test target switched to OnePlus

- Trigger: the user explicitly requested that subsequent testing use the connected OnePlus phone because the Pad is needed for use.
- Decision: all subsequent installation, screenshot, UI, shell, and regression commands target OnePlus `92f73a16` only.
- OPPO Pad `2d64f765` remains historical evidence and is not operated unless the user changes the target again.
