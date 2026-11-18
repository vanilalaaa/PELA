import gamificationFixture from '@pela/fixtures/mock/gamification.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { GamificationRepository } from '../application/GamificationRepository';
import type { Badge, Leaderboard, Streak, UserStats } from '../application/types';

interface GamificationFixture {
  stats: UserStats;
  streak: Streak;
  badges: Badge[];
  leaderboard: Leaderboard;
}

const data = fixture<GamificationFixture>(gamificationFixture);

export function createMockGamificationRepository(): GamificationRepository {
  return {
    getStats: () => mockCall(() => clone(data.stats), { starTotal: 0, currentStreak: 0 }),
    getStreak: () =>
      mockCall(() => clone(data.streak), { currentStreak: 0, longestStreak: 0, restoresUsed: 0, lastActiveDate: null, activeDates: [] }),
    getBadges: () => mockCall(() => clone(data.badges), []),
    setDisplayedBadges: (ids) =>
      mockCall(() => clone(data.badges).map((b) => ({ ...b, isDisplayed: ids.includes(b.badgeId) })), []),
    getLeaderboard: (period) =>
      mockCall(() => ({ ...clone(data.leaderboard), period }), { ...clone(data.leaderboard), period, entries: [], me: null }),
  };
}
