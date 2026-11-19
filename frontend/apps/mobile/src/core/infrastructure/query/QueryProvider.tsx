import React, { useEffect, useState } from 'react';
import { AppState, Platform, type AppStateStatus } from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import { QueryClientProvider, focusManager, onlineManager, type QueryClient } from '@tanstack/react-query';
import { createQueryClient } from './queryClient';

// Có mạng lại → TanStack Query tự refetch query đang dùng (refetchOnReconnect).
onlineManager.setEventListener((setOnline) =>
  NetInfo.addEventListener((state) => {
    setOnline(state.isConnected !== false && state.isInternetReachable !== false);
  }),
);

function onAppStateChange(status: AppStateStatus) {
  // RN không có window focus: coi app quay lại foreground là "focus" để refetch dữ liệu cũ.
  if (Platform.OS !== 'web') focusManager.setFocused(status === 'active');
}

export function QueryProvider({ children, client }: { children: React.ReactNode; client?: QueryClient }) {
  const [queryClient] = useState(() => client ?? createQueryClient());

  useEffect(() => {
    const sub = AppState.addEventListener('change', onAppStateChange);
    return () => sub.remove();
  }, []);

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
