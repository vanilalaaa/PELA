import type { ListeningClip, ListeningClipSummary, ListeningResult, ListeningSubmitInput } from './types';

export interface ListeningRepository {
  listClips(): Promise<ListeningClipSummary[]>;
  getClip(clipId: string): Promise<ListeningClip>;
  submit(input: ListeningSubmitInput): Promise<ListeningResult>;
}
