import { all, first, now, run, sha256Hex, text, uuid } from "../db";
import { badRequest, HttpError, json } from "../http";
import { fromLegacy, snake, uuidV5 } from "../ids";
import type { Env, Principal, Row } from "../types";

export async function handleMigrations(request: Request, env: Env, principal: Principal): Promise<Response | null> {
  if (request.method !== "POST" || new URL(request.url).pathname !== "/api/v1/admin/migrations") return null;
  if (!principal.scopes.includes("owner")) throw new HttpError(403, "FORBIDDEN", "仅 OWNER 可执行迁移");
  const input = await request.json() as Row;
  if (!input.data || typeof input.data !== "object") {
    return badRequest("MIGRATION_DATA_REQUIRED", "Workers 不能读取你电脑的 path；请把迁移 JSON 放在 data 字段中上传");
  }
  const root = input.data as Row;
  const serialized = JSON.stringify(root);
  const sourceSha256 = await sha256Hex(serialized);
  const format = detectFormat(root, text(input.format));
  const batchId = uuid();
  const dryRun = input.dryRun === true;
  const importer = new Importer(env, batchId, dryRun);
  if (!dryRun) {
    await run(env,
      "insert into import_jobs (id, source_name, source_sha256, source_format, status, report) values (?, ?, ?, ?, 'RUNNING', '{}')",
      batchId, text(input.sourceName, "inline-json"), sourceSha256, format);
  }
  try {
    await importer.dispatch(root, format);
    const result = importer.result(batchId, format, text(input.sourceName, "inline-json"), sourceSha256);
    if (!dryRun) {
      await run(env,
        "update import_jobs set status = 'COMPLETED', students_imported = ?, questions_imported = ?, issues_count = ?, report = ?, finished_at = ? where id = ?",
        result.students, result.questions, result.issues, JSON.stringify(result), now(), batchId);
    }
    return json(result);
  } catch (error) {
    if (!dryRun) {
      await run(env,
        "update import_jobs set status = 'FAILED', issues_count = 1, report = ?, finished_at = ? where id = ?",
        JSON.stringify({ error: error instanceof Error ? error.message : String(error) }), now(), batchId);
    }
    throw error;
  }
}

function detectFormat(root: Row, hint: string): string {
  if (hint) {
    const normalized = hint.toUpperCase();
    if (["SEED_V1", "BACKUP_JSON", "TTSEED"].includes(normalized)) return normalized;
  }
  const format = text(root.format).toLowerCase();
  if (format.includes("ttseed")) return "TTSEED";
  if (format.includes("backup")) return "BACKUP_JSON";
  if (Array.isArray(root.students) && root.students.some((item) => (item as Row).knowledge || (item as Row).courses)) return "SEED_V1";
  if (Array.isArray(root.students)) return "BACKUP_JSON";
  return badRequest("UNKNOWN_MIGRATION_FORMAT", "无法识别迁移格式，请指定 format=SEED_V1/BACKUP_JSON/TTSEED");
}

class Importer {
  readonly students = new Map<string, string>();
  readonly knowledge = new Set<string>();
  private readonly subjects = new Map<string, string>();
  private readonly issues: Row[] = [];
  private readonly counts = { students: 0, courses: 0, scores: 0, communications: 0, problems: 0, questions: 0 };

  constructor(private readonly env: Env, private readonly batchId: string, private readonly dryRun: boolean) {}

  async dispatch(root: Row, format: string): Promise<void> {
    if (format === "TTSEED") await this.ttseed(root);
    else if (format === "SEED_V1") await this.seedV1(root);
    else await this.backup(root);
  }

  result(batchId: string, format: string, sourceName: string, sourceSha256: string): Row {
    return {
      batchId, format, sourceName, sourceSha256, dryRun: this.dryRun,
      students: this.counts.students, courses: this.counts.courses, scores: this.counts.scores,
      knowledgePoints: this.knowledge.size, communications: this.counts.communications,
      problems: this.counts.problems, questions: this.counts.questions, issues: this.issues.length,
    };
  }

