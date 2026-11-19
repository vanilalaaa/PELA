import { describe, expect, it, vi } from 'vitest';
import type { PlacementRepository } from './PlacementRepository';
import type { PlacementProgressStore } from './PlacementProgressStore';
import {
  answerCurrent,
  goNext,
  hasAnsweredCurrent,
  isLastQuestion,
  newProgress,
  parsePlacementProgress,
  toSubmitAnswers,
  type PlacementProgress,
} from './placementProgress';
import { createPlacementUseCases, isAlreadyTaken } from './placementUseCases';
import { clockOffsetMs, durationSec, formatMmSs, isWarning, remainingMs } from './timer';
import type { PlacementAttempt, PlacementResult } from './types';

const T0 = Date.parse('2026-11-18T10:00:00Z');
const ATTEMPT: PlacementAttempt = {
  attemptId: 'att-1',
  orientedLevel: 'C1',
  testedLevel: 'B2',
  questions: [
    { questionId: 'q1', kind: 'fill_blank', text: 'She ___ to school.', options: ['go', 'goes', 'going', 'gone'] },
    { questionId: 'q2', kind: 'meaning', text: 'grateful', options: ['thankful', 'angry', 'tired', 'careful'] },
    { questionId: 'q3', kind: 'meaning', text: 'scarce', options: ['plenty', 'rare', 'bright', 'cheap'] },
  ],
  deadline: new Date(T0 + 20 * 60_000).toISOString(),
  serverTime: new Date(T0).toISOString(),
};

describe('đồng hồ Placement (FLOW-10)', () => {
  it('thời gian còn lại theo deadline server, không âm', () => {
    expect(remainingMs(ATTEMPT.deadline, 0, T0)).toBe(20 * 60_000);
    expect(remainingMs(ATTEMPT.deadline, 0, T0 + 20 * 60_000)).toBe(0);
    expect(remainingMs(ATTEMPT.deadline, 0, T0 + 25 * 60_000)).toBe(0);
    expect(durationSec(ATTEMPT.deadline, ATTEMPT.serverTime)).toBe(1200);
  });

  it.each([-10, 10])('giờ máy lệch %i phút vẫn ra đúng thời gian còn lại', (skewMin) => {
    const skew = skewMin * 60_000;
    const receivedAtDevice = T0 + skew; // máy chạy nhanh/chậm so với server
    const offset = clockOffsetMs(ATTEMPT.serverTime, receivedAtDevice);
    expect(remainingMs(ATTEMPT.deadline, offset, receivedAtDevice)).toBe(20 * 60_000);
    expect(remainingMs(ATTEMPT.deadline, offset, receivedAtDevice + 19 * 60_000)).toBe(60_000);
    expect(remainingMs(ATTEMPT.deadline, offset, receivedAtDevice + 20 * 60_000)).toBe(0);
  });

  it('hiển thị mm:ss và cảnh báo khi còn ≤ 1 phút', () => {
    expect(formatMmSs(20 * 60_000)).toBe('20:00');
    expect(formatMmSs(59_001)).toBe('01:00');
    expect(formatMmSs(1)).toBe('00:01');
    expect(formatMmSs(0)).toBe('00:00');
    expect(isWarning(60_000)).toBe(true);
    expect(isWarning(60_001)).toBe(false);
    expect(isWarning(0)).toBe(false);
  });
});

describe('tiến độ lượt Placement', () => {
  it('không sang câu sau khi chưa chọn; không lùi', () => {
    let p = newProgress(ATTEMPT, 0);
    expect(goNext(p).index).toBe(0);
    p = answerCurrent(p, 1);
    expect(hasAnsweredCurrent(p)).toBe(true);
    p = goNext(p);
    expect(p.index).toBe(1);
    p = goNext(answerCurrent(p, 0));
    expect(isLastQuestion(p)).toBe(true);
    expect(goNext(answerCurrent(p, 1)).index).toBe(2);
  });

  it('đáp án gửi đi là nội dung phương án; câu chưa làm bỏ qua', () => {
    const p = answerCurrent(newProgress(ATTEMPT, 0), 1);
    expect(toSubmitAnswers(p)).toEqual([{ questionId: 'q1', answer: 'goes' }]);
  });

  it('đọc tiến độ đã lưu: hỏng → null, chỉ số lệch được kẹp', () => {
    expect(parsePlacementProgress(null)).toBeNull();
    expect(parsePlacementProgress({ attempt: { attemptId: 'x', questions: [] } })).toBeNull();
    const p = parsePlacementProgress({ attempt: ATTEMPT, offsetMs: 5, index: 9, answers: { q1: 1, q2: 'x' } });
    expect(p).toMatchObject({ index: 2, offsetMs: 5, answers: { q1: 1 } });
  });
});

