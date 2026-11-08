# -*- coding: utf-8 -*-
"""扫描本地题库文件，去重并批量写入 TeachTrack Java 后端。

先解析到 backend/data/import 下的 SQLite 状态库，默认不写服务器；
加 --import 才创建题目。可重复运行，已导入题目不会重复创建。

用法：
    python scripts/import_question_bank.py --parse-only
    python scripts/import_question_bank.py --import
    python scripts/import_question_bank.py --refresh --import
"""
from __future__ import annotations

import argparse
import atexit
import concurrent.futures
import hashlib
import http.client
import io
import json
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
import urllib.parse
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath

try:
    import fitz
except ImportError:
    fitz = None

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
M = "{http://schemas.openxmlformats.org/officeDocument/2006/math}"
A = "{http://schemas.openxmlformats.org/drawingml/2006/main}"
V = "{urn:schemas-microsoft-com:vml}"
PARSER_VERSION = "2026-11-08.1"
SUPPORTED = {".docx", ".doc", ".pdf", ".zip"}
TEXT_TAGS = {W + "t", M + "t"}
QUESTION_RE = re.compile(r"^\s*(\d{1,3})\s*[.．、]\s*(?!\d)(?=\S)")
SOURCE_STAMP_RE = re.compile(r"^\s*[（(][^）)]{0,100}(?:中考|期末|期中|模拟|真题|阶段|单元测试|练习|月考|一模|二模|学业考试)[^）)]*[）)]")
QUESTION_PREFIX_RE = re.compile(r"^\s*\d{1,3}\s*[.．、]\s*")
SECTION_RE = re.compile(
    r"^\s*(?:[一二三四五六七八九十]+\s*[、.．]|第[一二三四五六七八九十]+(?:部分|大题))"
    r".{0,40}(?:选择|填空|实验|探究|计算|简答|解答|判断|综合|阅读)"
)
OPTION_RE = re.compile(r"([A-Ha-h])\s*[.．、:：)）]\s*")
OPTION_START_RE = re.compile(r"^\s*([A-Ha-h])\s*[.．、:：)）]\s*")
MARKER_RE = re.compile(r"【(答案|参考答案|解析|分析|解答|详解)】|^(参考答案|答案|解析|解答)\s*[:：]")
ANSWER_SELECT_RE = re.compile(r"故\s*选\s*[:：]?\s*([A-H](?:\s*[、,，]?\s*[A-H])*)")
ANSWER_VALUE_RE = re.compile(r"故\s*答案(?:为)?\s*[:：]\s*(.+)")
ANSWER_LETTERS_RE = re.compile(r"^[A-H](?:[、,，\s]*[A-H])*")
IMAGE_HINT_RE = re.compile(r"如图|所示|图中|图像|图象|图甲|图乙|图丙|图丁|下表|表格|曲线|装置")
ANSWER_DOC_RE = re.compile(r"(?:参考)?答案(?:与?解析|详解)?(?:版)?(?:[（(].*?[)）]|[)）])?$")
CHAPTER_RE = re.compile(r"(?<![\d.])(\d\.\d(?:\s*[~-～—–]\s*\d\.\d)?)(?![\d.])")
PUNCT_RE = re.compile(r"[\u0021-\u002f\u003a-\u0040\u005b-\u0060\u007b-\u007e\s，。；：！？、【】（）《》“”‘’]")
LEADING_NUMBER_RE = re.compile(r"^\s*[（(]?\s*(?:\d+|[一二三四五六七八九十]+)\s*[.、)）．]\s*")

SUBJECTS = {
    "初中科学": "初中科学",
    "高中物理": "高中物理",
    "高中生物": "高中生物",
}


@dataclass
class Paragraph:
    text: str
    images: int = 0
    numbering: int = 0
    question_number: int = 0


@dataclass
class Question:
    stem: str
    options: list[dict]
    number: int = 0
    standard_answer: str = ""
    analysis: str = ""
    images: int = 0
    requires_image: bool = False
    issues: list[str] = field(default_factory=list)


def sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


PAGE_NOISE_RE = re.compile(r"\n?\s*第\s*\d+\s*页\s*[（(]\s*共\s*\d+\s*页\s*[)）]\s*")
WATERMARK_RE = re.compile(r"\{#\{.*?\}#\}")


def clean_visible_text(value: str) -> str:
    return WATERMARK_RE.sub("", PAGE_NOISE_RE.sub("\n", value or "")).strip()


def normalize_stem(stem: str) -> str:
    """与后端 QuestionService.normalizeStem 同一规则：只去掉开头题号，数字本身保留。"""
    value = PUNCT_RE.sub("", LEADING_NUMBER_RE.sub("", stem.lower()))
    return value.strip()


def stem_key(stem: str) -> str:
    return sha_bytes(normalize_stem(stem).encode("utf-8"))


def compact_letters(value: str) -> str:
    return "".join(re.findall(r"[A-H]", value.upper()))


def rel_path(path: Path, root: Path) -> str:
    return str(path.relative_to(root)).replace("\\", "/")


def infer_subject(relative: str, source_name: str) -> str:
    text = relative + " " + source_name
    high_marker = any(word in text for word in ("高中", "高考", "高一", "高二", "高三"))
    if ("生物" in text or "遗传" in text) and (high_marker or "高中生物" in text):
        return "高中生物"
    if "物理" in text and high_marker:
        return "高中物理"
    if re.search(r"(数学|语文|英语|政治|历史|地理|信息技术)", text) and not re.search(
        r"(科学|化学|生物|物理)", text
    ):
        return "其他"
    return "初中科学"


