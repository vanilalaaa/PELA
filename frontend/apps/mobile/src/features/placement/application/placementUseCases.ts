import type { PlacementRepository } from './PlacementRepository';

export type PlacementUseCases = PlacementRepository;

export function createPlacementUseCases(repo: PlacementRepository): PlacementUseCases {
  return {
    getTest: () => repo.getTest(),
    submit: (attemptId, answers) => repo.submit(attemptId, answers),
  };
}
