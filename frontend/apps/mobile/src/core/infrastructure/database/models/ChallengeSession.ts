import { Model } from '@nozbe/watermelondb';
import { date, field } from '@nozbe/watermelondb/decorators';

/** Mirror bảng `challenge_session` — id WatermelonDB = challenge_id của Supabase. */
export class ChallengeSession extends Model {
  static table = 'challenge_session';

  @field('user_id') userId!: string;
  @field('challenge_type') challengeType!: 'daily' | 'weekly';
  /** Ngày theo múi giờ learner (BR-5), 'YYYY-MM-DD'. */
  @field('challenge_date') challengeDate!: string;
  @field('status') status!: 'in_progress' | 'completed';
  /** NULL tới khi hoàn thành; điểm sao do server chốt (BR-36). */
  @field('stars_earned') starsEarned!: number | null;
  @date('generated_at') generatedAt!: Date;
  @date('completed_at') completedAt!: Date | null;
}
