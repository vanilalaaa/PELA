import type { CefrLevel } from '../../../domain/types';

/** UC-06 lộ trình (SRS 4.1.5 Topic, 4.1.6 Lesson, 4.1.8 Lesson progress, 4.1.10 Topic progress). */
export type TopicStatus = 'available' | 'in_progress' | 'completed' | 'locked';
export type LessonStatus = 'not_started' | 'in_progress' | 'completed';

export interface LessonSummary {
  lessonId: string;
  topicId: string;
  orderIndex: number;
  title: string;
  newWordCount: number;
  status: LessonStatus;
  /** 0–5, do server tính (BR-36). */
  starsEarned: number;
}

export interface RoadmapTopic {
  topicId: string;
  cefrLevel: CefrLevel;
  name: string;
  orderIndex: number;
  status: TopicStatus;
  lessons: LessonSummary[];
}

export interface Roadmap {
  cefrLevel: CefrLevel;
  topics: RoadmapTopic[];
}
