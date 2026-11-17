import finalTestFixture from '@pela/fixtures/mock/finalTest.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { FinalTestRepository } from '../application/FinalTestRepository';
import type { FinalTestPaper, FinalTestResult, FinalTestStatus } from '../application/types';

interface FinalTestFixture {
  status: FinalTestStatus;
  paper: FinalTestPaper;
  result: FinalTestResult;
}

const data = fixture<FinalTestFixture>(finalTestFixture);

export function createMockFinalTestRepository(): FinalTestRepository {
  return {
    getStatus: (topicId) => mockCall(() => ({ ...clone(data.status), topicId }), { ...clone(data.status), topicId }),
    start: (topicId, attemptId) =>
      mockCall(() => ({ ...clone(data.paper), topicId, attemptId }), { attemptId, topicId, questions: [] }),
    submit: (input) =>
      mockCall(
        () => ({ ...clone(data.result), attemptId: input.attemptId, topicId: input.topicId }),
        { ...clone(data.result), attemptId: input.attemptId, topicId: input.topicId },
      ),
  };
}
