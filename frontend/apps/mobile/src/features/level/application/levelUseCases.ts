import type { CefrLevel } from '../../../domain/types';
import type { LevelRepository } from './LevelRepository';
import type { LevelOverview } from './types';

const ORDER: readonly CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

export type LevelChangeKind = 'same' | 'up' | 'down';

/** Lên hay xuống so với cấp hiện tại: lên phải thi Level-Up Test, xuống thì không (BR-28). */
export function levelChangeKind(current: CefrLevel, target: CefrLevel): LevelChangeKind {
  const diff = ORDER.indexOf(target) - ORDER.indexOf(current);
  return diff === 0 ? 'same' : diff > 0 ? 'up' : 'down';
}

/** Được bắt đầu thi lên cấp lúc `now` không (hiển thị; server vẫn là nơi chặn). */
export function canStartLevelUp(overview: LevelOverview, now: Date): boolean {
  if (overview.inProgressAttemptId) return false;
  return overview.nextAttemptAt === null || new Date(overview.nextAttemptAt).getTime() <= now.getTime();
}

export interface LevelUseCases extends LevelRepository {
  levelChangeKind: typeof levelChangeKind;
  canStartLevelUp: typeof canStartLevelUp;
}

export function createLevelUseCases(repo: LevelRepository): LevelUseCases {
  return {
    getOverview: () => repo.getOverview(),
    startLevelUpTest: (target, attemptId) => repo.startLevelUpTest(target, attemptId),
    submitLevelUpTest: (input) => repo.submitLevelUpTest(input),
    moveDown: (target) => repo.moveDown(target),
    levelChangeKind,
    canStartLevelUp,
  };
}
