import type { IsoDateTime } from '../../../domain/types';

/** UC-27 thông báo, UC-28 đánh giá, UC-31 báo lỗi (SRS 4.1.35–4.1.37, 4.1.40). */
export interface NotificationPreference {
  masterEnabled: boolean;
  dailyReminder: boolean;
  /** HH:mm theo múi giờ learner; mặc định 20:00. */
  remindTime: string;
  updatedAt: IsoDateTime;
}

export interface AppNotification {
  notificationId: string;
  type: string;
  title: string;
  body: string;
  readAt: IsoDateTime | null;
  createdAt: IsoDateTime;
}

export interface AppRatingInput {
  ratingId: string;
  /** 1–5 */
  stars: number;
  comment: string | null;
  appVersion: string;
}

export type AppReportCategory = 'bug' | 'wrong_content' | 'feedback' | 'other';

export interface AppReportInput {
  /** Id do client sinh; offline thì xếp hàng gửi sau (UC-31 A2). */
  reportId: string;
  category: AppReportCategory;
  /** ≤ 1.000 ký tự. */
  message: string;
  context: Record<string, string>;
  questionId: string | null;
  clipId: string | null;
}
