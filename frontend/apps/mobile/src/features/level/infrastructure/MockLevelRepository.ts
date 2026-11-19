import levelFixture from '@pela/fixtures/mock/level.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { LevelRepository } from '../application/LevelRepository';
import type { LevelOverview, LevelUpResult, LevelUpTest } from '../application/types';

interface LevelFixture {
  overview: LevelOverview;
  test: LevelUpTest;
  result: LevelUpResult;
}

const data = fixture<LevelFixture>(levelFixture);

export function createMockLevelRepository(): LevelRepository {
  return {
    getOverview: () => mockCall(() => clone(data.overview), { ...clone(data.overview), options: [] }),
    startLevelUpTest: (targetLevel, attemptId) =>
      mockCall(() => ({ ...clone(data.test), targetLevel, attemptId }), { ...clone(data.test), targetLevel, attemptId, questions: [] }),
    submitLevelUpTest: (input) =>
      mockCall(() => ({ ...clone(data.result), attemptId: input.attemptId }), { ...clone(data.result), attemptId: input.attemptId }),
    moveDown: (targetLevel) =>
      mockCall(() => ({ ...clone(data.overview), currentLevel: targetLevel }), { ...clone(data.overview), currentLevel: targetLevel }),
  };
}
