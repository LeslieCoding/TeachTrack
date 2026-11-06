import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import schema from "../migrations/0001_init.sql?raw";
import type { Env } from "../src/types";

const token = "test-teachtrack-token";
const studentId = "11111111-1111-4111-8111-111111111111";
const subjectId = "5681f185-310d-52e4-964c-be9d8cf1f5a6";
const testEnv = env as unknown as Env;

async function api(path: string, init: RequestInit = {}, authenticated = true): Promise<Response> {
  const headers = new Headers(init.headers);
  if (authenticated) headers.set("authorization", `Bearer ${token}`);
  if (init.body) headers.set("content-type", "application/json");
  const request = new Request(`http://test.local${path}`, { ...init, headers });
  return await worker.fetch(request, testEnv);
}

beforeAll(async () => {
  const statements = schema.split("-- updated_at maintenance")[0]
    .replace(/^\s*--.*$/gm, "")
    .split(/;\s*(?:\r?\n|$)/)
    .map((statement) => statement.trim())
    .filter(Boolean);
  await testEnv.DB.batch(statements.map((statement) => testEnv.DB.prepare(statement)));
  await testEnv.DB.prepare(
    `insert into students
     (id, legacy_id, display_name, full_name, status, current_grade, subject_id, updated_at)
     values (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)`,
  ).bind(studentId, "legacy-student-1", "蒋欣阳", "蒋欣阳", "初二", subjectId, "2026-11-06T03:00:00.000Z").run();
  await testEnv.DB.prepare(
    `insert into course_sessions
     (id, student_id, date, lesson_title, lesson_content, updated_at)
     values (?, ?, ?, ?, ?, ?)`,
  ).bind("22222222-2222-4222-8222-222222222222", studentId, "2026-11-05", "液体压强", "复习液体压强", "2026-11-05T12:00:00.000Z").run();
});

describe("Cloudflare core API", () => {
  it("exposes public health and rejects unauthenticated API calls", async () => {
    const health = await api("/actuator/health", {}, false);
    expect(health.status).toBe(200);
    expect(await health.json()).toEqual({ status: "UP" });
    expect((await api("/api/v1/students", {}, false)).status).toBe(401);
  });

  it("lists and finds students with the existing JSON contract", async () => {
    const response = await api("/api/v1/students?status=ACTIVE&q=蒋欣阳");
    expect(response.status).toBe(200);
    const page = await response.json() as any;
    expect(page.total).toBe(1);
    expect(page.items[0]).toMatchObject({
      id: studentId,
      legacyId: "legacy-student-1",
      displayName: "蒋欣阳",
      currentGrade: "初二",
      subjectId,
    });
    const byLegacy = await api("/api/v1/students/legacy-student-1");
    expect(byLegacy.status).toBe(200);
    expect((await byLegacy.json() as any).id).toBe(studentId);
  });

  it("returns course sessions and subjects", async () => {
    const sessions = await api(`/api/v1/students/${studentId}/sessions`);
    const items = await sessions.json() as any[];
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({ lessonTitle: "液体压强", studentId });
    const subjects = await api("/api/v1/subjects");
    expect((await subjects.json() as any[])).toHaveLength(4);
  });

  it("creates and searches knowledge points", async () => {
    const created = await api("/api/v1/knowledge-points", {
      method: "POST",
      body: JSON.stringify({ subjectId, name: "液体压强", grade: "八上", chapter: "压力与压强" }),
    });
    expect(created.status).toBe(201);
    const point = await created.json() as any;
    expect(point.code).toMatch(/^KP-/);
    const found = await api(`/api/v1/knowledge-points?subjectId=${subjectId}&q=压强`);
    expect((await found.json() as any[]).map((item) => item.id)).toContain(point.id);
  });

  it("reports D1 and returns an empty sync cursor", async () => {
    const database = await api("/api/v1/system/database");
    expect((await database.json() as any)).toMatchObject({ engine: "sqlite", mode: "CLOUD_OR_REMOTE", ssl: true });
    const sync = await api("/api/v1/sync/changes?cursor=0");
    expect(await sync.json()).toEqual({ changes: [], nextCursor: 0, hasMore: false });
  });
});