  private async ttseed(root: Row): Promise<void> {
    for (const item of rows(root.students)) {
      const legacy = text(item.id) || `ttseed-student-${(await sha256Hex(`${text(item.displayName)}:${text(item.subject)}`)).slice(0, 16)}`;
      const subjectId = await this.subjectId(text(item.subject));
      const studentId = await this.upsertStudent(legacy, {
        fullName: item.fullName ?? null, displayName: text(item.displayName, text(item.fullName, "未命名")),
        status: safeStatus(text(item.status, "UNKNOWN")), gender: item.gender ?? null,
        currentGrade: item.currentGrade ?? null, gradeAtRecord: text(item.grade) || null,
        nameCompleteness: text(item.nameCompleteness, item.fullName ? "CONFIRMED" : "PARTIAL"),
        school: text(item.school), className: text(item.className), subjectId,
        notes: text(item.notes), startDate: dateOnly(item.startDate),
      });
      for (const alias of stringList(item.alias)) {
        if (!alias.trim()) continue;
        const id = uuid();
        await this.write(
          `insert into student_aliases (id, student_id, alias, alias_type, source) values (?, ?, ?, 'NICKNAME', 'ttseed')
           on conflict (student_id, alias) do nothing`, [id, studentId, alias.trim()]);
      }
      await this.upsertProfile(studentId, {
        academicProfile: item.academicProfile, learningCharacteristics: item.learningCharacteristics,
        strengths: item.strengths, weaknesses: item.weaknesses, riskFlags: item.riskFlags,
      }, item.weaknesses, item.specialRules);
      const contact = item.parentContact;
      if (contact && typeof contact === "object") {
        const value = contact as Row;
        await this.upsertContact(null, legacy, text(value.name), text(value.phone), text(value.relation), value.isPrimary !== false, text(value.notes));
      } else if (text(contact)) {
        await this.upsertContact(null, legacy, text(contact), "", "", true, "");
      }
      let index = 0;
      for (const progress of rows(item.knowledgeProgress)) {
        const name = text(progress.name, text(progress.knowledgePointName));
        const explicitId = text(progress.knowledgePointId);
        let knowledgeId: string;
        if (explicitId) {
          knowledgeId = await fromLegacy("knowledge", explicitId);
        } else if (name) {
          const key = `${text(item.subject)}|${name}`;
          const legacyId = `ttseed-kp-${await uuidV5("knowledge", key)}`;
          knowledgeId = await this.upsertKnowledge(legacyId, {
            parentId: null, subjectId, grade: text(item.grade), name,
            code: `KP-${(await sha256Hex(key)).slice(0, 12)}`, description: "", sortOrder: index,
          });
        } else {
          this.issue("WARN", "knowledgeProgress", legacy, "缺少 name/knowledgePointId，已跳过");
          continue;
        }
        await this.upsertProgress(studentId, knowledgeId, Number(progress.level ?? 0), text(progress.notes));
        index++;
      }
      let scoreIndex = 0;
      for (const score of rows(item.scores)) {
        await this.upsertScore(`ttseed-score-${legacy}-${scoreIndex}`, studentId, null,
          text(score.examName, text(score.name, "历史成绩")), text(score.date), subjectId,
          Number(score.score ?? 0), Number(score.fullScore ?? 0), text(score.scoreLabel), text(score.fullScoreLabel),
          text(score.scoreAccuracy, "EXACT"), text(score.notes));
        scoreIndex++;
      }
      let planIndex = 0;
      for (const plan of rows(item.coursePlans)) {
        await this.upsertPlan(`ttseed-plan-${legacy}-${planIndex}`, studentId, plan);
        planIndex++;
      }
      let communicationIndex = 0;
      for (const communication of rows(item.communications)) {
        await this.upsertCommunication(`ttseed-communication-${legacy}-${communicationIndex}`, studentId, communication);
        communicationIndex++;
      }
      let sessionIndex = 0;
      for (const session of rows(item.courseSessions)) {
        await this.upsertCourse(text(session.id, `ttseed-course-${legacy}-${sessionIndex}`), studentId,
          text(session.subject, text(item.subject)), session);
        sessionIndex++;
      }
      let problemIndex = 0;
      for (const weakness of stringList(item.weaknesses)) {
        await this.upsertProblem(`ttseed-problem-${legacy}-${problemIndex}`, studentId, weakness, weakness, "", "OPEN");
        problemIndex++;
      }
    }
    for (const session of rows(root.courseSessions)) {
      const studentLegacy = text(session.studentId);
      if (!studentLegacy) {
        this.issue("WARN", "courseSession", "?", "顶层 courseSession 缺少 studentId，已跳过");
        continue;
      }
      const legacy = text(session.id) || `ttseed-course-${studentLegacy}-${(await sha256Hex(JSON.stringify(session))).slice(0, 12)}`;
      await this.upsertCourse(legacy, await this.studentIdOf(studentLegacy), text(session.subject), session);
    }
  }

