-- V7: prompt chấm Writing v2 (UC-23, Writing.Grade, MNT-4) và cấu hình retry chấm bài.
-- Bản v1 'TODO v1' (V3) chuyển is_active = false; mỗi name chỉ một bản active (uq_prompt_active).
-- Nội dung prompt dùng dollar-quote ($system$, $body$, $json$) để test đánh giá PER-2 đọc lại được.

UPDATE prompt_template SET is_active = false WHERE name = 'writing_grading' AND version = 1;

INSERT INTO prompt_template (name, version, system_text, body, response_schema, temperature, is_active, note) VALUES (
'writing_grading', 2,
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
- vocab_candidates: at most 5 worth-learning words taken from spelling, word-choice or word-form mistakes, or better alternatives for repeated words. Give the learner's form, the correct word, the reason (misspelled, misused, wrong_form, better_alternative) and its Vietnamese meaning. No function words, no proper nouns.
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
          "meaning_vi": {"type": "string"}
        },
        "required": ["learner_form", "word", "reason", "meaning_vi"]
      }
    },
    "comment": {"type": "string"}
  },
  "required": ["criteria", "issues", "rewrites", "vocab_candidates", "comment"]
}$json$::jsonb,
0.20, true,
'v2: rubric 4 tiêu chí x 25, issue trích nguyên văn theo câu, giải thích tiếng Việt, tối đa 5 từ đáng học (UC-23, BR-30)'
);

-- Retry chấm bài bất đồng bộ (Writing.Grade.Timeout); không hard-code trong code (MNT-3)
INSERT INTO system_setting (setting_key, value, description) VALUES
 ('writing.grading_max_attempts',        '3', 'Writing.Grade.Timeout: số lượt chấm tối đa (mỗi lượt gọi AI tối đa 2 lần); hết lượt → failed, không trừ quota (BR-29)'),
 ('writing.grading_retry_delay_minutes', '5', 'Writing.Grade.Timeout: thời gian chờ trước khi job chấm lại bài retry_queued')
ON CONFLICT (setting_key) DO NOTHING;
