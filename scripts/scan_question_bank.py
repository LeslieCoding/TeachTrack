# -*- coding: utf-8 -*-
"""题库本地资料扫描器。

扫描教师本地题库目录（docx/doc/pdf），输出两级结果：
1. 文件名级统计：年级/学期/教材版本/资料类型/章节分布（全量，快）
2. 内容级抽样：题型标记、来源标记、答案/解析配套情况（抽样，慢）

用法：
    python scripts/scan_question_bank.py [根目录 ...] [--sample-per-dir N] [--out 输出.json]

设计目标与 docs/ai-practice/QUESTION_BANK_ROADMAP.md 的 P2（批量导入）对齐：
后续 DOCX 导入可以复用这里的 extract_docx_text。
"""
import argparse
import json
import random
import re
import sys
from collections import Counter
from pathlib import Path

DOCX_EXT = {".docx"}
DOC_EXT = {".doc"}
PDF_EXT = {".pdf"}

# 资料类型关键词（按优先级匹配，长词优先）
TYPE_RULES = [
    ("知识点+题型", ["知识点+题型", "知识点＋题型"]),
    ("易错", ["易错"]),
    ("压轴", ["压轴"]),
    ("培优", ["培优"]),
    ("真题", ["真题"]),
    ("模拟", ["模拟", "一模", "二模", "三模"]),
    ("期中期末", ["期中期末", "期中", "期末"]),
    ("月考", ["月考"]),
    ("中考", ["中考"]),
    ("分班考", ["分班考"]),
    ("小升初", ["小升初"]),
    ("高考", ["高考"]),
    ("竞赛", ["竞赛"]),
    ("专题", ["专题"]),
    ("单元测", ["单元测", "单元综合", "单元测试"]),
    ("周测", ["周测"]),
    ("同步", ["同步", "讲练"]),
    ("综合", ["综合"]),
    ("复习", ["复习"]),
    ("讲义", ["讲义"]),
    ("练习", ["练习", "小题"]),
]

GRADE_RULES = [
    ("六下", ["六下", "六年级下"]),
    ("小升初", ["小升初", "升初一", "新初一"]),
    ("初一/七年级", ["初一", "七年级", "七上", "七下", "7年级"]),
    ("初二/八年级", ["初二", "八年级", "八上", "八下", "8年级"]),
    ("初三/九年级", ["初三", "九年级", "九上", "九下", "中考", "9年级"]),
    ("高一", ["高一", "升高一"]),
    ("高二/高三", ["高二", "高三", "高考"]),
]

TEXTBOOK_NEW_MARKERS = ["新教材", "2024-2025学年", "2025-2026学年", "2026-2027学年"]
TEXTBOOK_OLD_MARKERS = ["2022-2023学年", "2021-2022学年", "2023-2024学年"]

# 章节号，如 1.1 / 2.3-2.4 / 3.1~3.2
CHAPTER_RE = re.compile(r"(?<![\d.])(\d\.\d(?:\s*[~-～—–]\s*\d\.\d)?)(?![\d.])")

# 内容级：题型标记（出现次数即题量近似）
QUESTION_TYPE_MARKERS = {
    "SINGLE_CHOICE": [re.compile(r"^\s*\d+[\.、．]\s*[^。]{0,60}[A-D][\.、．][^A-D]{0,60}[A-D][\.、．]"), re.compile(r"\b[A-D]\s*[\.、．]\s*\S")],
    "FILL_BLANK": [re.compile(r"_{3,}")],
    "EXPERIMENT": [re.compile(r"实验探究"), re.compile(r"探究题")],
    "CALCULATION": [re.compile(r"计算题"), re.compile(r"[=＝]\s*\d+(\.\d+)?\s*(克|g|kg|J|W|V|A|Ω|N|Pa|m|cm|L|mL)?\s*[$）)]")],
    "SHORT_ANSWER": [re.compile(r"简答题"), re.compile(r"简要回答")],
}

SOURCE_MARKERS = {
    "学科网": re.compile(r"学科网|zxxk", re.I),
    "组卷网": re.compile(r"组卷网|zujuan", re.I),
    "21世纪教育网": re.compile(r"21世纪教育|21cnjy", re.I),
    "菁优网": re.compile(r"菁优网|jyeoo", re.I),
    "原创/自编": re.compile(r"原创题|自编|命题人"),
}


def classify(name, rules):
    for label, kws in rules:
        for kw in kws:
            if kw in name:
                return label
    return "其他"


_TAG_RE = re.compile(r"<[^>]+>")
_W_T_RE = re.compile(r"</w:p>|</w:tc>|</w:tr>")


