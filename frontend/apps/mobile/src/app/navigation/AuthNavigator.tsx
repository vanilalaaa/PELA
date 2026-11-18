import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ForgotPasswordOtpScreen } from '../../features/auth/presentation/ForgotPasswordOtpScreen';
import { ForgotPasswordScreen } from '../../features/auth/presentation/ForgotPasswordScreen';
import { LoginScreen } from '../../features/auth/presentation/LoginScreen';
import { ResetPasswordScreen } from '../../features/auth/presentation/ResetPasswordScreen';
import { RegisterScreen } from '../../features/auth/presentation/RegisterScreen';
import { VerifyEmailScreen } from '../../features/auth/presentation/VerifyEmailScreen';
import { WelcomeScreen } from '../../features/auth/presentation/WelcomeScreen';
import type { AuthStackParamList } from './types';

const Stack = createNativeStackNavigator<AuthStackParamList>();

/** AuthStack (UC-01). Chỉ hiện khi chưa đăng nhập. */
export function AuthNavigator() {
  return (
    <Stack.Navigator>
      {/* 01–06, 44b–44d dựng theo Figma, tự vẽ header (ScreenHeader) nên ẩn header của navigator. */}
      <Stack.Group screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Welcome" component={WelcomeScreen} />
        <Stack.Screen name="Login" component={LoginScreen} />
        <Stack.Screen name="Register" component={RegisterScreen} />
        <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} />
        <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
        <Stack.Screen name="ForgotPasswordOtp" component={ForgotPasswordOtpScreen} />
        {/* Vuốt lùi iOS tắt: rời 44d phải qua hộp xác nhận. */}
        <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} options={{ gestureEnabled: false }} />
      </Stack.Group>
    </Stack.Navigator>
  );
}
