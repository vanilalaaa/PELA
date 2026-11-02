#!/usr/bin/env python3
"""Sinh db/seed/R__dev_seed_writing_prompts.sql từ docs/content/writing/Writing-Prompts-<LEVEL>.txt.

Mỗi dòng đề có dạng:  N. [Topic - type] Nội dung đề. (~min-max words)

Chạy (từ thư mục gốc repo):
    python3 tools/content/gen_writing_seed.py

Lỗi (dòng không parse được, topic/type/khoảng từ sai, đề trùng chữ, số đề không khớp dòng đầu file)
được báo theo file + số dòng; có lỗi thì không ghi file đầu ra.
Chỉ dùng thư viện chuẩn. Đầu ra không phụ thuộc thời gian nên chạy lại không sinh diff.
"""
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
INPUT_DIR = REPO / "docs" / "content" / "writing"
OUTPUT = REPO / "db" / "seed" / "R__dev_seed_writing_prompts.sql"

LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"]
TOPICS = {"Daily Life", "Travel", "Work", "Technology", "Environment", "Society", "Education"}
TYPES = {"email", "paragraph", "essay"}
MAX_WORDS_LIMIT = 300  # Writing.Submit.Limit (SRS)
MIN_WORDS_FLOOR = 20
ADMIN_ID = "ad000000-0000-4000-8000-000000000001"  # admin seed trong R__dev_seed.sql

LEVEL_LINE = re.compile(r"^PELA Writing Prompts - Level (A1|A2|B1|B2|C1|C2)\s*$")
COUNT_LINE = re.compile(r"^\((\d+) prompts\b")
PROMPT_LINE = re.compile(r"^(\d+)\.\s+\[(.+?)\s+-\s+(\w+)\]\s+(.+?)\s+\(~(\d+)-(\d+) words\)\s*$")


def question_id(level, number):
    """9b000000-0000-4000-8000-0LLNNN000000: LL = 01..06 (A1..C2), NNN = số thứ tự trong file."""
    return "9b000000-0000-4000-8000-0%02d%03d000000" % (LEVELS.index(level) + 1, number)


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def parse_file(path, level, errors, seen_texts):
    rows = []
    lines = path.read_text(encoding="utf-8").splitlines()
    name = path.name

    def err(lineno, msg):
        errors.append("%s:%d: %s" % (name, lineno, msg))

    m = LEVEL_LINE.match(lines[0]) if lines else None
    if not m:
        err(1, "dòng đầu phải là 'PELA Writing Prompts - Level <A1..C2>'")
    elif m.group(1) != level:
        err(1, "level trong file là %s nhưng tên file là %s" % (m.group(1), level))

    declared = None
    if len(lines) > 1:
        c = COUNT_LINE.match(lines[1])
        if c:
            declared = int(c.group(1))
        else:
            err(2, "dòng 2 phải có dạng '(N prompts - ...)'")

    expected_number = 1
    for lineno, raw in enumerate(lines[2:], start=3):
        if not raw.strip():
            continue
        p = PROMPT_LINE.match(raw)
        if not p:
            err(lineno, "không parse được dòng đề: %r" % raw[:80])
            continue
        number, topic, qtype, text = int(p.group(1)), p.group(2), p.group(3), p.group(4).strip()
        lo, hi = int(p.group(5)), int(p.group(6))
        if number != expected_number:
            err(lineno, "số thứ tự là %d, cần %d" % (number, expected_number))
        expected_number = number + 1
        if topic not in TOPICS:
            err(lineno, "topic '%s' không thuộc danh sách cho phép %s" % (topic, sorted(TOPICS)))
        if qtype not in TYPES:
            err(lineno, "type '%s' phải là email|paragraph|essay" % qtype)
        if not (MIN_WORDS_FLOOR <= lo < hi <= MAX_WORDS_LIMIT):
            err(lineno, "khoảng từ %d-%d phải thoả %d <= min < max <= %d"
                % (lo, hi, MIN_WORDS_FLOOR, MAX_WORDS_LIMIT))
        key = " ".join(text.lower().split())
        if key in seen_texts:
            err(lineno, "trùng chữ đề với %s" % seen_texts[key])
        else:
            seen_texts[key] = "%s:%d" % (name, lineno)
        rows.append({"number": number, "topic": topic, "type": qtype, "text": text, "min": lo, "max": hi})

    if declared is not None and declared != len(rows):
        err(2, "dòng đầu ghi %d đề nhưng file có %d đề" % (declared, len(rows)))
    return rows


