# -*- coding: utf-8 -*-
"""从百度网盘读取原卷/解析版 DOCX，在内存中解析并导入 TeachTrack。"""
import argparse
import io
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass, field
import xml.etree.ElementTree as ET

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
R = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
A = "{http://schemas.openxmlformats.org/drawingml/2006/main}"
QUESTION_RE = re.compile(r"^\s*(\d{1,3})\s*[.、．]\s*\S+")
QUESTION_PREFIX_RE = re.compile(r"^\s*\d{1,3}\s*[.、．]\s*")
OPTION_RE = re.compile(r"(?<![A-Za-z0-9])([A-H])\s*[.、．:：)]\s*", re.I)
ANSWER_RE = re.compile(r"(?:【答案】|【参考答案】|答案[:：]|参考答案[:：])")
ANALYSIS_RE = re.compile(r"(?:【解析】|【分析】|【解答】|解析[:：]|分析[:：]|解答[:：])")
SECTION_RE = re.compile(r"^【(?:基础达标|巩固提升|期末精选|能力提升|拓展延伸)】$")
IMAGE_HINT_RE = re.compile(r"如图|所示|图中|图像|曲线|实验装置")


@dataclass
class Image:
    name: str
    data: bytes = field(repr=False)
    content_type: str = "image/png"


@dataclass
class Paragraph:
    text: str
    images: list[Image] = field(default_factory=list)


@dataclass
class Question:
    number: int
    stem: str = ""
    options: list[dict] = field(default_factory=list)
    standard_answer: str = ""
    analysis: str = ""
    images: list[Image] = field(default_factory=list)
    requires_image: bool = False

    def payload(self, args, has_images: bool = False) -> dict:
        answer_letters = re.findall(r"[A-H]", self.standard_answer.upper())
        if self.options:
            question_type = "MULTI_CHOICE" if len(answer_letters) > 1 else "SINGLE_CHOICE"
        elif re.search(r"实验|探究|装置", self.stem):
            question_type = "EXPERIMENT"
        else:
            question_type = "OTHER"
        return {
            "subjectId": args.subject_id,
            "grade": args.grade,
            "textbookVersion": args.textbook_version,
            "questionType": question_type,
            "difficulty": args.difficulty,
            "stem": self.stem,
            "options": self.options,
            "answer": {"standardAnswer": self.standard_answer, "analysis": self.analysis},
            "knowledge": [],
            "sourceType": "TEACHER_IMPORTED",
            "sourceName": args.source_name,
            "reviewStatus": "NEEDS_REVIEW",
            "copyrightStatus": "PERSONAL_USE",
            "usageScope": "PRIVATE_TEACHING",
            "abilityTags": ["含图待补"] if self.requires_image and not has_images else [],
        }


class BaiduClient:
    def __init__(self, config_dir: str):
        try:
            import bypy.bypy as bypy
        except ImportError as exc:
            raise RuntimeError("缺少 bypy，请先安装后再运行") from exc
        self.client = bypy.ByPy(configdir=config_dir)
        self.token = self.client._access_token or (self.client._get_token() or self.client._access_token)
        if not self.token:
            raise RuntimeError("百度网盘授权不可用")

    def download(self, fsid: int) -> bytes:
        last_error = None
        for _ in range(3):
            try:
                query = urllib.parse.urlencode({
                    "method": "filemetas", "access_token": self.token,
                    "fsids": json.dumps([fsid]), "dlink": 1,
                })
                with urllib.request.urlopen(
                    "https://pan.baidu.com/rest/2.0/xpan/multimedia?" + query, timeout=60,
                ) as response:
                    result = json.load(response)
                if result.get("errno", 0) != 0 or not result.get("list"):
                    raise RuntimeError(f"百度网盘文件信息读取失败: {result.get('errno')}")
                dlink = result["list"][0]["dlink"]
                url = dlink + ("&" if "?" in dlink else "?") + urllib.parse.urlencode({"access_token": self.token})
                request = urllib.request.Request(url, headers={"User-Agent": "pan.baidu.com"})
                with urllib.request.urlopen(request, timeout=120) as response:
                    data = response.read()
                with zipfile.ZipFile(io.BytesIO(data)) as archive:
                    if "word/document.xml" not in archive.namelist():
                        raise RuntimeError("下载内容不是有效 DOCX")
                return data
            except Exception as exc:
                last_error = exc
        raise RuntimeError(f"百度网盘文件下载重试 3 次仍失败: {last_error}")


