/**
 * Mỗi tính năng một test với Mock repository: dữ liệu fixture (packages/fixtures/mock) đúng hình dạng
 * mà UI cần, và 3 chế độ dev (bình thường / rỗng / lỗi) hoạt động. Dùng fake timer cho độ trễ 300–800 ms.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDevMockSettings } from '../core/infrastructure/mock';
import { createMemoryKeyValueStore } from '../core/infrastructure/storage/keyValueStore';
import { createMockAuthRepository } from './auth/infrastructure/MockAuthRepository';
import { createMockChallengeRepository } from './challenge/infrastructure/MockChallengeRepository';
import { createMockFinalTestRepository } from './finalTest/infrastructure/MockFinalTestRepository';
import { createMockGamificationRepository } from './gamification/infrastructure/MockGamificationRepository';
import { createMockLessonRepository } from './lesson/infrastructure/MockLessonRepository';
import { createMockLevelRepository } from './level/infrastructure/MockLevelRepository';
import { createMockListeningRepository } from './listening/infrastructure/MockListeningRepository';
import { createMockOnboardingRepository } from './onboarding/infrastructure/MockOnboardingRepository';
import placementFixture from '@pela/fixtures/mock/placement.json';
import { createMockPlacementRepository, mockPlacedLevel } from './placement/infrastructure/MockPlacementRepository';
import { createMockProfileRepository } from './profile/infrastructure/MockProfileRepository';
import { createMockRoadmapRepository } from './roadmap/infrastructure/MockRoadmapRepository';
import { createMockSettingsRepository } from './settings/infrastructure/MockSettingsRepository';
import { createMockSocialRepository } from './social/infrastructure/MockSocialRepository';
import { createMockSyncRepository } from './sync/infrastructure/MockSyncRepository';
import { createMockWritingRepository } from './writing/infrastructure/MockWritingRepository';

/** Chạy hết timer giả để lời gọi mock xong. */
async function run<T>(promise: Promise<T>): Promise<T> {
  // Gắn handler trước khi chạy timer để lỗi mock không thành unhandled rejection.
  const settled = promise.then(
    (value) => ({ ok: true as const, value }),
    (error: unknown) => ({ ok: false as const, error }),
  );
  await vi.runAllTimersAsync();
  const result = await settled;
  if (!result.ok) throw result.error;
  return result.value;
}

beforeEach(() => {
  vi.useFakeTimers();
  useDevMockSettings.getState().setMode('normal');
});
afterEach(() => {
  vi.useRealTimers();
});

