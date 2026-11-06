import type { Env, Row } from "./types";

export async function all<T = Row>(env: Env, sql: string, ...params: unknown[]): Promise<T[]> {
  const result = await env.DB.prepare(sql).bind(...params).all<T>();
  if (result.error) throw new Error(result.error);
  return result.results ?? [];
}

export async function first<T = Row>(env: Env, sql: string, ...params: unknown[]): Promise<T | null> {
  return await env.DB.prepare(sql).bind(...params).first<T>();
}

export async function run(env: Env, sql: string, ...params: unknown[]): Promise<D1Result> {
  return await env.DB.prepare(sql).bind(...params).run();
}

export function now(): string {
  return new Date().toISOString();
}

export function uuid(): string {
  return crypto.randomUUID();
}

export function text(value: unknown, fallback = ""): string {
  return value === null || value === undefined ? fallback : String(value);
}

export function nullable(value: unknown): unknown {
  return value === undefined ? null : value;
}

export async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function asHex(value: unknown): string {
  if (typeof value === "string") return value.replace(/^\\x/i, "").toLowerCase();
  if (value instanceof ArrayBuffer) {
    return [...new Uint8Array(value)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  }
  if (ArrayBuffer.isView(value)) {
    const bytes = new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
    return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  }
  return "";
}
