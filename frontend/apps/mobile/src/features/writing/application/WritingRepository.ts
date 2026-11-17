import type {
  AddVocabSuggestionResult,
  GenerateWritingPromptInput,
  WritingBankPrompt,
  WritingHistory,
  WritingPromptChoice,
  WritingSubmission,
  WritingSubmitInput,
  WritingSubmitResult,
} from './types';

/** Cổng ra Writing (UC-10, UC-23, UC-24). Chấm điểm, sao, lượt/ngày do server quyết (BR-23, BR-29). */
export interface WritingRepository {
  listPrompts(topic: string, limit?: number): Promise<WritingBankPrompt[]>;
  /** Luôn trả một đề: đề AI (RAG) hoặc đề có sẵn kèm fallbackReason (BR-7). */
  generatePrompt(input: GenerateWritingPromptInput): Promise<WritingPromptChoice>;
  /** `submissionId` do client sinh; gửi lại cùng id là an toàn. */
  submit(input: WritingSubmitInput): Promise<WritingSubmitResult>;
  getSubmission(submissionId: string): Promise<WritingSubmission>;
  getHistory(limit?: number): Promise<WritingHistory>;
  addVocabSuggestion(suggestionId: string): Promise<AddVocabSuggestionResult>;
}
