import { audit } from "../audit";
import { all, first, now, run, text, uuid } from "../db";
import { badRequest, camelize, intParam, json, notFound } from "../http";
import type { Env, Principal, Row } from "../types";
import { recordChange } from "./core";

const MODES = new Set(["BASIC", "ERROR_FOCUS", "TOPIC", "COMPREHENSIVE", "PRE_EXAM", "RETEST"]);

export async function handlePractices(request: Request, env: Env, url: URL, principal: Principal): Promise<Response | null> {
  const path = url.pathname;
  const method = request.method;
  if (method === "POST" && path === "/api/v1/practices/generate") {
    return json(await generatePractice(env, await request.json() as Row, principal), 201);
  }
  const get = path.match(/^\/api\/v1\/practices\/([^/]+)$/);
  if (get && method === "GET") return json(await practiceDetail(env, get[1]));
  const approve = path.match(/^\/api\/v1\/practices\/([^/]+)\/approve$/);
  if (approve && method === "POST") {
    await practiceSet(env, approve[1]);
    await run(env, "update practice_sets set status = 'APPROVED', version = version + 1, updated_at = ? where id = ?", now(), approve[1]);
    await audit(env, principal, "practice.approve", "PracticeSet", approve[1]);
    return json(await practiceDetail(env, approve[1]));
  }
  const submit = path.match(/^\/api\/v1\/practices\/([^/]+)\/submit$/);
  if (submit && method === "POST") {
    return json(await submitPractice(env, submit[1], await request.json() as Row, principal));
  }
  const list = path.match(/^\/api\/v1\/students\/([^/]+)\/practices$/);
  if (list && method === "GET") {
    const limit = intParam(url.searchParams.get("limit"), 50, 1, 200);
    return json(camelize(await all(env,
      `select id, student_id, subject_id, mode, title, total_score, estimated_minutes, status, version, created_at
       from practice_sets where student_id = ? and deleted_at is null order by created_at desc limit ?`, list[1], limit)));
  }
  const mastery = path.match(/^\/api\/v1\/students\/([^/]+)\/mastery$/);
  if (mastery && method === "GET") return json(camelize(await masteryRows(env, mastery[1])));
  return null;
}

async function generatePractice(env: Env, input: Row, principal: Principal): Promise<unknown> {
  if (!input.studentId) return badRequest("STUDENT_REQUIRED", "学生 ID 必填");
  if (!input.subjectId) return badRequest("SUBJECT_REQUIRED", "学科 ID 必填");
  if (!await first(env, "select id from students where id = ? and deleted_at is null", input.studentId)) return badRequest("NOT_FOUND", "学生不存在");
  if (!await first(env, "select id from subjects where id = ?", input.subjectId)) return badRequest("NOT_FOUND", "学科不存在");
  let count = intParam(input.questionCount === undefined ? null : String(input.questionCount), 10, 1, 50);
  const minDifficulty = intParam(input.minDifficulty === undefined ? null : String(input.minDifficulty), 1, 1, 5);
  const maxDifficulty = intParam(input.maxDifficulty === undefined ? null : String(input.maxDifficulty), 5, minDifficulty, 5);
  const mode = text(input.mode, "BASIC").toUpperCase();
  if (!MODES.has(mode)) return badRequest("INVALID_MODE", `未知练习模式: ${input.mode}`);
  const knowledgeIds = Array.isArray(input.knowledgePointIds) ? input.knowledgePointIds.map(String) : [];
  const candidates = await selectCandidates(env, text(input.subjectId), knowledgeIds, minDifficulty, maxDifficulty, count);
  if (!candidates.length) {
    return badRequest("INSUFFICIENT_QUESTIONS", "题库中没有符合条件且已审核的题目，AI 生成未配置或未通过质量门");
  }
  if (candidates.length < count) count = candidates.length;
  const practiceId = uuid();
  const totalScore = Number(input.totalScore) > 0 ? Number(input.totalScore) : count * 5;
  const perItem = totalScore / count;
  const title = text(input.title).trim() || `精准练习-${mode}`;
  const strategy = {
    requestedCount: input.questionCount ?? null, minDifficulty, maxDifficulty, knowledgePointIds: knowledgeIds,
    includeMistakes: input.includeMistakes ?? null, allowAi: input.allowAi ?? null,
  };
  await run(env,
    `insert into practice_sets (id, student_id, subject_id, mode, title, strategy, total_score, estimated_minutes, status, generated_by)
     values (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)`,
    practiceId, input.studentId, input.subjectId, mode, title, JSON.stringify(strategy), totalScore, count * 5, principal.userId);
  for (const [sort, candidate] of candidates.slice(0, count).entries()) {
    await run(env,
      `insert into practice_items (id, practice_set_id, question_id, source_kind, selection_reason, score, sort_order)
       values (?, ?, ?, 'BANK', ?, ?, ?)`,
      uuid(), practiceId, candidate.id, `已审核题库；难度 ${candidate.difficulty}`, perItem, sort);
  }
  await audit(env, principal, "practice.generate", "PracticeSet", practiceId);
  await recordChange(env, "practiceSet", practiceId, "UPSERT", 1, { id: practiceId });
  return await practiceDetail(env, practiceId);
}

