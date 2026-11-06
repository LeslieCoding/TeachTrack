import { all, first, now, run, sha256Hex, text, uuid } from "../db";
import { badRequest, camelize, intParam, json, notFound, parseJson } from "../http";
import type { Env, Principal, Row } from "../types";

const studentColumns = `id, legacy_id, full_name, display_name, status, gender, current_grade,
  grade_at_record, name_completeness, school, class_name, subject_id, notes, version, updated_at`;

export async function handleCore(
  request: Request,
  env: Env,
  url: URL,
  principal: Principal,
): Promise<Response | null> {
  const path = url.pathname;
  const method = request.method;

  if (method === "GET" && path === "/api/v1/system/database") {
    await first<Row>(env, "select 1 as connected");
    return json({
      engine: "sqlite",
      host: "cloudflare-d1",
      port: 0,
      database: "teachtrack",
      user: "worker",
      version: "Cloudflare D1",
      ssl: true,
      mode: "CLOUD_OR_REMOTE",
      note: "Cloudflare Workers + D1 managed cloud database. No local computer is required.",
    });
  }

  if (method === "GET" && path === "/api/v1/system/storage") {
    return json({ provider: env.FILES ? "r2" : "unconfigured" });
  }

  if (method === "GET" && path === "/api/v1/students") {
    const status = url.searchParams.get("status");
    const query = url.searchParams.get("q");
    const limit = intParam(url.searchParams.get("limit"), 50, 1, 200);
    const offset = intParam(url.searchParams.get("offset"), 0, 0, Number.MAX_SAFE_INTEGER);
    const conditions = ["deleted_at is null"];
    const params: unknown[] = [];
    if (status) {
      conditions.push("status = ?");
      params.push(status);
    }
    if (query) {
      conditions.push("(display_name like ? or coalesce(full_name,'') like ?)");
      params.push(`%${query}%`, `%${query}%`);
    }
    const where = conditions.join(" and ");
    const items = await all(env,
      `select ${studentColumns} from students where ${where} order by updated_at desc limit ? offset ?`,
      ...params, limit, offset,
    );
    const count = await first<Row>(env, `select count(*) as total from students where ${where}`, ...params);
    return json({ items: camelize(items), limit, offset, total: Number(count?.total ?? 0) });
  }

  const sessions = method === "GET" ? path.match(/^\/api\/v1\/students\/([^/]+)\/sessions$/) : null;
  if (sessions) {
    const student = await findStudent(env, decodeURIComponent(sessions[1]));
    if (!student) return notFound(`学生不存在: ${decodeURIComponent(sessions[1])}`);
    const limit = intParam(url.searchParams.get("limit"), 100, 1, 500);
    const items = await all(env,
      `select id, legacy_id, student_id, date, start_time, end_time, lesson_title,
              lesson_content, performance, problems, homework_summary, next_lesson_plan,
              teacher_notes, version
       from course_sessions where student_id = ? and deleted_at is null
       order by date desc, start_time desc limit ?`, student.id, limit,
    );
    return json(camelize(items));
  }

  const studentMatch = method === "GET" ? path.match(/^\/api\/v1\/students\/([^/]+)$/) : null;
  if (studentMatch) {
    const id = decodeURIComponent(studentMatch[1]);
    const student = await findStudent(env, id);
    return student ? json(camelize(student)) : notFound(`学生不存在: ${id}`);
  }

  if (method === "GET" && path === "/api/v1/subjects") {
    return json(camelize(await all(env, "select id, code, name, stage, sort_order from subjects order by sort_order, name")));
  }

  if (method === "GET" && path === "/api/v1/knowledge-points") {
    const conditions = ["deleted_at is null"];
    const params: unknown[] = [];
    const subjectId = url.searchParams.get("subjectId");
    const query = url.searchParams.get("q");
    if (subjectId) {
      conditions.push("subject_id = ?");
      params.push(subjectId);
    }
    if (query) {
      conditions.push("name like ?");
      params.push(`%${query}%`);
    }
    const limit = intParam(url.searchParams.get("limit"), 200, 1, 500);
    return json(camelize(await all(env,
      `select id, legacy_id, parent_id, subject_id, grade, textbook_version, chapter, section,
              name, code, description, sort_order from knowledge_points
       where ${conditions.join(" and ")} order by sort_order, name limit ?`, ...params, limit,
    )));
  }

  if (method === "POST" && path === "/api/v1/knowledge-points") {
    const payload = await request.json() as Row;
    if (!payload.subjectId || !text(payload.name).trim()) {
      return badRequest("INVALID_KNOWLEDGE_POINT", "学科与名称必填");
    }
    const subject = await first(env, "select id from subjects where id = ?", payload.subjectId);
    if (!subject) return badRequest("SUBJECT_NOT_FOUND", "学科不存在");
    const id = uuid();
    const code = text(payload.code).trim() || `KP-${(await sha256Hex(`${payload.subjectId}:${payload.name}`)).slice(0, 12)}`;
    await run(env,
      `insert into knowledge_points
       (id, parent_id, subject_id, grade, textbook_version, chapter, section, name, code, description, sort_order)
       values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      id, payload.parentId ?? null, payload.subjectId, text(payload.grade), text(payload.textbookVersion),
      text(payload.chapter), text(payload.section), text(payload.name).trim(), code,
      text(payload.description), Number(payload.sortOrder ?? 0),
    );
    const created = await first(env, "select * from knowledge_points where id = ?", id);
    return json(camelize(created), 201);
  }

  if (method === "GET" && path === "/api/v1/sync/changes") {
    const cursor = intParam(url.searchParams.get("cursor"), 0, 0, Number.MAX_SAFE_INTEGER);
    const limit = intParam(url.searchParams.get("limit"), 500, 1, 1000);
    const changes = await all(env,
      `select seq, entity_type, entity_id, operation, version, device_id, payload, changed_at
       from sync_changes where seq > ? order by seq limit ?`, cursor, limit,
    );
    const normalized = changes.map((change) => ({ ...change, payload: parseJson(change.payload) }));
    return json({
      changes: camelize(normalized),
      nextCursor: changes.length ? Number(changes[changes.length - 1].seq) : cursor,
      hasMore: changes.length === limit,
    });
  }

  return null;
}

async function findStudent(env: Env, id: string): Promise<Row | null> {
  return await first(env,
    `select ${studentColumns} from students where deleted_at is null and (id = ? or legacy_id = ?)`, id, id,
  );
}

export async function recordChange(
  env: Env,
  entityType: string,
  entityId: string,
  operation: "UPSERT" | "DELETE",
  version: number,
  payload: unknown,
  deviceId?: string | null,
): Promise<void> {
  await run(env,
    `insert into sync_changes (entity_type, entity_id, operation, version, device_id, payload, changed_at)
     values (?, ?, ?, ?, ?, ?, ?)`,
    entityType, entityId, operation, version, deviceId ?? null,
    payload === undefined ? null : JSON.stringify(payload), now(),
  );
}
