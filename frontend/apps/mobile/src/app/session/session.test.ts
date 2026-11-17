import { describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../../core/infrastructure/http/ApiRequestError';
import type { Me } from '../../features/auth/application';
import type { OnboardingStatus } from '../../features/onboarding/application/types';
import { bootstrapSession } from './bootstrapSession';
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
  const deps = (over: Partial<Parameters<typeof bootstrapSession>[0]> = {}) => ({
    restoreSession: vi.fn(async () => ME as Me | null),
    getOnboardingStatus: vi.fn(async () => DONE),
    clearTokens: vi.fn(async () => undefined),
    ...over,
  });

  it('có token, /me ok → signedIn kèm trạng thái onboarding', async () => {
    const d = deps();
    expect(await bootstrapSession(d)).toEqual({ status: 'signedIn', me: ME, onboardingEntry: null });
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
  it('/me lỗi 401 → signedOut và xoá token', async () => {
    const d = deps({ restoreSession: async () => Promise.reject(new ApiRequestError(401, 'UNAUTHORIZED', 'x')) });
    expect(await bootstrapSession(d)).toEqual({ status: 'signedOut' });
    expect(d.clearTokens).toHaveBeenCalledTimes(1);
  });
  it('lỗi mạng → signedOut nhưng giữ token', async () => {
    const d = deps({ restoreSession: async () => Promise.reject(new ApiRequestError(0, 'NETWORK_ERROR', 'x')) });
    expect(await bootstrapSession(d)).toEqual({ status: 'signedOut' });
    expect(d.clearTokens).not.toHaveBeenCalled();
  });
});
