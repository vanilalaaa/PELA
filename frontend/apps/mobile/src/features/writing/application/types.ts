import type { Schemas } from '@pela/api-contract';

/** Kiểu Writing theo OpenAPI (SRS 4.1.16 Writing submission, 4.1.29 issue, 4.1.30 vocab suggestion). */
export type WritingBankPrompt = Schemas['WritingBankPrompt'];
export type WritingPromptChoice = Schemas['WritingPromptChoice'];
export type GenerateWritingPromptInput = Schemas['GenerateWritingPromptRequest'];
export type WritingSubmitInput = Schemas['WritingSubmitRequest'];
export type WritingSubmitResult = Schemas['WritingSubmitResult'];
export type WritingSubmission = Schemas['WritingSubmission'];
export type WritingIssue = Schemas['WritingIssue'];
export type WritingVocabSuggestion = Schemas['WritingVocabSuggestion'];
export type WritingHistory = Schemas['WritingHistory'];

export interface AddVocabSuggestionResult {
  suggestionId: string;
  status: 'added' | 'auto_added';
  vocabId: string;
  reviewCardId?: string | null;
}
