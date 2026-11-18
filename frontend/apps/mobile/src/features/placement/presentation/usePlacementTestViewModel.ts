import { useCallback, useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, AppState } from 'react-native';
import { useTranslation } from 'react-i18next';
import { onlineManager } from '@tanstack/react-query';
import { useUseCases } from '../../../app/UseCasesContext';
import { useSession } from '../../../app/session/sessionStore';
import { useOnlineStatus } from '../../../app/hooks/useOnlineStatus';
import {
  answerCurrent,
  currentQuestion,
  goNext,
  hasAnsweredCurrent,
  isLastQuestion,
  type PlacementProgress,
} from '../application/placementProgress';
import { isAlreadyTaken } from '../application/placementUseCases';
import { isWarning, remainingMs } from '../application/timer';
import type { PlacementResult } from '../application/types';
import { useAlreadyTaken } from './useAlreadyTaken';

/** Đồng hồ thiết bị (gọi trong effect/handler, không lúc render). */
const now = () => Date.now();
/** Hiện thông báo hết giờ một lúc trước khi sang kết quả. */
const TIMEOUT_NOTICE_MS = 1500;

export type SubmitState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'error'; network: boolean }
  /** Hết giờ, chưa nộp được vì mất mạng: tự nộp khi có mạng. */
  | { kind: 'waitingNetwork' }
  | { kind: 'timedOut' };

/**
 * ViewModel 12 Câu hỏi (UC-03): một lượt, không lùi, không bỏ qua câu. Tiến độ lưu sau mỗi thao tác.
 * Đồng hồ theo deadline server (bù lệch giờ máy); về foreground thì tính lại; hết giờ thì tự nộp.
 */
export function usePlacementTestViewModel(onDone: (result: PlacementResult) => void, onMissing: () => void) {
  const { t } = useTranslation();
  const { placement } = useUseCases();
  const userId = useSession((s) => s.me?.userId ?? null);
  const online = useOnlineStatus();
  const taken = useAlreadyTaken();
  const [progress, setProgress] = useState<PlacementProgress | null>(null);
  const [remaining, setRemaining] = useState<number | null>(null);
  const [submit, setSubmit] = useState<SubmitState>({ kind: 'idle' });
  const submittingRef = useRef(false);
  const warnedRef = useRef(false);
  const progressRef = useRef<PlacementProgress | null>(null);
  // Giá trị mới nhất cho callback dài hạn (đồng hồ, tự nộp) mà không dựng lại effect mỗi lần render.
  const latest = useRef({ onDone, t, showTaken: taken.show });
  useEffect(() => {
    progressRef.current = progress;
    latest.current = { onDone, t, showTaken: taken.show };
  });

  useEffect(() => {
    let active = true;
    (async () => {
      const p = userId ? await placement.resume(userId) : null;
      if (!active) return;
      if (!p) onMissing();
      else setProgress(p);
    })();
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [placement, userId]);

  const doSubmit = useCallback(
    async (timedOut: boolean) => {
      const p = progressRef.current;
      if (!p || submittingRef.current) return;
      if (timedOut && !onlineManager.isOnline()) {
        setSubmit({ kind: 'waitingNetwork' });
        return;
      }
      submittingRef.current = true;
      setSubmit({ kind: 'submitting' });
      try {
        const result = await placement.submit(p);
        if (timedOut) {
          setSubmit({ kind: 'timedOut' });
          AccessibilityInfo.announceForAccessibility(latest.current.t('placement.test.timeUpSubmitted'));
          setTimeout(() => latest.current.onDone(result), TIMEOUT_NOTICE_MS);
        } else {
          latest.current.onDone(result);
        }
      } catch (e) {
        submittingRef.current = false;
        if (isAlreadyTaken(e)) {
          setSubmit({ kind: 'idle' });
          latest.current.showTaken();
          return;
        }
        const network = (e as { status?: number } | null)?.status === 0;
        setSubmit(timedOut && network ? { kind: 'waitingNetwork' } : { kind: 'error', network });
      }
    },
    [placement],
  );

  // Đồng hồ: tick mỗi giây + tính lại khi app về foreground; hết giờ thì tự nộp.
  useEffect(() => {
    if (!progress) return undefined;
    const tick = () => {
      const left = remainingMs(progress.attempt.deadline, progress.offsetMs, now());
      setRemaining(left);
      if (isWarning(left) && !warnedRef.current) {
        warnedRef.current = true;
        AccessibilityInfo.announceForAccessibility(latest.current.t('placement.test.oneMinuteLeft'));
      }
      if (left <= 0) void doSubmit(true);
    };
    tick();
    const id = setInterval(tick, 1000);
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') tick();
    });
    return () => {
      clearInterval(id);
      sub.remove();
    };
    // progress đổi khi chọn đáp án; chỉ cần dựng lại khi đổi lượt.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [progress?.attempt.attemptId, doSubmit]);

  // Hết giờ khi offline: có mạng lại thì tự nộp.
  useEffect(() => {
    if (online && submit.kind === 'waitingNetwork') void doSubmit(true);
  }, [online, submit.kind, doSubmit]);

  const update = (next: PlacementProgress) => {
    setProgress(next);
    if (userId) void placement.saveProgress(userId, next);
  };

  const expired = remaining !== null && remaining <= 0;
  const locked = expired || submit.kind === 'submitting' || submit.kind === 'timedOut';

  return {
    progress,
    question: progress ? currentQuestion(progress) : null,
    index: progress?.index ?? 0,
    total: progress?.attempt.questions.length ?? 0,
    selected: progress ? progress.answers[currentQuestion(progress)?.questionId ?? ''] : undefined,
    remaining,
    warning: remaining !== null && isWarning(remaining),
    last: progress ? isLastQuestion(progress) : false,
    canNext: !!progress && hasAnsweredCurrent(progress) && !locked,
    locked,
    submit,
    online,
    taken,
    choose: (optionIndex: number) => {
      if (progress && !locked) update(answerCurrent(progress, optionIndex));
    },
    next: () => {
      if (progress && hasAnsweredCurrent(progress) && !locked) update(goNext(progress));
    },
    submitNow: () => void doSubmit(false),
  };
}
