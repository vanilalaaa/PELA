import { Model } from '@nozbe/watermelondb';
import { field } from '@nozbe/watermelondb/decorators';

/**
 * Mirror bảng nối `lesson_vocab` (PK ghép lesson_id+vocab_id ở Supabase).
 * WatermelonDB cần 1 `id` riêng nên id ở đây KHÔNG trùng id server — 2 cột
 * lessonId/vocabId dưới mới là khoá tra cứu thật khi sync.
 */
export class LessonVocab extends Model {
  static table = 'lesson_vocab';

  @field('lesson_id') lessonId!: string;
  @field('vocab_id') vocabId!: string;
  @field('is_new_word') isNewWord!: boolean;
}