describe('use case Placement', () => {
  const RESULT = { attemptId: 'att-1', score: 67, passed: false, placedLevel: 'B1' } as PlacementResult;
  const memoryStore = (): PlacementProgressStore & { data: unknown } => {
    const s = {
      data: null as unknown,
      load: async () => s.data,
      save: async (_u: string, p: PlacementProgress) => void (s.data = JSON.parse(JSON.stringify(p))),
      clear: async () => void (s.data = null),
    };
    return s;
  };
  const repo = (over: Partial<PlacementRepository> = {}): PlacementRepository => ({
    start: vi.fn(async () => ATTEMPT),
    submit: vi.fn(async () => RESULT),
    ...over,
  });
  const taken = { status: 409, code: 'PLACEMENT_ALREADY_TAKEN' };

  it('start lưu tiến độ kèm độ lệch giờ; resume đọc lại đúng câu', async () => {
    const store = memoryStore();
    const uc = createPlacementUseCases(repo(), store, () => T0 + 5 * 60_000);
    const p = await uc.start('u1');
    expect(p.offsetMs).toBe(-5 * 60_000);
    await uc.saveProgress('u1', goNext(answerCurrent(p, 1)));
    expect(await uc.resume('u1')).toMatchObject({ index: 1, answers: { q1: 1 } });
  });

  it('nộp thành công thì xoá tiến độ; lỗi mạng thì giữ', async () => {
    const ok = memoryStore();
    const uc = createPlacementUseCases(repo(), ok, () => T0);
    await uc.submit(await uc.start('u1'));
    expect(await uc.resume('u1')).toBeNull();

    const bad = memoryStore();
    const uc2 = createPlacementUseCases(repo({ submit: async () => Promise.reject({ status: 0, code: 'NETWORK_ERROR' }) }), bad, () => T0);
    const p = await uc2.start('u1');
    await expect(uc2.submit(p)).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    expect(await uc2.resume('u1')).not.toBeNull();
  });

  it('409 PLACEMENT_ALREADY_TAKEN ở start hoặc submit → xoá tiến độ, ném lỗi cho màn xử lý', async () => {
    const store = memoryStore();
    const uc = createPlacementUseCases(repo({ submit: async () => Promise.reject(taken) }), store, () => T0);
    const p = await uc.start('u1');
    await expect(uc.submit(p)).rejects.toSatisfy(isAlreadyTaken);
    expect(await uc.resume('u1')).toBeNull();

    const store2 = memoryStore();
    store2.data = p;
    const uc2 = createPlacementUseCases(repo({ start: async () => Promise.reject(taken) }), store2, () => T0);
    await expect(uc2.start('u1')).rejects.toSatisfy(isAlreadyTaken);
    expect(store2.data).toBeNull();
  });

  it('hết hạn (kể cả khi chạy nền rồi quay lại) theo giờ server ước tính', async () => {
    const uc = createPlacementUseCases(repo(), memoryStore(), () => T0);
    const p = await uc.start('u1');
    expect(uc.isExpired(p, T0 + 19 * 60_000)).toBe(false);
    expect(uc.isExpired(p, T0 + 20 * 60_000)).toBe(true);
    // Máy chậm 3 phút so với server: lúc máy chỉ T0+17' thì server đã T0+20'.
    const skewed = { ...p, offsetMs: 3 * 60_000 };
    expect(uc.isExpired(skewed, T0 + 17 * 60_000)).toBe(true);
  });
});
