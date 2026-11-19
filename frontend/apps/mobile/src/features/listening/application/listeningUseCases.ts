import type { ListeningRepository } from './ListeningRepository';

export type ListeningUseCases = ListeningRepository;

export function createListeningUseCases(repo: ListeningRepository): ListeningUseCases {
  return { listClips: () => repo.listClips(), getClip: (id) => repo.getClip(id), submit: (input) => repo.submit(input) };
}
