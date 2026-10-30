import { appSchema, tableSchema } from '@nozbe/watermelondb';

/**
 * Schema offline (WatermelonDB) — chỉ các bảng cần cho học offline theo SRS mục 4.5.
 *
 * Quy ước để sync đơn giản (xem AGENTS.md mục 5 — "bảng offline phải có schema WatermelonDB
 * tương ứng, tên cột khớp Supabase"):
 *   - Khoá chính UUID của Supabase (vd. vocab_id, lesson_id, topic_id, user_id, challenge_id)
 *     được dùng làm chính `id` nội bộ của WatermelonDB (string) — KHÔNG tạo thêm cột PK riêng,
 *     để id ở client và server là một, sync không cần bảng map id.
 *   - Cột nào Supabase có thì giữ nguyên tên (snake_case) và kiểu tương ứng ở đây:
 *       uuid/varchar/text  -> 'string'
 *       int/numeric        -> 'number'
 *       boolean            -> 'boolean'
 *       timestamptz        -> 'number' (epoch ms, quy ước chuẩn của WatermelonDB)
 *       date               -> 'string' ('YYYY-MM-DD', không cần giờ)
 *       jsonb              -> 'string' (JSON.stringify/parse qua getter ở Model)
 *   - `writing_draft` chỉ có ở local trong Release 1.0: lưu và mở lại bản nháp Writing, kể cả
 *     offline; không đồng bộ bản nháp giữa các máy.
 *   - `outbox` là hàng đợi ghi offline chung cho toàn app (AGENTS.md mục 5). Mỗi dòng gắn
 *     `user_id` để worker sync chỉ đẩy dữ liệu của tài khoản đang đăng nhập.
 *
 * Mọi thay đổi schema phải tăng `version` và thêm bước tương ứng trong `migrations.ts`.
 */
