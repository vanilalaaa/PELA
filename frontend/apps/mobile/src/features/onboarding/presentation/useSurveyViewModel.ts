import { useEffect, useState } from 'react';
import { useUseCases } from '../../../app/UseCasesContext';
import { useSession } from '../../../app/session/sessionStore';
import {
  EMPTY_SURVEY_DRAFT,
  MAX_GOALS,
  isStepValid,
  toSurveyAnswers,
  toggleLimited,
  type SurveyDraft,
  type SurveyStep,
} from '../application/survey';
import type { SurveyAnswers } from '../application/types';

/**
 * ViewModel 09.1–09.5 (UC-02): 5 câu là state trong màn. Mỗi thay đổi lưu nháp (ROB-1) để mở lại app
 * vẫn ở đúng câu; nháp chỉ xoá khi gửi thành công (ở bước 10).
 */
export function useSurveyViewModel(onComplete: (answers: SurveyAnswers) => void) {
  const { onboarding } = useUseCases();
  const userId = useSession((s) => s.me?.userId ?? null);
  const [draft, setDraft] = useState<SurveyDraft>(EMPTY_SURVEY_DRAFT);
  const [restoring, setRestoring] = useState(true);

  useEffect(() => {
    let active = true;
    (async () => {
      const saved = userId ? await onboarding.loadSurveyDraft(userId) : null;
      if (!active) return;
      if (saved) setDraft(saved);
      setRestoring(false);
    })();
    return () => {
      active = false;
    };
  }, [onboarding, userId]);

  const update = (patch: Partial<SurveyDraft>) =>
    setDraft((prev) => {
      const next = { ...prev, ...patch };
      if (userId) void onboarding.saveSurveyDraft(userId, next);
      return next;
    });

  const step = draft.step;
  const canNext = isStepValid(step, draft);

  return {
    draft,
    restoring,
    step,
    canNext,
    goalsFull: draft.learningGoals.length >= MAX_GOALS,
    setOccupation: (occupation: SurveyDraft['occupation']) => update({ occupation }),
    setOccupationOther: (occupationOther: string) => update({ occupationOther }),
    toggleGoal: (goal: SurveyDraft['learningGoals'][number]) =>
      update({ learningGoals: toggleLimited(draft.learningGoals, goal, MAX_GOALS) }),
    toggleInterest: (interest: SurveyDraft['interests'][number]) => update({ interests: toggleLimited(draft.interests, interest) }),
    setDailyGoal: (dailyGoalMinutes: SurveyDraft['dailyGoalMinutes']) => update({ dailyGoalMinutes }),
    setLevel: (selfAssessedLevel: SurveyDraft['selfAssessedLevel']) => update({ selfAssessedLevel }),
    /** Câu sau; ở câu 5 thì chuyển sang bước 10 với câu trả lời đầy đủ. */
    next: () => {
      if (!canNext) return;
      if (step < 4) {
        update({ step: (step + 1) as SurveyStep });
        return;
      }
      const answers = toSurveyAnswers(draft);
      if (answers) onComplete(answers);
    },
    /** Lùi một câu (giữ đáp án). Trả false ở câu 1 để Back Android xử lý mặc định. */
    back: (): boolean => {
      if (step === 0) return false;
      update({ step: (step - 1) as SurveyStep });
      return true;
    },
  };
}