def read_docx(data: bytes) -> list[Paragraph]:
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        document = ET.fromstring(archive.read("word/document.xml"))
        relationships = ET.fromstring(archive.read("word/_rels/document.xml.rels"))
        targets = {item.attrib["Id"]: item.attrib["Target"] for item in relationships}
        paragraphs = []
        for node in document.iter(W + "p"):
            text = "".join(item.text or "" for item in node.iter(W + "t")).strip()
            images = []
            for blip in node.iter(A + "blip"):
                relation_id = blip.attrib.get(R + "embed") or blip.attrib.get(R + "link")
                target = targets.get(relation_id, "")
                if not target.startswith("media/"):
                    continue
                name = "word/" + target
                extension = target.rsplit(".", 1)[-1].lower()
                content_type = {"png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg", "webp": "image/webp"}.get(extension)
                if content_type:
                    images.append(Image(target.rsplit("/", 1)[-1], archive.read(name), content_type))
            if text or images:
                paragraphs.append(Paragraph(text, images))
        return paragraphs


def split_questions(paragraphs: list[Paragraph]) -> dict[int, list[Paragraph]]:
    starts = [index for index, paragraph in enumerate(paragraphs) if QUESTION_RE.match(paragraph.text)]
    result = {}
    for position, start in enumerate(starts):
        end = starts[position + 1] if position + 1 < len(starts) else len(paragraphs)
        block = [item for item in paragraphs[start:end] if not SECTION_RE.match(item.text)]
        number = int(QUESTION_RE.match(block[0].text).group(1))
        result[number] = block
    return result


def parse_question(number: int, paragraphs: list[Paragraph]) -> Question:
    question = Question(number)
    stem_lines = []
    options = []
    answers = []
    analysis = []
    section = "stem"
    for index, paragraph in enumerate(paragraphs):
        raw = QUESTION_PREFIX_RE.sub("", paragraph.text, count=1) if index == 0 else paragraph.text
        question.images.extend(paragraph.images)
        match = ANSWER_RE.search(raw)
        if match:
            after = raw[match.end():].strip()
            if after:
                answers.append(after)
            section = "answer"
            continue
        match = ANALYSIS_RE.search(raw)
        if match:
            after = raw[match.end():].strip()
            if after:
                analysis.append(after)
            section = "analysis"
            continue
        if section == "answer":
            answers.append(raw)
            continue
        if section == "analysis":
            analysis.append(raw)
            continue
        matches = [
            match for match in OPTION_RE.finditer(raw)
            if ")" not in match.group(0) or match.start() == 0 or raw[match.start() - 1].isspace()
        ]
        if matches and matches[0].start() == 0:
            for option_index, option in enumerate(matches):
                end = matches[option_index + 1].start() if option_index + 1 < len(matches) else len(raw)
                options.append({"key": option.group(1).upper(), "content": raw[option.end():end].strip(), "sortOrder": len(options)})
            section = "options"
        elif section == "options" and options:
            options[-1]["content"] = (options[-1]["content"] + "\n" + raw).strip()
        elif raw:
            stem_lines.append(raw)
    question.stem = "\n".join(stem_lines).strip()
    question.options = options
    question.standard_answer = "\n".join(filter(None, answers)).strip()
    question.analysis = "\n".join(filter(None, analysis)).strip()
    question.requires_image = bool(question.images or IMAGE_HINT_RE.search(question.stem))
    return question


def parse_pair(source: bytes, solution: bytes) -> list[Question]:
    source_blocks = split_questions(read_docx(source))
    solution_blocks = split_questions(read_docx(solution))
    if not source_blocks:
        raise RuntimeError("原卷中未识别到题号，未导入任何内容")
    questions = []
    for number, block in source_blocks.items():
        question = parse_question(number, block)
        if number in solution_blocks:
            solved = parse_question(number, solution_blocks[number])
            question.standard_answer = solved.standard_answer or question.standard_answer
            question.analysis = solved.analysis or question.analysis
        if not question.stem:
            raise RuntimeError(f"第 {number} 题题干为空，已停止导入")
        questions.append(question)
    return questions


