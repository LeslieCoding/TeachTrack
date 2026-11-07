#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Import numbered DOCX questions into the TeachTrack Java API.

The parser intentionally keeps uncertain items in DRAFT instead of inventing
missing options, answers or images. Images embedded in a question paragraph
are uploaded to the created question immediately after creation.
"""
import argparse
import json
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

import requests

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
A = "http://schemas.openxmlformats.org/drawingml/2006/main"
PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

QUESTION_START = re.compile(r"^\s*(\d{1,3})\s*[.、．](?!\d)\s*")
OPTION = re.compile(r"^\s*([A-H])\s*[.、．:：)]\s*(.*)$", re.I)
ANSWER = re.compile(r"^【?(?:参考答案|答案)】?\s*[:：]?\s*(.*)$", re.I)
ANALYSIS = re.compile(r"^【?(?:解析|解答|分析)】?\s*[:：]?\s*(.*)$")
DIFFICULTY = re.compile(r"^【?难度】?\s*[:：]?\s*(.*)$", re.I)
KNOWLEDGE = re.compile(r"^【?知识点】?\s*[:：]?\s*(.*)$")
GRADE = re.compile(r"^【?年级】?\s*[:：]?\s*(.*)$")
TEXTBOOK = re.compile(r"^【?(?:教材版本|教材)】?\s*[:：]?\s*(.*)$")
SOURCE = re.compile(r"^【?来源】?\s*[:：]?\s*(.*)$")
TYPE = re.compile(r"^【?题型】?\s*[:：]?\s*(.*)$", re.I)
IMAGE_REFERENCE = re.compile(r"如图|见图|右图|左图|下图|上图|图所示|图甲|图乙|电路图|装置图|图片")

TYPE_MAP = {
    "单选": "SINGLE_CHOICE", "单选题": "SINGLE_CHOICE", "SINGLE_CHOICE": "SINGLE_CHOICE",
    "多选": "MULTI_CHOICE", "多选题": "MULTI_CHOICE", "MULTI_CHOICE": "MULTI_CHOICE",
    "填空": "FILL_BLANK", "填空题": "FILL_BLANK", "FILL_BLANK": "FILL_BLANK",
    "实验": "EXPERIMENT", "实验题": "EXPERIMENT", "实验探究": "EXPERIMENT", "EXPERIMENT": "EXPERIMENT",
    "计算": "CALCULATION", "计算题": "CALCULATION", "CALCULATION": "CALCULATION",
    "简答": "SHORT_ANSWER", "简答题": "SHORT_ANSWER", "SHORT_ANSWER": "SHORT_ANSWER",
    "论述": "ESSAY", "论述题": "ESSAY", "ESSAY": "ESSAY",
    "其他": "OTHER", "OTHER": "OTHER",
}
CHOICE_TYPES = {"SINGLE_CHOICE", "MULTI_CHOICE"}
MIME_TYPES = {
    ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
    ".gif": "image/gif", ".webp": "image/webp", ".bmp": "image/bmp",
}


def paragraph_text(node):
    parts = []
    for child in node.iter():
        if child.tag == f"{{{W}}}t" and child.text:
            parts.append(child.text)
        elif child.tag == f"{{{W}}}tab":
            parts.append("\t")
        elif child.tag in (f"{{{W}}}br", f"{{{W}}}cr"):
            parts.append("\n")
    return "".join(parts).strip()


def parse_docx(path):
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        document = ET.fromstring(archive.read("word/document.xml"))
        relationships = {}
        if "word/_rels/document.xml.rels" in names:
            rel_root = ET.fromstring(archive.read("word/_rels/document.xml.rels"))
            for rel in rel_root.findall(f"{{{PKG_REL}}}Relationship"):
                relationships[rel.get("Id")] = rel.get("Target")

        paragraphs = []
        for node in document.iter(f"{{{W}}}p"):
            text = paragraph_text(node)
            images = []
            for blip in node.iter(f"{{{A}}}blip"):
                rel_id = blip.get(f"{{{R}}}embed")
                target = relationships.get(rel_id)
                if not target:
                    continue
                normalized = target.lstrip("/")
                if normalized.startswith("../"):
                    normalized = normalized[3:]
                member = normalized if normalized.startswith("word/") else f"word/{normalized}"
                if member not in names:
                    continue
                data = archive.read(member)
                suffix = Path(member).suffix.lower()
                images.append((Path(member).name, MIME_TYPES.get(suffix, "application/octet-stream"), data))
            if text or images:
                paragraphs.append((text, images))
        return paragraphs


def split_questions(paragraphs):
    blocks = []
    current = None
    for text, images in paragraphs:
        match = QUESTION_START.match(text)
        if match:
            if current is not None:
                blocks.append(current)
            current = {"number": int(match.group(1)), "lines": [text[match.end():]], "images": list(images)}
        elif current is not None:
            current["lines"].append(text)
            current["images"].extend(images)
    if current is not None:
        blocks.append(current)
    return blocks


def parse_difficulty(value):
    value = (value or "").strip()
    if value.isdigit() and 1 <= int(value) <= 5:
        return int(value)
    for labels, score in (
        (("容易", "简单"), 1), (("较易",), 2), (("中等", "普通"), 3),
        (("较难",), 4), (("困难", "很难"), 5),
    ):
        if any(label in value for label in labels):
            return score
    return None


def detect_type(stem, options, answer):
    if options:
        return "MULTI_CHOICE" if len(re.findall(r"[A-H]", answer.upper())) > 1 else "SINGLE_CHOICE"
    if re.search(r"实验|探究|器材|实验装置|实验现象", stem):
        return "EXPERIMENT"
    if re.search(r"_{3,}|（\s*）|\(\s*\)|（　+）", stem):
        return "FILL_BLANK"
    if re.search(r"计算|求出|是多少|结果为|多少[克米秒安伏欧瓦帕]|数值和单位", stem):
        return "CALCULATION"
    if re.search(r"简答|说明理由|解释|原因|谈谈|论述", stem):
        return "SHORT_ANSWER"
    return "OTHER"


def parse_block(block):
    lines = []
    for line in block["lines"]:
        line = line.strip()
        if len(re.findall(r"(?:^|\s)[A-H][.、．]", line)) >= 2 and not OPTION.match(line):
            line = re.sub(r"\s+(?=[A-H][.、．])", "\n", line)
        lines.extend(line.splitlines())
    stem, options, answer, analysis = [], [], [], []
    knowledge, grade, textbook, source, explicit_type = [], "", "", "", ""
    difficulty = 3
    section = "STEM"
    for line in lines:
        if not line:
            continue
        match = OPTION.match(line)
        if match and section in ("STEM", "OPTIONS"):
            section = "OPTIONS"
            options.append([match.group(1).upper(), match.group(2).strip()])
            continue
        match = ANSWER.match(line)
        if match:
            section = "ANSWER"
            if match.group(1).strip():
                answer.append(match.group(1).strip())
            continue
        match = ANALYSIS.match(line)
        if match:
            section = "ANALYSIS"
            if match.group(1).strip():
                analysis.append(match.group(1).strip())
            continue
        match = DIFFICULTY.match(line)
        if match:
            section = "META"
            difficulty = parse_difficulty(match.group(1)) or difficulty
            continue
        match = KNOWLEDGE.match(line)
        if match:
            section = "META"
            knowledge = [part.strip() for part in re.split(r"[、，,;；]", match.group(1)) if part.strip()]
            continue
        match = GRADE.match(line)
        if match:
            section = "META"
            grade = match.group(1).strip()
            continue
        match = TEXTBOOK.match(line)
        if match:
            section = "META"
            textbook = match.group(1).strip()
            continue
        match = SOURCE.match(line)
        if match:
            section = "META"
            source = match.group(1).strip()
            continue
        match = TYPE.match(line)
        if match:
            section = "META"
            explicit_type = TYPE_MAP.get(match.group(1).strip().upper(), TYPE_MAP.get(match.group(1).strip(), ""))
            continue
        if section == "ANALYSIS":
            analysis.append(line)
        elif section == "ANSWER":
            answer.append(line)
        elif section == "OPTIONS":
            options[-1][1] += "\n" + line
        elif section == "STEM":
            stem.append(line)

    clean_stem = "\n".join(stem).strip()
    clean_options = [[key, value.strip()] for key, value in options]
    clean_answer = "\n".join(answer).strip()
    question_type = explicit_type or detect_type(clean_stem, clean_options, clean_answer)
    errors = []
    if not clean_stem:
        errors.append("缺少题干")
    if question_type in CHOICE_TYPES:
        if len(clean_options) < 2:
            errors.append("选择题至少需要 2 个选项")
        if not clean_answer:
            errors.append("选择题缺少答案")
    if any(not value for _, value in clean_options):
        errors.append("存在空选项")
    if clean_options and question_type not in CHOICE_TYPES:
        errors.append("选项与题型不一致")
    if IMAGE_REFERENCE.search(clean_stem) and not block["images"]:
        errors.append("题干引用图片，但 DOCX 中未找到对应内嵌图片")

    return {
        "number": block["number"],
        "stem": clean_stem,
        "options": clean_options,
        "questionType": question_type,
        "difficulty": difficulty,
        "standardAnswer": clean_answer,
        "analysis": "\n".join(analysis).strip(),
        "knowledgeNames": knowledge,
        "grade": grade,
        "textbookVersion": textbook,
        "sourceName": source,
        "requiresImage": bool(IMAGE_REFERENCE.search(clean_stem)),
        "images": block["images"],
        "errors": errors,
    }


def load_token(env_file):
    for line in Path(env_file).read_text(encoding="utf-8").splitlines():
        if line.startswith("TEACHTRACK_BOOTSTRAP_TOKEN="):
            return line.split("=", 1)[1].strip()
    raise SystemExit(f"TEACHTRACK_BOOTSTRAP_TOKEN not found in {env_file}")


class Api:
    def __init__(self, base_url, token):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.session.headers.update({"Authorization": f"Bearer {token}"})

    def get(self, path, **params):
        response = self.session.get(self.base_url + path, params=params, timeout=30)
        response.raise_for_status()
        return response.json()

    def post(self, path, payload):
        response = self.session.post(self.base_url + path, json=payload, timeout=60)
        response.raise_for_status()
        return response.json()

    def put_bytes(self, path, data, content_type):
        response = self.session.post(
            self.base_url + path, data=data,
            headers={"Content-Type": content_type}, timeout=60
        )
        response.raise_for_status()
        return response.json()


def subject_id(api):
    subjects = api.get("/api/v1/subjects")
    chosen = next((item for item in subjects if "科学" in item.get("name", "")), subjects[0] if subjects else None)
    if not chosen:
        raise SystemExit("No subject exists in TeachTrack")
    return chosen["id"]


def knowledge_index(api, sid):
    points = api.get("/api/v1/knowledge-points", subjectId=sid, limit=500)
    return points


def matched_knowledge(names, points):
    matched = []
    for name in names:
        exact = next((point for point in points if point.get("name") == name), None)
        chosen = exact or next((point for point in points if name and name in point.get("name", "")), None)
        if chosen and chosen["id"] not in matched:
            matched.append(chosen["id"])
    return matched


def create_payload(question, sid, points, review_status, source_name):
    return {
        "subjectId": sid,
        "grade": question["grade"],
        "textbookVersion": question["textbookVersion"],
        "questionType": question["questionType"],
        "difficulty": question["difficulty"],
        "stem": question["stem"],
        "sourceType": "TEACHER_IMPORTED",
        "sourceName": question["sourceName"] or source_name,
        "copyrightStatus": "PERSONAL_USE",
        "usageScope": "PRIVATE_TEACHING",
        "reviewStatus": review_status,
        "options": [
            {"key": key, "content": content, "sortOrder": index}
            for index, (key, content) in enumerate(question["options"])
        ],
        "answer": {
            "standardAnswer": question["standardAnswer"],
            "analysis": question["analysis"],
            "scoringPoints": "",
            "solutionSteps": "",
        },
        "knowledge": [
            {"knowledgePointId": knowledge_id, "importance": "PRIMARY" if index == 0 else "RELATED"}
            for index, knowledge_id in enumerate(matched_knowledge(question["knowledgeNames"], points))
        ],
        "abilityTags": [] if not question["requiresImage"] or question["images"] else ["含图待补"],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("docx", nargs="+", type=Path)
    parser.add_argument("--api", default="")
    parser.add_argument("--token-file", default="backend/.env")
    parser.add_argument("--source-name", default="")
    parser.add_argument("--review-status", default="APPROVED", choices=["DRAFT", "NEEDS_REVIEW", "APPROVED"])
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--json-out", default="")
    args = parser.parse_args()

    parsed = []
    for path in args.docx:
        blocks = split_questions(parse_docx(path))
        questions = [parse_block(block) for block in blocks]
        for question in questions:
            question["file"] = str(path)
        parsed.extend(questions)
        print(f"{path.name}: {len(questions)} questions")
    if not parsed:
        raise SystemExit("No numbered questions found")

    if args.dry_run:
        report = [{key: value for key, value in item.items() if key != "images"} | {
            "imageCount": len(item["images"])
        } for item in parsed]
        output = json.dumps(report, ensure_ascii=False, indent=2)
        if args.json_out:
            Path(args.json_out).write_text(output, encoding="utf-8")
            print(f"written: {args.json_out}")
        else:
            print(output)
        return

    api_url = args.api
    if not api_url:
        runtime = Path("backend/data/runtime/server-url.txt")
        api_url = runtime.read_text(encoding="ascii").strip() if runtime.exists() else "http://127.0.0.1:8080"
    api = Api(api_url, load_token(args.token_file))
    sid = subject_id(api)
    points = knowledge_index(api, sid)
    created = duplicates = failed = 0
    image_uploaded = 0
    created_ids = []
    issues = []
    for index, question in enumerate(parsed, 1):
        review_status = args.review_status if not question["errors"] else "DRAFT"
        payload = create_payload(question, sid, points, review_status, args.source_name)
        try:
            detail = api.post("/api/v1/questions", payload)
        except requests.HTTPError as error:
            response = error.response
            if response is not None and response.status_code == 409:
                duplicates += 1
                continue
            failed += 1
            issues.append({"number": question["number"], "file": question["file"], "error": str(error)})
            continue
        created += 1
        question_id = detail["question"]["id"]
        created_ids.append(question_id)
        for filename, content_type, data in question["images"]:
            try:
                api.put_bytes(f"/api/v1/questions/{question_id}/image", data, content_type)
                image_uploaded += 1
            except Exception as error:
                issues.append({
                    "number": question["number"], "file": question["file"],
                    "questionId": question_id, "image": filename, "error": str(error),
                })
        if question["errors"]:
            issues.append({
                "number": question["number"], "file": question["file"],
                "questionId": question_id, "warnings": question["errors"],
            })
        if index % 20 == 0:
            print(f"progress {index}/{len(parsed)}")

    summary = {
        "parsed": len(parsed), "created": created, "duplicates": duplicates,
        "failed": failed, "imagesUploaded": image_uploaded,
        "createdIds": created_ids, "issues": issues,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if args.json_out:
        Path(args.json_out).write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
