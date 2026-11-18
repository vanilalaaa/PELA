import type { Badge, Leaderboard, LeaderboardPeriod, Streak, UserStats } from './types';

export interface GamificationRepository {
  getStats(): Promise<UserStats>;
  getStreak(): Promise<Streak>;
  getBadges(): Promise<Badge[]>;
  /** Đổi huy hiệu hiển thị; server từ chối nếu quá 3 (BR-22). */
  setDisplayedBadges(badgeIds: string[]): Promise<Badge[]>;
  getLeaderboard(period: LeaderboardPeriod, scope: 'global' | 'friends'): Promise<Leaderboard>;
}