class CloudClient:
    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip("/")
        self.token = token

    def request(self, method: str, path: str, body=None, content_type="application/json"):
        data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
        request = urllib.request.Request(
            self.base_url + path, data=data, method=method,
            headers={
                "Authorization": "Bearer " + self.token,
                "Content-Type": content_type,
                "User-Agent": "TeachTrack-Docx-Importer/1.0",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                content = response.read()
                return json.loads(content) if content else None
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")[:1000]
            raise RuntimeError(f"{method} {path} -> HTTP {exc.code}: {detail}") from exc

    def upsert(self, question: Question, args, update_existing: bool, check_images: bool) -> tuple[str, str, int]:
        encoded = urllib.parse.urlencode({"stem": question.stem})
        duplicate = self.request("GET", "/api/v1/questions/duplicates?" + encoded)
        exact = duplicate.get("exact")
        if exact and not update_existing:
            return exact["id"], "skipped", 0
        existing_images = []
        if exact:
            current = self.request("GET", "/api/v1/questions/" + exact["id"])
            if check_images:
                existing_images = self.request("GET", "/api/v1/questions/" + exact["id"] + "/images")
            payload = question.payload(args, bool(existing_images))
            payload["baseVersion"] = current["question"]["version"]
            result = self.request("PATCH", "/api/v1/questions/" + exact["id"], payload)
            return result["question"]["id"], "updated", len(existing_images)
        result = self.request("POST", "/api/v1/questions", question.payload(args))
        return result["question"]["id"], "created", 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-fsid", type=int, required=True)
    parser.add_argument("--solution-fsid", type=int, required=True)
    parser.add_argument("--source-name", required=True)
    parser.add_argument("--subject-id", required=True)
    parser.add_argument("--grade", required=True)
    parser.add_argument("--textbook-version", required=True)
    parser.add_argument("--difficulty", type=int, default=3, choices=range(1, 6))
    parser.add_argument("--bypy-config", default=os.environ.get("BYPY_CONFIG_DIR", os.path.expanduser("~/.bypy")))
    parser.add_argument("--base-url", default="https://teachtrack.leslieholmes.ccwu.cc")
    parser.add_argument("--import", dest="do_import", action="store_true")
    parser.add_argument("--update-existing", action="store_true")
    parser.add_argument("--upload-images", action="store_true")
    args = parser.parse_args()
    if args.upload_images and not args.do_import:
        parser.error("--upload-images 必须与 --import 一起使用")

    client = BaiduClient(args.bypy_config)
    questions = parse_pair(client.download(args.source_fsid), client.download(args.solution_fsid))
    print(json.dumps({
        "questions": len(questions),
        "withAnswer": sum(bool(item.standard_answer) for item in questions),
        "withAnalysis": sum(bool(item.analysis) for item in questions),
        "withImages": sum(bool(item.images) for item in questions),
        "images": sum(len(item.images) for item in questions),
        "requiresImage": sum(item.requires_image for item in questions),
    }, ensure_ascii=False))
    for item in questions:
        print(f"#{item.number} 选项={len(item.options)} 答案={len(item.standard_answer)}字 解析={len(item.analysis)}字 图片={len(item.images)} {item.stem[:48]}")
    if not args.do_import:
        print("预览完成；未写入云端。加 --import 才会导入。")
        return

    token = os.environ.get("TEACHTRACK_API_TOKEN", "").strip()
    if not token:
        parser.error("正式导入需要通过环境变量 TEACHTRACK_API_TOKEN 提供 API Token")
    cloud = CloudClient(args.base_url, token)
    counts = {"created": 0, "updated": 0, "skipped": 0}
    uploaded = 0
    images_skipped = 0
    for item in questions:
        question_id, action, existing_count = cloud.upsert(item, args, args.update_existing, args.upload_images)
        counts[action] += 1
        if args.upload_images and action != "skipped":
            if existing_count not in (0, len(item.images)):
                raise RuntimeError(f"第 {item.number} 题已有 {existing_count} 张图，与 DOCX 的 {len(item.images)} 张不一致；请先核对，未自动补图")
            if existing_count == len(item.images):
                images_skipped += existing_count
            else:
                for image in item.images:
                    cloud.request("POST", f"/api/v1/questions/{question_id}/image", image.data, image.content_type)
                    uploaded += 1
        print(f"#{item.number} {action} {question_id}")
    print(json.dumps({**counts, "imagesUploaded": uploaded, "imagesSkipped": images_skipped}, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
