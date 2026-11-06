import { auditAi } from "../audit";
import { all, first, text } from "../db";
import { camelize, HttpError, json, notFound } from "../http";
import type { Env, Principal, Row } from "../types";
import { analyzePaper } from "./exams";
import { generatePractice, masteryRows, practiceDetail } from "./practices";
import { getQuestion, searchQuestions } from "./questions";

interface Tool {
  name: string;
  description: string;
  write: boolean;
  inputSchema: Row;
}

export async function handleMcp(request: Request, env: Env, principal: Principal): Promise<Response | null> {
  if (request.method !== "POST" || new URL(request.url).pathname !== "/api/v1/mcp") return null;
  const input = await request.json() as Row;
  const id = input.id;
  const response: Row = { jsonrpc: "2.0" };
  if (id !== undefined) response.id = id;
  try {
    const method = text(input.method);
    if (method === "notifications/initialized") return new Response(null, { status: 204 });
    if (method === "initialize") {
      response.result = {
        protocolVersion: "2024-11-05", capabilities: { tools: {} },
        serverInfo: { name: "teachtrack", version: "4.0.0-alpha.1" },
      };
    } else if (method === "tools/list") {
      response.result = { tools: TOOLS.map(({ name, description, inputSchema }) => ({ name, description, inputSchema })) };
    } else if (method === "tools/call") {
      response.result = await callTool(env, input.params as Row, principal, text(id));
    } else {
      response.error = { code: -32601, message: `Method not found: ${method}` };
    }
  } catch (error) {
    response.error = {
      code: error instanceof HttpError ? -32000 : -32603,
      message: error instanceof Error ? error.message : "Internal error",
    };
  }
  return json(response);
}

async function callTool(env: Env, params: Row | undefined, principal: Principal, requestId: string): Promise<Row> {
  const name = text(params?.name);
  const args = params?.arguments && typeof params.arguments === "object" ? params.arguments as Row : {};
  const tool = TOOLS.find((candidate) => candidate.name === name);
  if (!tool) throw new HttpError(400, "UNKNOWN_TOOL", `Unknown tool: ${name}`);
  if (!principal.scopes.includes("ai.read") && !principal.scopes.includes("owner")) {
    throw new HttpError(403, "FORBIDDEN", "Token lacks ai.read");
  }
  if (tool.write && !principal.scopes.includes("ai.write.draft") && !principal.scopes.includes("owner")) {
    throw new HttpError(403, "FORBIDDEN", "Token lacks ai.write.draft");
  }
  try {
    const data = await executeTool(env, name, args, principal);
    await auditAi(env, principal, requestId, `mcp.${name}`, entityType(name), "SUCCESS");
    return { content: [{ type: "text", text: JSON.stringify(data) }], isError: false };
  } catch (error) {
    await auditAi(env, principal, requestId, `mcp.${name}`, entityType(name), "FAILURE", "TOOL_ERROR");
    throw error;
  }
}

async function executeTool(env: Env, name: string, args: Row, principal: Principal): Promise<unknown> {
  switch (name) {
    case "search_students": {
      const query = text(args.query);
      const limit = numberArg(args.limit, 20, 1, 100);
      const rows = await all(env,
        `select id, legacy_id, display_name, full_name, status, gender, current_grade, grade_at_record,
           school, subject_id, notes, version, updated_at from students
         where deleted_at is null and (display_name like ? or coalesce(full_name,'') like ?)
         order by updated_at desc limit ?`, `%${query}%`, `%${query}%`, limit);
      return camelize(rows);
    }
    case "get_student_profile": {
      const student = await requireStudent(env, args);
      const sessions = await first(env, "select count(*) as n from course_sessions where student_id = ? and deleted_at is null", student.id);
      return camelize({
        id: student.id, legacy_id: student.legacy_id, display_name: student.display_name, full_name: student.full_name,
        status: student.status, current_grade: student.current_grade, grade_at_record: student.grade_at_record,
        school: student.school, notes: student.notes, sessions: Number(sessions?.n ?? 0),
        mastery: await masteryRows(env, text(student.id)),
      });
    }
    case "get_student_sessions": {
      const student = await requireStudent(env, args);
      return camelize(await all(env,
        `select id, legacy_id, student_id, date, start_time, end_time, lesson_title, lesson_content,
           performance, problems, homework_summary, next_lesson_plan, teacher_notes, version
         from course_sessions where student_id = ? and deleted_at is null order by date desc limit ?`,
        student.id, numberArg(args.limit, 50, 1, 200)));
    }
    case "get_student_mastery": {
      const student = await requireStudent(env, args);
      return camelize(await masteryRows(env, text(student.id)));
    }
    case "get_student_mistakes": {
      const student = await requireStudent(env, args);
      return camelize(await all(env,
        `select id, question_id, knowledge_point_id, mistake_type, mistake_reason, repeat_count, status, last_seen_at
         from mistake_records where student_id = ? order by last_seen_at desc limit ?`,
        student.id, numberArg(args.limit, 50, 1, 200)));
    }
    case "search_questions": {
      const url = new URL("http://mcp.local/api/v1/questions");
      for (const field of ["subjectId", "grade", "questionType", "knowledgePointId", "sourceType", "reviewStatus", "minDifficulty", "maxDifficulty", "offset"]) {
        if (args[field] !== undefined && args[field] !== null) url.searchParams.set(field, String(args[field]));
      }
      url.searchParams.set("q", text(args.query));
      url.searchParams.set("limit", String(numberArg(args.limit, 20, 1, 100)));
      return await searchQuestions(env, url);
    }
    case "get_question":
      return await getQuestion(env, text(args.id));
    case "analyze_exam":
      return await analyzePaper(env, text(args.examId, text(args.paperId)), {
        text: text(args.text), questions: Array.isArray(args.questions) ? args.questions : [],
      }, principal);
    case "generate_practice":
    case "save_practice_draft":
      return await generatePractice(env, {
        studentId: args.studentId, subjectId: args.subjectId, knowledgePointIds: args.knowledgePointIds,
        questionCount: numberArg(args.questionCount, 10, 1, 50), minDifficulty: args.minDifficulty,
        maxDifficulty: args.maxDifficulty, totalScore: args.totalScore, includeMistakes: args.includeMistakes ?? false,
        allowAi: args.allowAi ?? true, mode: text(args.mode, "BASIC"), title: args.title,
      }, principal);
    case "get_practice":
      return await practiceDetail(env, text(args.id));
    default:
      throw new HttpError(400, "UNKNOWN_TOOL", `Unknown tool: ${name}`);
  }
}

