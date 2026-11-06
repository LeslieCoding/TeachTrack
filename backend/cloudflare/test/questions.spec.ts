import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import schema from "../migrations/0001_init.sql?raw";
import type { Env } from "../src/types";

const testEnv = env as unknown as Env;
const subjectId = "5681f185-310d-52e4-964c-be9d8cf1f5a6";
let questionId = "";

async function api(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("authorization", "Bearer test-teachtrack-token");
  if (init.body) headers.set("content-type", "application/json");
  return await worker.fetch(new Request(`http://test.local${path}`, { ...init, headers }), testEnv);
}

beforeAll(async () => {
  const statements = schema.split("-- updated_at maintenance")[0]
    .replace(/^\s*--.*$/gm, "").split(/;\s*(?:\r?\n|$)/)
    .map((statement) => statement.trim()).filter(Boolean);
  await testEnv.DB.batch(statements.map((statement) => testEnv.DB.prepare(statement)));
});

describe("question bank on D1", () => {
  it("creates a question with options, answer, knowledge and tags", async () => {
    const response = await api("/api/v1/questions", {
      method: "POST",
      body: JSON.stringify({
        subjectId,
        grade: "八上",
        questionType: "SINGLE_CHOICE",
        difficulty: 3,
        stem: "液体内部向各个方向的压强有什么关系？",
        sourceType: "SELF_CREATED",
        options: [{ key: "A", content: "相等" }, { key: "B", content: "不相等" }],
        answer: { standardAnswer: "A", analysis: "同种液体同一深度各方向压强相等" },
        knowledge: [],
        abilityTags: ["概念理解"],
      }),
    });
    expect(response.status).toBe(201);
    const detail = await response.json() as any;
    questionId = detail.question.id;
    expect(detail.options).toHaveLength(2);
    expect(detail.answer.standardAnswer).toBe("A");
    expect(detail.abilityTags).toEqual(["概念理解"]);
    expect(detail.question.reviewStatus).toBe("DRAFT");
  });

  it("searches by indexed filters and rejects exact duplicates", async () => {
    const response = await api(`/api/v1/questions?subjectId=${subjectId}&grade=八上&minDifficulty=2&maxDifficulty=4&q=压强`);
    const page = await response.json() as any;
    expect(page.total).toBe(1);
    expect(page.items[0].id).toBe(questionId);
    const duplicate = await api("/api/v1/questions", {
      method: "POST",
      body: JSON.stringify({ subjectId, questionType: "OTHER", difficulty: 3, stem: "液体内部向各个方向的压强有什么关系？" }),
    });
    expect(duplicate.status).toBe(409);
    expect((await duplicate.json() as any).error.code).toBe("DUPLICATE_QUESTION");
  });

  it("returns exact and normalized duplicates before editing or importing", async () => {
    const exact = await api(`/api/v1/questions/duplicates?stem=${encodeURIComponent("液体内部向各个方向的压强有什么关系？")}`);
    expect(exact.status).toBe(200);
    expect((await exact.json() as any).exact.id).toBe(questionId);

    const normalized = await api(`/api/v1/questions/duplicates?stem=${encodeURIComponent("液体内部向各个方向的压强有什么关系")}`);
    const result = await normalized.json() as any;
    expect(result.exact).toBeNull();
    expect(result.near.id).toBe(questionId);
  });

  it("enforces optimistic versions and keeps history", async () => {
    const updated = await api(`/api/v1/questions/${questionId}`, {
      method: "PATCH",
      body: JSON.stringify({ baseVersion: 1, difficulty: 4, reviewStatus: "NEEDS_REVIEW" }),
    });
    expect(updated.status).toBe(200);
    expect((await updated.json() as any).question.version).toBe(2);
    const conflict = await api(`/api/v1/questions/${questionId}`, {
      method: "PATCH", body: JSON.stringify({ baseVersion: 1, difficulty: 5 }),
    });
    expect(conflict.status).toBe(409);
    expect((await conflict.json() as any).error.code).toBe("VERSION_CONFLICT");
    const versions = await testEnv.DB.prepare("select count(*) as n from question_versions where question_id = ?")
      .bind(questionId).first<{ n: number }>();
    expect(versions?.n).toBe(2);
  });

  it("imports fifty questions without creating duplicates on retry", async () => {
    const stems = Array.from({ length: 50 }, (_, index) => `批量导入验收第 ${index + 1} 题`);
    for (const stem of stems) {
      const response = await api("/api/v1/questions", {
        method: "POST",
        body: JSON.stringify({ subjectId, questionType: "OTHER", difficulty: 3, stem, sourceType: "TEACHER_IMPORTED" }),
      });
      expect(response.status).toBe(201);
    }
    for (const stem of stems) {
      const duplicate = await api("/api/v1/questions", {
        method: "POST",
        body: JSON.stringify({ subjectId, questionType: "OTHER", difficulty: 3, stem, sourceType: "TEACHER_IMPORTED" }),
      });
      expect(duplicate.status).toBe(409);
      expect((await duplicate.json() as any).error.code).toBe("DUPLICATE_QUESTION");
    }
    const page = await (await api(`/api/v1/questions?q=${encodeURIComponent("批量导入验收")}&limit=100`)).json() as any;
    expect(page.total).toBe(50);
  });

  it("never auto-approves AI questions and soft-deletes", async () => {
    const ai = await api("/api/v1/questions", {
      method: "POST",
      body: JSON.stringify({ subjectId, questionType: "CALCULATION", difficulty: 2, stem: "AI 原创计算题", sourceType: "AI_GENERATED", reviewStatus: "APPROVED" }),
    });
    expect((await ai.json() as any).question.reviewStatus).toBe("NEEDS_TEACHER_REVIEW");
    const deleted = await api(`/api/v1/questions/${questionId}`, { method: "DELETE" });
    expect(deleted.status).toBe(204);
    expect((await api(`/api/v1/questions/${questionId}`)).status).toBe(404);
    const changes = await api("/api/v1/sync/changes?cursor=0");
    expect((await changes.json() as any).changes.length).toBeGreaterThan(0);
  });
});
