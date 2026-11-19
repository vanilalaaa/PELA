import type { PlacementRepository } from './PlacementRepository';
import type { PlacementProgressStore } from './PlacementProgressStore';
import { newProgress, parsePlacementProgress, toSubmitAnswers, type PlacementProgress } from './placementProgress';
import { clockOffsetMs, remainingMs } from './timer';
import { PLACEMENT_ALREADY_TAKEN, type PlacementResult } from './types';

/** Lỗi 409 khi đã làm bài (BR-19): caller lấy lại trạng thái từ server rồi vào app. */
export function isAlreadyTaken(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { code?: unknown }).code === PLACEMENT_ALREADY_TAKEN;
}

export interface PlacementUseCases {
  /** Lượt dở trên máy của tài khoản (null nếu không có / hỏng). */
  resume(userId: string): Promise<PlacementProgress | null>;
  /** Tạo lượt mới và lưu ngay. */
  start(userId: string): Promise<PlacementProgress>;
  saveProgress(userId: string, progress: PlacementProgress): Promise<void>;
  /** Nộp; thành công hoặc 409 thì xoá tiến độ trên máy. Lỗi khác giữ để thử lại. */
  submit(progress: PlacementProgress): Promise<PlacementResult>;
  /** Hết giờ theo giờ server ước tính. */
  isExpired(progress: PlacementProgress, deviceNow: number): boolean;
}

export function createPlacementUseCases(
  repo: PlacementRepository,
  store: PlacementProgressStore,
  now: () => number,
): PlacementUseCases {
  return {
    async resume(userId) {
      try {
        return parsePlacementProgress(await store.load(userId));
      } catch {
        return null;
      }
    },
    async start(userId) {
      try {
        const attempt = await repo.start();
        const progress = newProgress(attempt, clockOffsetMs(attempt.serverTime, now()));
        await store.save(userId, progress).catch(() => undefined);
        return progress;
      } catch (error) {
        if (isAlreadyTaken(error)) await store.clear().catch(() => undefined);
        throw error;
      }
    },
    saveProgress: (userId, progress) => store.save(userId, progress).catch(() => undefined),
    async submit(progress) {
      try {
        const result = await repo.submit(progress.attempt.attemptId, toSubmitAnswers(progress));
        await store.clear().catch(() => undefined);
        return result;
      } catch (error) {
        if (isAlreadyTaken(error)) await store.clear().catch(() => undefined);
        throw error;
      }
    },
    isExpired: (p, deviceNow) => remainingMs(p.attempt.deadline, p.offsetMs, deviceNow) <= 0,
  };
}
