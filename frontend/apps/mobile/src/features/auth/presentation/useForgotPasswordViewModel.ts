import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createSingleFlight } from '../../../core/ui/logic/singleFlight';
import { useUseCases } from '../../../app/UseCasesContext';
import { forgotSchema, mapForgotError, type FieldErrors, type ForgotAlert, type ForgotForm } from './authForms';

/**
 * ViewModel 44b Lấy lại mật khẩu (UC-01 2.0.A2, Auth.Recover.Request): gửi mã tới email. API luôn trả 202
 * nên gửi xong là sang 44c, không tiết lộ email có tồn tại không.
 */
export function useForgotPasswordViewModel(onSent: (email: string) => void, initialEmail = '') {
  const { auth } = useUseCases();
  const form = useForm<ForgotForm>({ resolver: zodResolver(forgotSchema), defaultValues: { email: initialEmail } });
  const [alert, setAlert] = useState<ForgotAlert | null>(null);
  const [serverFieldErrors, setServerFieldErrors] = useState<FieldErrors>({});
  const [singleFlight] = useState(createSingleFlight);

  const submitForm = form.handleSubmit(async ({ email }) => {
    setAlert(null);
    setServerFieldErrors({});
    try {
      await auth.requestPasswordReset(email);
      onSent(email);
    } catch (error) {
      const mapped = mapForgotError(error);
      setAlert(mapped.alert);
      setServerFieldErrors(mapped.fieldErrors);
    }
  });

  return {
    control: form.control,
    submitting: form.formState.isSubmitting,
    submit: () => singleFlight(() => submitForm()),
    alert,
    serverFieldErrors,
    clearServerError: () => setServerFieldErrors({}),
  };
}
