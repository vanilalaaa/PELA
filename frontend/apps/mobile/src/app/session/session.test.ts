import { describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../../core/infrastructure/http/ApiRequestError';
import type { Me } from '../../features/auth/application';
import type { OnboardingStatus } from '../../features/onboarding/application/types';
import { bootstrapSession, isSessionRejected } from './bootstrapSession';
import { parseSessionSnapshot } from './sessionSnapshot';
import { selectRootBranch } from './rootBranch';

const ME = { userId: 'u1', email: 'a@b.c', fullName: 'A', role: 'learner', status: 'active', timezone: 'Asia/Ho_Chi_Minh' } as Me;
const DONE: OnboardingStatus = { onboardingStatus: 'done', orientedLevel: 'C1', cefrLevel: 'B1' };

describe('selectRootBranch', () => {
  it.each([
    ['loading', false, 'splash'],
    ['loading', true, 'splash'],
    ['signedOut', true, 'auth'],
    ['signedIn', false, 'onboarding'],
    ['signedIn', true, 'main'],
  ] as const)('%s + onboardingDone=%s → %s', (status, done, branch) => {
    expect(selectRootBranch(status, done)).toBe(branch);
  });
});

describe('bootstrapSession', () => {
  const SNAP = { me: ME, onboardingEntry: { screen: 'PlacementIntro' as const, orientedLevel: 'B1' as const } };
  const deps = (over: Partial<Parameters<typeof bootstrapSession>[0]> = {}) => ({
    restoreSession: vi.fn(async () => ME as Me | null),
    getOnboardingStatus: vi.fn(async () => DONE),
    clearTokens: vi.fn(async () => undefined),
    loadSnapshot: vi.fn(async () => SNAP as typeof SNAP | null),
    clearSnapshot: vi.fn(async () => undefined),
    ...over,
  });
  const fail = (e: unknown) => async () => Promise.reject(e);

  it('có token, /me ok → signedIn kèm trạng thái onboarding (không stale)', async () => {
    const d = deps();
    expect(await bootstrapSession(d)).toEqual({ status: 'signedIn', me: ME, onboardingEntry: null, stale: false });
    expect(d.clearTokens).not.toHaveBeenCalled();
  });
  it('chưa khảo sát → signedIn, mở Survey', async () => {
    const d = deps({ getOnboardingStatus: async () => ({ onboardingStatus: 'surveyPending', orientedLevel: null, cefrLevel: null }) });
    expect(await bootstrapSession(d)).toMatchObject({ status: 'signedIn', onboardingEntry: { screen: 'Survey' } });
  });
  it('khảo sát xong, chưa làm Placement → signedIn, mở PlacementIntro kèm orientedLevel', async () => {
    const d = deps({ getOnboardingStatus: async () => ({ onboardingStatus: 'placementPending', orientedLevel: 'C1', cefrLevel: null }) });
    expect(await bootstrapSession(d)).toMatchObject({ onboardingEntry: { screen: 'PlacementIntro', orientedLevel: 'C1' } });
  });
  it('không có token → signedOut', async () => {
    const d = deps({ restoreSession: async () => null });
    expect(await bootstrapSession(d)).toEqual({ status: 'signedOut' });
    expect(d.clearTokens).not.toHaveBeenCalled();
  });

  it.each([
    ['401 sau refresh lỗi', new ApiRequestError(401, 'UNAUTHORIZED', 'x')],
    ['ACCOUNT_SUSPENDED', new ApiRequestError(403, 'ACCOUNT_SUSPENDED', 'x')],
    ['ACCOUNT_PENDING_DELETION', new ApiRequestError(403, 'ACCOUNT_PENDING_DELETION', 'x')],
  ])('server từ chối phiên (%s) → signedOut, xoá token và ảnh chụp', async (_n, error) => {
    const d = deps({ restoreSession: fail(error) });
    expect(await bootstrapSession(d)).toEqual({ status: 'signedOut' });
    expect(d.clearTokens).toHaveBeenCalledTimes(1);
    expect(d.clearSnapshot).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['mất mạng', new ApiRequestError(0, 'NETWORK_ERROR', 'x')],
    ['timeout', new ApiRequestError(0, 'TIMEOUT', 'x')],
    ['5xx', new ApiRequestError(503, 'AUTH_PROVIDER_UNAVAILABLE', 'x')],
    ['mock "Ép lỗi"', new ApiRequestError(500, 'MOCK_FORCED_ERROR', 'x')],
    ['lỗi không rõ', new Error('boom')],
  ])('lỗi tạm thời (%s) → giữ phiên bằng ảnh chụp (stale), không xoá token', async (_n, error) => {
    const d = deps({ restoreSession: fail(error) });
    expect(await bootstrapSession(d)).toEqual({ status: 'signedIn', me: ME, onboardingEntry: SNAP.onboardingEntry, stale: true });
    expect(d.clearTokens).not.toHaveBeenCalled();
    expect(d.clearSnapshot).not.toHaveBeenCalled();
  });

  it('lỗi tạm thời nhưng chưa có ảnh chụp → signedOut, vẫn giữ token', async () => {
    const d = deps({ restoreSession: fail(new ApiRequestError(0, 'NETWORK_ERROR', 'x')), loadSnapshot: async () => null });
    expect(await bootstrapSession(d)).toEqual({ status: 'signedOut' });
    expect(d.clearTokens).not.toHaveBeenCalled();
  });

  it('/me ok nhưng trạng thái onboarding lỗi mạng → dùng bước onboarding trong ảnh chụp', async () => {
    const d = deps({ getOnboardingStatus: fail(new ApiRequestError(0, 'NETWORK_ERROR', 'x')) });
    expect(await bootstrapSession(d)).toEqual({ status: 'signedIn', me: ME, onboardingEntry: SNAP.onboardingEntry, stale: true });
  });

  it('ảnh chụp của tài khoản khác → không dùng', async () => {
    const other = { ...SNAP, me: { ...ME, userId: 'u2' } };
    const d = deps({ getOnboardingStatus: fail(new ApiRequestError(0, 'NETWORK_ERROR', 'x')), loadSnapshot: async () => other });
    expect(await bootstrapSession(d)).toEqual({ status: 'signedOut' });
  });

  it('isSessionRejected chỉ đúng với 401 và tài khoản bị chặn', () => {
    expect(isSessionRejected(new ApiRequestError(401, 'X', 'x'))).toBe(true);
    expect(isSessionRejected(new ApiRequestError(403, 'FORBIDDEN', 'x'))).toBe(false);
    expect(isSessionRejected(new ApiRequestError(0, 'NETWORK_ERROR', 'x'))).toBe(false);
    expect(isSessionRejected(null)).toBe(false);
  });
});

describe('parseSessionSnapshot', () => {
  it('nhận ảnh chụp hợp lệ, từ chối dữ liệu hỏng', () => {
    expect(parseSessionSnapshot({ me: ME, onboardingEntry: null })).toEqual({ me: ME, onboardingEntry: null });
    expect(parseSessionSnapshot({ me: ME, onboardingEntry: { screen: 'Survey' } })).toMatchObject({ onboardingEntry: { screen: 'Survey' } });
    expect(parseSessionSnapshot({ me: ME, onboardingEntry: { screen: 'PlacementIntro' } })).toBeNull();
    expect(parseSessionSnapshot({ me: {}, onboardingEntry: null })).toBeNull();
    expect(parseSessionSnapshot(undefined)).toBeNull();
  });
});
