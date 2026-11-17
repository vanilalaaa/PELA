import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { LoginScreen } from '../../features/auth/presentation/LoginScreen';
import { RegisterScreen } from '../../features/auth/presentation/RegisterScreen';
import { VerifyEmailScreen } from '../../features/auth/presentation/VerifyEmailScreen';
import { WelcomeScreen } from '../../features/auth/presentation/WelcomeScreen';
import { ForgotPasswordOtpScreen, ForgotPasswordScreen, ResetPasswordScreen } from './screens/placeholders';
import type { AuthStackParamList } from './types';

const Stack = createNativeStackNavigator<AuthStackParamList>();

/** AuthStack (UC-01). Chỉ hiện khi chưa đăng nhập. */
export function AuthNavigator() {
  const { t } = useTranslation();
  const title = (name: keyof AuthStackParamList) => ({ title: t(`nav.title.${name}`) });
  return (
    <Stack.Navigator>
      {/* 01–06 dựng theo Figma, tự vẽ header (ScreenHeader) nên ẩn header của navigator. */}
      <Stack.Group screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Welcome" component={WelcomeScreen} />
        <Stack.Screen name="Login" component={LoginScreen} />
        <Stack.Screen name="Register" component={RegisterScreen} />
        <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} />
      </Stack.Group>
      {/* Đợt 2 (44b–44d) còn màn tạm. */}
      <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} options={title('ForgotPassword')} />
      <Stack.Screen name="ForgotPasswordOtp" component={ForgotPasswordOtpScreen} options={title('ForgotPasswordOtp')} />
      <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} options={title('ResetPassword')} />
    </Stack.Navigator>
  );
}
