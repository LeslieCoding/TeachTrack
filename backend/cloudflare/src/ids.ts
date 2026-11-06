const NAMESPACE = "6f1c2f8a-4c1e-5b7a-9d3e-7a2b1c9d0e4f";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export async function uuidV5(kind: string, legacyId: string): Promise<string> {
  const namespace = new Uint8Array(16);
  NAMESPACE.replaceAll("-", "").match(/.{2}/g)?.forEach((pair, index) => {
    namespace[index] = Number.parseInt(pair, 16);
  });
  const suffix = new TextEncoder().encode(`${kind}:${legacyId}`);
  const input = new Uint8Array(namespace.length + suffix.length);
  input.set(namespace);
  input.set(suffix, namespace.length);
  const hash = new Uint8Array(await crypto.subtle.digest("SHA-1", input));
  hash[6] = (hash[6] & 0x0f) | 0x50;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const hex = [...hash.slice(0, 16)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export async function fromLegacy(kind: string, legacyId: unknown): Promise<string> {
  const value = legacyId === null || legacyId === undefined ? "" : String(legacyId).trim();
  if (!value) return crypto.randomUUID();
  return UUID_PATTERN.test(value) ? value : await uuidV5(kind, value);
}

export function snake(camel: string): string {
  return camel.replace(/([a-z])([A-Z])/g, "$1_$2").toLowerCase();
}