def render(by_level):
    out = [
        "-- R__dev_seed_writing_prompts.sql — 138 đề Writing của bộ đề PELA cho môi trường dev.",
        "-- SINH TỰ ĐỘNG bởi tools/content/gen_writing_seed.py từ docs/content/writing/*.txt — không sửa tay.",
        "--",
        "-- Quy ước id: 9b000000-0000-4000-8000-0LLNNN000000, LL = 01..06 (A1..C2), NNN = số thứ tự trong file.",
        "-- Đề mới nạp với status = 'retired' và chưa có embedding (chk_active_prompt_has_embedding);",
        "-- job EmbedMissingPromptsJob tạo embedding rồi chuyển sang 'active'.",
        "-- Chạy lại: đề không đổi thì không bị đụng tới; đề đổi chữ (question_text) hoặc payload thì bị",
        "-- xoá embedding và về 'retired' để backfill embed lại.",
        "-- Chạy sau R__dev_seed.sql (Flyway xếp migration lặp lại theo mô tả) vì cần admin seed.",
        "",
        "INSERT INTO question_bank (question_id, question_text, question_type, question_purpose, payload,",
        "                           topic, cefr_level, status, created_by) VALUES",
    ]
    values = []
    for level in LEVELS:
        rows = by_level[level]
        values.append(" -- %s (%d đề, %d-%d từ)" % (level, len(rows), rows[0]["min"], rows[0]["max"]))
        for r in rows:
            payload = json.dumps({"min_words": r["min"], "max_words": r["max"]}, separators=(",", ":"))
            values.append(" (%s, %s, %s, 'writing_prompt', %s, %s, %s, 'retired', '%s')," % (
                sql_text(question_id(level, r["number"])), sql_text(r["text"]), sql_text(r["type"]),
                sql_text(payload), sql_text(r["topic"]), sql_text(level), ADMIN_ID))
    values[-1] = values[-1].rstrip(",")
    out.extend(values)
    out.extend([
        "ON CONFLICT (question_id) DO UPDATE",
        "SET question_text    = EXCLUDED.question_text,",
        "    payload          = EXCLUDED.payload,",
        "    embedding_vector = NULL,",
        "    embedding_model  = NULL,",
        "    status           = 'retired',",
        "    retired_at       = NULL",
        "WHERE question_bank.question_text IS DISTINCT FROM EXCLUDED.question_text",
        "   OR question_bank.payload       IS DISTINCT FROM EXCLUDED.payload;",
        "",
    ])
    return "\n".join(out)


def main():
    errors, seen_texts, by_level = [], {}, {}
    for level in LEVELS:
        path = INPUT_DIR / ("Writing-Prompts-%s.txt" % level)
        if not path.exists():
            errors.append("thiếu file %s" % path.relative_to(REPO))
            continue
        by_level[level] = parse_file(path, level, errors, seen_texts)
    if errors:
        print("LỖI (%d), không ghi file đầu ra:" % len(errors), file=sys.stderr)
        for e in errors:
            print("  " + e, file=sys.stderr)
        return 1
    OUTPUT.write_text(render(by_level), encoding="utf-8")
    total = sum(len(v) for v in by_level.values())
    print("Đã ghi %s (%d đề: %s)" % (OUTPUT.relative_to(REPO), total,
                                     ", ".join("%s %d" % (lv, len(by_level[lv])) for lv in LEVELS)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
