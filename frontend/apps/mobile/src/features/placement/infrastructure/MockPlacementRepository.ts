import placementFixture from '@pela/fixtures/mock/placement.json';
import { ApiRequestError } from '../../../core/infrastructure/http';
import { fixture, mockCall, readMockOnboarding, writeMockOnboarding } from '../../../core/infrastructure/mock';
import type { KeyValueStore } from '../../../core/infrastructure/storage/keyValueStore';
import type { CefrLevel } from '../../../domain/types';
import type { PlacementRepository } from '../application/PlacementRepository';
import { PLACEMENT_ALREADY_TAKEN, type PlacementAttempt, type PlacementQuestion, type PlacementResult } from '../application/types';

interface MockQuestion extends PlacementQuestion {
  /** Chỉ có trong mock (server không bao giờ gửi đáp án cho app). */
  correctOptionIndex: number;
}

interface PlacementFixture {
  questions: MockQuestion[];
  durationSec: number;
  /** Rút ngắn thời gian để thử cảnh báo 1 phút / hết giờ: khảo sát tự đánh giá ra bậc này. */
  shortDurationSec: number;
  shortDurationForOrientedLevel: CefrLevel;
}

const data = fixture<PlacementFixture>(placementFixture);
const LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
const ATTEMPT_KEY = 'pela.mock.placementAttempt';

const below = (level: CefrLevel): CefrLevel => LEVELS[Math.max(0, LEVELS.indexOf(level) - 1)] ?? 'A1';

/**
 * Bảng xếp bậc của BR-19 (UC-03 bước 3–4) — CHỈ dùng trong mock để giả server; app không tự tính.
 * Trả trình độ xếp theo bậc định hướng và % điểm.
 */
export function mockPlacedLevel(oriented: CefrLevel, score: number): CefrLevel {
  if (score >= 80) return oriented;
  switch (oriented) {
    case 'B2':
      return score < 40 ? 'A1' : 'A2';
    case 'C1':
      return score < 27 ? 'A1' : score <= 53 ? 'A2' : 'B1';
    case 'C2':
      return score < 20 ? 'A1' : score < 40 ? 'A2' : score < 60 ? 'B1' : 'B2';
    default:
      return 'A1';
  }
}

const alreadyTaken = () => new ApiRequestError(409, PLACEMENT_ALREADY_TAKEN, 'Placement test already taken');

/**
 * Placement giả lập như server (Hình A-1): `start` tạo lượt một lần (lượt thứ hai → 409), `submit` chấm từ
 * đáp án thô, xếp bậc theo BR-19 và chuyển onboarding sang done ngay khi nộp xong.
 */
export function createMockPlacementRepository(store: KeyValueStore): PlacementRepository {
  return {
    start: async () => {
      const state = await readMockOnboarding(store);
      const taken = await store.get(ATTEMPT_KEY);
      const oriented = (state.orientedLevel ?? 'B1') as CefrLevel;
      const now = Date.now();
      const duration = oriented === data.shortDurationForOrientedLevel ? data.shortDurationSec : data.durationSec;
      const attempt: PlacementAttempt = {
        attemptId: `c2000000-0000-4000-8000-${String(now % 1e12).padStart(12, '0')}`,
        orientedLevel: oriented,
        testedLevel: below(oriented),
        questions: data.questions.map(({ correctOptionIndex: _c, ...q }) => ({ ...q, options: [...q.options] })),
        deadline: new Date(now + duration * 1000).toISOString(),
        serverTime: new Date(now).toISOString(),
      };
      const out = await mockCall(() => {
        if (taken || state.onboardingStatus !== 'placementPending') throw alreadyTaken();
        return attempt;
      }, attempt);
      await store.set(ATTEMPT_KEY, JSON.stringify({ attemptId: out.attemptId, oriented }));
      return out;
    },
    submit: async (attemptId, answers) => {
      const state = await readMockOnboarding(store);
      const raw = await store.get(ATTEMPT_KEY);
      const saved = raw ? (JSON.parse(raw) as { attemptId: string; oriented: CefrLevel; submitted?: boolean }) : null;
      const total = data.questions.length;
      const correct = data.questions.filter((q) => answers.find((a) => a.questionId === q.questionId)?.answer === q.options[q.correctOptionIndex]).length;
      const score = Math.round((correct / total) * 100);
      const oriented = saved?.oriented ?? ((state.orientedLevel ?? 'B1') as CefrLevel);
      const result: PlacementResult = {
        attemptId,
        orientedLevel: oriented,
        testedLevel: below(oriented),
        score,
        correctCount: correct,
        totalQuestions: total,
        passed: score >= 80,
        placedLevel: mockPlacedLevel(oriented, score),
        attemptedAt: new Date().toISOString(),
      };
      const out = await mockCall(() => {
        if (!saved || saved.attemptId !== attemptId || saved.submitted || state.onboardingStatus === 'done') throw alreadyTaken();
        return result;
      }, result);
      await store.set(ATTEMPT_KEY, JSON.stringify({ ...saved, submitted: true }));
      await writeMockOnboarding(store, { onboardingStatus: 'done', orientedLevel: oriented, cefrLevel: out.placedLevel });
      return out;
    },
  };
}

/** Dev: xoá lượt mock (dùng khi đặt lại onboarding). */
export async function resetMockPlacement(store: KeyValueStore): Promise<void> {
  await store.remove(ATTEMPT_KEY);
}
