import { useState } from 'react';
import { useUseCases } from '../../../app/UseCasesContext';
import { useSession } from '../../../app/session/sessionStore';
import { onboardingEntry } from '../../onboarding/application/onboardingUseCases';

/**
 * Xử lý 409 PLACEMENT_ALREADY_TAKEN (BR-19): hiện thông báo, rồi khi người dùng tiếp tục thì lấy lại
 * tài khoản và trạng thái onboarding từ server và vào đúng nhánh (thường là Main).
 */
export function useAlreadyTaken() {
  const { auth, onboarding } = useUseCases();
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  return {
    visible,
    show: () => setVisible(true),
    busy,
    continueToApp: async () => {
      setBusy(true);
      try {
        const [me, status] = await Promise.all([auth.getMe(), onboarding.getStatus()]);
        useSession.getState().signIn(me, onboardingEntry(status));
      } catch {
        // Không lấy được trạng thái (mất mạng): server đã ghi nhận bài làm nên vẫn vào app.
        useSession.getState().completeOnboarding();
      } finally {
        setBusy(false);
      }
    },
  };
}
