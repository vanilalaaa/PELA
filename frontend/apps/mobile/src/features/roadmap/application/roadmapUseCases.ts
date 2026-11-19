import type { RoadmapRepository } from './RoadmapRepository';
import type { LessonSummary, Roadmap } from './types';

/** Bài học kế tiếp nên học: bài đang dở trước, rồi bài chưa học đầu tiên của chủ đề chưa khoá. */
export function nextLesson(roadmap: Roadmap): LessonSummary | null {
  const open = roadmap.topics.filter((t) => t.status !== 'locked');
  for (const status of ['in_progress', 'not_started'] as const) {
    for (const topic of open) {
      const lesson = [...topic.lessons].sort((a, b) => a.orderIndex - b.orderIndex).find((l) => l.status === status);
      if (lesson) return lesson;
    }
  }
  return null;
}

export interface RoadmapUseCases extends RoadmapRepository {
  nextLesson: typeof nextLesson;
}

export function createRoadmapUseCases(repo: RoadmapRepository): RoadmapUseCases {
  return { getRoadmap: () => repo.getRoadmap(), nextLesson };
}