  private async seedV1(root: Row): Promise<void> {
    for (const item of rows(root.students)) {
      const legacy = text(item.id);
      if (!legacy) {
        this.issue("WARN", "student", "?", "缺少 id，已跳过");
        continue;
      }
      const subjectName = text(item.subject);
      const subjectId = await this.subjectId(subjectName);
      const studentId = await this.upsertStudent(legacy, {
        fullName: item.fullName ?? null, displayName: text(item.displayName, text(item.fullName, `未命名-${legacy}`)),
        status: safeStatus(text(item.status, "UNKNOWN")), gender: item.gender ?? null,
        currentGrade: null, gradeAtRecord: text(item.historicalGrade) || null,
        nameCompleteness: text(item.nameCompleteness, item.fullName ? "CONFIRMED" : "PARTIAL"),
        school: "", className: "", subjectId, notes: "", startDate: null,
      });
      await this.upsertProfile(studentId, { strengths: item.strengths, weaknesses: item.weaknesses, focus: item.focus }, item.weaknesses, item.focus);
      const weaknessNames = stringList(item.weaknesses);
      let knowledgeIndex = 0;
      for (const raw of stringList(item.knowledge)) {
        const pieces = raw.split("/", 2);
        const group = pieces.length > 1 ? pieces[0] : "";
        const name = pieces.length > 1 ? pieces[1] : pieces[0];
        let parentId: string | null = null;
        if (group) {
          const groupLegacy = `seed-group-${await uuidV5("knowledge-group", `${subjectName}|${group}`)}`;
          parentId = await this.upsertKnowledge(groupLegacy, {
            parentId: null, subjectId, grade: text(item.historicalGrade), name: group,
            code: `KG-${(await sha256Hex(`${subjectName}|${group}`)).slice(0, 10)}`,
            description: "历史 Seed 自动建立的知识分类", sortOrder: knowledgeIndex,
          });
        }
        const key = `${subjectName}|${group}|${name}`;
        const knowledgeId = await this.upsertKnowledge(`seed-kp-${await uuidV5("knowledge", key)}`, {
          parentId, subjectId, grade: text(item.historicalGrade), name,
          code: `KP-${(await sha256Hex(key)).slice(0, 12)}`, description: "", sortOrder: knowledgeIndex,
        });
        const weak = weaknessNames.some((weakness) => weakness.includes(name) || name.includes(weakness));
        await this.upsertProgress(studentId, knowledgeId, weak ? 2 : 1, "来自历史 Seed；不是 AI 推断");
        knowledgeIndex++;
      }
      let courseIndex = 0;
      for (const course of rows(item.courses)) {
        await this.upsertCourse(`seed-course-${legacy}-${courseIndex}`, studentId, subjectName, {
          date: course.recordDate, lessonTitle: course.title, lessonContent: course.content,
          performance: course.performance, problems: course.problems, nextLessonPlan: course.nextLessonPlan,
          teacherNotes: `历史教学资料；日期精度：${text(course.dateAccuracy, "UNKNOWN")}`,
        });
        courseIndex++;
      }
      let scoreIndex = 0;
      for (const score of rows(item.scores)) {
        await this.upsertScore(`seed-score-${legacy}-${scoreIndex}`, studentId, null,
          text(score.type, "历史成绩"), text(score.exactDate), subjectId, Number(score.score ?? 0), Number(score.fullScore ?? 0),
          text(score.scoreText), text(score.fullScoreText), text(score.scoreAccuracy, "UNKNOWN"), text(score.note));
        scoreIndex++;
      }
      let communicationIndex = 0;
      for (const communication of rows(item.communications)) {
        await this.upsertCommunication(`seed-communication-${legacy}-${communicationIndex}`, studentId, communication, "其他");
        communicationIndex++;
      }
      let problemIndex = 0;
      for (const weakness of weaknessNames) {
        await this.upsertProblem(`seed-problem-${legacy}-${problemIndex}`, studentId, weakness, weakness, "", "OPEN");
        problemIndex++;
      }
      let progressIndex = 0;
      for (const record of rows(item.progress)) {
        await this.upsertStudentProgress(`seed-progress-${legacy}-${progressIndex}`, studentId, record, "历史教学资料");
        progressIndex++;
      }
    }
    for (const item of rows(root.unresolvedHistoricalRecords)) await this.upsertUnresolved(item, "匿名历史教学资料");
  }

