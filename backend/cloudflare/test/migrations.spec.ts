import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import schema from "../migrations/0001_init.sql?raw";
import type { Env } from "../src/types";

const testEnv = env as unknown as Env;

async function migrate(data: unknown, dryRun = false, format?: string): Promise<any> {
  const response = await worker.fetch(new Request("http://test.local/api/v1/admin/migrations", {
    method: "POST",
    headers: { authorization: "Bearer test-teachtrack-token", "content-type": "application/json" },
    body: JSON.stringify({ data, dryRun, format, sourceName: "test.json" }),
  }), testEnv);
  expect(response.status).toBe(200);
  return await response.json();
}

beforeAll(async () => {
  const statements = schema.split("-- updated_at maintenance")[0]
    .replace(/^\s*--.*$/gm, "").split(/;\s*(?:\r?\n|$)/)
    .map((statement) => statement.trim()).filter(Boolean);
  await testEnv.DB.batch(statements.map((statement) => testEnv.DB.prepare(statement)));
});

describe("JSON migration API on Workers", () => {
  it("dry-run changes nothing and TTSEED import is idempotent", async () => {
    const seed = {
      format: "TeachTrack .ttseed",
      students: [{
        id: "tt-001", displayName: "测试甲", fullName: "测试甲", alias: ["甲", "小甲"],
        status: "ACTIVE", gender: "男", grade: "八年级", subject: "初中科学",
        parentContact: { name: "家长甲", phone: "13800000000", relation: "母亲" },
        academicProfile: { level: "中等" }, learningCharacteristics: { style: "需要复述" },
        strengths: ["计算"], weaknesses: ["液体压强"], specialRules: ["先复习再新授"],
        knowledgeProgress: [{ name: "液体压强", level: 2, notes: "课堂讲过" }],
        courseSessions: [{ id: "tt-c-1", date: "2026-09-01", title: "液体压强", content: "公式与实验" }],
        coursePlans: [{ plannedDate: "2026-09-08", focus: "巩固液体压强" }],
        scores: [{ examName: "周测", date: "2026-09-05", score: 72, fullScore: 100 }],
      }, {
        id: "tt-002", displayName: "测试乙", alias: ["乙"], status: "PAUSED", grade: "九年级", subject: "初中科学",
        courseSessions: [{ id: "tt-c-2", date: "2026-09-02", title: "酸碱盐", content: "复分解反应" }],
      }],
    };
    const dry = await migrate(seed, true, "TTSEED");
    expect(dry).toMatchObject({ dryRun: true, students: 2, courses: 2, scores: 1 });
    expect((await testEnv.DB.prepare("select count(*) as n from students").first<{ n: number }>())?.n).toBe(0);
    expect((await testEnv.DB.prepare("select count(*) as n from import_jobs").first<{ n: number }>())?.n).toBe(0);

    const first = await migrate(seed, false, "TTSEED");
    expect(first).toMatchObject({ students: 2, courses: 2, scores: 1, knowledgePoints: 1 });
    await migrate(seed, false, "TTSEED");
    const checks: Record<string, number> = {};
    for (const table of ["students", "course_sessions", "student_aliases", "parent_contacts", "student_profiles", "lesson_plans"]) {
      checks[table] = (await testEnv.DB.prepare(`select count(*) as n from ${table}`).first<{ n: number }>())!.n;
    }
    expect(checks).toEqual({ students: 2, course_sessions: 2, student_aliases: 3, parent_contacts: 1, student_profiles: 2, lesson_plans: 1 });
    expect((await testEnv.DB.prepare("select count(*) as n from import_jobs where status = 'COMPLETED'").first<{ n: number }>())?.n).toBe(2);
  });

  it("imports backup JSON with scores, courses and problems", async () => {
    const backup = {
      format: "TeachTrack Backup",
      students: [{ id: "b-1", name: "备份学生", displayName: "备份", status: "ACTIVE", subject: "初中科学", grade: "八年级" }],
      exams: [{ id: "e-1", name: "期中", date: "2026-04-20", subject: "初中科学" }],
      courses: [{ id: "c-1", studentId: "b-1", date: "2026-04-21", title: "浮力", content: "复习" }],
      scores: [{ id: "s-1", studentId: "b-1", examId: "e-1", score: 88, fullScore: 100 }],
      problemRecords: [{ id: "p-1", studentId: "b-1", title: "浮力计算", description: "单位错误", date: "2026-04-21", status: "待复习" }],
    };
    const result = await migrate(backup, false, "BACKUP_JSON");
    expect(result).toMatchObject({ students: 1, courses: 1, scores: 1, problems: 1 });
    expect((await testEnv.DB.prepare("select score from student_scores where legacy_id = 's-1'").first<{ score: number }>())?.score).toBe(88);
    await migrate(backup, false, "BACKUP_JSON");
    expect((await testEnv.DB.prepare("select count(*) as n from students where legacy_id = 'b-1'").first<{ n: number }>())?.n).toBe(1);
    expect((await testEnv.DB.prepare("select count(*) as n from student_scores where legacy_id = 's-1'").first<{ n: number }>())?.n).toBe(1);
  });

  it("imports SEED_V1 knowledge groups with deterministic ids", async () => {
    const seed = {
      students: [{
        id: "seed-test-1", fullName: "种子学生", subject: "初中科学", historicalGrade: "八年级",
        knowledge: ["物理/液体压强"], weaknesses: ["压强"],
        courses: [{ recordDate: "2026/9/1", title: "压强复习", content: "公式" }],
        scores: [{ type: "周测", exactDate: "2026-09-02", score: 80, fullScore: 100 }],
      }],
      unresolvedHistoricalRecords: [{ id: "u-1", recordDate: "2026-09-01", title: "旧资料", content: "内容" }],
    };
    const result = await migrate(seed, false, "SEED_V1");
    expect(result).toMatchObject({ students: 1, courses: 1, scores: 1, knowledgePoints: 2 });
    const point = await testEnv.DB.prepare("select count(*) as n from knowledge_points where name = '液体压强' and parent_id is not null").first<{ n: number }>();
    expect(point?.n).toBe(1);
    expect((await testEnv.DB.prepare("select count(*) as n from unresolved_historical_records where legacy_id = 'u-1'").first<{ n: number }>())?.n).toBe(1);
  });
});
