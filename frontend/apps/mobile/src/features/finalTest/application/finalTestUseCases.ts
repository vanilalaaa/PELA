import type { FinalTestRepository } from './FinalTestRepository';
import type { FinalTestStatus } from './types';

/** Số lượt còn lại (hiển thị); quyết định khoá/mở vẫn do server (BR-20). */
export function attemptsLeft(status: FinalTestStatus): number {
  return status.locked ? 0 : Math.max(0, status.attemptsAllowed - status.attemptsUsed);
}

export interface FinalTestUseCases extends FinalTestRepository {
  attemptsLeft: typeof attemptsLeft;
}

export function createFinalTestUseCases(repo: FinalTestRepository): FinalTestUseCases {
  return {
    getStatus: (id) => repo.getStatus(id),
    start: (topicId, attemptId) => repo.start(topicId, attemptId),
    submit: (input) => repo.submit(input),
    attemptsLeft,
  };
}
