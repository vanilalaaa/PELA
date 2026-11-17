import type { ChallengeRepository } from './ChallengeRepository';

export type ChallengeUseCases = ChallengeRepository;

export function createChallengeUseCases(repo: ChallengeRepository): ChallengeUseCases {
  return { getCurrent: (type) => repo.getCurrent(type), submit: (input) => repo.submit(input) };
}
