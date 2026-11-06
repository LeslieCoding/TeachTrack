import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import schema from "../migrations/0001_init.sql?raw";
import type { Env } from "../src/types";

const testEnv = env as unknown as Env;
const deviceId = "33333333-3333-4333-8333-333333333333";
let studentId = "";

async function api(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("authorization", "Bearer test-teachtrack-token");
  if (init.body) headers.set("content-type", "application/json");
  return await worker.fetch(new Request(`http://test.local${path}`, { ...init, headers }), testEnv);
}

async function push(operation: Record<string, unknown>): Promise<any> {
  const response = await api("/api/v1/sync/operations", {
    method: "POST",
    body: JSON.stringify({ deviceId, operations: [operation] }),
  });
  expect(response.status).toBe(200);
  return await response.json();
}

beforeAll(async () => {
  const statements = schema.split("-- updated_at maintenance")[0]
    .replace(/^\s*--.*$/gm, "").split(/;\s*(?:\r?\n|$)/)
    .map((statement) => statement.trim()).filter(Boolean);
  await testEnv.DB.batch(statements.map((statement) => testEnv.DB.prepare(statement)));
});

describe("D1 incremental sync", () => {
  it("upserts a student by stable legacy id and is idempotent", async () => {
    const operation = {
      operationId: "op-student-1", entityType: "student", entityId: "student-legacy-1",
      operation: "UPSERT", baseVersion: 0,
      payload: { legacyId: "student-legacy-1", displayName: "王晨然", status: "ACTIVE", currentGrade: "初二" },
    };
    const first = await push(operation);
    expect(first.applied[0]).toMatchObject({ status: "APPLIED", version: 1 });
    studentId = first.applied[0].entityId;
    const second = await push(operation);
    expect(second.applied[0]).toMatchObject({ status: "APPLIED", version: 1 });
    const count = await testEnv.DB.prepare("select count(*) as n from students").first<{ n: number }>();
    expect(count?.n).toBe(1);
  });

  it("adds a course session linked to the student", async () => {
    const result = await push({
      operationId: "op-course-1", entityType: "courseSession", entityId: "course-legacy-1", baseVersion: 0,
      payload: { legacyId: "course-legacy-1", studentId, date: "2026-11-06", lessonTitle: "电功率" },
    });
    expect(result.applied[0].status).toBe("APPLIED");
    const sessions = await api(`/api/v1/students/${studentId}/sessions`);
    expect((await sessions.json() as any[])[0].lessonTitle).toBe("电功率");
  });

  it("returns 409-style conflict choices in the sync result", async () => {
    const remote = await push({
      operationId: "op-student-2", entityType: "student", entityId: "student-legacy-1", baseVersion: 1,
      payload: { displayName: "王晨然（云端）" },
    });
    expect(remote.applied[0].version).toBe(2);
    const local = await push({
      operationId: "op-student-3", entityType: "student", entityId: "student-legacy-1", baseVersion: 1,
      payload: { displayName: "王晨然（本地）" },
    });
    expect(local.applied).toHaveLength(0);
    expect(local.conflicts[0]).toMatchObject({
      entityId: studentId, baseVersion: 1, currentVersion: 2,
      choices: ["KEEP_LOCAL", "KEEP_REMOTE", "MANUAL_MERGE"],
    });
    expect(local.conflicts[0].remote.displayName).toBe("王晨然（云端）");
  });

  it("supports explicit force for KEEP_LOCAL and exposes changes", async () => {
    const forced = await push({
      operationId: "op-student-4", entityType: "student", entityId: "student-legacy-1", baseVersion: 1, force: true,
      payload: { displayName: "王晨然（本地）" },
    });
    expect(forced.applied[0].version).toBe(3);
    const changes = await api("/api/v1/sync/changes?cursor=0&limit=100");
    const page = await changes.json() as any;
    expect(page.changes.length).toBeGreaterThanOrEqual(4);
    expect(page.nextCursor).toBeGreaterThan(0);
    expect(page.changes[0].payload.id).toBe(studentId);
  });
});
