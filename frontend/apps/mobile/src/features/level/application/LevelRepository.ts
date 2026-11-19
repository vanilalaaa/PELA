import type { CefrLevel } from '../../../domain/types';
import type { LevelOverview, LevelUpResult, LevelUpSubmitInput, LevelUpTest } from './types';

export interface LevelRepository {
  getOverview(): Promise<LevelOverview>;
  /** `attemptId` do client sinh. Server từ chối khi đang trong thời gian chờ (BR-28). */
  startLevelUpTest(targetLevel: CefrLevel, attemptId: string): Promise<LevelUpTest>;
  submitLevelUpTest(input: LevelUpSubmitInput): Promise<LevelUpResult>;
  /** Xuống cấp, không cần thi (BR-28). Trả tổng quan mới. */
  moveDown(targetLevel: CefrLevel): Promise<LevelOverview>;
}
