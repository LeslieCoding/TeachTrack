import fontkit from "@pdf-lib/fontkit";
import { Document, HeadingLevel, ImageRun, Packer, Paragraph, TextRun } from "docx";
import { PDFDocument, PDFFont, PDFPage, StandardFonts } from "pdf-lib";
import { audit } from "../audit";
import { all, first, now, run, text, uuid } from "../db";
import { badRequest, HttpError, json, notFound } from "../http";
import type { Env, Principal, Row } from "../types";
import { allImages, getAssetBytes, putObject, signedDownloadUrl, storageProvider } from "./assets";
import fontData from "../../assets/cjk-font.bin";

const PDF_MIME = "application/pdf";
const DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

export async function handleExport(request: Request, env: Env, url: URL, principal: Principal): Promise<Response | null> {
  const match = new URL(request.url).pathname.match(/^\/api\/v1\/practices\/([^/]+)\/export$/);
  if (!match || request.method !== "GET") return null;
  if (!env.FILES && !env.MEDIA) throw new HttpError(503, "STORAGE_NOT_CONFIGURED", "导出需要文件存储");
  const format = text(url.searchParams.get("format"), "PDF").toUpperCase();
  const variant = text(url.searchParams.get("variant"), "STUDENT").toUpperCase();
  if (!["PDF", "DOCX"].includes(format)) return badRequest("INVALID_FORMAT", "仅支持 PDF 或 DOCX");
  if (!["STUDENT", "TEACHER"].includes(variant)) return badRequest("INVALID_VARIANT", "仅支持 STUDENT 或 TEACHER");
  const practiceId = match[1];
  const detail = await exportData(env, practiceId);
  const content = format === "PDF"
    ? await renderPdf(detail, variant === "TEACHER")
    : await renderDocx(detail, variant === "TEACHER");
  const extension = format.toLowerCase();
  const key = `exports/${practiceId}/${uuid()}-${variant.toLowerCase()}.${extension}`;
  const mime = format === "PDF" ? PDF_MIME : DOCX_MIME;
  await putObject(env, key, content, mime);
  const assetId = uuid();
  const digest = await crypto.subtle.digest("SHA-256", content);
  const sha256 = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  await run(env,
    `insert into assets (id, owner_type, owner_id, storage_provider, storage_key, mime_type, size_bytes, sha256, status, created_at, updated_at)
     values (?, 'PRACTICE', ?, ?, ?, ?, ?, ?, 'READY', ?, ?)`,
    assetId, practiceId, storageProvider(env), key, mime, content.byteLength, sha256, now(), now());
  const downloadUrl = await signedDownloadUrl(env, url.origin, key);
  await audit(env, principal, "practice.export", "PracticeSet", practiceId);
  return json({ assetId, format, variant, downloadUrl });
}

interface ExportData {
  title: string;
  studentName: string;
  date: string;
  items: Row[];
}

async function exportData(env: Env, practiceId: string): Promise<ExportData> {
  const set = await first(env, "select * from practice_sets where id = ? and deleted_at is null", practiceId);
  if (!set) return notFound(`练习不存在: ${practiceId}`);
  const student = await first(env, "select display_name from students where id = ?", set.student_id);
  const items = await all(env,
    `select q.stem, q.question_type, q.difficulty, q.ability_tags, pi.question_id, pi.score, pi.selection_reason,
       coalesce(qa.standard_answer, '') as standard_answer, coalesce(qa.analysis, '') as analysis
     from practice_items pi join questions q on q.id = pi.question_id
     left join question_answers qa on qa.question_id = q.id
     where pi.practice_set_id = ? order by pi.sort_order`, practiceId);
  for (const item of items) {
    const names = await all(env,
      `select kp.name from question_knowledge qk join knowledge_points kp on kp.id = qk.knowledge_point_id
       where qk.question_id = ? order by qk.importance`, item.question_id);
    item.knowledge = names.map((row) => row.name).join("、");
    const images = await allImages(env, text(item.question_id));
    const imageList: Array<{ data: ArrayBuffer; mime: string }> = [];
    for (const image of images) {
      const data = await getAssetBytes(env, image);
      if (!data) throw new HttpError(409, "QUESTION_IMAGE_MISSING", `题目 ${text(item.question_id)} 的图片文件不可读`);
      imageList.push({ data, mime: text(image.mime_type) });
    }
    item.images = imageList;
    if (text(item.ability_tags).includes("含图待补") && imageList.length === 0) {
      throw new HttpError(409, "QUESTION_IMAGE_MISSING", `题目 ${text(item.question_id)} 需要配图但尚未上传`);
    }
  }
  return {
    title: text(set.title), studentName: text(student?.display_name), date: new Date().toISOString().slice(0, 10), items,
  };
}

