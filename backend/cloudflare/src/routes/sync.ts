import { audit } from "../audit";
import { all, first, now, run, text, uuid } from "../db";
import { badRequest, camelize, json, notFound } from "../http";
import { fromLegacy, snake } from "../ids";
import type { Env, Principal, Row } from "../types";
import { recordChange } from "./core";
import { createQuestion, getQuestion, updateQuestion } from "./questions";

const CHOICES = ["KEEP_LOCAL", "KEEP_REMOTE", "MANUAL_MERGE"];
const STUDENT_FIELDS = ["fullName", "displayName", "status", "gender", "currentGrade", "gradeAtRecord", "nameCompleteness", "school", "className", "subjectId", "notes"];
const COURSE_FIELDS = ["date", "startTime", "endTime", "subjectId", "lessonTitle", "lessonContent", "performance", "problems", "homeworkSummary", "nextLessonPlan", "teacherNotes", "learningStatus"];

export async function handleSync(request: Request, env: Env, principal: Principal): Promise<Response | null> {
  if (request.method !== "POST" || new URL(request.url).pathname !== "/api/v1/sync/operations") return null;
  const input = await request.json() as Row;
  const operations = Array.isArray(input.operations) ? input.operations as Row[] : [];
  if (!operations.length) return json({ applied: [], conflicts: [] });
  if (operations.length > 200) return badRequest("BATCH_TOO_LARGE", "单次最多 200 条同步操作");
  const applied: unknown[] = [];
  const conflicts: unknown[] = [];
  const deviceId = text(input.deviceId) || null;
  for (const operation of operations) {
    const operationId = text(operation.operationId) || uuid();
    const previous = await first(env, "select status, result_version from sync_operations where operation_id = ?", operationId);
    if (previous) {
      applied.push({ operationId, status: previous.status, entityType: operation.entityType, entityId: operation.entityId, version: previous.result_version });
      continue;
    }
    const type = text(operation.entityType);
    if (type === "student") {
      await applyStudent(env, principal, deviceId, operationId, operation, applied, conflicts);
    } else if (type === "courseSession" || type === "course_session") {
      await applyCourse(env, principal, deviceId, operationId, operation, applied, conflicts);
    } else if (type === "question") {
      await applyQuestion(env, principal, deviceId, operationId, operation, applied, conflicts);
    } else {
      return badRequest("UNSUPPORTED_ENTITY", `同步暂不支持实体: ${type}`);
    }
  }
  return json({ applied, conflicts });
}

