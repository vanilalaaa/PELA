"""Test cho merge_ratings, build_ab_sheet và summarize_ab. Chạy: python3 -m unittest discover -s tools/evaluation -v"""
import csv
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import build_ab_sheet  # noqa: E402
import merge_ratings  # noqa: E402
import summarize_ab  # noqa: E402

HEADER = merge_ratings.COLUMNS


def write_csv(path, header, rows):
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)


def rating_row(i, r1=("", ""), r2=("", "")):
    return ["g%02d" % i, "Travel", "A2", "Write about trip %d, with a comma." % i, r1[0], r1[1], r2[0], r2[1]]


class MergeRatingsTest(unittest.TestCase):
    def rows(self, items):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "x.csv")
            write_csv(p, HEADER, items)
            return merge_ratings.read(p)

    def test_takes_each_raters_own_columns(self):
        a = self.rows([rating_row(1, r1=("y", "n"))])
        b = self.rows([rating_row(1, r2=("N", "Y"))])
        merged, problems = merge_ratings.merge(a, b)
        self.assertEqual(problems, [])
        self.assertEqual([merged[0][c] for c in HEADER[4:]], ["y", "n", "n", "y"])

    def test_rejects_when_a_rater_filled_the_other_raters_columns(self):
        a = self.rows([rating_row(1, r1=("y", "y"), r2=("y", "y"))])
        b = self.rows([rating_row(1, r1=("n", "n"), r2=("y", "y"))])
        _, problems = merge_ratings.merge(a, b)
        self.assertEqual(len([p for p in problems if "độc lập" in p]), 2)

    def test_rejects_missing_or_invalid_answers_and_mismatched_files(self):
        a = self.rows([rating_row(1, r1=("y", "maybe")), rating_row(2, r1=("y", "y"))])
        b = self.rows([rating_row(1, r2=("y", "")), rating_row(2, r2=("y", "y"))])
        _, problems = merge_ratings.merge(a, b)
        self.assertTrue(any("right_level_r1" in p and "g01" in p for p in problems))
        self.assertTrue(any("right_level_r2" in p and "g01" in p for p in problems))
        c = self.rows([rating_row(1, r2=("y", "y"))])
        self.assertIn("số dòng khác nhau", merge_ratings.merge(a, c)[1][0])
        other = self.rows([rating_row(1, r2=("y", "y")), ["g02", "Travel", "A2", "Different", "", "", "y", "y"]])
        self.assertTrue(any("không cùng một đề" in p for p in merge_ratings.merge(a, other)[1]))

    def test_main_writes_the_merged_file_only_when_valid(self):
        with tempfile.TemporaryDirectory() as d:
            p1, p2, out = (os.path.join(d, n) for n in ("a.csv", "b.csv", "out.csv"))
            write_csv(p1, HEADER, [rating_row(1, r1=("y", "y"))])
            write_csv(p2, HEADER, [rating_row(1, r2=("n", "y"))])
            self.assertEqual(merge_ratings.main(["--rater1", p1, "--rater2", p2, "--out", out]), 0)
            self.assertTrue(os.path.exists(out))
            os.remove(out)
            write_csv(p2, HEADER, [rating_row(1, r2=("", ""))])
            self.assertEqual(merge_ratings.main(["--rater1", p1, "--rater2", p2, "--out", out]), 1)
            self.assertFalse(os.path.exists(out))

    def test_wrong_header_is_an_error(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "x.csv")
            write_csv(p, ["id", "topic"], [["g01", "Travel"]])
            with self.assertRaises(ValueError):
                merge_ratings.read(p)