def infer_grade(relative: str) -> str:
    if "小升初" in relative or "新初一" in relative or "分班考" in relative:
        return "小升初"
    if "六下" in relative or "六年级下" in relative:
        return "六年级下"
    if "高三" in relative:
        return "高三"
    if "高二" in relative:
        return "高二"
    if "高一" in relative:
        return "高一"
    if "九年级" in relative or "初三" in relative or "中考" in relative or "九上" in relative or "九下" in relative:
        return "九年级"
    if "八年级" in relative or "初二" in relative or "八上" in relative or "八下" in relative:
        return "八年级"
    if "七年级" in relative or "初一" in relative or "七上" in relative or "七下" in relative:
        return "七年级"
    return ""


def infer_textbook(relative: str) -> str:
    if any(word in relative for word in ("新教材", "2024-2025学年", "2025-2026学年", "2026-2027学年", "浙教版2024")):
        return "浙教版（2024）"
    if any(word in relative for word in ("2021-2022学年", "2022-2023学年", "2023-2024学年")):
        return "浙教版（2013）"
    return ""


def infer_difficulty(relative: str) -> int:
    if any(word in relative for word in ("压轴", "竞赛", "培优")):
        return 5 if "压轴" in relative else 4
    if any(word in relative for word in ("中考", "一模", "二模", "三模", "易错", "真题")):
        return 4
    if any(word in relative for word in ("同步", "基础", "巩固", "周测")):
        return 2
    return 3


def infer_source_type(relative: str) -> str:
    if any(word in relative for word in ("周测", "上课讲义", "学生", "自编")):
        return "SELF_CREATED"
    return "TEACHER_IMPORTED"


def question_type(stem: str, options: list[dict], answer: str) -> str:
    if len(options) >= 2:
        letters = compact_letters(answer)
        return "MULTI_CHOICE" if len(set(letters)) > 1 else "SINGLE_CHOICE"
    if re.search(r"实验|探究|装置", stem):
        return "EXPERIMENT"
    if re.search(r"_{3,}|（\s*）\s*$|\(\s*\)\s*$", stem):
        return "FILL_BLANK"
    if re.search(r"计算|求.{0,20}[=＝?？]|多少", stem):
        return "CALCULATION"
    if re.search(r"简答|为什么|简述|说明理由", stem):
        return "SHORT_ANSWER"
    return "OTHER"


def split_question_blocks(paragraphs: list[Paragraph]) -> list[list[Paragraph]]:
    blocks: list[list[Paragraph]] = []
    current: list[Paragraph] = []
    last_number = 0
    heading_index = -1
    question_counter = 0
    for index, paragraph in enumerate(paragraphs):
        text = paragraph.text.strip()
        if SECTION_RE.match(text) or (paragraph.numbering > 0 and len(text) < 16 and not SOURCE_STAMP_RE.match(text)):
            if current:
                blocks.append(current)
                current = []
            last_number = 0
            heading_index = index
            continue
        match = QUESTION_RE.match(text)
        stamped = bool(SOURCE_STAMP_RE.match(text)) and len(text) >= 12
        auto_numbered = (
            paragraph.numbering > 0
            and len(text) >= 15
            and not text.endswith(("：", ":"))
        )
        accept = False
        number = 0
        if match and len(text) >= 6:
            number = int(match.group(1))
            if not current or number == last_number + 1 or (number == 1 and heading_index >= index - 4):
                accept = True
        elif stamped or auto_numbered:
            accept = True
        if accept:
            if current:
                blocks.append(current)
            question_counter += 1
            paragraph.question_number = number or question_counter
            current = [paragraph]
            last_number = number or question_counter
        elif current:
            current.append(paragraph)
    if current:
        blocks.append(current)
    return blocks


def option_tokens(text: str):
    return list(OPTION_RE.finditer(text))


def add_options(question: Question, raw: str, stem_lines: list[str]) -> bool:
    tokens = option_tokens(raw)
    if not tokens:
        return False
    if not question.options:
        a_tokens = [token for token in tokens if token.group(1).upper() == "A"]
        if not a_tokens:
            return False
        token = a_tokens[0]
        if token.start() > 0 and (len(tokens) < 4 or tokens[-1].group(1).upper() != "D"):
            return False
        prefix = raw[:token.start()].strip()
        if prefix:
            stem_lines.append(prefix)
        tokens = [item for item in tokens if item.start() >= token.start()]
    expected = chr(ord("A") + len(question.options))
    if question.options and tokens[0].group(1).upper() != expected:
        return False
    for index, token in enumerate(tokens):
        key = token.group(1).upper()
        expected_key = chr(ord("A") + len(question.options))
        if key != expected_key:
            break
        end = tokens[index + 1].start() if index + 1 < len(tokens) else len(raw)
        content = raw[token.end():end].strip()
        question.options.append({"key": key, "content": content, "sortOrder": len(question.options)})
    return True


def answer_value(raw: str) -> str:
    selected = ANSWER_SELECT_RE.search(raw)
    if selected:
        return compact_letters(selected.group(1))
    value = ANSWER_VALUE_RE.search(raw)
    if value:
        return value.group(1).strip()
    marker = MARKER_RE.search(raw)
    if marker and "答案" in (marker.group(0) or ""):
        value = raw[marker.end():].strip()
        value = re.split(r"【(?:解析|分析|解答|详解)】", value)[0].strip()
        if value:
            return value[:2000]
    return ""


def is_answer_document(source_name: str, paragraphs: list[Paragraph] | None = None) -> bool:
    stem = Path(source_name.split("!")[-1]).stem
    if re.search(r"解析版|全解全析|无答案", stem):
        return False
    if not ANSWER_DOC_RE.search(stem):
        return False
    return "参考" in stem or bool(re.search(r"答案$", stem))


