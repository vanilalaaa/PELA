import type { LessonContent, LessonResult, LessonSubmitInput } from './types';

export interface LessonRepository {
  getLesson(lessonId: string): Promise<LessonContent>;
  submit(input: LessonSubmitInput): Promise<LessonResult>;
}
