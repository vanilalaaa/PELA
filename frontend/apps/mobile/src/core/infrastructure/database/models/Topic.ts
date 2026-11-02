import { Model } from '@nozbe/watermelondb';
import { date, field } from '@nozbe/watermelondb/decorators';

/** Mirror bảng `topic` — id WatermelonDB = topic_id của Supabase. */
export class Topic extends Model {
  static table = 'topic';

  @field('cefr_level') cefrLevel!: string;
  @field('name') name!: string;
  @field('order_index') orderIndex!: number;
  @field('status') status!: 'draft' | 'published';
  @date('created_at') createdAt!: Date;
  @date('updated_at') updatedAt!: Date;
}
