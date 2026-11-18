import { useCallback, useEffect, useState } from 'react';
import { useUseCases } from '../../../app/UseCasesContext';
import type { CefrLevel } from '../../../domain/types';
import type { OnboardingNextStep, SurveyAnswers } from '../application/types';

/** Màn 10 hiện tối thiểu chừng này để người dùng kịp đọc (kể cả khi server trả rất nhanh). */
export const ANALYZING_MIN_MS = 1000;

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export type AnalyzingState = { kind: 'submitting' } | { kind: 'error'; network: boolean };

/**
 * ViewModel 10 Đang xem kết quả: gửi khảo sát khi mở màn (Onboarding.Survey.Map do server tính), hiện
 * tối thiểu ANALYZING_MIN_MS. Lỗi thì cho Thử lại; nháp vẫn còn nên quay lại sửa được.
 */
export function useSurveyAnalyzingViewModel(
  answers: SurveyAnswers,
  onDone: (next: OnboardingNextStep, mappedLevel: CefrLevel) => void,
) {
  const { onboarding } = useUseCases();
  const [state, setState] = useState<AnalyzingState>({ kind: 'submitting' });

  const submit = useCallback(async () => {
    setState({ kind: 'submitting' });
    const [result] = await Promise.allSettled([onboarding.submitSurvey(answers), wait(ANALYZING_MIN_MS)]);
    if (result.status === 'fulfilled') {
      onDone(result.value.next, result.value.mappedLevel);
      return;
    }
    const status = (result.reason as { status?: number } | null)?.status;
    setState({ kind: 'error', network: status === 0 });
  }, [answers, onboarding, onDone]);

  useEffect(() => {
    void submit();
    // Chỉ gửi một lần khi mở màn; thử lại qua nút.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { state, retry: () => void submit() };
}