async function renderDocx(data: ExportData, teacher: boolean): Promise<Uint8Array<ArrayBuffer>> {
  const children: Paragraph[] = [
    new Paragraph({ text: data.title, heading: HeadingLevel.TITLE }),
    new Paragraph({ text: `姓名：${data.studentName}    日期：${data.date}` }),
    new Paragraph({ text: teacher ? "教师版（含答案与解析）" : "学生版" }),
  ];
  data.items.forEach((item, index) => {
    children.push(new Paragraph({
      children: [new TextRun({ text: `${index + 1}. ${text(item.stem)}（${Number(item.score)} 分）`, bold: true })],
      spacing: { before: 240 },
    }));
    for (const image of (item.images as Array<{ data: ArrayBuffer; mime: string }>)) {
      children.push(new Paragraph({
        children: [new ImageRun({
          data: new Uint8Array(image.data),
          type: image.mime.includes("png") ? "png" : "jpg",
          transformation: { width: 400, height: 300 },
        })],
      }));
    }
    if (teacher) {
      children.push(new Paragraph({ text: `答案：${text(item.standard_answer)}` }));
      children.push(new Paragraph({ text: `解析：${text(item.analysis)}` }));
      children.push(new Paragraph({
        text: `知识点：${text(item.knowledge)}    题型：${text(item.question_type)}    难度：${item.difficulty}`,
      }));
      children.push(new Paragraph({ text: `选题理由：${text(item.selection_reason)}` }));
    } else {
      children.push(new Paragraph({ text: "答：\n\n" }));
    }
  });
  const document = new Document({
    sections: [{ children }],
    styles: { default: { document: { run: { font: "Noto Sans SC" } } } },
  });
  const buffer = await Packer.toBuffer(document);
  const content = new Uint8Array(buffer.byteLength);
  content.set(buffer);
  return content;
}

async function renderPdf(data: ExportData, teacher: boolean): Promise<Uint8Array<ArrayBuffer>> {
  const pdf = await PDFDocument.create();
  pdf.registerFontkit(fontkit);
  const font = await pdf.embedFont(fontData, { subset: true });
  const bold = await pdf.embedFont(StandardFonts.HelveticaBold);
  let page = pdf.addPage([595.28, 841.89]);
  let y = 800;
  const write = (value: string, size = 11, useBold = false) => {
    const selected = useBold ? bold : font;
    for (const line of wrapText(value, selected, size, 495)) {
      if (y < 55) {
        page = pdf.addPage([595.28, 841.89]);
        y = 800;
      }
      page.drawText(line, { x: 50, y, size, font: selected });
      y -= size * 1.65;
    }
  };
  write(data.title, 18);
  write(`姓名：${data.studentName}    日期：${data.date}`, 11);
  write(teacher ? "教师版（含答案与解析）" : "学生版", 11);
  for (const [index, item] of data.items.entries()) {
    y -= 12;
    write(`${index + 1}. ${text(item.stem)}（${Number(item.score)} 分）`, 12);
    for (const image of (item.images as Array<{ data: ArrayBuffer; mime: string }>)) {
      const embedded = image.mime.includes("png") ? await pdf.embedPng(image.data) : await pdf.embedJpg(image.data);
      const scale = Math.min(1, 450 / embedded.width, 300 / embedded.height);
      const width = embedded.width * scale;
      const height = embedded.height * scale;
      if (y - height < 55) {
        page = pdf.addPage([595.28, 841.89]);
        y = 800;
      }
      page.drawImage(embedded, { x: 50, y: y - height, width, height });
      y -= height + 12;
    }
    if (teacher) {
      write(`答案：${text(item.standard_answer)}`);
      write(`解析：${text(item.analysis)}`);
      write(`知识点：${text(item.knowledge)}    题型：${text(item.question_type)}    难度：${item.difficulty}`);
      write(`选题理由：${text(item.selection_reason)}`);
    } else {
      write("答：");
      y -= 45;
    }
  }
  const saved = await pdf.save();
  const content = new Uint8Array(saved.byteLength);
  content.set(saved);
  return content;
}

function wrapText(value: string, font: PDFFont, size: number, maxWidth: number): string[] {
  const lines: string[] = [];
  for (const paragraph of value.replaceAll("\r", "").split("\n")) {
    let line = "";
    for (const char of paragraph) {
      const candidate = line + char;
      let width = 0;
      try { width = font.widthOfTextAtSize(candidate, size); } catch { width = candidate.length * size; }
      if (line && width > maxWidth) {
        lines.push(line);
        line = char;
      } else {
        line = candidate;
      }
    }
    lines.push(line);
  }
  return lines;
}
