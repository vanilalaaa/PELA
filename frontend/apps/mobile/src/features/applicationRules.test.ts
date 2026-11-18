import { describe, expect, it } from 'vitest';
import { attemptsLeft } from './finalTest/application/finalTestUseCases';
import { nextLesson } from './roadmap/application/roadmapUseCases';
import type { Roadmap } from './roadmap/application/types';
import { isValidAppReport } from './settings/application/settingsUseCases';
import { isValidReport } from './social/application/socialUseCases';
import { buildSubmitInput } from './writing/application';

describe('writing.buildSubmitInput', () => {
  it('gửi đúng nguồn đề + id tương ứng (OpenAPI WritingSubmitRequest)', () => {
    expect(buildSubmitInput('s', null, 't')).toEqual({ submissionId: 's', promptSource: 'freeform', text: 't' });
    const ai = { promptSource: 'ai_generated' as const, ragCacheId: 'c', promptText: 'p', cacheHit: false };
    expect(buildSubmitInput('s', ai, 't')).toEqual({ submissionId: 's', promptSource: 'ai_generated', ragCacheId: 'c', text: 't' });
    const bank = { promptSource: 'bank' as const, questionId: 'q', promptText: 'p', cacheHit: false };
    expect(buildSubmitInput('s', bank, 't')).toEqual({ submissionId: 's', promptSource: 'bank', promptQuestionId: 'q', text: 't' });
  });
});

describe('roadmap.nextLesson', () => {
  const lesson = (id: string, order: number, status: 'not_started' | 'in_progress' | 'completed') => ({
    lessonId: id, topicId: 't', orderIndex: order, title: id, newWordCount: 8, status, starsEarned: 0,
  });
  it('ưu tiên bài đang dở, bỏ qua chủ đề bị khoá', () => {
    const r: Roadmap = {
      cefrLevel: 'A2',
      topics: [
        { topicId: 't1', cefrLevel: 'A2', name: 'a', orderIndex: 1, status: 'in_progress', lessons: [lesson('l2', 2, 'not_started'), lesson('l1', 1, 'completed')] },
        { topicId: 't2', cefrLevel: 'A2', name: 'b', orderIndex: 2, status: 'locked', lessons: [lesson('l9', 1, 'in_progress')] },
      ],
    };
    expect(nextLesson(r)?.lessonId).toBe('l2');
  });
});

describe('finalTest.attemptsLeft (BR-20)', () => {
  it('còn lượt = 3 − đã dùng; khoá thì 0', () => {
    const s = { topicId: 't', unlockCycle: 1, attemptsUsed: 2, attemptsAllowed: 3, locked: false, starsEarnedSinceLock: 0, starsRequiredToUnlock: 30, passed: false };
    expect(attemptsLeft(s)).toBe(1);
    expect(attemptsLeft({ ...s, locked: true })).toBe(0);
  });
});

describe('báo cáo người dùng / báo lỗi app', () => {
  it('lý do "khác" phải có ghi chú (SRS 4.1.34)', () => {
    expect(isValidReport({ reportId: 'r', targetId: 't', reason: 'other', note: '  ' })).toBe(false);
    expect(isValidReport({ reportId: 'r', targetId: 't', reason: 'spam', note: null })).toBe(true);
  });
  it('báo lỗi app: không rỗng, ≤ 1.000 ký tự (UC-31)', () => {
    expect(isValidAppReport({ message: ' ' })).toBe(false);
    expect(isValidAppReport({ message: 'a'.repeat(1000) })).toBe(true);
    expect(isValidAppReport({ message: 'a'.repeat(1001) })).toBe(false);
  });
});
