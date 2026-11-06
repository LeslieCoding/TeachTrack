import { audit } from "../audit";
import { all, first, now, run, sha256Hex, text, uuid } from "../db";
import { badRequest, camelize, HttpError, intParam, json, notFound } from "../http";
import type { Env, Principal, Row } from "../types";
import { recordChange } from "./core";
import { createQuestion } from "./questions";

export async function handleExams(request: Request, env: Env, url: URL, principal: Principal): Promise<Response | null> {
  const path = url.pathname;
  const method = request.method;
  if (method === "POST" && path === "/api/v1/exams/import") {
    return json(await importPaper(env, await request.json() as Row, principal), 201);
  }
  const list = path.match(/^\/api\/v1\/students\/([^/]+)\/exams$/);
  if (list && method === "GET") {
    const limit = intParam(url.searchParams.get("limit"), 50, 1, 200);
    const rows = await all(env,
      `select id, exam_name, exam_date, analysis_status, score, full_score from exam_papers
       where student_id = ? and deleted_at is null order by exam_date desc limit ?`, list[1], limit);
    return json(rows.map((row) => camelize({
      id: row.id, exam_name: row.exam_name, exam_date: row.exam_date,
      status: row.analysis_status, score: row.score, full_score: row.full_score,
    })));
  }
  const analyze = path.match(/^\/api\/v1\/exams\/([^/]+)\/analyze$/);
  if (analyze && method === "POST") {
    return json(await analyzePaper(env, analyze[1], await request.json() as Row, principal));
  }
  const analysis = path.match(/^\/api\/v1\/exams\/([^/]+)\/analysis$/);
  if (analysis && method === "GET") return json(await examDetail(env, analysis[1]));
  const update = path.match(/^\/api\/v1\/exams\/([^/]+)\/questions\/([^/]+)$/);
  if (update && method === "PATCH") {
    return json(await updateAnswer(env, update[1], update[2], await request.json() as Row, principal));
  }
  const approve = path.match(/^\/api\/v1\/exams\/([^/]+)\/approve$/);
  if (approve && method === "POST") {
    return json(await approvePaper(env, approve[1], url.searchParams.get("importToBank") !== "false", principal));
  }
  return null;
}

async function importPaper(env: Env, input: Row, principal: Principal): Promise<unknown> {
  if (!input.studentId) return badRequest("STUDENT_REQUIRED", "学生 ID 必填");
  if (!input.subjectId) return badRequest("SUBJECT_REQUIRED", "学科 ID 必填");
  if (!text(input.examName).trim()) return badRequest("EXAM_NAME_REQUIRED", "试卷名称必填");
  if (!await first(env, "select id from students where id = ? and deleted_at is null", input.studentId)) {
    return badRequest("NOT_FOUND", "学生不存在");
  }
  if (!await first(env, "select id from subjects where id = ?", input.subjectId)) {
    return badRequest("NOT_FOUND", "学科不存在");
  }
  const id = uuid();
  await run(env,
    `insert into exam_papers (id, student_id, subject_id, exam_name, exam_date, full_score, score, storage_asset_id, analysis_status)
     values (?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADED')`,
    id, input.studentId, input.subjectId, text(input.examName).trim(), dateOnly(input.examDate),
    input.fullScore ?? null, input.score ?? null, text(input.assetId) || null);
  await audit(env, principal, "exam.import", "ExamPaper", id);
  await recordChange(env, "examPaper", id, "UPSERT", 1, { id });
  return await paper(env, id);
}