  private async backup(root: Row): Promise<void> {
    for (const item of rows(root.students)) {
      const legacy = text(item.id);
      if (!legacy) continue;
      await this.upsertStudent(legacy, {
        fullName: item.name ?? null, displayName: text(item.displayName, text(item.name, "未命名")),
        status: safeStatus(text(item.status, "ACTIVE")), gender: item.gender ?? null,
        currentGrade: item.currentGrade ?? null, gradeAtRecord: text(item.grade) || null,
        nameCompleteness: text(item.nameCompleteness, "CONFIRMED"), school: text(item.school),
        className: text(item.className), subjectId: await this.subjectId(text(item.subject)),
        notes: text(item.notes), startDate: dateOnly(item.startDate),
      });
    }
    const exams = new Map<string, Row>();
    rows(root.exams).forEach((exam) => exams.set(text(exam.id), exam));
    for (const item of rows(root.knowledgePoints)) {
      const legacy = text(item.id);
      if (!legacy) continue;
      await this.upsertKnowledge(legacy, {
        parentId: text(item.parentId) ? await fromLegacy("knowledge", item.parentId) : null,
        subjectId: await this.subjectId(text(item.subject)), grade: text(item.grade),
        name: text(item.name, "未命名知识点"), code: text(item.code) || null,
        description: text(item.description), sortOrder: Number(item.orderIndex ?? 0),
      });
    }
    for (const item of rows(root.knowledgeProgress)) {
      if (!text(item.studentId) || !text(item.knowledgePointId)) continue;
      await this.upsertProgress(await this.studentIdOf(text(item.studentId)),
        await fromLegacy("knowledge", item.knowledgePointId), Number(item.level ?? 0), text(item.notes));
    }
    for (const item of rows(root.scores)) {
      const legacy = text(item.id);
      if (!legacy || !text(item.studentId)) continue;
      const exam = exams.get(text(item.examId));
      await this.upsertScore(legacy, await this.studentIdOf(text(item.studentId)), text(item.examId) || null,
        text(exam?.name, "历史成绩"), text(exam?.date), await this.subjectId(text(exam?.subject)),
        Number(item.score ?? 0), Number(item.fullScore ?? 0), text(item.scoreLabel), text(item.fullScoreLabel),
        text(item.scoreAccuracy, "EXACT"), text(item.notes));
    }
    for (const item of rows(root.scoreItems)) {
      const legacy = text(item.id);
      if (!legacy || !text(item.scoreId)) continue;
      await this.upsertLegacy("score_items", "score-item", legacy, {
        scoreId: await fromLegacy("score", item.scoreId), category: text(item.category), lostScore: Number(item.lostScore ?? 0),
        knowledgePointId: text(item.knowledgePointId) ? await fromLegacy("knowledge", item.knowledgePointId) : null,
        notes: text(item.notes),
      }, false, false, false);
    }
    for (const item of rows(root.courses)) {
      if (!text(item.id) || !text(item.studentId)) continue;
      await this.upsertCourse(text(item.id), await this.studentIdOf(text(item.studentId)), text(item.subject), item);
    }
    for (const item of rows(root.homework)) {
      if (!text(item.id) || !text(item.studentId)) continue;
      await this.upsertLegacy("homework", "homework", text(item.id), {
        studentId: await this.studentIdOf(text(item.studentId)),
        courseId: text(item.courseId) ? await fromLegacy("course", item.courseId) : null,
        name: text(item.name, "作业"), assignedDate: dateOnly(item.assignedDate), dueDate: dateOnly(item.dueDate),
        content: text(item.content), status: text(item.status, "未完成"), teacherComment: text(item.teacherComment),
      });
    }
    for (const item of rows(root.communications)) {
      if (!text(item.id) || !text(item.studentId)) continue;
      await this.upsertCommunication(text(item.id), await this.studentIdOf(text(item.studentId)), item);
    }
    for (const item of rows(root.lessonPlans)) {
      if (!text(item.id) || !text(item.studentId)) continue;
      await this.upsertPlan(text(item.id), await this.studentIdOf(text(item.studentId)), item);
    }
    for (const item of rows(root.tasks)) {
      if (!text(item.id)) continue;
      await this.upsertLegacy("tasks", "task", text(item.id), {
        title: text(item.title, "任务"), studentId: text(item.studentId) ? await this.studentIdOf(text(item.studentId)) : null,
        type: text(item.type, "其他"), dueDate: dateOnly(item.dueDate), priority: text(item.priority, "普通"),
        status: text(item.status, "待处理"), notes: text(item.notes),
      });
    }
    for (const item of rows(root.parentContacts)) {
      await this.upsertContact(text(item.id) || null, text(item.studentId), text(item.name), text(item.phone), text(item.relation), item.isPrimary === true, text(item.notes));
    }
    for (const item of rows(root.problemRecords)) {
      if (!text(item.id) || !text(item.studentId)) continue;
      await this.upsertProblem(text(item.id), await this.studentIdOf(text(item.studentId)), text(item.title), text(item.description), text(item.date), problemStatus(text(item.status, "待复习")));
    }
    for (const item of rows(root.templates)) {
      if (!text(item.id)) continue;
      await this.upsertLegacy("templates", "template", text(item.id), {
        name: text(item.name), type: text(item.type, "课后反馈"), content: text(item.content),
      }, false, true, false);
    }
    for (const item of rows(root.tags)) {
      if (!text(item.id)) continue;
      await this.upsertLegacy("tags", "tag", text(item.id), { name: text(item.name), color: text(item.color, "#176B57") }, false, false, false);
    }
    for (const item of rows(root.studentTags)) {
      if (!text(item.studentId) || !text(item.tagId)) continue;
      await this.write("insert into student_tags (student_id, tag_id) values (?, ?) on conflict do nothing", [
        await this.studentIdOf(text(item.studentId)), await fromLegacy("tag", item.tagId),
      ]);
    }
    for (const item of rows(root.attachments)) {
      const legacy = text(item.id);
      if (!legacy) continue;
      await this.write(
        `insert into assets (id, owner_type, owner_id, storage_provider, storage_key, mime_type, size_bytes, sha256, visibility, status)
         values (?, 'LEGACY_ATTACHMENT', null, 'local', ?, ?, ?, ?, 'PRIVATE', 'PENDING')
         on conflict (storage_provider, storage_key) do nothing`, [
          await fromLegacy("asset", legacy), `legacy/${legacy}`, text(item.mimeType, "application/octet-stream"),
          Number(item.size ?? 0), text(item.sha256),
        ]);
    }
    for (const item of rows(root.historicalLearningRecords)) {
      if (!text(item.id) || !text(item.studentId)) continue;
      await this.upsertLegacy("historical_learning_records", "historical", text(item.id), {
        studentId: await this.studentIdOf(text(item.studentId)), recordDate: dateOnly(item.recordDate),
        dateAccuracy: text(item.dateAccuracy, "UNKNOWN"), subjectId: await this.subjectId(text(item.subject)),
        title: text(item.title), content: text(item.content), source: text(item.source, "历史教学资料"),
      }, true, false, false);
    }
    for (const item of rows(root.studentProgress)) {
      if (!text(item.id) || !text(item.studentId)) continue;
      await this.upsertStudentProgress(text(item.id), await this.studentIdOf(text(item.studentId)), item, text(item.source, "历史教学资料"));
    }
    for (const item of rows(root.unresolvedHistoricalRecords)) await this.upsertUnresolved(item, text(item.source, "历史教学资料"));
  }