def build_answer_map(paragraphs: list[Paragraph]) -> dict[int, dict]:
    texts = [item.text.strip() for item in paragraphs]
    result: dict[int, dict] = {}
    index = 0
    while index < len(texts):
        if texts[index] == "题号":
            numbers = []
            cursor = index + 1
            while cursor < len(texts) and re.fullmatch(r"\d{1,3}", texts[cursor] or ""):
                numbers.append(int(texts[cursor]))
                cursor += 1
            if cursor < len(texts) and texts[cursor] == "答案":
                cursor += 1
                values = []
                while cursor < len(texts) and texts[cursor] not in {"题号"} and not SECTION_RE.match(texts[cursor]):
                    if texts[cursor]:
                        values.append(texts[cursor])
                    cursor += 1
                    if len(values) >= len(numbers):
                        break
                for number, value in zip(numbers, values):
                    result[number] = {"answer": value.strip(), "analysis": ""}
                index = cursor
                continue
        index += 1

    current = 0
    for text in texts:
        if not text or SECTION_RE.match(text):
            continue
        match = QUESTION_RE.match(text)
        if match:
            number = int(match.group(1))
            if number == current + 1 or (not result and current == 0):
                current = number
                rest = QUESTION_PREFIX_RE.sub("", text, count=1).strip()
                analysis_marker = re.search(r"【(?:详解|解析|分析|解答)】|^(?:详解|解析|解答)\s*[:：]", rest)
                if analysis_marker:
                    value = rest[:analysis_marker.start()].strip()
                    rest = rest[analysis_marker.end():].strip()
                else:
                    value = rest
                    rest = ""
                entry = result.setdefault(number, {"answer": "", "analysis": ""})
                if value and not entry["answer"]:
                    entry["answer"] = value
                if rest:
                    entry["analysis"] = (entry["analysis"] + "\n" + rest).strip()
                continue
        if current and current in result and len(text) > 20:
            result[current]["analysis"] = (result[current]["analysis"] + "\n" + text).strip()
    return result


def parse_question(block: list[Paragraph]) -> Question | None:
    question = Question(stem="", options=[])
    first = block[0].text.strip()
    start = QUESTION_RE.match(first)
    if block[0].question_number:
        question.number = block[0].question_number
    elif start:
        question.number = int(start.group(1))
    stem_lines: list[str] = []
    analysis_lines: list[str] = []
    mode = "content"
    images = 0
    for index, paragraph in enumerate(block):
        raw = paragraph.text.strip()
        images += paragraph.images
        if index == 0:
            raw = QUESTION_PREFIX_RE.sub("", raw, count=1).strip()
        if not raw:
            continue
        if SECTION_RE.match(raw):
            continue
        if mode == "content":
            marker = MARKER_RE.search(raw)
            if marker:
                prefix = raw[:marker.start()].strip()
                if prefix and not add_options(question, prefix, stem_lines):
                    stem_lines.append(prefix)
                marker_text = marker.group(0)
                raw = raw[marker.end():].strip()
                mode = "answer" if "答案" in marker_text else "analysis"
                if mode == "analysis":
                    raw = re.sub(r"^[解:：]\s*", "", raw)
        if mode == "answer":
            analysis_marker = re.search(r"【(?:解析|分析|解答|详解)】|^(?:解析|解答)\s*[:：]", raw)
            if analysis_marker:
                before = raw[:analysis_marker.start()].strip()
                if before:
                    value = answer_value(before)
                    if value:
                        question.standard_answer = value
                raw = raw[analysis_marker.end():].strip()
                mode = "analysis"
                raw = re.sub(r"^[解:：]\s*", "", raw)
            else:
                value = answer_value(raw)
                if value:
                    question.standard_answer = value
                if len(raw) > 24 or "故选" in raw or raw.startswith(("解", "分析")):
                    analysis_lines.append(raw)
                continue
        if mode == "analysis":
            selected = ANSWER_SELECT_RE.search(raw)
            if selected and not question.standard_answer:
                question.standard_answer = compact_letters(selected.group(1))
            value = ANSWER_VALUE_RE.search(raw)
            if value and not question.standard_answer:
                question.standard_answer = value.group(1).strip()
            if raw:
                analysis_lines.append(raw)
            continue
        if not add_options(question, raw, stem_lines):
            stem_lines.append(raw)
    question.stem = "\n".join(line.strip() for line in stem_lines if line.strip()).strip()
    question.stem = re.sub(r"\n{3,}", "\n\n", question.stem)
    question.analysis = "\n".join(line for line in analysis_lines if line.strip()).strip()
    question.images = images
    question.requires_image = bool(images or IMAGE_HINT_RE.search(question.stem))
    if len(question.stem) > 20000:
        question.stem = question.stem[:20000]
        question.issues.append("题干截断")
    if len(question.stem) < 4:
        return None
    valid_options = []
    seen_keys = set()
    for option in question.options:
        key = option["key"]
        if key in seen_keys or not option["content"]:
            continue
        seen_keys.add(key)
        option["sortOrder"] = len(valid_options)
        valid_options.append(option)
    question.stem = clean_visible_text(question.stem)
    question.analysis = clean_visible_text(question.analysis)
    question.standard_answer = clean_visible_text(question.standard_answer)
    for option in valid_options:
        option["content"] = clean_visible_text(option["content"])
    question.options = valid_options
    letters = compact_letters(question.standard_answer)
    if len(question.options) >= 2:
        if not letters:
            question.issues.append("答案缺失")
        elif not set(letters).issubset({item["key"] for item in question.options}):
            question.issues.append("答案与选项不匹配")
    elif not question.standard_answer:
        question.issues.append("答案缺失")
    if question.requires_image:
        question.issues.append("含图待补")
    return question


