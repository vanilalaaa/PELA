import type { SettingsRepository } from './SettingsRepository';
import type { AppReportInput } from './types';

export const REPORT_MESSAGE_MAX = 1000;

/** UC-31: nội dung không rỗng và ≤ 1.000 ký tự. */
export function isValidAppReport(input: Pick<AppReportInput, 'message'>): boolean {
  const length = input.message.trim().length;
  return length > 0 && length <= REPORT_MESSAGE_MAX;
}

export interface SettingsUseCases extends SettingsRepository {
  isValidAppReport: typeof isValidAppReport;
}

export function createSettingsUseCases(repo: SettingsRepository): SettingsUseCases {
  return {
    getNotificationPreference: () => repo.getNotificationPreference(),
    updateNotificationPreference: (pref) => repo.updateNotificationPreference(pref),
    listNotifications: () => repo.listNotifications(),
    rateApp: (input) => repo.rateApp(input),
    reportProblem: (input) => repo.reportProblem(input),
    isValidAppReport,
  };
}
