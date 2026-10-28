import { Model } from '@nozbe/watermelondb';
import { date, field, json } from '@nozbe/watermelondb/decorators';

const passthroughSanitizer = (raw: unknown) => (Array.isArray(raw) ? raw : []);

/** Mirror bảng `listening_clip` — id WatermelonDB = clip_id của Supabase. */
export class ListeningClip extends Model {
  static table = 'listening_clip';

  @field('topic') topic!: string;
  @field('title') title!: string;
  @field('cefr_level') cefrLevel!: string;
  /** Vẫn giữ nguyên theo schema server hiện tại — xem comment ở schema.ts. */
  @field('difficulty_level') difficultyLevel!: number;
  @field('order_index') orderIndex!: number;
  @field('audio_url') audioUrl!: string;
  @field('duration_sec') durationSec!: number | null;
  /** [{"speaker":"...","en":"...","vi":"..."}] — learner xem sau khi trả lời. */
  @json('transcript', passthroughSanitizer) transcript!: Array<{ speaker: string; en: string; vi: string }>;
  @field('status') status!: 'published' | 'retired';
  @date('created_at') createdAt!: Date;
}
