import { Model } from '@nozbe/watermelondb';
import { date, field } from '@nozbe/watermelondb/decorators';

/**
 * Mirror bảng `review_card` — id WatermelonDB = card_id của Supabase.
 * SM-2 cụ thể theo BR-26; `in_pool`/`needs_reinforcement` phải do server tính lại khi submit
 * (server-as-arbiter) — client chỉ đọc để hiển thị, không tự suy luận lại các cờ này.
 */
export class ReviewCard extends Model {
  static table = 'review_card';

  @field('user_id') userId!: string;
  @field('vocab_id') vocabId!: string;
  @field('easiness_factor') easinessFactor!: number;
  @field('interval_days') intervalDays!: number;
  @field('repetition_count') repetitionCount!: number;
  @field('error_count') errorCount!: number;
  /** 'YYYY-MM-DD', không có giờ — giữ string để so sánh ngày đơn giản offline. */
  @field('next_review_date') nextReviewDate!: string;
  @date('last_reviewed_at') lastReviewedAt!: Date | null;
  @field('status') status!: 'new' | 'learning' | 'review' | 'lapsed' | 'mastered';
  @field('consecutive_incorrect_count') consecutiveIncorrectCount!: number;
  @field('needs_reinforcement') needsReinforcement!: boolean;
  @field('origin') origin!: 'topic' | 'writing';
  @field('in_pool') inPool!: boolean;
  @date('created_at') createdAt!: Date;
  @date('updated_at') updatedAt!: Date;
}
