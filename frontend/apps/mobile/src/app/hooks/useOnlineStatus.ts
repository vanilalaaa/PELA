import { useSyncExternalStore } from 'react';
import { onlineManager } from '@tanstack/react-query';

/** Trạng thái mạng (onlineManager được NetInfo cập nhật trong QueryProvider). */
export function useOnlineStatus(): boolean {
  return useSyncExternalStore(
    (cb) => onlineManager.subscribe(cb),
    () => onlineManager.isOnline(),
  );
}
