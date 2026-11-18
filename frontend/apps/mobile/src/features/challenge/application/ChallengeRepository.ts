import type { ChallengeResult, ChallengeSession, ChallengeSubmitInput, ChallengeType } from './types';

export interface ChallengeRepository {
  /** Bộ thử thách hôm nay / tuần này; null nếu chưa mở (vd. weekly ngoài khung giờ). */
  getCurrent(type: ChallengeType): Promise<ChallengeSession | null>;
  submit(input: ChallengeSubmitInput): Promise<ChallengeResult>;
}