async function analyzePaper(env: Env, paperId: string, input: Row, principal: Principal): Promise<unknown> {
  const current = await paper(env, paperId);
  let parsed = Array.isArray(input.questions) ? input.questions as Row[] : [];
  if (!parsed.length) parsed = parseText(text(input.text));
  if (!parsed.length) {
    await run(env,
      "update exam_papers set analysis_status = 'PARSE_FAILED', parse_error = ?, version = version + 1, updated_at = ? where id = ?",
      "没有可分析的题目；请提供 OCR 文本或结构化题目", now(), paperId);
    return badRequest("NO_QUESTIONS", "没有可分析的题目；请提供 OCR 文本或结构化题目（不伪造结果）");
  }
  await run(env, "update exam_papers set analysis_status = 'PARSING', parse_error = '', version = version + 1, updated_at = ? where id = ?", now(), paperId);
  await run(env, "delete from exam_questions where exam_paper_id = ?", paperId);
  const knowledgeRows = await all(env, "select name from knowledge_points where subject_id = ? and deleted_at is null", current.subject_id);
  const knowledgeNames = knowledgeRows.map((row) => text(row.name)).filter((name) => name.length >= 2);
  const reportRows: Row[] = [];
  let totalScore = 0;
  let totalFull = 0;
  let wrong = 0;
  let lowConfidence = 0;

  for (const [index, item] of parsed.entries()) {
    const number = text(item.number, String(index + 1));
    const type = normalizeType(text(item.type));
    const score = Number(item.score ?? 0);
    const fullScore = Number(item.fullScore ?? 0);
    const studentAnswer = text(item.studentAnswer);
    const standardAnswer = text(item.standardAnswer);
    const stem = text(item.stem);
    const matched = knowledgeNames.filter((name) => stem.includes(name)).sort((a, b) => b.length - a.length).slice(0, 3);
    const knowledgeSummary = matched.join("、");
    let lastTaught: string | null = null;
    let wrongBefore = false;
    let repeatCount = 0;
    for (const name of matched) {
      const taught = await first<Row>(env,
        `select max(date) as last_date from course_sessions where student_id = ? and deleted_at is null
         and (lesson_title like ? or lesson_content like ? or problems like ?)`,
        current.student_id, `%${name}%`, `%${name}%`, `%${name}%`);
      if (taught?.last_date) {
        const value = `${text(taught.last_date).slice(0, 10)}T00:00:00.000Z`;
        if (!lastTaught || value > lastTaught) lastTaught = value;
      }
      const previous = await first<Row>(env,
        "select count(*) as n from mistake_records where student_id = ? and coalesce(mistake_reason,'') like ?",
        current.student_id, `%${name}%`);
      const count = Number(previous?.n ?? 0);
      if (count > 0) wrongBefore = true;
      repeatCount += count;
    }
    const correct = fullScore > 0
      ? score >= fullScore
      : !!studentAnswer && studentAnswer.trim().toLowerCase() === standardAnswer.trim().toLowerCase();
    const mistakeType = correct ? "UNKNOWN" : matched.length ? "UNKNOWN" : "NEEDS_TEACHER_CONFIRMATION";
    const reason = correct ? "" : matched.length ? "待教师确认：该题未得满分" : "AI 无法确认错因，需教师确认";
    const confidence = correct ? 0.9 : matched.length ? 0.4 : 0;
    const potentialGain = Math.max(0, fullScore - score);
    const suggested = correct ? "保持" : matched.length ? `复习 ${knowledgeSummary} 并做同类题` : "教师确认错因后安排训练";
    const examQuestionId = uuid();
    await run(env,
      `insert into exam_questions (id, exam_paper_id, question_number, question_type, score, full_score, sort_order, stem, standard_answer)
       values (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      examQuestionId, paperId, number, type, score, fullScore, index, stem, standardAnswer);
    await run(env,
      `insert into student_answers (id, student_id, exam_paper_id, exam_question_id, student_answer,
        standard_answer, score, full_score, is_correct, confidence) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      uuid(), current.student_id, paperId, examQuestionId, studentAnswer, standardAnswer, score, fullScore, correct ? 1 : 0, confidence);
    await run(env,
      `insert into exam_question_analyses (id, exam_paper_id, exam_question_id, student_id, ability,
        knowledge_summary, mistake_type, mistake_reason, confidence, difficulty, taught_before, last_taught_at,
        wrong_before, repeat_count, suggested_training, potential_score_gain, review_status)
       values (?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?, ?, 'NEEDS_REVIEW')`,
      uuid(), paperId, examQuestionId, current.student_id, abilityOf(type), knowledgeSummary, mistakeType,
      reason, confidence, lastTaught ? 1 : 0, lastTaught, wrongBefore ? 1 : 0, repeatCount, suggested, potentialGain);
    reportRows.push({ number, type, score, fullScore, correct, knowledge: knowledgeSummary, mistakeType, confidence });
    totalScore += score;
    totalFull += fullScore;
    if (!correct) wrong++;
    if (confidence < 0.5) lowConfidence++;
  }

  const summary = {
    summary: {
      totalScore, fullScore: totalFull, scoreRate: totalFull > 0 ? totalScore / totalFull : 0,
      questionCount: reportRows.length, wrongCount: wrong, lowConfidenceCount: lowConfidence,
    },
  };
  await run(env,
    `insert into exam_analysis_reports (id, exam_paper_id, summary, by_question_type, by_knowledge, by_mistake_type, ai_provider, ai_model, review_status)
     values (?, ?, ?, ?, ?, ?, 'internal', 'deterministic-v1', 'DRAFT')`,
    uuid(), paperId, JSON.stringify(summary), JSON.stringify(aggregateBy(reportRows, "type")),
    JSON.stringify(aggregateBy(reportRows, "knowledge")), JSON.stringify(aggregateBy(reportRows, "mistakeType")));
  await run(env,
    `update exam_papers set score = ?, full_score = ?, analysis_status = 'AI_PARSED', version = version + 1, updated_at = ? where id = ?`,
    totalFull > 0 ? totalScore : current.score, totalFull > 0 ? totalFull : current.full_score, now(), paperId);
  await audit(env, principal, "exam.analyze", "ExamPaper", paperId);
  await recordChange(env, "examPaper", paperId, "UPSERT", Number(current.version) + 1, { id: paperId, status: "AI_PARSED" });
  return await examDetail(env, paperId);
}

