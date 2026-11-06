import type { Row } from "./types";

export class HttpError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export function badRequest(code: string, message: string): never {
  throw new HttpError(400, code, message);
}

export function notFound(message: string): never {
  throw new HttpError(404, "NOT_FOUND", message);
}

export function conflict(code: string, message: string, details?: Row): never {
  throw new HttpError(409, code, JSON.stringify({ message, ...details }));
}

export function json(data: unknown, status = 200, headers: HeadersInit = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...headers },
  });
}

export function noContent(): Response {
  return new Response(null, { status: 204 });
}

export async function body<T>(request: Request): Promise<T> {
  try {
    return (await request.json()) as T;
  } catch {
    return badRequest("INVALID_JSON", "请求体必须是合法 JSON");
  }
}

export function intParam(value: string | null, fallback: number, min: number, max: number): number {
  const parsed = value === null ? fallback : Number.parseInt(value, 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(min, Math.min(parsed, max));
}

export function camelKey(key: string): string {
  return key.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
}

export function camelize(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(camelize);
  if (value && typeof value === "object" && !(value instanceof ArrayBuffer) && !ArrayBuffer.isView(value)) {
    return Object.fromEntries(
      Object.entries(value as Row).map(([key, item]) => [camelKey(key), camelize(item)]),
    );
  }
  return value;
}

export function parseJson(value: unknown, fallback: unknown = null): unknown {
  if (value === null || value === undefined) return fallback;
  if (typeof value !== "string") return value;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}
