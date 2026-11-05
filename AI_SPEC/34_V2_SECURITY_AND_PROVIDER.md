# TeachTrack 2.0 Security and Provider Policy

## Risk classification

This is R3 because it introduces network transmission, provider configuration, secrets, and model-directed writes. It is not allowed to silently upload existing student data or execute arbitrary model output.

## Provider model

The first implementation supports a provider-neutral interface and fake provider. Real provider adapters require explicit user configuration and separate acceptance evidence. No API key is committed to source, Gradle properties, logs, backup files, or screenshots.

## Consent

Before sending sensitive context, show provider, model, fields/categories, and a clear confirm action. Default context is the minimum text needed for interpretation. A user may choose a student explicitly; the system must not infer family details.

## Action safety

- Read-only search: no write confirmation, but disclose provider use when remote interpretation occurs.
- New draft: preview and confirmation.
- Score, parent contact, communication, archive, export, attachment, or bulk action: explicit confirmation with affected entity/field summary.
- Delete/permanent cleanup/send message: blocked from autonomous Copilot execution in 2.0 alpha.

## Rollback

Disable the Copilot feature flag and retain all 1.x navigation/repositories. Provider adapters can be removed without Room migration. If a schema is added for settings/audit, use explicit migrations and preserve user data.
