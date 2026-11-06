import { audit } from "../audit";
import { all, first, now, run, sha256Hex, text, uuid } from "../db";
import { badRequest, camelize, HttpError, intParam, json, noContent, notFound, parseJson } from "../http";
import type { Env, Principal, Row } from "../types";
import { recordChange } from "./core";

const TYPES = new Set(["SINGLE_CHOICE", "MULTI_CHOICE", "FILL_BLANK", "EXPERIMENT", "CALCULATION", "SHORT_ANSWER", "ESSAY", "OTHER"]);
const SOURCES = new Set(["SELF_CREATED", "TEACHER_IMPORTED", "STUDENT_EXAM", "AUTHORIZED_BANK", "AI_GENERATED", "AI_VARIANT"]);
const REVIEW = new Set(["DRAFT", "AI_PARSED", "NEEDS_REVIEW", "NEEDS_TEACHER_REVIEW", "APPROVED", "REJECTED"]);
const COPYRIGHT = new Set(["PERSONAL_USE", "AUTHORIZED", "PUBLIC_DOMAIN", "UNKNOWN"]);
const USAGE = new Set(["PRIVATE_TEACHING", "CLASSROOM", "PUBLIC"]);
const IMPORTANCE = new Set(["PRIMARY", "SECONDARY", "RELATED"]);
const questionColumns = `id, legacy_id, subject_id, grade, textbook_version, question_type, difficulty,
  stem, source_type, source_name, review_status, copyright_status, usage_scope, version, updated_at, ability_tags`;

export async function handleQuestions(
  request: Request,
  env: Env,
  url: URL,
  principal: Principal,
): Promise<Response | null> {
  const path = url.pathname;
  const method = request.method;
  if (!path.startsWith("/api/v1/questions")) return null;

  if (method === "POST" && path === "/api/v1/questions") {
    const input = await request.json() as Row;
    return json(await createQuestion(env, input, principal), 201);
  }
  if (method === "GET" && path === "/api/v1/questions") {
    return json(await searchQuestions(env, url));
  }
  if (method === "GET" && path === "/api/v1/questions/duplicates") {
    const stem = requireStem(url.searchParams.get("stem"));
    const stemHash = await sha256Hex(stem);
    const normalized = normalizeStem(stem);
    const exact = await first(env, `select ${questionColumns} from questions where stem_hash = ? and deleted_at is null limit 1`, stemHash);
    const near = await first(env, `select ${questionColumns} from questions where normalized_hash = ? and deleted_at is null limit 1`, normalized);
    const candidates = await all(env,
      `select ${questionColumns} from questions where deleted_at is null order by updated_at desc limit 500`);
    const similar = candidates
      .filter((candidate) => candidate.id !== exact?.id && candidate.id !== near?.id)
      .map((candidate) => ({ candidate, score: similarity(stem, text(candidate.stem)) }))
      .filter((item) => item.score > 0.25)
      .sort((a, b) => b.score - a.score)
      .slice(0, 5)
      .map((item) => item.candidate);
    return json(camelize({ exact, near, similar }));
  }

  const assets = path.match(/^\/api\/v1\/questions\/([^/]+)\/assets$/);
  if (assets && method === "GET") {
    await requireQuestion(env, assets[1]);
    return json(camelize(await all(env,
      "select asset_id, asset_type, sort_order from question_assets where question_id = ? order by sort_order", assets[1])));
  }
  if (assets && method === "POST") {
    await requireQuestion(env, assets[1]);
    const input = await request.json() as Row;
    const assetType = text(input.assetType, "IMAGE").toUpperCase();
    if (!new Set(["IMAGE", "DIAGRAM", "GRAPH", "FORMULA", "PDF_PAGE", "OTHER"]).has(assetType)) {
      return badRequest("INVALID_ASSET_TYPE", `未知附件类型: ${input.assetType}`);
    }
    await run(env,
      `insert into question_assets (id, question_id, asset_id, asset_type, sort_order)
       values (?, ?, ?, ?, ?) on conflict (question_id, asset_id) do nothing`,
      uuid(), assets[1], input.assetId, assetType, Number(input.sortOrder ?? 0));
    await audit(env, principal, "question.linkAsset", "Question", assets[1]);
    return noContentCreated();
  }

  const review = path.match(/^\/api\/v1\/questions\/([^/]+)\/review$/);
  if (review && method === "POST") {
    const input = await request.json() as Row;
    return json(await reviewQuestion(env, review[1], text(input.status), principal));
  }

  const item = path.match(/^\/api\/v1\/questions\/([^/]+)$/);
  if (item && method === "GET") return json(await getQuestion(env, item[1]));
  if (item && method === "PATCH") return json(await updateQuestion(env, item[1], await request.json() as Row, principal));
  if (item && method === "DELETE") {
    const result = await run(env,
      "update questions set deleted_at = ?, version = version + 1, updated_at = ? where id = ? and deleted_at is null",
      now(), now(), item[1]);
    if (!result.meta.changes) return notFound(`题目不存在: ${item[1]}`);
    await recordChange(env, "question", item[1], "DELETE", 1, { deleted: true });
    await audit(env, principal, "question.delete", "Question", item[1]);
    return noContent();
  }
  return null;
}

