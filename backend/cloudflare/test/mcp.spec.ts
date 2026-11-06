import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import schema from "../migrations/0001_init.sql?raw";
import type { Env } from "../src/types";

const testEnv = env as unknown as Env;

async function rpc(method: string, params: unknown = {}): Promise<any> {
  const response = await worker.fetch(new Request("http://test.local/api/v1/mcp", {
    method: "POST",
    headers: { authorization: "Bearer test-teachtrack-token", "content-type": "application/json" },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method, params }),
  }), testEnv);
  expect(response.status).toBe(200);
  return await response.json();
}

beforeAll(async () => {
  const statements = schema.split("-- updated_at maintenance")[0]
    .replace(/^\s*--.*$/gm, "").split(/;\s*(?:\r?\n|$)/)
    .map((statement) => statement.trim()).filter(Boolean);
  await testEnv.DB.batch(statements.map((statement) => testEnv.DB.prepare(statement)));
  await testEnv.DB.prepare("insert into students (id, legacy_id, display_name, status) values ('99999999-9999-4999-8999-999999999999', 'mcp-student', '程睿琦', 'ACTIVE')").run();
});

describe("MCP over Cloudflare Workers", () => {
  it("lists only safe business tools and calls search_students", async () => {
    const initialized = await rpc("initialize");
    expect(initialized.result.serverInfo.name).toBe("teachtrack");
    const list = await rpc("tools/list");
    expect(list.result.tools).toHaveLength(11);
    expect(list.result.tools.map((tool: any) => tool.name)).not.toContain("database_reset");
    expect(list.result.tools.map((tool: any) => tool.name)).not.toContain("delete_student");
    const called = await rpc("tools/call", { name: "search_students", arguments: { query: "程睿琦" } });
    expect(called.result.isError).toBe(false);
    const students = JSON.parse(called.result.content[0].text);
    expect(students[0].displayName).toBe("程睿琦");
    expect(students[0].phone).toBeUndefined();
    const audit = await testEnv.DB.prepare("select actor_type, action from audit_logs where action = 'mcp.search_students'").first();
    expect(audit).toMatchObject({ actor_type: "AI", action: "mcp.search_students" });
  });

  it("returns JSON-RPC errors for unknown tools", async () => {
    const response = await rpc("tools/call", { name: "database_reset", arguments: {} });
    expect(response.error.code).toBe(-32000);
    expect(response.error.message).toContain("Unknown tool");
  });
});
