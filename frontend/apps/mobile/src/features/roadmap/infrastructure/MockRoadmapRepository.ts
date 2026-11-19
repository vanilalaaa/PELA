import roadmapFixture from '@pela/fixtures/mock/roadmap.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { RoadmapRepository } from '../application/RoadmapRepository';
import type { Roadmap } from '../application/types';

const data = fixture<Roadmap>(roadmapFixture);

export function createMockRoadmapRepository(): RoadmapRepository {
  return { getRoadmap: () => mockCall(() => clone(data), { cefrLevel: data.cefrLevel, topics: [] }) };
}
