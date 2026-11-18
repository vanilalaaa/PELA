import { createContext, useContext } from 'react';
import type { AuthUseCases } from '../features/auth/application';
import type { ChallengeUseCases } from '../features/challenge/application/challengeUseCases';
import type { FinalTestUseCases } from '../features/finalTest/application/finalTestUseCases';
import type { GamificationUseCases } from '../features/gamification/application/gamificationUseCases';
import type { LevelUseCases } from '../features/level/application/levelUseCases';
import type { LessonUseCases } from '../features/lesson/application/lessonUseCases';
import type { ListeningUseCases } from '../features/listening/application/listeningUseCases';
import type { OnboardingUseCases } from '../features/onboarding/application/onboardingUseCases';
import type { PlacementUseCases } from '../features/placement/application/placementUseCases';
import type { ProfileUseCases } from '../features/profile/application/profileUseCases';
import type { RoadmapUseCases } from '../features/roadmap/application/roadmapUseCases';
import type { SettingsUseCases } from '../features/settings/application/settingsUseCases';
import type { SocialUseCases } from '../features/social/application/socialUseCases';
import type { SyncUseCases } from '../features/sync/application/syncUseCases';
import type { WritingUseCases } from '../features/writing/application';

/**
 * Tập use case đã tiêm phụ thuộc, do container.ts dựng. Mỗi tính năng thêm khoá của mình ở bước (c),
 * Presentation chỉ lấy use case từ đây, không import infrastructure.
 */
export interface UseCases {
  auth: AuthUseCases;
  onboarding: OnboardingUseCases;
  placement: PlacementUseCases;
  roadmap: RoadmapUseCases;
  lesson: LessonUseCases;
  finalTest: FinalTestUseCases;
  challenge: ChallengeUseCases;
  writing: WritingUseCases;
  listening: ListeningUseCases;
  profile: ProfileUseCases;
  social: SocialUseCases;
  gamification: GamificationUseCases;
  settings: SettingsUseCases;
  sync: SyncUseCases;
  level: LevelUseCases;
}

export const UseCasesContext = createContext<UseCases | null>(null);

export function useUseCases(): UseCases {
  const value = useContext(UseCasesContext);
  if (!value) throw new Error('useUseCases must be used inside UseCasesProvider');
  return value;
}
