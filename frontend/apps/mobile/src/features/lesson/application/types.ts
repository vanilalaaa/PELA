import type { PracticeQuestion, RawAnswer, Vocabulary } from '../../../domain/types';

/** UC-06 học một Lesson (SRS 4.1.7 Lesson vocab, 4.1.8 Lesson progress). */
export interface LessonContent {
  lessonId: string;
  title: string;
  newWords: Vocabulary[];
  questions: PracticeQuestion[];
}

export interface LessonSubmitInput {
  /** Id lượt học do client sinh để gửi lại an toàn. */
  sessionId: string;
  lessonId: string;
  answers: RawAnswer[];
}

/** Kết quả do server tính lại; làm lại chỉ cộng phần sao tăng thêm (BR-36). */
export interface LessonResult {
  lessonId: string;
  correctCount: number;
  totalCount: number;
  starsEarned: number;
  starsAdded: number;
}