function noContentCreated(): Response {
  return new Response(null, { status: 201 });
}

async function createQuestion(env: Env, input: Row, principal: Principal): Promise<unknown> {
  validate(input);
  await requireSubject(env, input.subjectId);
  const stem = requireStem(text(input.stem));
  const stemHash = await sha256Hex(stem);
  const duplicate = await first(env, "select id from questions where stem_hash = ? and deleted_at is null", stemHash);
  if (duplicate) throw new HttpError(409, "DUPLICATE_QUESTION", `题干完全相同的题目已存在: ${duplicate.id}`);
  const sourceType = text(input.sourceType, "SELF_CREATED");
  let reviewStatus = text(input.reviewStatus, "DRAFT");
  if (sourceType.startsWith("AI_") && reviewStatus === "APPROVED") reviewStatus = "NEEDS_TEACHER_REVIEW";
  const id = uuid();
  await run(env,
    `insert into questions
     (id, subject_id, grade, textbook_version, question_type, difficulty, stem, stem_hash, normalized_hash,
      source_type, source_name, copyright_status, usage_scope, review_status, ability_tags, created_by, version)
     values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)`,
    id, input.subjectId, text(input.grade), text(input.textbookVersion), input.questionType,
    Number(input.difficulty), stem, stemHash, normalizeStem(stem), sourceType, text(input.sourceName),
    text(input.copyrightStatus, "PERSONAL_USE"), text(input.usageScope, "PRIVATE_TEACHING"), reviewStatus,
    JSON.stringify(Array.isArray(input.abilityTags) ? input.abilityTags : []), principal.userId,
  );
  await applyChildren(env, id, input);
  const detail = await getQuestion(env, id);
  await insertVersion(env, id, 1, detail, "create", principal.userId);
  await recordChange(env, "question", id, "UPSERT", 1, { id, reviewStatus });
  await audit(env, principal, "question.create", "Question", id, null, detail);
  return detail;
}

