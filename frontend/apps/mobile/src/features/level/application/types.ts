import type { CefrLevel, IsoDateTime, PracticeQuestion, RawAnswer } from '../../../domain/types';

/** UC-25 đổi cấp (SRS 4.1.2 Level progress, 4.1.27 Level-up attempt; BR-28, AS-12). */
export type LevelStatus = 'locked' | 'unlocked' | 'completed';

export interface LevelOption {
  cefrLevel: CefrLevel;
  status: LevelStatus;
  /** Lên cấp cần Level-Up Test; xuống cấp thì không (BR-28). */
  requiresTest: boolean;
}

/** Cấu hình bài thi do Admin chỉnh (AS-12; mặc định 20 câu, 20 phút, đạt 80%, chờ 7 ngày). Lấy từ server, không hard-code (MNT-3). */
export interface LevelUpTestConfig {
  questionCount: number;
  durationMinutes: number;
  passPercent: number;
  retryWaitDays: number;
}

export interface LevelOverview {
  currentLevel: CefrLevel;
  options: LevelOption[];
  testConfig: LevelUpTestConfig;
  /** Sau lượt trượt: thời điểm được thi lại (BR-28); null = thi được ngay. */
  nextAttemptAt: IsoDateTime | null;
  /** Lượt đang làm dở (tối đa 1 lượt/learner). */
  inProgressAttemptId: string | null;
}

export interface LevelUpTest {
  attemptId: string;
  fromLevel: CefrLevel;
  targetLevel: CefrLevel;
  questions: PracticeQuestion[];
  startedAt: IsoDateTime;
  deadlineAt: IsoDateTime;
}

export interface LevelUpSubmitInput {
  attemptId: string;
  answers: RawAnswer[];
}

/** Điểm và đạt/trượt do server chấm (BR-23); đạt thì đổi cấp ngay. */
export interface LevelUpResult {
  attemptId: string;
  targetLevel: CefrLevel;
  score: number;
  passed: boolean;
  newLevel: CefrLevel;
  nextAttemptAt: IsoDateTime | null;
}
