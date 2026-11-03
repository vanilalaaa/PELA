#!/usr/bin/env python3
"""Dựng bảng chấm A/B mù (P8, việc 12): N đề do RAG sinh trộn với N đề của kho cùng topic + level, ẩn nguồn.

Đầu vào: generated.csv do RagGenerationSampleTest sinh (cột id, topic, level, prompt_text, ...) và các file đề kho
docs/content/writing/Writing-Prompts-<LEVEL>.txt. Với mỗi đề RAG, chọn MỘT đề kho khác nhau cùng topic + level (để so sánh công bằng,
không lệch do topic hay trình độ), bỏ phần "(~min-max words)" ở cuối đề kho (nếu giữ sẽ lộ nguồn). Trộn ngẫu nhiên (theo --seed) và đặt
id P01, P02… không chứa thông tin nguồn.

Đầu ra trong --out-dir:
    ab-sheet.csv  gửi cho người chấm: id, topic, level, prompt_text, appeal_1_5, fit_1_5 (hai cột cuối để trống)
    ab-key.csv    giữ kín: id, source (rag|bank), topic, level

Dùng:
    python3 tools/evaluation/build_ab_sheet.py --rag generated.csv --bank docs/content/writing --out-dir ab --n 20 --seed 7
Chỉ dùng thư viện chuẩn.
"""
import argparse
import csv
import os
import random
import re
import sys

PROMPT_LINE = re.compile(r"^(\d+)\.\s+\[(.+?)\s+-\s+(\w+)\]\s+(.+?)\s+\(~\d+-\d+ words\)\s*$")
LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"]


def read_bank(directory):
    """@return dict (topic, level) -> danh sách đề kho (đã bỏ phần khoảng từ)."""
    bank = {}
    for level in LEVELS:
        path = os.path.join(directory, "Writing-Prompts-%s.txt" % level)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            for line in f:
                m = PROMPT_LINE.match(line.strip())
                if m:
                    bank.setdefault((m.group(2), level), []).append(m.group(4).strip())
    return bank


def read_rag(path):
    with open(path, encoding="utf-8", newline="") as f:
        return [r for r in csv.DictReader(f) if r.get("prompt_text", "").strip()]


def build(rag_rows, bank, n, seed):
    """@return (danh sách (id, source, topic, level, text) đã trộn, danh sách lỗi)."""
    rng = random.Random(seed)
    rag = list(rag_rows)
    if len(rag) < n:
        return [], ["generated.csv chỉ có %d đề, cần %d" % (len(rag), n)]
    rng.shuffle(rag)
    rag = rag[:n]
    used = set()
    problems = []
    items = []
    for row in rag:
        key = (row["topic"], row["level"])
        rag_text = row["prompt_text"].strip()
        candidates = [t for t in bank.get(key, []) if (key, t) not in used and t.lower() != rag_text.lower()]
        if not candidates:
            problems.append("kho không còn đề cho %s/%s để ghép với một đề RAG" % key)
            continue
        bank_text = rng.choice(candidates)
        used.add((key, bank_text))
        items.append(("rag", key[0], key[1], rag_text))
        items.append(("bank", key[0], key[1], bank_text))
    if problems:
        return [], problems
    rng.shuffle(items)
    return [("P%02d" % (i + 1),) + item for i, item in enumerate(items)], []


def write(items, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "ab-sheet.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["id", "topic", "level", "prompt_text", "appeal_1_5", "fit_1_5"])
        for item_id, _source, topic, level, text in items:
            w.writerow([item_id, topic, level, text, "", ""])
    with open(os.path.join(out_dir, "ab-key.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["id", "source", "topic", "level"])
        for item_id, source, topic, level, _text in items:
            w.writerow([item_id, source, topic, level])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rag", required=True, help="generated.csv")
    parser.add_argument("--bank", default="docs/content/writing")
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--n", type=int, default=20, help="số đề RAG (và số đề kho): tổng 2N đề")
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args(argv)
    items, problems = build(read_rag(args.rag), read_bank(args.bank), args.n, args.seed)
    if problems:
        print("LỖI (%d), không ghi file:" % len(problems), file=sys.stderr)
        for p in problems:
            print("  " + p, file=sys.stderr)
        return 1
    write(items, args.out_dir)
    print("Đã ghi %s/ab-sheet.csv và %s/ab-key.csv (%d đề: %d RAG + %d kho)" % (args.out_dir, args.out_dir, len(items),
                                                                              args.n, args.n))
    return 0


if __name__ == "__main__":
    sys.exit(main())
