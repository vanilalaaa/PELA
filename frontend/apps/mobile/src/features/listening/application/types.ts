import type { CefrLevel, RawAnswer } from '../../../domain/types';

/** UC-11 Listening (SRS 4.1.17, 4.1.18, 4.1.28; BR-37: audio TTS sinh sẵn khi Admin lưu). */
export interface ListeningClipSummary {
  clipId: string;
  topic: string;
  title: string;
  cefrLevel: CefrLevel;
  orderIndex: number;
  durationSec: number | null;
  /** Sao tốt nhất đã đạt (0–5); null = chưa nghe. */
  bestStars: number | null;
}

export interface ListeningQuestion {
  questionId: string;
  text: string;
  options: string[];
}

export interface ListeningClip extends ListeningClipSummary {
  audioUrl: string;
  questions: ListeningQuestion[];
}

export interface ListeningSubmitInput {
  sessionId: string;
  clipId: string;
  answers: RawAnswer[];
}

/** Transcript chỉ hiện sau khi trả lời (4.1.28). */
export interface ListeningResult {
  sessionId: string;
  clipId: string;
  correctCount: number;
  totalQuestions: number;
  starsEarned: number;
  starsAdded: number;
  transcript: string;
}
