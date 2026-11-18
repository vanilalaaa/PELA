import placementFixture from '@pela/fixtures/mock/placement.json';
import { clone, fixture, mockCall, writeMockOnboarding } from '../../../core/infrastructure/mock';
import type { KeyValueStore } from '../../../core/infrastructure/storage/keyValueStore';
import type { PlacementRepository } from '../application/PlacementRepository';
import type { PlacementResult, PlacementTest } from '../application/types';

interface PlacementFixture {
  test: PlacementTest;
  result: PlacementResult;
}

const data = fixture<PlacementFixture>(placementFixture);

/** Nộp bài xong thì onboarding chuyển done ở trình độ được xếp (giống server, BR-19). */
export function createMockPlacementRepository(store: KeyValueStore): PlacementRepository {
  return {
    getTest: () => mockCall(() => clone(data.test), { ...clone(data.test), questions: [] }),
    submit: async (attemptId) => {
      const result = await mockCall(() => ({ ...clone(data.result), attemptId }), { ...clone(data.result), attemptId });
      await writeMockOnboarding(store, { onboardingStatus: 'done', orientedLevel: result.orientedLevel, cefrLevel: result.placedLevel });
      return result;
    },
  };
}
