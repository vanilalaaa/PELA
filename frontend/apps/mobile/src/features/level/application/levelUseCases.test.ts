import { describe, expect, it } from 'vitest';
import { canStartLevelUp, levelChangeKind } from './levelUseCases';
import type { LevelOverview } from './types';

const overview: LevelOverview = {
  currentLevel: 'A2',
  options: [],
  testConfig: { questionCount: 20, durationMinutes: 20, passPercent: 80, retryWaitDays: 7 },
  nextAttemptAt: null,
  inProgressAttemptId: null,
};

describe('levelChangeKind (BR-28)', () => {
  it('phân biệt lên, xuống, giữ nguyên', () => {
    expect(levelChangeKind('A2', 'B2')).toBe('up');
    expect(levelChangeKind('B1', 'A1')).toBe('down');
    expect(levelChangeKind('C1', 'C1')).toBe('same');
  });
});

describe('canStartLevelUp', () => {
  const now = new Date('2026-11-17T10:00:00Z');
  it('được thi khi không chờ và không có lượt dở', () => {
    expect(canStartLevelUp(overview, now)).toBe(true);
  });
  it('chặn trong thời gian chờ sau lượt trượt', () => {
    expect(canStartLevelUp({ ...overview, nextAttemptAt: '2026-11-20T10:00:00Z' }, now)).toBe(false);
    expect(canStartLevelUp({ ...overview, nextAttemptAt: '2026-11-17T09:00:00Z' }, now)).toBe(true);
  });
  it('chặn khi đang có lượt làm dở', () => {
    expect(canStartLevelUp({ ...overview, inProgressAttemptId: 'x' }, now)).toBe(false);
  });
});
