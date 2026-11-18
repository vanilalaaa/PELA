import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createSingleFlight } from '../../../core/ui/logic/singleFlight';
import { useSessionActions } from '../../../app/session/useSessionActions';
import { loginSchema, mapLoginError, type FieldErrors, type LoginAlert, type LoginForm } from './authForms';

/**
 * ViewModel 05 Login: form (react-hook-form + zod), "Ghi nhớ đăng nhập" (mặc định bật, Auth.Login.Remember),
 * gọi đăng nhập và ánh xạ lỗi server thành banner + lỗi ô. Đăng nhập xong RootNavigator tự chuyển nhánh.
 */
export function useLoginViewModel(initialEmail = '') {
  const { login } = useSessionActions();
  const form = useForm<LoginForm>({ resolver: zodResolver(loginSchema), defaultValues: { email: initialEmail, password: '' } });
  const [remember, setRemember] = useState(true);
  const [alert, setAlert] = useState<LoginAlert | null>(null);
  const [serverFieldErrors, setServerFieldErrors] = useState<FieldErrors>({});

  // Chặn gửi lần hai khi lần trước chưa xong (bấm nhanh hai lần, hoặc Enter + bấm nút).
  const [singleFlight] = useState(createSingleFlight);
  const submitForm = form.handleSubmit(async (values) => {
    setAlert(null);
    setServerFieldErrors({});
    try {
      await login({ email: values.email, password: values.password }, remember);
    } catch (error) {
      const mapped = mapLoginError(error);
      setAlert(mapped.alert);
      setServerFieldErrors(mapped.fieldErrors);
    }
  });
  const submit = () => singleFlight(() => submitForm());

  /** Sửa ô thì bỏ tô đỏ do server. */
  const clearServerError = (field: keyof LoginForm) =>
    setServerFieldErrors((prev) => (prev[field] ? { ...prev, [field]: undefined } : prev));

  return {
    control: form.control,
    /** Email đang gõ (điền sẵn cho 44b). */
    currentEmail: () => form.getValues('email').trim(),
    /** Chuyển focus sang ô khác; dời sang khung hình sau để Android không đóng bàn phím khi Enter. */
    focusField: (name: Parameters<typeof form.setFocus>[0]) => requestAnimationFrame(() => form.setFocus(name)),
    submitting: form.formState.isSubmitting,
    submit,
    remember,
    setRemember,
    alert,
    serverFieldErrors,
    clearServerError,
  };
}
