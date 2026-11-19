import writingFixture from '@pela/fixtures/mock/writing.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type {
  WritingBankPrompt,
  WritingHistory,
  WritingPromptChoice,
  WritingRepository,
  WritingSubmission,
} from '../application';

interface WritingFixture {
  prompts: WritingBankPrompt[];
  generated: WritingPromptChoice;
  submission: WritingSubmission;
  history: WritingHistory;
}

const data = fixture<WritingFixture>(writingFixture);
const emptyHistory: WritingHistory = { items: [], scoreTrend: [], topIssueCategories: [] };

/** Writing giả lập: bài nộp nào cũng trả kết quả chấm mẫu (điểm do "server" mock quyết, không tính ở client). */
export function createMockWritingRepository(): WritingRepository {
  return {
    listPrompts: (_topic, limit = 10) => mockCall(() => clone(data.prompts).slice(0, limit), []),
    generatePrompt: () => mockCall(() => clone(data.generated), clone(data.generated)),
    submit: (input) =>
      mockCall(() => ({ submissionId: input.submissionId, status: 'pending' as const }), {
        submissionId: input.submissionId,
        status: 'pending' as const,
      }),
    getSubmission: (submissionId) =>
      mockCall(() => ({ ...clone(data.submission), submissionId }), { ...clone(data.submission), submissionId }),
    getHistory: () => mockCall(() => clone(data.history), emptyHistory),
    addVocabSuggestion: (suggestionId) =>
      mockCall(
        () => ({ suggestionId, status: 'added' as const, vocabId: '9a000000-0000-4000-8000-000000000001', reviewCardId: null }),
        { suggestionId, status: 'added' as const, vocabId: '9a000000-0000-4000-8000-000000000001', reviewCardId: null },
      ),
  };
}
