import { Model } from '@nozbe/watermelondb';
import { date, field } from '@nozbe/watermelondb/decorators';

/** Mirror bảng `user_stats` (PK = user_id ở Supabase). */
export class UserStats extends Model {
  static table = 'user_stats';

  @field('user_id') userId!: string;
  @field('lessons_completed') lessonsCompleted!: number;
  /** [D-7] "thuộc" = review_card.status IN ('review','mastered'). */
  @field('words_mastered') wordsMastered!: number;
  @field('final_tests_passed') finalTestsPassed!: number;
  @field('writings_submitted') writingsSubmitted!: number;
  @field('listening_completed') listeningCompleted!: number;
  @field('daily_challenges_done') dailyChallengesDone!: number;
  @field('friends_count') friendsCount!: number;
  /** Tổng sao tích luỹ, không tiêu được (D-1). */
  @field('star_total') starTotal!: number;
  @date('updated_at') updatedAt!: Date;
}
