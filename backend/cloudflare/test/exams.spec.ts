import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import schema from "../migrations/0001_init.sql?raw";
import type { Env } from "../src/types";

const testEnv = env as unknown as Env;
const subjectId = "5681f185-310d-52e4-964c-be9d8cf1f5a6";
const studentId = "44444444-4444-4444-8444-444444444444";

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
  await testEnv.DB.prepare("insert into students (id, display_name, status, subject_id) values (?, '试卷测试学生', 'ACTIVE', ?)")
    .bind(studentId, subjectId).run();
  await testEnv.DB.prepare("insert into knowledge_points (id, subject_id, name, code, grade) values (?, ?, '液体压强', 'EXAM-KP-1', '八年级')")
    .bind("55555555-5555-4555-8555-555555555555", subjectId).run();
  await testEnv.DB.prepare("insert into course_sessions (id, student_id, date, lesson_title, lesson_content) values (?, ?, '2026-09-01', '液体压强复习', '液体压强公式与实验')")
    .bind("66666666-6666-4666-8666-666666666666", studentId).run();
});

describe("exam analysis on D1", () => {
  it("imports, analyzes, teacher-corrects and approves into the bank", async () => {
    const imported = await api("/api/v1/exams/import", {
      method: "POST",
      body: JSON.stringify({ studentId, subjectId, examName: "十月月考", examDate: "2026-10-20", fullScore: 6, score: 3 }),
    });
    expect(imported.status).toBe(201);
    const paperId = (await imported.json() as any).id;
    const analyzed = await api(`/api/v1/exams/${paperId}/analyze`, {
      method: "POST",
      body: JSON.stringify({ questions: [
        { number: "1", type: "SINGLE_CHOICE", score: 2, fullScore: 2, studentAnswer: "A", standardAnswer: "A", stem: "液体压强选择题" },
        { number: "2", type: "CALCULATION", score: 1, fullScore: 4, studentAnswer: "", standardAnswer: "p=ρgh", stem: "液体压强计算题" },
      ] }),
    });
    expect(analyzed.status).toBe(200);
    const detail = await analyzed.json() as any;
    expect(detail.paper.analysisStatus).toBe("AI_PARSED");
    expect(detail.questions).toHaveLength(2);
    expect(detail.analyses[0]).toMatchObject({ taughtBefore: true, mistakeType: "UNKNOWN" });
    expect(detail.analyses[1]).toMatchObject({ taughtBefore: true, mistakeType: "UNKNOWN" });
    expect(detail.reportJson).toContain("wrongCount");

    const examQuestionId = detail.questions[1].id;
    const corrected = await api(`/api/v1/exams/${paperId}/questions/${examQuestionId}`, {
      method: "PATCH",
      body: JSON.stringify({ score: 3, teacherComment: "步骤不完整", mistakeType: "CALCULATION_ERROR", mistakeReason: "单位换算错误" }),
    });
    expect(corrected.status).toBe(200);
    expect((await corrected.json() as any).analyses[1].mistakeType).toBe("CALCULATION_ERROR");

    const approved = await api(`/api/v1/exams/${paperId}/approve?importToBank=true`, { method: "POST" });
    expect(approved.status).toBe(200);
    const approvedDetail = await approved.json() as any;
    expect(approvedDetail.paper.analysisStatus).toBe("APPROVED");
    expect(approvedDetail.questions.every((question: any) => question.questionId)).toBe(true);
    const exams = await api(`/api/v1/students/${studentId}/exams`);
    expect((await exams.json() as any[])).toHaveLength(1);
  });

  it("fails honestly when no question can be parsed", async () => {
    const imported = await api("/api/v1/exams/import", {
      method: "POST", body: JSON.stringify({ studentId, subjectId, examName: "空白扫描件" }),
    });
    const paperId = (await imported.json() as any).id;
    const response = await api(`/api/v1/exams/${paperId}/analyze`, { method: "POST", body: JSON.stringify({ text: "没有题号的内容" }) });
    expect(response.status).toBe(400);
    expect((await response.json() as any).error.code).toBe("NO_QUESTIONS");
    expect((await api(`/api/v1/exams/${paperId}/analysis`)).status).toBe(200);
  });
});
