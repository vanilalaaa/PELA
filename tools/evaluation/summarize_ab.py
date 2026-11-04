#!/usr/bin/env python3
"""Tổng hợp kết quả A/B (P8, việc 12): điểm hấp dẫn và độ phù hợp trung bình của đề RAG so với đề kho, theo ab-key.csv.

Mỗi người chấm điền appeal_1_5 và fit_1_5 (số nguyên 1 đến 5) trên bản sao của ab-sheet.csv. Script gộp các bản đã chấm: tính điểm
trung bình mỗi đề trên các người chấm, rồi trung bình theo nguồn (rag, bank), chênh lệch rag trừ bank kèm khoảng tin cậy 95% bằng
bootstrap theo đề (có seed, lặp lại được). Có ô sai hoặc thiếu thì in lỗi theo từng file và id, không tính.

Dùng:
    python3 tools/evaluation/summarize_ab.py --key ab/ab-key.csv --sheet r1.csv --sheet r2.csv ... [--out report.md]
Chỉ dùng thư viện chuẩn.
"""
import argparse
import csv
import random
import statistics
import sys

SCORES = ("appeal_1_5", "fit_1_5")


def read_key(path):
    with open(path, encoding="utf-8", newline="") as f:
        return {r["id"]: r for r in csv.DictReader(f)}


def read_sheet(path, key):
    """@return ({id: {score: int}}, lỗi)."""
    problems = []
    result = {}
    with open(path, encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    ids = {r["id"] for r in rows}
    if ids != set(key):
        problems.append("%s: tập id không khớp ab-key.csv" % path)
    for r in rows:
        values = {}
        for score in SCORES:
            raw = (r.get(score) or "").strip()
            if not raw.isdigit() or not 1 <= int(raw) <= 5:
                problems.append("%s: %s của %s phải là số nguyên 1 đến 5 (đang là %r)" % (path, score, r["id"], raw))
            else:
                values[score] = int(raw)
        result[r["id"]] = values
    return result, problems


def bootstrap_ci(rag_means, bank_means, seed=11, rounds=2000):
    rng = random.Random(seed)
    diffs = []
    for _ in range(rounds):
        a = [rng.choice(rag_means) for _ in rag_means]
        b = [rng.choice(bank_means) for _ in bank_means]
        diffs.append(statistics.mean(a) - statistics.mean(b))
    diffs.sort()
    return diffs[int(0.025 * rounds)], diffs[int(0.975 * rounds) - 1]


def summarize(key, sheets):
    """@return dict score -> {rag, bank, diff, low, high, n_rag, n_bank}."""
    out = {}
    for score in SCORES:
        per_source = {"rag": [], "bank": []}
        for item_id, row in key.items():
            ratings = [s[item_id][score] for s in sheets]
            per_source[row["source"]].append(statistics.mean(ratings))
        rag, bank = per_source["rag"], per_source["bank"]
        low, high = bootstrap_ci(rag, bank)
        out[score] = {"rag": statistics.mean(rag), "bank": statistics.mean(bank),
                      "diff": statistics.mean(rag) - statistics.mean(bank), "low": low, "high": high,
                      "n_rag": len(rag), "n_bank": len(bank)}
    return out


def report(summary, raters):
    lines = ["# Kết quả A/B: đề RAG và đề kho", "",
             "Số người chấm: %d. Thang 1 đến 5; điểm trung bình theo đề, rồi theo nguồn." % raters, "",
             "| Tiêu chí | RAG | Kho | Chênh (RAG − kho) | Khoảng tin cậy 95% |", "|---|---|---|---|---|"]
    names = {"appeal_1_5": "Hấp dẫn", "fit_1_5": "Phù hợp"}
    for score in SCORES:
        s = summary[score]
        lines.append("| %s | %.2f | %.2f | %+.2f | [%+.2f, %+.2f] |" % (names[score], s["rag"], s["bank"], s["diff"], s["low"],
                                                                         s["high"]))
    lines += ["", "Cách đọc: khoảng tin cậy chứa 0 nghĩa là chưa đủ bằng chứng RAG hơn hay kém kho; hoàn toàn dưới 0 nghĩa là đề RAG "
                  "bị đánh giá thấp hơn rõ rệt. Mẫu nhỏ (khoảng %d đề mỗi nguồn) nên kết luận chỉ mang tính tham khảo." %
                  summary[SCORES[0]]["n_rag"]]
    return "\n".join(lines) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--key", required=True)
    parser.add_argument("--sheet", action="append", required=True)
    parser.add_argument("--out")
    args = parser.parse_args(argv)
    key = read_key(args.key)
    sheets, problems = [], []
    for path in args.sheet:
        sheet, errs = read_sheet(path, key)
        sheets.append(sheet)
        problems += errs
    if problems:
        print("LỖI (%d), không tính:" % len(problems), file=sys.stderr)
        for p in problems:
            print("  " + p, file=sys.stderr)
        return 1
    text = report(summarize(key, sheets), len(sheets))
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text)
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
