#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Convert a ChatGPT shared conversation into an idempotent TeachTrack seed.

The share is a public snapshot; rerunning this script merges changes into the
previously generated seed instead of duplicating records. Generated data stays
under backend/data/imports and is not committed.
"""
import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path

import requests

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

DEFAULT_SHARE_ID = "6aef67dc-9a24-83e8-a2fe-b6603242438e"
YEAR = 2026

CANONICAL = {
    "晨然": "王晨然", "王晨然": "王晨然",
    "鑫雅": "宣鑫雅", "新雅": "宣鑫雅", "宣鑫雅": "宣鑫雅", "萱新雅": "宣鑫雅", "宣新雅": "宣鑫雅",
    "欣阳": "蒋欣阳", "新阳": "蒋欣阳", "蒋欣阳": "蒋欣阳",
    "恩齐": "叶恩齐", "叶恩齐": "叶恩齐",
    "皓午": "郑皓午", "郑皓午": "郑皓午",
    "沚鑫": "陈沚鑫", "陈沚鑫": "陈沚鑫",
    "泽雅": "邓泽雅", "邓泽雅": "邓泽雅",
    "淑涵": "梁淑涵", "舒涵": "梁淑涵", "梁淑涵": "梁淑涵", "梁舒涵": "梁淑涵",
    "嘉豪": "秦嘉豪", "秦嘉豪": "秦嘉豪",
    "睿琦": "程睿琦", "程睿琦": "程睿琦",
    "诗煜": "雷诗煜", "雷诗煜": "雷诗煜",
    "浠怡": "陈浠怡", "陈浠怡": "陈浠怡",
    "紫宸": "高紫宸", "高紫宸": "高紫宸",
}

PROFILES = {
    "王晨然": {"gender": "女", "grade": "升初二", "subject": "初中科学"},
    "宣鑫雅": {"gender": "女", "grade": "升初三", "subject": "初中科学"},
    "蒋欣阳": {"gender": "男", "grade": "升初二", "subject": "初中科学"},
    "叶恩齐": {"gender": "女", "grade": "升初一", "subject": "初中科学"},
    "郑皓午": {"gender": "男", "grade": "升初二", "subject": "初中科学"},
    "陈沚鑫": {"gender": "男", "grade": "升初三", "subject": "初中科学"},
    "邓泽雅": {"gender": "女", "grade": "升初二", "subject": "初中科学"},
    "梁淑涵": {"gender": "女", "grade": "升初三", "subject": "初中科学"},
    "秦嘉豪": {"gender": "男", "grade": "升初三", "subject": "初中科学"},
    "程睿琦": {"gender": "男", "grade": "升初三", "subject": "初中科学"},
    "雷诗煜": {"gender": "女", "grade": "高一", "subject": "高中物理"},
    "陈浠怡": {"gender": "女", "grade": "高一", "subject": "高中物理"},
    "高紫宸": {"gender": "女", "grade": "初一", "subject": "初中科学"},
}

HEADING_RES = [
    re.compile(r"🗓️\s*([^\s\n]{1,12})\s+(\d{1,2})[.．](\d{1,2})\s*([^\n]{0,12}?)(?:课后)?反馈"),
    re.compile(r"【师之谕[_\s]*([^】_\n]{1,12})[_\s]*(科学|物理)[^】\n]*课堂反馈】"),
    re.compile(r"【师之谕([^】_\n]{1,12})[_\s]*(科学|物理)[^】\n]*课堂反馈】"),
]
SECTION_PATTERNS = {
    "content": [
        re.compile(r"(?:1⃣️|1️⃣)[^\n]*(?:本节课主要内容|上课内容)[^\n]*\n(.*?)(?=\n(?:2⃣️|2️⃣)[^\n]|\Z)", re.S),
    ],
    "performance": [
        re.compile(r"(?:2⃣️|2️⃣)[^\n]*(?:课堂学习情况|学生表现)[^\n]*\n(.*?)(?=\n(?:3⃣️|3️⃣)[^\n]|\Z)", re.S),
        re.compile(r"(?:3⃣️|3️⃣)[^\n]*(?:学生表现)[^\n]*\n(.*?)(?=\n(?:4⃣️|4️⃣|5⃣️|5️⃣)[^\n]|\Z)", re.S),
    ],
    "nextPlan": [
        re.compile(r"(?:3⃣️|3️⃣)[^\n]*(?:后续巩固方向)[^\n]*\n(.*?)(?=\n(?:4⃣️|4️⃣|5⃣️|5️⃣)[^\n]|\Z)", re.S),
        re.compile(r"(?:5⃣️|5️⃣)[^\n]*(?:下节课计划|教学计划)[^\n]*\n(.*?)(?=\Z)", re.S),
    ],
    "homework": [
        re.compile(r"(?:4⃣️|4️⃣)[^\n]*(?:课后作业)[^\n]*\n(.*?)(?=\n(?:5⃣️|5️⃣)[^\n]|\Z)", re.S),
    ],
}


def fetch_share(share_id):
    url = f"https://chatgpt.com/backend-api/share/{share_id}"
    proxies = None
    if os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy"):
        proxies = {"https": os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")}
    response = requests.get(url, timeout=180, proxies=proxies)
    response.raise_for_status()
    return response.json()


def message_texts(share, role):
    for node in share.get("linear_conversation") or []:
        message = node.get("message") or {}
        if (message.get("author") or {}).get("role") != role:
            continue
        content = message.get("content") or {}
        text = "\n".join(str(part) for part in (content.get("parts") or []) if isinstance(part, str)).strip()
        if text:
            yield message.get("create_time"), text


def clean_body(body):
    body = re.sub(r"^:::writing[^\n]*\n?", "", body)
    body = re.sub(r"\n?:::\s*$", "", body)
    body = re.sub(r"</?WritingBlock[^>]*>", "", body)
    body = re.sub(r"[^]*", "", body)
    return body.strip()


def extract_section(body, key):
    for pattern in SECTION_PATTERNS[key]:
        match = pattern.search(body)
        if match:
            value = match.group(1).strip()
            if value:
                return value
    return ""


def extract_feedback(share):
    latest = {}
    errors = []
    revision = 0
    for create_time, text in message_texts(share, "assistant"):
        heads = []
        for pattern in HEADING_RES:
            for match in pattern.finditer(text):
                heads.append((match.start(), match))
        heads.sort(key=lambda item: item[0])
        seen_positions = set()
        for position, (start, match) in enumerate(heads):
            if start in seen_positions:
                continue
            seen_positions.add(start)
            end = heads[position + 1][0] if position + 1 < len(heads) else len(text)
            body = clean_body(text[start:end])
            groups = match.groups()
            if len(groups) == 4:
                raw_name, month, day, subject_part = groups
                subject = "物理" if "物理" in subject_part else "科学"
                date = f"{YEAR}-{int(month):02d}-{int(day):02d}"
            else:
                raw_name, subject = groups
                date_match = re.search(r"(\d{1,2})月(\d{1,2})日", body)
                if not date_match:
                    errors.append(f"no date: {raw_name}")
                    continue
                date = f"{YEAR}-{int(date_match.group(1)):02d}-{int(date_match.group(2)):02d}"
            raw_name = raw_name.strip().replace(" ", "")
            canonical = CANONICAL.get(raw_name)
            if not canonical:
                errors.append(f"unknown student: {raw_name}")
                continue
            revision += 1
            course_subject = PROFILES.get(canonical, {}).get("subject", "初中科学")
            if subject == "物理" and course_subject.startswith("高中"):
                course_subject = "高中物理"
            key = f"chatgpt|{canonical}|{date}"
            latest[key] = {
                "key": key,
                "student": canonical,
                "date": date,
                "subject": course_subject,
                "scienceSubject": subject,
                "body": body,
                "revision": revision,
                "sourceTime": create_time,
                "content": extract_section(body, "content"),
                "performance": extract_section(body, "performance"),
                "nextPlan": extract_section(body, "nextPlan"),
                "homework": extract_section(body, "homework"),
            }
    return list(latest.values()), errors


def find_student(students, canonical):
    aliases = {canonical}
    for item in students:
        names = {item.get("fullName", ""), item.get("displayName", ""), *(item.get("aliases") or [])}
        if canonical in names:
            return item
        if aliases & {CANONICAL.get(name, name) for name in names if name}:
            return item
    return None


def ensure_student(students, canonical):
    item = find_student(students, canonical)
    if item:
        return item
    profile = PROFILES.get(canonical, {"subject": "初中科学"})
    digest = hashlib.sha256(("chatgpt|" + canonical).encode("utf-8")).hexdigest()[:12]
    item = {
        "id": f"chatgpt-student-{digest}",
        "fullName": canonical,
        "displayName": canonical[-2:] if len(canonical) >= 3 else canonical,
        "aliases": [canonical],
        "gender": profile.get("gender"),
        "historicalGrade": profile.get("grade", ""),
        "subject": profile.get("subject", "初中科学"),
        "status": "ACTIVE",
        "nameCompleteness": "CONFIRMED",
    }
    students.append(item)
    return item


def course_from_record(record, previous=None):
    title_date = record["date"][5:]
    course = {
        "sourceKey": record["key"],
        "sourceRevision": record["revision"],
        "recordDate": record["date"],
        "dateAccuracy": "EXACT",
        "subject": record["subject"],
        "title": f"课后反馈 · {title_date}",
        "content": record["body"],
        "performance": record["performance"],
        "problems": "",
        "nextLessonPlan": record["nextPlan"],
        "homeworkSummary": record["homework"],
        "date": record["date"],
    }
    if previous:
        course["index"] = previous.get("index")
    return course


def extract_exam(share):
    candidates = []
    for create_time, text in message_texts(share, "assistant"):
        if "梁淑涵" in text and "期中" in text and ("试卷分析" in text or "期中考试" in text):
            if "160" in text and "102" in text:
                candidates.append(text)
    if not candidates:
        return None
    body = clean_body(candidates[-1])
    score_match = re.search(r"实际得分[：:]\s*(\d+(?:\.\d+)?)", body)
    full_match = re.search(r"试卷满分[：:]\s*(\d+(?:\.\d+)?)", body)
    lost_match = re.search(r"实际失分[：:]\s*(\d+(?:\.\d+)?)", body)
    rate_match = re.search(r"得分率[：:]\s*(\d+(?:\.\d+)?)%", body)
    meeting = re.search(r"(?:4⃣️|4️⃣)[^\n]*家长会[^\n]*\n(.*?)(?=\n(?:5⃣️|5️⃣)[^\n]|\Z)", body, re.S)
    follow = re.search(r"(?:5⃣️|5️⃣)[^\n]*(?:后续|计划)[^\n]*\n(.*?)(?=\Z)", body, re.S)
    return {
        "student": "梁淑涵",
        "date": f"{YEAR}-11-07",
        "score": float(score_match.group(1)) if score_match else 102.0,
        "fullScore": float(full_match.group(1)) if full_match else 160.0,
        "lost": float(lost_match.group(1)) if lost_match else 58.0,
        "rate": float(rate_match.group(1)) if rate_match else 63.75,
        "body": body,
        "meeting": meeting.group(1).strip() if meeting else "",
        "followUp": follow.group(1).strip() if follow else "",
    }


def build_seed(base_seed, records, exam):
    students = [dict(item) for item in base_seed.get("students", [])]
    for student in students:
        for index, course in enumerate(student.get("courses", [])):
            course["index"] = index
        full_name = student.get("fullName") or student.get("displayName") or ""
        if CANONICAL.get(full_name, full_name) in PROFILES:
            student["status"] = "ACTIVE"
    by_name = {}
    for record in sorted(records, key=lambda item: (item["date"], item["key"])):
        student = ensure_student(students, record["student"])
        by_name[record["student"]] = student
        courses = student.setdefault("courses", [])
        previous = next((course for course in courses if course.get("sourceKey") == record["key"]), None)
        course = course_from_record(record, previous)
        if previous:
            previous.update(course)
        else:
            course["index"] = len(courses)
            courses.append(course)

    if exam:
        student = ensure_student(students, exam["student"])
        courses = student.setdefault("courses", [])
        key = f"chatgpt-exam|{exam['student']}|{exam['date']}"
        previous = next((course for course in courses if course.get("sourceKey") == key), None)
        course = {
            "sourceKey": key,
            "recordDate": exam["date"],
            "date": exam["date"],
            "dateAccuracy": "EXACT",
            "subject": "初中科学",
            "title": "家长会沟通与期中科学试卷分析",
            "content": exam["body"],
            "performance": f"期中科学：{exam['score']:g}/{exam['fullScore']:g}，得分率 {exam['rate']:g}%。",
            "nextLessonPlan": exam["followUp"],
        }
        if previous:
            previous.update(course)
        else:
            course["index"] = len(courses)
            courses.append(course)
        scores = student.setdefault("scores", [])
        score_key = f"chatgpt-score|{exam['student']}|{exam['date']}|期中科学"
        existing_score = next((item for item in scores if item.get("sourceKey") == score_key), None)
        score = {
            "sourceKey": score_key,
            "type": "期中科学试卷",
            "exactDate": exam["date"],
            "score": exam["score"],
            "fullScore": exam["fullScore"],
            "scoreText": f"{exam['score']:g}",
            "fullScoreText": f"{exam['fullScore']:g}",
            "scoreAccuracy": "EXACT",
            "note": f"失分 {exam['lost']:g}，得分率 {exam['rate']:g}%",
        }
        if existing_score:
            existing_score.update(score)
        else:
            score["index"] = len(scores)
            scores.append(score)
        communications = student.setdefault("communications", [])
        communication_key = f"chatgpt-parent-meeting|{exam['student']}|{exam['date']}"
        existing_communication = next((item for item in communications if item.get("sourceKey") == communication_key), None)
        communication = {
            "sourceKey": communication_key,
            "date": exam["date"],
            "dateAccuracy": "EXACT",
            "contact": "家长",
            "method": "家长会",
            "summary": exam["meeting"] or exam["body"],
            "parentFeedback": "期中科学成绩与后续学习安排沟通",
            "followUp": exam["followUp"],
        }
        if existing_communication:
            existing_communication.update(communication)
        else:
            communication["index"] = len(communications)
            communications.append(communication)

    base_seed["students"] = students
    base_seed["chatgptShare"] = {
        "shareId": DEFAULT_SHARE_ID,
        "importedAt": __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat(),
        "feedbackRecords": len(records),
        "examAnalysis": bool(exam),
    }
    return base_seed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--share-id", default=DEFAULT_SHARE_ID)
    parser.add_argument("--json-file", type=Path, default=None)
    parser.add_argument("--base-seed", type=Path, default=Path("seed/teacher_seed_v1.json"))
    parser.add_argument("--previous", type=Path, default=Path("backend/data/imports/chatgpt-share-seed.json"))
    parser.add_argument("--out", type=Path, default=Path("backend/data/imports/chatgpt-share-seed.json"))
    parser.add_argument("--summary", type=Path, default=Path("backend/data/imports/chatgpt-share-summary.json"))
    args = parser.parse_args()

    share = json.loads(args.json_file.read_text(encoding="utf-8")) if args.json_file else fetch_share(args.share_id)
    records, errors = extract_feedback(share)
    exam = extract_exam(share)
    source = args.previous if args.previous.exists() else args.base_seed
    seed = json.loads(source.read_text(encoding="utf-8"))
    seed = build_seed(seed, records, exam)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(seed, ensure_ascii=False, indent=2), encoding="utf-8")

    students = seed.get("students", [])
    summary = {
        "shareId": args.share_id,
        "title": share.get("title"),
        "messages": len(share.get("linear_conversation") or []),
        "studentsInSeed": len(students),
        "feedbackRecords": len(records),
        "studentsWithFeedback": len({item["student"] for item in records}),
        "courses": sum(len(student.get("courses", [])) for student in students),
        "chatgptCourses": sum(
            1 for student in students for course in student.get("courses", [])
            if str(course.get("sourceKey", "")).startswith("chatgpt")
        ),
        "examAnalysis": bool(exam),
        "errors": errors,
        "output": str(args.out),
    }
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
