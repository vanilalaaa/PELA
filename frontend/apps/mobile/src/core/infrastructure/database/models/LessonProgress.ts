import { Model } from '@nozbe/watermelondb';
import { date, field, json } from '@nozbe/watermelondb/decorators';

const passthroughSanitizer = (raw: unknown) => (raw && typeof raw === 'object' ? raw : {});

/** Mirror bảng `lesson_progress` — id WatermelonDB = progress_id của Supabase. */
export class LessonProgress extends Model {
  static table = 'lesson_progress';

  @field('user_id') userId!: string;
  @field('lesson_id') lessonId!: string;
  @field('status') status!: 'not_started' | 'in_progress' | 'completed';
  @field('stars_earned') starsEarned!: number | null;
  @field('correct_count') correctCount!: number | null;
  @field('total_count') totalCount!: number | null;
  /** [ROB-1] trạng thái "thoát giữa chừng rồi học tiếp" — JSON tự do, không áp schema chặt ở client. */
  @json('resume_state', passthroughSanitizer) resumeState!: Record<string, unknown> | null;
  @date('completed_at') completedAt!: Date | null;
}
