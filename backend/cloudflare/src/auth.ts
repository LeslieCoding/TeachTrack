import { asHex, first, run, sha256Hex } from "./db";
import { HttpError, parseJson } from "./http";
import type { Env, Principal, Row } from "./types";

export async function authenticate(request: Request, env: Env): Promise<Principal> {
  const header = request.headers.get("authorization") ?? "";
  if (!header.startsWith("Bearer ")) {
    throw new HttpError(401, "UNAUTHORIZED", "缺少 Bearer Token");
  }
  const token = header.slice(7).trim();
  if (!token) throw new HttpError(401, "UNAUTHORIZED", "Token 为空");

  if (env.BOOTSTRAP_TOKEN && token === env.BOOTSTRAP_TOKEN) {
    return { userId: "bootstrap", tokenId: "bootstrap", scopes: ["owner", "ai.read", "ai.write.draft"] };
  }

  const rows = await env.DB.prepare(
    `select id, user_id, token_hash, scopes, expires_at
     from api_tokens where token_prefix = ? and revoked_at is null`,
  ).bind(token.slice(0, 11)).all<Row>();
  const actual = await sha256Hex(token);
  const match = (rows.results ?? []).find((row) => asHex(row.token_hash) === actual);
  if (!match) throw new HttpError(401, "UNAUTHORIZED", "Token 无效");
  if (match.expires_at && Date.parse(String(match.expires_at)) <= Date.now()) {
    throw new HttpError(401, "UNAUTHORIZED", "Token 已过期");
  }
  await run(env, "update api_tokens set last_used_at = ? where id = ?", new Date().toISOString(), match.id);
  return {
    userId: String(match.user_id),
    tokenId: String(match.id),
    scopes: (parseJson(match.scopes, []) as string[]) ?? [],
  };
}