async function updateQuestion(env: Env, id: string, input: Row, principal: Principal): Promise<unknown> {
  const current = await getQuestion(env, id) as any;
  const baseVersion = Number(input.baseVersion);
  if (baseVersion !== current.question.version) {
    throw new HttpError(409, "VERSION_CONFLICT", `题目已被修改，当前版本 ${current.question.version}`);
  }
  const merged = {
    subjectId: input.subjectId ?? current.question.subjectId,
    questionType: text(input.questionType, current.question.questionType),
    difficulty: Number(input.difficulty ?? current.question.difficulty),
    sourceType: text(input.sourceType, current.question.sourceType),
    copyrightStatus: text(input.copyrightStatus, current.question.copyrightStatus),
    usageScope: text(input.usageScope, current.question.usageScope),
    reviewStatus: text(input.reviewStatus, current.question.reviewStatus),
  };
  validate(merged);
  await requireSubject(env, merged.subjectId);
  const stem = requireStem(text(input.stem, current.question.stem));
  if (merged.sourceType.startsWith("AI_") && merged.reviewStatus === "APPROVED" && !principal.scopes.includes("owner")) {
    throw new HttpError(403, "FORBIDDEN", "AI 生成题目必须由教师审核");
  }
  const result = await run(env,
    `update questions set subject_id = ?, grade = ?, textbook_version = ?, question_type = ?, difficulty = ?,
       stem = ?, stem_hash = ?, normalized_hash = ?, source_type = ?, source_name = ?, copyright_status = ?,
       usage_scope = ?, review_status = ?, ability_tags = ?, updated_by = ?, version = version + 1, updated_at = ?
     where id = ? and version = ? and deleted_at is null`,
    merged.subjectId, text(input.grade, current.question.grade),
    text(input.textbookVersion, current.question.textbookVersion), merged.questionType, merged.difficulty,
    stem, await sha256Hex(stem), normalizeStem(stem), merged.sourceType,
    text(input.sourceName, current.question.sourceName), merged.copyrightStatus, merged.usageScope,
    merged.reviewStatus, JSON.stringify(Array.isArray(input.abilityTags) ? input.abilityTags : current.abilityTags),
    principal.userId, now(), id, baseVersion,
  );
  if (!result.meta.changes) throw new HttpError(409, "VERSION_CONFLICT", "题目已被其他端修改");
  if (input.options !== undefined) await replaceOptions(env, id, input.options);
  if (input.answer !== undefined) await upsertAnswer(env, id, input.answer);
  if (input.knowledge !== undefined) await replaceKnowledge(env, id, input.knowledge);
  const detail = await getQuestion(env, id) as any;
  await insertVersion(env, id, detail.question.version, detail, "update", principal.userId);
  await recordChange(env, "question", id, "UPSERT", detail.question.version, { id, reviewStatus: detail.question.reviewStatus });
  await audit(env, principal, "question.update", "Question", id, current, detail);
  return detail;
}

async function reviewQuestion(env: Env, id: string, status: string, principal: Principal): Promise<unknown> {
  const normalized = status.trim().toUpperCase();
  if (!REVIEW.has(normalized)) return badRequest("INVALID_REVIEW_STATUS", `未知审核状态: ${status}`);
  const current = await getQuestion(env, id) as any;
  if (normalized === "APPROVED") {
    if (current.question.sourceType.startsWith("AI_") && !principal.scopes.includes("owner")) {
      throw new HttpError(403, "FORBIDDEN", "AI 生成题目必须由教师审核");
    }
    if (current.question.reviewStatus === "REJECTED") {
      return badRequest("INVALID_TRANSITION", "已拒绝的题目不能直接通过");
    }
  }
  await run(env,
    "update questions set review_status = ?, version = version + 1, updated_at = ? where id = ? and deleted_at is null",
    normalized, now(), id);
  const detail = await getQuestion(env, id) as any;
  await insertVersion(env, id, detail.question.version, detail, `review:${normalized}`, principal.userId);
  await recordChange(env, "question", id, "UPSERT", detail.question.version, { id, reviewStatus: normalized });
  await audit(env, principal, "question.review", "Question", id, current, detail);
  return detail;
}

async function searchQuestions(env: Env, url: URL): Promise<unknown> {
  const filters = readFilters(env, url);
  const limit = intParam(url.searchParams.get("limit"), 50, 1, 200);
  const offset = intParam(url.searchParams.get("offset"), 0, 0, Number.MAX_SAFE_INTEGER);
  const items = await all(env,
    `select distinct ${filters.columns} from questions q ${filters.join} where ${filters.where}
     order by q.updated_at desc limit ? offset ?`, ...filters.params, limit, offset);
  const count = await first<Row>(env,
    `select count(distinct q.id) as total from questions q ${filters.join} where ${filters.where}`, ...filters.params);
  return { items: camelize(items), limit, offset, total: Number(count?.total ?? 0) };
}

