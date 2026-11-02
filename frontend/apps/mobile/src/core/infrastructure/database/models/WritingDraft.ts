import { Model } from '@nozbe/watermelondb';
import { date, field } from '@nozbe/watermelondb/decorators';

/**
 * Bảng LOCAL-ONLY (chưa có ở Supabase) — nháp bài Writing soạn offline trước khi nộp.
 * Khi feature Writing có API thật, cần quyết định bảng/entity server tương ứng rồi map lại
 * (không tự thêm bảng `writing_draft` vào Supabase ở đây).
 */
export class WritingDraft extends Model {
  static table = 'writing_draft';

  @field('user_id') userId!: string;
  @field('prompt_id') promptId!: string | null;
  @field('topic') topic!: string | null;
  @field('cefr_level') cefrLevel!: string | null;
  @field('content') content!: string;
  @field('word_count') wordCount!: number;
  @field('status') status!: 'draft' | 'pending_sync' | 'submitted';
  @date('created_at') createdAt!: Date;
  @date('updated_at') updatedAt!: Date;
}
