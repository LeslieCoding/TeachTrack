import { run, uuid } from "./db";
import type { Env, Principal } from "./types";

export async function audit(
  env: Env,
  principal: Principal,
  action: string,
  entityType: string,
  entityId: string | null,
  before?: unknown,
  after?: unknown,
): Promise<void> {
  await run(env,
    `insert into audit_logs
     (id, actor_type, actor_id, token_id, action, entity_type, entity_id, before, after, result, created_at)
     values (?, 'USER', ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?)`,
    uuid(), principal.userId, principal.tokenId, action, entityType, entityId,
    before === undefined ? null : JSON.stringify(before),
    after === undefined ? null : JSON.stringify(after), new Date().toISOString(),
  );
}

export async function auditAi(
  env: Env,
  principal: Principal,
  requestId: string,
  action: string,
  entityType: string,
  result: "SUCCESS" | "FAILURE",
  errorCode?: string,
): Promise<void> {
  await run(env,
    `insert into audit_logs
     (id, actor_type, actor_id, token_id, provider, model, request_id, action, entity_type, result, error_code, created_at)
     values (?, 'AI', ?, ?, 'cloudflare-mcp', 'client', ?, ?, ?, ?, ?, ?)`,
    uuid(), principal.userId, principal.tokenId, requestId, action, entityType, result, errorCode ?? null,
    new Date().toISOString(),
  );
}