export const schema = appSchema({
  version: 1,
  tables: [
    tableSchema({
      name: 'vocabulary',
      columns: [
        { name: 'word', type: 'string' },
        { name: 'meaning', type: 'string' },
        { name: 'part_of_speech', type: 'string', isOptional: true },
        { name: 'ipa', type: 'string', isOptional: true },
        { name: 'example_sentence', type: 'string', isOptional: true },
        { name: 'example_meaning', type: 'string', isOptional: true },
        { name: 'audio_url', type: 'string', isOptional: true },
        { name: 'image_url', type: 'string', isOptional: true },
        { name: 'topic', type: 'string', isOptional: true },
        { name: 'cefr_level', type: 'string', isOptional: true },
        { name: 'source', type: 'string' },
        { name: 'owner_user_id', type: 'string', isOptional: true },
      ],
    }),
    tableSchema({
      name: 'topic',
      columns: [
        { name: 'cefr_level', type: 'string' },
        { name: 'name', type: 'string' },
        { name: 'order_index', type: 'number' },
        { name: 'status', type: 'string' },
        { name: 'created_at', type: 'number' },
        { name: 'updated_at', type: 'number' },
      ],
    }),
    tableSchema({
      name: 'lesson',
      columns: [
        { name: 'topic_id', type: 'string', isIndexed: true },
        { name: 'order_index', type: 'number' },
        { name: 'new_word_count', type: 'number' },
        { name: 'status', type: 'string' },
      ],
    }),
    tableSchema({
      name: 'lesson_vocab',
      columns: [
        { name: 'lesson_id', type: 'string', isIndexed: true },
        { name: 'vocab_id', type: 'string', isIndexed: true },
        { name: 'is_new_word', type: 'boolean' },
      ],
    }),
    tableSchema({
      name: 'lesson_progress',
      columns: [
        { name: 'user_id', type: 'string', isIndexed: true },
        { name: 'lesson_id', type: 'string', isIndexed: true },
        { name: 'status', type: 'string' },
        { name: 'stars_earned', type: 'number', isOptional: true },
        { name: 'correct_count', type: 'number', isOptional: true },
        { name: 'total_count', type: 'number', isOptional: true },
        { name: 'resume_state', type: 'string', isOptional: true },
        { name: 'completed_at', type: 'number', isOptional: true },
      ],
    }),
    tableSchema({
      name: 'topic_progress',
      columns: [
        { name: 'user_id', type: 'string', isIndexed: true },
        { name: 'topic_id', type: 'string', isIndexed: true },
        { name: 'status', type: 'string' },
        { name: 'stars_earned_since_lock', type: 'number' },
        { name: 'unlock_cycle', type: 'number' },
        { name: 'locked_at', type: 'number', isOptional: true },
        { name: 'unlocked_at', type: 'number', isOptional: true },
        { name: 'completed_at', type: 'number', isOptional: true },
      ],
    }),
    tableSchema({
      name: 'review_card',
      columns: [
        { name: 'user_id', type: 'string', isIndexed: true },
        { name: 'vocab_id', type: 'string', isIndexed: true },
        { name: 'easiness_factor', type: 'number' },
        { name: 'interval_days', type: 'number' },
        { name: 'repetition_count', type: 'number' },
        { name: 'error_count', type: 'number' },
        { name: 'next_review_date', type: 'string', isIndexed: true },
        { name: 'last_reviewed_at', type: 'number', isOptional: true },
        { name: 'status', type: 'string' },
        { name: 'consecutive_incorrect_count', type: 'number' },
        { name: 'needs_reinforcement', type: 'boolean' },
        { name: 'origin', type: 'string' },
        { name: 'in_pool', type: 'boolean' },
        { name: 'created_at', type: 'number' },
        { name: 'updated_at', type: 'number' },
      ],
    }),
    tableSchema({
      name: 'challenge_session',
      columns: [
        { name: 'user_id', type: 'string', isIndexed: true },
        { name: 'challenge_type', type: 'string' },
        { name: 'challenge_date', type: 'string', isIndexed: true },
        { name: 'status', type: 'string' },
        { name: 'stars_earned', type: 'number', isOptional: true },
        { name: 'generated_at', type: 'number' },
        { name: 'completed_at', type: 'number', isOptional: true },
      ],
    }),
    tableSchema({
      name: 'challenge_item',
      columns: [
        { name: 'challenge_id', type: 'string', isIndexed: true },
        { name: 'vocab_id', type: 'string', isIndexed: true },
        { name: 'question_type', type: 'string' },
        { name: 'is_correct', type: 'boolean', isOptional: true },
        { name: 'position', type: 'number' },
        { name: 'source', type: 'string' },
        { name: 'question_payload', type: 'string' },
        { name: 'user_answer', type: 'string', isOptional: true },
        { name: 'answered_at', type: 'number', isOptional: true },
      ],
    }),
    tableSchema({
      name: 'listening_clip',
      columns: [
        { name: 'topic', type: 'string' },
        { name: 'title', type: 'string' },
        { name: 'cefr_level', type: 'string', isIndexed: true },
        { name: 'order_index', type: 'number' },
        { name: 'audio_url', type: 'string' },
        { name: 'duration_sec', type: 'number', isOptional: true },
        { name: 'transcript', type: 'string' },
        { name: 'status', type: 'string' },
        { name: 'created_at', type: 'number' },
      ],
    }),
    tableSchema({
      name: 'writing_draft',
      columns: [
        // Chỉ có ở local — xem comment đầu file.
        { name: 'user_id', type: 'string', isIndexed: true },
        { name: 'prompt_id', type: 'string', isOptional: true },
        { name: 'topic', type: 'string', isOptional: true },
        { name: 'cefr_level', type: 'string', isOptional: true },
        { name: 'content', type: 'string' },
        { name: 'word_count', type: 'number' },
        { name: 'status', type: 'string' },
        { name: 'created_at', type: 'number' },
        { name: 'updated_at', type: 'number' },
      ],
    }),
    tableSchema({
      name: 'streak',
      columns: [
        { name: 'user_id', type: 'string', isIndexed: true },
        { name: 'current_streak', type: 'number' },
        { name: 'longest_streak', type: 'number' },
        { name: 'restores_used', type: 'number' },
        { name: 'restores_month', type: 'string', isOptional: true },
        { name: 'broken_streak', type: 'number', isOptional: true },
        { name: 'broken_on', type: 'string', isOptional: true },
        { name: 'last_active_date', type: 'string', isOptional: true },
      ],
    }),
    tableSchema({
      name: 'user_stats',
      columns: [
        { name: 'user_id', type: 'string', isIndexed: true },
        { name: 'lessons_completed', type: 'number' },
        { name: 'words_mastered', type: 'number' },
        { name: 'final_tests_passed', type: 'number' },
        { name: 'writings_submitted', type: 'number' },
        { name: 'listening_completed', type: 'number' },
        { name: 'daily_challenges_done', type: 'number' },
        { name: 'friends_count', type: 'number' },
        { name: 'star_total', type: 'number' },
        { name: 'updated_at', type: 'number' },
      ],
    }),
    tableSchema({
      name: 'outbox',
      columns: [
        // Mỗi thao tác ghi offline tạo 1 dòng outbox. Payload chỉ chứa dữ liệu thô (vd. đáp án
        // của learner); server tự tính lại Star, streak, SM-2 (BR-23).
        { name: 'user_id', type: 'string', isIndexed: true },
        { name: 'entity_table', type: 'string', isIndexed: true },
        { name: 'entity_id', type: 'string', isIndexed: true },
        { name: 'operation', type: 'string' }, // 'create' | 'update' | 'delete'
        { name: 'payload', type: 'string' }, // JSON dữ liệu thô gửi lên server
        { name: 'status', type: 'string', isIndexed: true }, // 'pending' | 'syncing' | 'synced' | 'failed'
        { name: 'retry_count', type: 'number' },
        { name: 'created_at', type: 'number' },
        { name: 'synced_at', type: 'number', isOptional: true },
        { name: 'error_message', type: 'string', isOptional: true },
      ],
    }),
  ],
});
