import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { LoginScreen } from './screens/LoginScreen';
import {
  ForgotPasswordOtpScreen,
  ForgotPasswordScreen,
  RegisterScreen,
  ResetPasswordScreen,
  VerifyEmailScreen,
  WelcomeScreen,
} from './screens/placeholders';
import type { AuthStackParamList } from './types';

const Stack = createNativeStackNavigator<AuthStackParamList>();

/** AuthStack (UC-01). Chỉ hiện khi chưa đăng nhập. */
export function AuthNavigator() {
  const { t } = useTranslation();
  const title = (name: keyof AuthStackParamList) => ({ title: t(`nav.title.${name}`) });
  return (
    <Stack.Navigator>
      <Stack.Screen name="Welcome" component={WelcomeScreen} options={{ headerShown: false }} />
      <Stack.Screen name="Login" component={LoginScreen} options={title('Login')} />
      <Stack.Screen name="Register" component={RegisterScreen} options={title('Register')} />
      <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} options={title('VerifyEmail')} />
      <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} options={title('ForgotPassword')} />
      <Stack.Screen name="ForgotPasswordOtp" component={ForgotPasswordOtpScreen} options={title('ForgotPasswordOtp')} />
      <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} options={title('ResetPassword')} />
    </Stack.Navigator>
  );
}
