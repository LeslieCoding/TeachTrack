import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import schema from "../migrations/0001_init.sql?raw";
import type { Env } from "../src/types";

const testEnv = env as unknown as Env;
const subjectId = "5681f185-310d-52e4-964c-be9d8cf1f5a6";
const studentId = "aaaaaaaa-1111-4111-8111-111111111111";
const PNG = Uint8Array.from(atob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="), (char) => char.charCodeAt(0));

async function api(path: string, init: RequestInit = {}, authenticated = true): Promise<Response> {
  const headers = new Headers(init.headers);
  if (authenticated) headers.set("authorization", "Bearer test-teachtrack-token");
  if (init.body && !headers.has("content-type")) headers.set("content-type", "application/json");
  return await worker.fetch(new Request(`http://test.local${path}`, { ...init, headers }), testEnv);
}

beforeAll(async () => {
  delete (testEnv as { FILES?: R2Bucket }).FILES;
  const statements = schema.split("-- updated_at maintenance")[0]
    .replace(/^\s*--.*$/gm, "").split(/;\s*(?:\r?\n|$)/)
    .map((statement) => statement.trim()).filter(Boolean);
  await testEnv.DB.batch(statements.map((statement) => testEnv.DB.prepare(statement)));
  await testEnv.DB.prepare("insert into students (id, display_name, status, subject_id) values (?, '配图测试学生', 'ACTIVE', ?)")
    .bind(studentId, subjectId).run();
});

describe("question images through KV", () => {
  it("blocks approval without image, stores KV image, and embeds it in exports", async () => {
    const created = await api("/api/v1/questions", {
      method: "POST",
      body: JSON.stringify({
        subjectId, grade: "八上", questionType: "SINGLE_CHOICE", difficulty: 3,
        stem: "如图所示，电路中的电流表示数是多少？（　　）",
        sourceType: "TEACHER_IMPORTED", abilityTags: ["含图待补"],
        options: [{ key: "A", content: "0.5 A" }, { key: "B", content: "1 A" }],
        answer: { standardAnswer: "A" },
      }),
    });
    expect(created.status).toBe(201);
    const questionId = (await created.json() as any).question.id;

    const blocked = await api(`/api/v1/questions/${questionId}/review`, { method: "POST", body: JSON.stringify({ status: "APPROVED" }) });
    expect(blocked.status).toBe(400);
    expect((await blocked.json() as any).error.code).toBe("IMAGE_REQUIRED");

    const uploaded = await api(`/api/v1/questions/${questionId}/image`, {
      method: "POST", headers: { "content-type": "image/png" }, body: PNG,
    });
    expect(uploaded.status).toBe(201);
    const image = await uploaded.json() as any;
    expect(image.url).toContain("/api/v1/assets/download/");

    const listed = await api(`/api/v1/questions/${questionId}/images`);
    const images = await listed.json() as any[];
    expect(images).toHaveLength(1);
    const signed = new URL(images[0].url);
    const downloaded = await api(signed.pathname + signed.search, {}, false);
    expect(downloaded.status).toBe(200);
    expect([...new Uint8Array(await downloaded.arrayBuffer()).slice(0, 4)]).toEqual([0x89, 0x50, 0x4e, 0x47]);

    const approved = await api(`/api/v1/questions/${questionId}/review`, { method: "POST", body: JSON.stringify({ status: "APPROVED" }) });
    expect(approved.status).toBe(200);
    expect((await approved.json() as any).abilityTags).not.toContain("含图待补");

    const practice = await api("/api/v1/practices/generate", {
      method: "POST", body: JSON.stringify({ studentId, subjectId, questionCount: 1, totalScore: 5 }),
    });
    expect(practice.status).toBe(201);
    const practiceId = (await practice.json() as any).set.id;

    for (const format of ["PDF", "DOCX"]) {
      const exported = await api(`/api/v1/practices/${practiceId}/export?format=${format}&variant=TEACHER`);
      expect(exported.status).toBe(200);
      const result = await exported.json() as any;
      const download = new URL(result.downloadUrl);
      const content = await api(download.pathname + download.search, {}, false);
      const bytes = new Uint8Array(await content.arrayBuffer());
      if (format === "PDF") expect([...bytes.slice(0, 4)]).toEqual([0x25, 0x50, 0x44, 0x46]);
      else expect([...bytes.slice(0, 2)]).toEqual([0x50, 0x4b]);
      expect(bytes.length).toBeGreaterThan(1000);
    }
  });
});
