import type { OnboardingRepository } from './OnboardingRepository';
import type { SurveyDraftStore } from './SurveyDraftStore';
import { parseSurveyDraft, resumeStep, type SurveyDraft } from './survey';
import type { OnboardingEntry, OnboardingNextStep, OnboardingStatus, SurveyAnswers, SurveyResult } from './types';

export const MAX_LEARNING_GOALS = 3;

/** Onboarding.Survey.SkipRule: ánh xạ ra A1 thì xếp thẳng A1, bỏ Placement Test (UC-03). */
export function nextStepAfterSurvey(result: SurveyResult): OnboardingNextStep {
  return result.mappedLevel === 'A1' ? 'done' : 'placement';
}

/** Đã xong onboarding (khảo sát xong và đã xếp trình độ, kể cả A1 bỏ Placement). */
export function isOnboardingDone(status: OnboardingStatus): boolean {
  return status.onboardingStatus === 'done';
}

/**
 * Màn mở đầu khi mở lại app: surveyPending → Survey; placementPending → PlacementIntro (BR-19: chưa
 * làm bài thì làm tiếp, không làm lại khảo sát); done → null (MainTabs). Thiếu orientedLevel thì coi
 * như dữ liệu hỏng và cho làm lại khảo sát.
 */
export function onboardingEntry(status: OnboardingStatus): OnboardingEntry {
  if (status.onboardingStatus === 'done') return null;
  if (status.onboardingStatus === 'placementPending' && status.orientedLevel) {
    return { screen: 'PlacementIntro', orientedLevel: status.orientedLevel };
  }
  return { screen: 'Survey' };
}

export interface OnboardingUseCases {
  getStatus(): Promise<OnboardingStatus>;
  submitSurvey(answers: SurveyAnswers): Promise<SurveyResult & { next: OnboardingNextStep }>;
  /** UC-03 A1: bỏ Placement, xếp A1 (không làm bài được nữa, BR-19). */
  startFromBeginning(): Promise<OnboardingStatus>;
  /** Bản nháp khảo sát của tài khoản (ROB-1); hỏng hoặc của tài khoản khác thì null. */
  loadSurveyDraft(userId: string): Promise<SurveyDraft | null>;
  /** Lưu nháp; lỗi lưu không chặn người dùng (chỉ mất khả năng khôi phục). */
  saveSurveyDraft(userId: string, draft: SurveyDraft): Promise<void>;
}

/** Kho nháp rỗng (test, hoặc khi không có DB). */
const NO_DRAFTS: SurveyDraftStore = { load: async () => null, save: async () => undefined, clear: async () => undefined };

export function createOnboardingUseCases(repo: OnboardingRepository, drafts: SurveyDraftStore = NO_DRAFTS): OnboardingUseCases {
  return {
    getStatus: () => repo.getStatus(),
    async submitSurvey(answers) {
      const result = await repo.submitSurvey({ ...answers, learningGoals: answers.learningGoals.slice(0, MAX_LEARNING_GOALS) });
      // Gửi xong thì bỏ nháp; xoá lỗi cũng không làm hỏng kết quả đã lưu ở server.
      await drafts.clear().catch(() => undefined);
      return { ...result, next: nextStepAfterSurvey(result) };
    },
    startFromBeginning: () => repo.startFromBeginning(),
    async loadSurveyDraft(userId) {
      try {
        const draft = parseSurveyDraft(await drafts.load(userId));
        return draft ? { ...draft, step: resumeStep(draft) } : null;
      } catch {
        return null;
      }
    },
    saveSurveyDraft: (userId, draft) => drafts.save(userId, draft).catch(() => undefined),
  };
}
