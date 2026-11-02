import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { bootstrap } from './src/app/container';
import { ThemeProvider } from './src/core/ui/theme';
import { RootNavigator } from './src/app/navigation/RootNavigator';

// Composition root — khởi tạo i18n (và các hạ tầng dùng chung khác) trước khi render.
bootstrap();

export default function App() {
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <RootNavigator />
        <StatusBar style="auto" />
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
