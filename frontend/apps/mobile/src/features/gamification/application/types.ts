import type { IsoDateTime, LocalDate } from '../../../domain/types';

/** UC-12 xếp hạng, UC-13 streak & huy hiệu (SRS 4.1.19–4.1.22, 4.1.31, 4.1.32). Mọi số do server tính (BR-23). */
export interface Streak {
  currentStreak: number;
  longestStreak: number;
  restoresUsed: number;
  lastActiveDate: LocalDate | null;
  /** Các ngày đã học gần đây để vẽ lịch tuần (Streak day). */
  activeDates: LocalDate[];
}

export type BadgeCategory = 'first_steps' | 'streak' | 'vocabulary' | 'topic' | 'writing' | 'listening' | 'social' | 'star';

export interface Badge {
  badgeId: string;
  name: string;
  description: string;
  iconUrl: string;
  category: BadgeCategory;
  tier: number;
  threshold: number;
  /** Tiến độ hiện tại so với threshold (server tính). */
  progress: number;
  earnedAt: IsoDateTime | null;
  /** Tối đa 3 huy hiệu hiển thị trên hồ sơ (BR-22). */
  isDisplayed: boolean;
}

export type LeaderboardPeriod = 'weekly' | 'all_time';

export interface LeaderboardEntry {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  starTotal: number;
  rank: number;
  isMe: boolean;
}

export interface Leaderboard {
  period: LeaderboardPeriod;
  periodStart: LocalDate | null;
  entries: LeaderboardEntry[];
  me: LeaderboardEntry | null;
  computedAt: IsoDateTime;
}

export interface UserStats {
  starTotal: number;
  currentStreak: number;
}