function readFilters(env: Env, url: URL): { columns: string; join: string; where: string; params: unknown[] } {
  const params: unknown[] = [];
  const where = ["q.deleted_at is null"];
  const kp = url.searchParams.get("knowledgePointId");
  const join = kp ? "join question_knowledge qk on qk.question_id = q.id" : "";
  const fields: Array<[string, string]> = [
    ["subjectId", "q.subject_id"], ["grade", "q.grade"], ["questionType", "q.question_type"],
    ["sourceType", "q.source_type"], ["reviewStatus", "q.review_status"],
  ];
  for (const [param, column] of fields) {
    const value = url.searchParams.get(param);
    if (value) {
      if (column === "q.question_type" && !TYPES.has(value)) badRequest("INVALID_QUESTION_TYPE", `未知题型: ${value}`);
      if (column === "q.source_type" && !SOURCES.has(value)) badRequest("INVALID_SOURCE_TYPE", `未知来源: ${value}`);
      if (column === "q.review_status" && !REVIEW.has(value)) badRequest("INVALID_REVIEW_STATUS", `未知审核状态: ${value}`);
      where.push(`${column} = ?`);
      params.push(value);
    }
  }
  for (const [param, operator] of [["minDifficulty", ">="], ["maxDifficulty", "<="]] as const) {
    const value = url.searchParams.get(param);
    if (value) {
      where.push(`q.difficulty ${operator} ?`);
      params.push(Number(value));
    }
  }
  if (kp) {
    where.push("qk.knowledge_point_id = ?");
    params.push(kp);
  }
  const query = url.searchParams.get("q");
  if (query) {
    where.push("q.stem like ?");
    params.push(`%${query}%`);
  }
  const columns = questionColumns.split(", ").map((column) => column.includes(" ") ? column : `q.${column}`).join(", ");
  return { columns, join, where: where.join(" and "), params };
}

async function getQuestion(env: Env, id: string): Promise<unknown> {
  const question = await first(env, `select ${questionColumns} from questions where id = ? and deleted_at is null`, id);
  if (!question) return notFound(`题目不存在: ${id}`);
  const [options, answer, knowledge] = await Promise.all([
    all(env, "select id, option_key, content, sort_order from question_options where question_id = ? order by sort_order, option_key", id),
    first(env, "select standard_answer, analysis, scoring_points, solution_steps from question_answers where question_id = ?", id),
    all(env, "select knowledge_point_id, importance from question_knowledge where question_id = ? order by importance", id),
  ]);
  return camelize({
    question: { ...question, ability_tags: undefined },
    options,
    answer,
    knowledge,
    abilityTags: parseJson(question.ability_tags, []),
  });
}

async function requireQuestion(env: Env, id: string): Promise<void> {
  await getQuestion(env, id);
}

async function requireSubject(env: Env, id: unknown): Promise<void> {
  if (!id) return badRequest("SUBJECT_REQUIRED", "必须指定学科");
  if (!await first(env, "select id from subjects where id = ?", id)) {
    return badRequest("SUBJECT_NOT_FOUND", `学科不存在: ${id}`);
  }
}

function validate(input: Row): void {
  if (!TYPES.has(text(input.questionType))) return badRequest("INVALID_QUESTION_TYPE", `未知题型: ${input.questionType}`);
  const difficulty = Number(input.difficulty);
  if (!Number.isInteger(difficulty) || difficulty < 1 || difficulty > 5) {
    return badRequest("INVALID_DIFFICULTY", "难度必须为 1..5");
  }
  for (const [field, allowed, code, message] of [
    ["sourceType", SOURCES, "INVALID_SOURCE_TYPE", "未知来源"],
    ["copyrightStatus", COPYRIGHT, "INVALID_COPYRIGHT_STATUS", "未知版权状态"],
    ["usageScope", USAGE, "INVALID_USAGE_SCOPE", "未知使用范围"],
    ["reviewStatus", REVIEW, "INVALID_REVIEW_STATUS", "未知审核状态"],
  ] as const) {
    const value = text(input[field]);
    if (value && !allowed.has(value)) return badRequest(code, message);
  }
}

