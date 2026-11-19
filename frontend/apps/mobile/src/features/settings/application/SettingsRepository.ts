import type { AppNotification, AppRatingInput, AppReportInput, NotificationPreference } from './types';

export interface SettingsRepository {
  getNotificationPreference(): Promise<NotificationPreference>;
  updateNotificationPreference(pref: Omit<NotificationPreference, 'updatedAt'>): Promise<NotificationPreference>;
  listNotifications(): Promise<AppNotification[]>;
  rateApp(input: AppRatingInput): Promise<void>;
  reportProblem(input: AppReportInput): Promise<void>;
}
