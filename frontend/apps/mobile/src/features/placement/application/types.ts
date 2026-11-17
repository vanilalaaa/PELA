import type { CefrLevel, IsoDateTime } from '../../../domain/types';

/** UC-03 Placement Test (SRS 4.1.3 Placement test attempt). Chỉ một lượt (BR-19). */
export interface PlacementQuestion {
  questionId: string;
  text: string;
  options: string[];
}

export interface PlacementTest {
  orientedLevel: CefrLevel;
  questions: PlacementQuestion[];
}

export interface PlacementAnswer {
  questionId: string;
  answer: string;
}

/** Điểm, đạt/trượt và trình độ xếp do server tính từ đáp án thô (BR-23). */
export interface PlacementResult {
  attemptId: string;
  orientedLevel: CefrLevel;
  testedLevel: CefrLevel;
  score: number;
  passed: boolean;
  placedLevel: CefrLevel;
  attemptedAt: IsoDateTime;
}
