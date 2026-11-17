/**
 * Mỗi tính năng một test với Mock repository: dữ liệu fixture (packages/fixtures/mock) đúng hình dạng
 * mà UI cần, và 3 chế độ dev (bình thường / rỗng / lỗi) hoạt động. Dùng fake timer cho độ trễ 300–800 ms.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDevMockSettings } from '../core/infrastructure/mock';
import { createMockAuthRepository } from './auth/infrastructure/MockAuthRepository';
import { createMockChallengeRepository } from './challenge/infrastructure/MockChallengeRepository';
import { createMockFinalTestRepository } from './finalTest/infrastructure/MockFinalTestRepository';
import { createMockGamificationRepository } from './gamification/infrastructure/MockGamificationRepository';
import { createMockLessonRepository } from './lesson/infrastructure/MockLessonRepository';
import { createMockLevelRepository } from './level/infrastructure/MockLevelRepository';
import { createMockListeningRepository } from './listening/infrastructure/MockListeningRepository';
import { createMockOnboardingRepository } from './onboarding/infrastructure/MockOnboardingRepository';
import { createMockPlacementRepository } from './placement/infrastructure/MockPlacementRepository';
import { createMockProfileRepository } from './profile/infrastructure/MockProfileRepository';
import { createMockRoadmapRepository } from './roadmap/infrastructure/MockRoadmapRepository';
import { createMockSettingsRepository } from './settings/infrastructure/MockSettingsRepository';
import { createMockSocialRepository } from './social/infrastructure/MockSocialRepository';
import { createMockSyncRepository } from './sync/infrastructure/MockSyncRepository';
import { createMockWritingRepository } from './writing/infrastructure/MockWritingRepository';

/** Chạy hết timer giả để lời gọi mock xong. */
async function run<T>(promise: Promise<T>): Promise<T> {
  await vi.runAllTimersAsync();
  return promise;
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

  it('onboarding: mặc định chưa khảo sát; khảo sát ánh xạ C1', async () => {
    const repo = createMockOnboardingRepository();
    expect(await run(repo.getStatus())).toMatchObject({ surveyDone: false, placementDone: false });
    expect(await run(repo.submitSurvey({} as never))).toEqual({ mappedLevel: 'C1' });
  });

  it('placement: kịch bản BR-19 — định hướng C1, thi B2, 64% → xếp B1, không đạt', async () => {
    const repo = createMockPlacementRepository();
    const r = await run(repo.submit('id-1', []));
    expect(r).toMatchObject({ attemptId: 'id-1', orientedLevel: 'C1', testedLevel: 'B2', score: 64, passed: false, placedLevel: 'B1' });
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
