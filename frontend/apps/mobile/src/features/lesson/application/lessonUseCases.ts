import type { LessonRepository } from './LessonRepository';

export type LessonUseCases = LessonRepository;

export function createLessonUseCases(repo: LessonRepository): LessonUseCases {
  return { getLesson: (id) => repo.getLesson(id), submit: (input) => repo.submit(input) };
}