def read_docx(source) -> list[Paragraph]:
    with zipfile.ZipFile(source) as archive:
        document = ET.fromstring(archive.read("word/document.xml"))
    result = []
    for node in document.iter(W + "p"):
        text = "".join(item.text or "" for item in node.iter() if item.tag in TEXT_TAGS).strip()
        images = sum(1 for item in node.iter() if item.tag in {A + "blip", V + "imagedata"})
        numbering = 0
        ppr = node.find(W + "pPr")
        if ppr is not None:
            num_pr = ppr.find(W + "numPr")
            num_id = num_pr.find(W + "numId") if num_pr is not None else None
            if num_id is not None:
                try:
                    numbering = int(num_id.attrib.get(W + "val", "0"))
                except ValueError:
                    numbering = 0
        if text or images:
            result.append(Paragraph(text, images, numbering))
    return result


_WORD_APP = None


def close_word():
    global _WORD_APP
    if _WORD_APP is not None:
        try:
            _WORD_APP.Quit()
        except Exception:
            pass
        _WORD_APP = None


atexit.register(close_word)


def read_doc_with_word(path: Path) -> list[Paragraph]:
    global _WORD_APP
    try:
        import pythoncom
        import win32com.client
    except ImportError as exc:
        raise RuntimeError("旧版 DOC 需要 Microsoft Word COM（pywin32 未安装）") from exc
    pythoncom.CoInitialize()
    try:
        if _WORD_APP is None:
            _WORD_APP = win32com.client.DispatchEx("Word.Application")
            _WORD_APP.Visible = False
            _WORD_APP.DisplayAlerts = 0
            _WORD_APP.AutomationSecurity = 3
        document = _WORD_APP.Documents.Open(
            FileName=str(path), ConfirmConversions=False, ReadOnly=True,
            AddToRecentFiles=False, Visible=False,
        )
        try:
            text = document.Content.Text
        finally:
            document.Close(False)
        return [Paragraph(line.strip()) for line in text.splitlines() if line.strip()]
    except Exception:
        if _WORD_APP is not None:
            try:
                _WORD_APP.Quit()
            except Exception:
                pass
            _WORD_APP = None
        raise


def read_doc(data: bytes | None, path: Path | None) -> list[Paragraph]:
    antiword = shutil.which("antiword")
    temp_name = None
    try:
        command_path = str(path) if path is not None else None
        if command_path is None:
            handle = tempfile.NamedTemporaryFile(suffix=".doc", delete=False)
            handle.write(data or b"")
            handle.close()
            temp_name = handle.name
            command_path = temp_name
        if antiword:
            process = subprocess.run(
                [antiword, "-m", "UTF-8.txt", command_path],
                capture_output=True, timeout=120,
            )
            if process.returncode == 0 and process.stdout.strip():
                text = process.stdout.decode("utf-8", errors="replace")
                return [Paragraph(line.strip()) for line in text.splitlines() if line.strip()]
        return read_doc_with_word(Path(command_path))
    finally:
        if temp_name:
            Path(temp_name).unlink(missing_ok=True)


def read_pdf(source) -> list[Paragraph]:
    if fitz is None:
        raise RuntimeError("PyMuPDF 未安装")
    result = []
    opened = fitz.open(stream=source, filetype="pdf") if isinstance(source, (bytes, bytearray)) else fitz.open(source)
    with opened as document:
        for page in document:
            for block in page.get_text("dict").get("blocks", []):
                if block.get("type") == 1:
                    result.append(Paragraph("", 1))
                    continue
                for line in block.get("lines", []):
                    text = "".join(span.get("text", "") for span in line.get("spans", [])).strip()
                    if text:
                        result.append(Paragraph(text))
    return result


def read_document(data: bytes | None, path: Path | None, suffix: str) -> list[Paragraph]:
    if data is not None and not isinstance(data, (bytes, bytearray)):
        data = data.read()
    if suffix == ".docx":
        return read_docx(io.BytesIO(data) if data is not None else path)
    if suffix == ".doc":
        return read_doc(bytes(data) if data is not None else None, path)
    if suffix == ".pdf":
        return read_pdf(bytes(data) if data is not None else path)
    raise RuntimeError(f"不支持的文档类型: {suffix}")