  private async upsertStudent(legacy: string, fields: Row): Promise<string> {
    this.counts.students++;
    const id = await this.upsertLegacy("students", "student", legacy, fields);
    this.students.set(legacy, id);
    return id;
  }

  private async upsertKnowledge(legacy: string, fields: Row): Promise<string> {
    if (!text(fields.code)) fields.code = `KP-${(await sha256Hex(legacy)).slice(0, 12)}`;
    const id = await this.upsertLegacy("knowledge_points", "knowledge", legacy, fields);
    this.knowledge.add(id);
    return id;
  }

  private async upsertProfile(studentId: string, profile: Row, weaknesses: unknown, specialRules: unknown): Promise<void> {
    const cleaned = Object.fromEntries(Object.entries(profile).filter(([, value]) => value !== undefined));
    await this.write(
      `insert into student_profiles (student_id, academic_profile, weaknesses, special_rules, updated_at) values (?, ?, ?, ?, ?)
       on conflict (student_id) do update set academic_profile = excluded.academic_profile,
         weaknesses = excluded.weaknesses, special_rules = excluded.special_rules, updated_at = excluded.updated_at`,
      [studentId, JSON.stringify(cleaned), JSON.stringify(Array.isArray(weaknesses) ? weaknesses : []),
        JSON.stringify(Array.isArray(specialRules) ? specialRules : []), now()]);
  }

