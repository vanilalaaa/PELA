import profileFixture from '@pela/fixtures/mock/profile.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { ProfileRepository } from '../application/ProfileRepository';
import type { Profile } from '../application/types';

const data = fixture<Profile>(profileFixture);

export function createMockProfileRepository(): ProfileRepository {
  let profile = clone(data);
  return {
    getProfile: () => mockCall(() => clone(profile), clone(profile)),
    updateFullName: (fullName) =>
      mockCall(() => {
        profile = { ...profile, fullName, fullNameChangedAt: new Date().toISOString() };
        return clone(profile);
      }, clone(profile)),
    updateUsername: (username) =>
      mockCall(() => {
        profile = { ...profile, username, usernameChangedAt: new Date().toISOString() };
        return clone(profile);
      }, clone(profile)),
  };
}
