import { useCallback, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { useUseCases } from '../../../app/UseCasesContext';
import { useSession } from '../../../app/session/sessionStore';
import { useOnlineStatus } from '../../../app/hooks/useOnlineStatus';
import { createSingleFlight } from '../../../core/ui/logic/singleFlight';
import type { PlacementProgress } from '../application/placementProgress';
import { isAlreadyTaken } from '../application/placementUseCases';
import { useAlreadyTaken } from './useAlreadyTaken';

/**
 * ViewModel 11 Intro (UC-03): có lượt dở trên máy thì "Làm tiếp", không thì "Bắt đầu" (cần mạng, FLOW-13).
 * "Bắt đầu từ đầu" chỉ có khi chưa bắt đầu lượt nào (FLOW-4).
 */
export function usePlacementIntroViewModel(onStarted: () => void, onPlacedA1: () => void) {
  const { placement, onboarding } = useUseCases();
  const userId = useSession((s) => s.me?.userId ?? null);
  const online = useOnlineStatus();
  const taken = useAlreadyTaken();
  const [progress, setProgress] = useState<PlacementProgress | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<'start' | 'beginning' | null>(null);
  const [error, setError] = useState<'network' | 'unknown' | null>(null);
  const [singleFlight] = useState(createSingleFlight);

  // Mỗi lần quay lại màn (vd. bấm ✕ ở 12) đọc lại lượt dở.
  useFocusEffect(
    useCallback(() => {
      let active = true;
      (async () => {
        const p = userId ? await placement.resume(userId) : null;
        if (!active) return;
        setProgress(p);
        setLoading(false);
      })();
      return () => {
        active = false;
      };
    }, [placement, userId]),
  );

  const start = () =>
    singleFlight(async () => {
      if (progress) {
        onStarted();
        return;
      }
      if (!userId) return;
      setError(null);
      setBusy('start');
      try {
        setProgress(await placement.start(userId));
        onStarted();
      } catch (e) {
        if (isAlreadyTaken(e)) taken.show();
        else setError((e as { status?: number } | null)?.status === 0 ? 'network' : 'unknown');
      } finally {
        setBusy(null);
      }
    });

  const startFromBeginning = () =>
    singleFlight(async () => {
      setError(null);
      setBusy('beginning');
      try {
        await onboarding.startFromBeginning();
        onPlacedA1();
      } catch (e) {
        setError((e as { status?: number } | null)?.status === 0 ? 'network' : 'unknown');
      } finally {
        setBusy(null);
      }
    });

  return {
    loading,
    online,
    resuming: progress !== null,
    progress,
    busy,
    error,
    taken,
    start,
    startFromBeginning,
    /** Bắt đầu lượt mới cần mạng; làm tiếp lượt dở thì không. */
    canStart: progress !== null || online,
  };
}
