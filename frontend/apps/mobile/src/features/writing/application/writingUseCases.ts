import type { WritingRepository } from './WritingRepository';
import type { WritingPromptChoice, WritingSubmitInput } from './types';

export interface WritingUseCases extends WritingRepository {
  /** Dựng body nộp bài từ đề đã chọn: gửi đúng promptSource + ragCacheId/promptQuestionId (OpenAPI). */
  submitForPrompt(submissionId: string, prompt: WritingPromptChoice | null, text: string): ReturnType<WritingRepository['submit']>;
}

/** Body nộp bài theo nguồn đề; `null` = viết tự do (freeform). */
export function buildSubmitInput(submissionId: string, prompt: WritingPromptChoice | null, text: string): WritingSubmitInput {
  if (!prompt) return { submissionId, promptSource: 'freeform', text };
  if (prompt.promptSource === 'ai_generated') {
    return { submissionId, promptSource: 'ai_generated', ragCacheId: prompt.ragCacheId ?? null, text };
  }
  return { submissionId, promptSource: 'bank', promptQuestionId: prompt.questionId ?? null, text };
}

export function createWritingUseCases(repo: WritingRepository): WritingUseCases {
  return {
    listPrompts: (topic, limit) => repo.listPrompts(topic, limit),
    generatePrompt: (input) => repo.generatePrompt(input),
    submit: (input) => repo.submit(input),
    getSubmission: (id) => repo.getSubmission(id),
    getHistory: (limit) => repo.getHistory(limit),
    addVocabSuggestion: (id) => repo.addVocabSuggestion(id),
    submitForPrompt: (submissionId, prompt, text) => repo.submit(buildSubmitInput(submissionId, prompt, text)),
  };
}
