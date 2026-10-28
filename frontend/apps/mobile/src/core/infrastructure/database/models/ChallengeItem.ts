import { Model } from '@nozbe/watermelondb';
import { date, field, json } from '@nozbe/watermelondb/decorators';

const passthroughSanitizer = (raw: unknown) => (raw && typeof raw === 'object' ? raw : {});

/**
 * Mirror bảng `challenge_item` — id WatermelonDB = item_id của Supabase.
 * Challenge sinh THEO LUẬT từ Vocabulary (không gọi AI/RAG) nên `question_payload` có thể
 * tạo ngay trên máy khi offline; server vẫn chấm lại tất định khi nhận kết quả.
 */
export class ChallengeItem extends Model {
  static table = 'challenge_item';

  @field('challenge_id') challengeId!: string;
  @field('vocab_id') vocabId!: string;
  @field('question_type') questionType!: 'mcq' | 'fill_blank' | 'word_order' | 'spelling' | 'listen_choose';
  @field('is_correct') isCorrect!: boolean | null;
  @field('position') position!: number;
  @field('source') source!: 'due_review' | 'weak' | 'new_vocab';
  @json('question_payload', passthroughSanitizer) questionPayload!: Record<string, unknown>;
  @json('user_answer', passthroughSanitizer) userAnswer!: Record<string, unknown> | null;
  @date('answered_at') answeredAt!: Date | null;
}