  private async upsertProgress(studentId: string, knowledgeId: string, level: number, notes: string): Promise<void> {
    await this.write(
      `insert into student_knowledge_progress_legacy (student_id, knowledge_point_id, level, last_updated, notes)
       values (?, ?, ?, ?, ?) on conflict (student_id, knowledge_point_id) do update set
       level = excluded.level, last_updated = excluded.last_updated, notes = excluded.notes`,
      [studentId, knowledgeId, level, now(), notes]);
    await this.write(
      `insert into student_mastery (student_id, knowledge_point_id, mastery_score, confidence, practice_count, formula_version)
       values (?, ?, ?, 0, 0, 'legacy-import') on conflict (student_id, knowledge_point_id) do nothing`,
      [studentId, knowledgeId, Math.min(100, Math.max(0, level * 25))]);
  }

  private async upsertCourse(legacy: string, studentId: string, subject: string, item: Row): Promise<void> {
    const parsed = dateOnly(item.date ?? item.recordDate);
    if (!parsed && text(item.date ?? item.recordDate)) this.issue("WARN", "courseSession", legacy, "日期无法解析，date 置空");
    this.counts.courses++;
    await this.upsertLegacy("course_sessions", "course", legacy, {
      studentId, date: parsed, startTime: text(item.startTime), endTime: text(item.endTime),
      subjectId: await this.subjectId(subject), lessonTitle: text(item.lessonTitle, text(item.title)),
      lessonContent: text(item.lessonContent, text(item.content)), performance: text(item.performance),
      problems: text(item.problems), homeworkSummary: text(item.homeworkSummary),
      nextLessonPlan: text(item.nextLessonPlan), teacherNotes: text(item.teacherNotes),
    });
  }

