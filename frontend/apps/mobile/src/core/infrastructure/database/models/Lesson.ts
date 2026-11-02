import { Model } from '@nozbe/watermelondb';
import { field } from '@nozbe/watermelondb/decorators';

/** Mirror bảng `lesson` — id WatermelonDB = lesson_id của Supabase. */
export class Lesson extends Model {
  static table = 'lesson';

  @field('topic_id') topicId!: string;
  @field('order_index') orderIndex!: number;
  @field('new_word_count') newWordCount!: number;
  @field('status') status!: 'draft' | 'published';
}