async function applyStudent(env: Env, principal: Principal, deviceId: string | null, operationId: string, operation: Row, applied: unknown[], conflicts: unknown[]): Promise<void> {
  const payload = payloadOf(operation);
  const legacy = text(payload.legacyId, text(operation.entityId));
  const id = await fromLegacy("student", legacy);
  const current = await first(env, "select * from students where id = ? and deleted_at is null", id);
  const base = Number(operation.baseVersion ?? 0);
  if (current && Number(current.version) !== base && operation.force !== true) {
    await recordOperation(env, operationId, deviceId, "student", id, base, Number(current.version), "CONFLICT");
    conflicts.push(conflict(operationId, "student", id, base, Number(current.version), camelize(current)));
    return;
  }
  let version: number;
  if (!current) {
    const status = text(payload.status, "ACTIVE").toUpperCase();
    await run(env,
      `insert into students (id, legacy_id, full_name, display_name, status, gender, current_grade,
        grade_at_record, name_completeness, school, class_name, subject_id, notes, version)
       values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)`,
      id, legacy, payload.fullName ?? null, text(payload.displayName, text(payload.fullName, "未命名")),
      ["UNKNOWN", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED"].includes(status) ? status : "ACTIVE",
      payload.gender ?? null, payload.currentGrade ?? null, payload.gradeAtRecord ?? null,
      text(payload.nameCompleteness, "CONFIRMED"), text(payload.school), text(payload.className),
      payload.subjectId ?? null, text(payload.notes));
    version = 1;
  } else {
    const sets: string[] = [];
    const params: unknown[] = [];
    for (const field of STUDENT_FIELDS) {
      if (Object.hasOwn(payload, field)) {
        sets.push(`${snake(field)} = ?`);
        params.push(payload[field] ?? null);
      }
    }
    version = Number(current.version);
    if (sets.length) {
      sets.push("version = version + 1", "updated_at = ?");
      params.push(now(), id, current.version);
      const result = await run(env, `update students set ${sets.join(", ")} where id = ? and version = ?`, ...params);
      if (!result.meta.changes) throw new Error("student sync version changed during write");
      version++;
    }
  }
  await recordOperation(env, operationId, deviceId, "student", id, base, version, "APPLIED");
  await recordChange(env, "student", id, "UPSERT", version, { id }, deviceId);
  await audit(env, principal, "sync.student.upsert", "Student", id);
  applied.push({ operationId, status: "APPLIED", entityType: "student", entityId: id, version });
}

async function applyCourse(env: Env, principal: Principal, deviceId: string | null, operationId: string, operation: Row, applied: unknown[], conflicts: unknown[]): Promise<void> {
  const payload = payloadOf(operation);
  const legacy = text(payload.legacyId, text(operation.entityId));
  const id = await fromLegacy("course", legacy);
  const current = await first(env, "select * from course_sessions where id = ? and deleted_at is null", id);
  const base = Number(operation.baseVersion ?? 0);
  if (current && Number(current.version) !== base && operation.force !== true) {
    await recordOperation(env, operationId, deviceId, "courseSession", id, base, Number(current.version), "CONFLICT");
    conflicts.push(conflict(operationId, "courseSession", id, base, Number(current.version), camelize(current)));
    return;
  }
  const studentId = await fromLegacy("student", text(payload.studentId));
  if (!await first(env, "select id from students where id = ?", studentId)) {
    return badRequest("STUDENT_NOT_FOUND", "课程记录引用的学生不存在");
  }
  let version: number;
  if (!current) {
    await run(env,
      `insert into course_sessions (id, legacy_id, student_id, date, start_time, end_time, subject_id,
        lesson_title, lesson_content, performance, problems, homework_summary, next_lesson_plan,
        teacher_notes, learning_status, version) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)`,
      id, legacy, studentId, dateOnly(payload.date), text(payload.startTime), text(payload.endTime), payload.subjectId ?? null,
      text(payload.lessonTitle, text(payload.title)), text(payload.lessonContent, text(payload.content)),
      text(payload.performance), text(payload.problems), text(payload.homeworkSummary), text(payload.nextLessonPlan),
      text(payload.teacherNotes), text(payload.learningStatus));
    version = 1;
  } else {
    const sets: string[] = [];
    const params: unknown[] = [];
    for (const field of COURSE_FIELDS) {
      if (Object.hasOwn(payload, field)) {
        sets.push(`${snake(field)} = ?`);
        params.push(field === "date" ? dateOnly(payload[field]) : payload[field] ?? null);
      }
    }
    version = Number(current.version);
    if (sets.length) {
      sets.push("version = version + 1", "updated_at = ?");
      params.push(now(), id, current.version);
      const result = await run(env, `update course_sessions set ${sets.join(", ")} where id = ? and version = ?`, ...params);
      if (!result.meta.changes) throw new Error("course sync version changed during write");
      version++;
    }
  }
  await recordOperation(env, operationId, deviceId, "courseSession", id, base, version, "APPLIED");
  await recordChange(env, "courseSession", id, "UPSERT", version, { id }, deviceId);
  await audit(env, principal, "sync.course.upsert", "CourseSession", id);
  applied.push({ operationId, status: "APPLIED", entityType: "courseSession", entityId: id, version });
}

async function applyQuestion(env: Env, principal: Principal, deviceId: string | null, operationId: string, operation: Row, applied: unknown[], conflicts: unknown[]): Promise<void> {
  const payload = payloadOf(operation);
  const rawId = text(payload.id, text(operation.entityId));
  const id = await fromLegacy("question", rawId);
  let current: any = null;
  try {
    current = await getQuestion(env, id);
  } catch (error) {
    if (!(error instanceof Error) || !("status" in error) || error.status !== 404) throw error;
  }
  const base = Number(operation.baseVersion ?? 0);
  if (current && Number(current.question.version) !== base && operation.force !== true) {
    await recordOperation(env, operationId, deviceId, "question", id, base, Number(current.question.version), "CONFLICT");
    conflicts.push(conflict(operationId, "question", id, base, Number(current.question.version), current));
    return;
  }
  const input: Row = {
    subjectId: payload.subjectId ?? current?.question.subjectId,
    grade: text(payload.grade, current?.question.grade ?? ""),
    textbookVersion: text(payload.textbookVersion, current?.question.textbookVersion ?? ""),
    questionType: text(payload.questionType, current?.question.questionType ?? "OTHER"),
    difficulty: Number(payload.difficulty ?? current?.question.difficulty ?? 3),
    stem: text(payload.stem, current?.question.stem ?? ""),
    sourceType: text(payload.sourceType, current?.question.sourceType ?? "TEACHER_IMPORTED"),
    sourceName: text(payload.sourceName, current?.question.sourceName ?? ""),
    copyrightStatus: text(payload.copyrightStatus, current?.question.copyrightStatus ?? "PERSONAL_USE"),
    usageScope: text(payload.usageScope, current?.question.usageScope ?? "PRIVATE_TEACHING"),
    reviewStatus: text(payload.reviewStatus, current?.question.reviewStatus ?? "DRAFT"),
  };
  const detail = current
    ? await updateQuestion(env, id, { ...input, baseVersion: current.question.version }, principal) as any
    : await createQuestion(env, input, principal) as any;
  const resultId = detail.question.id;
  await recordOperation(env, operationId, deviceId, "question", resultId, base, detail.question.version, "APPLIED");
  applied.push({ operationId, status: "APPLIED", entityType: "question", entityId: resultId, version: detail.question.version });
}

function payloadOf(operation: Row): Row {
  return operation.payload && typeof operation.payload === "object" ? operation.payload as Row : {};
}

function conflict(operationId: string, entityType: string, entityId: string, baseVersion: number, currentVersion: number, remote: unknown): Row {
  return { operationId, entityType, entityId, baseVersion, currentVersion, remote, choices: CHOICES };
}

async function recordOperation(env: Env, operationId: string, deviceId: string | null, entityType: string, entityId: string, baseVersion: number, resultVersion: number, status: string): Promise<void> {
  await run(env,
    `insert into sync_operations (id, operation_id, device_id, entity_type, entity_id, base_version, result_version, status)
     values (?, ?, ?, ?, ?, ?, ?, ?)`,
    uuid(), operationId, deviceId, entityType, entityId, baseVersion, resultVersion, status);
}

function dateOnly(value: unknown): string | null {
  const raw = text(value);
  return raw ? raw.slice(0, 10) : null;
}
