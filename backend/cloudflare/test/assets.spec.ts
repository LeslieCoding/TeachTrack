import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import schema from "../migrations/0001_init.sql?raw";
import type { Env } from "../src/types";

const testEnv = env as unknown as Env;

async function api(path: string, init: RequestInit = {}, authenticated = true): Promise<Response> {
  const headers = new Headers(init.headers);
  if (authenticated) headers.set("authorization", "Bearer test-teachtrack-token");
  return await worker.fetch(new Request(`http://test.local${path}`, { ...init, headers }), testEnv);
}

beforeAll(async () => {
  const statements = schema.split("-- updated_at maintenance")[0]
    .replace(/^\s*--.*$/gm, "").split(/;\s*(?:\r?\n|$)/)
    .map((statement) => statement.trim()).filter(Boolean);
  await testEnv.DB.batch(statements.map((statement) => testEnv.DB.prepare(statement)));
});

describe("private R2 assets", () => {
  it("uploads and downloads through signed URLs only", async () => {
    const ticketResponse = await api("/api/v1/assets/upload-url", {
      method: "POST",
      body: JSON.stringify({ ownerType: "EXAM", filename: "试卷.pdf", contentType: "application/pdf", sizeBytes: 12 }),
    });
    expect(ticketResponse.status).toBe(200);
    const ticket = await ticketResponse.json() as any;
    expect(ticket.method).toBe("PUT");
    const uploadUrl = new URL(ticket.uploadUrl);
    const uploaded = await api(uploadUrl.pathname + uploadUrl.search, {
      method: "PUT", body: new TextEncoder().encode("real pdf content"),
    }, false);
    expect(uploaded.status).toBe(204);

    const downloadTicketResponse = await api(`/api/v1/assets/${ticket.assetId}/download-url`);
    const downloadTicket = await downloadTicketResponse.json() as any;
    const downloadUrl = new URL(downloadTicket.url);
    const downloaded = await api(downloadUrl.pathname + downloadUrl.search, {}, false);
    expect(downloaded.status).toBe(200);
    expect(downloaded.headers.get("content-type")).toBe("application/pdf");
    expect(new TextDecoder().decode(await downloaded.arrayBuffer())).toBe("real pdf content");

    const tampered = new URL(downloadTicket.url);
    tampered.searchParams.set("sig", "0".repeat(64));
    expect((await api(tampered.pathname + tampered.search, {}, false)).status).toBe(403);
  });
});
