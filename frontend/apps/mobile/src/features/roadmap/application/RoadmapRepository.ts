import type { Roadmap } from './types';

export interface RoadmapRepository {
  /** Lộ trình trình độ hiện tại của learner (server chọn theo JWT). */
  getRoadmap(): Promise<Roadmap>;
}
