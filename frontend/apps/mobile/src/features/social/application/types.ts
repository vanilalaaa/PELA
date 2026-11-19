import type { CefrLevel, IsoDateTime } from '../../../domain/types';

/** UC-05 bạn bè, UC-26 chặn/báo cáo (SRS 4.1.4 Friendship, 4.1.33 User block, 4.1.34 User report). */
export interface UserCard {
  userId: string;
  fullName: string;
  username: string | null;
  avatarUrl: string | null;
  cefrLevel: CefrLevel | null;
  starTotal: number;
}

export interface FriendRequest {
  friendshipId: string;
  from: UserCard;
  createdAt: IsoDateTime;
}

export type ReportReason = 'spam' | 'harassment' | 'inappropriate_name' | 'inappropriate_avatar' | 'other';

export interface ReportUserInput {
  /** Id do client sinh (gửi lại an toàn). */
  reportId: string;
  targetId: string;
  reason: ReportReason;
  /** Bắt buộc khi reason = other. */
  note: string | null;
}
