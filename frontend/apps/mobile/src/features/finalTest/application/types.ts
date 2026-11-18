import type { IsoDateTime, PracticeQuestion, RawAnswer } from '../../../domain/types';

/** UC-07 Final Test của Topic (SRS 4.1.9, 4.1.10; BR-20): 3 lượt, trượt hết thì khoá, cần sao để mở lại. */
export interface FinalTestStatus {
  topicId: string;
  unlockCycle: number;
  attemptsUsed: number;
  attemptsAllowed: number;
  locked: boolean;
  /** Sao kiếm thêm từ khi bị khoá / số sao cần để mở (giá trị từ server, MNT-3). */
  starsEarnedSinceLock: number;
  starsRequiredToUnlock: number;
  passed: boolean;
}

export interface FinalTestPaper {
  attemptId: string;
  topicId: string;
  questions: PracticeQuestion[];
}

export interface FinalTestResult {
  attemptId: string;
  topicId: string;
  attemptNumber: number;
  score: number;
  passed: boolean;
  attemptedAt: IsoDateTime;
  status: FinalTestStatus;
}

export interface FinalTestSubmitInput {
  attemptId: string;
  topicId: string;
  answers: RawAnswer[];
}
