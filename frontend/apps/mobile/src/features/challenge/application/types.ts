import type { IsoDateTime, LocalDate, PracticeQuestion, RawAnswer } from '../../../domain/types';

/** UC-08/09 Daily/Weekly Challenge (SRS 4.1.13, 4.1.14). Sinh theo luật, không gọi AI (BR-5, quyết định 3.2). */
export type ChallengeType = 'daily' | 'weekly';
export type ChallengeItemSource = 'due_review' | 'weak' | 'new_vocab';

export interface ChallengeItem extends PracticeQuestion {
  source: ChallengeItemSource;
  position: number;
}

export interface ChallengeSession {
  challengeId: string;
  type: ChallengeType;
  challengeDate: LocalDate;
  status: 'in_progress' | 'completed';
  /** 0–5 daily, 0–6 weekly (BR-16, BR-17); null khi chưa xong. */
  starsEarned: number | null;
  items: ChallengeItem[];
  generatedAt: IsoDateTime;
}

export interface ChallengeSubmitInput {
  challengeId: string;
  answers: RawAnswer[];
}

export interface ChallengeResult {
  challengeId: string;
  correctCount: number;
  totalCount: number;
  starsEarned: number;
}
