import type { PelaClient } from '@pela/api-contract';
import { call, unwrap } from '../../../core/infrastructure/http';
import type { WritingRepository } from '../application';

/** Writing qua backend /v1/practice/writing/* (OpenAPI tag writing). */
export function createApiWritingRepository(client: PelaClient): WritingRepository {
  return {
    listPrompts: (topic, limit) =>
      call(async () => unwrap(await client.GET('/practice/writing/prompts', { params: { query: { topic, limit } } }))),
    generatePrompt: (body) => call(async () => unwrap(await client.POST('/practice/writing/prompts/generate', { body }))),
    submit: (body) => call(async () => unwrap(await client.POST('/practice/writing/submissions', { body }))),
    getSubmission: (submissionId) =>
      call(async () =>
        unwrap(await client.GET('/practice/writing/submissions/{submissionId}', { params: { path: { submissionId } } })),
      ),
    getHistory: (limit) =>
      call(async () => unwrap(await client.GET('/practice/writing/submissions', { params: { query: { limit } } }))),
    addVocabSuggestion: (suggestionId) =>
      call(async () =>
        unwrap(
          await client.POST('/practice/writing/vocab-suggestions/{suggestionId}/add', { params: { path: { suggestionId } } }),
        ),
      ),
  };
}
