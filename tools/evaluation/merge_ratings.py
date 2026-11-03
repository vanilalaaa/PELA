#!/usr/bin/env python3
"""Ghép bài chấm độc lập của hai người cho 50 đề do AI sinh (P8, việc 9) thành một file cho RagRatingReportTest.

Mỗi người chấm trên MỘT BẢN SAO RIÊNG của generated.csv: người 1 chỉ điền hai cột *_r1, người 2 chỉ điền hai cột *_r2. Script lấy
cột _r1 từ file của người 1 và cột _r2 từ file của người 2, kiểm tra hai file khớp nhau (id, nội dung đề), cột của người kia còn trống
(độc lập), và mọi ô là y hoặc n. Có lỗi thì in danh sách theo id và không ghi file.

Dùng:
    python3 tools/evaluation/merge_ratings.py --rater1 rater1.csv --rater2 rater2.csv --out generated-rated.csv
Rồi chạy RagRatingReportTest với biến RAG_RATED_CSV trỏ tới file vừa ghi (xem docs/evaluation/rag/RATING-GUIDE.md).
Chỉ dùng thư viện chuẩn.
"""
import argparse
import csv
import sys

COLUMNS = ["id", "topic", "level", "prompt_text", "on_topic_r1", "right_level_r1", "on_topic_r2", "right_level_r2"]
R1 = ["on_topic_r1", "right_level_r1"]
R2 = ["on_topic_r2", "right_level_r2"]


def read(path):
    with open(path, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames != COLUMNS:
            raise ValueError("%s: tiêu đề phải là %s, đang là %s" % (path, ",".join(COLUMNS), reader.fieldnames))
        return list(reader)


def merge(rater1_rows, rater2_rows):
    """@return (rows đã ghép, danh sách lỗi); rỗng lỗi = hợp lệ."""
    problems = []
    if len(rater1_rows) != len(rater2_rows):
        return [], ["hai file có số dòng khác nhau: %d và %d" % (len(rater1_rows), len(rater2_rows))]
    merged = []
    for a, b in zip(rater1_rows, rater2_rows):
        if a["id"] != b["id"] or a["prompt_text"] != b["prompt_text"]:
            problems.append("%s: hai file không cùng một đề (id hoặc prompt_text khác nhau)" % a["id"])
            continue
        if any(a[c].strip() for c in R2):
            problems.append("%s: file của người 1 có điền cột của người 2 (%s): chấm phải độc lập" % (a["id"], ", ".join(R2)))
        if any(b[c].strip() for c in R1):
            problems.append("%s: file của người 2 có điền cột của người 1 (%s): chấm phải độc lập" % (a["id"], ", ".join(R1)))
        row = dict(a)
        for column in R1:
            row[column] = a[column].strip().lower()
        for column in R2:
            row[column] = b[column].strip().lower()
        for column in R1 + R2:
            if row[column] not in ("y", "n"):
                problems.append("%s: %s phải là y hoặc n (đang là %r)" % (a["id"], column, row[column]))
        merged.append(row)
    return merged, problems


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rater1", required=True)
    parser.add_argument("--rater2", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    try:
        merged, problems = merge(read(args.rater1), read(args.rater2))
    except ValueError as e:
        print("LỖI:", e, file=sys.stderr)
        return 1
    if problems:
        print("LỖI (%d), không ghi file:" % len(problems), file=sys.stderr)
        for p in problems:
            print("  " + p, file=sys.stderr)
        return 1
    with open(args.out, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(merged)
    print("Đã ghi %s (%d đề)" % (args.out, len(merged)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
