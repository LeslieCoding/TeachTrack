# TeachTrack Architecture

## 1.x foundation

`Compose UI -> ViewModel -> Repository -> Room DAO -> SQLite`

The existing local-first MVP remains the source of truth for students, courses, scores, knowledge, plans, tasks, communication, backup, and timeline history.

## 2.0 Copilot extension

`Voice/Text Input -> Command Composer -> Provider Gateway -> Intent Validator -> Confirmation Policy -> Local Action Registry -> Repository/Room`

The cloud model is never allowed to call Room directly. It returns a strict, versioned command envelope. A local validator checks schema, entity references, permissions, sensitive-field policy, and whether confirmation is required. Only the local Action Registry can invoke existing repository operations.

The provider layer is optional. When no provider is configured, the full 1.x application remains usable and the command composer reports that Copilot is unavailable without blocking normal workflows.

## Provider boundary

`ProviderGateway` owns provider-neutral requests and responses. Provider adapters translate to a vendor API. API keys are stored only through Android Keystore-backed local storage or a future secure credential mechanism; they are never written to Room, logs, backup JSON, screenshots, or analytics.

## Action boundary

Each action has a stable ID, input schema, validation function, confirmation level, undo strategy, audit label, and repository call. Examples include `student.create`, `score.create`, `task.create`, `course.create`, `homework.create`, `communication.create`, `lesson_plan.create`, and read-only search/summary actions.

## Safety boundary

Natural language is not authorization. A write command is previewed before execution when it creates, edits, archives, exports, contacts, or changes sensitive information. Family contacts, student scores, and bulk operations require explicit confirmation. Ambiguous commands must produce a clarification state rather than guessing.