  private async upsertScore(legacy: string, studentId: string, examLegacy: string | null, name: string, date: string, subjectId: string, score: number, fullScore: number, scoreLabel: string, fullScoreLabel: string, accuracy: string, notes: string): Promise<void> {
    this.counts.scores++;
    await this.upsertLegacy("student_scores", "score", legacy, {
      studentId, examId: examLegacy ? await fromLegacy("exam", examLegacy) : null, examName: name,
      examDate: dateOnly(date), subjectId, score, fullScore, scoreLabel, fullScoreLabel, scoreAccuracy: accuracy, notes,
    }, true, true, false);
  }

  private async upsertCommunication(legacy: string, studentId: string, item: Row, defaultMethod = "微信"): Promise<void> {
    this.counts.communications++;
    await this.upsertLegacy("communications", "communication", legacy, {
      studentId, date: dateOnly(item.date), contact: text(item.contact), method: text(item.method, defaultMethod),
      summary: text(item.summary), parentFeedback: text(item.parentFeedback), followUp: text(item.followUp),
    });
  }

  private async upsertPlan(legacy: string, studentId: string, item: Row): Promise<void> {
    await this.upsertLegacy("lesson_plans", "lesson-plan", legacy, {
      studentId, plannedDate: dateOnly(item.plannedDate ?? item.date), chapter: text(item.chapter),
      knowledgePoints: text(item.knowledgePoints), focus: text(item.focus), difficulty: text(item.difficulty),
      review: text(item.review), newContent: text(item.newContent), homework: text(item.homework), notes: text(item.notes),
    });
  }

  private async upsertContact(legacy: string | null, studentLegacy: string, name: string, phone: string, relation: string, primary: boolean, notes: string): Promise<void> {
    if (!studentLegacy) return;
    const effective = legacy || `contact-${await sha256Hex(`${studentLegacy}:${name}:${relation}`)}`;
    await this.upsertLegacy("parent_contacts", "parent-contact", effective, {
      studentId: await this.studentIdOf(studentLegacy), name, phone, relation, isPrimary: primary ? 1 : 0, notes,
    });
  }

  private async upsertProblem(legacy: string, studentId: string, title: string, description: string, date: string, status: string): Promise<void> {
    if (!studentId) return;
    this.counts.problems++;
    await this.upsertLegacy("mistake_records", "mistake", legacy, {
      studentId, mistakeType: "UNKNOWN", mistakeReason: `${title}\n${description}`.trim(),
      firstSeenAt: timestampOrNow(date), lastSeenAt: timestampOrNow(date), repeatCount: 1, status,
    });
  }

  private async upsertStudentProgress(legacy: string, studentId: string, item: Row, source: string): Promise<void> {
    await this.upsertLegacy("student_progress", "progress", legacy, {
      studentId, date: dateOnly(item.date), dateAccuracy: text(item.dateAccuracy, "UNKNOWN"),
      category: text(item.category), title: text(item.title), detail: text(item.detail), source,
    }, true, false, false);
  }