describe('Mock repositories', () => {
  it('auth: đăng nhập mẫu trả token; sai mật khẩu → INVALID_CREDENTIALS', async () => {
    const repo = createMockAuthRepository();
    expect(await run(repo.login({ email: 'a@b.c', password: 'Abcdef12' }))).toHaveProperty('accessToken');
    const bad = run(repo.login({ email: 'a@b.c', password: 'WrongPass1' })).catch((e: { code: string }) => e.code);
    expect(await bad).toBe('INVALID_CREDENTIALS');
  });

  it('auth: quên mật khẩu — 429 kèm Retry-After, mã sai/hết hạn, hạn ngắn, token hỏng, trùng mật khẩu', async () => {
    const repo = createMockAuthRepository();
    await expect(run(repo.requestPasswordReset('busy@example.com'))).rejects.toMatchObject({ code: 'TOO_MANY_REQUESTS', retryAfterSec: 30 });
    await expect(run(repo.requestPasswordReset('learner@example.com'))).resolves.toBeUndefined();
    await expect(run(repo.verifyPasswordOtp('learner@example.com', '000000'))).rejects.toMatchObject({ code: 'INVALID_OTP' });
    await expect(run(repo.verifyPasswordOtp('expired@example.com', '123456'))).rejects.toMatchObject({ code: 'INVALID_OTP' });
    await expect(run(repo.verifyPasswordOtp('learner@example.com', '123456'))).resolves.toMatchObject({ expiresInSec: 600 });
    await expect(run(repo.verifyPasswordOtp('shortreset@example.com', '123456'))).resolves.toMatchObject({ expiresInSec: 20 });
    const broken = await run(repo.verifyPasswordOtp('resetfail@example.com', '123456'));
    const reset = (resetToken: string, newPassword: string) =>
      run(repo.resetPassword({ resetToken, newPassword, confirmPassword: newPassword }));
    await expect(reset(broken.resetToken, 'Abcdef12')).rejects.toMatchObject({ status: 401, code: 'INVALID_RESET_TOKEN' });
    await expect(reset('token', 'SamePass1')).rejects.toMatchObject({ code: 'SAME_PASSWORD' });
    await expect(reset('token', 'Abcdef12')).resolves.toBeUndefined();
  });

  it('onboarding: lưu trạng thái qua các lần mở app (surveyPending → placementPending → done)', async () => {
    const store = createMemoryKeyValueStore();
    const repo = createMockOnboardingRepository(store);
    expect(await run(repo.getStatus())).toEqual({ onboardingStatus: 'surveyPending', orientedLevel: null, cefrLevel: null });
    expect(await run(repo.submitSurvey({} as never))).toEqual({ mappedLevel: 'C1' });
    // "Mở lại app": repository mới, cùng kho lưu.
    const reopened = createMockOnboardingRepository(store);
    expect(await run(reopened.getStatus())).toEqual({ onboardingStatus: 'placementPending', orientedLevel: 'C1', cefrLevel: null });
    const placement = createMockPlacementRepository(store);
    const attempt = await run(placement.start());
    // 6/10 câu đúng = 60% ở bậc định hướng C1 → B1 (BR-19).
    const answers = attempt.questions.slice(0, 6).map((q, i) => ({ questionId: q.questionId, answer: placementFixture.questions[i]!.options[placementFixture.questions[i]!.correctOptionIndex]! }));
    await run(placement.submit(attempt.attemptId, answers));
    expect(await run(reopened.getStatus())).toEqual({ onboardingStatus: 'done', orientedLevel: 'C1', cefrLevel: 'B1' });
  });

  it('onboarding: "Bắt đầu từ đầu" → done ở A1', async () => {
    const store = createMemoryKeyValueStore();
    const repo = createMockOnboardingRepository(store);
    await run(repo.startFromBeginning());
    expect(await run(repo.getStatus())).toMatchObject({ onboardingStatus: 'done', cefrLevel: 'A1' });
  });

  it('placement: một lượt (BR-19) — start lần hai và nộp lại → 409; đề không lộ đáp án', async () => {
    const store = createMemoryKeyValueStore();
    await run(createMockOnboardingRepository(store).submitSurvey({ selfAssessedLevel: 'B2' } as never));
    const repo = createMockPlacementRepository(store);
    const attempt = await run(repo.start());
    expect(attempt).toMatchObject({ orientedLevel: 'B2', testedLevel: 'B1' });
    expect(JSON.stringify(attempt)).not.toContain('correctOptionIndex');
    await expect(run(repo.start())).rejects.toMatchObject({ status: 409, code: 'PLACEMENT_ALREADY_TAKEN' });
    const r = await run(repo.submit(attempt.attemptId, []));
    expect(r).toMatchObject({ score: 0, correctCount: 0, totalQuestions: 10, passed: false, placedLevel: 'A1' });
    await expect(run(repo.submit(attempt.attemptId, []))).rejects.toMatchObject({ code: 'PLACEMENT_ALREADY_TAKEN' });
  });

  it.each([
    ['A2', 50, 'A1'], ['B1', 79, 'A1'], ['B2', 39, 'A1'], ['B2', 40, 'A2'], ['C1', 26, 'A1'], ['C1', 27, 'A2'],
    ['C1', 53, 'A2'], ['C1', 54, 'B1'], ['C2', 19, 'A1'], ['C2', 20, 'A2'], ['C2', 40, 'B1'], ['C2', 60, 'B2'], ['C2', 80, 'C2'],
  ] as const)('mock xếp bậc theo bảng BR-19: định hướng %s, %i%% → %s', (oriented, score, placed) => {
    expect(mockPlacedLevel(oriented, score)).toBe(placed);
  });

  it('roadmap: có chủ đề; chế độ rỗng trả danh sách trống', async () => {
    const repo = createMockRoadmapRepository();
    expect((await run(repo.getRoadmap())).topics.length).toBeGreaterThan(0);
    useDevMockSettings.getState().setMode('empty');
    expect((await run(repo.getRoadmap())).topics).toEqual([]);
  });

  it('lesson: sao 0–5 (BR-16)', async () => {
    const r = await run(createMockLessonRepository().submit({ sessionId: 's', lessonId: 'l', answers: [] }));
    expect(r.starsEarned).toBeGreaterThanOrEqual(0);
    expect(r.starsEarned).toBeLessThanOrEqual(5);
  });

  it('finalTest: tối đa 3 lượt, ngưỡng mở khoá 30 sao (AS-5)', async () => {
    const s = await run(createMockFinalTestRepository().getStatus('t'));
    expect(s).toMatchObject({ topicId: 't', attemptsAllowed: 3, starsRequiredToUnlock: 30 });
  });

  it('challenge: daily 0–5 sao; chế độ lỗi ném MOCK_FORCED_ERROR', async () => {
    const repo = createMockChallengeRepository();
    expect((await run(repo.getCurrent('daily')))?.type).toBe('daily');
    useDevMockSettings.getState().setMode('error');
    const code = await run(repo.getCurrent('daily')).catch((e: { code: string }) => e.code);
    expect(code).toBe('MOCK_FORCED_ERROR');
  });

  it('writing: nộp bài giữ submissionId do client sinh', async () => {
    const r = await run(createMockWritingRepository().submit({ submissionId: 'sub-1', promptSource: 'freeform', text: 'x' }));
    expect(r).toEqual({ submissionId: 'sub-1', status: 'pending' });
  });

  it('listening: danh sách clip không lộ audio/câu hỏi', async () => {
    const clips = await run(createMockListeningRepository().listClips());
    expect(clips.length).toBeGreaterThan(0);
    expect(clips[0]).not.toHaveProperty('questions');
  });

  it('profile: đổi tên trả hồ sơ mới', async () => {
    const p = await run(createMockProfileRepository().updateFullName('Lan'));
    expect(p.fullName).toBe('Lan');
  });

  it('social: tìm bạn theo tên', async () => {
    const r = await run(createMockSocialRepository().searchUsers('lan'));
    expect(r.map((u) => u.username)).toContain('baolan');
  });

  it('gamification: tối đa 3 huy hiệu hiển thị trong fixture (BR-22)', async () => {
    const badges = await run(createMockGamificationRepository().getBadges());
    expect(badges.filter((b) => b.isDisplayed).length).toBeLessThanOrEqual(3);
  });

  it('settings: nhắc học mặc định 20:00', async () => {
    expect((await run(createMockSettingsRepository().getNotificationPreference())).remindTime).toBe('20:00');
  });

  it('sync: số bản ghi đã đồng bộ = số bản ghi gửi', async () => {
    const r = await run(createMockSyncRepository().push('dev', [{ id: '1', kind: 'k', payload: {}, createdAt: 'x' }]));
    expect(r.recordsSynced).toBe(1);
  });

  it('level: cấu hình Level-Up Test theo AS-12 (20 câu, 20 phút, 80%, chờ 7 ngày)', async () => {
    const o = await run(createMockLevelRepository().getOverview());
    expect(o.testConfig).toEqual({ questionCount: 20, durationMinutes: 20, passPercent: 80, retryWaitDays: 7 });
  });
});
