import type { PlacementAnswer, PlacementResult, PlacementTest } from './types';

export interface PlacementRepository {
  getTest(): Promise<PlacementTest>;
  /** `attemptId` do client sinh để gửi lại an toàn. */
  submit(attemptId: string, answers: PlacementAnswer[]): Promise<PlacementResult>;
}