async function requireStudent(env: Env, args: Row): Promise<Row> {
  const id = text(args.studentId, text(args.id));
  const student = await first(env, "select * from students where deleted_at is null and (id = ? or legacy_id = ?)", id, id);
  if (!student) return notFound(`学生不存在: ${id}`);
  return student;
}

function numberArg(value: unknown, fallback: number, min: number, max: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(min, Math.min(Math.trunc(parsed), max)) : fallback;
}

function entityType(name: string): string {
  if (name.includes("student")) return "Student";
  if (name.includes("question")) return "Question";
  if (name === "analyze_exam") return "ExamPaper";
  if (name.includes("practice")) return "PracticeSet";
  return "Tool";
}

function schema(properties: Row, required?: string[]): Row {
  return { type: "object", properties, ...(required ? { required } : {}) };
}

function prop(type: string, description: string): Row {
  return { type, description };
}

const TOOLS: Tool[] = [
  { name: "search_students", description: "Search students by display name. No parent contact data.", write: false, inputSchema: schema({ query: prop("string", "name fragment"), limit: prop("integer", "max results") }, ["query"]) },
  { name: "get_student_profile", description: "Get a de-identified student profile with mastery summary.", write: false, inputSchema: schema({ studentId: prop("string", "UUID or legacy id") }, ["studentId"]) },
  { name: "get_student_sessions", description: "List course sessions for a student.", write: false, inputSchema: schema({ studentId: prop("string", "UUID or legacy id"), limit: prop("integer", "max results") }, ["studentId"]) },
  { name: "get_student_mastery", description: "Get mastery scores and confidence per knowledge point.", write: false, inputSchema: schema({ studentId: prop("string", "UUID or legacy id") }, ["studentId"]) },
  { name: "get_student_mistakes", description: "List mistake records for a student.", write: false, inputSchema: schema({ studentId: prop("string", "UUID or legacy id"), limit: prop("integer", "max results") }, ["studentId"]) },
  { name: "search_questions", description: "Search the approved/teacher-owned question bank.", write: false, inputSchema: schema({ query: prop("string", "stem fragment"), subjectId: prop("string", "subject UUID"), grade: prop("string", "grade"), questionType: prop("string", "type"), reviewStatus: prop("string", "review status"), limit: prop("integer", "max results") }) },
  { name: "get_question", description: "Get one question with options, answer and analysis.", write: false, inputSchema: schema({ id: prop("string", "question UUID") }, ["id"]) },
  { name: "analyze_exam", description: "Analyze a previously imported exam paper. Creates an AI-parsed draft.", write: true, inputSchema: schema({ examId: prop("string", "exam paper UUID"), text: prop("string", "OCR text"), questions: prop("array", "structured questions") }, ["examId"]) },
  { name: "generate_practice", description: "Generate a practice set as DRAFT. Requires teacher review.", write: true, inputSchema: schema({ studentId: prop("string", "UUID"), subjectId: prop("string", "UUID"), knowledgePointIds: prop("array", "UUID list"), questionCount: prop("integer", "count"), mode: prop("string", "practice mode"), allowAi: prop("boolean", "allow AI originals") }, ["studentId", "subjectId"]) },
  { name: "save_practice_draft", description: "Save a generated practice set as DRAFT.", write: true, inputSchema: schema({ studentId: prop("string", "UUID"), subjectId: prop("string", "UUID"), knowledgePointIds: prop("array", "UUID list"), questionCount: prop("integer", "count") }, ["studentId", "subjectId"]) },
  { name: "get_practice", description: "Get a practice set with items and current mastery.", write: false, inputSchema: schema({ id: prop("string", "practice UUID") }, ["id"]) },
];
