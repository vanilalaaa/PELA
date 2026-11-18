import type { CefrLevel, IsoDateTime } from '../../../domain/types';

/** UC-03 Placement Test (SRS 4.1.3; Hình A-1). Chỉ một lượt (BR-19). */
export type PlacementQuestionKind = 'fill_blank' | 'meaning';

export interface PlacementQuestion {
  questionId: string;
  kind: PlacementQuestionKind;
  /** Câu điền từ có chỗ trống `___`. */
  text: string;
  options: string[];
}

/** POST /placement-test/start: lượt do server tạo (attemptId do server cấp), đề không kèm đáp án. */
export interface PlacementAttempt {
  attemptId: string;
  orientedLevel: CefrLevel;
  testedLevel: CefrLevel;
  questions: PlacementQuestion[];
  /** Hạn nộp theo giờ server (FLOW-10). */
  deadline: IsoDateTime;
  /** Giờ server lúc trả lượt, để bù lệch giờ thiết bị. */
  serverTime: IsoDateTime;
}

export interface PlacementAnswer {
  questionId: string;
  /** Nội dung đáp án đã chọn (server chấm từ đáp án thô, BR-23). */
  answer: string;
}

/** Điểm, đạt/trượt và trình độ xếp do server tính (BR-19, BR-23). */
export interface PlacementResult {
  attemptId: string;
  orientedLevel: CefrLevel;
  testedLevel: CefrLevel;
  /** Phần trăm 0–100. */
  score: number;
  correctCount: number;
  totalQuestions: number;
  passed: boolean;
  placedLevel: CefrLevel;
  attemptedAt: IsoDateTime;
}

/** Mã lỗi khi đã làm bài (lượt thứ hai, BR-19). */
export const PLACEMENT_ALREADY_TAKEN = 'PLACEMENT_ALREADY_TAKEN';