async function updateAnswer(env: Env, paperId: string, examQuestionId: string, input: Row, principal: Principal): Promise<unknown> {
  await paper(env, paperId);
  if (!await first(env, "select id from exam_questions where id = ? and exam_paper_id = ?", examQuestionId, paperId)) {
    return notFound("试卷题目不存在");
  }
  if (input.score !== undefined || input.fullScore !== undefined || input.studentAnswer !== undefined || input.teacherComment !== undefined) {
    const answer = await first(env, "select * from student_answers where exam_question_id = ?", examQuestionId);
    await run(env,
      "update student_answers set score = ?, full_score = ?, student_answer = ?, teacher_comment = ? where exam_question_id = ?",
      Number(input.score ?? answer?.score ?? 0), Number(input.fullScore ?? answer?.full_score ?? 0),
      text(input.studentAnswer, text(answer?.student_answer)), text(input.teacherComment, text(answer?.teacher_comment)), examQuestionId);
    await run(env, "update exam_questions set score = ?, full_score = ? where id = ?",
      Number(input.score ?? answer?.score ?? 0), Number(input.fullScore ?? answer?.full_score ?? 0), examQuestionId);
  }
  if (input.mistakeType !== undefined || input.mistakeReason !== undefined) {
    const analysis = await first(env, "select * from exam_question_analyses where exam_question_id = ?", examQuestionId);
    await run(env,
      "update exam_question_analyses set mistake_type = ?, mistake_reason = ?, review_status = 'APPROVED', updated_at = ? where exam_question_id = ?",
      text(input.mistakeType, text(analysis?.mistake_type, "UNKNOWN")),
      text(input.mistakeReason, text(analysis?.mistake_reason)), now(), examQuestionId);
  }
  await audit(env, principal, "exam.question.update", "ExamQuestion", examQuestionId);
  return await examDetail(env, paperId);
}

async function approvePaper(env: Env, paperId: string, importToBank: boolean, principal: Principal): Promise<unknown> {
  const current = await paper(env, paperId);
  if (importToBank) {
    const rows = await all(env, "select * from exam_questions where exam_paper_id = ? order by sort_order", paperId);
    for (const row of rows) {
      if (row.question_id || !text(row.stem)) continue;
      const hash = await sha256Hex(text(row.stem));
      const existing = await first(env, "select id from questions where stem_hash = ? and deleted_at is null limit 1", hash);
      if (existing) {
        await run(env, "update exam_questions set question_id = ? where id = ?", existing.id, row.id);
        continue;
      }
      try {
        const detail = await createQuestion(env, {
          subjectId: current.subject_id, grade: "", textbookVersion: "", questionType: row.question_type ?? "OTHER",
          difficulty: 3, stem: row.stem, sourceType: "STUDENT_EXAM", sourceName: current.exam_name,
          copyrightStatus: "PERSONAL_USE", usageScope: "PRIVATE_TEACHING", reviewStatus: "NEEDS_TEACHER_REVIEW",
          answer: { standardAnswer: text(row.standard_answer) },
        }, principal) as any;
        await run(env, "update exam_questions set question_id = ? where id = ?", detail.question.id, row.id);
      } catch (error) {
        if (!(error instanceof HttpError) || error.code !== "DUPLICATE_QUESTION") throw error;
        const raced = await first(env, "select id from questions where stem_hash = ? and deleted_at is null limit 1", hash);
        if (raced) await run(env, "update exam_questions set question_id = ? where id = ?", raced.id, row.id);
      }
    }
  }
  await run(env, "update exam_papers set analysis_status = 'APPROVED', version = version + 1, updated_at = ? where id = ?", now(), paperId);
  await audit(env, principal, "exam.approve", "ExamPaper", paperId);
  await recordChange(env, "examPaper", paperId, "UPSERT", Number(current.version) + 1, { id: paperId, status: "APPROVED" });
  return await examDetail(env, paperId);
}

async function paper(env: Env, id: string): Promise<Row> {
  const row = await first(env,
    `select id, student_id, subject_id, exam_name, exam_date, full_score, score, storage_asset_id, analysis_status, version
     from exam_papers where id = ? and deleted_at is null`, id);
  if (!row) return notFound(`试卷不存在: ${id}`);
  return row;
}

