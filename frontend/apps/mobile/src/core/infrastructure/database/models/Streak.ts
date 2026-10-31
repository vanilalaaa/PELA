import { Model } from '@nozbe/watermelondb';
import { field } from '@nozbe/watermelondb/decorators';

/** Mirror bảng `streak` (PK = user_id ở Supabase). */
export class Streak extends Model {
  static table = 'streak';

  @field('user_id') userId!: string;
  @field('current_streak') currentStreak!: number;
  @field('longest_streak') longestStreak!: number;
  /** [D-2] "Khôi phục streak": N lượt/tháng (system_setting). */
  @field('restores_used') restoresUsed!: number;
  @field('restores_month') restoresMonth!: string | null;
  @field('broken_streak') brokenStreak!: number | null;
  @field('broken_on') brokenOn!: string | null;
  @field('last_active_date') lastActiveDate!: string | null;
}
