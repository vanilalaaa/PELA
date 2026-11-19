import challengeFixture from '@pela/fixtures/mock/challenge.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { ChallengeRepository } from '../application/ChallengeRepository';
import type { ChallengeResult, ChallengeSession } from '../application/types';

interface ChallengeFixture {
  daily: ChallengeSession;
  weekly: ChallengeSession;
  result: ChallengeResult;
}

const data = fixture<ChallengeFixture>(challengeFixture);

export function createMockChallengeRepository(): ChallengeRepository {
  return {
    getCurrent: (type) => mockCall(() => clone(data[type]), null),
    submit: (input) =>
      mockCall(() => ({ ...clone(data.result), challengeId: input.challengeId }), {
        ...clone(data.result),
        challengeId: input.challengeId,
      }),
  };
}
