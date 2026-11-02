import { Model } from '@nozbe/watermelondb';
import { date, field } from '@nozbe/watermelondb/decorators';

/** Mirror bảng `topic_progress` (PK ghép user_id+topic_id ở Supabase). */
export class TopicProgress extends Model {
  static table = 'topic_progress';

  @field('user_id') userId!: string;
  @field('topic_id') topicId!: string;
  @field('status') status!: 'available' | 'in_progress' | 'completed' | 'locked';
  @field('stars_earned_since_lock') starsEarnedSinceLock!: number;
  /** [BR-20] chu kỳ mở khoá hiện tại. */
  @field('unlock_cycle') unlockCycle!: number;
  @date('locked_at') lockedAt!: Date | null;
  @date('unlocked_at') unlockedAt!: Date | null;
  @date('completed_at') completedAt!: Date | null;
}
