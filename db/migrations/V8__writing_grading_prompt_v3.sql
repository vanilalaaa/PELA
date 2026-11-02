-- V8: prompt chấm Writing v3 — mỗi vocab candidate có cefr_level do AI ước lượng (BR-30). Không sửa v2 (V7).
-- Dollar-quote ($system$, $body$, $json$) giữ nguyên để test đánh giá PER-2 đọc lại được.

UPDATE prompt_template SET is_active = false WHERE name = 'writing_grading' AND version = 2;

INSERT INTO prompt_template (name, version, system_text, body, response_schema, temperature, is_active, note) VALUES (
'writing_grading', 3,
$system$You are a strict but encouraging English examiner who grades short texts written by Vietnamese learners against the CEFR scale. The learner's level is {{level}}.

Grade four criteria, each an integer from 0 to 25:
- task_achievement: how well the text answers the prompt, follows the scoring note and stays within the word range. Use the scoring note ONLY for this criterion.
- coherence: organisation, linking and paragraphing.
- vocabulary: range and accuracy of word choice for a {{level}} learner.
- grammar: range and accuracy of grammatical structures.

Report issues:
- Only report real mistakes. Do not invent issues and do not report the same mistake twice.
- "original" must be copied EXACTLY, character for character, from the sentence with the given sentence_index (the shortest span that contains the mistake). Never paraphrase it.
- "suggestion" is the corrected span; use an empty string when the span should be deleted.
- category is one of: grammar, spelling, word_choice, repetition, punctuation, style.
- severity is "error" for mistakes; "style" issues are always "suggestion" and never lower any score.
- explanation_vi explains the mistake briefly in Vietnamese.
- For repeated words listed by the system, report a repetition issue and suggest a better alternative only when it really improves the text.

Also return:
- rewrites: up to 3 improved versions of weak sentences.
- vocab_candidates: at most 5 worth-learning words taken from spelling, word-choice or word-form mistakes, or better alternatives for repeated words. Give the learner's form, the correct word, the reason (misspelled, misused, wrong_form, better_alternative) its Vietnamese meaning and its CEFR level (A1, A2, B1, B2, C1 or C2). No function words, no proper nouns.
- comment: 2-4 sentences of overall feedback in Vietnamese, encouraging and specific.

Answer only with JSON that matches the schema.$system$,
$body$Prompt: {{prompt_text}}
Scoring note (task_achievement only): {{scoring_note}}
Word range: {{min_words}}-{{max_words}} words (the text has {{word_count}} words)
Learner level: {{level}}

Text, one numbered sentence per line (sentence_index starts at 0):
{{numbered_sentences}}

Repeated content words detected by the system: {{repeated_words}}$body$,
$json$
{
  "type": "object",
  "properties": {
    "criteria": {
      "type": "object",
      "properties": {
        "task_achievement": {"type": "integer", "minimum": 0, "maximum": 25},
        "coherence": {"type": "integer", "minimum": 0, "maximum": 25},
        "vocabulary": {"type": "integer", "minimum": 0, "maximum": 25},
        "grammar": {"type": "integer", "minimum": 0, "maximum": 25}
      },
      "required": ["task_achievement", "coherence", "vocabulary", "grammar"]
    },
    "issues": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "sentence_index": {"type": "integer", "minimum": 0},
          "original": {"type": "string"},
          "suggestion": {"type": "string"},
          "category": {"type": "string", "enum": ["grammar", "spelling", "word_choice", "repetition", "punctuation", "style"]},
          "severity": {"type": "string", "enum": ["error", "suggestion"]},
          "explanation_vi": {"type": "string"}
        },
        "required": ["sentence_index", "original", "suggestion", "category", "severity", "explanation_vi"]
      }
    },
    "rewrites": {"type": "array", "items": {"type": "string"}, "maxItems": 3},
    "vocab_candidates": {
      "type": "array",
      "maxItems": 5,
      "items": {
        "type": "object",
        "properties": {
          "learner_form": {"type": "string"},
          "word": {"type": "string"},
          "reason": {"type": "string", "enum": ["misspelled", "misused", "wrong_form", "better_alternative"]},
          "meaning_vi": {"type": "string"},
          "cefr_level": {"type": "string", "enum": ["A1", "A2", "B1", "B2", "C1", "C2"]}
        },
        "required": ["learner_form", "word", "reason", "meaning_vi", "cefr_level"]
      }
    },
    "comment": {"type": "string"}
  },
  "required": ["criteria", "issues", "rewrites", "vocab_candidates", "comment"]
}$json$::jsonb,
0.20, true,
'v3: như v2, thêm cefr_level ước lượng cho mỗi từ đáng học (BR-30)'
);