async function selectCandidates(env: Env, subjectId: string, knowledgeIds: string[], min: number, max: number, limit: number): Promise<Row[]> {
  const params: unknown[] = [subjectId, min, max];
  let knowledgeFilter = "";
  if (knowledgeIds.length) {
    knowledgeFilter = ` and exists (select 1 from question_knowledge qk where qk.question_id = q.id
      and qk.knowledge_point_id in (${knowledgeIds.map(() => "?").join(",")}))`;
    params.push(...knowledgeIds);
  }
  params.push(limit);
  return await all(env,
    `select q.id, q.difficulty, q.question_type from questions q
     where q.deleted_at is null and q.review_status = 'APPROVED' and q.subject_id = ?
       and q.difficulty between ? and ? ${knowledgeFilter} order by q.updated_at desc limit ?`, ...params);
}

async function practiceSet(env: Env, id: string): Promise<Row> {
  const set = await first(env,
    `select id, student_id, subject_id, mode, title, total_score, estimated_minutes, status, version, created_at
     from practice_sets where id = ? and deleted_at is null`, id);
  if (!set) return notFound(`练习不存在: ${id}`);
  return set;
}

async function practiceDetail(env: Env, id: string): Promise<unknown> {
  const set = await practiceSet(env, id);
  const items = await all(env,
    `select pi.id, pi.question_id, pi.source_kind, pi.selection_reason, pi.score, pi.sort_order,
       q.stem, q.question_type, q.difficulty, q.review_status
     from practice_items pi join questions q on q.id = pi.question_id where pi.practice_set_id = ? order by pi.sort_order`, id);
  return camelize({ set, items, mastery: await masteryRows(env, text(set.student_id)) });
}

async function submitPractice(env: Env, id: string, input: Row, principal: Principal): Promise<unknown> {
  const set = await practiceSet(env, id);
  if (set.status === "COMPLETED") return badRequest("ALREADY_COMPLETED", "该练习已提交");
  const items = await all(env,
    `select pi.id, pi.question_id, pi.score from practice_items pi where pi.practice_set_id = ? order by pi.sort_order`, id);
  if (!items.length) return badRequest("EMPTY_PRACTICE", "练习没有题目");
  const answers = new Map<string, Row>();
  if (Array.isArray(input.answers)) {
    for (const answer of input.answers) answers.set(text((answer as Row).itemId), answer as Row);
  }
  const attemptId = uuid();
  await run(env, "insert into practice_attempts (id, practice_set_id, student_id, total_score, full_score) values (?, ?, ?, 0, 0)", attemptId, id, set.student_id);
  let total = 0;
  let fullTotal = 0;
  const knowledgeToRecompute = new Set<string>();
  for (const item of items) {
    const itemId = text(item.id);
    const answer = answers.get(itemId);
    const studentAnswer = text(answer?.studentAnswer).trim();
    const standard = await first(env, "select standard_answer from question_answers where question_id = ?", item.question_id);
    const full = Number(item.score);
    let correct: boolean;
    let score: number;
    if (answer?.score !== undefined && answer.score !== null) {
      score = Math.max(0, Math.min(Number(answer.score), full));
      correct = answer.correct === undefined || answer.correct === null ? score >= full : Boolean(answer.correct);
    } else if (answer?.correct !== undefined && answer.correct !== null) {
      correct = Boolean(answer.correct);
      score = correct ? full : 0;
    } else {
      correct = !!text(standard?.standard_answer) && normalize(studentAnswer) === normalize(text(standard?.standard_answer));
      score = correct ? full : 0;
    }
    await run(env,
      `insert into practice_item_results (id, attempt_id, practice_item_id, student_answer, score, full_score, is_correct)
       values (?, ?, ?, ?, ?, ?, ?)`,
      uuid(), attemptId, itemId, studentAnswer, score, full, correct ? 1 : 0);
    total += score;
    fullTotal += full;
    const links = await all(env, "select knowledge_point_id from question_knowledge where question_id = ?", item.question_id);
    links.forEach((link) => knowledgeToRecompute.add(text(link.knowledge_point_id)));
  }
  await run(env, "update practice_attempts set total_score = ?, full_score = ? where id = ?", total, fullTotal, attemptId);
  for (const knowledgeId of knowledgeToRecompute) await recomputeMastery(env, text(set.student_id), knowledgeId);
  await run(env, "update practice_sets set status = 'COMPLETED', version = version + 1, updated_at = ? where id = ?", now(), id);
  await audit(env, principal, "practice.submit", "PracticeSet", id);
  return await practiceDetail(env, id);
}

