import type { PlacementAnswer, PlacementAttempt, PlacementResult } from './types';

export interface PlacementRepository {
  /** Tạo lượt (một lần, BR-19); lượt thứ hai → 409 PLACEMENT_ALREADY_TAKEN. */
  start(): Promise<PlacementAttempt>;
  /** Nộp đáp án của lượt; gửi lại cùng attemptId + đáp án là an toàn. */
  submit(attemptId: string, answers: PlacementAnswer[]): Promise<PlacementResult>;
}
