import listeningFixture from '@pela/fixtures/mock/listening.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { ListeningRepository } from '../application/ListeningRepository';
import type { ListeningClip, ListeningResult } from '../application/types';

interface ListeningFixture {
  clips: ListeningClip[];
  result: ListeningResult;
}

const data = fixture<ListeningFixture>(listeningFixture);

export function createMockListeningRepository(): ListeningRepository {
  const first = data.clips[0] as ListeningClip;
  return {
    listClips: () => mockCall(() => clone(data.clips).map(({ audioUrl: _a, questions: _q, ...summary }) => summary), []),
    getClip: (clipId) =>
      mockCall(() => clone(data.clips.find((c) => c.clipId === clipId) ?? first), { ...clone(first), questions: [] }),
    submit: (input) =>
      mockCall(() => ({ ...clone(data.result), sessionId: input.sessionId, clipId: input.clipId }), {
        ...clone(data.result),
        sessionId: input.sessionId,
        clipId: input.clipId,
      }),
  };
}
