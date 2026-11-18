import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createSingleFlight } from '../../../core/ui/logic/singleFlight';
import { useUseCases } from '../../../app/UseCasesContext';
import { mapRegisterError, registerSchema, type FieldErrors, type RegisterAlert, type RegisterForm } from './authForms';

/** ViewModel 02/03 Register: validate (Auth.Register.Validate), gọi đăng ký, ánh xạ lỗi (BR-1). */
export function useRegisterViewModel(onRegistered: (email: string, fullName: string) => void, initialName = '') {
  const { auth } = useUseCases();
  const form = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: initialName, email: '', password: '', confirmPassword: '' },
  });
  const password = useWatch({ control: form.control, name: 'password' });
  const [alert, setAlert] = useState<RegisterAlert | null>(null);
  const [serverFieldErrors, setServerFieldErrors] = useState<FieldErrors>({});

  // Chặn gửi lần hai khi lần trước chưa xong (bấm nhanh hai lần, hoặc Enter + bấm nút).
  const [singleFlight] = useState(createSingleFlight);
  const submitForm = form.handleSubmit(async (values) => {
    setAlert(null);
    setServerFieldErrors({});
    try {
      const result = await auth.register(values);
      onRegistered(result.email, values.fullName);
    } catch (error) {
      const mapped = mapRegisterError(error);
      setAlert(mapped.alert);
      setServerFieldErrors(mapped.fieldErrors);
    }
  });
  const submit = () => singleFlight(() => submitForm());

  const clearServerError = (field: keyof RegisterForm) =>
    setServerFieldErrors((prev) => (prev[field] ? { ...prev, [field]: undefined } : prev));

  return {
    control: form.control,
    /** Chuyển focus sang ô khác; dời sang khung hình sau để Android không đóng bàn phím khi Enter. */
    focusField: (name: Parameters<typeof form.setFocus>[0]) => requestAnimationFrame(() => form.setFocus(name)),
    watchPassword: password,
    submitting: form.formState.isSubmitting,
    submit,
    alert,
    serverFieldErrors,
    clearServerError,
  };
}
