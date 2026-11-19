import { useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createSingleFlight } from '../../../core/ui/logic/singleFlight';
import { useUseCases } from '../../../app/UseCasesContext';
import { isResetExpired, mapResetError, resetSchema, type FieldErrors, type ResetAlert, type ResetForm } from './authForms';

/** Đồng hồ hệ thống (gọi trong timer/handler, không lúc render). */
const now = () => Date.now();

/**
 * ViewModel 44d Mật khẩu mới (Auth.Recover.Reset). Hạn phiên đặt lại tính từ `expiresIn` server trả:
 * một bộ hẹn giờ chạy đúng lúc hết hạn, và kiểm lại khi app quay lại foreground (bộ hẹn giờ có thể bị
 * hoãn khi app nền). Hết hạn hoặc server trả INVALID_RESET_TOKEN → banner "Gửi lại mã".
 */
export function useResetPasswordViewModel(resetToken: string, expiresAt: string, onDone: () => void) {
  const { auth } = useUseCases();
  const form = useForm<ResetForm>({ resolver: zodResolver(resetSchema), defaultValues: { password: '', confirmPassword: '' } });
  const password = useWatch({ control: form.control, name: 'password' });
  const [alert, setAlert] = useState<ResetAlert | null>(() =>
    isResetExpired(expiresAt, now()) ? { key: 'resetExpired' } : null,
  );
  const [serverFieldErrors, setServerFieldErrors] = useState<FieldErrors>({});
  const [singleFlight] = useState(createSingleFlight);
  const expired = alert?.key === 'resetExpired';

  useEffect(() => {
    if (expired) return undefined;
    const markIfExpired = () => {
      if (isResetExpired(expiresAt, now())) setAlert({ key: 'resetExpired' });
    };
    const timer = setTimeout(markIfExpired, Math.max(0, Date.parse(expiresAt) - now()));
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') markIfExpired();
    });
    return () => {
      clearTimeout(timer);
      sub.remove();
    };
  }, [expiresAt, expired]);

  const submitForm = form.handleSubmit(async (values) => {
    if (isResetExpired(expiresAt, now())) {
      setAlert({ key: 'resetExpired' });
      return;
    }
    setAlert(null);
    setServerFieldErrors({});
    try {
      await auth.resetPassword({ resetToken, newPassword: values.password, confirmPassword: values.confirmPassword });
      onDone();
    } catch (error) {
      const mapped = mapResetError(error);
      setAlert(mapped.alert);
      setServerFieldErrors(mapped.fieldErrors);
    }
  });

  return {
    control: form.control,
    watchPassword: password,
    focusField: (name: keyof ResetForm) => requestAnimationFrame(() => form.setFocus(name)),
    submitting: form.formState.isSubmitting,
    submit: () => singleFlight(() => submitForm()),
    alert,
    expired,
    serverFieldErrors,
    clearServerError: (field: keyof ResetForm) =>
      setServerFieldErrors((prev) => (prev[field] ? { ...prev, [field]: undefined } : prev)),
  };
}
