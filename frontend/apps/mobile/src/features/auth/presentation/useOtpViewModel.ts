import { useState } from 'react';
import type { OtpStatus } from '../../../core/ui/components';
import { OTP_LENGTH } from '../../../core/ui/logic/otp';
import { createSingleFlight } from '../../../core/ui/logic/singleFlight';
import { useUseCases } from '../../../app/UseCasesContext';
import type { PasswordResetTicket } from '../application';
import { mapForgotError, mapOtpError, type ForgotAlert, type OtpAlert } from './authForms';

/**
 * ViewModel 44c Nhập mã (Auth.Recover.Verify): đủ 6 số thì tự xác thực (vẫn có nút Tiếp tục). Mã sai giữ
 * nguyên số để gõ đè; "Gửi lại mã" xoá các ô, khoá nút trong lúc gửi và báo đã gửi.
 */
export function useOtpViewModel(email: string, onVerified: (ticket: PasswordResetTicket) => void) {
  const { auth } = useUseCases();
  const [code, setCode] = useState('');
  const [status, setStatus] = useState<OtpStatus>('idle');
  const [alert, setAlert] = useState<OtpAlert | ForgotAlert | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);
  const [verifyOnce] = useState(createSingleFlight);
  const [resendOnce] = useState(createSingleFlight);

  const change = (next: string) => {
    setCode(next);
    if (status !== 'idle') setStatus('idle');
    if (alert?.key === 'invalidOtp') setAlert(null);
  };

  const verify = (value: string = code) =>
    verifyOnce(async () => {
      if (value.length !== OTP_LENGTH) return;
      setAlert(null);
      setResent(false);
      setVerifying(true);
      try {
        const ticket = await auth.verifyPasswordOtp(email, value);
        setStatus('correct');
        onVerified(ticket);
      } catch (error) {
        const mapped = mapOtpError(error);
        if (mapped.key === 'invalidOtp') setStatus('wrong');
        setAlert(mapped);
      } finally {
        setVerifying(false);
      }
    });

  /** Gửi lại mã: xoá các ô, gọi `onCleared` (màn đưa focus về ô đầu) rồi gửi. */
  const resend = (onCleared?: () => void) =>
    resendOnce(async () => {
      setAlert(null);
      setResent(false);
      setCode('');
      setStatus('idle');
      onCleared?.();
      setResending(true);
      try {
        await auth.requestPasswordReset(email);
        setResent(true);
      } catch (error) {
        setAlert(mapForgotError(error).alert);
      } finally {
        setResending(false);
      }
    });

  return { code, change, status, alert, verifying, resending, resent, verify, resend, canSubmit: code.length === OTP_LENGTH };
}
