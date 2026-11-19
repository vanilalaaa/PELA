/** Kiểu dùng chung giữa các tính năng (SRS 4.1 Data Dictionary, đổi tên sang camelCase). */

/** Trình độ CEFR (User.cefr_level). */
export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2';

/** UUID dạng chuỗi (khoá chính; id do client sinh cho dữ liệu ghi theo lô). */
export type Uuid = string;

/** Thời điểm ISO 8601 (timestamp). */
export type IsoDateTime = string;

/** Ngày địa phương YYYY-MM-DD theo múi giờ learner (BR-5, BR-11). */
export type LocalDate = string;

/** Từ vựng (SRS 4.1.11 Vocabulary). */
export interface Vocabulary {
  vocabId: Uuid;
  word: string;
  meaning: string;
  partOfSpeech: string | null;
  ipa: string | null;
  exampleSentence: string | null;
  exampleMeaning: string | null;
  audioUrl: string | null;
  imageUrl: string | null;
  cefrLevel: CefrLevel | null;
}

/** Dạng câu hỏi luyện từ (SRS 4.1.14 Challenge item question_type). */
export type QuestionType = 'mcq' | 'fill_blank' | 'spelling' | 'listen_choose';

/** Câu hỏi đã sinh để hiển thị; đáp án đúng không gửi kèm khi server chấm. */
export interface PracticeQuestion {
  questionId: Uuid;
  vocabId: Uuid;
  type: QuestionType;
  prompt: string;
  options: string[];
}

/** Đáp án thô gửi theo lô (BR-23); server chấm lại. */
export interface RawAnswer {
  questionId: Uuid;
  answer: string;
  answeredAt: IsoDateTime;
}