class Store:
    def __init__(self, path: Path, refresh: bool):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(path)
        self.conn.row_factory = sqlite3.Row
        if refresh:
            self.conn.executescript("drop table if exists source_files; drop table if exists questions; drop table if exists meta;")
        self.conn.executescript("""
            create table if not exists source_files (
                path text primary key,
                kind text not null,
                size integer not null default 0,
                sha256 text,
                status text not null default 'PENDING',
                duplicate_of text,
                questions integer not null default 0,
                error text,
                processed_at text
            );
            create table if not exists questions (
                stem_key text primary key,
                subject text not null,
                grade text not null default '',
                textbook text not null default '',
                question_type text not null,
                difficulty integer not null default 3,
                stem text not null,
                options_json text not null,
                answer text not null default '',
                analysis text not null default '',
                source_type text not null,
                source_names text not null,
                image_count integer not null default 0,
                requires_image integer not null default 0,
                tags text not null default '[]',
                review_status text not null default 'NEEDS_REVIEW',
                merged_sources integer not null default 1,
                import_status text not null default 'PENDING',
                db_id text,
                import_error text
            );
            create table if not exists meta (key text primary key, value text);
        """)
        self.conn.execute("insert or ignore into meta(key,value) values('parser_version',?)", (PARSER_VERSION,))
        self.conn.execute("insert or ignore into meta(key,value) values('raw_questions','0')")
        self.conn.commit()

    def source(self, path: str, kind: str, size: int, digest: str | None):
        self.conn.execute(
            "insert into source_files(path,kind,size,sha256) values(?,?,?,?) "
            "on conflict(path) do update set kind=excluded.kind,size=excluded.size,sha256=excluded.sha256",
            (path, kind, size, digest),
        )

    def source_status(self, path: str, status: str, questions: int = 0, error: str = "", duplicate_of: str | None = None):
        self.conn.execute(
            "update source_files set status=?,questions=?,error=?,duplicate_of=?,processed_at=? where path=?",
            (status, questions, error[:2000], duplicate_of, time.strftime("%Y-%m-%dT%H:%M:%S"), path),
        )

    def get_source_status(self, path: str) -> str | None:
        row = self.conn.execute("select status from source_files where path=?", (path,)).fetchone()
        return row["status"] if row else None

    def add_question(self, question: Question, subject: str, grade: str, textbook: str, source_type: str, source_name: str):
        key = stem_key(question.stem)
        tags = ["含图待补"] if question.requires_image else []
        row = self.conn.execute("select * from questions where stem_key=?", (key,)).fetchone()
        if row is None:
            self.conn.execute("""
                insert into questions(stem_key,subject,grade,textbook,question_type,difficulty,stem,options_json,
                    answer,analysis,source_type,source_names,image_count,requires_image,tags,review_status)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, (
                key, subject, grade, textbook, question_type(question.stem, question.options, question.standard_answer),
                infer_difficulty(source_name), question.stem,
                json.dumps(question.options, ensure_ascii=False), question.standard_answer, question.analysis,
                source_type, json.dumps([source_name], ensure_ascii=False), question.images,
                int(question.requires_image), json.dumps(tags, ensure_ascii=False), "NEEDS_REVIEW",
            ))
            self.conn.execute("update meta set value=cast(cast(value as integer)+1 as text) where key='raw_questions'")
            return False
        options = json.loads(row["options_json"] or "[]")
        names = json.loads(row["source_names"] or "[]")
        if source_name not in names:
            names.append(source_name)
        tags = set(json.loads(row["tags"] or "[]")) | set(tags)
        stem = row["stem"]
        if len(question.stem) > len(stem):
            stem = question.stem
        answer = row["answer"] if len(row["answer"]) >= len(question.standard_answer) else question.standard_answer
        analysis = row["analysis"] if len(row["analysis"]) >= len(question.analysis) else question.analysis
        if len(question.options) > len(options):
            options = question.options
        new_type = row["question_type"]
        if len(options) >= 2:
            letters = compact_letters(answer)
            new_type = "MULTI_CHOICE" if len(set(letters)) > 1 else "SINGLE_CHOICE"
        self.conn.execute("""
            update questions set subject=?,grade=coalesce(nullif(grade,''),?),textbook=coalesce(nullif(textbook,''),?),
                question_type=?,stem=?,options_json=?,answer=?,analysis=?,source_names=?,image_count=?,requires_image=?,tags=?,merged_sources=merged_sources+1
            where stem_key=?
        """, (
            subject, grade, textbook, new_type, stem, json.dumps(options, ensure_ascii=False), answer, analysis,
            json.dumps(names, ensure_ascii=False), max(row["image_count"], question.images), int(row["requires_image"] or question.requires_image),
            json.dumps(sorted(tags), ensure_ascii=False), key,
        ))
        return True

    def finalize_reviews(self):
        rows = self.conn.execute(
            "select stem_key,stem,options_json,answer,analysis,tags,source_names,requires_image from questions"
        ).fetchall()
        filtered = 0
        for row in rows:
            options = json.loads(row["options_json"] or "[]")
            for option in options:
                option["content"] = clean_visible_text(option.get("content", ""))
            answer = clean_visible_text(row["answer"] or "")
            analysis = clean_visible_text(row["analysis"] or "")
            stem = clean_visible_text(row["stem"] or "")
            if (
                len(options) < 2
                and not answer
                and not analysis
                and not row["requires_image"]
                and not re.search(
                    r"[？?]|下列|求|多少|哪|为什么|正确|错误|请|计算|判断|选择|填空|____|（\s*）|\(\s*\)",
                    stem,
                )
            ):
                self.conn.execute("delete from questions where stem_key=?", (row["stem_key"],))
                filtered += 1
                continue
            letters = compact_letters(answer)
            if len(options) >= 2:
                complete = bool(letters) and set(letters).issubset({item.get("key") for item in options})
            else:
                complete = bool(answer.strip())
            tags = set(json.loads(row["tags"] or "[]"))
            if len(stem) > 5000:
                tags.add("超长题干")
                complete = False
            if any(marker in stem for marker in ("【解答】", "【解析】", "故选")):
                tags.add("解析混入题干")
                complete = False
            if not complete:
                tags.add("待补答案")
            names = json.loads(row["source_names"] or "[]")
            subject = infer_subject(names[0], names[0]) if names else "初中科学"
            self.conn.execute(
                "update questions set stem=?,options_json=?,answer=?,analysis=?,subject=?,review_status=?,tags=? where stem_key=?",
                (stem, json.dumps(options, ensure_ascii=False), answer, analysis, subject,
                 "APPROVED" if complete else "NEEDS_REVIEW",
                 json.dumps(sorted(tags), ensure_ascii=False), row["stem_key"]),
            )
        self.conn.commit()


class ApiClient:
    def __init__(self, base_url: str, token: str):
        parsed = urllib.parse.urlparse(base_url)
        if parsed.scheme not in ("http", "https"):
            raise RuntimeError("后端地址必须是 http/https")
        self.scheme = parsed.scheme
        self.host = parsed.hostname or "127.0.0.1"
        self.port = parsed.port or (443 if parsed.scheme == "https" else 80)
        self.prefix = parsed.path.rstrip("/")
        self.token = token
        self.connection = None

    def _connect(self):
        cls = http.client.HTTPSConnection if self.scheme == "https" else http.client.HTTPConnection
        self.connection = cls(self.host, self.port, timeout=60)

    def request(self, method: str, path: str, body=None, retries: int = 2):
        payload = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers = {"Authorization": "Bearer " + self.token, "Accept": "application/json"}
        if payload is not None:
            headers["Content-Type"] = "application/json; charset=utf-8"
        last_error = None
        for attempt in range(retries + 1):
            try:
                if self.connection is None:
                    self._connect()
                self.connection.request(method, self.prefix + path, body=payload, headers=headers)
                response = self.connection.getresponse()
                data = response.read()
                status = response.status
                if status == 401:
                    raise RuntimeError("后端 401：Token 无效")
                if status >= 500 and attempt < retries:
                    raise OSError(f"HTTP {status}")
                return status, data
            except (OSError, http.client.HTTPException) as exc:
                last_error = exc
                if self.connection is not None:
                    self.connection.close()
                self.connection = None
                if attempt >= retries:
                    break
                time.sleep(1 + attempt)
        raise RuntimeError(f"请求失败: {last_error}")

    def close(self):
        if self.connection is not None:
            self.connection.close()


def load_token(env_file: Path) -> str:
    value = os.environ.get("TEACHTRACK_API_TOKEN", "").strip()
    if value:
        return value
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            if line.startswith("TEACHTRACK_BOOTSTRAP_TOKEN="):
                return line.split("=", 1)[1].strip()
    raise RuntimeError("未找到 TEACHTRACK_API_TOKEN 或 backend/.env")


def parse_into_store(store: Store, root: Path, limit_files: int, resume: bool, shard_index: int = 0, shard_count: int = 1):
    top_files = [p for p in root.rglob("*") if p.is_file() and not p.name.startswith("~$")]
    if shard_count > 1:
        top_files = [
            path for path in top_files
            if int(hashlib.sha1(str(path.parent).encode("utf-8")).hexdigest()[:8], 16) % shard_count == shard_index
        ]
    top_files.sort(key=lambda p: ({"docx": 0, "doc": 1, "pdf": 2, "zip": 3}.get(p.suffix.lower(), 9), str(p)))
    seen_content: dict[str, str] = {}
    parsed_docs = 0
    errors = 0
    duplicate_files = 0
    no_questions = 0
    raw_question_hits = 0

    def parse_document(data, path, virtual_name: str, size: int, digest: str, answers: dict[int, dict] | None = None):
        nonlocal parsed_docs, errors, no_questions, raw_question_hits
        suffix = Path(virtual_name.split("!")[-1]).suffix.lower()
        store.source(virtual_name, suffix, size, digest)
        try:
            paragraphs = read_document(data, path, suffix)
            blocks = split_question_blocks(paragraphs)
            count = 0
            for block in blocks:
                question = parse_question(block)
                if question is None:
                    continue
                if answers and question.number in answers:
                    extra = answers[question.number]
                    if not question.standard_answer:
                        question.standard_answer = extra.get("answer", "")
                    if len(extra.get("analysis", "")) > len(question.analysis):
                        question.analysis = extra["analysis"]
                    question.issues = [
                        issue for issue in question.issues
                        if not (issue == "答案缺失" and question.standard_answer)
                    ]
                subject = infer_subject(virtual_name, virtual_name)
                store.add_question(
                    question, subject, infer_grade(virtual_name), infer_textbook(virtual_name),
                    infer_source_type(virtual_name), virtual_name,
                )
                count += 1
                raw_question_hits += 1
            parsed_docs += 1
            store.source_status(virtual_name, "PARSED" if count else "NO_QUESTIONS", count)
            if not count:
                no_questions += 1
            return count
        except Exception as exc:
            errors += 1
            store.source_status(virtual_name, "ERROR", 0, f"{type(exc).__name__}: {exc}")
            return 0

    def merge_answers(target: dict[str, dict[int, dict]], key: str, values: dict[int, dict]):
        group = target.setdefault(key, {})
        for number, value in values.items():
            existing = group.get(number)
            if existing is None:
                group[number] = value
                continue
            if not existing.get("answer") and value.get("answer"):
                existing["answer"] = value["answer"]
            if len(value.get("analysis", "")) > len(existing.get("analysis", "")):
                existing["analysis"] = value["analysis"]

    def process_archive(data: bytes, virtual_name: str, size: int, digest: str, depth: int = 0):
        nonlocal errors
        store.source(virtual_name, ".zip", size, digest)
        total = 0
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                infos = [
                    info for info in archive.infolist()
                    if not info.is_dir()
                    and info.file_size <= 300 * 1024 * 1024
                    and Path(info.filename).suffix.lower() in SUPPORTED
                    and "__MACOSX/" not in info.filename
                    and not Path(info.filename).name.startswith("~$")
                ]
                answer_names: set[str] = set()
                archive_answers: dict[str, dict[int, dict]] = {}
                for info in infos:
                    suffix = Path(info.filename).suffix.lower()
                    if not is_answer_document(info.filename):
                        continue
                    child_name = virtual_name + "!" + info.filename
                    try:
                        child_data = archive.read(info)
                        paragraphs = read_document(io.BytesIO(child_data), None, suffix)
                        merge_answers(archive_answers, str(PurePosixPath(child_name).parent),
                                      build_answer_map(paragraphs))
                        answer_names.add(info.filename)
                        store.source(child_name, suffix, len(child_data), sha_bytes(child_data))
                        store.source_status(child_name, "ANSWER_KEY")
                    except Exception:
                        continue
                for info in infos:
                    if info.filename in answer_names:
                        continue
                    suffix = Path(info.filename).suffix.lower()
                    child_name = virtual_name + "!" + info.filename
                    child_data = archive.read(info)
                    child_digest = sha_bytes(child_data)
                    if child_digest in seen_content:
                        store.source(child_name, suffix, len(child_data), child_digest)
                        store.source_status(child_name, "DUPLICATE_FILE", duplicate_of=seen_content[child_digest])
                        continue
                    seen_content[child_digest] = child_name
                    if suffix == ".zip" and depth < 3:
                        total += process_archive(child_data, child_name, len(child_data), child_digest, depth + 1)
                    elif suffix != ".zip":
                        answers = archive_answers.get(str(PurePosixPath(child_name).parent))
                        total += parse_document(child_data, None, child_name, len(child_data), child_digest, answers)
            store.source_status(virtual_name, "PARSED" if total else "NO_QUESTIONS", total)
        except Exception as exc:
            errors += 1
            store.source_status(virtual_name, "ERROR", total, f"{type(exc).__name__}: {exc}")
        return total

    selected = top_files[:limit_files] if limit_files else top_files
    top_answers: dict[str, dict[int, dict]] = {}
    answer_paths: set[str] = set()
    for path in selected:
        suffix = path.suffix.lower()
        if suffix not in {".docx", ".doc", ".pdf"} or not is_answer_document(path.name):
            continue
        relative = rel_path(path, root)
        try:
            paragraphs = read_document(None, path, suffix)
            merge_answers(top_answers, str(PurePosixPath(relative).parent), build_answer_map(paragraphs))
            answer_paths.add(relative)
            store.source(relative, suffix, path.stat().st_size, sha_file(path))
            store.source_status(relative, "ANSWER_KEY")
        except Exception as exc:
            errors += 1
            store.source(relative, suffix, path.stat().st_size, None)
            store.source_status(relative, "ERROR", 0, f"{type(exc).__name__}: {exc}")

    processed = 0
    for path in top_files:
        relative = rel_path(path, root)
        suffix = path.suffix.lower()
        if resume and store.get_source_status(relative) in {"PARSED", "NO_QUESTIONS", "DUPLICATE_FILE", "UNSUPPORTED", "NO_TEXT", "ANSWER_KEY"}:
            continue
        if limit_files and processed >= limit_files:
            break
        processed += 1
        size = path.stat().st_size
        if relative in answer_paths:
            continue
        if suffix not in SUPPORTED:
            store.source(relative, suffix or "none", size, None)
            store.source_status(relative, "UNSUPPORTED")
            continue
        digest = sha_file(path)
        if digest in seen_content:
            store.source(relative, suffix, size, digest)
            store.source_status(relative, "DUPLICATE_FILE", duplicate_of=seen_content[digest])
            duplicate_files += 1
            continue
        seen_content[digest] = relative
        if suffix == ".zip":
            process_archive(path.read_bytes(), relative, size, digest)
        else:
            answers = top_answers.get(str(PurePosixPath(relative).parent))
            parse_document(None, path, relative, size, digest, answers)
        if processed % 50 == 0:
            store.conn.commit()
            print(f"scanned={processed} docs={parsed_docs} rawQuestions={raw_question_hits} errors={errors}", flush=True)
    store.conn.commit()
    store.finalize_reviews()
    return {
        "top_files": len(top_files),
        "processed_this_run": processed,
        "documents": parsed_docs,
        "raw_questions": raw_question_hits,
        "duplicate_files": duplicate_files,
        "no_questions_documents": no_questions,
        "errors": errors,
    }


def import_questions(store: Store, base_url: str, token: str, max_questions: int, workers: int = 6):
    client = ApiClient(base_url, token)
    status, body = client.request("GET", "/actuator/health")
    if status != 200:
        raise RuntimeError(f"后端健康检查失败: HTTP {status}")
    status, body = client.request("GET", "/api/v1/subjects")
    if status != 200:
        raise RuntimeError(f"读取学科失败: HTTP {status}")
    subjects = {item["name"]: item["id"] for item in json.loads(body)}
    client.close()
    rows = store.conn.execute(
        "select * from questions where import_status in ('PENDING','FAILED') order by stem_key"
    ).fetchall()
    if max_questions:
        rows = rows[:max_questions]
    counts = Counter()
    started = time.monotonic()
    local = threading.local()

    def post_question(item):
        key, payload = item
        if not hasattr(local, "client"):
            local.client = ApiClient(base_url, token)
        try:
            status, body = local.client.request("POST", "/api/v1/questions", payload)
            if status == 201:
                return key, "created", json.loads(body)["question"]["id"]
            if status == 409:
                return key, "duplicate", body.decode("utf-8", errors="replace")[:1000]
            return key, "failed", f"HTTP {status}: " + body.decode("utf-8", errors="replace")[:1000]
        except Exception as exc:
            return key, "failed", f"{type(exc).__name__}: {exc}"

    tasks = []
    for row in rows:
        subject_id = subjects.get(row["subject"]) or subjects.get("初中科学")
        if not subject_id:
            counts["failed"] += 1
            store.conn.execute(
                "update questions set import_status='FAILED',import_error=? where stem_key=?",
                ("缺少学科", row["stem_key"]),
            )
            continue
        try:
            source_name = json.loads(row["source_names"] or "[]")[0]
        except (ValueError, IndexError):
            source_name = ""
        payload = {
            "subjectId": subject_id,
            "grade": row["grade"],
            "textbookVersion": row["textbook"],
            "questionType": row["question_type"],
            "difficulty": row["difficulty"],
            "stem": row["stem"],
            "sourceType": row["source_type"],
            "sourceName": source_name,
            "copyrightStatus": "PERSONAL_USE",
            "usageScope": "PRIVATE_TEACHING",
            "reviewStatus": row["review_status"],
            "options": json.loads(row["options_json"] or "[]"),
            "answer": {
                "standardAnswer": row["answer"],
                "analysis": row["analysis"],
                "scoringPoints": "",
                "solutionSteps": "",
            },
            "knowledge": [],
            "abilityTags": json.loads(row["tags"] or "[]"),
        }
        tasks.append((row["stem_key"], payload))
    store.conn.commit()

    done = 0
    batch_size = max(workers * 8, 16)
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        for offset in range(0, len(tasks), batch_size):
            batch = tasks[offset:offset + batch_size]
            for key, action, detail in pool.map(post_question, batch):
                if action == "created":
                    store.conn.execute(
                        "update questions set import_status='IMPORTED',db_id=?,import_error=null where stem_key=?",
                        (detail, key),
                    )
                elif action == "duplicate":
                    store.conn.execute(
                        "update questions set import_status='DUPLICATE',import_error=? where stem_key=?",
                        (detail, key),
                    )
                else:
                    store.conn.execute(
                        "update questions set import_status='FAILED',import_error=? where stem_key=?",
                        (detail, key),
                    )
                counts[action] += 1
                done += 1
            store.conn.commit()
            elapsed = max(time.monotonic() - started, 0.001)
            print(
                f"imported={done}/{len(tasks)} created={counts['created']} duplicates={counts['duplicate']} "
                f"failed={counts['failed']} rate={done / elapsed:.1f}/s",
                flush=True,
            )
    store.conn.commit()
    return {"attempted": len(tasks), **counts, "workers": workers}


def report(store: Store, path: Path, scan_result: dict | None, import_result: dict | None):
    sources = store.conn.execute("select kind,status,count(*) count from source_files group by kind,status").fetchall()
    question_summary = store.conn.execute("""
        select count(*) unique_questions,
               sum(merged_sources-1) merged_duplicate_hits,
               sum(case when review_status='APPROVED' then 1 else 0 end) approved,
               sum(case when review_status='NEEDS_REVIEW' then 1 else 0 end) needs_review,
               sum(case when requires_image=1 then 1 else 0 end) requires_image,
               sum(case when answer<>'' then 1 else 0 end) with_answer,
               sum(case when analysis<>'' then 1 else 0 end) with_analysis
        from questions
    """).fetchone()
    result = {
        "parser_version": PARSER_VERSION,
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "scan": scan_result,
        "sources": [dict(row) for row in sources],
        "questions": dict(question_summary),
        "by_type": [dict(row) for row in store.conn.execute(
            "select question_type,count(*) count from questions group by question_type order by count desc"
        )],
        "by_grade": [dict(row) for row in store.conn.execute(
            "select grade,count(*) count from questions group by grade order by count desc"
        )],
        "by_import_status": [dict(row) for row in store.conn.execute(
            "select import_status,count(*) count from questions group by import_status"
        )],
        "errors": [dict(row) for row in store.conn.execute(
            "select path,error from source_files where status='ERROR' limit 30"
        )],
        "samples": [dict(row) for row in store.conn.execute(
            "select stem,question_type,grade,options_json,answer,analysis,source_names,review_status "
            "from questions order by merged_sources desc limit 12"
        )],
        "import": import_result,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return result


def main():
    project = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=r"D:\Students\My Documents")
    parser.add_argument("--state", default=str(project / "backend/data/import/question_bank.sqlite3"))
    parser.add_argument("--report", default=str(project / "backend/data/import/latest-report.json"))
    parser.add_argument("--env-file", default=str(project / "backend/.env"))
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--parse-only", action="store_true")
    parser.add_argument("--import", dest="do_import", action="store_true")
    parser.add_argument("--import-only", action="store_true")
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--limit-files", type=int, default=0)
    parser.add_argument("--max-questions", type=int, default=0)
    parser.add_argument("--workers", type=int, default=6)
    parser.add_argument("--shard-index", type=int, default=0)
    parser.add_argument("--shard-count", type=int, default=1)
    args = parser.parse_args()

    root = Path(args.root)
    if not root.is_dir():
        parser.error(f"题库目录不存在: {root}")
    if args.shard_count < 1 or not 0 <= args.shard_index < args.shard_count:
        parser.error("分片参数无效")
    store = Store(Path(args.state), args.refresh)
    try:
        if args.import_only:
            store.finalize_reviews()
            scan_result = None
        else:
            scan_result = parse_into_store(
                store, root, args.limit_files, not args.refresh, args.shard_index, args.shard_count
            )
            print(json.dumps(scan_result, ensure_ascii=False), flush=True)
        import_result = None
        if args.do_import and not args.parse_only:
            token = load_token(Path(args.env_file))
            import_result = import_questions(store, args.base_url, token, args.max_questions, args.workers)
            print(json.dumps(import_result, ensure_ascii=False), flush=True)
        summary = report(store, Path(args.report), scan_result, import_result)
        print(json.dumps({
            "questions": summary["questions"],
            "byType": summary["by_type"],
            "byGrade": summary["by_grade"],
            "report": args.report,
        }, ensure_ascii=False, indent=2), flush=True)
    finally:
        store.conn.close()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("interrupted", file=sys.stderr)
        sys.exit(130)
    except Exception as exc:
        print(f"ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(1)