async function examDetail(env: Env, id: string): Promise<unknown> {
  const current = await paper(env, id);
  const [questions, answers, analyses, report] = await Promise.all([
    all(env, `select id, exam_paper_id, question_id, question_number, question_type, score, full_score, sort_order, stem, standard_answer
      from exam_questions where exam_paper_id = ? order by sort_order`, id),
    all(env, `select id, exam_question_id, student_answer, standard_answer, score, full_score, is_correct, teacher_comment, ai_comment
      from student_answers where exam_paper_id = ? order by created_at`, id),
    all(env, `select id, exam_question_id, ability, knowledge_summary, mistake_type, mistake_reason, confidence, difficulty,
      taught_before, last_taught_at, wrong_before, repeat_count, suggested_training, potential_score_gain, review_status
      from exam_question_analyses where exam_paper_id = ? order by created_at`, id),
    first(env, `select summary, by_question_type, by_knowledge, by_mistake_type, generated_at, review_status
      from exam_analysis_reports where exam_paper_id = ? order by generated_at desc limit 1`, id),
  ]);
  const reportJson = report ? JSON.stringify({
    summary: JSON.parse(text(report.summary, "{}")),
    byQuestionType: JSON.parse(text(report.by_question_type, "[]")),
    byKnowledge: JSON.parse(text(report.by_knowledge, "[]")),
    byMistakeType: JSON.parse(text(report.by_mistake_type, "[]")),
    generatedAt: report.generated_at,
    reviewStatus: report.review_status,
  }) : "{}";
  return camelize({
    paper: current, questions,
    answers: answers.map((answer) => ({ ...answer, is_correct: answer.is_correct === null ? null : Boolean(answer.is_correct) })),
    analyses: analyses.map((analysis) => ({
      ...analysis, taught_before: Boolean(analysis.taught_before), wrong_before: Boolean(analysis.wrong_before),
    })),
    reportJson,
  });
}

function parseText(value: string): Row[] {
  if (!value.trim()) return [];
  const result: Row[] = [];
  for (const line of value.split(/\r?\n/)) {
    const match = line.match(/^\s*(\d+)\s*[.、．]\s*(.+)$/);
    if (!match) continue;
    const stem = match[2].trim();
    const score = stem.match(/(\d+(?:\.\d+)?)\s*分/);
    const type = stem.includes("选择") ? "SINGLE_CHOICE" : stem.includes("填空") ? "FILL_BLANK"
      : stem.includes("实验") ? "EXPERIMENT" : stem.includes("计算") ? "CALCULATION" : "OTHER";
    result.push({ number: match[1], type, score: 0, fullScore: score ? Number(score[1]) : 0, stem });
  }
  return result;
}

function aggregateBy(rows: Row[], key: string): Row[] {
  const totals = new Map<string, { score: number; fullScore: number; questionCount: number }>();
  for (const row of rows) {
    const name = text(row[key], "未匹配") || "未匹配";
    const value = totals.get(name) ?? { score: 0, fullScore: 0, questionCount: 0 };
    value.score += Number(row.score ?? 0);
    value.fullScore += Number(row.fullScore ?? 0);
    value.questionCount++;
    totals.set(name, value);
  }
  return [...totals.entries()].sort((a, b) => b[1].fullScore - a[1].fullScore).map(([name, value]) => ({
    key: name, ...value, scoreRate: value.fullScore > 0 ? value.score / value.fullScore : 0,
  }));
}

function normalizeType(value: string): string {
  const type = value.trim().toUpperCase();
  const names: Record<string, string> = {
    SINGLE_CHOICE: "SINGLE_CHOICE", "选择题": "SINGLE_CHOICE", "单选": "SINGLE_CHOICE",
    MULTI_CHOICE: "MULTI_CHOICE", "多选题": "MULTI_CHOICE", "多选": "MULTI_CHOICE",
    FILL_BLANK: "FILL_BLANK", "填空题": "FILL_BLANK", "填空": "FILL_BLANK",
    EXPERIMENT: "EXPERIMENT", "实验题": "EXPERIMENT", "实验探究题": "EXPERIMENT",
    CALCULATION: "CALCULATION", "计算题": "CALCULATION", "解答题": "CALCULATION",
    SHORT_ANSWER: "SHORT_ANSWER", "简答题": "SHORT_ANSWER", ESSAY: "ESSAY", "论述题": "ESSAY",
  };
  return names[type] ?? names[value] ?? "OTHER";
}

function abilityOf(type: string): string {
  return type === "EXPERIMENT" ? "实验探究" : type === "CALCULATION" ? "计算与推理"
    : ["SINGLE_CHOICE", "MULTI_CHOICE"].includes(type) ? "辨析" : type === "FILL_BLANK" ? "记忆与理解" : "综合";
}

function dateOnly(value: unknown): string | null {
  const raw = text(value);
  return raw ? raw.slice(0, 10) : null;
}

export { analyzePaper };
