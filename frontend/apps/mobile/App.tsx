import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { bootstrap, useCases } from './src/app/container';
import { UseCasesProvider } from './src/app/UseCasesProvider';
import { QueryProvider } from './src/core/infrastructure/query';
import { ThemeProvider } from './src/core/ui/theme';
import { RootNavigator } from './src/app/navigation/RootNavigator';

// Composition root — khởi tạo i18n (và các hạ tầng dùng chung khác) trước khi render.
bootstrap();

export default function App() {
  return (
    <SafeAreaProvider>
      <QueryProvider>
        <UseCasesProvider value={useCases}>
          <ThemeProvider>
            <RootNavigator />
            <StatusBar style="auto" />
          </ThemeProvider>
        </UseCasesProvider>
      </QueryProvider>
    </SafeAreaProvider>
  );
}
