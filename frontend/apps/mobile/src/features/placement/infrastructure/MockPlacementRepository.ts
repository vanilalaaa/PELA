import placementFixture from '@pela/fixtures/mock/placement.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { PlacementRepository } from '../application/PlacementRepository';
import type { PlacementResult, PlacementTest } from '../application/types';

interface PlacementFixture {
  test: PlacementTest;
  result: PlacementResult;
}

const data = fixture<PlacementFixture>(placementFixture);

export function createMockPlacementRepository(): PlacementRepository {
  return {
    getTest: () => mockCall(() => clone(data.test), { ...clone(data.test), questions: [] }),
    submit: (attemptId) =>
      mockCall(() => ({ ...clone(data.result), attemptId }), { ...clone(data.result), attemptId }),
  };
}
