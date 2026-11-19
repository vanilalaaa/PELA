import type { GamificationRepository } from './GamificationRepository';

export const MAX_DISPLAYED_BADGES = 3;

export type GamificationUseCases = GamificationRepository;

export function createGamificationUseCases(repo: GamificationRepository): GamificationUseCases {
  return {
    getStats: () => repo.getStats(),
    getStreak: () => repo.getStreak(),
    getBadges: () => repo.getBadges(),
    setDisplayedBadges: (ids) => repo.setDisplayedBadges(ids.slice(0, MAX_DISPLAYED_BADGES)),
    getLeaderboard: (period, scope) => repo.getLeaderboard(period, scope),
  };
}