function requireStem(stem: string | null): string {
  const value = text(stem).trim();
  if (!value) return badRequest("STEM_REQUIRED", "题干不能为空");
  return value;
}

function normalizeStem(stem: string): string {
  return stem.toLowerCase().replace(/[\p{P}\p{Z}，。；：！？、【】（）《》“”‘’]/gu, "").replace(/[0-9]/g, "#");
}

function similarity(a: string, b: string): number {
  const left = new Set([...normalizeStem(a)].map((char, index, chars) => chars.slice(index, index + 2).join("")).filter((gram) => gram.length === 2));
  const right = new Set([...normalizeStem(b)].map((char, index, chars) => chars.slice(index, index + 2).join("")).filter((gram) => gram.length === 2));
  if (!left.size || !right.size) return 0;
  const intersection = [...left].filter((gram) => right.has(gram)).length;
  return (2 * intersection) / (left.size + right.size);
}

async function applyChildren(env: Env, id: string, input: Row): Promise<void> {
  await replaceOptions(env, id, input.options);
  await upsertAnswer(env, id, input.answer);
  await replaceKnowledge(env, id, input.knowledge);
}

async function replaceOptions(env: Env, id: string, options: unknown): Promise<void> {
  await run(env, "delete from question_options where question_id = ?", id);
  if (!Array.isArray(options)) return;
  for (const [index, value] of options.entries()) {
    const option = value as Row;
    await run(env,
      "insert into question_options (id, question_id, option_key, content, sort_order) values (?, ?, ?, ?, ?)",
      uuid(), id, option.key, option.content, Number(option.sortOrder ?? index));
  }
}

async function upsertAnswer(env: Env, id: string, value: unknown): Promise<void> {
  if (!value || typeof value !== "object") {
    await run(env, "delete from question_answers where question_id = ?", id);
    return;
  }
  const answer = value as Row;
  await run(env,
    `insert into question_answers (question_id, standard_answer, analysis, scoring_points, solution_steps, updated_at)
     values (?, ?, ?, ?, ?, ?) on conflict (question_id) do update set
       standard_answer = excluded.standard_answer, analysis = excluded.analysis,
       scoring_points = excluded.scoring_points, solution_steps = excluded.solution_steps, updated_at = excluded.updated_at`,
    id, text(answer.standardAnswer), text(answer.analysis), text(answer.scoringPoints), text(answer.solutionSteps), now());
}

async function replaceKnowledge(env: Env, id: string, value: unknown): Promise<void> {
  await run(env, "delete from question_knowledge where question_id = ?", id);
  if (!Array.isArray(value)) return;
  for (const item of value) {
    const link = item as Row;
    const importance = text(link.importance, "PRIMARY");
    if (!IMPORTANCE.has(importance)) return badRequest("INVALID_IMPORTANCE", "未知知识点重要度");
    await run(env,
      "insert into question_knowledge (question_id, knowledge_point_id, importance) values (?, ?, ?) on conflict do nothing",
      id, link.knowledgePointId, importance);
  }
}

async function insertVersion(env: Env, questionId: string, version: number, snapshot: unknown, note: string, userId: string): Promise<void> {
  await run(env,
    "insert into question_versions (id, question_id, version, snapshot, change_note, created_by) values (?, ?, ?, ?, ?, ?)",
    uuid(), questionId, version, JSON.stringify(snapshot), note, userId);
}

export { createQuestion, getQuestion, searchQuestions, updateQuestion };
