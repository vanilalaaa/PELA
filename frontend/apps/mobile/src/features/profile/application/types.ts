import type { CefrLevel, IsoDateTime } from '../../../domain/types';

/** UC-04 hồ sơ (SRS 4.1.1 User, 4.1.32 User stats; BR-32 giới hạn đổi tên). */
export interface Profile {
  userId: string;
  fullName: string;
  username: string | null;
  avatarUrl: string | null;
  cefrLevel: CefrLevel;
  starTotal: number;
  currentStreak: number;
  friendCount: number;
  /** Lần đổi gần nhất để UI báo còn bao lâu được đổi (server vẫn là nơi chặn, BR-32). */
  fullNameChangedAt: IsoDateTime | null;
  usernameChangedAt: IsoDateTime | null;
}