class BuildAbSheetTest(unittest.TestCase):
    BANK = {("Travel", "A2"): ["Bank travel one.", "Bank travel two.", "Bank travel three."],
            ("Work", "B1"): ["Bank work one.", "Bank work two."]}
    RAG = [{"id": "g01", "topic": "Travel", "level": "A2", "prompt_text": "Rag travel one."},
           {"id": "g02", "topic": "Travel", "level": "A2", "prompt_text": "Rag travel two."},
           {"id": "g03", "topic": "Work", "level": "B1", "prompt_text": "Rag work one."}]

    def test_pairs_each_rag_prompt_with_a_distinct_bank_prompt_of_the_same_topic_and_level(self):
        items, problems = build_ab_sheet.build(self.RAG, self.BANK, 3, 1)
        self.assertEqual(problems, [])
        self.assertEqual(len(items), 6)
        for pair in (("Travel", "A2", 2), ("Work", "B1", 1)):
            for source in ("rag", "bank"):
                self.assertEqual(sum(1 for i in items if i[1] == source and (i[2], i[3]) == pair[:2]), pair[2])
        bank_texts = [i[4] for i in items if i[1] == "bank"]
        self.assertEqual(len(bank_texts), len(set(bank_texts)))
        self.assertEqual(sorted(i[0] for i in items), ["P%02d" % n for n in range(1, 7)])

    def test_is_reproducible_with_the_same_seed(self):
        self.assertEqual(build_ab_sheet.build(self.RAG, self.BANK, 3, 5)[0], build_ab_sheet.build(self.RAG, self.BANK, 3, 5)[0])

    def test_errors_when_the_bank_runs_out_or_there_are_too_few_rag_prompts(self):
        many_work = self.RAG + [dict(self.RAG[2], id="g04"), dict(self.RAG[2], id="g05")]
        self.assertIn("không còn đề", build_ab_sheet.build(many_work, self.BANK, 5, 1)[1][0])
        self.assertIn("chỉ có", build_ab_sheet.build(self.RAG, self.BANK, 10, 1)[1][0])

    def test_sheet_hides_the_source_and_the_key_keeps_it(self):
        items, _ = build_ab_sheet.build(self.RAG, self.BANK, 3, 2)
        with tempfile.TemporaryDirectory() as d:
            build_ab_sheet.write(items, d)
            with open(os.path.join(d, "ab-sheet.csv"), encoding="utf-8") as f:
                sheet = f.read()
            with open(os.path.join(d, "ab-key.csv"), encoding="utf-8") as f:
                key = list(csv.DictReader(f))
        self.assertEqual(sheet.splitlines()[0], "id,topic,level,prompt_text,appeal_1_5,fit_1_5")
        self.assertNotIn(",rag,", sheet)
        self.assertNotIn(",bank,", sheet)
        self.assertEqual({r["source"] for r in key}, {"rag", "bank"})

    def test_reads_bank_files_and_strips_the_word_range(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "Writing-Prompts-A2.txt"), "w", encoding="utf-8") as f:
                f.write("PELA Writing Prompts - Level A2\n(2 prompts)\n\n1. [Travel - email] Write an email. (~40-70 words)\n\n"
                        "2. [Daily Life - paragraph] Describe your day. (~40-70 words)\n")
            bank = build_ab_sheet.read_bank(d)
        self.assertEqual(bank, {("Travel", "A2"): ["Write an email."], ("Daily Life", "A2"): ["Describe your day."]})

    def test_works_on_the_real_bank_files(self):
        bank = build_ab_sheet.read_bank(os.path.join(os.path.dirname(__file__), "..", "..", "docs", "content", "writing"))
        self.assertEqual(sum(len(v) for v in bank.values()), 138)
        self.assertTrue(all("words)" not in t for texts in bank.values() for t in texts))


class SummarizeAbTest(unittest.TestCase):
    KEY = {"P01": {"id": "P01", "source": "rag"}, "P02": {"id": "P02", "source": "bank"},
           "P03": {"id": "P03", "source": "rag"}, "P04": {"id": "P04", "source": "bank"}}

    def sheet(self, rows):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "s.csv")
            write_csv(p, ["id", "topic", "level", "prompt_text", "appeal_1_5", "fit_1_5"],
                      [[i, "T", "A1", "x", a, f] for i, (a, f) in rows.items()])
            return summarize_ab.read_sheet(p, self.KEY)

    def test_averages_per_source_across_raters_and_reports_the_difference(self):
        s1, p1 = self.sheet({"P01": ("4", "5"), "P02": ("3", "3"), "P03": ("4", "4"), "P04": ("3", "4")})
        s2, p2 = self.sheet({"P01": ("5", "5"), "P02": ("3", "3"), "P03": ("5", "4"), "P04": ("3", "4")})
        self.assertEqual(p1 + p2, [])
        summary = summarize_ab.summarize(self.KEY, [s1, s2])
        self.assertAlmostEqual(summary["appeal_1_5"]["rag"], 4.5)
        self.assertAlmostEqual(summary["appeal_1_5"]["bank"], 3.0)
        self.assertAlmostEqual(summary["appeal_1_5"]["diff"], 1.5)
        self.assertAlmostEqual(summary["fit_1_5"]["rag"], 4.5)
        self.assertLessEqual(summary["appeal_1_5"]["low"], summary["appeal_1_5"]["diff"])
        self.assertGreaterEqual(summary["appeal_1_5"]["high"], summary["appeal_1_5"]["diff"])
        self.assertIn("| Hấp dẫn | 4.50 | 3.00 | +1.50 |", summarize_ab.report(summary, 2))

    def test_rejects_scores_out_of_range_and_unknown_ids(self):
        _, problems = self.sheet({"P01": ("6", "x"), "P02": ("3", "3"), "P03": ("4", "4"), "P04": ("3", "4")})
        self.assertEqual(len(problems), 2)
        _, problems = self.sheet({"P01": ("4", "4"), "P99": ("3", "3")})
        self.assertTrue(any("không khớp" in p for p in problems))

    def test_bootstrap_interval_is_reproducible(self):
        self.assertEqual(summarize_ab.bootstrap_ci([4, 5, 4], [3, 3, 4]), summarize_ab.bootstrap_ci([4, 5, 4], [3, 3, 4]))


if __name__ == "__main__":
    unittest.main()
