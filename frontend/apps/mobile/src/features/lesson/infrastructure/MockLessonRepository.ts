import lessonFixture from '@pela/fixtures/mock/lesson.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { LessonRepository } from '../application/LessonRepository';
import type { LessonContent, LessonResult } from '../application/types';

interface LessonFixture {
  lesson: LessonContent;
  result: LessonResult;
}

const data = fixture<LessonFixture>(lessonFixture);

export function createMockLessonRepository(): LessonRepository {
  return {
    getLesson: (lessonId) =>
      mockCall(() => ({ ...clone(data.lesson), lessonId }), { ...clone(data.lesson), lessonId, newWords: [], questions: [] }),
    submit: (input) =>
      mockCall(() => ({ ...clone(data.result), lessonId: input.lessonId }), { ...clone(data.result), lessonId: input.lessonId }),
  };
}
