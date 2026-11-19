import type { FinalTestPaper, FinalTestResult, FinalTestStatus, FinalTestSubmitInput } from './types';

export interface FinalTestRepository {
  getStatus(topicId: string): Promise<FinalTestStatus>;
  /** `attemptId` do client sinh. Server từ chối khi hết lượt (423 Locked). */
  start(topicId: string, attemptId: string): Promise<FinalTestPaper>;
  submit(input: FinalTestSubmitInput): Promise<FinalTestResult>;
}