def extract_docx_text(path: Path, max_chars: int = 60000) -> str:
    """快速抽取 docx 正文文本（直接读 word/document.xml，比 python-docx 快一个量级）。

    用途是关键词/标记统计，不保证版式还原；需要精确结构时改用 python-docx。
    """
    try:
        import zipfile
        with zipfile.ZipFile(str(path)) as z:
            with z.open("word/document.xml") as f:
                xml = f.read().decode("utf-8", errors="replace")
        xml = _W_T_RE.sub("\n", xml)
        text = _TAG_RE.sub("", xml)
        return text[:max_chars]
    except Exception as e:
        return f"__ERROR__:{type(e).__name__}:{e}"


def scan_file_names(root: Path):
    files = [p for p in root.rglob("*") if p.suffix.lower() in (DOCX_EXT | DOC_EXT | PDF_EXT) and not p.name.startswith("~$")]
    stats = {
        "total": len(files),
        "by_ext": Counter(p.suffix.lower() for p in files),
        "by_grade": Counter(),
        "by_type": Counter(),
        "by_textbook": Counter(),
        "chapters": Counter(),
        "answers": Counter(),  # 原卷/答案/解析配套
        "dupe_stems": 0,
    }
    seen_names = Counter()
    for p in files:
        name = p.stem
        full = str(p)
        stats["by_grade"][classify(full, GRADE_RULES)] += 1
        stats["by_type"][classify(name, TYPE_RULES)] += 1
        if any(m in full for m in TEXTBOOK_NEW_MARKERS):
            stats["by_textbook"]["新教材"] += 1
        elif any(m in full for m in TEXTBOOK_OLD_MARKERS):
            stats["by_textbook"]["老教材"] += 1
        else:
            stats["by_textbook"]["未标注"] += 1
        for m in CHAPTER_RE.findall(name):
            stats["chapters"][m.replace(" ", "")] += 1
        if re.search(r"答案|解析|详解", name):
            stats["answers"]["带答案/解析"] += 1
        else:
            stats["answers"]["原卷/无答案标注"] += 1
        seen_names[name] += 1
    stats["dupe_stems"] = sum(c - 1 for c in seen_names.values() if c > 1)
    return files, stats


def sample_content(files, per_dir: int, seed: int = 42):
    random.seed(seed)
    by_dir = {}
    for p in files:
        by_dir.setdefault(str(p.parent), []).append(p)
    picked = []
    for _, group in by_dir.items():
        group = sorted(group)
        if len(group) <= per_dir:
            picked.extend(group)
        else:
            picked.extend(random.sample(group, per_dir))
    results = {
        "sampled_files": len(picked),
        "parse_ok": 0,
        "parse_fail": 0,
        "question_type_hits": Counter(),
        "source_hits": Counter(),
        "has_answer_section": 0,
        "has_analysis_section": 0,
        "doc_lengths": [],
        "sample_records": [],
    }
    for p in picked:
        if p.suffix.lower() != ".docx":
            continue
        text = extract_docx_text(p)
        if text.startswith("__ERROR__"):
            results["parse_fail"] += 1
            continue
        results["parse_ok"] += 1
        results["doc_lengths"].append(len(text))
        qt = [label for label, pats in QUESTION_TYPE_MARKERS.items() if any(pat.search(text) for pat in pats)]
        for label in qt:
            results["question_type_hits"][label] += 1
        src = [label for label, pat in SOURCE_MARKERS.items() if pat.search(text)]
        for label in src:
            results["source_hits"][label] += 1
        if re.search(r"参考答案|【答案】|答案[:：]", text):
            results["has_answer_section"] += 1
        if re.search(r"解析|【解析】|详解", text):
            results["has_analysis_section"] += 1
        if len(results["sample_records"]) < 60:
            results["sample_records"].append({
                "file": str(p),
                "question_types": qt,
                "sources": src,
                "has_answer": bool(re.search(r"参考答案|【答案】|答案[:：]", text)),
                "has_analysis": bool(re.search(r"解析|【解析】|详解", text)),
                "length": len(text),
            })
    return results


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("roots", nargs="+")
    ap.add_argument("--sample-per-dir", type=int, default=2)
    ap.add_argument("--out", default="")
    args = ap.parse_args()

    all_files = []
    all_stats = []
    for r in args.roots:
        root = Path(r)
        files, stats = scan_file_names(root)
        all_files.extend(files)
        all_stats.append({"root": r, **{k: dict(v) if isinstance(v, Counter) else v for k, v in stats.items()}})
    content = sample_content(all_files, args.sample_per_dir)
    report = {
        "filename_stats": all_stats,
        "content_sample": {k: (dict(v) if isinstance(v, Counter) else v) for k, v in content.items()},
    }
    out = json.dumps(report, ensure_ascii=False, indent=1)
    if args.out:
        Path(args.out).write_text(out, encoding="utf-8")
        print(f"written: {args.out}")
    else:
        print(out)


if __name__ == "__main__":
    main()