async function recomputeMastery(env: Env, studentId: string, knowledgeId: string): Promise<void> {
  const evidence = await all(env,
    `select pir.is_correct, q.difficulty, pir.created_at from practice_item_results pir
     join practice_attempts pa on pa.id = pir.attempt_id
     join practice_items pi on pi.id = pir.practice_item_id
     join questions q on q.id = pi.question_id
     join question_knowledge qk on qk.question_id = q.id
     where pa.student_id = ? and qk.knowledge_point_id = ? order by pir.created_at`, studentId, knowledgeId);
  let correctCount = 0;
  let weighted = 0;
  let totalWeight = 0;
  let last: string | null = null;
  for (const item of evidence) {
    const correct = Boolean(item.is_correct);
    if (correct) correctCount++;
    const difficultyWeight = 0.6 + 0.2 * (Math.max(1, Math.min(Number(item.difficulty), 5)) - 1);
    const days = Math.max(0, (Date.now() - Date.parse(text(item.created_at))) / 86400000);
    const weight = difficultyWeight * Math.pow(0.5, days / 45);
    totalWeight += weight;
    if (correct) weighted += weight;
    if (!last || text(item.created_at) > last) last = text(item.created_at);
  }
  const wrongCount = evidence.length - correctCount;
  const raw = totalWeight > 0 ? 100 * weighted / totalWeight : 0;
  const shrunk = (totalWeight / (totalWeight + 2)) * raw + (2 / (totalWeight + 2)) * 50;
  const mastery = Math.max(0, Math.min(100, shrunk - Math.min(20, 4 * Math.min(wrongCount, 5))));
  const confidence = Math.min(1, totalWeight / 5);
  const trendValue = trend(evidence);
  await run(env,
    `insert into student_mastery (student_id, knowledge_point_id, mastery_score, confidence, practice_count,
       correct_count, wrong_count, last_practiced_at, trend, formula_version)
     values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'v1')
     on conflict (student_id, knowledge_point_id) do update set
       mastery_score = excluded.mastery_score, confidence = excluded.confidence, practice_count = excluded.practice_count,
       correct_count = excluded.correct_count, wrong_count = excluded.wrong_count,
       last_practiced_at = excluded.last_practiced_at, trend = excluded.trend, formula_version = 'v1',
       version = student_mastery.version + 1, updated_at = ?`,
    studentId, knowledgeId, mastery, confidence, evidence.length, correctCount, wrongCount, last, trendValue.toFixed(3), now());
}

function trend(evidence: Row[]): number {
  if (evidence.length < 2) return 0;
  const window = Math.min(3, Math.floor(evidence.length / 2));
  let firstValue = 0;
  let lastValue = 0;
  for (let index = 0; index < window; index++) {
    firstValue += evidence[index].is_correct ? 1 : 0;
    lastValue += evidence[evidence.length - 1 - index].is_correct ? 1 : 0;
  }
  return (lastValue - firstValue) / window;
}

async function masteryRows(env: Env, studentId: string): Promise<Row[]> {
  return await all(env,
    `select knowledge_point_id, mastery_score, confidence, practice_count, correct_count, wrong_count, trend
     from student_mastery where student_id = ? order by mastery_score`, studentId);
}

function normalize(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, "");
}

export { generatePractice, masteryRows, practiceDetail };
