import { audit } from "../audit";
import { first, run, sha256Hex, text, uuid } from "../db";
import { badRequest, camelize, HttpError, json, noContent, notFound } from "../http";
import type { Env, Principal, Row } from "../types";

const MAX_UPLOAD_BYTES = 50 * 1024 * 1024;
const SIGNED_PRINCIPAL: Principal = { userId: "signed-url", tokenId: "signed-url", scopes: [] };

export async function handleAssets(
  request: Request,
  env: Env,
  url: URL,
  principal: Principal | null,
): Promise<Response | null> {
  const path = url.pathname;
  if (!path.startsWith("/api/v1/assets/")) return null;

  const upload = path.match(/^\/api\/v1\/assets\/upload\/([^/]+)$/);
  if (upload && request.method === "PUT") {
    const key = decodeKey(upload[1]);
    const expiresAt = Number(url.searchParams.get("exp"));
    const signature = text(url.searchParams.get("sig"));
    await verifySignature(env, "upload", key, expiresAt, signature);
    const asset = await first(env, "select * from assets where storage_key = ?", key);
    if (!asset) return notFound("资源记录不存在");
    const data = await request.arrayBuffer();
    if (!data.byteLength) return badRequest("EMPTY_FILE", "上传内容为空");
    if (data.byteLength > MAX_UPLOAD_BYTES) return badRequest("FILE_TOO_LARGE", "最大允许 50 MB");
    const bucket = requireBucket(env);
    try {
      await bucket.put(key, data, { httpMetadata: { contentType: text(asset.mime_type, "application/octet-stream") } });
      const digest = await crypto.subtle.digest("SHA-256", data);
      const sha256 = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
      await run(env,
        "update assets set status = 'READY', size_bytes = ?, sha256 = ?, updated_at = ? where id = ?",
        data.byteLength, sha256, new Date().toISOString(), asset.id);
      await audit(env, SIGNED_PRINCIPAL, "asset.upload", "Asset", text(asset.id));
      return noContent();
    } catch (error) {
      await run(env, "update assets set status = 'FAILED', updated_at = ? where id = ?", new Date().toISOString(), asset.id);
      throw error;
    }
  }

  const download = path.match(/^\/api\/v1\/assets\/download\/([^/]+)$/);
  if (download && request.method === "GET") {
    const key = decodeKey(download[1]);
    await verifySignature(env, "download", key, Number(url.searchParams.get("exp")), text(url.searchParams.get("sig")));
    const asset = await first(env, "select * from assets where storage_key = ?", key);
    if (!asset) return notFound("资源记录不存在");
    const object = await requireBucket(env).get(key);
    if (!object) return notFound("资源文件不存在");
    await audit(env, SIGNED_PRINCIPAL, "asset.download", "Asset", text(asset.id));
    return new Response(object.body, {
      headers: {
        "content-type": text(asset.mime_type, "application/octet-stream"),
        "cache-control": "private, max-age=0, no-store",
      },
    });
  }

  if (!principal) throw new HttpError(401, "UNAUTHORIZED", "需要认证");

  if (path === "/api/v1/assets/upload-url" && request.method === "POST") {
    requireBucket(env);
    const input = await request.json() as Row;
    const sizeBytes = Number(input.sizeBytes ?? 0);
    if (sizeBytes > MAX_UPLOAD_BYTES) return badRequest("FILE_TOO_LARGE", "最大允许 50 MB");
    const ownerType = text(input.ownerType, "general").trim().toLowerCase();
    const ownerId = text(input.ownerId, "unassigned");
    const filename = text(input.filename, "file").trim().replace(/[^A-Za-z0-9._-]/g, "_").slice(0, 80) || "file";
    const key = `${ownerType}/${ownerId}/${uuid()}-${filename}`;
    const contentType = text(input.contentType, "application/octet-stream");
    const assetId = uuid();
    await run(env,
      `insert into assets (id, owner_type, owner_id, storage_provider, storage_key, mime_type, size_bytes, sha256, status)
       values (?, ?, ?, 'r2', ?, ?, 0, '', 'PENDING')`,
      assetId, ownerType, input.ownerId ?? null, key, contentType);
    const expiresAt = Math.floor(Date.now() / 1000) + 600;
    const signature = await sign(env, "upload", key, expiresAt);
    const uploadUrl = `${url.origin}/api/v1/assets/upload/${encodeKey(key)}?exp=${expiresAt}&sig=${signature}`;
    await audit(env, principal, "asset.createUpload", "Asset", assetId);
    return json({ assetId, uploadUrl, method: "PUT", expiresAt: new Date(expiresAt * 1000).toISOString() });
  }

  const item = path.match(/^\/api\/v1\/assets\/([^/]+)\/download-url$/);
  if (item && request.method === "GET") {
    requireBucket(env);
    const asset = await first(env, "select * from assets where id = ?", item[1]);
    if (!asset) return notFound("资源不存在");
    if (asset.status !== "READY") return badRequest("ASSET_NOT_READY", "资源尚未上传完成");
    const key = text(asset.storage_key);
    const expiresAt = Math.floor(Date.now() / 1000) + 600;
    const signature = await sign(env, "download", key, expiresAt);
    const signedUrl = `${url.origin}/api/v1/assets/download/${encodeKey(key)}?exp=${expiresAt}&sig=${signature}`;
    await audit(env, principal, "asset.downloadUrl", "Asset", item[1]);
    return json({ url: signedUrl, method: "GET", expiresAt: new Date(expiresAt * 1000).toISOString() });
  }

  return null;
}

function requireBucket(env: Env): R2Bucket {
  if (!env.FILES) throw new HttpError(503, "STORAGE_NOT_CONFIGURED", "Cloudflare R2 文件桶尚未配置");
  if (!env.STORAGE_SECRET) throw new HttpError(503, "STORAGE_NOT_CONFIGURED", "文件签名密钥尚未配置");
  return env.FILES;
}

async function sign(env: Env, action: string, key: string, expiresAt: number): Promise<string> {
  if (!env.STORAGE_SECRET) throw new HttpError(503, "STORAGE_NOT_CONFIGURED", "文件签名密钥尚未配置");
  const cryptoKey = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(env.STORAGE_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", cryptoKey, new TextEncoder().encode(`${action}:${key}:${expiresAt}`));
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function verifySignature(env: Env, action: string, key: string, expiresAt: number, actual: string): Promise<void> {
  if (!Number.isFinite(expiresAt) || expiresAt * 1000 < Date.now()) {
    throw new HttpError(403, "INVALID_SIGNATURE", "上传或下载链接无效或已过期");
  }
  const expected = await sign(env, action, key, expiresAt);
  if (expected !== actual) throw new HttpError(403, "INVALID_SIGNATURE", "上传或下载链接无效或已过期");
}

export async function signedDownloadUrl(env: Env, origin: string, key: string): Promise<string> {
  const expiresAt = Math.floor(Date.now() / 1000) + 600;
  const signature = await sign(env, "download", key, expiresAt);
  return `${origin}/api/v1/assets/download/${encodeKey(key)}?exp=${expiresAt}&sig=${signature}`;
}

function encodeKey(key: string): string {
  const bytes = new TextEncoder().encode(key);
  let binary = "";
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

function decodeKey(encoded: string): string {
  try {
    const base64 = encoded.replaceAll("-", "+").replaceAll("_", "/");
    const binary = atob(base64);
    return new TextDecoder().decode(Uint8Array.from(binary, (char) => char.charCodeAt(0)));
  } catch {
    return badRequest("INVALID_STORAGE_KEY", "资源键编码无效");
  }
}
