import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import schema from "../migrations/0001_init.sql?raw";
import type { Env } from "../src/types";

const testEnv = env as unknown as Env;
const subjectId = "5681f185-310d-52e4-964c-be9d8cf1f5a6";
const studentId = "77777777-7777-4777-8777-777777777777";
const knowledgeId = "88888888-8888-4888-8888-888888888888";

async function api(path: string, init: RequestInit = {}, authenticated = true): Promise<Response> {
  const headers = new Headers(init.headers);
  if (authenticated) headers.set("authorization", "Bearer test-teachtrack-token");
  if (init.body) headers.set("content-type", "application/json");
  return await worker.fetch(new Request(`http://test.local${path}`, { ...init, headers }), testEnv);
}

beforeAll(async () => {
  const statements = schema.split("-- updated_at maintenance")[0]
    .replace(/^\s*--.*$/gm, "").split(/;\s*(?:\r?\n|$)/)
    .map((statement) => statement.trim()).filter(Boolean);
  await testEnv.DB.batch(statements.map((statement) => testEnv.DB.prepare(statement)));
  await testEnv.DB.prepare("insert into students (id, display_name, status, subject_id) values (?, '练习测试学生', 'ACTIVE', ?)")
    .bind(studentId, subjectId).run();
  await testEnv.DB.prepare("insert into knowledge_points (id, subject_id, name, code) values (?, ?, '电功率', 'PRACTICE-KP-1')")
    .bind(knowledgeId, subjectId).run();
});

describe("practice and mastery on D1", () => {
  it("generates only from approved questions, submits and recomputes mastery", async () => {
    const created = await api("/api/v1/questions", {
      method: "POST",
      body: JSON.stringify({
        subjectId, questionType: "FILL_BLANK", difficulty: 3, stem: "电功率的计算公式是什么？",
        answer: { standardAnswer: "P=UI" }, knowledge: [{ knowledgePointId: knowledgeId, importance: "PRIMARY" }],
      }),
    });
    const question = await created.json() as any;
    const insufficient = await api("/api/v1/practices/generate", {
      method: "POST", body: JSON.stringify({ studentId, subjectId, knowledgePointIds: [knowledgeId], questionCount: 1 }),
    });
    expect(insufficient.status).toBe(400);
    expect((await insufficient.json() as any).error.code).toBe("INSUFFICIENT_QUESTIONS");
    await api(`/api/v1/questions/${question.question.id}/review`, { method: "POST", body: JSON.stringify({ status: "APPROVED" }) });

    const generated = await api("/api/v1/practices/generate", {
      method: "POST",
      body: JSON.stringify({ studentId, subjectId, knowledgePointIds: [knowledgeId], questionCount: 1, totalScore: 10, mode: "TOPIC" }),
    });
    expect(generated.status).toBe(201);
    const practice = await generated.json() as any;
    expect(practice.set.status).toBe("DRAFT");
    expect(practice.items).toHaveLength(1);
    expect(practice.items[0].selectionReason).toContain("已审核题库");
    const approved = await api(`/api/v1/practices/${practice.set.id}/approve`, { method: "POST" });
    expect((await approved.json() as any).set.status).toBe("APPROVED");

    const submitted = await api(`/api/v1/practices/${practice.set.id}/submit`, {
      method: "POST",
      body: JSON.stringify({ answers: [{ itemId: practice.items[0].id, studentAnswer: " P = U I " }] }),
    });
    expect(submitted.status).toBe(200);
    const completed = await submitted.json() as any;
    expect(completed.set.status).toBe("COMPLETED");
    expect(completed.mastery).toHaveLength(1);
    expect(completed.mastery[0].correctCount).toBe(1);
    expect(completed.mastery[0].masteryScore).toBeGreaterThan(0);
    const list = await api(`/api/v1/students/${studentId}/practices`);
    expect((await list.json() as any[])).toHaveLength(1);
  });

  it("exports real Chinese PDF and DOCX files through signed downloads", async () => {
    const set = await testEnv.DB.prepare("select id from practice_sets order by created_at desc limit 1").first<{ id: string }>();
    expect(set?.id).toBeTruthy();
    for (const [format, variant] of [["PDF", "TEACHER"], ["DOCX", "STUDENT"]] as const) {
      const response = await api(`/api/v1/practices/${set!.id}/export?format=${format}&variant=${variant}`);
      expect(response.status).toBe(200);
      const exported = await response.json() as any;
      expect(exported.format).toBe(format);
      expect(exported.variant).toBe(variant);
      const download = new URL(exported.downloadUrl);
      const content = await api(download.pathname + download.search, {}, false);
      expect(content.status).toBe(200);
      const bytes = new Uint8Array(await content.arrayBuffer());
      expect(bytes.length).toBeGreaterThan(1000);
      if (format === "PDF") {
        expect([...bytes.slice(0, 4)]).toEqual([0x25, 0x50, 0x44, 0x46]);
      } else {
        expect([...bytes.slice(0, 2)]).toEqual([0x50, 0x4b]);
      }
    }
  });
});
