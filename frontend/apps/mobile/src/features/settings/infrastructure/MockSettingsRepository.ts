import settingsFixture from '@pela/fixtures/mock/settings.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { SettingsRepository } from '../application/SettingsRepository';
import type { AppNotification, NotificationPreference } from '../application/types';

interface SettingsFixture {
  preference: NotificationPreference;
  notifications: AppNotification[];
}

const data = fixture<SettingsFixture>(settingsFixture);

export function createMockSettingsRepository(): SettingsRepository {
  let preference = clone(data.preference);
  return {
    getNotificationPreference: () => mockCall(() => clone(preference), clone(preference)),
    updateNotificationPreference: (next) =>
      mockCall(() => {
        preference = { ...next, updatedAt: new Date().toISOString() };
        return clone(preference);
      }, clone(preference)),
    listNotifications: () => mockCall(() => clone(data.notifications), []),
    rateApp: () => mockCall(() => undefined, undefined),
    reportProblem: () => mockCall(() => undefined, undefined),
  };
}