  private async upsertUnresolved(item: Row, source: string): Promise<void> {
    const legacy = text(item.id) || `unresolved-${(await sha256Hex(`${text(item.title)}:${text(item.content)}`)).slice(0, 16)}`;
    await this.upsertLegacy("unresolved_historical_records", "unresolved", legacy, {
      recordDate: dateOnly(item.recordDate), dateAccuracy: text(item.dateAccuracy, "UNKNOWN"),
      title: text(item.title), content: text(item.content), source,
    }, true, false, false);
  }

  private async upsertLegacy(table: string, kind: string, legacy: string, fields: Row, batch = true, updated = true, versioned = false): Promise<string> {
    const id = await fromLegacy(kind, legacy);
    const data: Row = { id, legacy_id: legacy };
    for (const [key, value] of Object.entries(fields)) data[snake(key)] = normalize(value);
    if (batch) data.import_batch_id = this.batchId;
    if (updated) data.updated_at = now();
    const columns = Object.keys(data);
    const updates = columns.filter((column) => !["id", "legacy_id"].includes(column))
      .map((column) => `${column} = excluded.${column}`);
    if (versioned || ["students", "course_sessions", "mistake_records"].includes(table)) updates.push(`version = ${table}.version + 1`);
    await this.write(
      `insert into ${table} (${columns.join(", ")}) values (${columns.map(() => "?").join(", ")})
       on conflict (legacy_id) do update set ${updates.join(", ")}`,
      columns.map((column) => data[column]));
    return id;
  }

  private async subjectId(nameOrCode: string): Promise<string> {
    const key = nameOrCode.trim().toLowerCase() || "other";
    if (this.subjects.has(key)) return this.subjects.get(key)!;
    let id: string | undefined;
    if (nameOrCode.trim()) {
      const found = await first(this.env, "select id from subjects where lower(name) = lower(?) or lower(code) = lower(?) limit 1", nameOrCode.trim(), nameOrCode.trim());
      id = found?.id as string | undefined;
      if (!id) this.issue("WARN", "subject", nameOrCode, "未知学科，归入 OTHER");
    }
    if (!id) {
      const other = await first(this.env, "select id from subjects where code = 'OTHER'");
      id = text(other?.id);
    }
    this.subjects.set(key, id);
    return id;
  }

  private async studentIdOf(legacy: string): Promise<string> {
    if (this.students.has(legacy)) return this.students.get(legacy)!;
    const found = await first(this.env, "select id from students where legacy_id = ?", legacy);
    const id = text(found?.id) || await fromLegacy("student", legacy);
    this.students.set(legacy, id);
    return id;
  }

  private async write(sql: string, params: unknown[]): Promise<void> {
    if (!this.dryRun) await run(this.env, sql, ...params);
  }

  private issue(severity: string, entityType: string, sourceRef: string, message: string): void {
    if (this.issues.length < 500) this.issues.push({ severity, entityType, sourceRef, message });
  }
}

function rows(value: unknown): Row[] {
  return Array.isArray(value) ? value.filter((item) => item && typeof item === "object") as Row[] : [];
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.map(String) : [];
}

function normalize(value: unknown): unknown {
  if (value === undefined) return null;
  if (typeof value === "boolean") return value ? 1 : 0;
  if (value && typeof value === "object") return JSON.stringify(value);
  return value;
}

function dateOnly(value: unknown): string | null {
  const raw = text(value).trim();
  if (!raw) return null;
  const normalized = raw.replaceAll("/", "-");
  const match = normalized.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
  if (!match) return null;
  return `${match[1]}-${match[2].padStart(2, "0")}-${match[3].padStart(2, "0")}`;
}

function timestampOrNow(value: unknown): string {
  const date = dateOnly(value);
  return date ? `${date}T00:00:00.000Z` : now();
}

function safeStatus(value: string): string {
  const status = value.toUpperCase();
  return ["UNKNOWN", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED"].includes(status) ? status : "UNKNOWN";
}

function problemStatus(value: string): string {
  return value === "已解决" ? "RESOLVED" : value === "忽略" ? "IGNORED" : value === "复习中" ? "REVIEWING" : "OPEN";
}
